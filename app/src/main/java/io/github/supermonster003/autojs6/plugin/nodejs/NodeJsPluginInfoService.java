package io.github.supermonster003.autojs6.plugin.nodejs;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import org.autojs.plugin.common.api.IPluginInfoProvider;
import org.autojs.plugin.common.api.PluginCapabilityKeys;
import org.autojs.plugin.common.api.PluginInfo;
import org.autojs.plugin.nodejs.api.NodeJsPluginCapabilityKeys;
import org.autojs.plugin.nodejs.api.NodeJsPluginActions;
import org.autojs.plugin.nodejs.api.NodeJsPluginIds;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;

public class NodeJsPluginInfoService extends Service {

    private static final String[] SUPPORTED_ABIS = {"arm64-v8a", "armeabi-v7a", "x86_64"};

    private final IPluginInfoProvider.Stub binder = new IPluginInfoProvider.Stub() {
        @Override
        public PluginInfo getInfo() throws RemoteException {
            PackageInfo installedPackage;
            try {
                installedPackage = getPackageManager().getPackageInfo(getPackageName(), 0);
            } catch (PackageManager.NameNotFoundException e) {
                throw new IllegalStateException("Installed plugin package is unavailable", e);
            }
            Bundle capabilities = new Bundle();
            capabilities.putInt(PluginCapabilityKeys.REQUIRES_HOST_VERSION, 3923);
            capabilities.putString(NodeJsPluginCapabilityKeys.NODE_VERSION, "24.21.0");
            capabilities.putString(NodeJsPluginCapabilityKeys.RUNTIME_SLOT, NodeJsPluginIds.VARIANT_NODE_24_21);
            capabilities.putString(NodeJsPluginCapabilityKeys.NATIVE_LIBRARY_NAME, "node");
            capabilities.putInt(
                    NodeJsPluginCapabilityKeys.RUNTIME_CONTRACT_VERSION,
                    NodeJsRuntimeContract.CONTRACT_VERSION
            );
            capabilities.putString(
                    NodeJsPluginCapabilityKeys.RUNTIME_SERVICE_ACTION,
                    NodeJsPluginActions.RUNTIME
            );
            capabilities.putString("nodeRuntimeKitSchema", BuildConfig.NODE_PLUGIN_RUNTIME_KIT_SCHEMA);
            capabilities.putString("nodeRuntimeKitVersion", BuildConfig.NODE_PLUGIN_RUNTIME_KIT_VERSION);
            capabilities.putString("nodeRuntimeKitSha256", BuildConfig.NODE_PLUGIN_RUNTIME_KIT_SHA256);
            capabilities.putString("nodeRuntimeKitId", BuildConfig.NODE_PLUGIN_RUNTIME_KIT_ID);

            return new PluginInfo(
                    getString(R.string.app_name),
                    getString(R.string.plugin_description),
                    "@raw/plugin_instruction",
                    "SuperMonster003",
                    null,
                    installedPackage.versionName,
                    installedPackage.versionCode,
                    BuildConfig.VERSION_DATE,
                    NodeJsPluginIds.ID,
                    NodeJsPluginIds.ENGINE,
                    NodeJsPluginIds.VARIANT_NODE_24_21,
                    SUPPORTED_ABIS,
                    capabilities
            );
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
