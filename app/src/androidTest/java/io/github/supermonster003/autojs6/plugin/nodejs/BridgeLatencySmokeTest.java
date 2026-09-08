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
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityBroker;
import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Measures the full JS/JNI/Binder round trip and exercises real asynchronous replies. */
@RunWith(AndroidJUnit4.class)
public final class BridgeLatencySmokeTest {
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

    @Test public void defaultJniRoundTripLatency() throws Exception { measure(null, "jni"); }
    @Test public void forcedFileRoundTripLatency() throws Exception { measure("file", "file"); }

    private void measure(String requestedTransport, String expectedTransport) throws Exception {
        Bundle result = run(new ScreenStateTestBroker(), requestedTransport,
                "(async () => { const device = require('device'); const samples = [];\n" +
                "for (let i = 0; i < 200; ++i) { const start = process.hrtime.bigint();\n" +
                "if (!(await device.isScreenOn())) throw new Error('response mismatch');\n" +
                "samples.push(Number(process.hrtime.bigint() - start) / 1e6); }\n" +
                "samples.sort((a, b) => a - b);\n" +
                "console.log('m12.latency=' + JSON.stringify({p50: samples[99], p95: samples[189]}));\n" +
                "})().catch(e => { console.error(e); process.exitCode = 1; });");
        String[] payload = result.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD);
        assertEquals(expectedTransport, value(payload, "embedded_script.bridge_live_transport"));
        assertEquals("200", value(payload, "embedded_script.bridge_live_dispatch_count"));
        assertEquals("0", value(payload, "embedded_script.bridge_live_dispatch_pending_count"));
        if ("jni".equals(expectedTransport)) {
            assertEquals("0", value(payload, "embedded_script.bridge_live_dispatch_poll_count"));
        }
        String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
        int marker = stdout.indexOf("m12.latency=");
        assertTrue("missing latency measurements: " + stdout, marker >= 0);
        JSONObject stats = new JSONObject(stdout.substring(marker + "m12.latency=".length()).trim());
        Bundle status = new Bundle();
        status.putString("stream", "\nM12 bridge " + expectedTransport + " 200 calls P50=" +
                stats.getDouble("p50") + " ms P95=" + stats.getDouble("p95") + " ms\n");
        InstrumentationRegistry.getInstrumentation().sendStatus(0, status);
    }

    @Test
    public void asynchronousBatchRetainsUnicodeAndRetiresTimedOutRequests() throws Exception {
        ScheduledExecutorService replies = Executors.newSingleThreadScheduledExecutor();
        CountDownLatch lateReply = new CountDownLatch(1);
        INodeJsHostCapabilityBroker broker = new INodeJsHostCapabilityBroker.Stub() {
            @Override public Bundle getBrokerInfo() { return new ScreenStateTestBroker().getBrokerInfo(); }
            @Override public Bundle getNativeDiagnostics() { return new Bundle(); }
            @Override public void destroy(Bundle reason) { }
            @Override public void dispatch(Bundle request, INodeJsHostCapabilityCallback callback) {
                try {
                    JSONObject call = new JSONObject(request.getString(NodeJsRuntimeContract.KEY_BRIDGE_REQUEST_JSON));
                    boolean late = "late".equals(call.getString("method"));
                    Bundle response = new Bundle();
                    response.putString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON,
                            new JSONObject().put("id", call.getString("id")).put("ok", true)
                                    .put("result", call.getJSONArray("args").get(0)).toString());
                    replies.schedule(() -> {
                        try {
                            callback.onResponse(response);
                            callback.onResponse(response); // A buggy broker must not settle twice.
                        } catch (RemoteException error) {
                            throw new AssertionError(error);
                        } finally {
                            if (late) lateReply.countDown();
                        }
                    }, late ? 250 : 20, TimeUnit.MILLISECONDS);
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
            }
        };
        try {
            Bundle first = run(broker, null,
                    "(async () => { const bridge = require('autojs6:bridge');\n" +
                    "const text = 'bridge-' + String.fromCodePoint(0x1f680, 0x4e2d) + '\\u0000-end';\n" +
                    "const call = (method, value, timeoutMs = 5000) => bridge.callAutoJs('device', method, [value], {permissions:['device'], timeoutMs});\n" +
                    "const batch = Array.from({length:32}, (_, i) => call('echo', text + i));\n" +
                    "await call('overflow', text).then(() => { throw new Error('missing limit'); }, e => {\n" +
                    "if (e.code !== 'ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT') throw e; });\n" +
                    "const values = await Promise.all(batch);\n" +
                    "values.forEach((v, i) => { if (v !== text + i) throw new Error('UTF-8 corruption'); });\n" +
                    "await call('late', text, 20).then(() => { throw new Error('missing timeout'); }, e => {\n" +
                    "if (e.code !== 'ERR_AUTOJS6_BRIDGE_TIMEOUT') throw e; });\n" +
                    "if (bridge.__test.pendingCount() !== 0) throw new Error('pending leak');\n" +
                    "console.log('m12.batch=32 unicode timeout');\n" +
                    "})().catch(e => { console.error(e); process.exitCode = 1; });");
            assertTrue(first.getString(NodeJsRuntimeContract.KEY_STDOUT, "").contains("m12.batch=32 unicode timeout"));
            Bundle next = run(new ScreenStateTestBroker(), null,
                    "require('device').isScreenOn().then(v => { if (!v) throw new Error('wrong reply');\n" +
                    "setTimeout(() => console.log('m12.reuse=ok'), 400); });");
            assertTrue(next.getString(NodeJsRuntimeContract.KEY_STDOUT, "").contains("m12.reuse=ok"));
            assertTrue("late reply did not exercise the closed channel", lateReply.await(5, TimeUnit.SECONDS));
            assertEquals(value(first.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD), "embedded_script.runtime_plugin.pid"),
                    value(next.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD), "embedded_script.runtime_plugin.pid"));
        } finally {
            replies.shutdownNow();
        }
    }

    private Bundle run(INodeJsHostCapabilityBroker broker, String transport, String source) throws Exception {
        Bundle request = new Bundle();
        request.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, "m12-bridge-" + System.nanoTime());
        request.putString(NodeJsRuntimeContract.KEY_SOURCE, source);
        request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 20_000);
        request.putBinder(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_BROKER, broker.asBinder());
        request.putStringArray(NodeJsRuntimeContract.KEY_RUNTIME_MODULE_SOURCE_NAMES,
                new String[]{"autojs6:bridge-permissions", "autojs6:bridge-live-config"});
        request.putStringArray(NodeJsRuntimeContract.KEY_RUNTIME_MODULE_SOURCES,
                new String[]{"{\"version\":1,\"enforced\":true,\"permissions\":[\"device\"]}",
                        transport == null ? "{}" : "{\"transport\":\"" + transport + "\"}"});
        Bundle result = runtime.runScript(request, new INodeJsRuntimeCallback.Stub() {
            @Override public void onEvent(Bundle event) { }
        });
        assertNotNull(result);
        assertTrue("execution failed: " + result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "") +
                        " / stdout: " + result.getString(NodeJsRuntimeContract.KEY_STDOUT, "") +
                        " / stderr: " + result.getString(NodeJsRuntimeContract.KEY_STDERR, ""),
                result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
        return result;
    }

    private static String value(String[] payload, String key) {
        for (String entry : payload) {
            if (entry.startsWith(key + "=")) return entry.substring(key.length() + 1);
        }
        throw new AssertionError("missing native payload key: " + key);
    }
}
