import org.gradle.api.tasks.Exec

val nodeRuntimeOwnershipPolicy = rootProject.layout.projectDirectory.file(
    "tools/nodejs/ownership/runtime-ownership-policy.json",
)
val nodeRuntimeOwnershipVerifier = rootProject.layout.projectDirectory.file(
    "tools/nodejs/ownership/verify-runtime-ownership.js",
)
val nodeRuntimeOwnershipReport = rootProject.layout.buildDirectory.file(
    "reports/nodejs/runtime-ownership.json",
)

val verifyNodeRuntimeOwnershipGate = tasks.register<Exec>("verifyNodeRuntimeOwnershipGate") {
    group = "verification"
    description = "Fails when a plugin-owned Node runtime path is missing, duplicated, or not classified by the canonical ownership policy."

    inputs.files(nodeRuntimeOwnershipPolicy, nodeRuntimeOwnershipVerifier)
    outputs.file(nodeRuntimeOwnershipReport)
    outputs.upToDateWhen { false }
    workingDir = rootProject.projectDir

    doFirst {
        listOf(nodeRuntimeOwnershipPolicy.asFile, nodeRuntimeOwnershipVerifier.asFile).forEach { required ->
            if (!required.isFile) {
                throw GradleException("Missing Node runtime ownership input: ${required.absolutePath}")
            }
        }
    }

    commandLine(
        "node",
        nodeRuntimeOwnershipVerifier.asFile.absolutePath,
        "--repo-root",
        rootProject.projectDir.absolutePath,
        "--policy",
        nodeRuntimeOwnershipPolicy.asFile.relativeTo(rootProject.projectDir).invariantSeparatorsPath,
        "--report",
        nodeRuntimeOwnershipReport.get().asFile.relativeTo(rootProject.projectDir).invariantSeparatorsPath,
        "--self-test",
    )
}

// Roadmap M0.3: ownership verification is manual-only; `check` no longer
// depends on it. Release-oriented gates still pull it in explicitly.
val nodeRuntimeOwnershipNormalGateNames = setOf(
    "verifyNodeCapabilityTruthGate",
    "verifyNodePluginRuntimeKitRelease",
)

tasks.matching { task -> task.name in nodeRuntimeOwnershipNormalGateNames }.configureEach {
    dependsOn(verifyNodeRuntimeOwnershipGate)
}
