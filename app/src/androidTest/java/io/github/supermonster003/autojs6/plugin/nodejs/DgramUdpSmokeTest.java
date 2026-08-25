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

/** Roadmap M9.1/M9.2: raw dgram/http2 are policy-controlled and trace_events is denied. */
@RunWith(AndroidJUnit4.class)
public final class DgramUdpSmokeTest {

    private static final long BIND_TIMEOUT_MS = 30_000L;
    private static final long SCRIPT_TIMEOUT_MS = 30_000L;

    @Test
    public void udpLoopbackWorksAndPolicyEdgesStayExplicit() throws Exception {
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

            Bundle enabledResult = runtime.runScript(
                    request(
                            "let traceCode = '';\n" +
                                    "try { require('trace_events'); } catch (error) {\n" +
                                    "  traceCode = error.autojs6Code || error.code || '';\n" +
                                    "}\n" +
                                    "if (traceCode !== 'ERR_AUTOJS6_BUILTIN_DISABLED') {\n" +
                                    "  throw new Error('unexpected trace_events code: ' + traceCode);\n" +
                                    "}\n" +
                                    "console.log('m9.trace_events.code=' + traceCode);\n" +
                                    "const Module = require('module');\n" +
                                    "if (!Module.isBuiltin('dgram')) throw new Error('dgram missing from module.isBuiltin');\n" +
                                    "const dgram = require('node:dgram');\n" +
                                    "const server = dgram.createSocket('udp4');\n" +
                                    "const client = dgram.createSocket('udp4');\n" +
                                    "const fail = setTimeout(() => {\n" +
                                    "  client.close();\n" +
                                    "  server.close();\n" +
                                    "  throw new Error('UDP loopback timed out');\n" +
                                    "}, 10000);\n" +
                                    "server.on('message', (message, remote) => {\n" +
                                    "  if (message.toString() !== 'm9.udp.ping') throw new Error('unexpected UDP request');\n" +
                                    "  server.send('m9.udp.pong', remote.port, remote.address);\n" +
                                    "});\n" +
                                    "client.on('message', (message) => {\n" +
                                    "  clearTimeout(fail);\n" +
                                    "  console.log('m9.dgram.reply=' + message.toString());\n" +
                                    "  console.log('m9.dgram=PASS');\n" +
                                    "  client.close();\n" +
                                    "  server.close();\n" +
                                    "});\n" +
                                    "server.bind(0, '127.0.0.1', () => {\n" +
                                    "  client.send('m9.udp.ping', server.address().port, '127.0.0.1');\n" +
                                    "});\n",
                            null
                    ),
                    emptyCallback()
            );
            assertSucceededWith(
                    enabledResult,
                    "m9.trace_events.code=ERR_AUTOJS6_BUILTIN_DISABLED",
                    "m9.dgram.reply=m9.udp.pong",
                    "m9.dgram=PASS"
            );

            Bundle disabledResult = runtime.runScript(
                    request(
                            "let code = '';\n" +
                                    "try { require('dgram'); } catch (error) {\n" +
                                    "  code = error.autojs6Code || error.code || '';\n" +
                                    "}\n" +
                                    "if (code !== 'ERR_AUTOJS6_BUILTIN_DISABLED') {\n" +
                                    "  throw new Error('unexpected dgram disabled code: ' + code);\n" +
                                    "}\n" +
                                    "console.log('m9.dgram.disabled=' + code);\n" +
                                    "let http2Code = '';\n" +
                                    "try { require('node:http2'); } catch (error) {\n" +
                                    "  http2Code = error.autojs6Code || error.code || '';\n" +
                                    "}\n" +
                                    "if (http2Code !== 'ERR_AUTOJS6_BUILTIN_DISABLED') {\n" +
                                    "  throw new Error('unexpected http2 disabled code: ' + http2Code);\n" +
                                    "}\n" +
                                    "console.log('m9.http2.disabled=' + http2Code);\n",
                            false
                    ),
                    emptyCallback()
            );
            assertSucceededWith(
                    disabledResult,
                    "m9.dgram.disabled=ERR_AUTOJS6_BUILTIN_DISABLED",
                    "m9.http2.disabled=ERR_AUTOJS6_BUILTIN_DISABLED"
            );
        } finally {
            context.unbindService(connection);
        }
    }

    private static Bundle request(String source, Boolean rawNetworkEnabled) {
        Bundle request = new Bundle();
        request.putString(NodeJsRuntimeContract.KEY_SOURCE, source);
        request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, SCRIPT_TIMEOUT_MS);
        if (rawNetworkEnabled != null) {
            request.putBoolean(NodeJsRuntimeContract.KEY_RAW_NODE_NETWORK_MODULES_ENABLED, rawNetworkEnabled);
        }
        return request;
    }

    private static INodeJsRuntimeCallback emptyCallback() {
        return new INodeJsRuntimeCallback.Stub() {
            @Override
            public void onEvent(Bundle event) {
            }
        };
    }

    private static void assertSucceededWith(Bundle result, String... expectedLines) {
        assertNotNull("runScript returned null", result);
        String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
        String stderr = result.getString(NodeJsRuntimeContract.KEY_STDERR, "");
        assertTrue(
                "script failed: " + result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "") +
                        " / stdout: " + stdout + " / stderr: " + stderr,
                result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED)
        );
        for (String expectedLine : expectedLines) {
            assertTrue(
                    "missing output '" + expectedLine + "'; stdout=" + stdout + " stderr=" + stderr,
                    stdout.contains(expectedLine)
            );
        }
    }
}
