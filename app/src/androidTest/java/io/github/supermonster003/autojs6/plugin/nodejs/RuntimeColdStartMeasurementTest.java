package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Roadmap M10.3/M17.3 measurement harness.
 *
 * <p>Run this class after force-stopping the package. The bind wall time then
 * includes process creation and the first readiness query waiting for Node/V8
 * prewarm. Every script execution still creates a fresh isolate/environment,
 * so the first run and twenty process-warm runs expose the part a startup
 * snapshot could plausibly improve.</p>
 */
@RunWith(AndroidJUnit4.class)
public final class RuntimeColdStartMeasurementTest {

    private static final long BIND_TIMEOUT_MS = 30_000L;
    private static final int EXECUTION_COUNT = 21;
    private static final long SNAPSHOT_REVIEW_THRESHOLD_MS = 1_000L;

    @Test
    public void recordsColdBindAndFreshIsolateDistribution() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext()
                .getApplicationContext();

        long bindStartedAt = SystemClock.elapsedRealtime();
        try (BoundRuntime bound = BoundRuntime.bind(context)) {
            Bundle runtimeInfo = bound.runtime.getRuntimeInfo();
            // The dispatcher binds workers asynchronously; include the readiness query in cold startup.
            long bindAndPrewarmWallMs = SystemClock.elapsedRealtime() - bindStartedAt;
            assertNotNull("getRuntimeInfo returned null", runtimeInfo);
            assertTrue(
                    runtimeInfo.getString("runtimeReadinessDetail", "runtime not ready"),
                    runtimeInfo.getBoolean("runtimeReady")
            );

            int runtimePid = runtimeInfo.getInt(NodeJsRuntimeContract.KEY_PID, -1);
            assertTrue("invalid runtime pid: " + runtimePid, runtimePid > 0);
            Map<String, String> readiness = nativeValues(runtimeInfo);

            JSONArray runsJson = new JSONArray();
            List<Long> warmWallMs = new ArrayList<>();
            List<Long> warmNativeTotalMs = new ArrayList<>();
            Map<String, List<Long>> warmPhases = new LinkedHashMap<>();
            warmPhases.put("isolateCreateMs", new ArrayList<>());
            warmPhases.put("environmentCreateMs", new ArrayList<>());
            warmPhases.put("bootstrapMs", new ArrayList<>());
            warmPhases.put("scriptExecutionMs", new ArrayList<>());
            warmPhases.put("teardownMs", new ArrayList<>());

            Measurement first = null;
            for (int index = 0; index < EXECUTION_COUNT; index++) {
                Measurement measurement = execute(bound.runtime, index, runtimePid);
                if (index == 0) {
                    first = measurement;
                } else {
                    warmWallMs.add(measurement.wallMs);
                    warmNativeTotalMs.add(measurement.nativeTotalMs);
                    warmPhases.get("isolateCreateMs").add(measurement.isolateCreateMs);
                    warmPhases.get("environmentCreateMs").add(measurement.environmentCreateMs);
                    warmPhases.get("bootstrapMs").add(measurement.bootstrapMs);
                    warmPhases.get("scriptExecutionMs").add(measurement.scriptExecutionMs);
                    warmPhases.get("teardownMs").add(measurement.teardownMs);
                }
                runsJson.put(measurement.toJson());
            }
            assertNotNull("first execution was not recorded", first);

            long warmWallP95Ms = percentile(warmWallMs, 0.95d);
            long warmNativeP95Ms = percentile(warmNativeTotalMs, 0.95d);
            boolean snapshotWorthImplementation = warmWallP95Ms >= SNAPSHOT_REVIEW_THRESHOLD_MS;

            JSONObject report = new JSONObject();
            report.put("schema", "autojs6-node-cold-start-measurement-v1");
            report.put("capturedAtUtc", utcTimestamp());
            report.put("device", deviceJson());

            JSONObject runtime = new JSONObject();
            runtime.put("nodeVersion", runtimeInfo.getString(NodeJsRuntimeContract.KEY_NODE_VERSION, ""));
            runtime.put("runtimeSlot", runtimeInfo.getString(NodeJsRuntimeContract.KEY_RUNTIME_SLOT, ""));
            runtime.put("processName", runtimeInfo.getString(NodeJsRuntimeContract.KEY_PROCESS_NAME, ""));
            runtime.put("pid", runtimePid);
            runtime.put("persistentProcessRuntime", runtimeInfo.getBoolean("persistentProcessRuntime"));
            runtime.put("isolatePerExecution", runtimeInfo.getBoolean("isolatePerExecution"));
            runtime.put("servicePrewarmMs", longValue(readiness, "timing.runtime_plugin_prewarm.ms"));
            runtime.put("buildConfiguration", first.buildConfiguration);
            report.put("runtime", runtime);

            JSONObject summary = new JSONObject();
            summary.put("executionCount", EXECUTION_COUNT);
            summary.put("bindAndPrewarmWallMs", bindAndPrewarmWallMs);
            summary.put("bindAndFirstExecutionWallMs", bindAndPrewarmWallMs + first.wallMs);
            summary.put("firstExecutionWallMs", first.wallMs);
            summary.put("firstNativeTotalMs", first.nativeTotalMs);
            summary.put("warmExecutionWallMedianMs", percentile(warmWallMs, 0.50d));
            summary.put("warmExecutionWallP95Ms", warmWallP95Ms);
            summary.put("warmNativeTotalMedianMs", percentile(warmNativeTotalMs, 0.50d));
            summary.put("warmNativeTotalP95Ms", warmNativeP95Ms);
            summary.put("snapshotReviewThresholdMs", SNAPSHOT_REVIEW_THRESHOLD_MS);
            summary.put("snapshotWorthImplementation", snapshotWorthImplementation);
            summary.put(
                    "recommendation",
                    snapshotWorthImplementation
                            ? "continue_startup_snapshot_design"
                            : "do_not_implement_startup_snapshot"
            );
            JSONObject phaseSummary = new JSONObject();
            for (Map.Entry<String, List<Long>> entry : warmPhases.entrySet()) {
                phaseSummary.put(entry.getKey(), distribution(entry.getValue()));
            }
            summary.put("warmPhaseDistribution", phaseSummary);
            report.put("summary", summary);
            report.put("runs", runsJson);
            report.put(
                    "method",
                    "Force-stop the package before instrumentation; bind includes dedicated-process " +
                            "startup and the first readiness query waiting for prewarm; run 0 is the first fresh isolate; " +
                            "runs 1-" + (EXECUTION_COUNT - 1) +
                            " are fresh isolates in the same persistent process."
            );

            String fileName = "m10-cold-start-" + safeFilePart(Build.DEVICE) +
                    "-sdk" + Build.VERSION.SDK_INT + ".json";
            File output = new File(context.getFilesDir(), fileName);
            byte[] encoded = report.toString(2).getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream stream = new FileOutputStream(output, false)) {
                stream.write(encoded);
                stream.getFD().sync();
            }

