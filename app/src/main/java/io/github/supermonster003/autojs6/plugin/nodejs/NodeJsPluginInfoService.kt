package io.github.supermonster003.autojs6.plugin.nodejs

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import org.autojs.plugin.common.api.IPluginInfoProvider
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.nodejs.api.NodeJsPluginCapabilityKeys
import org.autojs.plugin.nodejs.api.NodeJsPluginIds

class NodeJsPluginInfoService : Service() {

    private val binder = object : IPluginInfoProvider.Stub() {
        override fun getInfo(): PluginInfo {
            return PluginInfo(
                name = getString(R.string.app_name),
                description = null,
                instruction = null,
                author = "SuperMonster003",
                collaborators = null,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
                versionDate = BuildConfig.VERSION_DATE,
                id = NodeJsPluginIds.ID,
                engine = NodeJsPluginIds.ENGINE,
                variant = NodeJsPluginIds.VARIANT_NODE_24_5,
                supportedAbis = SUPPORTED_ABIS,
                capabilities = Bundle().apply {
                    putInt(PluginCapabilityKeys.REQUIRES_HOST_VERSION, 3923)
                    putString(NodeJsPluginCapabilityKeys.NODE_VERSION, "24.5.0")
                    putString(NodeJsPluginCapabilityKeys.RUNTIME_SLOT, NodeJsPluginIds.VARIANT_NODE_24_5)
                    putString(NodeJsPluginCapabilityKeys.NATIVE_LIBRARY_NAME, "node")
                },
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private companion object {
        val SUPPORTED_ABIS = arrayOf("arm64-v8a", "armeabi-v7a", "x86_64")
    }
}
