package io.github.supermonster003.autojs6.plugin.nodejs;

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
 * Roadmap M6.2: unrestricted fs reaches outside the request workspace after
 * Android grants All files access, while runtime-owned sensitive roots remain
 * denied independently of that grant.
 */
@RunWith(AndroidJUnit4.class)
public final class UnrestrictedFsSmokeTest {

    private static final long BIND_TIMEOUT_MS = 30_000L;

    @Test
    public void externalStorageWorksButSensitiveRootsRemainDenied() throws Exception {
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
            assertTrue(
                    "/proc denial marker missing: " + stdout,
                    stdout.contains("m6.fs.denied.proc=ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED")
            );
            assertTrue(
                    "/sys denial marker missing: " + stdout,
                    stdout.contains("m6.fs.denied.sys=ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED")
            );
            assertTrue(
                    "/dev denial marker missing: " + stdout,
                    stdout.contains("m6.fs.denied.dev=ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED")
            );
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
                + "for (const [name, sensitivePath] of Object.entries({\n"
                + "  proc: '/proc/self/status',\n"
                + "  sys: '/sys/kernel',\n"
                + "  dev: '/dev/null'\n"
                + "})) {\n"
                + "  let denial = null;\n"
                + "  try { fs.readFileSync(sensitivePath); } catch (error) { denial = error; }\n"
                + "  if (!denial) throw new Error('sensitive path unexpectedly readable: ' + sensitivePath);\n"
                + "  const code = String(denial.autojs6Code || denial.code || denial.name || '');\n"
                + "  if (code !== 'ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED') {\n"
                + "    throw new Error('unexpected sensitive-path denial for ' + sensitivePath + ': ' + code);\n"
                + "  }\n"
                + "  console.log('m6.fs.denied.' + name + '=' + code);\n"
                + "}\n";
    }
}
