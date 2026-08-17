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
    private static final long CONCURRENCY_TIMEOUT_MS = 30_000L;
    private static final int WORKSPACE_MAX_FILES = 128;
    private static final long WORKSPACE_MAX_BYTES = 1024L * 1024L;
    private static final String RUNTIME_PROCESS_SUFFIX = ":nodejs_runtime";
    private static final String ERROR_TYPESCRIPT_UNSUPPORTED_EXTENSION =
            "ERR_AUTOJS6_TYPESCRIPT_UNSUPPORTED_EXTENSION";
    private static final String ERROR_UNSUPPORTED_TYPESCRIPT_SYNTAX =
            "ERR_UNSUPPORTED_TYPESCRIPT_SYNTAX";
    private static final String TRANSPORT_PROTOCOL_DIRECTORY = ".autojs6-workspace-transport";
    private static final String INPUT_MANIFEST_PATH =
            TRANSPORT_PROTOCOL_DIRECTORY + "/input-manifest-v1.json";
    private static final String OUTPUT_TOMBSTONE_PATH =
            TRANSPORT_PROTOCOL_DIRECTORY + "/deletions-v1.json";
    private static final String NODE_COMPAT_CORPUS_V1_ASSET_ROOT = "node_compat_corpus";
    private static final List<String> NODE_COMPAT_CORPUS_V1_FILES = Arrays.asList(
            "cjs-basic/index.js",
            "cjs-basic/package.json",
            "cjs-buffer-path-util/index.js",
            "cjs-buffer-path-util/package.json",
            "cjs-deep-deps/index.js",
            "cjs-deep-deps/node_modules/first-dep/index.js",
            "cjs-deep-deps/node_modules/first-dep/node_modules/second-dep/index.js",
            "cjs-deep-deps/node_modules/first-dep/node_modules/second-dep/package.json",
            "cjs-deep-deps/node_modules/first-dep/package.json",
            "cjs-deep-deps/package.json",
            "cjs-disabled-feature-detection/index.js",
            "cjs-disabled-feature-detection/package.json",
            "cjs-fs-promisify/index.js",
            "cjs-fs-promisify/package.json",
            "cjs-json-cache/data.json",
            "cjs-json-cache/index.js",
            "cjs-json-cache/package.json",
            "cjs-package-exports/feature.js",
            "cjs-package-exports/index.js",
            "cjs-package-exports/package.json",
            "cjs-package-exports/private.js",
            "cjs-package-imports/index.js",
            "cjs-package-imports/lib/alias.js",
            "cjs-package-imports/package.json",
            "cjs-self-reference/feature.js",
            "cjs-self-reference/index.js",
            "cjs-self-reference/package.json"
    );
    private static final List<String> NODE_COMPAT_CORPUS_V1_FIXTURES = Arrays.asList(
            "cjs-basic",
            "cjs-deep-deps",
            "cjs-package-exports",
            "cjs-package-imports",
            "cjs-self-reference",
            "cjs-json-cache",
            "cjs-fs-promisify",
            "cjs-buffer-path-util",
            "cjs-disabled-feature-detection"
    );
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
                targetContext.getPackageName() + RUNTIME_PROCESS_SUFFIX,
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
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.assertWorkspaceOutputCommitted();
        }
    }

    @Test
    public void x3d_03_materializedPlaintextCtsUsesNotEncryptedPrivatePreparation() throws Exception {
        // This fixture is deliberately materialized in the workspace-v2 input
        // archive. It proves the plugin-private X3c second operation after the
        // candidate exists in the plugin mirror; it does not claim that the
        // current Host archive writer discovers unknown computed dependencies.
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put(
                "main.cjs",
                "const value = require('./value.cts');\n" +
                        "console.log('x3d.cts=' + value.answer);\n"
        );
        files.put(
                "value.cts",
                "const answer: number = 42;\nmodule.exports = { answer };\n"
        );
        try (WorkspaceInvocation invocation = execute(
                "plaintext-cts",
                "main.cjs",
                files,
                true
        )) {
            assertSucceeded(invocation.result, "x3d.cts=42");
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.assertWorkspaceOutputCommitted();
            invocation.provider.assertHealthyAndResolved("value.cts");
            // Two runtime policy metadata preflight resolves (project.json,
            // package.json) precede the value.cts resolution.
            assertNativeValue(
                    invocation.result,
                    "embedded_script.runtime_plugin.module_provider.request_count",
                    "3"
            );
            assertNativeValue(
                    invocation.result,
                    "embedded_script.runtime_plugin.module_provider.metadata_preflight.provider_request_count",
                    "2"
            );
            assertNativeValue(
                    invocation.result,
                    "embedded_script.runtime_plugin.module_provider.not_encrypted_count",
                    "1"
            );
            assertNativeValue(
                    invocation.result,
                    "embedded_script.runtime_plugin.module_provider.mapped_request_path_count",
                    "3"
            );
            assertNativeValue(
                    invocation.result,
                    "embedded_script.runtime_plugin.module_provider.mapped_response_path_count",
                    "1"
            );
            assertNativeValue(
                    invocation.result,
                    "embedded_script.runtime_plugin.module_provider.typescript.stripped_count",
                    "1"
            );
            assertNativeValue(
                    invocation.result,
                    "embedded_script.module_provider.transport_failure_count",
                    "0"
            );
            assertNativeValue(
                    invocation.result,
                    "embedded_script.runtime_plugin.module_provider.typescript." +
                            "plaintext_preparation_request_count",
                    "1"
            );
            assertNativeValue(
                    invocation.result,
                    "embedded_script.runtime_plugin.module_provider.typescript." +
                            "plaintext_preparation_count",
                    "1"
            );
            assertNativeValue(
                    invocation.result,
                    "embedded_script.runtime_plugin.module_provider.typescript.last_status",
                    "prepared"
            );
            assertNativeValue(
                    invocation.result,
                    "embedded_script.runtime_plugin.module_provider.typescript.last_extension",
                    "cts"
            );
            assertNativeValue(
                    invocation.result,
                    "embedded_script.runtime_plugin.module_provider.prepared_source_count",
                    "1"
            );
        }
    }

    @Test
    public void x3d_04_tsxFailsCanonicalAndStillEmitsOneTerminalEvent() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put(
                "main.cjs",
                "require('./unsupported.tsx');\n"
        );
        files.put(
                "unsupported.tsx",
                "const value: number = 42;\nmodule.exports = value;\n"
        );
        try (WorkspaceInvocation invocation = execute(
                "tsx-negative",
                "main.cjs",
                files,
                true
        )) {
            assertFalse("TSX execution unexpectedly succeeded",
                    invocation.result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            assertEquals(
                    ERROR_TYPESCRIPT_UNSUPPORTED_EXTENSION,
                    invocation.result.getString(NodeJsRuntimeContract.KEY_ERROR_CODE)
            );
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.provider.assertHealthyAndNotCalled();
        }
    }

    @Test
    public void x3e_05_nodeCompatCorpusV2PassesThroughPublishedBinder() throws Exception {
        // This is the plugin-owned, normalized-text migration of the former Host v2 corpus.
        // The companion Host ownership gate verifies that its copy and selector are absent.
        LinkedHashMap<String, String> files = loadNodeCompatCorpusV2Assets();
        assertEquals("node_compat_corpus_v2 asset count", 15, files.size());
        files.put("main.cjs", nodeCompatCorpusV2RunnerSource());

        try (WorkspaceInvocation invocation = execute(
                "node-compat-corpus-v2",
                "main.cjs",
                files,
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
    public void x3e_06_tsEntryRunsAsCommonJsWithPluginStripper() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put(
                "main.ts",
                "type Label = string;\n" +
                        "interface Payload { value: Label; }\n" +
                        "const value: Label = ('cjs' as string);\n" +
                        "function suffix<T>(input: T): string { return String(input); }\n" +
                        "module.exports = { value, suffix };\n" +
                        "console.log('ts.cjs=' + suffix(value));\n"
        );
        try (WorkspaceInvocation invocation = execute(
                "typescript-entry-cjs",
                "main.ts",
                files,
                false
        )) {
            assertSucceeded(invocation.result, "ts.cjs=cjs");
            assertNativeValue(invocation.result, "embedded_script.typescript.stripped", "true");
            assertNativeValue(invocation.result, "embedded_script.typescript.extension", "ts");
            assertNativeValue(invocation.result, "embedded_script.esm_entry", "false");
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.assertWorkspaceOutputCommitted();
        }
    }

    @Test
    public void x3e_07_tsEntryFollowsPackageTypeModule() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("package.json", "{\"type\":\"module\"}\n");
        files.put(
                "main.ts",
                "type Label = string;\n" +
                        "const value: Label = await Promise.resolve('module');\n" +
                        "export const named: Label = value;\n" +
                        "console.log('ts.esm.type=' + named);\n"
        );
        try (WorkspaceInvocation invocation = execute(
                "typescript-entry-type-module",
                "main.ts",
                files,
                false
        )) {
            assertSucceeded(invocation.result, "ts.esm.type=module");
            assertNativeValue(invocation.result, "embedded_script.typescript.stripped", "true");
            assertNativeValue(invocation.result, "embedded_script.typescript.extension", "ts");
            assertNativeValue(invocation.result, "embedded_script.esm_entry", "true");
            assertNativeValue(
                    invocation.result,
                    "embedded_script.package_type_module_policy",
                    "type=module accepted by Embedded Node partial ESM"
            );
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.assertWorkspaceOutputCommitted();
        }
    }

    @Test
    public void x3e_08_mtsEntryRemovesTypeOnlyImport() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put(
                "main.mts",
                "import type { Label } from './types.mts';\n" +
                        "type Local = string;\n" +
                        "const value: Local = await Promise.resolve('mts');\n" +
                        "export const named: Local = value;\n" +
                        "console.log('ts.mts=' + named);\n"
        );
        try (WorkspaceInvocation invocation = execute(
                "typescript-entry-mts",
                "main.mts",
                files,
                false
        )) {
            assertSucceeded(invocation.result, "ts.mts=mts");
            assertNativeValue(invocation.result, "embedded_script.typescript.stripped", "true");
            assertNativeValue(invocation.result, "embedded_script.typescript.extension", "mts");
            assertNativeValue(invocation.result, "embedded_script.esm_entry", "true");
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.assertWorkspaceOutputCommitted();
        }
    }

    @Test
    public void x3e_09_ctsModuleSourcesCanBeRequired() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put(
                "main.cjs",
                "const dep = require('./dep.cts');\n" +
                        "console.log('ts.cts=' + dep.value);\n"
        );
        LinkedHashMap<String, String> moduleSources = new LinkedHashMap<>();
        moduleSources.put(
                "dep.cts",
                "type Label = string;\n" +
                        "const value: Label = 'cts';\n" +
                        "module.exports = { value };\n"
        );
        try (WorkspaceInvocation invocation = execute(
                "typescript-module-sources-cts",
                "main.cjs",
                files,
                moduleSources,
                false
        )) {
            assertSucceeded(invocation.result, "ts.cts=cts");
            assertModuleSourceTypeScriptReceipts(invocation.result, 1, 1, 1);
            assertNativeValue(
                    invocation.result,
                    "embedded_script.typescript.module_sources.source.0.extension",
                    "cts"
            );
            assertNativeValue(
                    invocation.result,
                    "embedded_script.typescript.module_sources.source.0.stripped",
                    "true"
            );
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.assertWorkspaceOutputCommitted();
        }
    }

    @Test
    public void x3e_10_packagedTsAndMtsModuleSourcesGraphPasses() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("package.json", "{\"type\":\"module\"}\n");
        files.put(
                "main.mts",
                "import { value } from './dep.ts';\n" +
                        "type Label = string;\n" +
                        "const dynamic = await import('./dynamic.mts');\n" +
                        "const marker: Label = value + ':' + dynamic.suffix;\n" +
                        "console.log('ts.module_sources=' + marker);\n"
        );
        LinkedHashMap<String, String> moduleSources = new LinkedHashMap<>();
        moduleSources.put(
                "dep.ts",
                "export type Label = string;\n" +
                        "export const value: Label = 'ts';\n"
        );
        moduleSources.put(
                "dynamic.mts",
                "type Suffix = string;\n" +
                        "export const suffix: Suffix = 'mts';\n"
        );
        try (WorkspaceInvocation invocation = execute(
                "typescript-module-sources-graph",
                "main.mts",
                files,
                moduleSources,
                false
        )) {
            assertSucceeded(invocation.result, "ts.module_sources=ts:mts");
            assertNativeValue(invocation.result, "embedded_script.typescript.stripped", "true");
            assertNativeValue(invocation.result, "embedded_script.typescript.extension", "mts");
            assertModuleSourceTypeScriptReceipts(invocation.result, 2, 2, 2);
            assertNativeValue(invocation.result, "embedded_script.esm_entry", "true");
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.assertWorkspaceOutputCommitted();
        }
    }

    @Test
    public void x3e_11_tsxEntryFailsCanonicalBeforeNative() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("main.tsx", "export default <View />;\n");
        try (WorkspaceInvocation invocation = execute(
                "typescript-entry-tsx-negative",
                "main.tsx",
                files,
                false
        )) {
            assertCanonicalTypeScriptFailure(
                    invocation,
                    ERROR_TYPESCRIPT_UNSUPPORTED_EXTENSION,
                    "tsx",
                    "TSX"
            );
        }
    }

    @Test
    public void x3e_12_enumEntryFailsCanonicalBeforeNative() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("main.ts", "enum Color { Red }\nconsole.log(Color.Red);\n");
        try (WorkspaceInvocation invocation = execute(
                "typescript-entry-enum-negative",
                "main.ts",
                files,
                false
        )) {
            assertCanonicalTypeScriptFailure(
                    invocation,
                    ERROR_UNSUPPORTED_TYPESCRIPT_SYNTAX,
                    "enum",
                    "enum declarations require code generation"
            );
            assertNativeValue(invocation.result, "embedded_script.typescript.line", "1");
        }
    }

    @Test
    public void x3f_13_missingComputedCtsMaterializesThroughProviderV2AndStaysProtected() throws Exception {
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
            assertSucceeded(invocation.result, "x3f.cts=42");
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.provider.assertHealthyAndMaterialized("computed.cts");
            invocation.assertWorkspaceOutputCommitted();
            invocation.assertWorkspaceExcludes("computed.cts", "const base: number = 41");
            assertNativeValue(invocation.result,
                    "embedded_script.module_provider.missing_candidate_request_count", "1");
            assertNativeValue(invocation.result,
                    "embedded_script.module_provider.materialized_count", "1");
            assertNativeValue(invocation.result,
                    "embedded_script.module_provider.materialized_source_bytes",
                    Integer.toString(rawPlaintext.length));
            // Two metadata preflight resolves, the missing-candidate resolve
            // (not_encrypted), and the materialization itself.
            assertNativeValue(invocation.result,
                    "embedded_script.runtime_plugin.module_provider.request_count", "4");
            assertNativeValue(invocation.result,
                    "embedded_script.runtime_plugin.module_provider.metadata_preflight.provider_request_count",
                    "2");
            assertNativeValue(invocation.result,
                    "embedded_script.runtime_plugin.module_provider.missing_candidate_request_count", "1");
            assertNativeValue(invocation.result,
                    "embedded_script.runtime_plugin.module_provider.plaintext_count", "1");
            assertNativeValue(invocation.result,
                    "embedded_script.runtime_plugin.module_provider.materialized_count", "1");
            assertNativeValue(invocation.result,
                    "embedded_script.runtime_plugin.module_provider.materialized_source_bytes",
                    Integer.toString(rawPlaintext.length));
            assertNativeValue(invocation.result,
                    "embedded_script.runtime_plugin.module_provider.not_encrypted_count", "1");
            assertNativeValue(invocation.result,
                    "embedded_script.runtime_plugin.module_provider.typescript." +
                            "raw_already_accounted_preparation_count", "1");
            assertNativeValue(invocation.result,
                    "embedded_script.runtime_plugin.module_provider.typescript." +
                            "plaintext_preparation_request_count", "1");
            assertNativeValue(invocation.result,
                    "embedded_script.runtime_plugin.module_provider.typescript." +
                            "plaintext_preparation_count", "1");
            assertNativeValue(invocation.result,
                    "embedded_script.runtime_plugin.module_provider.typescript.stripped_count", "1");
            assertNativeValue(invocation.result,
                    "embedded_script.module_provider.transport_failure_count", "0");
            assertNativeValue(invocation.result,
                    "embedded_script.runtime_plugin.workspace.provider_materialized_count", "1");
            assertNativeValue(invocation.result,
                    "embedded_script.runtime_plugin.workspace.provider_protected_output_file_count", "1");
            assertNativeValue(invocation.result,
                    "embedded_script.runtime_plugin.workspace.provider_protected_tombstone_count", "0");
        }
    }

    @Test
    public void x3f_14_workspaceDirectExclusivePublicationUsesMode0600() throws Exception {
        byte[] source = "module.exports = 42;\n".getBytes(StandardCharsets.UTF_8);
        try (DirectWorkspaceSession workspace = openDirectWorkspace("exclusive-success")) {
            File privateSource = workspace.writePrivateSource(source);
            File target = workspace.runtimeFile("nested/value.cjs");
            PluginWorkspaceArchiveSession.ProviderMaterialization receipt =
                    workspace.session.materializeProviderSourceNoReplace(
                            target.getAbsolutePath(),
                            privateSource,
                            source.length,
                            SystemClock.elapsedRealtime() + 5_000L
                    );

            StructStat stat = Os.lstat(target.getAbsolutePath());
            assertTrue("provider target is not regular", OsConstants.S_ISREG(stat.st_mode));
            assertEquals("provider target mode", 0600, stat.st_mode & 0777);
            assertEquals("provider target bytes", source.length, stat.st_size);
            assertEquals(target.getAbsolutePath(), receipt.runtimePath());
            assertEquals(source.length, receipt.sourceBytes());
            assertEquals(new String(source, StandardCharsets.UTF_8), readUtf8(target));
            workspace.assertDiagnostic("provider_materialized_count", "1");
            workspace.assertDiagnostic("provider_materialization_failure_count", "0");
        }
    }

    @Test
    public void x3f_15_workspaceExclusiveConflictPreservesSentinel() throws Exception {
        byte[] source = "module.exports = 'provider';\n".getBytes(StandardCharsets.UTF_8);
        String sentinel = "module.exports = 'sentinel';\n";
        try (DirectWorkspaceSession workspace = openDirectWorkspace("exclusive-conflict")) {
            File privateSource = workspace.writePrivateSource(source);
            File target = workspace.runtimeFile("value.cjs");
            writeUtf8(target, sentinel);
            StructStat before = Os.lstat(target.getAbsolutePath());

            try {
                workspace.session.materializeProviderSourceNoReplace(
                        target.getAbsolutePath(),
                        privateSource,
                        source.length,
                        SystemClock.elapsedRealtime() + 5_000L
                );
                throw new AssertionError("Expected exclusive provider target conflict");
            } catch (IOException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("exclusive open"));
            }

            StructStat after = Os.lstat(target.getAbsolutePath());
            assertEquals("sentinel device changed", before.st_dev, after.st_dev);
            assertEquals("sentinel inode changed", before.st_ino, after.st_ino);
            assertEquals("sentinel source changed", sentinel, readUtf8(target));
            workspace.assertDiagnostic("provider_materialization_conflict_count", "1");
            workspace.assertDiagnostic("provider_materialization_failure_count", "1");
        }
    }

    @Test
    public void x3f_16_workspaceDeadlineRemovesOwnedPartialAndDirectories() throws Exception {
        byte[] source = "module.exports = 'partial';\n".getBytes(StandardCharsets.UTF_8);
        try (DirectWorkspaceSession workspace = openDirectWorkspace("deadline-partial-cleanup")) {
            File privateSource = workspace.writePrivateSource(source);
            File target = workspace.runtimeFile("created/deep/value.cjs");
            AtomicInteger clockCalls = new AtomicInteger();
            PluginWorkspaceArchiveSession.ProviderMaterializationClock clock = () ->
                    clockCalls.incrementAndGet() == 1 ? 1L : 10L;

            try {
                workspace.session.materializeProviderSourceNoReplace(
                        target.getAbsolutePath(),
                        privateSource,
                        source.length,
                        10L,
                        clock
                );
                throw new AssertionError("Expected post-copy provider deadline failure");
            } catch (PluginWorkspaceArchiveSession
                    .ProviderMaterializationDeadlineExceededException expected) {
                assertTrue(expected.getMessage(),
                        expected.getMessage().contains("after private copy and fsync"));
            }

            assertEquals("deadline clock stage count", 2, clockCalls.get());
            assertFalse("owned partial target survived timeout", target.exists());
            assertFalse("provider-created directory shell survived timeout",
                    workspace.runtimeFile("created").exists());
            workspace.assertDiagnostic("provider_materialization_failure_count", "1");
        }
    }

    @Test
    public void x3f_17_workspaceTimeoutNeverDeletesForeignReplacement() throws Exception {
        byte[] source = "module.exports = 'owned';\n".getBytes(StandardCharsets.UTF_8);
        String sentinel = "module.exports = 'foreign';\n";
        try (DirectWorkspaceSession workspace = openDirectWorkspace("deadline-foreign-replacement")) {
            File privateSource = workspace.writePrivateSource(source);
            File target = workspace.runtimeFile("value.cjs");
            AtomicInteger clockCalls = new AtomicInteger();
            PluginWorkspaceArchiveSession.ProviderMaterializationClock clock = () -> {
                int call = clockCalls.incrementAndGet();
                if (call == 2) {
                    try {
                        Os.remove(target.getAbsolutePath());
                        writeUtf8(target, sentinel);
                    } catch (Exception error) {
                        throw new AssertionError("Unable to install foreign replacement", error);
                    }
                    return 10L;
                }
                return 1L;
            };

            try {
                workspace.session.materializeProviderSourceNoReplace(
                        target.getAbsolutePath(),
                        privateSource,
                        source.length,
                        10L,
                        clock
                );
                throw new AssertionError("Expected replacement-stage provider deadline failure");
            } catch (PluginWorkspaceArchiveSession
                    .ProviderMaterializationDeadlineExceededException expected) {
                assertTrue(expected.getMessage(),
                        expected.getMessage().contains("after private copy and fsync"));
            }

            assertEquals("foreign replacement was deleted or changed", sentinel, readUtf8(target));
            workspace.assertDiagnostic("provider_materialization_failure_count", "1");
        }
    }

    @Test
    public void x3g_18_nodeCompatCorpusV1PassesThroughPublishedBinder() throws Exception {
        // Plugin-owned copy of the migrated Host v1 corpus. The immutable X3g removal
        // receipt proves that all 27 assets and both migrated selectors left Host.
        LinkedHashMap<String, String> files = loadNodeCompatCorpusV1Assets();
        assertEquals("node_compat_corpus v1 asset count", 27, files.size());
        files.put("main.cjs", nodeCompatCorpusV1RunnerSource());

        try (WorkspaceInvocation invocation = execute(
                "node-compat-corpus-v1",
                "main.cjs",
                files,
                false
        )) {
            assertSucceeded(invocation.result, "compat.corpus.total=9");
            for (String fixture : NODE_COMPAT_CORPUS_V1_FIXTURES) {
                String receipt = "compat.fixture." + fixture + "=PASS";
                assertTrue(
                        "missing corpus receipt " + receipt + " in " +
                                invocation.result.getString(NodeJsRuntimeContract.KEY_STDOUT, ""),
                        invocation.result.getString(NodeJsRuntimeContract.KEY_STDOUT, "")
                                .contains(receipt)
                );
            }
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.assertWorkspaceOutputCommitted();
            assertEquals("v1 corpus unexpectedly used a module-source provider",
                    null, invocation.provider);
        }
    }

    @Test
    public void x3g_19_preloadShapedModuleSourcesOverridesWorkspaceDisk() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put(
                "main.cjs",
                "const fixture = require('./preloaded-fixture');\n" +
                        "Promise.resolve(fixture()).then((result) => {\n" +
                        "  if (result !== 'PASS') {\n" +
                        "    throw new Error('preloaded fixture returned ' + result);\n" +
                        "  }\n" +
                        "  console.log('compat.preload.fixture=PASS');\n" +
                        "}).catch((error) => {\n" +
                        "  console.error('compat.preload.error=' +\n" +
                        "    (error && (error.stack || error.message) || error));\n" +
                        "  process.exitCode = 1;\n" +
                        "});\n"
        );
        files.put("preloaded-fixture.js", "module.exports = () => 'DISK';\n");
        LinkedHashMap<String, String> moduleSources = new LinkedHashMap<>();
        moduleSources.put(
                "preloaded-fixture.js",
                "const assert = require('assert');\n" +
                        "const path = require('path');\n" +
                        "module.exports = async function run() {\n" +
                        "  assert.strictEqual(path.basename('/tmp/preloaded.txt'), 'preloaded.txt');\n" +
                        "  assert.deepStrictEqual({ ok: true }, { ok: true });\n" +
                        "  return 'PASS';\n" +
                        "};\n"
        );

        try (WorkspaceInvocation invocation = execute(
                "node-compat-preload-v1",
                "main.cjs",
                files,
                moduleSources,
                false
        )) {
            assertSucceeded(invocation.result, "compat.preload.fixture=PASS");
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.assertWorkspaceOutputCommitted();
        }
    }

    @Test
    public void x3h_20_rawTsEntryLoadsExtensionlessCjsThroughExactProvider() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put(
                "main.ts",
                "type Answer = number;\n" +
                        "const value: Answer = require('./feature').answer;\n" +
                        "console.log('x3h.raw_ts_extensionless=' + value);\n"
        );
        LinkedHashMap<String, ProviderAction> actions = new LinkedHashMap<>();
        actions.put("project.json", ProviderAction.notFound());
        actions.put("package.json", ProviderAction.notFound());
        actions.put("feature.js", ProviderAction.notFound());
        actions.put(
                "feature.cjs",
                ProviderAction.decrypted("module.exports = { answer: 42 };\n")
        );

        try (WorkspaceInvocation invocation = executeExactGraph(
                "x3h-raw-ts-extensionless-cjs",
                "main.ts",
                files,
                actions
        )) {
            assertSucceeded(invocation.result, "x3h.raw_ts_extensionless=42");
            assertNativeValue(invocation.result, "embedded_script.typescript.stripped", "true");
            assertNativeValue(invocation.result, "embedded_script.typescript.extension", "ts");
            assertNativeValue(invocation.result,
                    "embedded_script.runtime_plugin.module_provider.metadata_preflight.path_count", "2");
            assertNativeValue(invocation.result,
                    "embedded_script.runtime_plugin.module_provider.metadata_preflight.provider_request_count", "2");
            assertNativeValue(invocation.result,
                    "embedded_script.runtime_plugin.module_provider.request_count", "4");
            invocation.exactGraphProvider.assertHealthyAndExactEvents(Arrays.asList(
                    "resolve_existing:project.json:not_found",
                    "resolve_existing:package.json:not_found",
                    "resolve_existing:feature.js:not_found",
                    "resolve_existing:feature.cjs:decrypted"
            ));
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.assertWorkspaceOutputCommitted();
        }
    }

    @Test
    public void x3h_21_packageMetadataMainIndexAndBareGraphUsesExactProvider() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put(
                "main.cjs",
                "const main = require('./mainpkg');\n" +
                        "const index = require('./indexpkg');\n" +
                        "const bare = require('barepkg');\n" +
                        "console.log('x3h.package_graph=' + [main, index, bare].join('/'));\n"
        );
        LinkedHashMap<String, ProviderAction> actions = new LinkedHashMap<>();
        actions.put("project.json", ProviderAction.notFound());
        actions.put("package.json", ProviderAction.notFound());
        for (String extension : Arrays.asList(".js", ".cjs", ".cts", ".ts", ".json")) {
            actions.put("mainpkg" + extension, ProviderAction.notFound());
        }
        actions.put(
                "mainpkg/package.json",
                ProviderAction.decrypted("{\"main\":\"entry\"}\n")
        );
        actions.put(
                "mainpkg/entry.js",
                ProviderAction.decrypted("module.exports = 'main';\n")
        );
        for (String extension : Arrays.asList(".js", ".cjs", ".cts", ".ts", ".json")) {
            actions.put("indexpkg" + extension, ProviderAction.notFound());
        }
        actions.put("indexpkg/package.json", ProviderAction.notFound());
        actions.put("indexpkg/index.js", ProviderAction.notFound());
        actions.put(
                "indexpkg/index.cjs",
                ProviderAction.decrypted("module.exports = 'index';\n")
        );
        actions.put(
                "node_modules/barepkg/package.json",
                ProviderAction.decrypted("{\"main\":\"entry\"}\n")
        );
        actions.put(
                "node_modules/barepkg/entry.js",
                ProviderAction.decrypted("module.exports = 'bare';\n")
        );

        try (WorkspaceInvocation invocation = executeExactGraph(
                "x3h-package-main-index-bare",
                "main.cjs",
                files,
                actions
        )) {
            assertSucceeded(invocation.result, "x3h.package_graph=main/index/bare");
            assertNativeValue(invocation.result,
                    "embedded_script.runtime_plugin.module_provider.metadata_preflight.path_count", "2");
            assertNativeValue(invocation.result,
                    "embedded_script.runtime_plugin.module_provider.metadata_preflight.provider_request_count", "2");
            assertNativeValue(invocation.result,
                    "embedded_script.runtime_plugin.module_provider.request_count", "19");
            invocation.exactGraphProvider.assertHealthyAndExactEvents(Arrays.asList(
                    "resolve_existing:project.json:not_found",
                    "resolve_existing:package.json:not_found",
                    "resolve_existing:mainpkg.js:not_found",
                    "resolve_existing:mainpkg.cjs:not_found",
                    "resolve_existing:mainpkg.cts:not_found",
                    "resolve_existing:mainpkg.ts:not_found",
                    "resolve_existing:mainpkg.json:not_found",
                    "resolve_existing:mainpkg/package.json:decrypted",
                    "resolve_existing:mainpkg/entry.js:decrypted",
                    "resolve_existing:indexpkg.js:not_found",
                    "resolve_existing:indexpkg.cjs:not_found",
                    "resolve_existing:indexpkg.cts:not_found",
                    "resolve_existing:indexpkg.ts:not_found",
                    "resolve_existing:indexpkg.json:not_found",
                    "resolve_existing:indexpkg/package.json:not_found",
                    "resolve_existing:indexpkg/index.js:not_found",
                    "resolve_existing:indexpkg/index.cjs:decrypted",
                    "resolve_existing:node_modules/barepkg/package.json:decrypted",
                    "resolve_existing:node_modules/barepkg/entry.js:decrypted"
            ));
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.assertWorkspaceOutputCommitted();
        }
    }

    @Test
    public void x3h_22_workspaceCloseSerializesWithProviderMaterialization() throws Exception {
        byte[] source = "module.exports = 'quiescent-cleanup';\n".getBytes(StandardCharsets.UTF_8);
        try (DirectWorkspaceSession workspace = openDirectWorkspace("provider-close-serialization")) {
            File privateSource = workspace.writePrivateSource(source);
            File target = workspace.runtimeFile("late/value.cjs");
            workspace.assertDiagnostic("status", "ready");
            assertFalse("provider target unexpectedly existed before materialization", target.exists());
            StructStat privateSourceStat = Os.lstat(privateSource.getAbsolutePath());
            assertTrue("provider private source is not regular",
                    OsConstants.S_ISREG(privateSourceStat.st_mode));
            assertEquals("provider private source bytes", source.length, privateSourceStat.st_size);
            CountDownLatch materializerStarted = new CountDownLatch(1);
            CountDownLatch materializationEntered = new CountDownLatch(1);
            CountDownLatch releaseMaterialization = new CountDownLatch(1);
            CountDownLatch closeStarted = new CountDownLatch(1);
            CountDownLatch closeFinished = new CountDownLatch(1);
            AtomicReference<Throwable> materializationFailure = new AtomicReference<>();
            AtomicReference<Throwable> closeFailure = new AtomicReference<>();
            AtomicInteger clockCalls = new AtomicInteger();
            PluginWorkspaceArchiveSession.ProviderMaterializationClock clock = () -> {
                if (clockCalls.incrementAndGet() == 1) {
                    materializationEntered.countDown();
                    try {
                        if (!releaseMaterialization.await(
                                CONCURRENCY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                            throw new AssertionError("Timed out releasing provider materialization");
                        }
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("Provider materialization wait was interrupted", error);
                    }
                }
                return 1L;
            };

            Thread materializer = new Thread(() -> {
                materializerStarted.countDown();
                try {
                    workspace.session.materializeProviderSourceNoReplace(
                            target.getAbsolutePath(), privateSource, source.length, 100L, clock
                    );
                } catch (Throwable error) {
                    materializationFailure.set(error);
                }
            }, "x3h-provider-materializer");
            Thread closer = new Thread(() -> {
                closeStarted.countDown();
                try {
                    workspace.session.close();
                } catch (Throwable error) {
                    closeFailure.set(error);
                } finally {
                    closeFinished.countDown();
                }
            }, "x3h-workspace-closer");

            materializer.start();
            try {
                assertTrue("provider materializer thread did not start",
                        materializerStarted.await(CONCURRENCY_TIMEOUT_MS, TimeUnit.MILLISECONDS));
                if (!awaitWorkerStageOrFailure(
                        materializationEntered,
                        materializer,
                        materializationFailure,
                        CONCURRENCY_TIMEOUT_MS
                )) {
                    Throwable failure = materializationFailure.get();
                    if (failure != null) {
                        throw new AssertionError(
                                "provider materialization failed before monitor hook", failure);
                    }
                    throw new AssertionError(
                            "provider materialization did not enter the workspace monitor; " +
                                    "workerState=" + materializer.getState());
                }
                closer.start();
                assertTrue("workspace close thread did not start",
                        closeStarted.await(CONCURRENCY_TIMEOUT_MS, TimeUnit.MILLISECONDS));
                assertFalse("workspace close bypassed in-flight provider materialization",
                        closeFinished.await(100, TimeUnit.MILLISECONDS));
            } finally {
                releaseMaterialization.countDown();
                materializer.join(CONCURRENCY_TIMEOUT_MS);
                if (closer.getState() != Thread.State.NEW) {
                    closer.join(CONCURRENCY_TIMEOUT_MS);
                }
            }

            assertFalse("provider materializer thread remained alive", materializer.isAlive());
            assertFalse("workspace closer thread remained alive", closer.isAlive());
            assertEquals("provider materialization failure", null, materializationFailure.get());
            assertEquals("workspace close failure", null, closeFailure.get());
            assertFalse("provider plaintext survived serialized workspace cleanup", target.exists());
            assertFalse("provider-created directory survived serialized workspace cleanup",
                    workspace.runtimeFile("late").exists());
        }
    }

    @Test
    public void x3i_23_zlibCallbacksStreamsAndShadowingPassThroughPublishedBinder()
            throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("node_modules/zlib/index.js", "module.exports = { shadow: true };\n");
        files.put("main.cjs", zlibCallbacksStreamsAndShadowingSource());

        try (WorkspaceInvocation invocation = execute(
                "x3i-zlib-callbacks-streams-shadowing",
                "main.cjs",
                files,
                false
        )) {
            assertSucceeded(invocation.result, "zlib.buffer.focused.ready=true");
            assertNativeValue(
                    invocation.result,
                    "embedded_script.pending_zlib_callbacks",
                    "0"
            );
            assertNativeValue(
                    invocation.result,
                    "embedded_script.pending_zlib_streams",
                    "false"
            );
            assertNativeValue(
                    invocation.result,
                    "embedded_script.pending_zlib_stream_count",
                    "0"
            );
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.assertWorkspaceOutputCommitted();
            assertEquals("ZLIB staging unexpectedly used a module-source provider",
                    null, invocation.provider);
        }
    }

    @Test
    public void x3j_24_cryptoSafeExpansionAndShadowingPassThroughPublishedBinder()
            throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("node_modules/crypto/index.js", "module.exports = { shadow: true };\n");
        files.put("main.cjs", cryptoSafeExpansionAndShadowingSource());

        try (WorkspaceInvocation invocation = execute(
                "x3j-crypto-safe-expansion-shadowing",
                "main.cjs",
                files,
                false,
                10_000L
        )) {
            assertSucceeded(invocation.result, "crypto.expansion.focused.ready=true");
            assertNativeValue(
                    invocation.result,
                    "embedded_script.pending_crypto_callbacks",
                    "0"
            );
            assertNativeValue(
                    invocation.result,
                    "embedded_script.timed_out",
                    "false"
            );
            invocation.callback.assertOneStartedAndOneTerminalEvent();
            invocation.assertWorkspaceOutputCommitted();
            assertEquals("Crypto staging unexpectedly used a module-source provider",
                    null, invocation.provider);
            assertEquals("Crypto staging unexpectedly used an exact-graph provider",
                    null, invocation.exactGraphProvider);
        }
    }

    private static boolean awaitWorkerStageOrFailure(
            CountDownLatch stage,
            Thread worker,
            AtomicReference<Throwable> failure,
            long timeoutMs
    ) throws InterruptedException {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        while (true) {
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0L) return false;
            if (stage.await(Math.min(remaining, 100L), TimeUnit.MILLISECONDS)) return true;
            if (failure.get() != null || !worker.isAlive()) return false;
        }
    }

    private static void assertModuleSourceTypeScriptReceipts(
            Bundle result,
            int inputCount,
            int typeScriptCount,
            int strippedCount
    ) {
        assertNativeValue(
                result,
                "embedded_script.typescript.module_sources.input_count",
                Integer.toString(inputCount)
        );
        assertNativeValue(
                result,
                "embedded_script.typescript.module_sources.typescript_count",
                Integer.toString(typeScriptCount)
        );
        assertNativeValue(
                result,
                "embedded_script.typescript.module_sources.stripped_count",
                Integer.toString(strippedCount)
        );
    }

    private static void assertCanonicalTypeScriptFailure(
            WorkspaceInvocation invocation,
            String expectedErrorCode,
            String expectedSyntaxKind,
            String expectedMessageFragment
    ) {
        assertFalse("TypeScript negative unexpectedly succeeded",
                invocation.result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
        assertEquals(
                expectedErrorCode,
                invocation.result.getString(NodeJsRuntimeContract.KEY_ERROR_CODE)
        );
        assertTrue(
                invocation.result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, ""),
                invocation.result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "")
                        .contains(expectedMessageFragment)
        );
        assertNativeValue(
                invocation.result,
                "embedded_script.typescript.syntax_kind",
                expectedSyntaxKind
        );
        invocation.callback.assertOneStartedAndOneTerminalEvent();
        invocation.assertWorkspaceNotMaterializedBeforeNative();
    }

    private static LinkedHashMap<String, String> loadNodeCompatCorpusV1Assets()
            throws IOException {
        LinkedHashMap<String, String> loaded = loadNodeCompatCorpusAssets(
                NODE_COMPAT_CORPUS_V1_ASSET_ROOT,
                NODE_COMPAT_CORPUS_V1_FILES,
                "node_compat_corpus"
        );
        LinkedHashMap<String, String> workspaceFiles = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : loaded.entrySet()) {
            workspaceFiles.put(
                    NODE_COMPAT_CORPUS_V1_ASSET_ROOT + "/" + entry.getKey(),
                    entry.getValue()
            );
        }
        return workspaceFiles;
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

    private static String nodeCompatCorpusV1RunnerSource() {
        StringBuilder fixtures = new StringBuilder("[");
        for (int index = 0; index < NODE_COMPAT_CORPUS_V1_FIXTURES.size(); index++) {
            if (index > 0) fixtures.append(',');
            fixtures.append(JSONObject.quote(NODE_COMPAT_CORPUS_V1_FIXTURES.get(index)));
        }
        fixtures.append(']');
        return "(async () => {\n" +
                "  const fixtures = " + fixtures + ";\n" +
                "  for (const name of fixtures) {\n" +
                "    const fixture = require('./node_compat_corpus/' + name);\n" +
                "    const run = typeof fixture === 'function' ? fixture : fixture.run;\n" +
                "    if (typeof run !== 'function') {\n" +
                "      throw new Error('fixture ' + name + ' does not export a runner');\n" +
                "    }\n" +
                "    const result = await run();\n" +
                "    if (result !== 'PASS') {\n" +
                "      throw new Error('fixture ' + name + ' returned ' + result);\n" +
                "    }\n" +
                "    console.log('compat.fixture.' + name + '=PASS');\n" +
                "  }\n" +
                "  console.log('compat.corpus.total=' + fixtures.length);\n" +
                "})().catch((error) => {\n" +
                "  console.error('compat.corpus.error=' +\n" +
                "    (error && (error.stack || error.message) || error));\n" +
                "  process.exitCode = 1;\n" +
                "});\n";
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

    private static String zlibCallbacksStreamsAndShadowingSource() {
        return "\"nodejs\";\n" +
                "const assert = require('assert/strict');\n" +
                "const fs = require('fs');\n" +
                "const zlib = require('zlib');\n" +
                "const nodeZlib = require('node:zlib');\n" +
                "const stream = require('stream');\n" +
                "const { pipeline } = require('stream/promises');\n" +
                "const { promisify } = require('util');\n" +
                "\n" +
                "async function collect(chunks, transforms) {\n" +
                "  const values = [];\n" +
                "  const writable = new stream.Writable({\n" +
                "    write(chunk, encoding, callback) {\n" +
                "      values.push(Buffer.from(chunk));\n" +
                "      callback();\n" +
                "    }\n" +
                "  });\n" +
                "  await pipeline(stream.Readable.from(chunks), ...transforms, writable);\n" +
                "  return Buffer.concat(values).toString('utf8');\n" +
                "}\n" +
                "\n" +
                "async function collectError(chunks, transforms) {\n" +
                "  try {\n" +
                "    await collect(chunks, transforms);\n" +
                "    return 'ok';\n" +
                "  } catch (error) {\n" +
                "    return error instanceof Error ? 'error' : 'other';\n" +
                "  }\n" +
                "}\n" +
                "\n" +
                "function rejectsBuiltin(name) {\n" +
                "  try {\n" +
                "    require(name);\n" +
                "  } catch (error) {\n" +
                "    return error && error.autojs6Code === 'ERR_AUTOJS6_BUILTIN_DISABLED';\n" +
                "  }\n" +
                "  return false;\n" +
                "}\n" +
                "\n" +
                "return (async () => {\n" +
                "  assert.strictEqual(zlib, nodeZlib);\n" +
                "  assert.strictEqual(zlib.shadow, undefined);\n" +
                "  assert.strictEqual(require.resolve('node:zlib'), 'zlib');\n" +
                "  assert.strictEqual(rejectsBuiltin('zlib/promises'), true);\n" +
                "  assert.strictEqual(rejectsBuiltin('node:zlib/promises'), true);\n" +
                "\n" +
                "  const gzip = zlib.gzipSync(Buffer.from('gzip-value'));\n" +
                "  assert.strictEqual(zlib.gunzipSync(gzip).toString('utf8'), 'gzip-value');\n" +
                "  const largePayload = Buffer.alloc(3 * 1024 * 1024, 65);\n" +
                "  assert.strictEqual(\n" +
                "    zlib.gunzipSync(zlib.gzipSync(largePayload)).length,\n" +
                "    largePayload.length\n" +
                "  );\n" +
                "\n" +
                "  const deflated = zlib.deflateSync(Buffer.from('deflate-value'));\n" +
                "  assert.strictEqual(zlib.inflateSync(deflated).toString('utf8'), 'deflate-value');\n" +
                "\n" +
                "  const raw = zlib.deflateRawSync(Buffer.from('raw-value'));\n" +
                "  assert.strictEqual(zlib.inflateRawSync(raw).toString('utf8'), 'raw-value');\n" +
                "\n" +
                "  const callbackGzip = await promisify(zlib.gzip)(Buffer.from('callback-gzip'));\n" +
                "  assert.strictEqual(\n" +
                "    zlib.gunzipSync(callbackGzip).toString('utf8'),\n" +
                "    'callback-gzip'\n" +
                "  );\n" +
                "\n" +
                "  const brotli = await promisify(zlib.brotliCompress)(Buffer.from('brotli-value'), {\n" +
                "    params: { [zlib.constants.BROTLI_PARAM_QUALITY]: 1 }\n" +
                "  });\n" +
                "  assert.strictEqual(\n" +
                "    zlib.brotliDecompressSync(brotli).toString('utf8'),\n" +
                "    'brotli-value'\n" +
                "  );\n" +
                "\n" +
                "  assert.strictEqual(\n" +
                "    await collect([Buffer.from('stream-value')], [zlib.createGzip(), zlib.createGunzip()]),\n" +
                "    'stream-value'\n" +
                "  );\n" +
                "  fs.writeFileSync('./stream-source.txt', 'fs-pipeline-value', 'utf8');\n" +
                "  await pipeline(\n" +
                "    fs.createReadStream('./stream-source.txt'),\n" +
                "    zlib.createGzip(),\n" +
                "    fs.createWriteStream('./stream-source.txt.gz')\n" +
                "  );\n" +
                "  assert.strictEqual(\n" +
                "    zlib.gunzipSync(fs.readFileSync('./stream-source.txt.gz')).toString('utf8'),\n" +
                "    'fs-pipeline-value'\n" +
                "  );\n" +
                "  assert.strictEqual(\n" +
                "    await collectError([Buffer.from('not gzip')], [zlib.createGunzip()]),\n" +
                "    'error'\n" +
                "  );\n" +
                "  assert.throws(\n" +
                "    () => zlib.gunzipSync(Buffer.from('not gzip')),\n" +
                "    /incorrect header|invalid/i\n" +
                "  );\n" +
                "  assert.strictEqual(Object.isFrozen(zlib.constants), true);\n" +
                "\n" +
                "  console.log('zlib.buffer.focused.ready=true');\n" +
                "})();\n";
    }

    private static String cryptoSafeExpansionAndShadowingSource() {
        return "\"nodejs\";\n" +
                "const assert = require(\"assert/strict\");\n" +
                "const crypto = require(\"crypto\");\n" +
                "const nodeCrypto = require(\"node:crypto\");\n" +
                "\n" +
                "return (async () => {\n" +
                "  assert.strictEqual(crypto, nodeCrypto);\n" +
                "  assert.strictEqual(crypto.shadow, undefined);\n" +
                "  assert.strictEqual(require.resolve(\"crypto\"), \"crypto\");\n" +
                "  assert.strictEqual(require.resolve(\"node:crypto\"), \"crypto\");\n" +
                "\n" +
                "  const uuidA = crypto.randomUUID();\n" +
                "  const uuidB = crypto.randomUUID();\n" +
                "  assert.match(uuidA, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);\n" +
                "  assert.match(uuidB, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);\n" +
                "  assert.notStrictEqual(uuidA, uuidB);\n" +
                "\n" +
                "  const hmac = crypto.createHmac(\"sha256\", \"key\").update(\"abc\").digest(\"hex\");\n" +
                "  assert.strictEqual(hmac, \"9c196e32dc0175f86f4b1cb89289d6619de6bee699e4c378e68309ed97a1a6ab\");\n" +
                "\n" +
                "  assert.strictEqual(\n" +
                "    crypto.timingSafeEqual(Buffer.from(\"same\"), Buffer.from(\"same\")),\n" +
                "    true\n" +
                "  );\n" +
                "  assert.strictEqual(\n" +
                "    crypto.timingSafeEqual(Buffer.from(\"same\"), Buffer.from(\"diff\")),\n" +
                "    false\n" +
                "  );\n" +
                "  assert.throws(() => crypto.timingSafeEqual(Buffer.from(\"a\"), Buffer.from(\"aa\")));\n" +
                "\n" +
                "  assert.strictEqual(typeof crypto.createPrivateKey, \"undefined\");\n" +
                "  assert.strictEqual(typeof crypto.createPublicKey, \"undefined\");\n" +
                "  assert.strictEqual(typeof crypto.generateKeyPair, \"undefined\");\n" +
                "  assert.strictEqual(typeof crypto.createSign, \"undefined\");\n" +
                "  assert.strictEqual(typeof crypto.createVerify, \"undefined\");\n" +
                "\n" +
                "  // Current Safe Profile exposes limited WebCrypto; keep it bounded and documented.\n" +
                "  assert.strictEqual(typeof crypto.webcrypto, \"object\");\n" +
                "  assert.strictEqual(crypto.subtle, crypto.webcrypto.subtle);\n" +
                "  assert.strictEqual(typeof crypto.webcrypto.createHmac, \"undefined\");\n" +
                "  assert.strictEqual(typeof crypto.webcrypto.createPrivateKey, \"undefined\");\n" +
                "\n" +
                "  console.log(\"crypto.expansion.focused.ready=true\");\n" +
                "})();\n";
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

    private WorkspaceInvocation executeExactGraph(
            String label,
            String entryName,
            LinkedHashMap<String, String> sourceFiles,
            LinkedHashMap<String, ProviderAction> exactProviderActions
    ) throws Exception {
        return execute(
                label,
                entryName,
                sourceFiles,
                new LinkedHashMap<>(),
                false,
                null,
                exactProviderActions,
                SCRIPT_TIMEOUT_MS
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
            RecordingCallback callback = new RecordingCallback(runtimePid);
            Bundle request = runtimeRequest(
                    executionId,
                    new File(sandboxRoot, entryName),
                    sandboxRoot,
                    sourceFiles.get(entryName),
                    moduleSources,
                    provider == null ? exactGraphProvider : provider,
                    timeoutMs
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

    private DirectWorkspaceSession openDirectWorkspace(String label) throws Exception {
        String executionId = "x3f-workspace-" + label + "-" + UUID.randomUUID();
        File invocationRoot = new File(
                targetContext.getCacheDir(),
                "node-plugin-android-conformance/" + executionId
        );
        File pluginCache = new File(invocationRoot, "plugin-cache");
        File hostSandbox = new File(invocationRoot, "host-sandbox");
        File transportRoot = new File(invocationRoot, "transport");
        ensureDirectory(pluginCache);
        ensureDirectory(hostSandbox);
        ensureDirectory(transportRoot);
        File inputArchive = new File(transportRoot, "workspace-input.zip");
        File outputArchive = new File(transportRoot, "workspace-output.zip");
        writeWorkspaceInputArchive(inputArchive, new LinkedHashMap<>());
        assertTrue("Unable to create direct workspace output archive", outputArchive.createNewFile());

        ParcelFileDescriptor inputDescriptor = ParcelFileDescriptor.open(
                inputArchive,
                ParcelFileDescriptor.MODE_READ_ONLY
        );
        ParcelFileDescriptor outputDescriptor;
        try {
            outputDescriptor = ParcelFileDescriptor.open(
                    outputArchive,
                    ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_TRUNCATE
            );
        } catch (Throwable error) {
            inputDescriptor.close();
            if (error instanceof Exception) throw (Exception) error;
            if (error instanceof Error) throw (Error) error;
            throw new AssertionError(error);
        }
        Bundle request = new Bundle();
        request.putInt(
                NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_TRANSPORT_VERSION,
                NodeJsRuntimeContract.WORKSPACE_ARCHIVE_TRANSPORT_CONTRACT_VERSION
        );
        request.putParcelable(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_INPUT_FD, inputDescriptor);
        request.putParcelable(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_OUTPUT_FD, outputDescriptor);
        request.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, executionId);
        request.putString(NodeJsRuntimeContract.KEY_WORKING_DIRECTORY, hostSandbox.getAbsolutePath());
        request.putString(NodeJsRuntimeContract.KEY_SANDBOX_ROOT, hostSandbox.getAbsolutePath());
        request.putString(NodeJsRuntimeContract.KEY_WORKSPACE_RELATIVE_WORKING_DIRECTORY, "");
        request.putInt(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_MAX_FILES, WORKSPACE_MAX_FILES);
        request.putLong(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_MAX_BYTES, WORKSPACE_MAX_BYTES);
        try {
            return new DirectWorkspaceSession(
                    invocationRoot,
                    PluginWorkspaceArchiveSession.open(pluginCache, request)
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
        request.putString(NodeJsRuntimeContract.KEY_RUNTIME_ADAPTER, NodeJsPluginIds.VARIANT_NODE_24_5);
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
        request.putBoolean(NodeJsRuntimeContract.KEY_ESM_EXPERIMENTAL_ENABLED, true);
        request.putBoolean(NodeJsRuntimeContract.KEY_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED, true);
        request.putBoolean(NodeJsRuntimeContract.KEY_RAW_NODE_NETWORK_MODULES_EXPERIMENTAL_ENABLED, false);
        request.putBoolean(NodeJsRuntimeContract.KEY_WORKER_THREADS_EXPERIMENTAL_ENABLED, false);
        request.putBoolean(NodeJsRuntimeContract.KEY_CHILD_PROCESS_EXPERIMENTAL_ENABLED, false);
        request.putBoolean(NodeJsRuntimeContract.KEY_JAVA_INTEROP_EXPERIMENTAL_ENABLED, false);
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
                NodeJsPluginIds.VARIANT_NODE_24_5,
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
                        .endsWith(RUNTIME_PROCESS_SUFFIX)
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

    private static void writeUtf8(File target, String value) throws IOException {
        ensureDirectory(target.getParentFile());
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
    }

    private static String readUtf8(File source) throws IOException {
        try (FileInputStream input = new FileInputStream(source);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) continue;
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
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

        void assertHealthyAndResolved(String expectedName) {
            assertEquals("plaintext provider failure", null, failure.get());
            assertEquals("plaintext provider resolve count", 1, resolveCount.get());
            assertEquals("plaintext provider Binder caller PID", expectedRuntimePid, callerPid.get());
            assertNotEquals(
                    "plaintext provider unexpectedly ran in the instrumentation process",
                    Process.myPid(),
                    callerPid.get()
            );
            assertTrue(
                    lastResolvedPath.get(),
                    lastResolvedPath.get().toLowerCase(Locale.ROOT)
                            .endsWith(expectedName.toLowerCase(Locale.ROOT))
            );
            assertEquals(
                    NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_OPERATION_RESOLVE_EXISTING,
                    lastOperation.get()
            );
        }

        void assertHealthyAndMaterialized(String expectedName) {
            assertEquals("plaintext provider failure", null, failure.get());
            assertEquals("plaintext provider resolve count", 1, resolveCount.get());
            assertEquals("plaintext provider Binder caller PID", expectedRuntimePid, callerPid.get());
            assertNotEquals(
                    "plaintext provider unexpectedly ran in the instrumentation process",
                    Process.myPid(),
                    callerPid.get()
            );
            assertTrue(
                    lastResolvedPath.get(),
                    lastResolvedPath.get().toLowerCase(Locale.ROOT)
                            .endsWith(expectedName.toLowerCase(Locale.ROOT))
            );
            assertEquals(
                    NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_OPERATION_MATERIALIZE_MISSING_PLAINTEXT,
                    lastOperation.get()
            );
        }

        void assertHealthyAndNotCalled() {
            assertEquals("plaintext provider failure", null, failure.get());
            assertEquals("TSX admission should fail before provider dispatch", 0, resolveCount.get());
            assertEquals("", lastResolvedPath.get());
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

        void assertWorkspaceExcludes(String forbiddenPath, String forbiddenText) throws IOException {
            String normalizedForbiddenPath = forbiddenPath.replace('\\', '/');
            byte[] forbiddenBytes = forbiddenText.getBytes(StandardCharsets.UTF_8);
            try (ZipInputStream input = new ZipInputStream(new FileInputStream(outputArchive))) {
                ZipEntry entry;
                byte[] buffer = new byte[4096];
                while ((entry = input.getNextEntry()) != null) {
                    assertNotEquals(
                            "provider materialization leaked into workspace output",
                            normalizedForbiddenPath,
                            entry.getName()
                    );
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    int count;
                    while ((count = input.read(buffer)) >= 0) {
                        if (count == 0) continue;
                        bytes.write(buffer, 0, count);
                    }
                    assertFalse(
                            "provider plaintext leaked through workspace output entry " + entry.getName(),
                            containsBytes(bytes.toByteArray(), forbiddenBytes)
                    );
                    input.closeEntry();
                }
            }
        }

        private static boolean containsBytes(byte[] haystack, byte[] needle) {
            if (needle.length == 0) return true;
            for (int start = 0; start <= haystack.length - needle.length; start++) {
                int index = 0;
                while (index < needle.length && haystack[start + index] == needle[index]) {
                    index++;
                }
                if (index == needle.length) return true;
            }
            return false;
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
