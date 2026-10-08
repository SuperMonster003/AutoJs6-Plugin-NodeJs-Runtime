package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.*;
import static io.github.supermonster003.autojs6.plugin.nodejs.ConcurrentExecutionSmokeTest.*;

import android.content.Context;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import androidx.test.platform.app.InstrumentationRegistry;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.json.JSONObject;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class CompileCacheSmokeTest {
    @Test public void nativeCachePersistsAcrossWorkspaceCopiesAndInvalidatesChangedSource() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = new File(context.getCacheDir(), "m23-compile-cache-" + UUID.randomUUID());
        assertTrue(root.mkdirs());
        try (Binding binding = new Binding()) {
            JSONObject first = run(binding, root, 42, false);
            assertTrue(first.toString(), first.getBoolean("enabled"));
            File cache = new File(first.getString("cache"));
            assertTrue(cache.isDirectory());
            Map<String, Long> saved = files(cache);
            assertFalse("Node did not persist module code cache", saved.isEmpty());
            JSONObject second = run(binding, root, 42, false);
            assertEquals(cache.getPath(), second.getString("cache"));
            assertEquals("portable cache keys changed with the execution directory", saved, files(cache));
            JSONObject changed = run(binding, root, 43, false);
            assertEquals(43, changed.getInt("value"));
            assertEquals("source changes should replace Node's existing entry", saved.keySet(), files(cache).keySet());
            JSONObject disabled = run(binding, root, 44, true);
            assertFalse(disabled.getBoolean("enabled"));
            assertEquals(44, disabled.getInt("value"));
        } finally { delete(root); }
    }

    @Test public void webGlobalsKeepNativeIdentity() throws Exception {
        try (Binding binding = new Binding()) {
            assertSucceeded(binding.runtime.runScript(request("m23-lazy-globals", """
                const assert = require('node:assert/strict');
                assert.equal(globalThis.crypto, require('node:crypto').webcrypto);
                assert.equal(CompressionStream, require('node:stream/web').CompressionStream);
                assert.equal(DecompressionStream, require('node:stream/web').DecompressionStream);
                assert.equal(TextDecoder, require('node:util').TextDecoder);
                assert.equal(require('autojs6:profile').compileCache.customLoader, false);
                console.log('m23.lazy-globals=PASS');
                """), null));
        }
    }

    @Test public void workersInheritTheWorkspaceCache() throws Exception {
        try (Binding binding = new Binding()) {
            assertSucceeded(binding.runtime.runScript(request("m23-worker-cache", """
                const { Worker } = require('node:worker_threads');
                const assert = require('node:assert/strict');
                const worker = new Worker(`require('node:worker_threads').parentPort.postMessage(!!require('node:module').getCompileCacheDir())`, {eval:true});
                worker.on('message', enabled => { assert.equal(enabled, true); console.log('m23.worker-cache=PASS'); });
                worker.on('error', error => { throw error; });
                """), null));
        }
    }

    private static JSONObject run(Binding binding, File root, int value, boolean disabled) throws Exception {
        String source = """
                const assert = require('node:assert/strict'), fs = require('node:fs'), m = require('node:module');
                const cjs = require('./large.cjs');
                m.flushCompileCache();
                const dir = m.getCompileCacheDir();
                console.log(JSON.stringify({value:cjs.value, enabled:!!dir, cache:dir ? fs.realpathSync(dir) : ''}));
                """;
        StringBuilder module = new StringBuilder("exports.value=" + value + ";\n");
        for (int i = 0; i < 1200; i++) module.append("exports.f").append(i).append(" = x => x + ").append(i).append(";\n");
        File input = new File(root, "input.zip"), output = new File(root, "output.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(input))) {
            entry(zip, ".autojs6-workspace-transport/input-manifest-v1.json", "{\"version\":1,\"deleteEligibleFiles\":[]}");
            entry(zip, "large.cjs", module.toString());
            entry(zip, "main.cjs", source);
        }
        Bundle request = request("cache-" + UUID.randomUUID(), source);
        request.putString(NodeJsRuntimeContract.KEY_SOURCE_NAME, new File(root, "main.cjs").getPath());
        request.putString(NodeJsRuntimeContract.KEY_WORKING_DIRECTORY, root.getPath());
        request.putString(NodeJsRuntimeContract.KEY_SANDBOX_ROOT, root.getPath());
        request.putInt(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_TRANSPORT_VERSION, 2);
        request.putString(NodeJsRuntimeContract.KEY_WORKSPACE_RELATIVE_WORKING_DIRECTORY, "");
        if (disabled) {
            request.putStringArray(NodeJsRuntimeContract.KEY_ENV_NAMES, new String[]{"NODE_DISABLE_COMPILE_CACHE"});
            request.putStringArray(NodeJsRuntimeContract.KEY_ENV_VALUES, new String[]{"1"});
        }
        try (ParcelFileDescriptor in = ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY);
             ParcelFileDescriptor out = ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_TRUNCATE)) {
            request.putParcelable(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_INPUT_FD, in);
            request.putParcelable(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_OUTPUT_FD, out);
            Bundle result = binding.runtime.runScript(request, null);
            assertSucceeded(result);
            return new JSONObject(result.getString(NodeJsRuntimeContract.KEY_STDOUT).trim());
        }
    }

    private static void entry(ZipOutputStream zip, String name, String source) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(source.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static Map<String, Long> files(File root) {
        Map<String, Long> result = new HashMap<>();
        collect(root, root, result);
        return result;
    }
    private static void collect(File root, File file, Map<String, Long> result) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) collect(root, child, result);
        else if (file.isFile()) result.put(root.toURI().relativize(file.toURI()).getPath(), file.lastModified());
    }
    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }
}
