package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Exercises the bundled OpenSSL update through the public runtime and terminal entry points. */
@RunWith(AndroidJUnit4.class)
public final class OpenSslSecuritySmokeTest {
    private Context context;
    private File root;

    @Before public void prepareFixtures() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        root = new File(context.getCacheDir(), "openssl-security-" + UUID.randomUUID());
        assertTrue(root.mkdirs());
        var assets = InstrumentationRegistry.getInstrumentation().getContext().getAssets();
        for (String name : assets.list("openssl-security")) {
            try (var input = assets.open("openssl-security/" + name)) {
                Files.copy(input, new File(root, name).toPath());
            }
        }
    }

    @After public void removeFixtures() {
        if (root != null) deleteTree(root);
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    @Test public void cjsUsesUpdatedTlsAndCrypto() throws Exception {
        runEmbedded("main.cjs", "m25.openssl.cjs=PASS version=3.5.9");
    }

    @Test public void esmAndWorkersUseUpdatedTlsAndCrypto() throws Exception {
        runEmbedded("main.mjs", "m25.openssl.esm-workers=PASS version=3.5.9");
    }

    @Test public void terminalUsesUpdatedTlsAndCrypto() throws Exception {
        runTerminal("main.cjs", "m25.openssl.cjs=PASS version=3.5.9");
    }

    @Test public void relativeCrlDistributionPointsDoNotAmplifyCertificateMemory() throws Exception {
        runTerminal("crldp-memory.cjs", "m25.openssl.crldp=PASS");
    }

    private void runTerminal(String entry, String expected) throws Exception {
        File log = new File(root, "terminal.log");
        File launcher = new File(context.getApplicationInfo().nativeLibraryDir, BuildConfig.NODE_CLI_EXECUTABLE);
        ProcessBuilder builder = new ProcessBuilder(launcher.getAbsolutePath(), new File(root, entry).getAbsolutePath())
                .directory(root).redirectErrorStream(true).redirectOutput(log);
        builder.environment().remove("LD_LIBRARY_PATH");
        builder.environment().remove("LD_PRELOAD");
        builder.environment().remove("NODE_OPTIONS");
        Process process = builder.start();
        try {
            process.getOutputStream().close();
            assertTrue("terminal test timed out", process.waitFor(60, TimeUnit.SECONDS));
            String output = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
            assertEquals(output, 0, process.exitValue());
            assertTrue(output, output.contains(expected));
            report(output);
        } finally {
            process.destroy();
        }
    }

    private void runEmbedded(String entry, String expected) throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBinder> binder = new AtomicReference<>();
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {
                binder.set(service);
                connected.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name) {}
        };
        assertTrue(context.bindService(new Intent().setComponent(new ComponentName(context.getPackageName(),
                context.getPackageName() + ".NodeJsRuntimePluginService")), connection, Context.BIND_AUTO_CREATE));
        try {
            assertTrue("bind timed out", connected.await(30, TimeUnit.SECONDS));
            INodeJsRuntimePlugin runtime = INodeJsRuntimePlugin.Stub.asInterface(binder.get());
            assertNotNull(runtime);
            // Use the same archive path as real host projects, including CJS/ESM worker files.
            File inputArchive = new File(root, "input.zip");
            File[] fixtures = root.listFiles();
            assertNotNull(fixtures);
            JSONArray names = new JSONArray();
            try (var zip = new ZipOutputStream(Files.newOutputStream(inputArchive.toPath()))) {
                for (File file : fixtures) {
                    names.put(file.getName());
                    zip.putNextEntry(new ZipEntry(file.getName()));
                    Files.copy(file.toPath(), zip);
                    zip.closeEntry();
                }
                zip.putNextEntry(new ZipEntry(".autojs6-workspace-transport/input-manifest-v1.json"));
                zip.write(new JSONObject().put("version", 1).put("deleteEligibleFiles", names).toString()
                        .getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            Bundle request = new Bundle();
            request.putString(NodeJsRuntimeContract.KEY_SOURCE,
                    new String(Files.readAllBytes(new File(root, entry).toPath()), StandardCharsets.UTF_8));
            request.putString(NodeJsRuntimeContract.KEY_SOURCE_NAME, new File(root, entry).getAbsolutePath());
            request.putString(NodeJsRuntimeContract.KEY_WORKING_DIRECTORY, root.getAbsolutePath());
            request.putString(NodeJsRuntimeContract.KEY_SANDBOX_ROOT, root.getAbsolutePath());
            request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 60_000L);
            request.putInt(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_TRANSPORT_VERSION,
                    NodeJsRuntimeContract.WORKSPACE_ARCHIVE_TRANSPORT_CONTRACT_VERSION);
            request.putString(NodeJsRuntimeContract.KEY_WORKSPACE_RELATIVE_WORKING_DIRECTORY, "");
            Bundle result;
            try (var input = ParcelFileDescriptor.open(inputArchive, ParcelFileDescriptor.MODE_READ_ONLY);
                 var output = ParcelFileDescriptor.open(new File(root, "output.zip"), ParcelFileDescriptor.MODE_READ_WRITE
                         | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE)) {
                request.putParcelable(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_INPUT_FD, input);
                request.putParcelable(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_OUTPUT_FD, output);
                result = runtime.runScript(request, null);
            }
            assertNotNull(result);
            String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
            String details = result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "") + " / "
                    + result.getString(NodeJsRuntimeContract.KEY_STDERR, "") + " / " + stdout;
            assertTrue(details, result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            assertTrue(details, stdout.contains(expected));
            report(stdout);
        } finally {
            context.unbindService(connection);
        }
    }

    private static void report(String text) {
        Bundle status = new Bundle();
        status.putString("stream", "\n" + text);
        InstrumentationRegistry.getInstrumentation().sendStatus(0, status);
    }
}
