package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.*;
import android.content.*;
import android.os.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.autojs.plugin.nodejs.api.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class HostInputSmokeTest {
    private Context context;
    private ServiceConnection connection;
    private INodeJsRuntimePlugin runtime;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final BlockingQueue<Boolean> input = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> output = new LinkedBlockingQueue<>();
    private final String executionId = "stdin-" + UUID.randomUUID();

    @Before public void connect() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<IBinder> binder = new AtomicReference<>();
        connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) { binder.set(service); ready.countDown(); }
            @Override public void onServiceDisconnected(ComponentName name) {}
        };
        assertTrue(context.bindService(new Intent().setComponent(new ComponentName(context.getPackageName(),
                context.getPackageName() + ".NodeJsRuntimePluginService")), connection, Context.BIND_AUTO_CREATE));
        assertTrue(ready.await(30, TimeUnit.SECONDS));
        runtime = INodeJsRuntimePlugin.Stub.asInterface(binder.get());
    }

    @After public void disconnect() throws Exception {
        if (runtime != null) runtime.cancelScript(executionId);
        executor.shutdownNow();
        if (connection != null) context.unbindService(connection);
    }

    private Future<Bundle> start(String source) {
        Bundle request = new Bundle();
        request.putInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, 2); // Published v2 caller.
        request.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, executionId);
        request.putString(NodeJsRuntimeContract.KEY_SOURCE, source);
        request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 20_000L);
        return executor.submit(() -> runtime.runScript(request, new INodeJsRuntimeCallback.Stub() {
            @Override public void onEvent(Bundle event) {
                String type = event.getString(NodeJsRuntimeContract.KEY_EVENT_TYPE);
                String text = event.getString(NodeJsRuntimeContract.KEY_EVENT_TEXT, "");
                if (NodeJsRuntimeContract.EVENT_STDIN_STATE.equals(type) && "true".equals(text)) input.add(true);
                if (NodeJsRuntimeContract.EVENT_STDOUT.equals(type)) output.add(text);
            }
        }));
    }

    private Bundle message(String kind, String data, boolean eof) {
        Bundle message = new Bundle();
        message.putString(NodeJsRuntimeContract.KEY_MESSAGE_KIND, kind);
        message.putString(NodeJsRuntimeContract.KEY_MESSAGE_DATA, data);
        message.putBoolean(NodeJsRuntimeContract.KEY_MESSAGE_EOF, eof);
        return message;
    }

    private void ready() throws Exception { assertNotNull("stdin never requested input", input.poll(15, TimeUnit.SECONDS)); }

    private String success(Future<Bundle> future) throws Exception {
        Bundle result = future.get(25, TimeUnit.SECONDS);
        String output = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
        assertTrue(result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "") + " / " +
                result.getString(NodeJsRuntimeContract.KEY_STDERR, "") + " / " + output,
                result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
        return output;
    }

    @Test public void readlineAcceptsTwoUnicodeLinesAndKeepsV2Handshake() throws Exception {
        Bundle info = runtime.getRuntimeInfo();
        assertEquals(2, info.getInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION));
        assertEquals(3, info.getInt(NodeJsRuntimeContract.KEY_MAX_CONTRACT_VERSION));
        Future<Bundle> run = start("(async () => { const assert = require('node:assert/strict'); " +
                "assert.ok(process.stdin instanceof require('node:stream').Readable);" +
                "const rl = require('node:readline/promises').createInterface({input:process.stdin,output:process.stdout});" +
                "const a = await rl.question('First? '); console.log('first=' + a);" +
                "const b = await rl.question('Second? '); console.log('second=' + b); rl.close();" +
                "})().catch(e => { console.error(e.stack); process.exitCode = 1; });");
        ready();
        assertFalse(run.isDone());
        assertFalse(runtime.postMessage("unknown-id", message("stdin", "wrong\n", false)));
        assertFalse(runtime.postMessage(executionId, message("stdin", "x".repeat(65537), false)));
        assertTrue(runtime.postMessage(executionId, message("stdin", "你好 🌏\n", false)));
        ready();
        assertTrue(runtime.postMessage(executionId, message("stdin", "第二行\n", false)));
        String text = success(run);
        assertTrue(text, text.contains("first=你好 🌏"));
        assertTrue(text, text.contains("second=第二行"));
        assertFalse(runtime.postMessage(executionId, message("stdin", "late\n", false)));
    }

    @Test public void hostMessagesUseOneShotEventEmitterAndJsonValues() throws Exception {
        Future<Bundle> run = start("(async () => { const host = require('autojs6:host');" +
                "const imported = await import('autojs6:host'); if (imported.default !== host) throw Error('ESM identity');" +
                "host.once('message', data => { if (data.answer !== 42 || data.text !== '你好 🌏') throw Error('payload');" +
                "console.log('host.message=' + data.answer); }); console.log('host.ready'); })();");
        StringBuilder text = new StringBuilder();
        while (!text.toString().contains("host.ready")) {
            String chunk = output.poll(15, TimeUnit.SECONDS);
            if (chunk == null && run.isDone()) success(run);
            assertNotNull("host listener never became ready: " + text, chunk);
            text.append(chunk);
        }
        assertFalse(run.isDone());
        assertFalse(runtime.postMessage(executionId, message("invalid", "{}", false)));
        assertTrue(runtime.postMessage(executionId, message("message", "{\"answer\":42,\"text\":\"你好 🌏\"}", false)));
        assertTrue(success(run).contains("host.message=42"));
        assertFalse(runtime.postMessage(executionId, message("message", "null", false)));
    }

    @Test public void stdinAsyncIteratorConsumesEofWithoutTrailingNewline() throws Exception {
        Future<Bundle> run = start("(async () => { process.stdin.setEncoding('utf8'); let text = '';" +
                "for await (const chunk of process.stdin) text += chunk; console.log('eof=' + text + ':' + process.stdin.readableEnded); })();");
        ready();
        assertTrue(runtime.postMessage(executionId, message("stdin", "终点 🌏", true)));
        assertTrue(success(run).contains("eof=终点 🌏:true"));
    }

    @Test public void publishedInputSampleRunsAndUnreferencedMessageListenerCanExit() throws Exception {
        String source;
        try (java.io.InputStream stream = InstrumentationRegistry.getInstrumentation().getContext()
                .getAssets().open("host-input/main.cjs")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) bytes.write(buffer, 0, read);
            source = bytes.toString("UTF-8");
        }
        Future<Bundle> run = start(source + "\nconst host = require('autojs6:host'); host.on('message', () => {}).unref();");
        ready();
        assertTrue(runtime.postMessage(executionId, message("stdin", "Ada\n", false)));
        ready();
        assertTrue(runtime.postMessage(executionId, message("stdin", "Hello from the console\n", false)));
        assertTrue(success(run).contains("sample.host-input=PASS"));
    }

    @Test public void waitingInputCanBeCancelledAndDoesNotKeepNextScriptAlive() throws Exception {
        Future<Bundle> run = start("require('node:readline').createInterface({input:process.stdin}).question('wait', () => {});");
        ready();
        assertTrue(runtime.cancelScript(executionId));
        assertFalse(run.get(15, TimeUnit.SECONDS).getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
        assertFalse(runtime.postMessage(executionId, message("stdin", "late\n", false)));
        Bundle request = new Bundle();
        request.putInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, 2);
        request.putString(NodeJsRuntimeContract.KEY_SOURCE, "console.log('v2.after.cancel');");
        Bundle result = runtime.runScript(request, null);
        assertTrue(result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE), result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
        assertTrue(result.getString(NodeJsRuntimeContract.KEY_STDOUT, "").contains("v2.after.cancel"));
    }
}
