package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Roadmap M2.4/M9.2/M13.4/M14.3: twenty-one real npm packages run inside the plugin runtime.
 * The original high-frequency CommonJS corpus is extended with axios and
 * Express loopback probes plus three ESM-only packages (nanoid, p-limit and
 * yocto-queue). The genuine npm-install tree is shipped as androidTest assets
 * through the workspace archive transport; main.js asserts one core behavior
 * per package without registry access on the Android device.
 */
@RunWith(AndroidJUnit4.class)
public final class NpmEcosystemSmokeTest {

    private static final String ASSET_ROOT = "npm_ecosystem_corpus";
    private static final String INPUT_MANIFEST_PATH =
            ".autojs6-workspace-transport/input-manifest-v1.json";
    private static final long BIND_TIMEOUT_MS = 30_000L;
    private static final long SCRIPT_TIMEOUT_MS = 120_000L;
    private static final List<String> PACKAGES = List.of(
            "lodash", "dayjs", "ms", "semver", "uuid",
            "debug", "mime", "qs", "js-yaml", "ajv",
            "axios", "express", "nanoid", "p-limit", "yocto-queue",
            "zod", "cheerio", "date-fns", "mqtt", "ws", "pngjs"
    );

    @Test
    public void commonJsAndEsmPackagesRunInsidePluginRuntime() throws Exception {
        Context targetContext = InstrumentationRegistry.getInstrumentation()
                .getTargetContext()
                .getApplicationContext();
        AssetManager assets = InstrumentationRegistry.getInstrumentation()
                .getContext()
                .getAssets();

        File invocationRoot = new File(
                targetContext.getCacheDir(),
                "npm-ecosystem-smoke/" + UUID.randomUUID()
        );
        File transportRoot = new File(invocationRoot, "transport");
        assertTrue("unable to create transport dir", transportRoot.mkdirs() || transportRoot.isDirectory());
        // Host-side sandbox: only referenced for request path mapping; the
        // actual files travel inside the workspace archive.
        File hostSandbox = new File(invocationRoot, "host-sandbox");
        assertTrue("unable to create host sandbox", hostSandbox.mkdirs() || hostSandbox.isDirectory());
        File inputArchive = new File(transportRoot, "workspace-input.zip");
        File outputArchive = new File(transportRoot, "workspace-output.zip");
        assertTrue("unable to create output archive", outputArchive.createNewFile());

        String entrySource = writeCorpusArchive(assets, inputArchive);

        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBinder> binder = new AtomicReference<>();
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                binder.set(service);
                connected.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };
        ComponentName component = new ComponentName(
                targetContext.getPackageName(),
                targetContext.getPackageName() + ".NodeJsRuntimePluginService"
        );
        assertTrue(
                "bindService was rejected",
                targetContext.bindService(
                        new Intent().setComponent(component),
                        connection,
                        Context.BIND_AUTO_CREATE
                )
        );
        try {
            assertTrue("bind timed out", connected.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS));
            INodeJsRuntimePlugin runtime = INodeJsRuntimePlugin.Stub.asInterface(binder.get());
            assertNotNull("runtime proxy unavailable", runtime);

