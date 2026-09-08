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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.json.JSONArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class BridgeFileTransportSmokeTest {
    private Context context;
    private ServiceConnection connection;
    private INodeJsRuntimePlugin runtime;

    @Before
    public void bind() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBinder> binder = new AtomicReference<>();
        connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {
                binder.set(service);
                connected.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        assertTrue(context.bindService(new Intent().setComponent(new ComponentName(context.getPackageName(),
                context.getPackageName() + ".NodeJsRuntimePluginService")), connection, Context.BIND_AUTO_CREATE));
        assertTrue("bind timed out", connected.await(30, TimeUnit.SECONDS));
        runtime = INodeJsRuntimePlugin.Stub.asInterface(binder.get());
        assertNotNull(runtime);
    }

    @After
    public void unbind() {
        if (context != null && connection != null) context.unbindService(connection);
    }

    @Test
    public void completedRequestsKeepOnlyBoundedDiagnostics() throws Exception {
        // Exercise the shipped file transport without depending on members removed by R8.
        Bundle request = new Bundle();
        request.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, "bounded-diagnostics-" + System.nanoTime());
        request.putString(NodeJsRuntimeContract.KEY_SOURCE, """
            (async () => {
              const device = require('device');
              for (let i = 0; i < 96; ++i) {
                if (!(await device.isScreenOn())) throw new Error('response mismatch');
              }
            })().catch(error => { console.error(error.stack); process.exitCode = 1; });
            """);
        request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 20_000);
        request.putBinder(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_BROKER, new ScreenStateTestBroker().asBinder());
        request.putStringArray(NodeJsRuntimeContract.KEY_RUNTIME_MODULE_SOURCE_NAMES,
                new String[]{"autojs6:bridge-permissions", "autojs6:bridge-live-config"});
        request.putStringArray(NodeJsRuntimeContract.KEY_RUNTIME_MODULE_SOURCES,
                new String[]{"{\"version\":1,\"enforced\":true,\"permissions\":[\"device\"]}", "{\"transport\":\"file\"}"});
        Bundle result = runtime.runScript(request, new INodeJsRuntimeCallback.Stub() {
            @Override public void onEvent(Bundle event) { }
        });
        assertNotNull(result);
        assertTrue("execution failed: " + result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "") +
                        " / stderr: " + result.getString(NodeJsRuntimeContract.KEY_STDERR, ""),
                result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
        String[] payload = result.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD);
        assertEquals("file", value(payload, "embedded_script.bridge_live_transport"));
        assertEquals("96", value(payload, "embedded_script.bridge_live_dispatch_count"));
        assertEquals("0", value(payload, "embedded_script.bridge_live_retained_request_count"));
        assertEquals("32", value(payload, "embedded_script.bridge_live_retained_response_count"));
        String recent = value(payload, "embedded_script.bridge_live_responses_json");
        assertTrue("response history grows with completed calls: " + recent, new JSONArray(recent).length() <= 32);
        assertTrue(recent.getBytes(StandardCharsets.UTF_8).length < 64 * 1024);
    }

    private static String value(String[] payload, String key) {
        for (String entry : payload) {
            if (entry.startsWith(key + "=")) return entry.substring(key.length() + 1);
        }
        throw new AssertionError("missing native payload key: " + key);
    }
}
