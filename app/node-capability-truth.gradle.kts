val nodeCapabilityCatalogFile = layout.projectDirectory.file("src/main/assets/nodejs/node-capability-catalog.json")
val nodeCapabilityVerifierFile = rootProject.layout.projectDirectory.file("tools/nodejs/capabilities/verify-capability-truth.js")
val nodeCapabilityReleaseDirectory = rootProject.layout.projectDirectory.dir("releases/nodejs-capability-catalog/1.1.1")
val nodeCapabilityReleaseFile = nodeCapabilityReleaseDirectory.file("node-capability-catalog.json")
val nodeCapabilityReleaseLockFile = nodeCapabilityReleaseDirectory.file("node-capability-catalog.lock")
val nodeCapabilityTruthReportFile = layout.buildDirectory.file("reports/nodejs/capability-truth.json")
val nodeModuleSourceProviderResolverVerifierFile = rootProject.layout.projectDirectory.file(
    "tools/nodejs/resolver/test-module-source-provider.js",
)
val nodeModuleSourceProviderResolverReportFile = layout.buildDirectory.file(
    "reports/nodejs/module-source-provider-resolver.json",
)

val nodeCapabilityRuntimeInputs = files(
    layout.projectDirectory.file("src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeJsRuntimePluginService.java"),
    layout.projectDirectory.file("src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/PluginModuleSourceProviderFileTransportSession.java"),
    layout.projectDirectory.file("src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeTypeScriptStripper.java"),
    layout.projectDirectory.file("src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeStartupEnvironmentPolicy.java"),
    layout.projectDirectory.file("src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeJsPluginInfoService.java"),
    layout.projectDirectory.file("src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeBridgePermissionManifest.java"),
    layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"),
    layout.projectDirectory.file("src/main/cpp/node_bridge_internal.h"),
    rootProject.layout.projectDirectory.file("plugin-api/nodejs-api/src/main/java/org/autojs/plugin/nodejs/api/NodeJsRuntimeContract.kt"),
    rootProject.layout.projectDirectory.file("plugin-api/nodejs-api/src/main/java/org/autojs/plugin/nodejs/api/NodeJsPluginIds.kt"),
    rootProject.layout.projectDirectory.file("plugin-api/nodejs-api/src/main/java/org/autojs/plugin/nodejs/api/NodeJsPluginCapabilityKeys.kt"),
)

val verifyNodeModuleSourceProviderResolverGate = tasks.register<Exec>(
    "verifyNodeModuleSourceProviderResolverGate",
) {
    group = "verification"
    description = "Executes the Android-free CJS/ESM/dynamic plaintext TypeScript provider transport scenarios."
    inputs.files(
        nodeModuleSourceProviderResolverVerifierFile,
        layout.projectDirectory.file("src/main/cpp/node_bridge_sources.cpp"),
    )
    outputs.file(nodeModuleSourceProviderResolverReportFile)
    outputs.upToDateWhen { false }
    doFirst {
        nodeModuleSourceProviderResolverReportFile.get().asFile.apply {
            parentFile.mkdirs()
            delete()
        }
    }
    commandLine(
        "node",
        nodeModuleSourceProviderResolverVerifierFile.asFile.absolutePath,
        "--report",
        nodeModuleSourceProviderResolverReportFile.get().asFile.absolutePath,
    )
}

tasks.register<Exec>("stageNodeCapabilityCatalogRelease") {
    group = "release"
    description = "Stages the immutable Node capability catalog 1.1.1 release and refuses overwrite."
    inputs.file(nodeCapabilityCatalogFile)
    inputs.file(nodeCapabilityVerifierFile)
    outputs.upToDateWhen { false }
    commandLine(
        "node",
        nodeCapabilityVerifierFile.asFile.absolutePath,
        "--project-root",
        rootProject.projectDir.absolutePath,
        "--catalog",
        nodeCapabilityCatalogFile.asFile.absolutePath,
        "--stage-release",
    )
}

tasks.register<Exec>("verifyNodeCapabilityCatalogRelease") {
    group = "verification"
    description = "Verifies the immutable Node capability catalog 1.1.1 JSON and lock."
    inputs.file(nodeCapabilityCatalogFile)
    inputs.file(nodeCapabilityVerifierFile)
    inputs.file(nodeCapabilityReleaseFile)
    inputs.file(nodeCapabilityReleaseLockFile)
    inputs.files(nodeCapabilityRuntimeInputs)
    outputs.upToDateWhen { false }
    commandLine(
        "node",
        nodeCapabilityVerifierFile.asFile.absolutePath,
        "--project-root",
        rootProject.projectDir.absolutePath,
        "--catalog",
        nodeCapabilityCatalogFile.asFile.absolutePath,
        "--verify-release",
    )
}

val verifyNodeCapabilityTruthGate = tasks.register<Exec>("verifyNodeCapabilityTruthGate") {
    group = "verification"
    description = "Verifies the canonical Node capability catalog against plugin runtime truth."
    dependsOn(verifyNodeModuleSourceProviderResolverGate)
    inputs.file(nodeCapabilityCatalogFile)
    inputs.file(nodeCapabilityVerifierFile)
    inputs.file(nodeCapabilityReleaseFile)
    inputs.file(nodeCapabilityReleaseLockFile)
    inputs.files(nodeCapabilityRuntimeInputs)
    inputs.files(
        fileTree(rootProject.projectDir.resolve("app/src/main/java")) {
            include("**/*.java", "**/*.kt")
        },
        fileTree(rootProject.projectDir.resolve("app/src/main/cpp")) {
            include("**/*.c", "**/*.cc", "**/*.cpp", "**/*.h", "**/*.hpp")
        },
        fileTree(rootProject.projectDir.resolve(".readme")) {
            include("lang_*.json", "README-*.md")
        },
        fileTree(rootProject.projectDir.resolve("sample/nodejs")) {
            include("**/README.md", "**/project.json")
        },
        rootProject.layout.projectDirectory.file("README.md"),
    )
    outputs.file(nodeCapabilityTruthReportFile)
    outputs.upToDateWhen { false }
    doFirst {
        nodeCapabilityTruthReportFile.get().asFile.apply {
            parentFile.mkdirs()
            delete()
        }
    }
    commandLine(
        "node",
        nodeCapabilityVerifierFile.asFile.absolutePath,
        "--project-root",
        rootProject.projectDir.absolutePath,
        "--catalog",
        nodeCapabilityCatalogFile.asFile.absolutePath,
        "--self-test",
        "--verify-release",
        "--report",
        nodeCapabilityTruthReportFile.get().asFile.absolutePath,
    )
}

// Roadmap M0.3: verification gates are manual-only. Run them explicitly
// (e.g. `gradlew :app:verifyNodeCapabilityTruthGate`) when needed; the normal
// build/check lifecycle no longer depends on them.
tasks.matching { it.name == "verifyNodePluginRuntimeKitGate" }.configureEach {
    dependsOn(verifyNodeModuleSourceProviderResolverGate)
}