            Bundle request = new Bundle();
            request.putInt(
                    NodeJsRuntimeContract.KEY_CONTRACT_VERSION,
                    NodeJsRuntimeContract.CONTRACT_VERSION
            );
            request.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, "npm-ecosystem-" + UUID.randomUUID());
            request.putString(NodeJsRuntimeContract.KEY_SOURCE, entrySource);
            request.putString(
                    NodeJsRuntimeContract.KEY_SOURCE_NAME,
                    new File(hostSandbox, "main.js").getAbsolutePath()
            );
            request.putString(
                    NodeJsRuntimeContract.KEY_WORKING_DIRECTORY,
                    hostSandbox.getAbsolutePath()
            );
            request.putString(
                    NodeJsRuntimeContract.KEY_SANDBOX_ROOT,
                    hostSandbox.getAbsolutePath()
            );
            request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, SCRIPT_TIMEOUT_MS);
            request.putInt(
                    NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_TRANSPORT_VERSION,
                    NodeJsRuntimeContract.WORKSPACE_ARCHIVE_TRANSPORT_CONTRACT_VERSION
            );
            request.putString(NodeJsRuntimeContract.KEY_WORKSPACE_RELATIVE_WORKING_DIRECTORY, "");
            request.putInt(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_MAX_FILES, 16_384);
            request.putLong(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_MAX_BYTES, 64L * 1024L * 1024L);

            ParcelFileDescriptor inputDescriptor = ParcelFileDescriptor.open(
                    inputArchive,
                    ParcelFileDescriptor.MODE_READ_ONLY
            );
            ParcelFileDescriptor outputDescriptor = ParcelFileDescriptor.open(
                    outputArchive,
                    ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_TRUNCATE
            );
            request.putParcelable(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_INPUT_FD, inputDescriptor);
            request.putParcelable(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_OUTPUT_FD, outputDescriptor);

            Bundle result;
            try {
                result = runtime.runScript(request, new INodeJsRuntimeCallback.Stub() {
                    @Override
                    public void onEvent(Bundle event) {
                    }
                });
            } finally {
                inputDescriptor.close();
                outputDescriptor.close();
            }

            assertNotNull("runScript returned null", result);
            String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
            String stderr = result.getString(NodeJsRuntimeContract.KEY_STDERR, "");
            assertTrue(
                    "npm corpus run failed: " +
                            result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "") +
                            " / stderr: " + stderr + " / stdout: " + stdout,
                    result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED)
            );
            for (String packageName : PACKAGES) {
                assertTrue(
                        "package '" + packageName + "' did not pass; stdout=" + stdout +
                                " stderr=" + stderr,
                        stdout.contains("npm." + packageName + "=ok")
                );
            }
            assertTrue("suite terminator missing; stdout=" + stdout, stdout.contains("npm.suite=done"));
        } finally {
            targetContext.unbindService(connection);
            deleteRecursively(invocationRoot);
        }
    }

    /**
     * Streams every corpus asset into the workspace input archive and returns
     * the entry script's source. Assets are enumerated recursively; binary
     * copy, no decoding, so package payloads arrive byte-identical.
     */
    private static String writeCorpusArchive(AssetManager assets, File archive) throws Exception {
        // This is an instrumentation screenshot for the PNG corpus, independent of MediaProjection consent.
        Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull("instrumentation screenshot is unavailable", screenshot);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, png));
        byte[] expected = new JSONObject().put("width", screenshot.getWidth()).put("height", screenshot.getHeight())
                .toString().getBytes(StandardCharsets.UTF_8);
        screenshot.recycle();
        List<String> files = new ArrayList<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(ASSET_ROOT);
        while (!pending.isEmpty()) {
            String directory = pending.removeFirst();
            String[] children = assets.list(directory);
            if (children == null || children.length == 0) {
                files.add(directory);
                continue;
            }
            for (String child : children) {
                pending.addLast(directory + "/" + child);
            }
        }
        assertTrue("corpus asset sweep found too few files: " + files.size(), files.size() > 1000);

        String entrySource = null;
        List<String> workspaceNames = new ArrayList<>();
        for (String assetPath : files) {
            workspaceNames.add(assetPath.substring(ASSET_ROOT.length() + 1));
        }
        workspaceNames.add("device-screen.png");
        workspaceNames.add("device-screen.json");
        Collections.sort(workspaceNames);

        JSONObject manifest = new JSONObject()
                .put("version", 1)
                .put("deleteEligibleFiles", new JSONArray(workspaceNames));
        try (ZipOutputStream output = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(archive)))) {
            output.putNextEntry(new ZipEntry(INPUT_MANIFEST_PATH));
            output.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            for (String workspaceName : workspaceNames) {
                byte[] content = "device-screen.png".equals(workspaceName) ? png.toByteArray()
                        : "device-screen.json".equals(workspaceName) ? expected
                        : readAsset(assets, ASSET_ROOT + "/" + workspaceName);
                if ("main.js".equals(workspaceName)) {
                    entrySource = new String(content, StandardCharsets.UTF_8);
                }
                output.putNextEntry(new ZipEntry(workspaceName));
                output.write(content);
                output.closeEntry();
            }
        }
        assertNotNull("corpus is missing main.js", entrySource);
        return entrySource;
    }

    private static byte[] readAsset(AssetManager assets, String path) throws IOException {
        try (InputStream input = assets.open(path);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static void deleteRecursively(File target) {
        File[] children = target.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        target.delete();
    }
}
