import groovy.json.JsonSlurper

tasks.register("verifyNodePluginProjectWizardTool") {
    group = "verification"
    description = "Verifies the plugin-owned Node project wizard CLI can enumerate templates."

    val toolFile = layout.projectDirectory.file("tools/nodejs/project/autojs6-node-project.js")
    val jsonReport = layout.buildDirectory.file("reports/nodejs/project-wizard-tool.json")

    inputs.file(toolFile)
    outputs.file(jsonReport)

    doLast {
        val tool = toolFile.asFile
        if (!tool.isFile) {
            throw GradleException("Missing Node project wizard tool: ${tool.absolutePath}")
        }
        val process = ProcessBuilder("node", tool.absolutePath, "templates", "--json")
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("Node project wizard template listing failed with exit code $exitCode:\n$output")
        }
        val report = JsonSlurper().parseText(output) as? Map<*, *>
            ?: throw GradleException("Expected JSON object from Node project wizard output.")
        if (report["schema"] != "autojs6-node-project-templates-v1") {
            throw GradleException("Unexpected Node project wizard template schema: ${report["schema"]}")
        }
        val templates = report["templates"] as? List<*>
        if (templates.isNullOrEmpty()) {
            throw GradleException("Node project wizard reported no templates.")
        }
        jsonReport.get().asFile.apply {
            parentFile.mkdirs()
            writeText(output)
        }
        logger.lifecycle("Verified plugin Node project wizard tool with ${templates.size} template(s).")
    }
}

tasks.register("verifyNodePluginResolverTool") {
    group = "verification"
    description = "Verifies the plugin-owned Node resolver visualizer CLI can explain resolver traces."

    val toolFile = layout.projectDirectory.file("tools/nodejs/resolver/explain-node-resolve.js")
    val workDir = layout.buildDirectory.dir("tmp/nodejs/resolver-tool")
    val reportDir = layout.buildDirectory.dir("reports/nodejs/resolver-tool")
    val jsonReport = reportDir.map { it.file("resolve-explanation.json") }

    inputs.file(toolFile)
    outputs.file(jsonReport)

    doLast {
        val tool = toolFile.asFile
        if (!tool.isFile) {
            throw GradleException("Missing Node resolver visualizer tool: ${tool.absolutePath}")
        }
        val traceFile = workDir.get().asFile.resolve("package-exports.log")
        traceFile.parentFile.mkdirs()
        traceFile.writeText(
            """
            [resolve] request request="trace-pkg/private" from="/work/main.js"
            [resolve] node_modules search request="trace-pkg/private" from="/work/main.js" paths="/work/node_modules"
            [resolve] package.json found request="trace-pkg/private" packageDir="/work/node_modules/trace-pkg" file="/work/node_modules/trace-pkg/package.json"
            [resolve] exports blocked request="./private" module="trace-pkg/private" target="null" selectedCondition="" conditionKeys="" ignoredConditions=""
            [resolve] error request="trace-pkg/private" from="/work/main.js" code="ERR_PACKAGE_PATH_NOT_EXPORTED" autojs6Code="ERR_AUTOJS6_PACKAGE_PATH_NOT_EXPORTED" message="target=null"
            """.trimIndent() + "\n",
        )
        val reports = reportDir.get().asFile
        reports.deleteRecursively()
        val process = ProcessBuilder(
            "node",
            tool.absolutePath,
            "--mode", "json",
            "--request", "trace-pkg/private",
            "--from", "/work/main.js",
            "--trace-file", traceFile.absolutePath,
            "--report-dir", reports.absolutePath,
        )
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("Node resolver visualizer failed with exit code $exitCode:\n$output")
        }
        val report = JsonSlurper().parseText(output) as? Map<*, *>
            ?: throw GradleException("Expected JSON object from Node resolver visualizer output.")
        if (report["schema"] != "autojs6-node-resolver-visualizer-v1") {
            throw GradleException("Unexpected Node resolver visualizer schema: ${report["schema"]}")
        }
        if (report["diagnosis"] != "package-exports") {
            throw GradleException("Unexpected Node resolver diagnosis: ${report["diagnosis"]}")
        }
        if (report["traceAvailable"] != true) {
            throw GradleException("Node resolver visualizer did not consume the trace file.")
        }
        if (!jsonReport.get().asFile.isFile) {
            throw GradleException("Node resolver visualizer did not write ${jsonReport.get().asFile.absolutePath}")
        }
        logger.lifecycle("Verified plugin Node resolver visualizer tool with package-exports fixture.")
    }
}

