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

/** Roadmap M8.3: executionMode drives lifecycle config without engine-info. */
@RunWith(AndroidJUnit4.class)
public final class ExecutionModeLifecycleSmokeTest {

    private static final long BIND_TIMEOUT_MS = 30_000L;

    @Test
    public void explicitLongRunningRequestOpensCheckpointPolicy() throws Exception {
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

            Bundle request = new Bundle();
            request.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, "m8-explicit-lifecycle-mode");
            request.putString(NodeJsRuntimeContract.KEY_SOURCE_NAME, "m8-explicit-lifecycle-mode.cjs");
            request.putString(NodeJsRuntimeContract.KEY_WORKING_DIRECTORY, context.getCacheDir().getAbsolutePath());
            request.putString(NodeJsRuntimeContract.KEY_EXECUTION_MODE, "interactive_long_running");
            request.putString(
                    NodeJsRuntimeContract.KEY_SOURCE,
                    "const lifecycle = require('autojs6:lifecycle');\n" +
                            "console.log('m8.lifecycle.mode=' + lifecycle.policy.executionMode);\n" +
                            "console.log('m8.lifecycle.surface=' + lifecycle.policy.launchSurface);\n" +
                            "console.log('m8.lifecycle.checkpoint=' + lifecycle.policy.checkpoint.enabled);\n" +
                            "console.log('m8.lifecycle.restart=' + lifecycle.policy.restartPolicy);\n" +
                            "console.log('m8.lifecycle.auto=' + lifecycle.policy.automaticRestart);\n"
            );

            Bundle result = runtime.runScript(request, emptyCallback());
            assertNotNull("lifecycle execution returned null", result);
            assertTrue(
                    "lifecycle execution failed: " +
                            result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, ""),
                    result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED)
            );
            String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
            assertTrue("request executionMode was not injected", stdout.contains(
                    "m8.lifecycle.mode=interactive_long_running"
            ));
            assertTrue("direct long-running surface was not inferred", stdout.contains(
                    "m8.lifecycle.surface=interactive_session"
            ));
            assertTrue("checkpoint policy stayed closed", stdout.contains(
                    "m8.lifecycle.checkpoint=true"
            ));
            assertTrue("restart policy changed", stdout.contains("m8.lifecycle.restart=never"));
            assertTrue("automatic restart unexpectedly enabled", stdout.contains("m8.lifecycle.auto=false"));
        } finally {
            context.unbindService(connection);
        }
    }

    private static INodeJsRuntimeCallback emptyCallback() {
        return new INodeJsRuntimeCallback.Stub() {
            @Override
            public void onEvent(Bundle event) {
            }
        };
    }
}
