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
                    "const batch = Array.from({length:40}, (_, i) => call('echo', text + i));\n" +
                    "if (bridge.__test.pendingCount() !== 32 || bridge.__test.waitingCount() !== 8) throw new Error('window ' + bridge.__test.pendingCount() + '/' + bridge.__test.waitingCount());\n" +
                    "const values = await Promise.all(batch);\n" +
                    "values.forEach((v, i) => { if (v !== text + i) throw new Error('UTF-8 corruption'); });\n" +
                    "if (bridge.__test.waitingCount() !== 0) throw new Error('waiting leak');\n" +
                    "const slow = Array.from({length:32}, (_, i) => call('late', text + i));\n" +
                    "await call('queued', text, 20).then(() => { throw new Error('missing queued timeout'); }, e => {\n" +
                    "if (e.code !== 'ERR_AUTOJS6_BRIDGE_TIMEOUT') throw e; });\n" +
                    "if (bridge.__test.waitingCount() !== 0) throw new Error('queued timeout leak');\n" +
                    "const slowValues = await Promise.all(slow);\n" +
                    "slowValues.forEach((v, i) => { if (v !== text + i) throw new Error('slow mismatch'); });\n" +
                    "const cfetch = require('fetch');\n" +
                    "const fetches = await Promise.allSettled(Array.from({length:40}, () => cfetch('https://example.invalid/')));\n" +
                    "const fetchCodes = new Set(fetches.map(r => r.status === 'rejected' ? String(r.reason.code) : 'fulfilled'));\n" +
                    "if (fetchCodes.size !== 1 || fetchCodes.has('ERR_AUTOJS6_NETWORK_POLICY_DENIED')) throw new Error('fetch window ' + [...fetchCodes]);\n" +
                    "if (cfetch.policy.maxConcurrentRequests !== undefined || require('websocket').policy.maxConnections !== undefined) throw new Error('policy still lists a concurrency cap');\n" +
                    "const limits = require('autojs6:profile').bridgeLimits;\n" +
                    "if (String(limits.runtimeEnforced) !== 'maxPendingBridgeCalls' || limits.hostEnforced.length !== 5) throw new Error('bridgeLimits report ' + JSON.stringify(limits));\n" +
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

    @Test public void controlledNetworkFacadesLeaveLimitsToTheHost() throws Exception {
        java.util.concurrent.ConcurrentLinkedQueue<String> seen = new java.util.concurrent.ConcurrentLinkedQueue<>();
        AtomicReference<String> largeResponseFailure = new AtomicReference<>("");
        INodeJsHostCapabilityBroker broker = new INodeJsHostCapabilityBroker.Stub() {
            @Override public Bundle getBrokerInfo() {
                Bundle info = new ScreenStateTestBroker().getBrokerInfo();
                info.putStringArray(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_MODULES, new String[]{"device", "fetch", "websocket"});
                return info;
            }
            @Override public Bundle getNativeDiagnostics() { return new Bundle(); }
            @Override public void destroy(Bundle reason) { }
            @Override public void dispatch(Bundle request, INodeJsHostCapabilityCallback callback) {
                try {
                    JSONObject call = new JSONObject(request.getString(NodeJsRuntimeContract.KEY_BRIDGE_REQUEST_JSON));
                    String module = call.getString("module"), method = call.getString("method");
                    JSONObject descriptor = call.getJSONArray("args").optJSONObject(0);
                    JSONObject result = new JSONObject();
                    if ("fetch".equals(module)) {
                        String url = descriptor.getString("url");
                        String path = new java.net.URL(url).getPath();
                        seen.add("fetch " + descriptor.getString("method") + " " + path + " " + descriptor.toString());
                        result.put("url", url).put("status", 200).put("statusText", "OK").put("headers", new JSONArray());
                        if (path.startsWith("/redirect/")) {
                            int hops = Integer.parseInt(path.substring("/redirect/".length()));
                            if (hops > 0) {
                                result.put("status", 302).put("headers", new JSONArray().put(new JSONArray().put("location").put("/redirect/" + (hops - 1))));
                            }
                            result.put("bodyText", "hop " + hops);
                        } else if (path.startsWith("/large/")) {
                            byte[] bytes = new byte[Integer.parseInt(path.substring("/large/".length()))];
                            for (int index = 0; index < bytes.length; index++) bytes[index] = (byte) (index % 251);
                            result.put("bodyBase64", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)).put("bodyBytes", bytes.length);
                        } else {
                            result.put("headers", new JSONArray().put(new JSONArray().put("content-type").put("application/json")));
                            result.put("bodyText", descriptor.toString());
                        }
                    } else if ("drainEvents".equals(method)) {
                        Bundle response = new Bundle();
                        response.putString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON,
                                new JSONObject().put("id", call.getString("id")).put("ok", true).put("result", new JSONArray()).toString());
                        callback.onResponse(response);
                        return;
                    } else {
                        seen.add("websocket " + method + " " + (descriptor == null ? "" : descriptor.toString()).replaceAll("\"(text|dataBase64)\":\"[^\"]*\"", "$1=<payload>"));
                        if ("connect".equals(method)) {
                            result.put("id", "ws-" + seen.size()).put("url", descriptor.getString("url")).put("readyState", "open")
                                    .put("maxMessageBytes", descriptor.optInt("maxMessageBytes", 65536))
                                    .put("maxQueueSize", descriptor.optInt("maxQueueSize", 32));
                        } else if ("send".equals(method)) {
                            seen.add("websocket sent " + descriptor.optString("text", "").length());
                        }
                    }
                    Bundle response = new Bundle();
                    response.putString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON,
                            new JSONObject().put("id", call.getString("id")).put("ok", true).put("result", result).toString());
                    try {
                        callback.onResponse(response);
                    } catch (RemoteException | RuntimeException error) {
                        largeResponseFailure.set(error.getClass().getSimpleName());
                    }
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
            }
        };
        Bundle result = run(broker, null,
                "(async () => { const assert = require('assert'); const cfetch = require('fetch'); const base = 'http://fake.local';\n" +
                "let r = await cfetch(base + '/echo', { method: 'PUT', body: 'x', timeoutMs: 300000, maxResponseBytes: 268435456, maxRedirects: 15 });\n" +
                "let d = await r.json();\n" +
                "assert.deepStrictEqual([d.method, d.bodyText, d.timeoutMs, d.maxResponseBytes, d.maxRedirects], ['PUT', 'x', 300000, 268435456, 15]);\n" +
                "r = await cfetch(base + '/echo'); d = await r.json();\n" +
                "assert.deepStrictEqual([d.method, 'maxResponseBytes' in d, d.timeoutMs, d.maxRedirects], ['GET', false, 30000, 20]);\n" +
                "r = await cfetch(base + '/echo', { method: 'GET', body: 'b' }); d = await r.json(); assert.strictEqual(d.bodyText, 'b');\n" +
                "r = await cfetch(base + '/redirect/12', { maxRedirects: 15 }); assert.strictEqual(r.status, 200); assert.strictEqual(await r.text(), 'hop 0');\n" +
                "await assert.rejects(cfetch(base + '/redirect/3', { maxRedirects: 2 }), e => e.code === 'ERR_AUTOJS6_NETWORK_POLICY_DENIED');\n" +
                "r = await cfetch(base + '/large/262144', { maxResponseBytes: 10 }); assert.strictEqual((await r.arrayBuffer()).byteLength, 262144);\n" +
                "const large = await cfetch(base + '/large/2097152', { timeoutMs: 3000 }).then(x => 'ok', e => String(e.code));\n" +
                "assert.deepStrictEqual([cfetch.policy.hardMaxResponseBytes, cfetch.policy.limitsEnforcedBy, cfetch.policy.defaultMaxRedirects, require('axios').policy.limitsEnforcedBy], [undefined, 'host_provider', 20, 'host_provider']);\n" +
                "const ws = require('websocket');\n" +
                "const conn = await ws.connect('ws://fake.local/', { timeoutMs: 120000, maxMessageBytes: 4194304, maxQueueSize: 512 });\n" +
                "assert.strictEqual(conn.maxMessageBytes, 4194304);\n" +
                "await conn.send('x'.repeat(262144));\n" +
                "const largeSend = await conn.send('y'.repeat(2097152)).then(() => 'ok', e => String(e.code));\n" +
                "const plain = await ws.connect('ws://fake.local/plain'); assert.strictEqual(plain.maxMessageBytes, 65536);\n" +
                "assert.deepStrictEqual([ws.policy.hardMaxMessageBytes, ws.policy.limitsEnforcedBy], [undefined, 'host_provider']);\n" +
                "await conn.close(); await plain.close();\n" +
                "console.log('m20.network=' + JSON.stringify({ large, largeSend }));\n" +
                "})().catch(e => { console.error(e); process.exitCode = 1; });", "device", "network");
        String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
        int marker = stdout.indexOf("m20.network=");
        assertTrue("missing network marker: " + stdout, marker >= 0);
        JSONObject outcome = new JSONObject(stdout.substring(marker + "m20.network=".length()).trim());
        String log = String.join("\n", seen);
        assertTrue(log, log.contains("fetch PUT /echo"));
        assertTrue(log, log.contains("fetch GET /redirect/12 ") && log.contains("\"maxRedirects\":15,\"redirectCount\":12}"));
        assertTrue(log, log.contains("\"maxRedirects\":2,\"redirectCount\":2}") && !log.contains("\"maxRedirects\":2,\"redirectCount\":3}"));
        assertTrue(log, log.contains("\"timeoutMs\":120000") && log.contains("\"maxMessageBytes\":4194304") && log.contains("\"maxQueueSize\":512"));
        String plainConnect = "";
        for (String entry : seen) if (entry.startsWith("websocket connect ") && entry.replace("\\/", "/").contains("ws://fake.local/plain")) plainConnect = entry;
        assertTrue(log, plainConnect.contains("\"timeoutMs\":10000") && !plainConnect.contains("maxMessageBytes") && !plainConnect.contains("maxQueueSize"));
        assertTrue(log, log.contains("websocket sent 262144"));
        // A 2 MiB body in either direction is bounded by the Binder transaction, not by the runtime.
        assertTrue("2 MiB response should not succeed over Binder: " + outcome, !"ok".equals(outcome.getString("large")));
        assertTrue("2 MiB response should fail at the broker: " + largeResponseFailure.get(), !largeResponseFailure.get().isEmpty());
        assertTrue("2 MiB send should not succeed over Binder: " + outcome, !"ok".equals(outcome.getString("largeSend")));
        assertTrue("2 MiB send should not reach the broker: " + log, !log.contains("websocket sent 2097152"));
        Bundle status = new Bundle();
        status.putString("stream", "\nM20 network facade 2 MiB response=" + outcome.getString("large") + " (broker " + largeResponseFailure.get() +
                ") 2 MiB send=" + outcome.getString("largeSend") + "\n");
        InstrumentationRegistry.getInstrumentation().sendStatus(0, status);
    }

    @Test public void controlledFetchBodiesArriveThroughTheBinaryTransport() throws Exception {
        java.util.concurrent.ConcurrentLinkedQueue<String> seen = new java.util.concurrent.ConcurrentLinkedQueue<>();
        java.util.concurrent.atomic.AtomicInteger binaryFlagged = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger descriptorsSent = new java.util.concurrent.atomic.AtomicInteger();
        INodeJsHostCapabilityBroker broker = new INodeJsHostCapabilityBroker.Stub() {
            @Override public Bundle getBrokerInfo() {
                Bundle info = new ScreenStateTestBroker().getBrokerInfo();
                info.putStringArray(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_MODULES, new String[]{"device", "fetch"});
                return info;
            }
            @Override public Bundle getNativeDiagnostics() { return new Bundle(); }
            @Override public void destroy(Bundle reason) { }
            @Override public void dispatch(Bundle request, INodeJsHostCapabilityCallback callback) {
                try {
                    JSONObject call = new JSONObject(request.getString(NodeJsRuntimeContract.KEY_BRIDGE_REQUEST_JSON));
                    if (call.optBoolean("binary", false)) binaryFlagged.incrementAndGet();
                    JSONObject descriptor = call.getJSONArray("args").getJSONObject(0);
                    String path = new java.net.URL(descriptor.getString("url")).getPath();
                    seen.add(path);
                    JSONObject result = new JSONObject().put("url", descriptor.getString("url")).put("status", 200).put("statusText", "OK")
                            .put("headers", new JSONArray().put(new JSONArray().put("content-type").put("application/octet-stream")));
                    Bundle response = new Bundle();
                    java.io.File payload = null;
                    android.os.ParcelFileDescriptor fd = null;
                    try {
                        if (path.startsWith("/pfd/") || path.equals("/redirect-pfd")) {
                            // Host-side shape of M20.2 batch 13: body in a cache file, JSON carries only its size.
                            int count = path.equals("/redirect-pfd") ? 4096 : Integer.parseInt(path.substring("/pfd/".length()));
                            payload = java.io.File.createTempFile("fetch-body-", ".bin", context.getCacheDir());
                            try (java.io.FileOutputStream stream = new java.io.FileOutputStream(payload)) {
                                byte[] chunk = new byte[8192];
                                for (int offset = 0; offset < count; offset += chunk.length) {
                                    int length = Math.min(chunk.length, count - offset);
                                    for (int index = 0; index < length; index++) chunk[index] = (byte) ((offset + index) % 251);
                                    stream.write(chunk, 0, length);
                                }
                            }
                            if (path.equals("/redirect-pfd")) {
                                result.put("status", 302).put("headers", new JSONArray().put(new JSONArray().put("location").put("/pfd/1024")));
                            }
                            result.put("bodyBytes", count).put("bodyTransport", "pfd");
                            fd = android.os.ParcelFileDescriptor.open(payload, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
                            response.putParcelable(NodeJsRuntimeContract.KEY_BRIDGE_BINARY_PFD, fd);
                            response.putLong(NodeJsRuntimeContract.KEY_BRIDGE_BINARY_BYTE_COUNT, count);
                            descriptorsSent.incrementAndGet();
                        } else if (path.startsWith("/inline/")) {
                            byte[] bytes = new byte[Integer.parseInt(path.substring("/inline/".length()))];
                            for (int index = 0; index < bytes.length; index++) bytes[index] = (byte) (index % 251);
                            result.put("bodyBase64", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)).put("bodyBytes", bytes.length);
                        } else {
                            result.put("bodyBase64", "").put("bodyBytes", 0);
                        }
                        response.putString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON,
                                new JSONObject().put("id", call.getString("id")).put("ok", true).put("result", result).toString());
                        callback.onResponse(response);
                    } finally {
                        if (fd != null) fd.close();
                        if (payload != null) payload.delete();
                    }
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
            }
        };
        for (String transport : new String[]{"jni", "file"}) {
            seen.clear();
            binaryFlagged.set(0);
            descriptorsSent.set(0);
            Bundle result = run(broker, transport,
                    "(async () => { const cfetch = require('fetch'); const base = 'http://fake.local';\n" +
                    "const big = Buffer.from(await (await cfetch(base + '/pfd/4194304')).arrayBuffer());\n" +
                    "let pattern = big.length === 4194304; for (let i = 0; pattern && i < big.length; i += 4099) pattern = big[i] === i % 251;\n" +
                    "const inline = Buffer.from(await (await cfetch(base + '/inline/65536')).arrayBuffer());\n" +
                    "const empty = await (await cfetch(base + '/empty')).arrayBuffer();\n" +
                    "const redirected = await cfetch(base + '/redirect-pfd'); const redirectedBody = await redirected.arrayBuffer();\n" +
                    "const parallel = await Promise.all(Array.from({ length: 40 }, (_, i) => cfetch(base + '/pfd/' + (1024 + i)).then(r => r.arrayBuffer()).then(b => b.byteLength)));\n" +
                    "const text = await (await cfetch(base + '/pfd/13')).text();\n" +
                    "const viaAxios = await require('axios').get(base + '/pfd/2048', { responseType: 'arraybuffer' });\n" +
                    "console.log('m20.pfd=' + JSON.stringify({ pattern, inline: inline.length === 65536 && inline[65535] === 65535 % 251, empty: empty.byteLength,\n" +
                    "  redirect: [redirected.status, redirectedBody.byteLength], parallel: parallel.every((n, i) => n === 1024 + i), text: text.length, axios: viaAxios.data.byteLength }));\n" +
                    "})().catch(e => { console.error(e); process.exitCode = 1; });", "device", "network");
            String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
            int marker = stdout.indexOf("m20.pfd=");
            assertTrue("missing pfd marker (" + transport + "): " + stdout, marker >= 0);
            JSONObject outcome = new JSONObject(stdout.substring(marker + "m20.pfd=".length()).trim());
            assertTrue(transport + ": " + outcome, outcome.getBoolean("pattern") && outcome.getBoolean("inline") && outcome.getInt("empty") == 0);
            assertEquals(transport + ": " + outcome, "[200,1024]", outcome.getJSONArray("redirect").toString());
            assertTrue(transport + ": " + outcome, outcome.getBoolean("parallel") && outcome.getInt("text") == 13 && outcome.getInt("axios") == 2048);
            // big + inline + empty + redirect + its follow-up + 40 parallel + text + axios
            assertEquals("fetch requests over " + transport + ": " + seen, 47, seen.size());
            assertEquals("every fetch request asks for the binary transport (" + transport + ")", 47, binaryFlagged.get());
            assertEquals("descriptor replies over " + transport, 45, descriptorsSent.get());
        }
    }

    private static String value(String[] payload, String key) {
        for (String entry : payload) {
            if (entry.startsWith(key + "=")) return entry.substring(key.length() + 1);
        }
        throw new AssertionError("missing native payload key: " + key);
    }
}