tasks.register("verifyNodePluginRuntimeBuildTool") {
    group = "verification"
    description = "Verifies the plugin-owned Node runtime build plan assets and checker."

    val runtimeBuildDir = layout.projectDirectory.dir("tools/nodejs/runtime-build")
    val toolFile = runtimeBuildDir.file("verify-runtime-build-plan.js")
    val lockFile = runtimeBuildDir.file("runtime-build.lock.json")
    val windowsBuildScript = runtimeBuildDir.file("build-node-runtime.ps1")
    val unixBuildScript = runtimeBuildDir.file("build-node-runtime.sh")
    val dockerFile = runtimeBuildDir.file("container/Dockerfile")
    val reportDir = layout.buildDirectory.dir("reports/nodejs/runtime-build-tool")
    val jsonReport = reportDir.map { it.file("runtime-build-plan.json") }

    inputs.files(toolFile, lockFile, windowsBuildScript, unixBuildScript, dockerFile)
    outputs.file(jsonReport)

    doLast {
        val tool = toolFile.asFile
        val runtimeDir = runtimeBuildDir.asFile
        listOf(tool, lockFile.asFile, windowsBuildScript.asFile, unixBuildScript.asFile, dockerFile.asFile).forEach { file ->
            if (!file.isFile) {
                throw GradleException("Missing Node runtime build asset: ${file.absolutePath}")
            }
        }
        val reports = reportDir.get().asFile
        reports.deleteRecursively()
        val process = ProcessBuilder(
            "node",
            tool.absolutePath,
            "--repo-root", rootProject.projectDir.absolutePath,
            "--runtime-build-dir", runtimeDir.absolutePath,
            "--lock-file", lockFile.asFile.absolutePath,
            "--report-dir", reports.absolutePath,
        )
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("Node runtime build plan check failed with exit code $exitCode:\n$output")
        }
        val reportFile = jsonReport.get().asFile
        if (!reportFile.isFile) {
            throw GradleException("Node runtime build plan check did not write ${reportFile.absolutePath}")
        }
        val report = JsonSlurper().parse(reportFile) as? Map<*, *>
            ?: throw GradleException("Expected JSON object in ${reportFile.absolutePath}.")
        if (report["schema"] != "autojs6-node-runtime-build-plan-check-v1") {
            throw GradleException("Unexpected Node runtime build plan schema: ${report["schema"]}")
        }
        val lockSummary = report["lockSummary"] as? Map<*, *>
            ?: throw GradleException("Node runtime build plan report did not include lockSummary.")
        if (lockSummary["targetVersion"] != "24.17.0") {
            throw GradleException("Unexpected Node target version: ${lockSummary["targetVersion"]}")
        }
        val decision = report["decision"] as? Map<*, *>
            ?: throw GradleException("Node runtime build plan report did not include decision.")
        if (decision["status"] != "prebuilt_ready_source_build_bootstrap_only") {
            throw GradleException("Unexpected Node runtime build plan decision: ${decision["status"]}")
        }
        if (decision["artifactMaterializationStatus"] != "ready") {
            throw GradleException("Pinned Node runtime artifact materialization is not ready: ${decision["artifactMaterializationStatus"]}")
        }
        if (decision["sourceBuildStatus"] != "bootstrap_only") {
            throw GradleException("Node runtime source-build status must remain explicit bootstrap_only: ${decision["sourceBuildStatus"]}")
        }
        logger.lifecycle("Verified plugin Node runtime build plan tool with decision ${decision["status"]}.")
    }
}
