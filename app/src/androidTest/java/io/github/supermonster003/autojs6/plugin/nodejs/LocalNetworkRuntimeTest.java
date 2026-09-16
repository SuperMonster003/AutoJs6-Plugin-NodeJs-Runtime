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

/** Actual Node socket access under the plugin UID, including denied-grant recovery. */
@RunWith(AndroidJUnit4.class)
public final class LocalNetworkRuntimeTest {

    private static final long BIND_TIMEOUT_MS = 30_000L;

    @Test
    public void localNetworkUsesThePluginGrant() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext()
                .getApplicationContext();
        Bundle args = InstrumentationRegistry.getArguments();
        org.junit.Assume.assumeTrue("Provide lanPort for the owned LAN fixture", args.containsKey("lanPort"));
        String host = args.getString("lanHost", "10.0.2.2");
        int port = Integer.parseInt(args.getString("lanPort"));
        boolean granted = LocalNetworkAccess.isGranted(context);
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
            request.putString(NodeJsRuntimeContract.KEY_SOURCE,
                    "const s = require('net').createConnection({host:" + org.json.JSONObject.quote(host) + ",port:" + port + "});"
                    + "s.setTimeout(3000,()=>{s.destroy();throw new Error('LAN timeout')});"
                    + "s.on('data',b=>{if(b.toString()!=='K')throw new Error('bad reply');console.log('LAN_OK');s.end()});"
                    + "s.on('error',e=>{throw e});");
            request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 10_000L);
            Bundle result = runtime.runScript(request, new INodeJsRuntimeCallback.Stub() {
                @Override
                public void onEvent(Bundle event) {
                }
            });

            assertNotNull("runScript returned null", result);
            String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
            String stderr = result.getString(NodeJsRuntimeContract.KEY_STDERR, "");
            org.junit.Assert.assertEquals("stdout=" + stdout + " stderr=" + stderr,
                    granted, result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            if (granted) assertTrue(stdout, stdout.contains("LAN_OK"));
            else assertTrue(result.toString(), result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "")
                    .contains(context.getString(R.string.local_network_failure_hint)));
        } finally {
            context.unbindService(connection);
        }
    }
}
