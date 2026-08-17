import com.android.build.api.dsl.ApplicationExtension
import groovy.json.JsonSlurper
import java.security.MessageDigest

// Reads the Runtime Kit manifest and exposes its identity as BuildConfig
// fields (consumed by NodeJsPluginInfoService / NodeJsRuntimePluginService
// when answering getBrokerInfo/runtimeInfo). The former release/verification
// gate chain (verifyNodePluginRuntimeKit*, stageNodePluginRuntimeKitRelease)
// was removed in M4.2; releases are identified by version + git tag.

val nodePluginRuntimeKitManifestFile = layout.projectDirectory.file("src/main/assets/nodejs/node-plugin-runtime-kit.json")

fun nodePluginRuntimeKitSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
    return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

@Suppress("UNCHECKED_CAST")
val nodePluginRuntimeKitManifest = JsonSlurper().parse(nodePluginRuntimeKitManifestFile.asFile) as Map<String, Any?>
val nodePluginRuntimeKitSchema = nodePluginRuntimeKitManifest.getValue("schema").toString()
val nodePluginRuntimeKitVersion = nodePluginRuntimeKitManifest.getValue("kitVersion").toString()
val nodePluginRuntimeKitId = nodePluginRuntimeKitManifest.getValue("kitId").toString()
val nodePluginRuntimeKitSha256 = nodePluginRuntimeKitSha256(nodePluginRuntimeKitManifestFile.asFile)

extensions.configure<ApplicationExtension> {
    defaultConfig {
        buildConfigField("String", "NODE_PLUGIN_RUNTIME_KIT_SCHEMA", "\"$nodePluginRuntimeKitSchema\"")
        buildConfigField("String", "NODE_PLUGIN_RUNTIME_KIT_VERSION", "\"$nodePluginRuntimeKitVersion\"")
        buildConfigField("String", "NODE_PLUGIN_RUNTIME_KIT_SHA256", "\"$nodePluginRuntimeKitSha256\"")
        buildConfigField("String", "NODE_PLUGIN_RUNTIME_KIT_ID", "\"$nodePluginRuntimeKitId\"")
    }
}