            Bundle status = new Bundle();
            status.putString(
                    "stream",
                    "M10_COLD_START_RESULT=" + summary + "\n" +
                            "M17_BUILD_CONFIGURATION=" + first.buildConfiguration + "\n" +
                            "M10_COLD_START_FILE=" + output.getAbsolutePath() + "\n"
            );
            InstrumentationRegistry.getInstrumentation().sendStatus(2, status);
        }
    }

    private static Measurement execute(
            INodeJsRuntimePlugin runtime,
            int index,
            int expectedRuntimePid
    ) throws Exception {
        Bundle request = new Bundle();
        request.putInt(
                NodeJsRuntimeContract.KEY_CONTRACT_VERSION,
                NodeJsRuntimeContract.CONTRACT_VERSION
        );
        request.putString(
                NodeJsRuntimeContract.KEY_EXECUTION_ID,
                "m10-cold-start-" + index + "-" + UUID.randomUUID()
        );
        request.putString(NodeJsRuntimeContract.KEY_SOURCE_NAME, "m10-cold-start-" + index + ".cjs");
        request.putString(
                NodeJsRuntimeContract.KEY_SOURCE,
                "'use strict'; if (1 + 1 !== 2) throw new Error('arithmetic'); " +
                        "console.log('m10.cold-start=' + process.pid);\n" +
                        "console.log('m17.config=' + JSON.stringify({node:process.versions.node,openssl:process.versions.openssl," +
                        "icu:process.versions.icu || null,intl:typeof Intl,inspector:process.features && process.features.inspector}));\n"
        );

        long startedAt = SystemClock.elapsedRealtime();
        Bundle result = runtime.runScript(request, new INodeJsRuntimeCallback.Stub() {
            @Override
            public void onEvent(Bundle event) {
            }
        });
        long wallMs = SystemClock.elapsedRealtime() - startedAt;
        assertNotNull("runScript returned null at index " + index, result);
        assertTrue(
                "run " + index + " failed: " +
                        result.getString(NodeJsRuntimeContract.KEY_ERROR_CODE, "") + " / " +
                        result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, ""),
                result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED)
        );
        assertTrue(
                "run " + index + " stdout missing runtime pid: " +
                        result.getString(NodeJsRuntimeContract.KEY_STDOUT, ""),
                result.getString(NodeJsRuntimeContract.KEY_STDOUT, "")
                        .contains("m10.cold-start=" + expectedRuntimePid)
        );

        Map<String, String> nativePayload = nativeValues(result);
        assertEquals(
                "process runtime was not reused at run " + index + ": " + nativePayload,
                "true",
                nativePayload.get("process_runtime.reused")
        );
        Measurement measurement = new Measurement(index, wallMs, result, nativePayload);
        assertTrue("missing native total timing at run " + index, measurement.nativeTotalMs >= 0L);
        assertTrue("missing isolate timing at run " + index, measurement.isolateCreateMs >= 0L);
        assertTrue("missing environment timing at run " + index, measurement.environmentCreateMs >= 0L);
        return measurement;
    }

    private static Map<String, String> nativeValues(Bundle bundle) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        String[] payload = bundle.getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD);
        if (payload == null) return values;
        for (String entry : payload) {
            if (entry == null) continue;
            int separator = entry.indexOf('=');
            if (separator <= 0) continue;
            values.put(entry.substring(0, separator), entry.substring(separator + 1));
        }
        return values;
    }

    private static long longValue(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isEmpty()) return -1L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static long percentile(List<Long> input, double fraction) {
        List<Long> values = nonNegativeValues(input);
        assertTrue("no non-negative samples available", !values.isEmpty());
        Collections.sort(values);
        int rank = (int) Math.ceil(fraction * values.size());
        int index = Math.max(0, Math.min(values.size() - 1, rank - 1));
        return values.get(index);
    }

    private static JSONObject distribution(List<Long> input) throws Exception {
        List<Long> values = nonNegativeValues(input);
        JSONObject distribution = new JSONObject();
        distribution.put("sampleCount", values.size());
        if (values.isEmpty()) {
            distribution.put("medianMs", JSONObject.NULL);
            distribution.put("p95Ms", JSONObject.NULL);
        } else {
            distribution.put("medianMs", percentile(values, 0.50d));
            distribution.put("p95Ms", percentile(values, 0.95d));
        }
        return distribution;
    }

    private static List<Long> nonNegativeValues(List<Long> input) {
        List<Long> values = new ArrayList<>();
        for (Long value : input) {
            if (value != null && value >= 0L) values.add(value);
        }
        return values;
    }

    private static JSONObject deviceJson() throws Exception {
        JSONObject device = new JSONObject();
        device.put("manufacturer", Build.MANUFACTURER);
        device.put("brand", Build.BRAND);
        device.put("model", Build.MODEL);
        device.put("device", Build.DEVICE);
        device.put("sdkInt", Build.VERSION.SDK_INT);
        device.put("supportedAbis", new JSONArray(Arrays.asList(Build.SUPPORTED_ABIS)));
        return device;
    }

    private static String utcTimestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static String safeFilePart(String value) {
        return value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static final class Measurement {
        final int index;
        final long wallMs;
        final long contractElapsedMs;
        final long nativeTotalMs;
        final long isolateCreateMs;
        final long environmentCreateMs;
        final long bootstrapMs;
        final long scriptExecutionMs;
        final long teardownMs;
        final String buildConfiguration;

        Measurement(
                int index,
                long wallMs,
                Bundle result,
                Map<String, String> nativePayload
        ) {
            this.index = index;
            this.wallMs = wallMs;
            this.contractElapsedMs = result.getLong(NodeJsRuntimeContract.KEY_ELAPSED_MS, -1L);
            this.nativeTotalMs = longValue(nativePayload, "timing.total.ms");
            this.isolateCreateMs = longValue(nativePayload, "timing.isolate_create.ms");
            this.environmentCreateMs = longValue(nativePayload, "timing.environment_create.ms");
            this.bootstrapMs = longValue(nativePayload, "timing.bootstrap.ms");
            this.scriptExecutionMs = longValue(nativePayload, "timing.script_execution.ms");
            this.teardownMs = longValue(nativePayload, "timing.teardown.ms");
            this.buildConfiguration = Arrays.stream(result.getString(NodeJsRuntimeContract.KEY_STDOUT, "").split("\\n"))
                    .filter(line -> line.startsWith("m17.config=")).map(line -> line.substring(11)).findFirst().orElse("");
        }

        JSONObject toJson() throws Exception {
            JSONObject json = new JSONObject();
            json.put("index", index);
            json.put("kind", index == 0 ? "first_fresh_isolate" : "process_warm_fresh_isolate");
            json.put("wallMs", wallMs);
            json.put("contractElapsedMs", contractElapsedMs);
            json.put("nativeTotalMs", nativeTotalMs);
            json.put("isolateCreateMs", isolateCreateMs);
            json.put("environmentCreateMs", environmentCreateMs);
            json.put("bootstrapMs", bootstrapMs);
            json.put("scriptExecutionMs", scriptExecutionMs);
            json.put("teardownMs", teardownMs);
            json.put("processRuntimeReused", true);
            return json;
        }
    }

    private static final class BoundRuntime implements AutoCloseable {
        final Context context;
        final ServiceConnection connection;
        final INodeJsRuntimePlugin runtime;
        private boolean closed;

        BoundRuntime(Context context, ServiceConnection connection, INodeJsRuntimePlugin runtime) {
            this.context = context;
            this.connection = connection;
            this.runtime = runtime;
        }

        static BoundRuntime bind(Context context) throws Exception {
            CountDownLatch connected = new CountDownLatch(1);
            AtomicReference<IBinder> binder = new AtomicReference<>();
            AtomicReference<String> failure = new AtomicReference<>();
            ServiceConnection connection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    binder.set(service);
                    connected.countDown();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    failure.compareAndSet(null, "runtime service disconnected");
                }

                @Override
                public void onBindingDied(ComponentName name) {
                    failure.compareAndSet(null, "runtime service binding died");
                    connected.countDown();
                }

                @Override
                public void onNullBinding(ComponentName name) {
                    failure.compareAndSet(null, "runtime service returned a null binding");
                    connected.countDown();
                }
            };
            ComponentName component = new ComponentName(
                    context.getPackageName(),
                    context.getPackageName() + ".NodeJsRuntimePluginService"
            );
            boolean accepted = context.bindService(
                    new Intent().setComponent(component),
                    connection,
                    Context.BIND_AUTO_CREATE
            );
            assertTrue("runtime bind was rejected: " + component, accepted);
            assertTrue(
                    "runtime bind timed out after " + BIND_TIMEOUT_MS + " ms",
                    connected.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            );
            assertTrue("runtime bind failed: " + failure.get(), failure.get() == null);
            INodeJsRuntimePlugin runtime = INodeJsRuntimePlugin.Stub.asInterface(binder.get());
            assertNotNull("runtime AIDL proxy unavailable", runtime);
            assertTrue("runtime Binder is not alive", runtime.asBinder().isBinderAlive());
            return new BoundRuntime(context, connection, runtime);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            context.unbindService(connection);
        }
    }
}
