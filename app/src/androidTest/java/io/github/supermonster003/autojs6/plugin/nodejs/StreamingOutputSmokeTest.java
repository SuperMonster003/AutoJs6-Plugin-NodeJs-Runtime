package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Roadmap M2.1: stdout must reach the caller while the script is still
 * running. The script prints one marker, sleeps, then prints another; the
 * first EVENT_STDOUT must arrive well before EVENT_FINISHED, which the old
 * end-of-run replay could never do (all events landed within milliseconds of
 * the terminal bundle).
 */
@RunWith(AndroidJUnit4.class)
public final class StreamingOutputSmokeTest {

    private static final long BIND_TIMEOUT_MS = 30_000L;
    private static final long SCRIPT_SLEEP_MS = 2_000L;
    /** The first chunk must beat FINISHED by at least this much. */
    private static final long STREAMING_LEAD_MS = 1_000L;

    private static final class RecordedEvent {
        final String type;
        final String text;
        final long elapsedRealtime;

        RecordedEvent(String type, String text, long elapsedRealtime) {
            this.type = type;
            this.text = text;
            this.elapsedRealtime = elapsedRealtime;
        }
    }

    @Test
    public void stdoutStreamsWhileScriptStillRunning() throws Exception {
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

            List<RecordedEvent> events = new ArrayList<>();
            INodeJsRuntimeCallback.Stub callback = new INodeJsRuntimeCallback.Stub() {
                @Override
                public void onEvent(Bundle event) {
                    synchronized (events) {
                        events.add(new RecordedEvent(
                                event.getString(NodeJsRuntimeContract.KEY_EVENT_TYPE, ""),
                                event.getString(NodeJsRuntimeContract.KEY_EVENT_TEXT, ""),
                                SystemClock.elapsedRealtime()
                        ));
                    }
                }
            };

            Bundle request = new Bundle();
            request.putString(
                    NodeJsRuntimeContract.KEY_SOURCE,
                    "console.log('m2.stream.first');\n" +
                            "setTimeout(() => { console.log('m2.stream.second'); }, " + SCRIPT_SLEEP_MS + ");\n"
            );
            Bundle result = runtime.runScript(request, callback);

            assertNotNull("runScript returned null", result);
            assertTrue(
                    "execution failed: " + result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, ""),
                    result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED)
            );
            // The terminal aggregate stays intact for callers that only read
            // the result bundle.
            String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
            assertTrue("terminal stdout missing first marker: " + stdout, stdout.contains("m2.stream.first"));
            assertTrue("terminal stdout missing second marker: " + stdout, stdout.contains("m2.stream.second"));

            List<RecordedEvent> snapshot;
            synchronized (events) {
                snapshot = new ArrayList<>(events);
            }
            RecordedEvent firstStdout = null;
            RecordedEvent finished = null;
            StringBuilder streamedText = new StringBuilder();
            for (RecordedEvent event : snapshot) {
                if (NodeJsRuntimeContract.EVENT_STDOUT.equals(event.type)) {
                    streamedText.append(event.text);
                    if (firstStdout == null && event.text.contains("m2.stream.first")) {
                        firstStdout = event;
                    }
                }
                if (NodeJsRuntimeContract.EVENT_FINISHED.equals(event.type)) {
                    finished = event;
                }
            }
            assertNotNull("no EVENT_STDOUT carrying the first marker; events=" + describe(snapshot), firstStdout);
            assertNotNull("no EVENT_FINISHED received; events=" + describe(snapshot), finished);
            assertTrue(
                    "streamed text incomplete: " + streamedText,
                    streamedText.toString().contains("m2.stream.first")
                            && streamedText.toString().contains("m2.stream.second")
            );
            long lead = finished.elapsedRealtime - firstStdout.elapsedRealtime;
            assertTrue(
                    "first stdout event arrived only " + lead + "ms before FINISHED; "
                            + "expected >= " + STREAMING_LEAD_MS + "ms (script sleeps " + SCRIPT_SLEEP_MS
                            + "ms after the first print) — output is not streaming",
                    lead >= STREAMING_LEAD_MS
            );
        } finally {
            context.unbindService(connection);
        }
    }

    private static String describe(List<RecordedEvent> events) {
        StringBuilder text = new StringBuilder();
        for (RecordedEvent event : events) {
            text.append('[').append(event.type).append(':').append(event.text.trim()).append("] ");
        }
        return text.toString().trim();
    }
}
