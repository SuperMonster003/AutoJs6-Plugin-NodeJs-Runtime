package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Base64;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Roadmap M10.2: explicit debug-only inspector, loopback policy and live CDP breakpoint. */
@RunWith(AndroidJUnit4.class)
public final class InspectorDebugSmokeTest {

    private static final long BIND_TIMEOUT_MS = 30_000L;
    private static final long SCRIPT_TIMEOUT_MS = 25_000L;

    @Test
    public void explicitDebugRequestProvidesLoopbackCdpAndDefaultStaysDenied() throws Exception {
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
        File workingDirectory = new File(context.getCacheDir(), "inspector-smoke-" + UUID.randomUUID());
        assertTrue("unable to create inspector working directory", workingDirectory.mkdirs());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Bundle> execution = null;
        try {
            assertTrue("bind timed out", connected.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS));
            INodeJsRuntimePlugin runtime = INodeJsRuntimePlugin.Stub.asInterface(binder.get());
            assertNotNull("runtime proxy unavailable", runtime);

            Bundle denied = runtime.runScript(
                    request(
                            workingDirectory,
                            "let code = '';\n" +
                                    "try { require('inspector'); } catch (error) { code = error.autojs6Code || error.code || ''; }\n" +
                                    "if (code !== 'ERR_AUTOJS6_BUILTIN_DISABLED') throw new Error('unexpected inspector code: ' + code);\n" +
                                    "console.log('m10.inspector.default-denied=' + code);\n",
                            false
                    ),
                    emptyCallback()
            );
            assertSucceededWith(denied, "m10.inspector.default-denied=ERR_AUTOJS6_BUILTIN_DISABLED");

            File urlFile = new File(workingDirectory, "inspector-url.txt");
            File readyFile = new File(workingDirectory, "debugger-ready.txt");
            execution = executor.submit(() -> runtime.runScript(
                    request(workingDirectory, inspectorBreakpointSource(workingDirectory), true),
                    emptyCallback()
            ));
            waitForFile(urlFile, execution, 10_000L);
            String inspectorUrl = readUtf8(urlFile).trim();
            assertTrue(
                    "Inspector must bind IPv4 loopback only: " + inspectorUrl,
                    inspectorUrl.startsWith("ws://127.0.0.1:")
            );

            try (CdpWebSocket cdp = CdpWebSocket.connect(inspectorUrl)) {
                cdp.sendJson("{\"id\":1,\"method\":\"Runtime.enable\"}");
                cdp.sendJson("{\"id\":2,\"method\":\"Debugger.enable\"}");
                cdp.awaitResponse(2, 8_000L);
                writeUtf8(readyFile, "ready");
                JSONObject paused = cdp.awaitMethod("Debugger.paused", 10_000L);
                assertPausedAtSource(paused, "inspector-breakpoint.cjs");
                cdp.sendJson("{\"id\":3,\"method\":\"Debugger.resume\"}");
                cdp.awaitResponse(3, 5_000L);
            }

            Bundle result = execution.get(15L, TimeUnit.SECONDS);
            assertSucceededWith(
                    result,
                    "m10.inspector.non-loopback-denied=true",
                    "m10.inspector.wait-denied=true",
                    "m10.inspector.debug-only=true",
                    "m10.inspector.breakpoint-resumed=true"
            );

            Bundle profiling = runtime.runScript(
                    request(workingDirectory, inspectorSessionSource(), true),
                    emptyCallback()
            );
            assertSucceededWith(
                    profiling,
                    "m10.inspector.module-shape=true",
                    "m10.inspector.cpu-profile=true",
                    "m10.inspector.heap-usage=true"
            );
        } finally {
            if (execution != null) execution.cancel(true);
            executor.shutdownNow();
            context.unbindService(connection);
            deleteTree(workingDirectory);
        }
    }

    private static Bundle request(File workingDirectory, String source, boolean inspectorEnabled) {
        Bundle request = new Bundle();
        request.putInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, NodeJsRuntimeContract.CONTRACT_VERSION);
        request.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, "inspector-" + UUID.randomUUID());
        request.putString(NodeJsRuntimeContract.KEY_SOURCE, source);
        request.putString(
                NodeJsRuntimeContract.KEY_SOURCE_NAME,
                new File(workingDirectory, "inspector-breakpoint.cjs").getAbsolutePath()
        );
        request.putString(NodeJsRuntimeContract.KEY_WORKING_DIRECTORY, workingDirectory.getAbsolutePath());
        request.putString(NodeJsRuntimeContract.KEY_SANDBOX_ROOT, workingDirectory.getAbsolutePath());
        request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, SCRIPT_TIMEOUT_MS);
        request.putBoolean(NodeJsRuntimeContract.KEY_INSPECTOR_ENABLED, inspectorEnabled);
        return request;
    }

    private static String inspectorBreakpointSource(File workingDirectory) {
        String urlPath = JSONObject.quote(new File(workingDirectory, "inspector-url.txt").getAbsolutePath());
        String readyPath = JSONObject.quote(new File(workingDirectory, "debugger-ready.txt").getAbsolutePath());
        return "\"nodejs\";\n" +
                "const fs = require('fs');\n" +
                "const inspector = require('inspector');\n" +
                "const profile = require('autojs6:profile');\n" +
                "let nonLoopbackDenied = false;\n" +
                "let waitDenied = false;\n" +
                "try { inspector.open(0, '0.0.0.0', false); } catch (error) { nonLoopbackDenied = error && error.code === 'ERR_INVALID_ARG_VALUE'; }\n" +
                "try { inspector.open(0, '127.0.0.1', true); } catch (error) { waitDenied = error && error.code === 'ERR_INVALID_ARG_VALUE'; }\n" +
                "console.log('m10.inspector.non-loopback-denied=' + nonLoopbackDenied);\n" +
                "console.log('m10.inspector.wait-denied=' + waitDenied);\n" +
                "console.log('m10.inspector.debug-only=' + (profile.featureFlags.inspector.enabled === true && profile.featureFlags.inspector.debugOnly === true && profile.featureFlags.inspector.releaseForbidden === true));\n" +
                "inspector.open(0, 'localhost', false);\n" +
                "const url = inspector.url();\n" +
                "if (!url || url.indexOf('ws://127.0.0.1:') !== 0) throw new Error('bad inspector URL: ' + url);\n" +
                "fs.writeFileSync(" + urlPath + ", url, 'utf8');\n" +
                "const readyFile = " + readyPath + ";\n" +
                "const deadline = Date.now() + 10000;\n" +
                "(function awaitDebugger() {\n" +
                "  if (fs.existsSync(readyFile)) {\n" +
                "    (function breakpointTarget() { debugger; })();\n" +
                "    console.log('m10.inspector.breakpoint-resumed=true');\n" +
                "    setTimeout(function() { inspector.close(); }, 50);\n" +
                "    return;\n" +
                "  }\n" +
                "  if (Date.now() >= deadline) throw new Error('Debugger client did not become ready');\n" +
                "  setTimeout(awaitDebugger, 25);\n" +
                "})();\n";
    }

    private static String inspectorSessionSource() {
        return "\"nodejs\";\n" +
                "const inspector = require('node:inspector');\n" +
                "const Module = require('module');\n" +
                "console.log('m10.inspector.module-shape=' + (Module.isBuiltin('inspector') && typeof inspector.Session === 'function'));\n" +
                "const session = new inspector.Session();\n" +
                "session.connect();\n" +
                "const post = (method, params) => new Promise((resolve, reject) => session.post(method, params || {}, (error, result) => error ? reject(error) : resolve(result || {})));\n" +
                "(async function() {\n" +
                "  await post('Profiler.enable');\n" +
                "  await post('Profiler.start');\n" +
                "  let total = 0; for (let index = 0; index < 100000; ++index) total += index;\n" +
                "  const stopped = await post('Profiler.stop');\n" +
                "  const heap = await post('Runtime.getHeapUsage');\n" +
                "  session.disconnect();\n" +
                "  console.log('m10.inspector.cpu-profile=' + !!(stopped.profile && stopped.profile.nodes && stopped.profile.nodes.length));\n" +
                "  console.log('m10.inspector.heap-usage=' + !!(heap.usedSize > 0 && heap.totalSize > 0));\n" +
                "})().catch(function(error) { setImmediate(function() { throw error; }); });\n";
    }

    private static INodeJsRuntimeCallback emptyCallback() {
        return new INodeJsRuntimeCallback.Stub() {
            @Override
            public void onEvent(Bundle event) {
            }
        };
    }

    private static void waitForFile(File file, Future<Bundle> execution, long timeoutMs) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (file.isFile() && file.length() > 0L) return;
            if (execution.isDone()) {
                Bundle result = execution.get();
                throw new AssertionError(describeResult("inspector endpoint startup", result));
            }
            Thread.sleep(25L);
        }
        throw new AssertionError("timed out waiting for " + file.getAbsolutePath());
    }

    private static void assertPausedAtSource(JSONObject paused, String sourceName) throws Exception {
        JSONArray frames = paused.getJSONObject("params").getJSONArray("callFrames");
        for (int index = 0; index < frames.length(); ++index) {
            JSONObject frame = frames.getJSONObject(index);
            if (frame.optString("url").contains(sourceName) ||
                    "breakpointTarget".equals(frame.optString("functionName"))) return;
        }
        throw new AssertionError("paused event did not contain " + sourceName + " or breakpointTarget: " + paused);
    }

    private static void assertSucceededWith(Bundle result, String... expectedLines) {
        assertNotNull("runScript returned null", result);
        assertTrue(describeResult("script", result), result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
        String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
        for (String expectedLine : expectedLines) {
            assertTrue("missing '" + expectedLine + "'; " + describeResult("script", result), stdout.contains(expectedLine));
        }
    }

    private static String describeResult(String label, Bundle result) {
        return label + " succeeded=" + result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED) +
                " error=" + result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "") +
                " stdout=" + result.getString(NodeJsRuntimeContract.KEY_STDOUT, "") +
                " stderr=" + result.getString(NodeJsRuntimeContract.KEY_STDERR, "");
    }

    private static String readUtf8(File file) throws IOException {
        try (InputStream input = new java.io.FileInputStream(file)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void writeUtf8(File file, String text) throws IOException {
        try (OutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void deleteTree(File target) {
        if (target == null || !target.exists()) return;
        File[] children = target.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        // Test-only app-private directory; best-effort cleanup.
        target.delete();
    }

    private static final class CdpWebSocket implements Closeable {
        private static final int MAX_FRAME_BYTES = 4 * 1024 * 1024;
        private final Socket socket;
        private final InputStream input;
        private final OutputStream output;
        private final SecureRandom random = new SecureRandom();

        private CdpWebSocket(Socket socket) throws IOException {
            this.socket = socket;
            this.input = socket.getInputStream();
            this.output = socket.getOutputStream();
        }

        static CdpWebSocket connect(String url) throws Exception {
            URI uri = URI.create(url);
            if (!"ws".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost())) {
                throw new IllegalArgumentException("expected loopback ws URL: " + url);
            }
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(uri.getHost(), uri.getPort()), 5_000);
            socket.setSoTimeout(10_000);
            CdpWebSocket webSocket = new CdpWebSocket(socket);
            webSocket.handshake(uri);
            return webSocket;
        }

        private void handshake(URI uri) throws Exception {
            byte[] nonce = new byte[16];
            random.nextBytes(nonce);
            String key = Base64.encodeToString(nonce, Base64.NO_WRAP);
            String request = "GET " + uri.getRawPath() + " HTTP/1.1\r\n" +
                    "Host: 127.0.0.1:" + uri.getPort() + "\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: " + key + "\r\n" +
                    "Sec-WebSocket-Version: 13\r\n\r\n";
            output.write(request.getBytes(StandardCharsets.US_ASCII));
            output.flush();
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            int matched = 0;
            while (header.size() < 16 * 1024) {
                int value = input.read();
                if (value < 0) throw new EOFException("EOF during inspector WebSocket handshake");
                header.write(value);
                int expected = "\r\n\r\n".charAt(matched);
                matched = value == expected ? matched + 1 : (value == '\r' ? 1 : 0);
                if (matched == 4) break;
            }
            String response = header.toString(StandardCharsets.US_ASCII.name());
            if (!response.startsWith("HTTP/1.1 101")) {
                throw new IOException("inspector WebSocket upgrade failed: " + response);
            }
        }

        synchronized void sendJson(String json) throws IOException {
            sendFrame(0x1, json.getBytes(StandardCharsets.UTF_8));
        }

        JSONObject awaitResponse(int id, long timeoutMs) throws Exception {
            return awaitMessage(message -> message.optInt("id", -1) == id, timeoutMs);
        }

        JSONObject awaitMethod(String method, long timeoutMs) throws Exception {
            return awaitMessage(message -> method.equals(message.optString("method")), timeoutMs);
        }

        private JSONObject awaitMessage(MessagePredicate predicate, long timeoutMs) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            while (System.nanoTime() < deadline) {
                Frame frame = readFrame();
                if (frame.opcode == 0x8) throw new EOFException("inspector WebSocket closed");
                if (frame.opcode == 0x9) {
                    sendFrame(0xA, frame.payload);
                    continue;
                }
                if (frame.opcode != 0x1) continue;
                JSONObject message = new JSONObject(new String(frame.payload, StandardCharsets.UTF_8));
                if (predicate.matches(message)) return message;
            }
            throw new IOException("timed out waiting for inspector CDP message");
        }

        private Frame readFrame() throws IOException {
            int first = readByte();
            int second = readByte();
            int opcode = first & 0x0f;
            boolean masked = (second & 0x80) != 0;
            long length = second & 0x7f;
            if (length == 126) {
                length = ((long) readByte() << 8) | readByte();
            } else if (length == 127) {
                length = 0L;
                for (int index = 0; index < 8; ++index) length = (length << 8) | readByte();
            }
            if (length < 0L || length > MAX_FRAME_BYTES) {
                throw new IOException("inspector frame is too large: " + length);
            }
            byte[] mask = masked ? readFully(4) : null;
            byte[] payload = readFully((int) length);
            if (mask != null) {
                for (int index = 0; index < payload.length; ++index) payload[index] ^= mask[index & 3];
            }
            return new Frame(opcode, payload);
        }

        private synchronized void sendFrame(int opcode, byte[] payload) throws IOException {
            output.write(0x80 | opcode);
            if (payload.length <= 125) {
                output.write(0x80 | payload.length);
            } else if (payload.length <= 0xffff) {
                output.write(0x80 | 126);
                output.write((payload.length >>> 8) & 0xff);
                output.write(payload.length & 0xff);
            } else {
                output.write(0x80 | 127);
                long payloadLength = payload.length;
                for (int shift = 56; shift >= 0; shift -= 8) output.write((int) ((payloadLength >>> shift) & 0xff));
            }
            byte[] mask = new byte[4];
            random.nextBytes(mask);
            output.write(mask);
            for (int index = 0; index < payload.length; ++index) output.write(payload[index] ^ mask[index & 3]);
            output.flush();
        }

        private int readByte() throws IOException {
            int value = input.read();
            if (value < 0) throw new EOFException("inspector WebSocket EOF");
            return value;
        }

        private byte[] readFully(int length) throws IOException {
            byte[] bytes = new byte[length];
            int offset = 0;
            while (offset < length) {
                int count = input.read(bytes, offset, length - offset);
                if (count < 0) throw new EOFException("inspector WebSocket EOF");
                offset += count;
            }
            return bytes;
        }

        @Override
        public void close() throws IOException {
            try {
                sendFrame(0x8, new byte[]{0x03, (byte) 0xE8});
            } catch (IOException ignored) {
            }
            socket.close();
        }

        private interface MessagePredicate {
            boolean matches(JSONObject message);
        }

        private static final class Frame {
            final int opcode;
            final byte[] payload;

            Frame(int opcode, byte[] payload) {
                this.opcode = opcode;
                this.payload = payload;
            }
        }
    }
}
