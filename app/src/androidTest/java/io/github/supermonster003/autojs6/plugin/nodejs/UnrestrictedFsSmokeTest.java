package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Roadmap M6.2 / M23.1: unrestricted fs reaches outside the request workspace after
 * Android grants All files access. Since M23.1 {@code require("fs")} is Node's own
 * module, so /proc, /sys and /dev are Android's decision like every other path:
 * the process's own procfs entries and /dev/null read fine, and whatever Android
 * refuses comes back as a plain Node error without an AutoJs6 policy code.
 */
@RunWith(AndroidJUnit4.class)
public final class UnrestrictedFsSmokeTest {

    private static final long BIND_TIMEOUT_MS = 30_000L;

    @Test
    public void externalStorageWorksAndSystemPathsFollowAndroid() throws Exception {
        Assume.assumeTrue(
                "MANAGE_EXTERNAL_STORAGE is only available from Android 11",
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        );

        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext()
                .getApplicationContext();
        grantAllFilesAccess(context.getPackageName());

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
                context.getPackageName(),
                context.getPackageName() + ".NodeJsRuntimePluginService"
        );
        assertTrue(
                "bindService was rejected",
                context.bindService(new Intent().setComponent(component), connection, Context.BIND_AUTO_CREATE)
        );
        try {
            assertTrue("bind timed out", connected.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS));
            INodeJsRuntimePlugin runtime = INodeJsRuntimePlugin.Stub.asInterface(binder.get());
            assertNotNull("runtime proxy unavailable", runtime);

            Bundle request = new Bundle();
            request.putString(NodeJsRuntimeContract.KEY_SOURCE, unrestrictedFsProbeSource());
            Bundle result = runtime.runScript(request, new INodeJsRuntimeCallback.Stub() {
                @Override
                public void onEvent(Bundle event) {
                }
            });

            assertNotNull("runScript returned null", result);
            String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
            String stderr = result.getString(NodeJsRuntimeContract.KEY_STDERR, "");
            assertTrue(
                    "unrestricted fs probe failed: "
                            + result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "")
                            + " / stderr: " + stderr + " / stdout: " + stdout,
                    result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED)
            );
            assertTrue("external write/read marker missing: " + stdout, stdout.contains("m6.fs.external=ok"));
            assertTrue("/proc/self/status marker missing: " + stdout, stdout.contains("m23.fs.proc=ok"));
            assertTrue("/dev/null marker missing: " + stdout, stdout.contains("m23.fs.dev=ok"));
            assertTrue("/sys/kernel marker missing: " + stdout, stdout.contains("m23.fs.sys="));
            assertTrue("fs identity marker missing: " + stdout, stdout.contains("m23.fs.native=true"));
            assertFalse("an AutoJs6 fs policy code leaked: " + stdout, stdout.contains("ERR_AUTOJS6_FS_"));
        } finally {
            context.unbindService(connection);
        }
    }

    private static void grantAllFilesAccess(String packageName) throws IOException {
        shell("appops set " + packageName + " MANAGE_EXTERNAL_STORAGE allow");
        String state = shell("appops get " + packageName + " MANAGE_EXTERNAL_STORAGE");
        assertTrue(
                "MANAGE_EXTERNAL_STORAGE app-op was not granted: " + state,
                state.toLowerCase(Locale.US).contains("allow")
        );
    }

    private static String shell(String command) throws IOException {
        ParcelFileDescriptor descriptor = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .executeShellCommand(command);
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name()).trim();
        }
    }

    private static String unrestrictedFsProbeSource() {
        return "const fs = require('fs');\n"
                + "const directory = '/sdcard/Download';\n"
                + "const filename = directory + '/autojs6-node-unrestricted-fs-smoke-' + process.pid + '.txt';\n"
                + "const marker = 'm6-unrestricted-' + process.pid;\n"
                + "fs.mkdirSync(directory, { recursive: true });\n"
                + "try {\n"
                + "  fs.writeFileSync(filename, marker, 'utf8');\n"
                + "  const actual = fs.readFileSync(filename, 'utf8');\n"
                + "  if (actual !== marker) throw new Error('external storage round-trip mismatch');\n"
                + "  console.log('m6.fs.external=ok');\n"
                + "} finally {\n"
                + "  try { fs.unlinkSync(filename); } catch (_) {}\n"
                + "}\n"
                + "console.log('m23.fs.native=' + (fs === process.getBuiltinModule('fs') && require('fs/promises') === fs.promises));\n"
                + "const status = fs.readFileSync('/proc/self/status', 'utf8');\n"
                + "if (!/^Pid:\\s+\\d+/m.test(status)) throw new Error('unexpected /proc/self/status: ' + status.slice(0, 80));\n"
                + "console.log('m23.fs.proc=ok');\n"
                + "if (fs.readFileSync('/dev/null').length !== 0) throw new Error('/dev/null is not empty');\n"
                + "console.log('m23.fs.dev=ok');\n"
                + "let sys = 'ok';\n"
                + "try { fs.readdirSync('/sys/kernel'); } catch (error) {\n"
                + "  if (error.autojs6Code !== undefined) throw new Error('policy code on /sys/kernel: ' + error.autojs6Code);\n"
                + "  sys = String(error.code);\n"
                + "}\n"
                + "console.log('m23.fs.sys=' + sys);\n";
    }
}
