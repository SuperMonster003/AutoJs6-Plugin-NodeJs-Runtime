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
                    "ERR_AUTOJS6_FS_PATH_ESCAPE",
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

        LinkedHashMap<String, String> npmShadowFiles = new LinkedHashMap<>();
        npmShadowFiles.put(
                "main.mjs",
                "import app from 'app';\n" +
                        "console.log('x3d.esm.facade-shadow=' + app.source);\n"
        );
        npmShadowFiles.put(
                "node_modules/app/package.json",
                "{\"name\":\"app\",\"type\":\"module\",\"exports\":\"./index.mjs\"}\n"
        );
        npmShadowFiles.put(
                "node_modules/app/index.mjs",
                "export default { source: 'npm' };\n"
        );
        try (WorkspaceInvocation invocation = execute(
                "esm-autojs6-facade-npm-shadow",
                "main.mjs",
                npmShadowFiles,
                false
        )) {
            assertSucceeded(invocation.result, "x3d.esm.facade-shadow=npm");
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
                            missingPlaintextSource
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
            this.executionId = executionId;
            this.root = root.getCanonicalFile();
            this.rootPrefix = this.root.getAbsolutePath() + File.separator;
            this.expectedRuntimePid = expectedRuntimePid;
            this.missingPlaintextSource = missingPlaintextSource == null
                    ? null
                    : missingPlaintextSource.clone();
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
