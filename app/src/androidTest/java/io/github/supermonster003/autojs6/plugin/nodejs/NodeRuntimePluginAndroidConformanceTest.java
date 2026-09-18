package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.autojs.plugin.nodejs.api.INodeJsModuleSourceProvider;
import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsPluginIds;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Installed-runtime conformance for the plugin-owned Node.js boundary.
 *
 * <p>The test client lives in the instrumentation target process and binds an
 * actionless explicit component in {@code :nodejs_runtime}. Script execution
 * crosses the published AIDL contract, transfers a real workspace-v2 archive
 * through distinct PFDs, and reaches the packaged libnode/native bridge. Host
 * discovery, signer authorization, and fallback remain Host-owned tests.</p>
 */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class NodeRuntimePluginAndroidConformanceTest {

    private static final long BIND_TIMEOUT_MS = 30_000L;
    private static final long SCRIPT_TIMEOUT_MS = 20_000L;
    private static final int WORKSPACE_MAX_FILES = 128;
    private static final long WORKSPACE_MAX_BYTES = 1024L * 1024L;
    private static final String RUNTIME_PROCESS_SUFFIX = ":nodejs_runtime";
    private static final String TRANSPORT_PROTOCOL_DIRECTORY = ".autojs6-workspace-transport";
    private static final String INPUT_MANIFEST_PATH =
            TRANSPORT_PROTOCOL_DIRECTORY + "/input-manifest-v1.json";
    private static final String OUTPUT_TOMBSTONE_PATH =
            TRANSPORT_PROTOCOL_DIRECTORY + "/deletions-v1.json";
    private static final String NODE_COMPAT_CORPUS_V2_ASSET_ROOT = "node_compat_corpus_v2";
    private static final List<String> NODE_COMPAT_CORPUS_V2_FILES = Arrays.asList(
            "async-esm.mjs",
            "cjs-cycle.cjs",
            "data.json",
            "dynamic.json",
            "esm-cycle.mjs",
            "esm-harness.mjs",
            "json-require.cjs",
            "meta-target.mjs",
            "node_modules/custom-condition-pkg/auto.mjs",
            "node_modules/custom-condition-pkg/default.mjs",
            "node_modules/custom-condition-pkg/import.mjs",
            "node_modules/custom-condition-pkg/package.json",
            "sync-esm.mjs",
            "ts-cjs.cts",
            "ts-esm.mts"
    );
    private static final List<String> NODE_COMPAT_CORPUS_V2_CHECKS = Arrays.asList(
            "require_esm",
            "tla_rejection",
            "json_attributes",
            "esm_cjs_cycle",
            "import_meta_resolve",
            "package_custom_condition",
            "typescript_mixed_graph"
    );

    private Context targetContext;
    private BoundRuntime boundRuntime;
    private final AtomicLong plaintextProviderAdvertisedSourceMaxBytes = new AtomicLong(-1L);

    @Before
    public void bindRealPluginRuntime() throws Exception {
        targetContext = InstrumentationRegistry.getInstrumentation()
                .getTargetContext()
                .getApplicationContext();
        boundRuntime = BoundRuntime.bind(targetContext);
    }

    @After
    public void unbindRealPluginRuntime() {
        if (boundRuntime != null) {
            boundRuntime.close();
            boundRuntime = null;
        }
    }

    @Test
    public void x3d_01_remoteRuntimeInfoAndPrewarmUseDedicatedNativeProcess() throws Exception {
        Bundle runtimeInfo = boundRuntime.runtime.getRuntimeInfo();
        assertRuntimeInfo(runtimeInfo);

        int runtimePid = runtimeInfo.getInt(NodeJsRuntimeContract.KEY_PID, -1);
        assertNotEquals("runtime Binder unexpectedly stayed in the instrumentation process",
                Process.myPid(), runtimePid);
        assertEquals(
                targetContext.getPackageName() + RUNTIME_PROCESS_SUFFIX + "0",
                runtimeInfo.getString(NodeJsRuntimeContract.KEY_PROCESS_NAME)
        );

        Bundle prewarmRequest = new Bundle();
        prewarmRequest.putInt(
                NodeJsRuntimeContract.KEY_CONTRACT_VERSION,
                NodeJsRuntimeContract.CONTRACT_VERSION
        );
        Bundle prewarm = boundRuntime.runtime.prewarmRuntime(prewarmRequest);
        assertNotNull(prewarm);
        assertTrue(prewarm.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, ""),
                prewarm.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
        assertTrue(prewarm.getBoolean("started"));
        assertEquals("ready", prewarm.getString("status"));
        assertEquals(runtimePid, prewarm.getInt(NodeJsRuntimeContract.KEY_PID, -1));
        assertNativeValue(prewarm, "process_runtime.healthy", "true");
        assertNativeValue(prewarm, "process_runtime.persistent_enabled", "true");
    }

    @Test
    public void x3d_02_realCjsAndEsmExecuteThroughPublishedBinder() throws Exception {
        LinkedHashMap<String, String> cjsFiles = new LinkedHashMap<>();
        cjsFiles.put(
                "main.cjs",
                "'use strict'; console.log('x3d.cjs=' + (6 * 7));\n"
        );
        try (WorkspaceInvocation invocation = execute(
                "cjs",
                "main.cjs",
                cjsFiles,
                false
        )) {
            assertSucceeded(invocation.result, "x3d.cjs=42");
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.assertWorkspaceOutputCommitted();
        }

        LinkedHashMap<String, String> esmFiles = new LinkedHashMap<>();
        esmFiles.put(
                "main.mjs",
                "const value = await import('./value.mjs');\n" +
                        "console.log('x3d.esm=' + value.answer);\n"
        );
        esmFiles.put("value.mjs", "export const answer = 42;\n");
        try (WorkspaceInvocation invocation = execute(
                "esm",
                "main.mjs",
                esmFiles,
                false
        )) {
            assertSucceeded(invocation.result, "x3d.esm=42");
            assertEquals("managed ESM should not print an internal VM Modules warning",
                    "", invocation.result.getString(NodeJsRuntimeContract.KEY_STDERR, ""));
            assertEquals("managed ESM should not stream an internal VM Modules warning",
                    "", invocation.callback.stderr());
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.assertWorkspaceOutputCommitted();
        }
    }

    @Test
    public void m18_experimentalNoticesKeepWarningEventsAndOtherDiagnostics() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("main.cjs", """
                const assert = require('node:assert/strict');
                const warnings = [];
                process.on('warning', warning => warnings.push(warning));
                process.emitWarning('m18.experimental.string', 'ExperimentalWarning');
                const experimental = new Error('m18.experimental.error');
                experimental.name = 'ExperimentalWarning';
                process.emitWarning(experimental);
                process.emitWarning('m18.visible.warning', {code: 'AUTOJS6_TEST_WARNING'});
                process.emitWarning('m18.visible.deprecation', {
                  type: 'DeprecationWarning', code: 'DEP_AUTOJS6_TEST'
                });
                console.error('m18.visible.stderr');
                setImmediate(async () => {
                  assert.deepEqual(warnings.map(warning => warning.name), [
                    'ExperimentalWarning', 'ExperimentalWarning', 'Warning', 'DeprecationWarning'
                  ]);
                  assert.equal(warnings[0].message, 'm18.experimental.string');
                  assert.equal(warnings[1], experimental);
                  assert.equal(warnings[2].code, 'AUTOJS6_TEST_WARNING');
                  const worker = new (require('node:worker_threads').Worker)('./warnings-worker.cjs');
                  const workerWarnings = await new Promise((resolve, reject) => {
                    worker.once('message', resolve);
                    worker.once('error', reject);
                  });
                  assert.deepEqual(workerWarnings, ['ExperimentalWarning', 'Warning']);
                  console.log('m18.warning.events=PASS');
                });
                """);
        files.put("warnings-worker.cjs", """
                const {parentPort} = require('node:worker_threads');
                const warnings = [];
                process.on('warning', warning => warnings.push(warning.name));
                process.emitWarning('m18.experimental.worker', 'ExperimentalWarning');
                process.emitWarning('m18.visible.worker');
                setImmediate(() => parentPort.postMessage(warnings));
                """);
        // Exercise separate environments in the persistent process as well as
        // both terminal aggregation and the live stderr callback.
        for (int index = 0; index < 2; index++) {
            try (WorkspaceInvocation invocation = execute(
                    "warnings-" + index, "main.cjs", files, new LinkedHashMap<>(),
                    false, null, null, SCRIPT_TIMEOUT_MS, false, Collections.emptyList(), true
            )) {
                assertSucceeded(invocation.result, "m18.warning.events=PASS");
                for (String stderr : new String[]{
                        invocation.result.getString(NodeJsRuntimeContract.KEY_STDERR, ""),
                        invocation.callback.stderr()
                }) {
                    assertFalse(stderr, stderr.contains("m18.experimental."));
                    assertTrue(stderr, stderr.contains("m18.visible.warning"));
                    assertTrue(stderr, stderr.contains("m18.visible.deprecation"));
                    assertTrue(stderr, stderr.contains("m18.visible.stderr"));
                    assertTrue(stderr, stderr.contains("m18.visible.worker"));
                }
                invocation.callback.assertOneStartedAndOneTerminalEvent();
            }
        }
    }

    @Test
    public void m18_fileAndCompressionStreamsRoundTripAndAbort() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("main.mjs", """
                import assert from 'node:assert/strict';
                import fs from 'node:fs';
                import {pipeline} from 'node:stream/promises';
                import {Readable} from 'node:stream';
                import zlib from 'node:zlib';
                import profile from './profile.cjs';
                assert.equal(profile.featureFlags.fs_streams.status, 'stable');
                assert.equal(profile.featureFlags.zlib_streams.status, 'stable');
                const data = Buffer.alloc(128 * 1024);
                for (let i = 0; i < data.length; ++i) data[i] = i * 31 % 251;
                fs.writeFileSync('input.bin', data);
                for (const [compress, decompress] of [
                  [zlib.createGzip, zlib.createGunzip],
                  [zlib.createDeflate, zlib.createInflate],
                  [zlib.createBrotliCompress, zlib.createBrotliDecompress]
                ]) {
                  const input = fs.createReadStream('input.bin', {highWaterMark: 4093});
                  const compressed = fs.createWriteStream('compressed.bin', {highWaterMark: 2048});
                  await pipeline(input, compress(), compressed);
                  assert.ok(input.destroyed && compressed.destroyed);
                  await pipeline(fs.createReadStream('compressed.bin'), decompress(), fs.createWriteStream('output.bin'));
                  assert.deepEqual(fs.readFileSync('output.bin'), data);
                }
                const controller = new AbortController();
                const slow = Readable.from((async function* () {
                  for (let i = 0; i < 100; ++i) {
                    yield data.subarray(0, 4096);
                    await new Promise(resolve => setTimeout(resolve, 5));
                  }
                })());
                const destination = fs.createWriteStream('aborted.bin');
                const abortTimer = setTimeout(() => controller.abort(), 15);
                await assert.rejects(pipeline(slow, zlib.createGzip(), destination, {signal: controller.signal}),
                  error => error.name === 'AbortError');
                clearTimeout(abortTimer);
                assert.ok(slow.destroyed && destination.destroyed);
                // A cancelled pipeline must not prevent a subsequent file stream.
                await pipeline(Readable.from([data]), fs.createWriteStream('recovered.bin'));
                assert.deepEqual(fs.readFileSync('recovered.bin'), data);
                console.log('m18.streams=PASS');
                """);
        files.put("profile.cjs", "module.exports = require('autojs6:profile');\n");
        try (WorkspaceInvocation invocation = execute("streams", "main.mjs", files, false)) {
            assertSucceeded(invocation.result, "m18.streams=PASS");
            invocation.assertWorkspaceOutputCommitted();
        }
    }

    @Test
    public void m18_vmContextsModulesAndTimeoutRemainUsable() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("main.mjs", """
                import assert from 'node:assert/strict';
                import vm from 'node:vm';
                import profile from './profile.cjs';
                assert.equal(profile.featureFlags.vm.status, 'stable');
                const context = vm.createContext({value: 40});
                const script = new vm.Script('value += 2; value');
                assert.equal(script.runInContext(context), 42);
                assert.equal(context.value, 42);
                assert.equal(vm.runInNewContext('typeof value'), 'undefined');
                assert.equal(vm.compileFunction('return value + n', ['n'], {parsingContext: context})(1), 43);
                assert.throws(() => vm.runInContext('while (true) {}', context, {timeout: 20}),
                  error => error.code === 'ERR_SCRIPT_EXECUTION_TIMEOUT');
                assert.equal(vm.runInContext('value', context), 42);
                const dependency = new vm.SyntheticModule(['value'], function() {
                  this.setExport('value', 21);
                }, {context});
                const module = new vm.SourceTextModule('import {value} from "dependency"; export const answer = value * 2;', {context});
                await module.link(name => { assert.equal(name, 'dependency'); return dependency; });
                await module.evaluate({timeout: 1000});
                assert.equal(module.namespace.answer, 42);
                console.log('m18.vm=PASS');
                """);
        files.put("profile.cjs", "module.exports = require('autojs6:profile');\n");
        try (WorkspaceInvocation invocation = execute("vm", "main.mjs", files, false)) {
            assertSucceeded(invocation.result, "m18.vm=PASS");
            invocation.callback.assertOneStartedAndOneTerminalEvent();
        }
    }

    @Test
    public void m18_missingCapabilitiesExplainDeclarationsWithoutRequiringAProfile() throws Exception {
        String[] projects = {
                null,
                "{\"node\":{\"profile\":\"pro_compat_opt_in\",\"permissions\":[]}}",
                "{\"node\":{\"permissions\":[\"screen_capture\",\"media\",\"media.metadata\"]}}"
        };
        for (int index = 0; index < projects.length; index++) {
            String source = """
                    (async () => {
                      const bridge = require('autojs6:bridge');
                      let dispatched = 0;
                      bridge.__test.setTransport({postMessage(message) {
                        const request = JSON.parse(message); ++dispatched;
                        const result = request.module === 'media_projection'
                          ? {__autojs6ScreenCapturerHandle: 'fixture', width: 1, height: 1}
                          : {schema: 'autojs6-node-mediainfo-snapshot-v1', sections: {audio: []}};
                        setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({id: request.id, ok: true, result})));
                      }});
                      const allowed = CASE_INDEX === 2;
                      for (const [call, capability] of [
                        [() => require('images').requestScreenCapture(), 'screen_capture'],
                        [() => require('mediainfo').read('sample.wav'), 'media.metadata']
                      ]) {
                        let error;
                        try { await call(); } catch (caught) { error = caught; }
                        if (allowed) {
                          if (error) throw error;
                        } else {
                          if (!error || error.code !== 'ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED') throw new Error('Missing capability error: ' + error);
                          if (!error.message.includes('declare node.permissions:') || !error.message.includes(capability)) throw error;
                          if (error.message.includes('required profile:') || error.profileSelectionHint.profileSelectionRequired) throw error;
                        }
                      }
                      if (dispatched !== (allowed ? 2 : 0)) throw new Error('Unexpected dispatch count: ' + dispatched);
                      console.log('m18.declaration-hint=PASS');
                    })().catch(error => { console.error(error.stack); process.exitCode = 1; });
                    """.replace("CASE_INDEX", Integer.toString(index));
            LinkedHashMap<String, String> files = new LinkedHashMap<>();
            files.put("main.cjs", source);
            if (projects[index] != null) {
                files.put("project.json", projects[index]);
            }
            try (WorkspaceInvocation invocation = execute("capability-hint-" + index, "main.cjs", files, false)) {
                assertSucceeded(invocation.result, "m18.declaration-hint=PASS");
            }
        }
    }

    /** M18.2: the autojs6:profile diagnostics must describe the runtime as it actually behaves. */
    @Test
    public void m18_profileDiagnosticsDescribeTheActualRuntime() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("main.cjs", """
                const assert = require('node:assert/strict');
                const profile = require('autojs6:profile');
                assert.equal(profile.engineVersion, 'autojs6-node-profile-v1.3');
                // File access is bounded by Android permissions plus the /proc, /sys and /dev denial, not by a sandbox root.
                assert.equal(profile.scopedFs, false);
                assert.equal(profile.filesystemProfile.mode, 'android_file_access_with_sensitive_roots_denied');
                assert.equal(profile.filesystemProfile.safeProfileScoped, false);
                assert.equal(profile.filesystemProfile.androidSharedStorage, true);
                assert.equal(profile.filesystemProfile.advancedApis.streams, 'stable');
                assert.equal(profile.processParityProfile.chdir, 'existing_directories_except_sensitive_roots');
                assert.ok(!profile.processParityProfile.androidUnsupported.includes('unrestricted_chdir'));
                assert.equal(profile.processParityProfile.versions, 'native');
                assert.equal(process.versions.node, process.version.slice(1));
                assert.equal(typeof process.resourceUsage().userCPUTime, 'number');
                assert.equal(profile.stdlibProfile.moduleStatus['zlib/promises'], 'not_a_node_builtin');
                assert.ok(!profile.stdlibProfile.androidUnsupported.includes('zlib/promises'));
                assert.equal(profile.workerThreadsProfile.messageChannel, 'stable');
                assert.equal(profile.processWorkerReplacementProfile.secondExecutionSlot, 'process_pool_two_slots');
                assert.equal(profile.processWorkerReplacementProfile.executionMode, 'not_applicable_runs_inside_execution');
                assert.equal(profile.packagedCapabilityProfile.longRunning, 'interactive_long_running_via_execution_mode');
                assert.equal(profile.wasiProfile.status, 'denied_by_decision');
                assert.deepEqual(profile.wasiProfile.targetProfiles, []);
                assert.equal(profile.nativeAddonProfile.status, 'unsupported_by_policy');
                assert.equal(profile.esmLoaderProfile.dataUrlImports, 'inline_js_json_only');
                assert.equal(profile.packageManagerProfile.registryDownload, 'denied_by_policy');
                assert.equal(profile.packageManagerProfile.terminalCli, 'host_terminal_node_npm_corepack_launcher');
                for (const name of ['processParityProfile', 'packagedCapabilityProfile', 'processWorkerReplacementProfile', 'wasiProfile', 'nativeAddonProfile']) {
                  assert.ok(!/(_partial|_reserved|closed_by_p)/.test(profile[name].status), name + '=' + profile[name].status);
                }
                console.log('m18.profile=PASS');
                """);
        try (WorkspaceInvocation invocation = execute("profile-diagnostics", "main.cjs", files, false)) {
            assertSucceeded(invocation.result, "m18.profile=PASS");
        }
    }

    /** M18.2: every host-independent sample project runs from its own files and prints its expected output. */
    @Test
    public void m18_sampleProjectsPrintTheirExpectedOutput() throws Exception {
        AssetManager assets = InstrumentationRegistry.getInstrumentation().getContext().getAssets();
        String[] names = {
                "packaged-esm", "packaged-dynamic-import", "require-esm", "compile-cache",
                "inspector-debug", "cpu-profile", "heap-snapshot", "wasm-basic", "wasm-plugin",
                "js-plugin-ui", "desktop-parity-suite", "pro-parity-suite"
        };
        for (String name : names) {
            LinkedHashMap<String, String> files = readSampleProject(assets, name);
            JSONObject project = new JSONObject(files.get("project.json"));
            JSONObject node = project.optJSONObject("node");
            long timeout = Math.max(node == null ? 0L : node.optLong("timeoutMs", 0L), SCRIPT_TIMEOUT_MS);
            // Workers stay enabled, as the plugin defaults when the host sends no override.
            try (WorkspaceInvocation invocation = execute("sample-" + name, project.optString("main", "main.cjs"), files,
                    new LinkedHashMap<>(), false, null, null, timeout, false, Collections.emptyList(), true)) {
                Bundle result = invocation.result;
                String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
                assertTrue(name + " failed: " + result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "")
                                + '\n' + result.getString(NodeJsRuntimeContract.KEY_STDERR, "") + '\n' + stdout,
                        result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
                assertEquals(name + " exit code", 0, result.getInt(NodeJsRuntimeContract.KEY_EXIT_CODE, -1));
                for (String line : files.get("expected-output.txt").split("\\r?\\n")) {
                    String expected = line.trim();
                    // Lines with <...> placeholders describe host-dependent outcomes.
                    if (expected.isEmpty() || expected.contains("<")) continue;
                    assertTrue(name + " stdout is missing '" + expected + "':" + '\n' + stdout, stdout.contains(expected));
                }
            }
        }
    }

    private static LinkedHashMap<String, String> readSampleProject(AssetManager assets, String name) throws IOException {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        java.util.ArrayDeque<String> pending = new java.util.ArrayDeque<>();
        pending.add(name);
        while (!pending.isEmpty()) {
            String entry = pending.removeFirst();
            String[] children = assets.list(entry);
            if (children != null && children.length > 0) {
                for (String child : children) pending.addLast(entry + "/" + child);
                continue;
            }
            try (InputStream input = assets.open(entry); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[8192];
                int count;
                while ((count = input.read(chunk)) >= 0) buffer.write(chunk, 0, count);
                files.put(entry.substring(name.length() + 1), new String(buffer.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        assertTrue("sample " + name + " is missing project.json", files.containsKey("project.json"));
        assertTrue("sample " + name + " is missing expected-output.txt", files.containsKey("expected-output.txt"));
        return files;
    }

    /** M18.2: the media playback session facade and the media_store facade shape requests, permissions and results. */
    @Test
    public void m18_mediaPlaybackAndMediaStoreFacadesShapeBridgeRequests() throws Exception {
        String[] projects = {
                """
                {"node":{"permissions":["media","media.playback","media.library","media.library.mutate"]}}""",
                """
                {"node":{"permissions":["media","media.audio","media.library"]}}"""
        };
        for (int index = 0; index < projects.length; index++) {
            String source = """
                    (async () => {
                      const assert = require('node:assert/strict');
                      const bridge = require('autojs6:bridge');
                      const requests = [];
                      let stopped = true;
                      const item = {schema: 'autojs6-node-media-store-item-v1', collection: 'audio', id: 7, uri: 'content://media/external/audio/media/7', displayName: 'tone.wav'};
                      bridge.__test.setTransport({postMessage(message) {
                        const request = JSON.parse(message);
                        requests.push(request);
                        const target = request.args.find(arg => arg && typeof arg === 'object' && typeof arg.id === 'string');
                        let result = null;
                        if (request.module === 'media' && request.method === 'play') {
                          stopped = false;
                          result = {schema: 'autojs6-node-media-playback-v1', id: 'media-playback-1', path: '/host/' + request.args[0], active: true, playing: true, ended: false,
                            looping: request.args[1].looping === true, volume: request.args[1].volume === undefined ? 1 : request.args[1].volume, durationMs: 1500, positionMs: 0};
                        } else if (request.module === 'media') {
                          if (request.method === 'stop') stopped = true;
                          result = {schema: 'autojs6-node-media-playback-v1', id: target ? target.id : 'media-playback-1', path: '/host/tone.wav', active: !stopped,
                            playing: request.method === 'resume', ended: false, looping: false, volume: 0.4, durationMs: 1500, positionMs: request.method === 'seekTo' ? request.args[0] : 10};
                        } else if (request.method === 'capabilities') {
                          result = {schema: 'autojs6-node-media-store-capabilities-v1', collections: ['audio', 'images', 'video', 'downloads']};
                        } else if (request.method === 'query') {
                          result = {schema: 'autojs6-node-media-store-query-v1', collection: request.args[0], count: 1, limit: request.args[1].limit, offset: 0, items: [item]};
                        } else if (request.method === 'get') {
                          result = request.args[1] === 7 ? item : null;
                        } else if (request.method === 'delete') {
                          result = {schema: 'autojs6-node-media-store-delete-v1', collection: request.args[0], id: request.args[1], deleted: true};
                        } else if (request.method === 'scanFile') {
                          result = {schema: 'autojs6-node-media-store-scan-v1', path: request.args[0], uri: null, scanned: false};
                        }
                        setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({id: request.id, ok: true, result})));
                      }});
                      const media = require('media');
                      const store = require('media_store');
                      const compat = require('autojs6:compat');
                      const last = () => requests[requests.length - 1];
                      if (CASE_INDEX === 0) {
                        const session = await media.play('tone.wav', {volume: 0.4, looping: false});
                        assert.ok(Object.isFrozen(session) && Object.isFrozen(media) && Object.isFrozen(store));
                        assert.deepEqual([session.id, session.path, session.volume, session.looping], ['media-playback-1', '/host/tone.wav', 0.4, false]);
                        assert.deepEqual(last().args, ['tone.wav', {volume: 0.4, looping: false}]);
                        const paused = await session.pause();
                        assert.ok(Object.isFrozen(paused) && paused.active && !paused.playing);
                        assert.deepEqual(last().args, [{id: 'media-playback-1'}]);
                        assert.equal((await session.seekTo(500)).positionMs, 500);
                        assert.deepEqual(last().args, [500, {id: 'media-playback-1'}]);
                        assert.equal((await media.resume({session})).playing, true);
                        assert.equal((await media.resume({session: 'media-playback-1'})).id, 'media-playback-1');
                        assert.equal((await session.stop()).active, false);
                        await media.getPlaybackStatus();
                        assert.deepEqual(last().args, []);
                        await assert.rejects(media.play(''), error => error.code === 'ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED');
                        await assert.rejects(media.play('tone.wav', {volume: 2}), TypeError);
                        await assert.rejects(media.seekTo(-1), TypeError);
                        await assert.rejects(media.pause({session: 'bad id'}), TypeError);
                        const compatSession = await compat.media.playMusic('tone.wav', 0.2, true);
                        assert.equal(compatSession.id, 'media-playback-1');
                        assert.deepEqual(last().args, ['tone.wav', {volume: 0.2, looping: true}]);
                        assert.equal(await compat.media.isMusicPlaying(), false);
                        assert.equal(await compat.media.getMusicDuration(), 1500);
                        assert.equal(await compat.media.getMusicCurrentPosition(), 10);
                        assert.equal(await compat.media.pauseMusic(), undefined);
                        assert.equal(await compat.media.stopMusic(), undefined);
                        assert.equal(await compat.media.getMusicDuration(), 0);
                        assert.equal(await compat.media.getMusicCurrentPosition(), -1);
                        assert.equal(await compat.media.scanFile('tone.wav'), false);
                        const capabilities = await store.capabilities();
                        assert.equal(capabilities.schema, 'autojs6-node-media-store-capabilities-v1');
                        const query = await store.query('audio', {filter: {displayName: {startsWith: 'tone'}, ownedOnly: true}, sort: '-dateAdded', limit: 5, columns: ['displayName'], timeoutMs: 2000});
                        assert.deepEqual(last().args, ['audio', {filter: {displayName: {startsWith: 'tone'}, ownedOnly: true}, sort: '-dateAdded', limit: 5, columns: ['displayName']}]);
                        assert.equal(last().timeoutMs, 2000);
                        assert.ok(Object.isFrozen(query) && Object.isFrozen(query.items[0]));
                        assert.equal((await store.get('audio', query.items[0])).displayName, 'tone.wav');
                        assert.deepEqual(last().args, ['audio', 7]);
                        assert.equal(await store.get('audio', '8'), null);
                        assert.equal((await store.delete('audio', query.items[0])).deleted, true);
                        assert.equal((await store.remove('audio', 7)).id, 7);
                        assert.equal((await store.scanFile('tone.wav', {mimeType: 'audio/wav'})).scanned, false);
                        assert.deepEqual(last().args, ['tone.wav', {mimeType: 'audio/wav'}]);
                        await assert.rejects(store.query('playlists'), TypeError);
                        await assert.rejects(store.get('audio', -1), TypeError);
                        await assert.rejects(store.exportFile('audio', 7, ''), error => error.code === 'ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED');
                        const seen = requests.map(request => request.module + '.' + request.method + ':' + request.permissions.join('+'));
                        for (const expected of [
                          'media.play:media+media.playback',
                          'media.pause:media+media.playback',
                          'media.seekTo:media+media.playback',
                          'media.getPlaybackStatus:media+media.playback',
                          'media_store.capabilities:media+media.library',
                          'media_store.query:media+media.library',
                          'media_store.get:media+media.library',
                          'media_store.delete:media+media.library+media.library.mutate',
                          'media_store.scanFile:media+media.library'
                        ]) {
                          assert.ok(seen.includes(expected), expected + ' not in ' + seen.join(' '));
                        }
                        assert.ok(!seen.some(entry => entry.startsWith('media_store.exportFile')));
                      } else {
                        await assert.rejects(media.play('tone.wav'), error => error.code === 'ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED' && error.message.includes('media.playback'));
                        await assert.rejects(compat.media.playMusic('tone.wav'), error => error.code === 'ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED');
                        await assert.rejects(store.delete('audio', 7), error => error.code === 'ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED' && error.message.includes('media.library.mutate'));
                        await assert.rejects(store.insert('audio', {displayName: 'a.wav', mimeType: 'audio/wav', source: 'tone.wav'}), error => error.code === 'ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED');
                        assert.equal((await store.capabilities()).schema, 'autojs6-node-media-store-capabilities-v1');
                        assert.equal(typeof media.getAudioStreamInfo, 'function');
                        assert.equal(requests.length, 1);
                      }
                      console.log('m18.media-facade=PASS');
                    })().catch(error => { console.error(error.stack); process.exitCode = 1; });
                    """.replace("CASE_INDEX", Integer.toString(index));
            LinkedHashMap<String, String> files = new LinkedHashMap<>();
            files.put("main.cjs", source);
            files.put("project.json", projects[index]);
            try (WorkspaceInvocation invocation = execute("media-facade-" + index, "main.cjs", files, false)) {
                assertSucceeded(invocation.result, "m18.media-facade=PASS");
            }
        }
    }

    /** M20.2: module loading follows Android file access like Node; /proc, /sys and /dev stay denied. */
    @Test
    public void m20_loaderFollowsAndroidFileAccessForAbsolutePathsAndFileUrls() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("y.cjs", "module.exports = 'cjs-abs';");
        files.put("x.mjs", "export const v = 'esm-abs';");
        files.put("main.cjs", """
                const fs = require('node:fs');
                const path = require('node:path');
                const { pathToFileURL } = require('node:url');
                const { Worker } = require('node:worker_threads');
                const profile = require('autojs6:profile').esmLoaderProfile;
                console.log('m20.profile=' + profile.absolutePathImports + '/' + profile.fileUrlImports + '/' + profile.workingDirectoryEscape);
                // A directory outside the workspace root, next to it.
                const outside = path.join(path.dirname(__dirname), 'm20-outside-' + process.pid);
                fs.mkdirSync(outside, { recursive: true });
                fs.writeFileSync(path.join(outside, 'sibling.json'), '{"value":7}');
                fs.writeFileSync(path.join(outside, 'lib.cjs'), "module.exports = { where: 'outside-cjs', sibling: require('./sibling.json').value };");
                fs.writeFileSync(path.join(outside, 'lib.mjs'), "export const where = 'outside-esm'; export { default as fromCjs } from './lib.cjs';");
                fs.writeFileSync(path.join(outside, 'static.mjs'),
                  'import { v } from ' + JSON.stringify(path.join(__dirname, 'x.mjs')) + ';' +
                  ' import { where } from ' + JSON.stringify(pathToFileURL(path.join(outside, 'lib.mjs')).href) + ';' +
                  ' export const combined = v + "+" + where;');
                fs.writeFileSync(path.join(outside, 'worker.cjs'),
                  "require('node:worker_threads').parentPort.postMessage(require(__dirname + '/lib.cjs').where);");
                (async () => {
                  try {
                    console.log('m20.require.abs=' + require(path.join(__dirname, 'y.cjs')));
                    console.log('m20.require.resolve.abs=' + (require.resolve(path.join(__dirname, 'y.cjs')) === path.join(__dirname, 'y.cjs')));
                    const outsideCjs = require('../' + path.basename(outside) + '/lib.cjs');
                    console.log('m20.require.outside=' + outsideCjs.where + '/' + outsideCjs.sibling);
                    console.log('m20.require.outside.abs=' + (require(path.join(outside, 'lib.cjs')) === outsideCjs));
                    console.log('m20.import.abs=' + (await import(path.join(__dirname, 'x.mjs'))).v);
                    console.log('m20.import.fileurl=' + (await import(pathToFileURL(path.join(__dirname, 'x.mjs')).href)).v);
                    const outsideEsm = await import(pathToFileURL(path.join(outside, 'lib.mjs')).href);
                    console.log('m20.import.outside=' + outsideEsm.where + '/' + outsideEsm.fromCjs.sibling);
                    console.log('m20.import.static=' + (await import(path.join(outside, 'static.mjs'))).combined);
                    const workerValue = await new Promise((resolve, reject) => {
                      const worker = new Worker(path.join(outside, 'worker.cjs'));
                      worker.once('message', resolve);
                      worker.once('error', reject);
                    });
                    console.log('m20.worker.outside=' + workerValue);
                    const denied = {
                      'require.proc': () => require('/proc/self/status'),
                      'import.proc': () => import('/proc/self/status'),
                      'import.fileurl.proc': () => import('file:///proc/self/status'),
                      'import.sys': () => import('file:///sys/kernel'),
                      'worker.dev': () => new Worker('/dev/null')
                    };
                    for (const [label, attempt] of Object.entries(denied)) {
                      let code = 'none';
                      try { await attempt(); } catch (error) { code = error.autojs6Code || error.code || error.name; }
                      console.log('m20.denied.' + label + '=' + code);
                    }
                    console.log('m20.loader=PASS');
                  } finally {
                    fs.rmSync(outside, { recursive: true, force: true });
                  }
                })().catch((error) => { console.error(error && error.stack || error); process.exitCode = 1; });
                """);
        try (WorkspaceInvocation invocation = execute("m20-loader", "main.cjs", files,
                new LinkedHashMap<>(), false, null, null, SCRIPT_TIMEOUT_MS, false, Collections.emptyList(), true)) {
            Bundle result = invocation.result;
            String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
            assertTrue("m20 loader failed: " + result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "")
                            + " / " + result.getString(NodeJsRuntimeContract.KEY_STDERR, "") + " / " + stdout,
                    result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            for (String expected : new String[] {
                    "m20.profile=allowed_within_android_app_permissions/local_file_urls/allowed_within_android_app_permissions",
                    "m20.require.abs=cjs-abs",
                    "m20.require.resolve.abs=true",
                    "m20.require.outside=outside-cjs/7",
                    "m20.require.outside.abs=true",
                    "m20.import.abs=esm-abs",
                    "m20.import.fileurl=esm-abs",
                    "m20.import.outside=outside-esm/7",
                    "m20.import.static=esm-abs+outside-esm",
                    "m20.worker.outside=outside-cjs",
                    "m20.denied.require.proc=ERR_AUTOJS6_FS_PATH_ESCAPE",
                    "m20.denied.import.proc=ERR_AUTOJS6_FS_PATH_ESCAPE",
                    "m20.denied.import.fileurl.proc=ERR_AUTOJS6_FS_PATH_ESCAPE",
                    "m20.denied.import.sys=ERR_AUTOJS6_FS_PATH_ESCAPE",
                    "m20.denied.worker.dev=ERR_AUTOJS6_FS_PATH_ESCAPE",
                    "m20.loader=PASS"
            }) {
                assertTrue("stdout is missing '" + expected + "': " + stdout, stdout.contains(expected));
            }
        }
    }

    /** M20.2: worker messages and fs watchers follow native Node limits; only Android memory bounds them. */
    @Test
    public void m20_workerMessagesAndFsWatchersFollowNativeLimits() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("echo.cjs", "const { parentPort, workerData } = require('node:worker_threads');"
                + " parentPort.on('message', (m) => parentPort.postMessage({ length: m.length, workerData: workerData.length, big: new Uint8Array(300000) }));");
        files.put("main.cjs", """
                const assert = require('node:assert/strict');
                const fs = require('node:fs');
                const path = require('node:path');
                const wt = require('node:worker_threads');
                const profile = require('autojs6:profile');
                assert.equal(wt.policy.messageBudget, 'native_structured_clone');
                assert.equal(profile.filesystemProfile.limits.watchers, 'native_unbounded');
                assert.equal(profile.processWorkerReplacementProfile.messageBudget, 'native_structured_clone');
                (async () => {
                  const worker = new wt.Worker(path.join(__dirname, 'echo.cjs'), { workerData: new Uint8Array(200000) });
                  try {
                    const replies = [];
                    const done = new Promise((resolve, reject) => {
                      worker.on('message', (reply) => { replies.push(reply); if (replies.length === 40) resolve(); });
                      worker.on('error', reject);
                    });
                    // 40 messages in one tick, the first a 1 MiB payload: the old 64 KB / 32-queued caps threw here.
                    worker.postMessage(new Uint8Array(1048576));
                    for (let index = 1; index < 40; index += 1) worker.postMessage(new Uint8Array(1000));
                    await done;
                    assert.equal(replies[0].length, 1048576);
                    assert.equal(replies[0].workerData, 200000);
                    assert.equal(replies[0].big.length, 300000);
                    console.log('m20.worker.messages=' + replies.length);
                  } finally {
                    await worker.terminate();
                  }
                  const dir = fs.mkdtempSync(path.join(__dirname, 'watch-'));
                  const watchers = [];
                  try {
                    // 24 watchers: the old cap threw ERR_AUTOJS6_FS_WATCH_LIMIT at the 17th.
                    for (let index = 0; index < 24; index += 1) {
                      const file = path.join(dir, 'file-' + index + '.txt');
                      fs.writeFileSync(file, 'x');
                      watchers.push(fs.watch(file, () => {}));
                    }
                    console.log('m20.fs.watchers=' + watchers.length);
                    const busy = path.join(dir, 'busy.txt');
                    fs.writeFileSync(busy, 'start');
                    let events = 0;
                    watchers.push(fs.watch(busy, () => { events += 1; }));
                    // 100 change events within a second: the old quota closed the watcher after 64.
                    for (let index = 0; index < 100; index += 1) {
                      fs.writeFileSync(busy, 'tick ' + index);
                      await new Promise((resolve) => setImmediate(resolve));
                    }
                    const deadline = Date.now() + 3000;
                    while (events < 65 && Date.now() < deadline) await new Promise((resolve) => setTimeout(resolve, 20));
                    console.log('m20.fs.watch.events=' + (events >= 65 ? 'unbounded' : String(events)));
                  } finally {
                    for (const watcher of watchers) watcher.close();
                    fs.rmSync(dir, { recursive: true, force: true });
                  }
                  console.log('m20.quotas=PASS');
                })().catch((error) => { console.error(error && error.stack || error); process.exitCode = 1; });
                """);
        try (WorkspaceInvocation invocation = execute("m20-quotas", "main.cjs", files,
                new LinkedHashMap<>(), false, null, null, SCRIPT_TIMEOUT_MS, false, Collections.emptyList(), true)) {
            Bundle result = invocation.result;
            String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
            assertTrue("m20 quotas failed: " + result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "")
                            + " / " + result.getString(NodeJsRuntimeContract.KEY_STDERR, "") + " / " + stdout,
                    result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            for (String expected : new String[] {
                    "m20.worker.messages=40",
                    "m20.fs.watchers=24",
                    "m20.fs.watch.events=unbounded",
                    "m20.quotas=PASS"
            }) {
                assertTrue("stdout is missing '" + expected + "': " + stdout, stdout.contains(expected));
            }
        }
    }

    /** M20.2: the fs wrapper no longer rejects its own option restrictions; stream, watch, cp, glob and FileHandle options follow Node 24. */
    @Test
    public void m20_fsWrapperOptionsFollowNode() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("main.cjs", """
                const assert = require('node:assert/strict');
                const fs = require('node:fs');
                const fsp = require('node:fs/promises');
                const path = require('node:path');
                const profile = require('autojs6:profile');
                assert.equal(profile.filesystemProfile.advancedApis.recursiveWatch, 'native');
                (async () => {
                  const dir = fs.mkdtempSync(path.join(__dirname, 'fsopt-'));
                  try {
                    const file = path.join(dir, 'a.txt');
                    fs.writeFileSync(file, 'hello');
                    // Custom fs option, prototype-inherited fd option and non r/rs flags: the old wrapper threw ERR_AUTOJS6_EMBEDDED_NODE_UNSUPPORTED_OPTION.
                    let opened = 0;
                    const custom = { open: (p, f, m, cb) => { opened += 1; fs.open(p, f, m, cb); }, read: fs.read, close: fs.close };
                    let data = '';
                    for await (const chunk of fs.createReadStream(file, { fs: custom, encoding: 'utf8' })) data += chunk;
                    assert.equal(data, 'hello');
                    assert.equal(opened, 1);
                    const inherited = Object.create({ fd: fs.openSync(file, 'r'), encoding: 'utf8' });
                    let viaFd = '';
                    for await (const chunk of fs.createReadStream(null, inherited)) viaFd += chunk;
                    assert.equal(viaFd, 'hello');
                    let rw = '';
                    for await (const chunk of fs.createReadStream(file, { flags: 'r+', encoding: 'utf8' })) rw += chunk;
                    assert.equal(rw, 'hello');
                    const logPath = path.join(dir, 'log.txt');
                    const utf8 = new fs.Utf8Stream(Object.create({ dest: logPath, sync: true }));
                    utf8.write('line');
                    utf8.end();
                    await new Promise((resolve) => utf8.once('close', resolve));
                    assert.equal(fs.readFileSync(logPath, 'utf8'), 'line');
                    console.log('m20.fs.options.streams=PASS');
                    // Recursive watch: native on Linux since Node 20; the old wrapper rejected recursive=true.
                    fs.mkdirSync(path.join(dir, 'nested'));
                    const seen = [];
                    const watcher = fs.watch(dir, { recursive: true }, (eventType, filename) => { seen.push(String(filename)); });
                    let nestedEvent = false;
                    for (let attempt = 0; attempt < 8 && !nestedEvent; attempt += 1) {
                      await new Promise((resolve) => setTimeout(resolve, 250));
                      fs.writeFileSync(path.join(dir, 'nested', 'inner-' + attempt + '.txt'), 'x');
                      const deadline = Date.now() + 1000;
                      while (!nestedEvent && Date.now() < deadline) {
                        await new Promise((resolve) => setTimeout(resolve, 50));
                        nestedEvent = seen.some((name) => name.startsWith('nested/') || name.startsWith('nested' + path.sep));
                      }
                    }
                    watcher.close();
                    assert.ok(nestedEvent, 'recursive watch events: ' + seen.join(','));
                    console.log('m20.fs.options.watch=recursive');
                    // Async cp filters: awaited by fs.cp / fs.promises.cp; cpSync keeps Node's ERR_INVALID_RETURN_VALUE.
                    const src = path.join(dir, 'src');
                    fs.mkdirSync(path.join(src, 'deep'), { recursive: true });
                    fs.writeFileSync(path.join(src, 'keep.txt'), 'k');
                    fs.writeFileSync(path.join(src, 'skip.txt'), 's');
                    fs.writeFileSync(path.join(src, 'deep', 'd.txt'), 'd');
                    const filtered = [];
                    await fsp.cp(src, path.join(dir, 'dst'), { recursive: true, filter: async (source) => {
                      await new Promise((resolve) => setTimeout(resolve, 1));
                      filtered.push(path.basename(source));
                      return !source.endsWith('skip.txt');
                    } });
                    assert.ok(fs.existsSync(path.join(dir, 'dst', 'keep.txt')));
                    assert.ok(fs.existsSync(path.join(dir, 'dst', 'deep', 'd.txt')));
                    assert.ok(!fs.existsSync(path.join(dir, 'dst', 'skip.txt')));
                    assert.deepEqual(filtered.slice().sort(), ['d.txt', 'deep', 'keep.txt', 'skip.txt', 'src']);
                    await new Promise((resolve, reject) => fs.cp(src, path.join(dir, 'dst2'), { recursive: true, filter: async (source) => !source.endsWith('skip.txt') }, (error) => error ? reject(error) : resolve()));
                    assert.ok(fs.existsSync(path.join(dir, 'dst2', 'keep.txt')) && !fs.existsSync(path.join(dir, 'dst2', 'skip.txt')));
                    assert.throws(() => fs.cpSync(src, path.join(dir, 'dst3'), { recursive: true, filter: async () => true }), { code: 'ERR_INVALID_RETURN_VALUE' });
                    console.log('m20.fs.options.cp=PASS');
                    // Glob: parent segments, absolute patterns, literal '!' and exclude arrays follow Node; the old wrapper threw EPERM / UNSUPPORTED_OPTION.
                    assert.deepEqual(fs.globSync('../src/*.txt', { cwd: path.join(dir, 'dst') }).sort(), ['../src/keep.txt', '../src/skip.txt']);
                    assert.deepEqual(fs.globSync(path.join(src, '*.txt')).sort(), [path.join(src, 'keep.txt'), path.join(src, 'skip.txt')]);
                    assert.deepEqual(fs.globSync('!*.txt', { cwd: src }), []);
                    assert.deepEqual(fs.globSync('**/*.txt', { cwd: src, exclude: ['skip.txt'], followSymlinks: true }).sort(), ['deep/d.txt', 'keep.txt']);
                    const viaPromise = [];
                    for await (const entry of fsp.glob('../src/deep/*.txt', { cwd: path.join(dir, 'dst') })) viaPromise.push(entry);
                    assert.deepEqual(viaPromise, ['../src/deep/d.txt']);
                    console.log('m20.fs.options.glob=PASS');
                    // FileHandle web streams ignore type/encoding like Node; FileHandle streams always use the handle fd; Dir is Node's constructor.
                    const handle = await fsp.open(file, 'r');
                    const reader = handle.readableWebStream({ type: 'bytes', encoding: 'utf8' }).getReader();
                    let bytes = 0;
                    for (;;) { const { done, value } = await reader.read(); if (done) break; bytes += value.byteLength; }
                    await handle.close();
                    assert.equal(bytes, 5);
                    const handle2 = await fsp.open(file, 'r');
                    let viaHandle = '';
                    for await (const chunk of handle2.createReadStream({ encoding: 'utf8', fd: 999 })) viaHandle += chunk;
                    assert.equal(viaHandle, 'hello');
                    assert.throws(() => new fs.Dir(), { code: 'ERR_MISSING_ARGS' });
                    assert.equal(typeof fs.Stats, 'function');
                    console.log('m20.fs.options.handles=PASS');
                  } finally {
                    fs.rmSync(dir, { recursive: true, force: true });
                  }
                  console.log('m20.fs.options=PASS');
                })().catch((error) => { console.error(error && error.stack || error); process.exitCode = 1; });
                """);
        try (WorkspaceInvocation invocation = execute("m20-fs-options", "main.cjs", files, false)) {
            assertSucceeded(invocation.result, "m20.fs.options=PASS");
            String stdout = invocation.result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
            for (String expected : new String[] {
                    "m20.fs.options.streams=PASS",
                    "m20.fs.options.watch=recursive",
                    "m20.fs.options.cp=PASS",
                    "m20.fs.options.glob=PASS",
                    "m20.fs.options.handles=PASS"
            }) {
                assertTrue("stdout is missing '" + expected + "': " + stdout, stdout.contains(expected));
            }
        }
    }

    /** M20.2: the runtime keeps no module-source budget, a CommonJS entry sees Node's absolute paths, and mkdtemp keeps the caller's prefix spelling. */
    @Test
    public void m20_loaderBudgetAndEntryPathsFollowNode() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("main.cjs", """
                const assert = require('node:assert/strict');
                const fs = require('node:fs');
                const fsp = require('node:fs/promises');
                const path = require('node:path');
                (async () => {
                  // Node hands the entry an absolute __filename even though the host named it 'main.cjs'.
                  assert.ok(path.isAbsolute(__filename), __filename);
                  assert.equal(path.basename(__filename), 'main.cjs');
                  assert.equal(__dirname, path.dirname(__filename));
                  assert.equal(require.main, module);
                  assert.equal(require.main.filename, __filename);
                  assert.equal(require.main.id, '.');
                  assert.equal(process.argv[1], __filename);
                  assert.equal(require.resolve('./main.cjs'), __filename);
                  const dep = require('./lib/dep.cjs');
                  assert.equal(dep.id, path.join(__dirname, 'lib', 'dep.cjs'));
                  assert.equal(dep.parentId, '.');
                  assert.ok(dep.mainIsParent);
                  const esm = (await import('./lib/esm.mjs')).info;
                  assert.equal(esm.filename, path.join(__dirname, 'lib', 'esm.mjs'));
                  assert.equal(esm.main, false);
                  console.log('m20.entry.paths=PASS');
                  // mkdtemp returns the caller's prefix spelling plus the native suffix, in the requested encoding.
                  const outside = path.resolve(process.cwd(), '..', 'm20-loader-' + process.pid);
                  fs.mkdirSync(path.join(outside, 'real-parent'), { recursive: true });
                  const created = [];
                  try {
                    const rel = fs.mkdtempSync('tmp-');
                    assert.match(rel, /^tmp-[A-Za-z0-9]{6}$/);
                    assert.ok(fs.statSync(path.resolve(rel)).isDirectory());
                    created.push(rel);
                    const dotted = fs.mkdtempSync('./tmp-');
                    assert.ok(dotted.startsWith('./tmp-'), dotted);
                    created.push(dotted);
                    const abs = fs.mkdtempSync(path.join(__dirname, 'tmp-'));
                    assert.ok(abs.startsWith(path.join(__dirname, 'tmp-')), abs);
                    created.push(abs);
                    const viaPromise = await fsp.mkdtemp('tmp-');
                    assert.match(viaPromise, /^tmp-[A-Za-z0-9]{6}$/);
                    created.push(viaPromise);
                    const viaCallback = await new Promise((resolve, reject) => fs.mkdtemp('tmp-', (error, folder) => error ? reject(error) : resolve(folder)));
                    assert.match(viaCallback, /^tmp-[A-Za-z0-9]{6}$/);
                    created.push(viaCallback);
                    const asBuffer = fs.mkdtempSync('tmp-', { encoding: 'buffer' });
                    assert.ok(Buffer.isBuffer(asBuffer));
                    assert.match(asBuffer.toString(), /^tmp-[A-Za-z0-9]{6}$/);
                    created.push(asBuffer.toString());
                    const asHex = fs.mkdtempSync('tmp-', 'hex');
                    assert.match(Buffer.from(asHex, 'hex').toString(), /^tmp-[A-Za-z0-9]{6}$/);
                    created.push(Buffer.from(asHex, 'hex').toString());
                    const trailing = fs.mkdtempSync(path.join(outside, 'real-parent') + path.sep);
                    assert.equal(path.dirname(trailing), path.join(outside, 'real-parent'));
                    fs.symlinkSync('real-parent', path.join(outside, 'link-parent'));
                    const viaLink = fs.mkdtempSync(path.join(outside, 'link-parent', 'tmp-'));
                    assert.ok(viaLink.startsWith(path.join(outside, 'link-parent', 'tmp-')), viaLink);
                    assert.ok(fs.statSync(viaLink).isDirectory());
                    const disposable = fs.mkdtempDisposableSync('tmp-');
                    assert.match(disposable.path, /^tmp-[A-Za-z0-9]{6}$/);
                    disposable.remove();
                    assert.ok(!fs.existsSync(disposable.path));
                    assert.throws(() => fs.mkdtempSync('missing-dir/tmp-'), (error) => error.code === 'ENOENT' && error.autojs6Code === undefined);
                    assert.throws(() => fs.mkdtempSync('tmp-', { encoding: 'nope' }), { code: 'ERR_INVALID_ARG_VALUE' });
                    assert.throws(() => fs.mkdtempSync(42), { code: 'ERR_INVALID_ARG_TYPE' });
                    assert.throws(() => fs.mkdtempSync('tmp-' + String.fromCharCode(0)), { autojs6Code: 'ERR_AUTOJS6_FS_NUL_BYTE' });
                    console.log('m20.mkdtemp=PASS');
                    // No module-source budget: 22 MiB modules (three of them, 66 MiB in total) and 8300 modules load.
                    const bigSource = 'module.exports = 1; // ' + 'x'.repeat(22 * 1024 * 1024);
                    for (const name of ['big-a.cjs', 'big-b.cjs', 'big-c.cjs']) {
                      fs.writeFileSync(path.join(outside, name), bigSource);
                      assert.equal(require(path.join(outside, name)), 1);
                    }
                    let sum = 0;
                    for (let index = 0; index < 8300; index += 1) {
                      sum += (await import('data:text/javascript,export default ' + index + ';')).default;
                    }
                    assert.equal(sum, 8300 * 8299 / 2);
                    console.log('m20.module.budget=PASS');
                  } finally {
                    for (const folder of created) fs.rmSync(folder, { recursive: true, force: true });
                    fs.rmSync(outside, { recursive: true, force: true });
                  }
                  console.log('m20.loader.paths=PASS');
                })().catch((error) => { console.error(error && error.stack || error); process.exitCode = 1; });
                """);
        files.put("lib/dep.cjs", """
                module.exports = { id: module.id, parentId: module.parent && module.parent.id, mainIsParent: require.main === module.parent };
                """);
        files.put("lib/esm.mjs", """
                export const info = { filename: import.meta.filename, main: import.meta.main };
                """);
        try (WorkspaceInvocation invocation = execute("m20-loader-paths", "main.cjs", files, false, 180_000L)) {
            assertSucceeded(invocation.result, "m20.loader.paths=PASS");
            String stdout = invocation.result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
            for (String expected : new String[] {
                    "m20.entry.paths=PASS",
                    "m20.mkdtemp=PASS",
                    "m20.module.budget=PASS"
            }) {
                assertTrue("stdout is missing '" + expected + "': " + stdout, stdout.contains(expected));
            }
        }
    }

    /** M20.2: readdir/opendir recursion is native (no 4096-entry cap) and fs policy codes collapse to NUL / hard boundary / reach root. */
    @Test
    public void m20_readdirRecursionAndFsPolicyCodesFollowNode() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("main.cjs", """
                const assert = require('node:assert/strict');
                const fs = require('node:fs');
                const fsp = require('node:fs/promises');
                const path = require('node:path');
                (async () => {
                  const dir = fs.mkdtempSync(path.join(__dirname, 'readdir-'));
                  try {
                    const tree = path.join(dir, 'tree');
                    fs.mkdirSync(path.join(tree, 'sub', 'deep'), { recursive: true });
                    // 4200 files: the old wrapper walked the tree itself and threw ERR_OUT_OF_RANGE past 4096 entries.
                    for (let index = 0; index < 4200; index += 1) fs.writeFileSync(path.join(tree, 'sub', 'f' + index + '.txt'), '');
                    fs.writeFileSync(path.join(tree, 'sub', 'deep', 'leaf.txt'), 'leaf');
                    fs.writeFileSync(path.join(tree, 'top.txt'), 'top');
                    const names = fs.readdirSync(tree, { recursive: true });
                    assert.equal(names.length, 4204);
                    assert.ok(names.includes(path.join('sub', 'deep', 'leaf.txt')));
                    const dirents = fs.readdirSync(tree, { recursive: true, withFileTypes: true });
                    assert.equal(dirents.length, 4204);
                    const leaf = dirents.find((entry) => entry.name === 'leaf.txt');
                    assert.equal(leaf.parentPath, path.join(tree, 'sub', 'deep'));
                    assert.ok(leaf.isFile() && dirents.find((entry) => entry.name === 'deep').isDirectory());
                    const relativeTree = path.relative(process.cwd(), tree);
                    const relativeLeaf = fs.readdirSync(relativeTree, { recursive: true, withFileTypes: true }).find((entry) => entry.name === 'leaf.txt');
                    assert.equal(relativeLeaf.parentPath, path.join(relativeTree, 'sub', 'deep'));
                    assert.equal(fs.readdirSync(tree, { withFileTypes: true }).find((entry) => entry.name === 'top.txt').parentPath, tree);
                    assert.equal((await fsp.readdir(tree, { recursive: true })).length, 4204);
                    let opened = 0;
                    for await (const entry of await fsp.opendir(tree, { recursive: true })) opened += 1;
                    assert.equal(opened, 4204);
                    assert.ok(Buffer.isBuffer(fs.readdirSync(tree, { encoding: 'buffer' })[0]));
                    // Listing '/' is Android's decision (SELinux may answer EACCES); the wrapper no longer adds an EPERM of its own.
                    let rootEntries = null;
                    try { rootEntries = fs.readdirSync('/'); } catch (error) { assert.equal(error.code, 'EACCES'); assert.equal(error.autojs6Code, undefined); }
                    if (rootEntries) assert.ok(rootEntries.includes('proc') && rootEntries.includes('dev'), rootEntries.join(','));
                    console.log('m20.readdir.recursive=' + names.length);
                    // One code for the hard boundary (shared with the module loader and worker fs), one for NUL bytes, none for ordinary failures.
                    const codeOf = (fn) => { try { fn(); return 'none'; } catch (error) { return String(error.autojs6Code || error.code); } };
                    assert.equal(codeOf(() => fs.readFileSync('/proc/self/status')), 'ERR_AUTOJS6_FS_PATH_ESCAPE');
                    assert.equal(codeOf(() => fs.readFileSync('../'.repeat(16) + 'proc/self/status')), 'ERR_AUTOJS6_FS_PATH_ESCAPE');
                    assert.equal(codeOf(() => fs.readdirSync('/sys/kernel')), 'ERR_AUTOJS6_FS_PATH_ESCAPE');
                    assert.equal(codeOf(() => fs.symlinkSync('/proc/self', path.join(dir, 'proc-link'))), 'ERR_AUTOJS6_FS_PATH_ESCAPE');
                    assert.equal(codeOf(() => fs.readlinkSync('/proc/self/exe')), 'ERR_AUTOJS6_FS_PATH_ESCAPE');
                    assert.equal(codeOf(() => fs.readFileSync(path.join(dir, 'a' + String.fromCharCode(0) + 'b'))), 'ERR_AUTOJS6_FS_NUL_BYTE');
                    let missing = null;
                    try { fs.readFileSync(path.join(dir, 'missing.txt')); } catch (error) { missing = error; }
                    assert.equal(missing.code, 'ENOENT');
                    assert.equal(missing.autojs6Code, undefined);
                    let existing = null;
                    try { fs.mkdirSync(tree); } catch (error) { existing = error; }
                    assert.equal(existing.code, 'EEXIST');
                    assert.equal(existing.autojs6Code, undefined);
                    console.log('m20.fs.codes=PASS');
                    // readlink / chmod / chown / utimes accept absolute paths (the old validators rejected any absolute path) and chmod follows symlinks like Node.
                    const target = path.resolve(dir, 'target.txt');
                    fs.writeFileSync(target, 'x');
                    const absLink = path.resolve(dir, 'abs-link');
                    fs.symlinkSync(target, absLink);
                    assert.equal(fs.readlinkSync(absLink), target);
                    fs.chmodSync(target, 0o600);
                    assert.equal(fs.statSync(target).mode & 0o777, 0o600);
                    fs.chmodSync(absLink, 0o640);
                    assert.equal(fs.statSync(target).mode & 0o777, 0o640);
                    fs.utimesSync(target, new Date(1000000000000), new Date(1000000000000));
                    assert.equal(fs.statSync(target).mtimeMs, 1000000000000);
                    fs.utimesSync(absLink, new Date(1100000000000), new Date(1100000000000));
                    assert.equal(fs.statSync(target).mtimeMs, 1100000000000);
                    const stat = fs.statSync(target);
                    fs.chownSync(target, stat.uid, stat.gid);
                    await fsp.chmod(absLink, 0o644);
                    assert.equal(fs.statSync(target).mode & 0o777, 0o644);
                    console.log('m20.fs.absolute.ops=PASS');
                  } finally {
                    fs.rmSync(dir, { recursive: true, force: true });
                  }
                  console.log('m20.readdir.codes=PASS');
                })().catch((error) => { console.error(error && error.stack || error); process.exitCode = 1; });
                """);
        try (WorkspaceInvocation invocation = execute("m20-readdir-codes", "main.cjs", files, false)) {
            assertSucceeded(invocation.result, "m20.readdir.codes=PASS");
            String stdout = invocation.result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
            for (String expected : new String[] {
                    "m20.readdir.recursive=4204",
                    "m20.fs.codes=PASS",
                    "m20.fs.absolute.ops=PASS"
            }) {
                assertTrue("stdout is missing '" + expected + "': " + stdout, stdout.contains(expected));
            }
        }
    }

    /** M20.2: the provider transport adopts the host-advertised protocol sizes; the built-in numbers are only a fallback. */
    @Test
    public void m20_providerTransportAdoptsHostAdvertisedLimits() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put(
                "main.cjs",
                "const value = require('./materialized-plain.cjs');\n" +
                        "console.log('m20.provider.limits=' + value.length);\n"
        );
        StringBuilder module = new StringBuilder("module.exports = '");
        while (module.length() < 2048) module.append("0123456789abcdef");
        module.append("';\n");
        byte[] rawPlaintext = module.toString().getBytes(StandardCharsets.UTF_8);
        int payloadLength = module.length() - "module.exports = '".length() - "';\n".length();

        plaintextProviderAdvertisedSourceMaxBytes.set(1024L);
        try (WorkspaceInvocation invocation = execute(
                "provider-limits-host-1024",
                "main.cjs",
                files,
                new LinkedHashMap<>(),
                true,
                rawPlaintext
        )) {
            assertFalse("2 KiB source unexpectedly passed a host-advertised 1 KiB limit",
                    invocation.result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            assertEquals(
                    NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_BUDGET_EXCEEDED,
                    invocation.result.getString(NodeJsRuntimeContract.KEY_ERROR_CODE)
            );
            assertNativeValue(invocation.result,
                    "embedded_script.module_provider.transport_limits_source", "host");
            assertNativeValue(invocation.result,
                    "embedded_script.module_provider.transport_source_bytes_limit", "1024");
            assertNativeValue(invocation.result,
                    "embedded_script.module_provider.transport_aggregate_source_bytes_limit", "4096");
            assertNativeValue(invocation.result,
                    "embedded_script.module_provider.transport_request_count_limit", "64");
            invocation.callback.assertOneStartedAndOneTerminalEvent();
        } finally {
            plaintextProviderAdvertisedSourceMaxBytes.set(-1L);
        }

        try (WorkspaceInvocation invocation = execute(
                "provider-limits-default",
                "main.cjs",
                files,
                new LinkedHashMap<>(),
                true,
                rawPlaintext
        )) {
            String stdout = invocation.result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
            assertTrue("materialized source failed under the default limits: " +
                            invocation.result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "") + " / " + stdout,
                    invocation.result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            assertTrue(stdout, stdout.contains("m20.provider.limits=" + payloadLength));
            assertNativeValue(invocation.result,
                    "embedded_script.module_provider.transport_limits_source", "default");
            assertNativeValue(invocation.result,
                    "embedded_script.module_provider.transport_source_bytes_limit", "16777216");
            assertNativeValue(invocation.result,
                    "embedded_script.module_provider.transport_aggregate_source_bytes_limit", "67108864");
            assertNativeValue(invocation.result,
                    "embedded_script.module_provider.transport_request_count_limit", "139264");
            invocation.callback.assertOneStartedAndOneTerminalEvent();
        }
    }

    /** M20.2: opendir returns Node's own lazy Dir and removing the reach root is Node's decision like any other path. */
    @Test
    public void m20_opendirIsNativeLazyDirAndReachRootFollowsNode() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("main.cjs", """
                const assert = require('node:assert/strict');
                const fs = require('node:fs');
                const fsp = require('node:fs/promises');
                const path = require('node:path');
                (async () => {
                  const dir = fs.mkdtempSync(path.join(__dirname, 'opendir-'));
                  try {
                    const tree = path.join(dir, 'tree');
                    fs.mkdirSync(path.join(tree, 'sub', 'deep'), { recursive: true });
                    fs.writeFileSync(path.join(tree, 'a.txt'), '');
                    fs.writeFileSync(path.join(tree, 'sub', 'b.txt'), '');
                    fs.writeFileSync(path.join(tree, 'sub', 'deep', 'c.txt'), '');
                    const relativeTree = path.relative(process.cwd(), tree);
                    // The Dir is native (bufferSize / recursive go to native opendir); dir.path and parentPath keep the caller's spelling.
                    const sync = fs.opendirSync(relativeTree, { bufferSize: 1, recursive: true });
                    assert.ok(sync instanceof fs.Dir);
                    assert.equal(sync.path, relativeTree);
                    const seen = [];
                    let entry;
                    while ((entry = sync.readSync()) !== null) seen.push(entry);
                    assert.equal(seen.length, 5);
                    const c = seen.find((item) => item.name === 'c.txt');
                    assert.equal(c.parentPath, path.join(relativeTree, 'sub', 'deep'));
                    assert.ok(c.isFile() && c instanceof fs.Dirent && seen.find((item) => item.name === 'deep').isDirectory());
                    sync.closeSync();
                    assert.throws(() => sync.readSync(), (error) => error.code === 'ERR_DIR_CLOSED');
                    await assert.rejects(sync.read(), (error) => error.code === 'ERR_DIR_CLOSED');
                    const viaCallback = await new Promise((resolve, reject) => fs.opendir(tree, (error, opened) => error ? reject(error) : resolve(opened)));
                    assert.equal(viaCallback.path, tree);
                    const first = await viaCallback.read();
                    assert.equal(first.parentPath, tree);
                    const second = await new Promise((resolve, reject) => viaCallback.read((error, item) => error ? reject(error) : resolve(item)));
                    assert.ok(second === null || second.parentPath === tree);
                    await viaCallback.close();
                    let count = 0;
                    for await (const item of await fsp.opendir(relativeTree, { recursive: true })) {
                      count += 1;
                      assert.ok(String(item.parentPath).startsWith(relativeTree), String(item.parentPath));
                    }
                    assert.equal(count, 5);
                    const partial = await fsp.opendir(tree);
                    for await (const item of partial) { void item; break; }
                    await assert.rejects(partial.read(), (error) => error.code === 'ERR_DIR_CLOSED');
                    const buffered = fs.opendirSync(tree, { encoding: 'buffer' });
                    assert.ok(Buffer.isBuffer(buffered.readSync().name));
                    buffered.closeSync();
                    // Option validation and failures are Node's own: ERR_OUT_OF_RANGE, ENOENT / ENOTDIR with syscall 'opendir', no autojs6Code.
                    assert.throws(() => fs.opendirSync(tree, { bufferSize: 0 }), (error) => error.code === 'ERR_OUT_OF_RANGE');
                    assert.throws(() => fs.opendirSync(path.join(tree, 'missing')), (error) => error.code === 'ENOENT' && error.syscall === 'opendir' && error.autojs6Code === undefined);
                    assert.throws(() => fs.opendirSync(path.join(tree, 'a.txt')), (error) => error.code === 'ENOTDIR' && error.syscall === 'opendir' && error.autojs6Code === undefined);
                    await assert.rejects(fsp.opendir(path.join(tree, 'missing')), (error) => error.code === 'ENOENT' && error.syscall === 'opendir');
                    assert.throws(() => fs.opendirSync('/proc/self'), (error) => error.autojs6Code === 'ERR_AUTOJS6_FS_PATH_ESCAPE');
                    console.log('m20.opendir=PASS');
                    // Removing the reach root is Node's decision: non-recursive rm of a directory is ERR_FS_EISDIR, rmdir of a non-empty root is native; no ERR_AUTOJS6_FS_SCOPED_PATH.
                    let rootRm = null;
                    try { fs.rmSync('/'); } catch (error) { rootRm = error; }
                    assert.equal(rootRm && rootRm.code, 'ERR_FS_EISDIR');
                    assert.equal(rootRm.autojs6Code, undefined);
                    let rootRmdir = null;
                    try { fs.rmdirSync('/'); } catch (error) { rootRmdir = error; }
                    assert.ok(rootRmdir && rootRmdir.autojs6Code === undefined && rootRmdir.code !== 'ERR_AUTOJS6_FS_SCOPED_PATH', String(rootRmdir));
                    let rootRmdirAsync = null;
                    try { await fsp.rmdir('/'); } catch (error) { rootRmdirAsync = error; }
                    assert.ok(rootRmdirAsync && rootRmdirAsync.autojs6Code === undefined, String(rootRmdirAsync));
                    console.log('m20.reach.root=' + rootRmdir.code);
                  } finally {
                    fs.rmSync(dir, { recursive: true, force: true });
                  }
                  console.log('m20.opendir.codes=PASS');
                })().catch((error) => { console.error(error && error.stack || error); process.exitCode = 1; });
                """);
        try (WorkspaceInvocation invocation = execute("m20-opendir-codes", "main.cjs", files, false)) {
            assertSucceeded(invocation.result, "m20.opendir.codes=PASS");
            String stdout = invocation.result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
            for (String expected : new String[] {
                    "m20.opendir=PASS",
                    "m20.reach.root="
            }) {
                assertTrue("stdout is missing '" + expected + "': " + stdout, stdout.contains(expected));
            }
        }
    }

    /** M20.2: worker process.exit() ends the thread as in Node; getBuiltinModule follows the worker allowlist. */
    @Test
    public void m20_workerProcessExitAndGetBuiltinModuleFollowNode() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("worker.cjs", """
                const assert = require('node:assert/strict');
                const { parentPort } = require('node:worker_threads');
                assert.strictEqual(process.getBuiltinModule('node:path'), require('node:path'));
                assert.strictEqual(process.getBuiltinModule('fs'), require('fs'));
                assert.strictEqual(process.getBuiltinModule('no-such-builtin'), undefined);
                assert.throws(() => process.getBuiltinModule('node:inspector'), (e) => e.code === 'ERR_AUTOJS6_WORKER_BRIDGE_DENIED');
                assert.throws(() => process.abort(), (e) => e.code === 'ERR_AUTOJS6_PROCESS_API_DISABLED');
                assert.throws(() => process.kill(process.pid), (e) => e.code === 'ERR_AUTOJS6_PROCESS_API_DISABLED');
                parentPort.postMessage('before-exit');
                process.exit(3);
                parentPort.postMessage('after-exit-must-not-run');
                """);
        files.put("main.cjs", """
                const assert = require('node:assert/strict');
                const path = require('node:path');
                const { Worker } = require('node:worker_threads');
                const profile = require('autojs6:profile').workerThreadsProfile.processApis;
                assert.strictEqual(profile.exit, 'ends_worker_thread_as_node');
                assert.strictEqual(profile.getBuiltinModule, 'allowlisted_builtins');
                (async () => {
                  const messages = [];
                  const worker = new Worker(path.join(__dirname, 'worker.cjs'));
                  const exitCode = await new Promise((resolve, reject) => {
                    worker.on('message', (m) => messages.push(m));
                    worker.on('error', reject);
                    worker.on('exit', resolve);
                  });
                  console.log('m20.worker.exitCode=' + exitCode);
                  console.log('m20.worker.messages=' + messages.join(','));
                  // The main thread keeps running after the worker exited.
                  await new Promise((resolve) => setTimeout(resolve, 10));
                  console.log('m20.worker.process=PASS');
                })().catch((error) => { console.error(error && error.stack || error); process.exitCode = 1; });
                """);
        try (WorkspaceInvocation invocation = execute("m20-worker-process", "main.cjs", files,
                new LinkedHashMap<>(), false, null, null, SCRIPT_TIMEOUT_MS, false, Collections.emptyList(), true)) {
            Bundle result = invocation.result;
            String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
            assertTrue("m20 worker process failed: " + result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "")
                            + " / " + result.getString(NodeJsRuntimeContract.KEY_STDERR, "") + " / " + stdout,
                    result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            assertEquals("exit code", 0, result.getInt(NodeJsRuntimeContract.KEY_EXIT_CODE, -1));
            for (String expected : new String[] {
                    "m20.worker.exitCode=3",
                    "m20.worker.messages=before-exit",
                    "m20.worker.process=PASS"
            }) {
                assertTrue("stdout is missing '" + expected + "': " + stdout, stdout.contains(expected));
            }
            assertFalse("worker code after process.exit ran: " + stdout, stdout.contains("after-exit-must-not-run"));
        }
    }

    /** M20.2: workers resolve bare package specifiers through the workspace node_modules as in Node. */
    @Test
    public void m20_workersResolveWorkspacePackages() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("node_modules/plain-cjs/package.json", "{\"name\":\"plain-cjs\",\"main\":\"lib/entry\"}");
        files.put("node_modules/plain-cjs/lib/entry.js", "module.exports = { name: 'plain-cjs', dep: require('dep-nested') };");
        files.put("node_modules/plain-cjs/node_modules/dep-nested/index.js", "module.exports = 'nested';");
        files.put("node_modules/dep-nested/index.js", "module.exports = 'top';");
        files.put("node_modules/@scope/cond/package.json",
                "{\"name\":\"@scope/cond\",\"exports\":{\".\":{\"import\":\"./esm.mjs\",\"require\":\"./cjs.cjs\"},"
                        + "\"./features/*\":\"./features/*.js\",\"./package.json\":\"./package.json\"}}");
        files.put("node_modules/@scope/cond/esm.mjs", "export const flavor = 'esm';");
        files.put("node_modules/@scope/cond/cjs.cjs", "module.exports = { flavor: 'cjs' };");
        files.put("node_modules/@scope/cond/features/alpha.js", "module.exports = 'alpha';");
        files.put("node_modules/type-module-pkg/package.json", "{\"name\":\"type-module-pkg\",\"type\":\"module\",\"exports\":\"./index.js\"}");
        files.put("node_modules/type-module-pkg/index.js", "export const kind = 'esm-js';");
        files.put("node_modules/index-only/index.js", "module.exports = 'index-only';");
        files.put("node_modules/dir-main/package.json", "{\"name\":\"dir-main\",\"main\":\"lib\"}");
        files.put("node_modules/dir-main/lib/index.js", "module.exports = 'dir-main';");
        files.put("node_modules/self-ref/package.json", "{\"name\":\"self-ref\",\"exports\":{\".\":\"./main.cjs\",\"./util\":\"./util.cjs\"}}");
        files.put("node_modules/self-ref/main.cjs", "module.exports = require('self-ref/util');");
        files.put("node_modules/self-ref/util.cjs", "module.exports = 'self-util';");
        files.put("worker.cjs", """
                const assert = require('node:assert/strict');
                const { parentPort } = require('node:worker_threads');
                const plain = require('plain-cjs');
                assert.equal(plain.name, 'plain-cjs');
                assert.equal(plain.dep, 'nested');
                assert.equal(require('dep-nested'), 'top');
                assert.equal(require('@scope/cond').flavor, 'cjs');
                assert.equal(require('@scope/cond/features/alpha'), 'alpha');
                assert.equal(require('@scope/cond/package.json').name, '@scope/cond');
                assert.equal(require('index-only'), 'index-only');
                assert.equal(require('dir-main'), 'dir-main');
                assert.equal(require('self-ref'), 'self-util');
                assert.ok(require.resolve('plain-cjs').endsWith('/node_modules/plain-cjs/lib/entry.js'), require.resolve('plain-cjs'));
                assert.throws(() => require('@scope/cond/hidden'), (e) => e.code === 'ERR_PACKAGE_PATH_NOT_EXPORTED');
                assert.throws(() => require('no-such-package'), (e) => e.code === 'MODULE_NOT_FOUND');
                assert.throws(() => require('type-module-pkg'), (e) => e.code === 'ERR_AUTOJS6_REQUIRE_ESM_UNSUPPORTED');
                for (const name of ['ui', 'sqlite', 'autojs6:fetch', 'node:inspector', 'inspector']) {
                  assert.throws(() => require(name), (e) => e.code === 'ERR_AUTOJS6_WORKER_BRIDGE_DENIED', name);
                }
                parentPort.postMessage('cjs-ok');
                """);
        files.put("worker.mjs", """
                import { flavor } from '@scope/cond';
                import { kind } from 'type-module-pkg';
                import plain from 'plain-cjs';
                import { parentPort } from 'node:worker_threads';
                parentPort.postMessage(flavor + '/' + kind + '/' + plain.name);
                """);
        files.put("main.cjs", """
                const assert = require('node:assert/strict');
                const path = require('node:path');
                const { Worker } = require('node:worker_threads');
                assert.equal(require('autojs6:profile').workerThreadsProfile.packageResolution, 'workspace_node_modules');
                // The main thread resolves the same fixtures, so both loaders agree.
                assert.equal(require('@scope/cond').flavor, 'cjs');
                assert.equal(require('plain-cjs').dep, 'nested');
                function runWorker(file) {
                  return new Promise((resolve, reject) => {
                    const worker = new Worker(path.join(__dirname, file));
                    worker.once('message', resolve);
                    worker.once('error', reject);
                    worker.once('exit', (code) => { if (code !== 0) reject(new Error(file + ' exited with ' + code)); });
                  });
                }
                (async () => {
                  console.log('m20.worker.packages.cjs=' + await runWorker('worker.cjs'));
                  console.log('m20.worker.packages.esm=' + await runWorker('worker.mjs'));
                  console.log('m20.worker.packages=PASS');
                })().catch((error) => { console.error(error && error.stack || error); process.exitCode = 1; });
                """);
        try (WorkspaceInvocation invocation = execute("m20-worker-packages", "main.cjs", files,
                new LinkedHashMap<>(), false, null, null, SCRIPT_TIMEOUT_MS, false, Collections.emptyList(), true)) {
            Bundle result = invocation.result;
            String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
            assertTrue("m20 worker packages failed: " + result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "")
                            + " / " + result.getString(NodeJsRuntimeContract.KEY_STDERR, "") + " / " + stdout,
                    result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            assertEquals("exit code", 0, result.getInt(NodeJsRuntimeContract.KEY_EXIT_CODE, -1));
            for (String expected : new String[] {
                    "m20.worker.packages.cjs=cjs-ok",
                    "m20.worker.packages.esm=esm/esm-js/plain-cjs",
                    "m20.worker.packages=PASS"
            }) {
                assertTrue("stdout is missing '" + expected + "': " + stdout, stdout.contains(expected));
            }
        }
    }

    /** M20.2: dynamic import() inside workers goes through the worker partial ESM loader. */
    @Test
    public void m20_workerDynamicImportFollowsWorkerLoader() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("node_modules/dyn-pkg/package.json",
                "{\"name\":\"dyn-pkg\",\"exports\":{\"import\":\"./esm.mjs\",\"require\":\"./cjs.cjs\"}}");
        files.put("node_modules/dyn-pkg/esm.mjs", "export const flavor = 'esm';");
        files.put("node_modules/dyn-pkg/cjs.cjs", "module.exports = { flavor: 'cjs' };");
        files.put("dep.mjs", "export const value = 42;\nexport default 'dep-default';");
        files.put("dep.cjs", "module.exports = { cjs: true };");
        files.put("data.json", "{\"answer\": 42}");
        files.put("worker.cjs", """
                const assert = require('node:assert/strict');
                const path = require('node:path');
                const { pathToFileURL } = require('node:url');
                const { parentPort } = require('node:worker_threads');
                (async () => {
                  const dep = await import('./dep.mjs');
                  assert.equal(dep.value, 42);
                  assert.equal(dep.default, 'dep-default');
                  const cjs = await import('./dep.cjs');
                  assert.equal(cjs.default.cjs, true);
                  const pathNs = await import('node:path');
                  assert.equal(typeof pathNs.join, 'function');
                  const pkg = await import('dyn-pkg');
                  assert.equal(pkg.flavor, 'esm');
                  assert.equal(require('dyn-pkg').flavor, 'cjs');
                  const viaUrl = await import(pathToFileURL(path.join(__dirname, 'dep.mjs')).href);
                  assert.equal(viaUrl, dep);
                  const viaAbsolute = await import(path.join(__dirname, 'dep.mjs'));
                  assert.equal(viaAbsolute, dep);
                  const json = await import('./data.json', { with: { type: 'json' } });
                  assert.equal(json.default.answer, 42);
                  await assert.rejects(import('./data.json'), (e) => e.code === 'ERR_IMPORT_ATTRIBUTE_MISSING');
                  await assert.rejects(import('./dep.mjs', { with: { type: 'json' } }), (e) => e.code === 'ERR_IMPORT_ATTRIBUTE_TYPE_INCOMPATIBLE');
                  await assert.rejects(import('./dep.mjs', { with: { type: 'css' } }), (e) => e.code === 'ERR_IMPORT_ATTRIBUTE_UNSUPPORTED');
                  await assert.rejects(import('node:inspector'), (e) => e.code === 'ERR_AUTOJS6_WORKER_BRIDGE_DENIED');
                  await assert.rejects(import('autojs6:fetch'), (e) => e.code === 'ERR_AUTOJS6_WORKER_BRIDGE_DENIED');
                  await assert.rejects(import('./missing.mjs'), (e) => e.code === 'ERR_AUTOJS6_MODULE_NOT_FOUND');
                  await assert.rejects(import('no-such-dyn-package'), (e) => e.code === 'MODULE_NOT_FOUND');
                  await assert.rejects(import('/proc/self/status'), (e) => e.code === 'ERR_AUTOJS6_FS_PATH_ESCAPE');
                  parentPort.postMessage('cjs-ok');
                })().catch((error) => { parentPort.postMessage('cjs-fail:' + (error && (error.stack || error.message))); });
                """);
        files.put("worker.mjs", """
                import { parentPort } from 'node:worker_threads';
                const dep = await import('./dep.mjs');
                const pkg = await import('dyn-pkg');
                const json = await import('./data.json', { with: { type: 'json' } });
                parentPort.postMessage(dep.value + '/' + pkg.flavor + '/' + json.default.answer);
                """);
        files.put("main.cjs", """
                const assert = require('node:assert/strict');
                const path = require('node:path');
                const { Worker } = require('node:worker_threads');
                assert.equal(require('autojs6:profile').workerThreadsProfile.dynamicImport, true);
                function runWorker(target, options) {
                  return new Promise((resolve, reject) => {
                    const worker = new Worker(target, options);
                    worker.once('message', resolve);
                    worker.once('error', reject);
                    worker.once('exit', (code) => { if (code !== 0) reject(new Error(String(target) + ' exited with ' + code)); });
                  });
                }
                (async () => {
                  console.log('m20.worker.dynamicImport.cjs=' + await runWorker(path.join(__dirname, 'worker.cjs')));
                  console.log('m20.worker.dynamicImport.esm=' + await runWorker(path.join(__dirname, 'worker.mjs')));
                  const evalSource = "const { parentPort } = require('node:worker_threads');"
                    + " import('./dep.mjs').then((ns) => parentPort.postMessage('eval:' + ns.value));";
                  console.log('m20.worker.dynamicImport.eval=' + await runWorker(evalSource, { eval: true }));
                  console.log('m20.worker.dynamicImport=PASS');
                })().catch((error) => { console.error(error && error.stack || error); process.exitCode = 1; });
                """);
        try (WorkspaceInvocation invocation = execute("m20-worker-dynamic-import", "main.cjs", files,
                new LinkedHashMap<>(), false, null, null, SCRIPT_TIMEOUT_MS, false, Collections.emptyList(), true)) {
            Bundle result = invocation.result;
            String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
            assertTrue("m20 worker dynamic import failed: " + result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "")
                            + " / " + result.getString(NodeJsRuntimeContract.KEY_STDERR, "") + " / " + stdout,
                    result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            assertEquals("exit code", 0, result.getInt(NodeJsRuntimeContract.KEY_EXIT_CODE, -1));
            for (String expected : new String[] {
                    "m20.worker.dynamicImport.cjs=cjs-ok",
                    "m20.worker.dynamicImport.esm=42/esm/42",
                    "m20.worker.dynamicImport.eval=eval:42",
                    "m20.worker.dynamicImport=PASS"
            }) {
                assertTrue("stdout is missing '" + expected + "': " + stdout, stdout.contains(expected));
            }
        }
    }

    @Test
    public void x3d_03_importedCommonJsStackRemovesFunctionWrapperOffset() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("main.cjs", "require('./fail.cjs').fail();\n");
        files.put(
                "fail.cjs",
                "module.exports.fail = function fail() {\n" +
                        "  const marker = 'x3d.imported-stack';\n" +
                        "  throw new Error(marker);\n" +
                        "};\n"
        );

        try (WorkspaceInvocation invocation = execute(
                "imported-commonjs-stack",
                "main.cjs",
                files,
                false
        )) {
            assertFalse("Imported CommonJS failure unexpectedly succeeded",
                    invocation.result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            String stack = invocation.result.getString(NodeJsRuntimeContract.KEY_ERROR_STACK, "");
            assertTrue("Imported CommonJS frame was not normalized:\n" + stack,
                    stack.contains("/fail.cjs:3:"));
            assertFalse("Function wrapper offset leaked into imported CommonJS frame:\n" + stack,
                    stack.contains("/fail.cjs:6:"));
            int mappedFrames = Integer.parseInt(nativeValues(invocation.result).getOrDefault(
                    "embedded_script.generated_stack_mapped_frame_count",
                    "0"
            ));
            assertTrue("No generated CommonJS stack frame was normalized", mappedFrames > 0);
            invocation.callback.assertOneStartedAndOneTerminalEvent();
        }
    }

    @Test
    public void x3d_04_rawTypeScriptEntryRequiresCompilerBeforeWorkspaceCreation() throws Exception {
        for (String entryName : Arrays.asList("main.ts", "main.mts", "main.cts")) {
            LinkedHashMap<String, String> files = new LinkedHashMap<>();
            files.put(entryName, "const answer: number = 42;\nconsole.log(answer);\n");

            try (WorkspaceInvocation invocation = execute(
                    "raw-typescript-entry-negative-" + entryName,
                    entryName,
                    files,
                    false
            )) {
                assertFalse("Raw TypeScript entry unexpectedly succeeded: " + entryName,
                        invocation.result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
                assertEquals(
                        NodeJsRuntimeContract.ERROR_TYPESCRIPT_COMPILER_REQUIRED,
                        invocation.result.getString(NodeJsRuntimeContract.KEY_ERROR_CODE)
                );
                assertNativeValue(invocation.result,
                        "embedded_script.runtime_plugin.native_dispatch_started", "false");
                assertNativeValue(invocation.result,
                        "embedded_script.runtime_plugin.workspace.commit_allowed", "false");
                invocation.callback.assertOneStartedAndOneTerminalEvent();
            }
        }
    }

    @Test
    public void x3d_05_dynamicCtsRequiresCompilerOutput() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put(
                "main.cjs",
                "require('./dynamic.cts');\n"
        );
        files.put(
                "dynamic.cts",
                "const value: number = 42;\nmodule.exports = value;\n"
        );
        try (WorkspaceInvocation invocation = execute(
                "dynamic-cts-compiler-required",
                "main.cjs",
                files,
                true
        )) {
            assertFalse("Dynamic raw TypeScript unexpectedly succeeded",
                    invocation.result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            assertEquals(
                    NodeJsRuntimeContract.ERROR_TYPESCRIPT_COMPILER_REQUIRED,
                    invocation.result.getString(NodeJsRuntimeContract.KEY_ERROR_CODE)
            );
            assertNativeValue(invocation.result,
                    "embedded_script.module_provider.missing_candidate_request_count", "0");
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.provider.assertHealthyAndNotCalled();
        }
    }

    @Test
    public void x3d_06_precompiledSnapshotMapsTsMtsAndCtsDynamicSpecifiers() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put(
                "main.mjs",
                "const extensions = ['ts', 'mts', 'cts'];\n" +
                        "const loaded = await Promise.all(extensions.map((extension) => " +
                        "import('./value.' + extension)));\n" +
                        "console.log('x3d.typescript.snapshot=' + " +
                        "loaded.reduce((sum, item) => sum + Number(item.value), 0));\n"
        );
        files.put("value.ts", "export const value: number = 10;\n");
        files.put("value.mts", "export const value: number = 20;\n");
        files.put("value.cts", "export const value: number = 12;\n");
        LinkedHashMap<String, String> generatedModules = new LinkedHashMap<>();
        generatedModules.put("value.js", "module.exports = { value: 10 };\n");
        generatedModules.put("value.mjs", "export const value = 20;\n");
        generatedModules.put("value.cjs", "module.exports = { value: 12 };\n");

        try (WorkspaceInvocation invocation = executeWithTypeScriptSnapshot(
                "typescript-snapshot-positive",
                "main.mjs",
                files,
                generatedModules,
                Arrays.asList("value.ts", "value.mts", "value.cts"),
                true
        )) {
            assertSucceeded(invocation.result, "x3d.typescript.snapshot=42");
            assertNativeValue(invocation.result,
                    "embedded_script.typescript.dynamic_specifier.snapshot_enabled", "true");
            assertNativeValue(invocation.result,
                    "embedded_script.typescript.dynamic_specifier.source_count", "3");
            assertNativeValue(invocation.result,
                    "embedded_script.typescript.dynamic_specifier.mapped_count", "3");
            assertNativeValue(invocation.result,
                    "embedded_script.typescript.dynamic_specifier.rejected_count", "0");
            assertNativeValue(invocation.result,
                    "embedded_script.module_provider.missing_candidate_request_count", "0");
            invocation.provider.assertHealthyAndNotCalled();
            invocation.callback.assertOneStartedAndOneTerminalEvent();
        }
    }

    @Test
    public void x3d_07_precompiledSnapshotRejectsMissingAmbiguousAndEscapingSpecifiers()
            throws Exception {
        LinkedHashMap<String, String> missingFiles = new LinkedHashMap<>();
        missingFiles.put(
                "main.mjs",
                "const extension = '.mts';\nawait import('./late' + extension);\n"
        );
        try (WorkspaceInvocation invocation = executeWithTypeScriptSnapshot(
                "typescript-snapshot-missing",
                "main.mjs",
                missingFiles,
                new LinkedHashMap<>(),
                Collections.emptyList(),
                true
        )) {
            assertFalse(invocation.result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            assertNativeValue(invocation.result,
                    "embedded_script.typescript.dynamic_specifier.snapshot_enabled", "true");
            assertEquals(
                    NodeJsRuntimeContract.ERROR_TYPESCRIPT_SNAPSHOT_MODULE_NOT_FOUND,
                    invocation.result.getString(NodeJsRuntimeContract.KEY_ERROR_CODE)
            );
            assertNativeValue(invocation.result,
                    "embedded_script.typescript.dynamic_specifier.rejected_count", "1");
            assertNativeValue(invocation.result,
                    "embedded_script.typescript.dynamic_specifier.last_error_code",
                    NodeJsRuntimeContract.ERROR_TYPESCRIPT_SNAPSHOT_MODULE_NOT_FOUND);
            assertNativeValue(invocation.result,
                    "embedded_script.module_provider.missing_candidate_request_count", "0");
            invocation.provider.assertHealthyAndNotCalled();
        }

        LinkedHashMap<String, String> ambiguousFiles = new LinkedHashMap<>();
        ambiguousFiles.put("main.mjs", "await import('./value');\n");
        ambiguousFiles.put("value.mts", "export const value: number = 20;\n");
        ambiguousFiles.put("value.cts", "export const value: number = 22;\n");
        LinkedHashMap<String, String> ambiguousGenerated = new LinkedHashMap<>();
        ambiguousGenerated.put("value.mjs", "export const value = 20;\n");
        ambiguousGenerated.put("value.cjs", "module.exports = { value: 22 };\n");
        try (WorkspaceInvocation invocation = executeWithTypeScriptSnapshot(
                "typescript-snapshot-ambiguous",
                "main.mjs",
                ambiguousFiles,
                ambiguousGenerated,
                Arrays.asList("value.mts", "value.cts"),
                true
        )) {
            assertFalse(invocation.result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            assertEquals(
                    NodeJsRuntimeContract.ERROR_TYPESCRIPT_SNAPSHOT_MODULE_AMBIGUOUS,
                    invocation.result.getString(NodeJsRuntimeContract.KEY_ERROR_CODE)
            );
            assertNativeValue(invocation.result,
                    "embedded_script.typescript.dynamic_specifier.ambiguous_count", "1");
            assertNativeValue(invocation.result,
                    "embedded_script.module_provider.missing_candidate_request_count", "0");
            invocation.provider.assertHealthyAndNotCalled();
        }

        // M20.2: a parent-directory target is no longer an fs policy violation; a raw
        // TypeScript source outside the snapshot still needs compiler output.
        LinkedHashMap<String, String> escapingFiles = new LinkedHashMap<>();
        escapingFiles.put("main.mjs", "await import('../escape.mts');\n");
        try (WorkspaceInvocation invocation = executeWithTypeScriptSnapshot(
                "typescript-snapshot-escape",
                "main.mjs",
                escapingFiles,
                new LinkedHashMap<>(),
                Collections.emptyList(),
                true
        )) {
            assertFalse(invocation.result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            assertEquals(
                    NodeJsRuntimeContract.ERROR_TYPESCRIPT_SNAPSHOT_MODULE_NOT_FOUND,
                    invocation.result.getString(NodeJsRuntimeContract.KEY_ERROR_CODE)
            );
            assertNativeValue(invocation.result,
                    "embedded_script.module_provider.missing_candidate_request_count", "0");
            invocation.provider.assertHealthyAndNotCalled();
        }
    }

    @Test
    public void x3d_08_esmCyclePreservesLiveBindingsThroughReExport() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put(
                "main.mjs",
                "import { snapshot, setValue } from './state.mjs';\n" +
                        "console.log('x3d.esm.live.initial=' + snapshot());\n" +
                        "setValue(42);\n" +
                        "console.log('x3d.esm.live.updated=' + snapshot());\n"
        );
        files.put(
                "state.mjs",
                "import { readValue } from './reader.mjs';\n" +
                        "export let value = 1;\n" +
                        "export function setValue(next) { value = next; }\n" +
                        "export function snapshot() { return readValue(); }\n"
        );
        files.put(
                "reader.mjs",
                "import { value } from './barrel.mjs';\n" +
                        "export function readValue() { return value; }\n"
        );
        files.put("barrel.mjs", "export { value } from './state.mjs';\n");

        try (WorkspaceInvocation invocation = execute(
                "esm-live-binding-cycle",
                "main.mjs",
                files,
                false
        )) {
            assertSucceeded(invocation.result, "x3d.esm.live.initial=1");
            assertTrue(
                    "ESM cycle did not observe the mutated live binding: " +
                            invocation.result.getString(NodeJsRuntimeContract.KEY_STDOUT, ""),
                    invocation.result.getString(NodeJsRuntimeContract.KEY_STDOUT, "")
                            .contains("x3d.esm.live.updated=42")
            );
            assertNativeValue(
                    invocation.result,
                    "embedded_script.esm_linker",
                    "vm_source_text_module"
            );
            assertNativeValue(
                    invocation.result,
                    "embedded_script.esm_live_bindings",
                    "true"
            );
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.assertWorkspaceOutputCommitted();
        }
    }

    @Test
    public void x3d_09_esmAndCjsFacadesSkipMissingTypeScriptCandidatesAndKeepNpmPrecedence()
            throws Exception {
        LinkedHashMap<String, String> esmFacadeFiles = new LinkedHashMap<>();
        esmFacadeFiles.put(
                "main.mjs",
                "import app from 'app';\n" +
                        "import device from 'device';\n" +
                        "import toast from 'toast';\n" +
                        "console.log('x3d.esm.facades=' + [typeof app.isInstalled, " +
                        "typeof device.isScreenOn, typeof toast.showToast].join(','));\n"
        );
        try (WorkspaceInvocation invocation = execute(
                "esm-autojs6-facades",
                "main.mjs",
                esmFacadeFiles,
                false
        )) {
            assertSucceeded(invocation.result, "x3d.esm.facades=function,function,function");
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.assertWorkspaceOutputCommitted();
        }

        LinkedHashMap<String, String> cjsFacadeFiles = new LinkedHashMap<>();
        cjsFacadeFiles.put(
                "main.cjs",
                "const app = require('app');\n" +
                        "const device = require('device');\n" +
                        "const toast = require('toast');\n" +
                        "console.log('x3d.cjs.facades=' + [typeof app.isInstalled, " +
                        "typeof device.isScreenOn, typeof toast.showToast].join(','));\n"
        );
        try (WorkspaceInvocation invocation = execute(
                "cjs-autojs6-facades",
                "main.cjs",
                cjsFacadeFiles,
                false
        )) {
            assertSucceeded(invocation.result, "x3d.cjs.facades=function,function,function");
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.assertWorkspaceOutputCommitted();
        }

        // npm precedence covers only the runtime modules that stand in for npm packages (mime, ...):
        // a same-named package under node_modules shadows `mime` but not the `app` / `websocket` facades
        // (the published-Binder harness runs with Java interop disabled, so `java` is covered by the shared predicate).
        LinkedHashMap<String, String> npmShadowFiles = new LinkedHashMap<>();
        npmShadowFiles.put(
                "main.mjs",
                "import app from 'app';\n" +
                        "import mime from 'mime';\n" +
                        "import websocket from 'websocket';\n" +
                        "console.log('x3d.esm.facade-shadow=' + [typeof app.isInstalled, String(app.source), mime.source, " +
                        "typeof websocket.connect, String(websocket.source)].join(','));\n"
        );
        for (String shadowed : new String[]{"app", "mime", "websocket"}) {
            npmShadowFiles.put(
                    "node_modules/" + shadowed + "/package.json",
                    "{\"name\":\"" + shadowed + "\",\"type\":\"module\",\"exports\":\"./index.mjs\"}\n"
            );
            npmShadowFiles.put(
                    "node_modules/" + shadowed + "/index.mjs",
                    "export default { source: 'npm' };\n"
            );
        }
        try (WorkspaceInvocation invocation = execute(
                "esm-autojs6-facade-npm-shadow",
                "main.mjs",
                npmShadowFiles,
                false
        )) {
            assertSucceeded(invocation.result, "x3d.esm.facade-shadow=function,undefined,npm,function,undefined");
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.assertWorkspaceOutputCommitted();
        }

        LinkedHashMap<String, String> cjsShadowFiles = new LinkedHashMap<>();
        cjsShadowFiles.put(
                "main.cjs",
                "const profile = require('autojs6:profile');\n" +
                        "const sensors = require('sensors');\n" +
                        "const fetch = require('fetch');\n" +
                        "const websocket = require('websocket');\n" +
                        "const device = require('device');\n" +
                        "const files = require('files');\n" +
                        "const mime = require('mime');\n" +
                        "const colors = require('colors');\n" +
                        "console.log('x3d.cjs.facade-shadow=' + [typeof sensors.subscribe, typeof fetch.fetch, typeof websocket.connect, " +
                        "typeof device.isScreenOn, typeof files.read, mime.source, colors.source].join(','));\n" +
                        "console.log('x3d.cjs.facade-resolve=' + [require.resolve('sensors'), require.resolve('device'), " +
                        "require.resolve('mime').indexOf('/node_modules/mime/') >= 0, " +
                        "require.resolve('colors').indexOf('/node_modules/colors/') >= 0].join(','));\n" +
                        "console.log('x3d.cjs.facade-profile=' + [profile.moduleResolutionProfile.npmPrecedence, " +
                        "profile.moduleResolutionProfile.npmShadowableModules.join('+'), " +
                        "profile.moduleResolutionProfile.runtimeModulesShadowable, " +
                        "profile.moduleResolutionProfile.appliesTo.join('+')].join(','));\n"
        );
        for (String shadowed : new String[]{"sensors", "fetch", "websocket", "device", "files", "mime", "colors"}) {
            cjsShadowFiles.put(
                    "node_modules/" + shadowed + "/package.json",
                    "{\"name\":\"" + shadowed + "\",\"main\":\"index.js\"}\n"
            );
            cjsShadowFiles.put(
                    "node_modules/" + shadowed + "/index.js",
                    "module.exports = { source: 'npm' };\n"
            );
        }
        try (WorkspaceInvocation invocation = execute(
                "cjs-autojs6-facade-npm-shadow",
                "main.cjs",
                cjsShadowFiles,
                false
        )) {
            assertSucceeded(invocation.result, "x3d.cjs.facade-shadow=function,function,function,function,function,npm,npm");
            assertSucceeded(invocation.result, "x3d.cjs.facade-resolve=sensors,device,true,true");
            assertSucceeded(
                    invocation.result,
                    "x3d.cjs.facade-profile=npm_shims_only,axios+colors+mime+nanoid+opencc+undici,false,require+require.resolve+import"
            );
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.assertWorkspaceOutputCommitted();
        }
    }

    @Test
    public void x3e_05_nodeCompatCorpusV2PassesThroughPublishedBinder() throws Exception {
        // This is the plugin-owned, normalized-text migration of the former Host v2 corpus.
        // The companion Host ownership gate verifies that its copy and selector are absent.
        LinkedHashMap<String, String> files = loadNodeCompatCorpusV2Assets();
        assertEquals("node_compat_corpus_v2 asset count", 15, files.size());
        files.put("main.cjs", nodeCompatCorpusV2RunnerSource());

        LinkedHashMap<String, String> generatedModules = new LinkedHashMap<>();
        generatedModules.put("ts-cjs.cjs", files.get("ts-cjs.cts"));
        generatedModules.put("ts-esm.mjs", files.get("ts-esm.mts"));

        try (WorkspaceInvocation invocation = executeWithTypeScriptSnapshot(
                "node-compat-corpus-v2",
                "main.cjs",
                files,
                generatedModules,
                Arrays.asList("ts-cjs.cts", "ts-esm.mts"),
                false
        )) {
            assertSucceeded(invocation.result, "compat.v2.total=7");
            for (String check : NODE_COMPAT_CORPUS_V2_CHECKS) {
                String receipt = "compat.v2." + check + "=PASS";
                assertTrue(
                        "missing corpus receipt " + receipt + " in " +
                                invocation.result.getString(NodeJsRuntimeContract.KEY_STDOUT, ""),
                        invocation.result.getString(NodeJsRuntimeContract.KEY_STDOUT, "")
                                .contains(receipt)
                );
            }
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.assertWorkspaceOutputCommitted();
            assertEquals("v2 corpus unexpectedly used a module-source provider",
                    null, invocation.provider);
        }
    }

    @Test
    public void x3f_13_missingComputedCtsRequiresCompilerOutputBeforeProviderMaterialization()
            throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put(
                "main.cjs",
                "const stem = 'computed';\n" +
                        "const value = require('./' + stem + '.cts');\n" +
                        "console.log('x3f.cts=' + value.answer);\n"
        );
        byte[] rawPlaintext = (
                "const base: number = 41;\n" +
                        "module.exports = { answer: base + 1 };\n"
        ).getBytes(StandardCharsets.UTF_8);
        try (WorkspaceInvocation invocation = execute(
                "missing-computed-cts",
                "main.cjs",
                files,
                new LinkedHashMap<>(),
                true,
                rawPlaintext
        )) {
            assertFalse("Provider raw TypeScript unexpectedly succeeded",
                    invocation.result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            assertEquals(
                    NodeJsRuntimeContract.ERROR_TYPESCRIPT_COMPILER_REQUIRED,
                    invocation.result.getString(NodeJsRuntimeContract.KEY_ERROR_CODE)
            );
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.provider.assertHealthyAndNotCalled();
            assertNativeValue(invocation.result,
                    "embedded_script.module_provider.missing_candidate_request_count", "0");
        }
    }

    private static LinkedHashMap<String, String> loadNodeCompatCorpusV2Assets()
            throws IOException {
        return loadNodeCompatCorpusAssets(
                NODE_COMPAT_CORPUS_V2_ASSET_ROOT,
                NODE_COMPAT_CORPUS_V2_FILES,
                "node_compat_corpus_v2"
        );
    }

    private static LinkedHashMap<String, String> loadNodeCompatCorpusAssets(
            String assetRoot,
            List<String> relativePaths,
            String label
    ) throws IOException {
        AssetManager assets = InstrumentationRegistry.getInstrumentation()
                .getContext()
                .getAssets();
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        long totalBytes = 0L;
        for (String relativePath : relativePaths) {
            String assetPath = assetRoot + "/" + relativePath;
            byte[] content;
            try (InputStream input = assets.open(assetPath);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count == 0) continue;
                    totalBytes += count;
                    if (totalBytes > WORKSPACE_MAX_BYTES) {
                        throw new IOException(label + " exceeds the workspace budget");
                    }
                    output.write(buffer, 0, count);
                }
                content = output.toByteArray();
            }
            files.put(relativePath, new String(content, StandardCharsets.UTF_8));
        }
        return files;
    }

    private static String nodeCompatCorpusV2RunnerSource() {
        return "(async () => {\n" +
                "  const assert = require('assert');\n" +
                "  const syncEsm = require('./sync-esm.mjs');\n" +
                "  assert.strictEqual(syncEsm.named, 'sync-esm');\n" +
                "  assert.deepStrictEqual(syncEsm.default, { value: 'sync-esm', count: 2 });\n" +
                "  console.log('compat.v2.require_esm=PASS');\n" +
                "  let asyncRejected = false;\n" +
                "  try { require('./async-esm.mjs'); } catch (error) {\n" +
                "    asyncRejected = error && error.code === 'ERR_REQUIRE_ASYNC_MODULE';\n" +
                "  }\n" +
                "  assert.strictEqual(asyncRejected, true);\n" +
                "  console.log('compat.v2.tla_rejection=PASS');\n" +
                "  const cycle = require('./cjs-cycle.cjs');\n" +
                "  assert.deepStrictEqual(cycle.result(), {\n" +
                "    fromEsm: 'esm-cycle', esmSawCjs: 'cjs-cycle', esmSawDone: false,\n" +
                "    seen: 'esm-cycle', done: true,\n" +
                "  });\n" +
                "  console.log('compat.v2.esm_cjs_cycle=PASS');\n" +
                "  const harness = await import('./esm-harness.mjs');\n" +
                "  assert.strictEqual(harness.default.json, 'json-attrs/dynamic-json');\n" +
                "  assert.strictEqual(harness.default.jsonCacheShared, true);\n" +
                "  assert.strictEqual(harness.default.customCondition, 'autojs6');\n" +
                "  assert.strictEqual(harness.default.tsMixed, 'mts/cts');\n" +
                "  assert.strictEqual(harness.default.metaResolve, true);\n" +
                "  console.log('compat.v2.json_attributes=PASS');\n" +
                "  console.log('compat.v2.import_meta_resolve=PASS');\n" +
                "  console.log('compat.v2.package_custom_condition=PASS');\n" +
                "  console.log('compat.v2.typescript_mixed_graph=PASS');\n" +
                "  console.log('compat.v2.total=7');\n" +
                "})().catch((error) => {\n" +
                "  console.error('compat.v2.error=' +\n" +
                "    (error && (error.stack || error.message) || error));\n" +
                "  process.exitCode = 1;\n" +
                "});\n";
    }

    private WorkspaceInvocation execute(
            String label,
            String entryName,
            LinkedHashMap<String, String> sourceFiles,
            boolean withPlaintextProvider
    ) throws Exception {
        return execute(
                label,
                entryName,
                sourceFiles,
                new LinkedHashMap<>(),
                withPlaintextProvider,
                null,
                null,
                SCRIPT_TIMEOUT_MS
        );
    }

    private WorkspaceInvocation execute(
            String label,
            String entryName,
            LinkedHashMap<String, String> sourceFiles,
            boolean withPlaintextProvider,
            long timeoutMs
    ) throws Exception {
        return execute(
                label,
                entryName,
                sourceFiles,
                new LinkedHashMap<>(),
                withPlaintextProvider,
                null,
                null,
                timeoutMs
        );
    }

    private WorkspaceInvocation execute(
            String label,
            String entryName,
            LinkedHashMap<String, String> sourceFiles,
            LinkedHashMap<String, String> moduleSourceFiles,
            boolean withPlaintextProvider
    ) throws Exception {
        return execute(
                label,
                entryName,
                sourceFiles,
                moduleSourceFiles,
                withPlaintextProvider,
                null,
                null,
                SCRIPT_TIMEOUT_MS
        );
    }

    private WorkspaceInvocation execute(
            String label,
            String entryName,
            LinkedHashMap<String, String> sourceFiles,
            LinkedHashMap<String, String> moduleSourceFiles,
            boolean withPlaintextProvider,
            byte[] missingPlaintextSource
    ) throws Exception {
        return execute(
                label,
                entryName,
                sourceFiles,
                moduleSourceFiles,
                withPlaintextProvider,
                missingPlaintextSource,
                null,
                SCRIPT_TIMEOUT_MS
        );
    }

    private WorkspaceInvocation executeWithTypeScriptSnapshot(
            String label,
            String entryName,
            LinkedHashMap<String, String> sourceFiles,
            LinkedHashMap<String, String> moduleSourceFiles,
            List<String> precompiledSourceNames,
            boolean withPlaintextProvider
    ) throws Exception {
        return execute(
                label,
                entryName,
                sourceFiles,
                moduleSourceFiles,
                withPlaintextProvider,
                null,
                null,
                SCRIPT_TIMEOUT_MS,
                true,
                precompiledSourceNames,
                false
        );
    }

    private WorkspaceInvocation execute(
            String label,
            String entryName,
            LinkedHashMap<String, String> sourceFiles,
            LinkedHashMap<String, String> moduleSourceFiles,
            boolean withPlaintextProvider,
            byte[] missingPlaintextSource,
            LinkedHashMap<String, ProviderAction> exactProviderActions,
            long timeoutMs
    ) throws Exception {
        return execute(
                label,
                entryName,
                sourceFiles,
                moduleSourceFiles,
                withPlaintextProvider,
                missingPlaintextSource,
                exactProviderActions,
                timeoutMs,
                false,
                Collections.emptyList(),
                false
        );
    }

    private WorkspaceInvocation execute(
            String label,
            String entryName,
            LinkedHashMap<String, String> sourceFiles,
            LinkedHashMap<String, String> moduleSourceFiles,
            boolean withPlaintextProvider,
            byte[] missingPlaintextSource,
            LinkedHashMap<String, ProviderAction> exactProviderActions,
            long timeoutMs,
            boolean typeScriptPrecompiledSnapshot,
            List<String> precompiledSourceNames,
            boolean workerThreadsEnabled
    ) throws Exception {
        String executionId = "x3d-" + label + "-" + UUID.randomUUID();
        File invocationRoot = new File(
                targetContext.getCacheDir(),
                "node-plugin-android-conformance/" + executionId
        );
        File sandboxRoot = new File(invocationRoot, "sandbox");
        File transportRoot = new File(invocationRoot, "transport");
        ensureDirectory(sandboxRoot);
        ensureDirectory(transportRoot);

        try {
            for (Map.Entry<String, String> entry : sourceFiles.entrySet()) {
                File target = containedFile(sandboxRoot, entry.getKey());
                ensureDirectory(target.getParentFile());
                try (FileOutputStream output = new FileOutputStream(target)) {
                    output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                }
            }
            LinkedHashMap<String, String> moduleSources = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : moduleSourceFiles.entrySet()) {
                moduleSources.put(
                        containedFile(sandboxRoot, entry.getKey()).getAbsolutePath(),
                        entry.getValue()
                );
            }

            File inputArchive = new File(transportRoot, "workspace-input.zip");
            File outputArchive = new File(transportRoot, "workspace-output.zip");
            writeWorkspaceInputArchive(inputArchive, sourceFiles);
            assertTrue("Unable to create workspace output archive", outputArchive.createNewFile());

            int runtimePid = requireRemoteRuntimePid();
            PlaintextProvider provider = withPlaintextProvider
                    ? new PlaintextProvider(
                            executionId,
                            sandboxRoot,
                            runtimePid,
                            missingPlaintextSource,
                            plaintextProviderAdvertisedSourceMaxBytes.get()
                    )
                    : null;
            ExactGraphProvider exactGraphProvider = exactProviderActions == null
                    ? null
                    : new ExactGraphProvider(
                            executionId,
                            sandboxRoot,
                            runtimePid,
                            exactProviderActions
                    );
            // Output is forwarded by the dispatcher; module providers are called directly by the worker.
            RecordingCallback callback = new RecordingCallback(boundRuntime.runtime.getRuntimeInfo().getInt("dispatcherPid", runtimePid));
            Bundle request = runtimeRequest(
                    executionId,
                    new File(sandboxRoot, entryName),
                    sandboxRoot,
                    sourceFiles.get(entryName),
                    moduleSources,
                    provider == null ? exactGraphProvider : provider,
                    timeoutMs
            );
            request.putBoolean(
                    NodeJsRuntimeContract.KEY_TYPESCRIPT_PRECOMPILED_SNAPSHOT,
                    typeScriptPrecompiledSnapshot
            );
            request.putBoolean(NodeJsRuntimeContract.KEY_WORKER_THREADS_ENABLED, workerThreadsEnabled);
            String[] absolutePrecompiledSourceNames = new String[precompiledSourceNames.size()];
            for (int index = 0; index < precompiledSourceNames.size(); index++) {
                absolutePrecompiledSourceNames[index] = containedFile(
                        sandboxRoot,
                        precompiledSourceNames.get(index)
                ).getAbsolutePath();
            }
            request.putStringArray(
                    NodeJsRuntimeContract.KEY_TYPESCRIPT_PRECOMPILED_SOURCE_NAMES,
                    absolutePrecompiledSourceNames
            );
            if (exactGraphProvider != null) {
                assertEquals("X3h initial moduleSources must be zero", 0,
                        request.getStringArray(NodeJsRuntimeContract.KEY_MODULE_SOURCE_NAMES).length);
                assertEquals("X3h initial runtimeModuleSources must be zero", 0,
                        request.getStringArray(NodeJsRuntimeContract.KEY_RUNTIME_MODULE_SOURCE_NAMES).length);
            }

            ParcelFileDescriptor inputDescriptor = ParcelFileDescriptor.open(
                    inputArchive,
                    ParcelFileDescriptor.MODE_READ_ONLY
            );
            ParcelFileDescriptor outputDescriptor = ParcelFileDescriptor.open(
                    outputArchive,
                    ParcelFileDescriptor.MODE_READ_WRITE |
                            ParcelFileDescriptor.MODE_TRUNCATE
            );
            assertNotEquals(
                    "workspace archive transport reused one descriptor for input and output",
                    inputDescriptor.getFd(),
                    outputDescriptor.getFd()
            );
            request.putParcelable(
                    NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_INPUT_FD,
                    inputDescriptor
            );
            request.putParcelable(
                    NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_OUTPUT_FD,
                    outputDescriptor
            );

            Bundle result;
            try {
                result = boundRuntime.runtime.runScript(request, callback);
            } finally {
                inputDescriptor.close();
                outputDescriptor.close();
            }
            assertNotNull("plugin runtime returned a null result", result);
            return new WorkspaceInvocation(
                    invocationRoot,
                    outputArchive,
                    result,
                    callback,
                    provider,
                    exactGraphProvider
            );
        } catch (Throwable error) {
            deleteRecursively(invocationRoot);
            if (error instanceof Exception) throw (Exception) error;
            if (error instanceof Error) throw (Error) error;
            throw new AssertionError(error);
        }
    }

    private int requireRemoteRuntimePid() throws RemoteException {
        Bundle runtimeInfo = boundRuntime.runtime.getRuntimeInfo();
        assertRuntimeInfo(runtimeInfo);
        int runtimePid = runtimeInfo.getInt(NodeJsRuntimeContract.KEY_PID, -1);
        assertNotEquals(
                "runtime Binder unexpectedly stayed in the instrumentation process",
                Process.myPid(),
                runtimePid
        );
        return runtimePid;
    }

    private static Bundle runtimeRequest(
            String executionId,
            File sourceFile,
            File sandboxRoot,
            String source,
            LinkedHashMap<String, String> moduleSources,
            INodeJsModuleSourceProvider provider,
            long timeoutMs
    ) {
        Bundle request = new Bundle();
        request.putInt(
                NodeJsRuntimeContract.KEY_CONTRACT_VERSION,
                NodeJsRuntimeContract.CONTRACT_VERSION
        );
        request.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, executionId);
        request.putString(NodeJsRuntimeContract.KEY_SOURCE, source);
        request.putString(NodeJsRuntimeContract.KEY_SOURCE_NAME, sourceFile.getAbsolutePath());
        request.putString(
                NodeJsRuntimeContract.KEY_WORKING_DIRECTORY,
                sandboxRoot.getAbsolutePath()
        );
        request.putString(NodeJsRuntimeContract.KEY_SANDBOX_ROOT, sandboxRoot.getAbsolutePath());
        request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, timeoutMs);
        request.putString(NodeJsRuntimeContract.KEY_EXECUTION_MODE, "one_shot");
        request.putStringArray(NodeJsRuntimeContract.KEY_ENV_NAMES, new String[0]);
        request.putStringArray(NodeJsRuntimeContract.KEY_ENV_VALUES, new String[0]);
        request.putStringArray(
                NodeJsRuntimeContract.KEY_MODULE_SOURCE_NAMES,
                moduleSources.keySet().toArray(new String[0])
        );
        request.putStringArray(
                NodeJsRuntimeContract.KEY_MODULE_SOURCES,
                moduleSources.values().toArray(new String[0])
        );
        request.putStringArray(NodeJsRuntimeContract.KEY_RUNTIME_MODULE_SOURCE_NAMES, new String[0]);
        request.putStringArray(NodeJsRuntimeContract.KEY_RUNTIME_MODULE_SOURCES, new String[0]);
        request.putBoolean(NodeJsRuntimeContract.KEY_ESM_ENABLED, true);
        request.putBoolean(NodeJsRuntimeContract.KEY_DYNAMIC_IMPORT_ENABLED, true);
        request.putBoolean(NodeJsRuntimeContract.KEY_RAW_NODE_NETWORK_MODULES_ENABLED, false);
        request.putBoolean(NodeJsRuntimeContract.KEY_WORKER_THREADS_ENABLED, false);
        request.putBoolean(NodeJsRuntimeContract.KEY_CHILD_PROCESS_ENABLED, false);
        request.putBoolean(NodeJsRuntimeContract.KEY_JAVA_INTEROP_ENABLED, false);
        request.putInt(
                NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_TRANSPORT_VERSION,
                NodeJsRuntimeContract.WORKSPACE_ARCHIVE_TRANSPORT_CONTRACT_VERSION
        );
        request.putString(NodeJsRuntimeContract.KEY_WORKSPACE_RELATIVE_WORKING_DIRECTORY, "");
        request.putInt(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_MAX_FILES, WORKSPACE_MAX_FILES);
        request.putLong(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_MAX_BYTES, WORKSPACE_MAX_BYTES);
        if (provider != null) {
            request.putBinder(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER,
                    provider.asBinder()
            );
        }
        return request;
    }

    private static void writeWorkspaceInputArchive(
            File archive,
            LinkedHashMap<String, String> sourceFiles
    ) throws Exception {
        List<String> names = new ArrayList<>(sourceFiles.keySet());
        Collections.sort(names);
        JSONObject manifest = new JSONObject()
                .put("version", 1)
                .put("deleteEligibleFiles", new JSONArray(names));
        try (ZipOutputStream output = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(archive)))) {
            output.putNextEntry(new ZipEntry(INPUT_MANIFEST_PATH));
            output.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            for (String name : names) {
                output.putNextEntry(new ZipEntry(name));
                output.write(sourceFiles.get(name).getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }

    private static void assertRuntimeInfo(Bundle runtimeInfo) {
        assertNotNull(runtimeInfo);
        assertEquals(
                NodeJsRuntimeContract.CONTRACT_VERSION,
                runtimeInfo.getInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, -1)
        );
        assertEquals(
                NodeJsPluginIds.VARIANT_NODE_24_21,
                runtimeInfo.getString(NodeJsRuntimeContract.KEY_RUNTIME_SLOT)
        );
        assertTrue("Node version is blank",
                !runtimeInfo.getString(NodeJsRuntimeContract.KEY_NODE_VERSION, "").isEmpty());
        assertTrue(
                "native runtime did not pass its onCreate readiness gate: " +
                        runtimeInfo.getString("runtimeReadinessDetail", ""),
                runtimeInfo.getBoolean("runtimeReady")
        );
        assertTrue(runtimeInfo.getBoolean("dedicatedRuntimeProcess"));
        List<String> capabilities = Arrays.asList(
                runtimeInfo.getStringArray(NodeJsRuntimeContract.KEY_CAPABILITIES) == null
                        ? new String[0]
                        : runtimeInfo.getStringArray(NodeJsRuntimeContract.KEY_CAPABILITIES)
        );
        assertTrue(capabilities.contains(NodeJsRuntimeContract.CAPABILITY_NATIVE_EMBEDDED_RUNTIME));
        assertTrue(capabilities.contains(NodeJsRuntimeContract.CAPABILITY_BUNDLE_TRANSPORT));
        assertTrue(capabilities.contains(
                NodeJsRuntimeContract.CAPABILITY_SCOPED_WORKSPACE_ARCHIVE_TRANSPORT));
    }

    private static void assertSucceeded(Bundle result, String expectedOutput) {
        assertEquals(
                NodeJsRuntimeContract.CONTRACT_VERSION,
                result.getInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, -1)
        );
        assertTrue(
                result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "") + "\n" +
                        result.getString(NodeJsRuntimeContract.KEY_STDERR, "") + "\n" +
                        "native diagnostics=" + nativeValues(result),
                result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED)
        );
        assertEquals(0, result.getInt(NodeJsRuntimeContract.KEY_EXIT_CODE, -1));
        assertTrue(
                result.getString(NodeJsRuntimeContract.KEY_STDOUT, ""),
                result.getString(NodeJsRuntimeContract.KEY_STDOUT, "").contains(expectedOutput)
        );
        assertTrue(result.getInt(NodeJsRuntimeContract.KEY_PID, -1) > 0);
        assertTrue(
                result.getString(NodeJsRuntimeContract.KEY_PROCESS_NAME, ""),
                result.getString(NodeJsRuntimeContract.KEY_PROCESS_NAME, "")
                        .matches(".*:nodejs_runtime[01]")
        );
        assertNativeValue(result, "process_runtime.healthy", "true");
        assertNativeValue(result, "execution.teardown_clean", "true");
        assertNativeValue(
                result,
                "embedded_script.runtime_plugin.workspace.contract_version",
                Integer.toString(NodeJsRuntimeContract.WORKSPACE_ARCHIVE_TRANSPORT_CONTRACT_VERSION)
        );
        assertNativeValue(
                result,
                "embedded_script.runtime_plugin.workspace.legacy_directory_scan_ran",
                "false"
        );
        assertNativeValue(
                result,
                "embedded_script.runtime_plugin.workspace.mapped",
                "true"
        );
        assertNativeValue(
                result,
                "embedded_script.runtime_plugin.workspace.status",
                "committed"
        );
        assertNativeValue(
                result,
                "embedded_script.runtime_plugin.workspace.input_directory_scan_count",
                "0"
        );
        assertNativeValue(
                result,
                "embedded_script.runtime_plugin.workspace.output_private_workspace_scan_count",
                "1"
        );
    }

    private static void assertNativeValue(Bundle result, String key, String expected) {
        Map<String, String> values = nativeValues(result);
        assertEquals("native diagnostic " + key + " in " + values, expected, values.get(key));
    }

    private static Map<String, String> nativeValues(Bundle result) {
        return nativeValues(result.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD));
    }

    private static Map<String, String> nativeValues(String[] payload) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        if (payload == null) return values;
        for (String entry : payload) {
            if (entry == null) continue;
            int separator = entry.indexOf('=');
            if (separator <= 0) continue;
            values.put(entry.substring(0, separator), entry.substring(separator + 1));
        }
        return values;
    }

    private static File containedFile(File root, String relativePath) throws IOException {
        if (relativePath == null || relativePath.isEmpty() || relativePath.startsWith("/") ||
                relativePath.startsWith("\\") || relativePath.contains("\u0000")) {
            throw new IOException("Invalid fixture path: " + relativePath);
        }
        File canonicalRoot = root.getCanonicalFile();
        File target = new File(canonicalRoot, relativePath).getCanonicalFile();
        String prefix = canonicalRoot.getAbsolutePath() + File.separator;
        if (!target.getAbsolutePath().startsWith(prefix)) {
            throw new IOException("Fixture escapes its workspace root: " + relativePath);
        }
        return target;
    }

    private static File runtimeFilePreservingCredentialAlias(File root, String relativePath)
            throws IOException {
        if (relativePath == null || relativePath.isEmpty() || relativePath.startsWith("/") ||
                relativePath.indexOf('\\') >= 0 || relativePath.indexOf('\0') >= 0) {
            throw new IOException("Invalid runtime fixture path: " + relativePath);
        }
        File target = root.getAbsoluteFile();
        for (String segment : relativePath.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IOException("Runtime fixture path contains traversal: " + relativePath);
            }
            target = new File(target, segment);
        }
        String rootPath = root.getAbsoluteFile().getAbsolutePath();
        String targetPath = target.getAbsolutePath();
        if (!targetPath.startsWith(rootPath + File.separator)) {
            throw new IOException("Runtime fixture escapes its workspace root: " + relativePath);
        }
        // Do not canonicalize this path: Android may rewrite the credential-storage
        // alias (/data/user/0 versus /data/data), while production deliberately
        // compares against the exact runtimeSandboxRoot absolute-path identity.
        return target;
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (directory == null || (!directory.isDirectory() && !directory.mkdirs())) {
            throw new IOException("Unable to create directory: " + directory);
        }
    }

    private static void deleteRecursively(File target) {
        if (target == null || !target.exists()) return;
        File[] children = target.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        // Every target is a UUID-named child of this test's cache root.
        target.delete();
    }

    private static final class DirectWorkspaceSession implements AutoCloseable {
        final File invocationRoot;
        final PluginWorkspaceArchiveSession session;

        DirectWorkspaceSession(File invocationRoot, PluginWorkspaceArchiveSession session) {
            this.invocationRoot = invocationRoot;
            this.session = session;
        }

        File runtimeFile(String relativePath) throws IOException {
            return runtimeFilePreservingCredentialAlias(
                    new File(session.sandboxRoot()), relativePath);
        }

        File writePrivateSource(byte[] source) throws IOException {
            File privateSource = new File(invocationRoot, "private/provider.source");
            ensureDirectory(privateSource.getParentFile());
            try (FileOutputStream output = new FileOutputStream(privateSource)) {
                output.write(source);
                output.getFD().sync();
            }
            return privateSource;
        }

        void assertDiagnostic(String suffix, String expected) {
            Map<String, String> values = nativeValues(session.nativePayload());
            String key = "embedded_script.runtime_plugin.workspace." + suffix;
            assertEquals("workspace diagnostic " + key + " in " + values, expected, values.get(key));
        }

        @Override
        public void close() {
            session.close();
            deleteRecursively(invocationRoot);
        }
    }

    private static final class BoundRuntime implements AutoCloseable {
        final Context context;
        final ServiceConnection connection;
        final INodeJsRuntimePlugin runtime;
        private boolean closed;

        private BoundRuntime(
                Context context,
                ServiceConnection connection,
                INodeJsRuntimePlugin runtime
        ) {
            this.context = context;
            this.connection = connection;
            this.runtime = runtime;
        }

        static BoundRuntime bind(Context context) throws Exception {
            CountDownLatch connected = new CountDownLatch(1);
            AtomicReference<IBinder> binder = new AtomicReference<>();
            AtomicReference<String> failure = new AtomicReference<>();
            ServiceConnection connection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    binder.set(service);
                    connected.countDown();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    failure.compareAndSet(null, "service disconnected before conformance completed");
                }

                @Override
                public void onBindingDied(ComponentName name) {
                    failure.compareAndSet(null, "service binding died");
                    connected.countDown();
                }

                @Override
                public void onNullBinding(ComponentName name) {
                    failure.compareAndSet(null, "service returned a null binding");
                    connected.countDown();
                }
            };
            ComponentName component = new ComponentName(
                    context.getPackageName(),
                    context.getPackageName() + ".NodeJsRuntimePluginService"
            );
            Intent intent = new Intent().setComponent(component);
            boolean accepted = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            if (!accepted) {
                throw new AssertionError("actionless explicit runtime bind was rejected: " + component);
            }
            if (!connected.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                context.unbindService(connection);
                throw new AssertionError("runtime bind timed out after " + BIND_TIMEOUT_MS + " ms");
            }
            if (failure.get() != null || binder.get() == null) {
                context.unbindService(connection);
                throw new AssertionError("runtime bind failed: " + failure.get());
            }
            INodeJsRuntimePlugin runtime = INodeJsRuntimePlugin.Stub.asInterface(binder.get());
            if (runtime == null || !runtime.asBinder().isBinderAlive()) {
                context.unbindService(connection);
                throw new AssertionError("runtime AIDL proxy is unavailable");
            }
            return new BoundRuntime(context, connection, runtime);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            context.unbindService(connection);
        }
    }

    private static final class RecordingCallback extends INodeJsRuntimeCallback.Stub {
        private final List<Bundle> events = Collections.synchronizedList(new ArrayList<>());
        private final int expectedRuntimePid;
        private final AtomicInteger callerPid = new AtomicInteger(-1);
        private final AtomicReference<String> callerFailure = new AtomicReference<>();

        RecordingCallback(int expectedRuntimePid) {
            this.expectedRuntimePid = expectedRuntimePid;
        }

        @Override
        public void onEvent(Bundle event) {
            recordCallerPid("callback");
            events.add(event == null ? new Bundle() : new Bundle(event));
        }

        String stderr() {
            StringBuilder text = new StringBuilder();
            synchronized (events) {
                for (Bundle event : events) {
                    if (NodeJsRuntimeContract.EVENT_STDERR.equals(
                            event.getString(NodeJsRuntimeContract.KEY_EVENT_TYPE))) {
                        text.append(event.getString(NodeJsRuntimeContract.KEY_EVENT_TEXT, ""));
                    }
                }
            }
            return text.toString();
        }

        void assertOneStartedAndOneTerminalEvent() {
            List<Bundle> snapshot;
            synchronized (events) {
                snapshot = new ArrayList<>(events);
            }
            assertTrue("callback emitted no events", !snapshot.isEmpty());
            int started = 0;
            int finished = 0;
            for (Bundle event : snapshot) {
                String type = event.getString(NodeJsRuntimeContract.KEY_EVENT_TYPE, "");
                if (NodeJsRuntimeContract.EVENT_STARTED.equals(type)) started++;
                if (NodeJsRuntimeContract.EVENT_FINISHED.equals(type)) finished++;
            }
            assertEquals("started callback count in " + describe(snapshot), 1, started);
            assertEquals("terminal callback count in " + describe(snapshot), 1, finished);
            assertEquals(
                    "terminal callback was not last in " + describe(snapshot),
                    NodeJsRuntimeContract.EVENT_FINISHED,
                    snapshot.get(snapshot.size() - 1)
                            .getString(NodeJsRuntimeContract.KEY_EVENT_TYPE)
            );
            assertEquals("callback Binder caller mismatch", null, callerFailure.get());
            assertEquals("callback Binder caller PID", expectedRuntimePid, callerPid.get());
            assertNotEquals(
                    "callback unexpectedly originated in the instrumentation process",
                    Process.myPid(),
                    callerPid.get()
            );
        }

        private void recordCallerPid(String operation) {
            int observedPid = Binder.getCallingPid();
            callerPid.compareAndSet(-1, observedPid);
            if (observedPid != expectedRuntimePid || callerPid.get() != observedPid) {
                callerFailure.compareAndSet(
                        null,
                        operation + " callerPid=" + observedPid +
                                " firstCallerPid=" + callerPid.get() +
                                " expectedRuntimePid=" + expectedRuntimePid
                );
            }
        }

        private static String describe(List<Bundle> events) {
            List<String> types = new ArrayList<>();
            for (Bundle event : events) {
                types.add(event.getString(NodeJsRuntimeContract.KEY_EVENT_TYPE, "missing"));
            }
            return types.toString();
        }
    }

    private static final class ProviderAction {
        final String status;
        final byte[] source;

        private ProviderAction(String status, byte[] source) {
            this.status = status;
            this.source = source == null ? new byte[0] : source.clone();
        }

        static ProviderAction notFound() {
            return new ProviderAction(
                    NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_NOT_FOUND,
                    null
            );
        }

        static ProviderAction decrypted(String source) {
            return new ProviderAction(
                    NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_DECRYPTED,
                    source.getBytes(StandardCharsets.UTF_8)
            );
        }
    }

    private static final class ExactGraphProvider extends INodeJsModuleSourceProvider.Stub {
        private final String executionId;
        private final File root;
        private final String rootPrefix;
        private final int expectedRuntimePid;
        private final LinkedHashMap<String, ProviderAction> actions;
        private final List<String> events = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger callerPid = new AtomicInteger(-1);
        private final AtomicReference<String> failure = new AtomicReference<>(null);

        ExactGraphProvider(
                String executionId,
                File root,
                int expectedRuntimePid,
                LinkedHashMap<String, ProviderAction> actions
        ) throws IOException {
            this.executionId = executionId;
            this.root = root.getCanonicalFile();
            this.rootPrefix = this.root.getAbsolutePath() + File.separator;
            this.expectedRuntimePid = expectedRuntimePid;
            this.actions = new LinkedHashMap<>(actions);
        }

        @Override
        public Bundle resolveModuleSource(Bundle request) {
            recordCallerPid();
            long startedAt = SystemClock.elapsedRealtime();
            String requestId = request == null ? "" : request.getString(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_REQUEST_ID,
                    ""
            );
            String operation = request == null ? "" : request.getString(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_OPERATION,
                    ""
            );
            Bundle response = providerResponse(requestId, operation, startedAt);
            if (request == null || request.getInt(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION,
                    0
            ) != NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_CONTRACT_VERSION) {
                return providerFailure(response, "invalid provider contract");
            }
            if (!executionId.equals(request.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID, ""))) {
                return providerFailure(response, "execution identity mismatch");
            }
            if (!NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_OPERATION_RESOLVE_EXISTING
                    .equals(operation)) {
                return providerFailure(response, "unexpected provider operation: " + operation);
            }
            long deadline = request.getLong(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_DEADLINE_ELAPSED_REALTIME_MS,
                    0L
            );
            if (deadline <= SystemClock.elapsedRealtime()) {
                return providerFailure(response, "provider deadline missing or expired");
            }
            String requestedPath = request.getString(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_PATH,
                    ""
            );
            try {
                File requested = new File(requestedPath).getCanonicalFile();
                String absolute = requested.getAbsolutePath();
                if (!absolute.startsWith(rootPrefix)) {
                    return providerFailure(response, "requested path is outside the authorized root");
                }
                String relative = absolute.substring(rootPrefix.length())
                        .replace(File.separatorChar, '/');
                ProviderAction action = actions.get(relative);
                if (action == null) {
                    return providerFailure(response, "unexpected exact provider path: " + relative);
                }
                if (requested.exists()) {
                    return providerFailure(response, "exact provider candidate unexpectedly exists: " + relative);
                }
                events.add(operation + ":" + relative + ":" + action.status);
                response.putString(
                        NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_STATUS,
                        action.status
                );
                response.putString(
                        NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_RESOLVED_PATH,
                        absolute
                );
                if (NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_DECRYPTED
                        .equals(action.status)) {
                    response.putLong(
                            NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_SOURCE_BYTES,
                            action.source.length
                    );
                    response.putParcelable(
                            NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_SOURCE_FD,
                            sourcePipe(action.source)
                    );
                }
                response.putLong(
                        NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_ELAPSED_MS,
                        Math.max(0L, SystemClock.elapsedRealtime() - startedAt)
                );
                return response;
            } catch (IOException error) {
                return providerFailure(response, "exact provider failed: " + error.getMessage());
            }
        }

        @Override
        public Bundle getNativeDiagnostics() {
            Bundle diagnostics = new Bundle();
            diagnostics.putStringArray(
                    NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                    new String[]{
                            "x3h.exact_provider.event_count=" + events.size(),
                            "x3h.exact_provider.failure=" + failure.get()
                    }
            );
            return diagnostics;
        }

        @Override
        public void cancel(String reason) {
            // Request-scoped terminal cleanup; no external provider state is retained.
        }

        void assertHealthyAndExactEvents(List<String> expected) {
            assertEquals("exact graph provider failure", null, failure.get());
            assertEquals("exact graph provider Binder caller PID", expectedRuntimePid, callerPid.get());
            assertNotEquals(
                    "exact graph provider unexpectedly ran in instrumentation process",
                    Process.myPid(),
                    callerPid.get()
            );
            synchronized (events) {
                assertEquals("exact provider operation/path/status order", expected, events);
            }
        }

        private void recordCallerPid() {
            int observedPid = Binder.getCallingPid();
            callerPid.compareAndSet(-1, observedPid);
            if (observedPid != expectedRuntimePid || callerPid.get() != observedPid) {
                failure.compareAndSet(
                        null,
                        "provider callerPid=" + observedPid +
                                " firstCallerPid=" + callerPid.get() +
                                " expectedRuntimePid=" + expectedRuntimePid
                );
            }
        }

        private ParcelFileDescriptor sourcePipe(byte[] source) throws IOException {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            Thread writer = new Thread(() -> {
                try (FileOutputStream output =
                             new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    output.write(source);
                    output.flush();
                } catch (Throwable error) {
                    failure.compareAndSet(null, "exact provider pipe failed: " + error.getMessage());
                }
            });
            writer.setName("X3hExactGraphProviderPipe");
            writer.setDaemon(true);
            writer.start();
            return pipe[0];
        }

        private static Bundle providerResponse(
                String requestId,
                String operation,
                long startedAt
        ) {
            Bundle response = new Bundle();
            response.putInt(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION,
                    NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_CONTRACT_VERSION
            );
            response.putString(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_REQUEST_ID,
                    requestId
            );
            response.putString(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_OPERATION,
                    operation
            );
            response.putLong(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_SOURCE_BYTES, 0L);
            response.putString(NodeJsRuntimeContract.KEY_ERROR_CODE, "");
            response.putString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "");
            response.putString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_DENIAL_REASON, "");
            response.putLong(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_ELAPSED_MS,
                    Math.max(0L, SystemClock.elapsedRealtime() - startedAt)
            );
            return response;
        }

        private Bundle providerFailure(Bundle response, String message) {
            failure.compareAndSet(null, message);
            response.putString(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_STATUS,
                    NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_FAILED
            );
            response.putString(
                    NodeJsRuntimeContract.KEY_ERROR_CODE,
                    NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_FAILED
            );
            response.putString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, message);
            return response;
        }
    }

    private static final class PlaintextProvider extends INodeJsModuleSourceProvider.Stub {
        private final String executionId;
        private final File root;
        private final String rootPrefix;
        private final int expectedRuntimePid;
        private final byte[] missingPlaintextSource;
        private final long advertisedSourceMaxBytes;
        private final AtomicInteger resolveCount = new AtomicInteger();
        private final AtomicInteger callerPid = new AtomicInteger(-1);
        private final AtomicReference<String> lastResolvedPath = new AtomicReference<>("");
        private final AtomicReference<String> lastOperation = new AtomicReference<>("");
        private final AtomicReference<String> failure = new AtomicReference<>();

        PlaintextProvider(String executionId, File root, int expectedRuntimePid) throws IOException {
            this(executionId, root, expectedRuntimePid, null);
        }

        PlaintextProvider(
                String executionId,
                File root,
                int expectedRuntimePid,
                byte[] missingPlaintextSource
        ) throws IOException {
            this(executionId, root, expectedRuntimePid, missingPlaintextSource, -1L);
        }

        PlaintextProvider(
                String executionId,
                File root,
                int expectedRuntimePid,
                byte[] missingPlaintextSource,
                long advertisedSourceMaxBytes
        ) throws IOException {
            this.executionId = executionId;
            this.root = root.getCanonicalFile();
            this.rootPrefix = this.root.getAbsolutePath() + File.separator;
            this.expectedRuntimePid = expectedRuntimePid;
            this.missingPlaintextSource = missingPlaintextSource == null
                    ? null
                    : missingPlaintextSource.clone();
            this.advertisedSourceMaxBytes = advertisedSourceMaxBytes;
        }

        @Override
        public Bundle resolveModuleSource(Bundle request) {
            recordCallerPid();
            long startedAt = SystemClock.elapsedRealtime();
            String requestId = request == null ? "" : request.getString(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_REQUEST_ID,
                    ""
            );
            String operation = request == null ? "" : request.getString(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_OPERATION,
                    ""
            );
            Bundle response = providerResponse(requestId, operation, startedAt);
            if (request == null || request.getInt(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION,
                    0
            ) != NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_CONTRACT_VERSION) {
                return providerFailure(response, "invalid provider contract");
            }
            if (!executionId.equals(request.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID, ""))) {
                return providerFailure(response, "execution identity mismatch");
            }
            long deadline = request.getLong(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_DEADLINE_ELAPSED_REALTIME_MS,
                    0L
            );
            if (deadline <= SystemClock.elapsedRealtime()) {
                return providerFailure(response, "provider deadline missing or expired");
            }
            String requestedPath = request.getString(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_PATH,
                    ""
            );
            try {
                File requested = new File(requestedPath).getCanonicalFile();
                if (!requested.getAbsolutePath().startsWith(rootPrefix)) {
                    return providerFailure(response, "requested path is outside the authorized root");
                }
                boolean materializeMissing = missingPlaintextSource != null;
                boolean resolveExistingOperation = NodeJsRuntimeContract
                        .MODULE_SOURCE_PROVIDER_OPERATION_RESOLVE_EXISTING.equals(operation);
                boolean materializeOperation = NodeJsRuntimeContract
                        .MODULE_SOURCE_PROVIDER_OPERATION_MATERIALIZE_MISSING_PLAINTEXT.equals(operation);
                if (!resolveExistingOperation && !materializeOperation) {
                    return providerFailure(response, "unexpected provider operation: " + operation);
                }
                if (resolveExistingOperation && !requested.isFile()) {
                    // Runtime policy metadata preflight (project.json/package.json) and the
                    // missing-candidate probe both resolve absent paths first. Mirror the
                    // published host provider: unknown absent paths are not_found, while the
                    // candidate this provider can materialize answers not_encrypted so the
                    // runtime follows up with materialize_missing_plaintext.
                    String requestedName = requested.getName();
                    boolean metadataPath = "project.json".equals(requestedName)
                            || "package.json".equals(requestedName);
                    if (materializeMissing && !metadataPath) {
                        response.putString(
                                NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_STATUS,
                                NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_NOT_ENCRYPTED
                        );
                        response.putString(
                                NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_RESOLVED_PATH,
                                requested.getAbsolutePath()
                        );
                    } else {
                        response.putString(
                                NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_STATUS,
                                NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_NOT_FOUND
                        );
                    }
                    response.putLong(
                            NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_ELAPSED_MS,
                            Math.max(0L, SystemClock.elapsedRealtime() - startedAt)
                    );
                    return response;
                }
                if (materializeOperation != materializeMissing) {
                    return providerFailure(response, "unexpected provider operation: " + operation);
                }
                if (materializeMissing && requested.exists()) {
                    return providerFailure(response, "materialization candidate unexpectedly exists");
                }
                resolveCount.incrementAndGet();
                lastResolvedPath.set(requested.getAbsolutePath());
                lastOperation.set(operation);
                response.putString(
                        NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_STATUS,
                        materializeMissing
                                ? NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_PLAINTEXT
                                : NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_NOT_ENCRYPTED
                );
                response.putString(
                        NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_RESOLVED_PATH,
                        requested.getAbsolutePath()
                );
                if (materializeMissing) {
                    response.putLong(
                            NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_SOURCE_BYTES,
                            missingPlaintextSource.length
                    );
                    response.putParcelable(
                            NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_SOURCE_FD,
                            sourcePipe(missingPlaintextSource)
                    );
                } else {
                    response.putLong(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_SOURCE_BYTES, 0L);
                }
                response.putLong(
                        NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_ELAPSED_MS,
                        Math.max(0L, SystemClock.elapsedRealtime() - startedAt)
                );
                return response;
            } catch (IOException error) {
                return providerFailure(response, "canonicalization failed: " + error.getMessage());
            }
        }

        @Override
        public Bundle getNativeDiagnostics() {
            Bundle diagnostics = new Bundle();
            diagnostics.putStringArray(
                    NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                    new String[]{
                            "x3d.plaintext_provider.resolve_count=" + resolveCount.get(),
                            "x3d.plaintext_provider.failure=" + failure.get()
                    }
            );
            if (advertisedSourceMaxBytes > 0L) {
                // Mirrors NodeJsEncryptedModuleSourceProvider: the host publishes its transport sizes.
                diagnostics.putInt(
                        NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_SOURCE_MAX_BYTES,
                        (int) advertisedSourceMaxBytes
                );
                diagnostics.putLong(
                        NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_TOTAL_MAX_BYTES,
                        advertisedSourceMaxBytes * 4L
                );
                diagnostics.putInt(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_MAX_COUNT, 64);
                diagnostics.putInt(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_REQUEST_MAX_COUNT, 64);
            }
            return diagnostics;
        }

        @Override
        public void cancel(String reason) {
            // The request-scoped transport always closes its provider at terminal completion.
        }

        void assertHealthyAndNotCalled() {
            assertEquals("plaintext provider failure", null, failure.get());
            assertEquals("plaintext provider resolve count", 0, resolveCount.get());
            assertEquals("plaintext provider last resolved path", "", lastResolvedPath.get());
            assertEquals("plaintext provider last operation", "", lastOperation.get());
        }

        private void recordCallerPid() {
            int observedPid = Binder.getCallingPid();
            callerPid.compareAndSet(-1, observedPid);
            if (observedPid != expectedRuntimePid || callerPid.get() != observedPid) {
                failure.compareAndSet(
                        null,
                        "provider callerPid=" + observedPid +
                                " firstCallerPid=" + callerPid.get() +
                                " expectedRuntimePid=" + expectedRuntimePid
                );
            }
        }

        private static Bundle providerResponse(
                String requestId,
                String operation,
                long startedAt
        ) {
            Bundle response = new Bundle();
            response.putInt(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION,
                    NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_CONTRACT_VERSION
            );
            response.putString(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_REQUEST_ID,
                    requestId
            );
            response.putString(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_OPERATION,
                    operation
            );
            response.putLong(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_SOURCE_BYTES, 0L);
            response.putString(NodeJsRuntimeContract.KEY_ERROR_CODE, "");
            response.putString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "");
            response.putString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_DENIAL_REASON, "");
            response.putLong(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_ELAPSED_MS,
                    Math.max(0L, SystemClock.elapsedRealtime() - startedAt)
            );
            return response;
        }

        private ParcelFileDescriptor sourcePipe(byte[] source) throws IOException {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            Thread writer = new Thread(() -> {
                try (FileOutputStream output =
                             new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    output.write(source);
                    output.flush();
                } catch (Throwable error) {
                    failure.compareAndSet(null, "plaintext pipe failed: " + error.getMessage());
                }
            });
            writer.setName("X3fPlaintextProviderPipe");
            writer.setDaemon(true);
            writer.start();
            return pipe[0];
        }

        private Bundle providerFailure(Bundle response, String message) {
            failure.compareAndSet(null, message);
            response.putString(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_STATUS,
                    NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_FAILED
            );
            response.putString(
                    NodeJsRuntimeContract.KEY_ERROR_CODE,
                    NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_FAILED
            );
            response.putString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, message);
            return response;
        }
    }

    private static final class WorkspaceInvocation implements AutoCloseable {
        final File invocationRoot;
        final File outputArchive;
        final Bundle result;
        final RecordingCallback callback;
        final PlaintextProvider provider;
        final ExactGraphProvider exactGraphProvider;

        WorkspaceInvocation(
                File invocationRoot,
                File outputArchive,
                Bundle result,
                RecordingCallback callback,
                PlaintextProvider provider,
                ExactGraphProvider exactGraphProvider
        ) {
            this.invocationRoot = invocationRoot;
            this.outputArchive = outputArchive;
            this.result = result;
            this.callback = callback;
            this.provider = provider;
            this.exactGraphProvider = exactGraphProvider;
        }

        void assertWorkspaceOutputCommitted() throws IOException {
            assertTrue("workspace output archive is empty", outputArchive.length() > 0L);
            int tombstoneCount = 0;
            String lastEntryName = "";
            JSONObject tombstone = null;
            try (ZipInputStream input = new ZipInputStream(new FileInputStream(outputArchive))) {
                ZipEntry entry;
                byte[] buffer = new byte[4096];
                while ((entry = input.getNextEntry()) != null) {
                    lastEntryName = entry.getName();
                    ByteArrayOutputStream entryBytes = new ByteArrayOutputStream();
                    int total = 0;
                    int count;
                    while ((count = input.read(buffer)) >= 0) {
                        if (count == 0) continue;
                        total += count;
                        if (total > WORKSPACE_MAX_BYTES) {
                            throw new IOException("workspace output entry exceeds the test budget");
                        }
                        entryBytes.write(buffer, 0, count);
                    }
                    if (OUTPUT_TOMBSTONE_PATH.equals(entry.getName())) {
                        tombstoneCount++;
                        try {
                            tombstone = new JSONObject(
                                    entryBytes.toString(StandardCharsets.UTF_8.name())
                            );
                        } catch (Exception error) {
                            throw new IOException("workspace tombstone manifest is invalid", error);
                        }
                    }
                    input.closeEntry();
                }
            }
            assertEquals("workspace output tombstone manifest count", 1, tombstoneCount);
            assertEquals("workspace tombstone manifest must be the final ZIP entry",
                    OUTPUT_TOMBSTONE_PATH, lastEntryName);
            assertNotNull("workspace tombstone manifest is missing", tombstone);
            assertEquals("workspace tombstone manifest version", 1, tombstone.optInt("version", 0));
            assertNotNull("workspace tombstone deletedFiles array is missing",
                    tombstone.optJSONArray("deletedFiles"));
            assertEquals("conformance scripts unexpectedly deleted an explicit input", 0,
                    tombstone.optJSONArray("deletedFiles").length());
        }

        void assertWorkspaceNotMaterializedBeforeNative() {
            assertEquals("pre-native TypeScript failure unexpectedly committed workspace output",
                    0L, outputArchive.length());
            Map<String, String> values = nativeValues(result);
            assertFalse(
                    "pre-native TypeScript failure unexpectedly exposed workspace diagnostics: " + values,
                    values.containsKey(
                            "embedded_script.runtime_plugin.workspace.contract_version"
                    )
            );
            assertFalse(
                    "pre-native TypeScript failure unexpectedly reached native execution: " + values,
                    values.containsKey("execution.teardown_clean")
            );
        }

        @Override
        public void close() {
            deleteRecursively(invocationRoot);
        }
    }
}
