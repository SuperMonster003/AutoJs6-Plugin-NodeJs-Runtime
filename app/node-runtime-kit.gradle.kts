import com.android.build.api.dsl.ApplicationExtension
import groovy.json.JsonSlurper
import java.security.MessageDigest

val nodePluginRuntimeKitManifestFile = layout.projectDirectory.file("src/main/assets/nodejs/node-plugin-runtime-kit.json")
val nodePluginRuntimeKitVerifierFile = rootProject.layout.projectDirectory.file("tools/nodejs/runtime-kit/verify-node-plugin-runtime-kit.js")
val nodePluginRuntimeKitReleaseTargetVersion = "1.1.3"
val nodePluginRuntimeKitReleaseTargetId = "autojs6-node-plugin-runtime-kit-v1.1.3-node24_5"
val nodePluginRuntimeKitReleaseDirectory = rootProject.layout.projectDirectory.dir(
    "releases/nodejs-plugin-runtime-kit/$nodePluginRuntimeKitReleaseTargetVersion",
)
val nodePluginRuntimeKitReleaseFile = nodePluginRuntimeKitReleaseDirectory.file("node-plugin-runtime-kit.json")
val nodePluginRuntimeKitReleaseLockFile = nodePluginRuntimeKitReleaseDirectory.file("node-plugin-runtime-kit.lock")
val nodePluginRuntimeKitReportFile = rootProject.layout.buildDirectory.file("reports/nodejs/runtime-kit-manifest.json")
val nodePluginRuntimeKitGateRequested = gradle.startParameter.taskNames.any { requested ->
    requested.substringAfterLast(':') == "verifyNodePluginRuntimeKitGate"
}

// AGP 9.0.1 lint can crash while resolving applied Kotlin scripts before it
// analyzes app sources. Keep normal release lint unchanged; this focused
// artifact gate validates the already compiled/R8-processed release APK.
if (nodePluginRuntimeKitGateRequested) {
    tasks.matching { task ->
        task.name in setOf("lintVitalAnalyzeRelease", "lintVitalReportRelease", "lintVitalRelease")
    }.configureEach {
        enabled = false
    }
}

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

require(nodePluginRuntimeKitSchema == "autojs6-node-plugin-runtime-kit-v1") {
    "Unsupported Node plugin Runtime Kit schema: $nodePluginRuntimeKitSchema"
}
require(nodePluginRuntimeKitVersion == "1.1.3") {
    "Unsupported Node plugin Runtime Kit version: $nodePluginRuntimeKitVersion"
}
require(nodePluginRuntimeKitId == "autojs6-node-plugin-runtime-kit-v1.1.3-node24_5") {
    "Unexpected Node plugin Runtime Kit ID: $nodePluginRuntimeKitId"
}

fun requireNodePluginRuntimeKitReleaseTarget() {
    require(nodePluginRuntimeKitVersion == nodePluginRuntimeKitReleaseTargetVersion) {
        "Runtime Kit release requires canonical version $nodePluginRuntimeKitReleaseTargetVersion; " +
            "current manifest is $nodePluginRuntimeKitVersion"
    }
    require(nodePluginRuntimeKitId == nodePluginRuntimeKitReleaseTargetId) {
        "Runtime Kit release requires canonical ID $nodePluginRuntimeKitReleaseTargetId; " +
            "current manifest is $nodePluginRuntimeKitId"
    }
}

extensions.configure<ApplicationExtension> {
    defaultConfig {
        buildConfigField("String", "NODE_PLUGIN_RUNTIME_KIT_SCHEMA", "\"$nodePluginRuntimeKitSchema\"")
        buildConfigField("String", "NODE_PLUGIN_RUNTIME_KIT_VERSION", "\"$nodePluginRuntimeKitVersion\"")
        buildConfigField("String", "NODE_PLUGIN_RUNTIME_KIT_SHA256", "\"$nodePluginRuntimeKitSha256\"")
        buildConfigField("String", "NODE_PLUGIN_RUNTIME_KIT_ID", "\"$nodePluginRuntimeKitId\"")
    }
}

fun nodePluginRuntimeKitReleaseApks(): List<File> {
    val candidates = fileTree(layout.buildDirectory.dir("outputs/apk/release")) {
        include("**/*.apk")
    }.files.sortedBy { it.invariantSeparatorsPath }
    val carrierSuffixes = listOf("universal", "arm64-v8a", "armeabi-v7a", "x86_64")
    val result = carrierSuffixes.map { suffix ->
        val matches = candidates.filter { it.name.endsWith("-$suffix.apk") }
        require(matches.size == 1) {
            "Expected exactly one $suffix release plugin APK, found ${matches.size}: " +
                    matches.joinToString { it.invariantSeparatorsPath }
        }
        matches.single()
    }
    require(candidates.size == result.size && candidates.toSet() == result.toSet()) {
        "Unexpected release APK carrier set: ${candidates.joinToString { it.invariantSeparatorsPath }}"
    }
    return result
}

