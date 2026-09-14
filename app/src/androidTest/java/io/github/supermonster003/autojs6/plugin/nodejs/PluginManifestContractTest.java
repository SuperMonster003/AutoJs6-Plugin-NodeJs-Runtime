package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.autojs.plugin.nodejs.api.NodeJsPluginCapabilityKeys;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

@RunWith(AndroidJUnit4.class)
public final class PluginManifestContractTest {
    private static final String PERMISSION = "org.autojs.permission.PLUGIN";

    @Test
    public void activationAndServiceDiscoveryMatchHostContract() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageManager pm = context.getPackageManager();
        String packageName = context.getPackageName();
        ApplicationInfo app = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
        assertEquals(0, app.flags & ApplicationInfo.FLAG_ALLOW_BACKUP);
        assertNotNull(app.metaData);
        String wakeName = app.metaData.getString("org.autojs.plugin.WAKE_ACTIVITY");
        assertNotNull(wakeName);
        if (wakeName.startsWith(".")) wakeName = packageName + wakeName;
        ActivityInfo wake = pm.getActivityInfo(new ComponentName(packageName, wakeName), 0);
        assertEquals(packageName + ".WakeActivity", wake.name);
        assertTrue(wake.enabled);
        assertTrue(wake.exported);
        assertEquals(PERMISSION, wake.permission);
        assertEquals(android.R.style.Theme_NoDisplay, wake.theme);
        List<ResolveInfo> activities = pm.queryIntentActivities(
                new Intent("org.autojs.plugin.action.WAKE")
                        .addCategory(Intent.CATEGORY_DEFAULT).setPackage(packageName),
                PackageManager.MATCH_DEFAULT_ONLY);
        assertEquals(1, activities.size());
        assertEquals(wake.name, activities.get(0).activityInfo.name);

        assertService(pm, packageName, "org.autojs.plugin.INFO", "NodeJsPluginInfoService");
        ServiceInfo runtime = assertService(pm, packageName, "org.autojs.plugin.nodejs.RUNTIME", "NodeJsRuntimePluginService");
        assertTerminalLauncherContract(context, runtime);
        for (int slot = 0; slot < 2; slot++) {
            ServiceInfo worker = pm.getServiceInfo(new ComponentName(packageName, packageName + ".NodeJsRuntimeSlot" + slot + "Service"), 0);
            assertEquals(packageName + ":nodejs_runtime" + slot, worker.processName);
            assertEquals(false, worker.exported);
        }
    }

    /**
     * The host reads the terminal launcher contract from these meta-data without binding, then
     * executes lib/<abi>/libnodexe.so next to libnode.so; every advertised ABI must package both.
     * zh-CN: 宿主不绑定服务, 直接从这些 meta-data 读取终端启动器契约, 再执行 libnode.so 旁的
     * lib/<abi>/libnodexe.so; 每个已声明 ABI 都必须同时打包两者.
     */
    private static void assertTerminalLauncherContract(Context context, ServiceInfo runtime) throws Exception {
        Bundle meta = runtime.metaData;
        assertEquals("1", String.valueOf(meta.get(NodeJsPluginCapabilityKeys.NODE_CLI_SCHEMA)));
        assertEquals(BuildConfig.NODE_CLI_EXECUTABLE, meta.getString(NodeJsPluginCapabilityKeys.NODE_CLI_EXECUTABLE));
        assertEquals(BuildConfig.NODE_CLI_COMMANDS, meta.getString(NodeJsPluginCapabilityKeys.NODE_CLI_COMMANDS));
        assertEquals(BuildConfig.NODE_CLI_ARCHIVE, meta.getString(NodeJsPluginCapabilityKeys.NODE_CLI_ARCHIVE));
        assertEquals(BuildConfig.NODE_CLI_ARCHIVE_SHA256, meta.getString(NodeJsPluginCapabilityKeys.NODE_CLI_ARCHIVE_SHA256));
        assertEquals(BuildConfig.NODE_CLI_ARCHIVE_ROOT, meta.getString(NodeJsPluginCapabilityKeys.NODE_CLI_ARCHIVE_ROOT));
        assertEquals(String.valueOf(BuildConfig.NODE_CLI_ARCHIVE_ENTRY_COUNT),
                String.valueOf(meta.get(NodeJsPluginCapabilityKeys.NODE_CLI_ARCHIVE_ENTRY_COUNT)));
        assertEquals(String.valueOf(BuildConfig.NODE_CLI_ARCHIVE_BYTES),
                String.valueOf(meta.get(NodeJsPluginCapabilityKeys.NODE_CLI_ARCHIVE_BYTES)));
        assertEquals(BuildConfig.NODE_CLI_NPM_VERSION, String.valueOf(meta.get(NodeJsPluginCapabilityKeys.NODE_CLI_NPM_VERSION)));
        assertEquals(BuildConfig.NODE_CLI_COREPACK_VERSION, String.valueOf(meta.get(NodeJsPluginCapabilityKeys.NODE_CLI_COREPACK_VERSION)));

        File launcher = new File(context.getApplicationInfo().nativeLibraryDir, BuildConfig.NODE_CLI_EXECUTABLE);
        assertTrue("launcher missing: " + launcher, launcher.isFile());
        try (FileInputStream stream = new FileInputStream(launcher)) {
            byte[] magic = new byte[4];
            assertEquals(4, stream.read(magic));
            assertTrue("launcher is not an ELF file", Arrays.equals(new byte[]{0x7f, 'E', 'L', 'F'}, magic));
        }
        try (java.io.InputStream asset = context.getAssets().open(BuildConfig.NODE_CLI_ARCHIVE)) {
            assertEquals('P', asset.read());
            assertEquals('K', asset.read());
        }

        List<String> installedPaths = new ArrayList<>();
        installedPaths.add(context.getApplicationInfo().sourceDir);
        if (context.getApplicationInfo().splitSourceDirs != null) {
            installedPaths.addAll(Arrays.asList(context.getApplicationInfo().splitSourceDirs));
        }
        int packagedAbis = 0;
        for (String path : installedPaths) {
            try (ZipFile apk = new ZipFile(path)) {
                for (String abi : NodeJsRuntimePluginService.SUPPORTED_ABIS) {
                    if (apk.getEntry("lib/" + abi + "/libnode.so") == null) continue;
                    packagedAbis++;
                    assertNotNull("launcher is not packaged for " + abi,
                            apk.getEntry("lib/" + abi + "/" + BuildConfig.NODE_CLI_EXECUTABLE));
                }
            }
        }
        assertTrue("no runtime ABI is packaged", packagedAbis > 0);
    }

    private static ServiceInfo assertService(PackageManager pm, String packageName, String action, String name) {
        List<ResolveInfo> matches = pm.queryIntentServices(
                new Intent(action).addCategory("nodejs").setPackage(packageName),
                PackageManager.GET_META_DATA);
        assertEquals(action, 1, matches.size());
        ServiceInfo service = matches.get(0).serviceInfo;
        assertEquals(packageName + "." + name, service.name);
        assertTrue(service.enabled);
        assertTrue(service.exported);
        assertEquals(PERMISSION, service.permission);
        assertNotNull(service.metaData);
        assertEquals(3923, service.metaData.getInt("requiresHostVersion"));
        return service;
    }
}
