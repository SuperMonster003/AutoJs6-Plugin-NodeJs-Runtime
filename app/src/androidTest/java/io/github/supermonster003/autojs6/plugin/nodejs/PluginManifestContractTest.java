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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

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
        assertService(pm, packageName, "org.autojs.plugin.nodejs.RUNTIME", "NodeJsRuntimePluginService");
    }

    private static void assertService(PackageManager pm, String packageName, String action, String name) {
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
    }
}