val nodePluginRuntimeKitInputs = files(
    nodePluginRuntimeKitManifestFile,
    nodePluginRuntimeKitVerifierFile,
    rootProject.layout.projectDirectory.file("tools/nodejs/runtime-build/runtime-build.lock.json"),
    rootProject.layout.projectDirectory.file("tools/nodejs/runtime-build/build-node-runtime.ps1"),
    rootProject.layout.projectDirectory.file("tools/nodejs/runtime-build/build-node-runtime.sh"),
    layout.projectDirectory.file("src/main/assets/nodejs/node-capability-catalog.json"),
    layout.projectDirectory.file("src/main/cpp/CMakeLists.txt"),
    layout.projectDirectory.file("src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeJsPluginInfoService.java"),
    layout.projectDirectory.file("src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeJsRuntimePluginService.java"),
    rootProject.layout.projectDirectory.file("releases/nodejs-api/1.1.0/nodejs-api.lock"),
    rootProject.layout.projectDirectory.file("releases/nodejs-api/1.1.0/nodejs-api-1.1.0.aar"),
    rootProject.layout.projectDirectory.file(
        "releases/nodejs-capability-catalog/1.1.1/node-capability-catalog.lock",
    ),
)

tasks.register<Exec>("stageNodePluginRuntimeKitRelease") {
    group = "release"
    description = "Stages immutable Runtime Kit 1.1.3 only after API/catalog/core truth and payload bytes are final."
    dependsOn(
        ":nodejs-api:verifyNodeJsApiPublication",
        "verifyNodeCapabilityCatalogRelease",
        "verifyNodeCapabilityTruthGate",
        "stripReleaseDebugSymbols",
    )
    inputs.files(nodePluginRuntimeKitInputs)
    outputs.upToDateWhen { false }
    doFirst {
        requireNodePluginRuntimeKitReleaseTarget()
    }
    commandLine(
        "node",
        nodePluginRuntimeKitVerifierFile.asFile.absolutePath,
        "--project-root", rootProject.projectDir.absolutePath,
        "--manifest", nodePluginRuntimeKitManifestFile.asFile.absolutePath,
        "--stage-release",
    )
}

tasks.register<Exec>("verifyNodePluginRuntimeKitRelease") {
    group = "verification"
    description = "Verifies the locked Runtime Kit control plane and stripped native payload sources; use verifyNodePluginRuntimeKitGate for APK carriers."
    dependsOn(
        ":nodejs-api:verifyNodeJsApiPublication",
        "verifyNodeCapabilityCatalogRelease",
        "verifyNodeCapabilityTruthGate",
        "stripReleaseDebugSymbols",
    )
    inputs.files(nodePluginRuntimeKitInputs, nodePluginRuntimeKitReleaseFile, nodePluginRuntimeKitReleaseLockFile)
    outputs.upToDateWhen { false }
    doFirst {
        requireNodePluginRuntimeKitReleaseTarget()
    }
    commandLine(
        "node",
        nodePluginRuntimeKitVerifierFile.asFile.absolutePath,
        "--project-root", rootProject.projectDir.absolutePath,
        "--manifest", nodePluginRuntimeKitManifestFile.asFile.absolutePath,
        "--verify-release",
    )
}

tasks.register<Exec>("verifyNodePluginRuntimeKitGate") {
    group = "verification"
    description = "Assembles and verifies the universal/per-ABI plugin APK Runtime Kit manifest and all ten payload entries."
    dependsOn(
        ":nodejs-api:verifyNodeJsApiPublication",
        "verifyNodeCapabilityCatalogRelease",
        "verifyNodeCapabilityTruthGate",
        "assembleRelease",
    )
    inputs.files(nodePluginRuntimeKitInputs, nodePluginRuntimeKitReleaseFile, nodePluginRuntimeKitReleaseLockFile)
    outputs.file(nodePluginRuntimeKitReportFile)
    outputs.upToDateWhen { false }
    doFirst {
        requireNodePluginRuntimeKitReleaseTarget()
        nodePluginRuntimeKitReportFile.get().asFile.apply {
            parentFile.mkdirs()
            delete()
        }
        val verifierArgs = mutableListOf(
            "node",
            nodePluginRuntimeKitVerifierFile.asFile.absolutePath,
            "--project-root", rootProject.projectDir.absolutePath,
            "--manifest", nodePluginRuntimeKitManifestFile.asFile.absolutePath,
            "--verify-release",
            "--require-release-apks",
            "--self-test",
        )
        nodePluginRuntimeKitReleaseApks().forEach { apk ->
            verifierArgs.addAll(listOf("--release-apk", apk.absolutePath))
        }
        verifierArgs.addAll(listOf("--report", nodePluginRuntimeKitReportFile.get().asFile.absolutePath))
        commandLine(verifierArgs)
    }
}
