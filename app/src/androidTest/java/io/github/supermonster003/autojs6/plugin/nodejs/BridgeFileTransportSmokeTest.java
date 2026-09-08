package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityBroker;
import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityCallback;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

@RunWith(AndroidJUnit4.class)
public final class BridgeFileTransportSmokeTest {

    @Test
    public void completedRequestsKeepOnlyBoundedDiagnostics() throws Exception {
        File cache = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
        INodeJsHostCapabilityBroker broker = new INodeJsHostCapabilityBroker.Stub() {
            @Override public Bundle getBrokerInfo() { return new Bundle(); }
            @Override public Bundle getNativeDiagnostics() { return new Bundle(); }
            @Override public void destroy(Bundle reason) { }

            @Override
            public void dispatch(Bundle request, INodeJsHostCapabilityCallback callback) throws RemoteException {
                try {
                    JSONObject call = new JSONObject(request.getString(NodeJsRuntimeContract.KEY_BRIDGE_REQUEST_JSON));
                    Bundle response = new Bundle();
                    response.putString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON,
                            new JSONObject().put("id", call.getString("id")).put("ok", true)
                                    .put("result", true).toString());
                    callback.onResponse(response);
                } catch (org.json.JSONException error) {
                    throw new AssertionError(error);
                }
            }
        };
        PluginNodeBridgeFileTransportSession session = new PluginNodeBridgeFileTransportSession(
                cache, "bounded-diagnostics-" + System.nanoTime(), broker, 32);
        JSONObject config = new JSONObject(session.configJson());
        File requests = new File(config.getString("requestDir"));
        File responses = new File(config.getString("responseDir"));
        session.start();
        try {
            for (int index = 0; index < 96; index++) {
                String id = String.format(java.util.Locale.ROOT, "request-%05d", index);
                File request = new File(requests, id + ".json");
                File temporary = new File(requests, id + ".tmp");
                try (FileOutputStream output = new FileOutputStream(temporary)) {
                    output.write(new JSONObject().put("id", id).put("module", "device")
                            .put("method", "isScreenOn").toString().getBytes(StandardCharsets.UTF_8));
                }
                assertTrue(temporary.renameTo(request));
                File response = new File(responses, id + ".json");
                long deadline = SystemClock.elapsedRealtime() + 5_000;
                while ((!response.isFile() || request.exists()) && SystemClock.elapsedRealtime() < deadline) {
                    SystemClock.sleep(2);
                }
                assertTrue("response missing for " + id, response.isFile());
                assertTrue("completed request retained on disk", !request.exists());
                assertTrue(response.delete());
            }
            String[] payload = session.nativePayload();
            assertEquals("96", value(payload, "embedded_script.bridge_live_dispatch_count"));
            assertEquals("0", value(payload, "embedded_script.bridge_live_retained_request_count"));
            assertEquals("32", value(payload, "embedded_script.bridge_live_retained_response_count"));
            String recent = value(payload, "embedded_script.bridge_live_responses_json");
            assertTrue("response history grows with completed calls: " + recent,
                    new JSONArray(recent).length() <= 32);
            assertTrue(recent.getBytes(StandardCharsets.UTF_8).length < 64 * 1024);
        } finally {
            session.stop();
        }
    }

    private static String value(String[] payload, String key) {
        for (String entry : payload) {
            if (entry.startsWith(key + "=")) return entry.substring(key.length() + 1);
        }
        throw new AssertionError("missing native payload key: " + key);
    }
}
