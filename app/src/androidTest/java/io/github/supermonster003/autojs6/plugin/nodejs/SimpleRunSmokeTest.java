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

import org.autojs.plugin.nodejs.api.INodeJsModuleSourceProvider;
import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Roadmap M0.5: the smallest possible request — nothing but source — must
 * execute and return stdout. No workspace descriptors, no contract fields,
 * no module sources. This is the canary for "a simple script just runs".
 */
@RunWith(AndroidJUnit4.class)
public final class SimpleRunSmokeTest {

    private static final long BIND_TIMEOUT_MS = 30_000L;

    @Test
    public void bareSourceOnlyRequestRunsAndReturnsStdout() throws Exception {
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
            assertTrue(
                    "bind timed out",
                    connected.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            );
            INodeJsRuntimePlugin runtime = INodeJsRuntimePlugin.Stub.asInterface(binder.get());
            assertNotNull("runtime proxy unavailable", runtime);

            Bundle request = new Bundle();
            request.putString(
                    NodeJsRuntimeContract.KEY_SOURCE,
                    "console.log('m0.smoke=' + (2 + 40));\n"
            );
            Bundle result = runtime.runScript(request, new INodeJsRuntimeCallback.Stub() {
                @Override
                public void onEvent(Bundle event) {
                }
            });

            assertNotNull("runScript returned null", result);
            String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
            String error = result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "");
            assertTrue(
                    "execution failed: " + error + " / stdout: " + stdout,
                    result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED)
            );
            assertTrue("stdout missing marker: " + stdout, stdout.contains("m0.smoke=42"));

            // Published v1 hosts attach a provider binder without any version
            // field, and their provider answers with version 1 and no
            // operation echo. Execution must still succeed.
            Bundle v1Request = new Bundle();
            v1Request.putString(
                    NodeJsRuntimeContract.KEY_SOURCE,
                    "console.log('m0.smoke.v1provider=' + (1 + 1));\n"
            );
            v1Request.putBinder(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER,
                    new V1StyleProvider().asBinder()
            );
            Bundle v1Result = runtime.runScript(v1Request, new INodeJsRuntimeCallback.Stub() {
                @Override
                public void onEvent(Bundle event) {
                }
            });
            assertNotNull("v1-provider runScript returned null", v1Result);
            String v1Stdout = v1Result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
            String v1Error = v1Result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "");
            assertTrue(
                    "v1-provider execution failed: " + v1Error + " / stdout: " + v1Stdout,
                    v1Result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED)
            );
            assertTrue(
                    "v1-provider stdout missing marker: " + v1Stdout,
                    v1Stdout.contains("m0.smoke.v1provider=2")
            );
        } finally {
            context.unbindService(connection);
        }
    }

    /**
     * Mimics the published v1 host provider: rejects any request whose version
     * header is not exactly 1 (as NodeJsEncryptedModuleSourceProvider does),
     * then answers accepted requests with a version-1 not_found envelope.
     */
    private static final class V1StyleProvider extends INodeJsModuleSourceProvider.Stub {
        @Override
        public Bundle resolveModuleSource(Bundle request) {
            Bundle response = new Bundle();
            response.putInt(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION, 1);
            response.putString(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_REQUEST_ID,
                    request == null
                            ? ""
                            : request.getString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_REQUEST_ID, "")
            );
            int requestVersion = request == null
                    ? 0
                    : request.getInt(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION, 0);
            if (requestVersion != 1) {
                response.putString(
                        NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_STATUS,
                        NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_FAILED
                );
                response.putString(
                        NodeJsRuntimeContract.KEY_ERROR_CODE,
                        NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_INVALID_REQUEST
                );
                return response;
            }
            response.putString(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_STATUS,
                    NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_NOT_FOUND
            );
            return response;
        }

        @Override
        public Bundle getNativeDiagnostics() {
            return new Bundle();
        }

        @Override
        public void cancel(String reason) {
        }
    }
}
