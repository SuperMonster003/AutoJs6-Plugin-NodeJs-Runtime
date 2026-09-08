package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.os.IBinder;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.autojs.plugin.common.api.IPluginInfoProvider;
import org.autojs.plugin.common.api.PluginCapabilityKeys;
import org.autojs.plugin.common.api.PluginInfo;
import org.autojs.plugin.nodejs.api.NodeJsPluginCapabilityKeys;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class PluginInfoContractTest {
    @Test
    public void boundInfoDescribesInstalledPlugin() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
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
        assertTrue(context.bindService(new Intent().setComponent(new ComponentName(
                context.getPackageName(), NodeJsPluginInfoService.class.getName())),
                connection, Context.BIND_AUTO_CREATE));
        try {
            assertTrue("INFO bind timed out", connected.await(10, TimeUnit.SECONDS));
            assertEquals("org.autojs.plugin.common.api.IPluginInfoProvider", binder.get().getInterfaceDescriptor());
            PluginInfo info = IPluginInfoProvider.Stub.asInterface(binder.get()).getInfo();
            assertNotNull(info);
            PackageInfo installed = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            assertEquals(installed.versionName, info.getVersionName());
            assertEquals(installed.versionCode, info.getVersionCode());
            assertEquals(installed.applicationInfo.loadLabel(context.getPackageManager()).toString(), info.getName());
            // Use the installed resource contract; R classes can be removed by release R8.
            int description = context.getResources().getIdentifier(
                    "plugin_description", "string", context.getPackageName());
            assertTrue("plugin_description resource is missing", description != 0);
            assertEquals(context.getString(description), info.getDescription());
            assertEquals("@raw/plugin_instruction", info.getInstruction());
            assertEquals("nodejs", info.getId());
            assertEquals("nodejs", info.getEngine());
            assertEquals("node24_5", info.getVariant());
            assertArrayEquals(new String[]{"arm64-v8a", "armeabi-v7a", "x86_64"}, info.getSupportedAbis());
            assertNotNull(info.getCapabilities());
            assertEquals(3923, info.getCapabilities().getInt(PluginCapabilityKeys.REQUIRES_HOST_VERSION));
            assertEquals(NodeJsRuntimeContract.CONTRACT_VERSION,
                    info.getCapabilities().getInt(NodeJsPluginCapabilityKeys.RUNTIME_CONTRACT_VERSION));
        } finally {
            context.unbindService(connection);
        }
    }
}
