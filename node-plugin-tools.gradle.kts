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
