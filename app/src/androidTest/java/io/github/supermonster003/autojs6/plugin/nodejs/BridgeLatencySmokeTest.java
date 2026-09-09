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
import org.json.JSONArray;
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
        // Channel reuse and FD baselines must observe consecutive executions in the same worker.
        assertTrue(context.bindService(new Intent().setComponent(new ComponentName(context.getPackageName(),
                context.getPackageName() + ".NodeJsRuntimeSlot0Service")), connection, Context.BIND_AUTO_CREATE));
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

    @Test
    public void pushedBurstIsBoundedAndListenersCloseWithoutPolling() throws Exception {
        INodeJsHostCapabilityBroker broker = new INodeJsHostCapabilityBroker.Stub() {
            @Override public Bundle getBrokerInfo() {
                Bundle info = new ScreenStateTestBroker().getBrokerInfo();
                info.putStringArray(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_MODULES, new String[]{"sensors"});
                return info;
            }
            @Override public Bundle getNativeDiagnostics() { return new Bundle(); }
            @Override public void destroy(Bundle reason) { }
            @Override public void dispatch(Bundle request, INodeJsHostCapabilityCallback callback) {
                try {
                    JSONObject call = new JSONObject(request.getString(NodeJsRuntimeContract.KEY_BRIDGE_REQUEST_JSON));
                    String id = call.getString("id");
                    boolean subscribe = "subscribe".equals(call.getString("method"));
                    if (subscribe && !call.optBoolean("events")) throw new AssertionError("push was not negotiated");
                    Bundle reply = new Bundle();
                    reply.putString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON,
                            new JSONObject().put("id", id).put("ok", true).put("result", subscribe
                                    ? new JSONObject().put("id", "burst").put("subscriptionId", "burst").put("type", "accelerometer")
                                    : JSONObject.NULL).toString());
                    callback.onResponse(reply);
                    if (subscribe) {
                        // Synchronous dispatch holds the Node thread until the burst is queued.
                        for (int i = 0; i < 300; i++) {
                            JSONObject event = new JSONObject().put("type", "event").put("event",
                                    new JSONObject().put("type", "accelerometer").put("values", new JSONArray().put(i).put(0).put(0)));
                            Bundle pushed = new Bundle();
                            pushed.putBoolean("event", true);
                            pushed.putString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON,
                                    new JSONObject().put("id", id).put("ok", true).put("event", true)
                                            .put("subscriptionId", "burst").put("result", event).toString());
                            callback.onResponse(pushed);
                        }
                    }
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
            }
        };
        Bundle result = run(broker, null,
                "(async () => { const values = []; let once = 0; let removed = 0;\n" +
                "const sub = require('sensors').subscribe('accelerometer');\n" +
                "const unwanted = () => removed++; sub.once('event', unwanted).off('event', unwanted);\n" +
                "sub.once('event', () => once++);\n" +
                "const done = new Promise(resolve => sub.on('event', event => { values.push(event.values[0]); if (values.length === 128) resolve(); }));\n" +
                "await sub.ready; await done; await sub.close();\n" +
                "if (sub.subscriptionId !== 'burst' || once !== 1 || removed !== 0 || values[0] !== 172 || values[127] !== 299) throw new Error('push ordering or listener mismatch');\n" +
                "console.log('m12.push=bounded closed'); })().catch(e => { console.error(e); process.exitCode = 1; });", "sensors");
        assertTrue(result.getString(NodeJsRuntimeContract.KEY_STDOUT, "").contains("m12.push=bounded closed"));
        String[] payload = result.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD);
        assertEquals("2", value(payload, "embedded_script.bridge_live_dispatch_count"));
        assertEquals("300", value(payload, "embedded_script.bridge_live_event_count"));
        assertEquals("172", value(payload, "embedded_script.bridge_live_event_dropped_count"));
        assertEquals("0", value(payload, "embedded_script.bridge_live_dispatch_poll_count"));
    }

    private Bundle run(INodeJsHostCapabilityBroker broker, String transport, String source, String... permissions) throws Exception {
        Bundle request = new Bundle();
        request.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, "m12-bridge-" + System.nanoTime());
        request.putString(NodeJsRuntimeContract.KEY_SOURCE, source);
        request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 20_000);
        request.putBinder(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_BROKER, broker.asBinder());
        request.putStringArray(NodeJsRuntimeContract.KEY_RUNTIME_MODULE_SOURCE_NAMES,
                new String[]{"autojs6:bridge-permissions", "autojs6:bridge-live-config"});
        request.putStringArray(NodeJsRuntimeContract.KEY_RUNTIME_MODULE_SOURCES,
                new String[]{new JSONObject().put("version", 1).put("enforced", true)
                        .put("permissions", new JSONArray(permissions.length == 0 ? new String[]{"device"} : permissions)).toString(),
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

    @Test public void binaryAttachmentsCloseAfterUseTimeoutAndExecutionExit() throws Exception {
        java.io.File payload = java.io.File.createTempFile("bridge-bytes-", ".bin", context.getCacheDir());
        byte[] content = new byte[8192];
        for (int index = 0; index < content.length; index++) content[index] = (byte) index;
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(payload)) { stream.write(content); }
        ScheduledExecutorService replies = Executors.newSingleThreadScheduledExecutor();
        INodeJsHostCapabilityBroker broker = new INodeJsHostCapabilityBroker.Stub() {
            @Override public Bundle getBrokerInfo() {
                Bundle info = new Bundle();
                info.putInt(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_BROKER_VERSION, 1);
                info.putStringArray(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_MODULES, new String[]{"image"});
                return info;
            }
            @Override public Bundle getNativeDiagnostics() { return new Bundle(); }
            @Override public void destroy(Bundle reason) { }
            @Override public void dispatch(Bundle request, INodeJsHostCapabilityCallback callback) {
                try {
                    JSONObject call = new JSONObject(request.getString(NodeJsRuntimeContract.KEY_BRIDGE_REQUEST_JSON));
                    boolean binary = "toBytes".equals(call.getString("method"));
                    Runnable reply = () -> {
                        try {
                            JSONObject result = binary ? new JSONObject().put("byteCount", content.length)
                                    : new JSONObject().put("id", "image-fixture").put("__autojs6ImageHandle", "image-fixture")
                                            .put("width", 2048).put("height", 1);
                            Bundle response = new Bundle();
                            response.putString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON,
                                    new JSONObject().put("id", call.getString("id")).put("ok", true).put("result", result).toString());
                            if (binary) {
                                try (android.os.ParcelFileDescriptor fd = android.os.ParcelFileDescriptor.open(payload,
                                        android.os.ParcelFileDescriptor.MODE_READ_ONLY)) {
                                    response.putParcelable(NodeJsRuntimeContract.KEY_BRIDGE_BINARY_PFD, fd);
                                    response.putLong(NodeJsRuntimeContract.KEY_BRIDGE_BINARY_BYTE_COUNT, content.length);
                                    callback.onResponse(response);
                                }
                            } else callback.onResponse(response);
                        } catch (Exception error) { throw new AssertionError(error); }
                    };
                    if (binary && "png".equals(call.getJSONArray("args").getJSONObject(1).optString("format")))
                        replies.schedule(reply, 100, TimeUnit.MILLISECONDS);
                    else reply.run();
                } catch (Exception error) { throw new AssertionError(error); }
            }
        };
        try {
            for (String transport : new String[]{"jni", "file"}) {
                Bundle warm = run(broker, transport, "console.log(process.pid);", "image");
                int pid = Integer.parseInt(warm.getString(NodeJsRuntimeContract.KEY_STDOUT, "").trim());
                long before = runtimeFdCount(pid);
                Bundle result = run(broker, transport, """
                    (async () => {
                      const image = require('image');
                      const frame = await image.readImage('fixture.png');
                      const held = [];
                      for (let i = 0; i < 40; ++i) {
                        const bytes = await image.toBytes(frame, 'rgba');
                        if (!Buffer.isBuffer(bytes) || bytes.length !== 8192 || bytes[4095] !== 255) throw new Error('bad mapped bytes');
                        held.push(bytes);
                      }
                      let timeouts = 0;
                      await Promise.all(Array.from({length: 16}, () => image.toBytes(frame, {format: 'png', timeoutMs: 10})
                        .catch(error => { if (error.code !== 'ERR_AUTOJS6_BRIDGE_TIMEOUT') throw error; ++timeouts; })));
                      if (timeouts !== 16) throw new Error('missing timeout');
                      await new Promise(resolve => setTimeout(resolve, 250));
                      const next = await image.toBytes(frame, 'rgba');
                      if (next[17] !== 17 || held[0][4095] !== 255) throw new Error('mapping invalidated');
                      console.log('m14.binary.lifetime=PASS');
                    })().catch(error => { console.error(error.stack); process.exitCode = 1; });
                    """, "image");
                assertTrue(result.getString(NodeJsRuntimeContract.KEY_STDOUT, "").contains("m14.binary.lifetime=PASS"));
                assertEquals("binary execution moved to another process", pid, result.getInt(NodeJsRuntimeContract.KEY_PID));
                long after = runtimeFdCount(pid);
                assertTrue("FD growth after " + transport + ": " + before + " -> " + after, after <= before + 2);
                System.out.println("m14.binary.fd." + transport + "=" + before + "->" + after);
            }
        } finally {
            replies.shutdownNow();
            payload.delete();
        }
    }

    private long runtimeFdCount(int expectedPid) throws RemoteException {
        // A non-debuggable Release process cannot be inspected through another process's /proc.
        // The runtime already reports its own idle FD count; require the same live worker at both ends.
        Bundle info = runtime.getRuntimeInfo();
        String[] payload = info.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD);
        assertEquals(Integer.toString(expectedPid), value(payload, "runtime_process.pid"));
        assertEquals("true", value(payload, "runtime_process.proc_fd_readable"));
        long count = Long.parseLong(value(payload, "runtime_process.fd_count"));
        assertTrue("runtime FD count unavailable", count > 0);
        return count;
    }

    private static String value(String[] payload, String key) {
        for (String entry : payload) {
            if (entry.startsWith(key + "=")) return entry.substring(key.length() + 1);
        }
        throw new AssertionError("missing native payload key: " + key);
    }
}
