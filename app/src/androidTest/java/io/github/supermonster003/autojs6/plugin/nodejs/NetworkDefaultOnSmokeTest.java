package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Roadmap M2.6: network builtins work without any opt-in flag. The request
 * deliberately omits KEY_RAW_NODE_NETWORK_MODULES_ENABLED; the
 * script must require('http'), serve one loopback request, and print the
 * response — the desktop-Node default.
 */
@RunWith(AndroidJUnit4.class)
public final class NetworkDefaultOnSmokeTest {

    private static final long BIND_TIMEOUT_MS = 30_000L;

    @Test
    public void httpServerWorksWithoutOptInFlag() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext()
                .getApplicationContext();
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
            request.putString(
                    NodeJsRuntimeContract.KEY_SOURCE,
                    "const http = require('http');\n" +
                            "const server = http.createServer((req, res) => {\n" +
                            "  res.end('m2.network.pong');\n" +
                            "});\n" +
                            "server.listen(0, '127.0.0.1', () => {\n" +
                            "  const port = server.address().port;\n" +
                            "  http.get({ host: '127.0.0.1', port }, (res) => {\n" +
                            "    let body = '';\n" +
                            "    res.on('data', (chunk) => { body += chunk; });\n" +
                            "    res.on('end', () => {\n" +
                            "      console.log('m2.network.reply=' + body);\n" +
                            "      server.close();\n" +
                            "    });\n" +
                            "  }).on('error', (error) => {\n" +
                            "    console.error('m2.network.error=' + error.message);\n" +
                            "    server.close();\n" +
                            "  });\n" +
                            "});\n"
            );
            Bundle result = runtime.runScript(request, new INodeJsRuntimeCallback.Stub() {
                @Override
                public void onEvent(Bundle event) {
                }
            });

            assertNotNull("runScript returned null", result);
            String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
            String stderr = result.getString(NodeJsRuntimeContract.KEY_STDERR, "");
            assertTrue(
                    "network script failed: " +
                            result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "") +
                            " / stderr: " + stderr,
                    result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED)
            );
            assertTrue(
                    "loopback http round-trip did not complete; stdout=" + stdout + " stderr=" + stderr,
                    stdout.contains("m2.network.reply=m2.network.pong")
            );
        } finally {
            context.unbindService(connection);
        }
    }
}
