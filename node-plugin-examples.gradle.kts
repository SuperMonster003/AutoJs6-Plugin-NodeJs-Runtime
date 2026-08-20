import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File

private val nodePluginExampleKnownStatuses = setOf("stable", "partial", "disabled", "unsupported")
private val nodePluginExampleIgnoredDirectories = setOf("npm-ci-pure-js", "autojs6-pm-fixture")
private val nodePluginExampleKnownTags = setOf(
    "main-app",
    "packaged",
    "smoke",
    "commonjs",
    "node_modules",
    "scoped-fs",
    "fs-promises",
    "pure-js-npm",
    "bridge",
    "toast",
    "app",
    "requires-accessibility",
    "requires-screen-capture",
    "requires-image",
    "permission-optional",
    "security",
    "disabled-features",
    "typescript",
    "phase7",
    "esm",
    "dynamic-import",
    "network",
    "raw-network",
    "websocket",
    "ocr",
    "long-running",
    "scheduled-task",
    "worker",
    "node-test",
    "database",
    "plugin",
    "ui",
    "phase8",
    "tsx",
    "require-esm",
    "http",
    "filehandle",
    "fs-watch",
    "compile-cache",
    "inspector",
    "profiler",
    "hardened-sandbox",
    "design-gated",
    "phase9",
    "runtime-info",
    "snapshot",
    "heap-snapshot",
    "multi-instance",
    "sandbox",
    "wasm",
    "wasi",
    "wasm-worker",
    "wasm-plugin",
    "phase10",
    "storage",
    "notifications",
    "sensors",
    "package-install",
    "provider",
    "capability-truth",
    "crash",
    "adapter",
    "debug",
    "queue",
    "phase13",
    "pro-parity",
    "desktop-parity",
)

private val nodePluginExampleSupportedEntryExtensions = setOf("js", "cjs", "mjs", "ts", "cts", "mts")

private fun nodePluginExampleReadJsonObject(file: File): Map<*, *> =
    JsonSlurper().parse(file) as? Map<*, *>
        ?: throw GradleException("Expected JSON object in ${file.absolutePath}")

private fun nodePluginExampleList(value: Any?): List<String> =
    (value as? List<*>).orEmpty().map { it.toString() }

private fun nodePluginExampleMainHasNodeDirective(file: File): Boolean {
    val firstMeaningful = file.readLines()
        .map(String::trim)
        .firstOrNull { it.isNotEmpty() && !it.startsWith("//") }
        .orEmpty()
    return firstMeaningful == "\"nodejs\";" ||
            firstMeaningful == "'nodejs';" ||
            firstMeaningful == "\"node\";" ||
            firstMeaningful == "'node';"
}

private fun nodePluginExampleEntryFile(dir: File, name: String, projectJson: Map<*, *>): File {
    val entry = projectJson["main"]?.toString().orEmpty()
    if (entry.isBlank()) {
        throw GradleException("Node plugin example '$name' project.json must declare a main entry.")
    }
    if (entry.contains('\u0000') || entry.startsWith("/") || entry.startsWith("\\") || Regex("""^[A-Za-z]:""").containsMatchIn(entry)) {
        throw GradleException("Node plugin example '$name' project.json main must be a safe relative path: $entry")
    }
    val file = dir.resolve(entry).canonicalFile
    val root = dir.canonicalFile
    if (file != root && !file.toPath().startsWith(root.toPath())) {
        throw GradleException("Node plugin example '$name' project.json main escapes the example directory: $entry")
    }
    val extension = file.extension.lowercase()
    if (extension !in nodePluginExampleSupportedEntryExtensions) {
        throw GradleException("Node plugin example '$name' project.json main uses unsupported extension '.$extension'.")
    }
    return file
}

tasks.register("verifyNodePluginExamples") {
    group = "verification"
    description = "Verifies plugin-owned sample/nodejs examples, metadata, expected outputs, and smoke classifications."

    val examplesRootProvider = layout.projectDirectory.dir("sample/nodejs")
    val jsonReport = layout.buildDirectory.file("reports/nodejs/plugin-examples.json")
    val markdownReport = layout.buildDirectory.file("reports/nodejs/plugin-examples.md")

    inputs.dir(examplesRootProvider)
    outputs.file(jsonReport)
    outputs.file(markdownReport)

    doLast {
        val examplesRoot = examplesRootProvider.asFile
        val manifestFile = examplesRoot.resolve("examples.json")
        if (!manifestFile.isFile) {
            throw GradleException("Missing Node plugin example manifest: ${manifestFile.relativeTo(rootProject.projectDir)}")
        }
        val manifest = nodePluginExampleReadJsonObject(manifestFile)
        if (manifest["schema"] != "autojs6-node-examples-v1") {
            throw GradleException("Unsupported Node plugin example manifest schema: ${manifest["schema"]}")
        }
        val examples = (manifest["examples"] as? List<*>).orEmpty().map {
            it as? Map<*, *> ?: throw GradleException("Each Node plugin example manifest entry must be an object.")
        }
        val manifestNames = examples.map { it["name"].toString() }
        val duplicateNames = manifestNames.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplicateNames.isNotEmpty()) {
            throw GradleException("Duplicate Node plugin example manifest entries: ${duplicateNames.joinToString()}")
        }
        val directoryNames = examplesRoot.listFiles(File::isDirectory).orEmpty()
            .map(File::getName)
            .filterNot(nodePluginExampleIgnoredDirectories::contains)
            .sorted()
        val missingManifest = directoryNames - manifestNames.toSet()
        val missingDirectory = manifestNames.toSet() - directoryNames.toSet()
        if (missingManifest.isNotEmpty() || missingDirectory.isNotEmpty()) {
            throw GradleException(
                "Node plugin example manifest mismatch; directories missing from manifest=${missingManifest.joinToString()}, " +
                        "manifest entries missing directories=${missingDirectory.joinToString()}"
            )
        }

        val rows = mutableListOf<Map<String, Any?>>()
        examples.forEach { entry ->
            val name = entry["name"].toString()
            val status = entry["status"].toString()
            val tags = nodePluginExampleList(entry["tags"])
            val mainAppSmoke = entry["mainAppSmoke"] as? Boolean ?: false
            val packagedCompatible = entry["packagedCompatible"] as? Boolean ?: false
            if (status !in nodePluginExampleKnownStatuses) {
                throw GradleException("Node plugin example '$name' has unknown status '$status'.")
            }
            val unknownTags = tags.filterNot(nodePluginExampleKnownTags::contains)
            if (unknownTags.isNotEmpty()) {
                throw GradleException("Node plugin example '$name' has unknown tags: ${unknownTags.joinToString()}")
            }
            val dir = examplesRoot.resolve(name)
            val requiredFiles = listOf("README.md", "project.json", "package.json", "expected-output.txt")
            val missingFiles = requiredFiles.filterNot { dir.resolve(it).isFile }
            if (missingFiles.isNotEmpty()) {
                throw GradleException("Node plugin example '$name' is missing files: ${missingFiles.joinToString()}")
            }
            val readme = dir.resolve("README.md").readText()
            if (!readme.lineSequence().firstOrNull().orEmpty().contains(name)) {
                throw GradleException("Node plugin example '$name' README should start with a title containing the example name.")
            }
            val projectJson = nodePluginExampleReadJsonObject(dir.resolve("project.json"))
            if (projectJson["name"] != name || projectJson["type"] != "node") {
                throw GradleException("Node plugin example '$name' project.json must declare name='$name' and type='node'.")
            }
            val mainFile = nodePluginExampleEntryFile(dir, name, projectJson)
            if (!mainFile.isFile) {
                throw GradleException("Node plugin example '$name' is missing declared main entry: ${mainFile.relativeTo(dir)}")
            }
            if ("phase7" in tags) {
                val exampleMetadata = projectJson["example"] as? Map<*, *>
                    ?: throw GradleException("Phase 7 Node plugin example '$name' must declare project.json example metadata.")
                val capabilities = nodePluginExampleList(exampleMetadata["capabilities"])
                val limitations = nodePluginExampleList(exampleMetadata["securityLimitations"])
                val expectedProvider = exampleMetadata["expectedProvider"]?.toString().orEmpty()
                val packagedSupport = exampleMetadata["packagedSupport"]?.toString().orEmpty()
                if (capabilities.isEmpty()) {
                    throw GradleException("Phase 7 Node plugin example '$name' must declare example.capabilities.")
                }
                if (expectedProvider.isBlank()) {
                    throw GradleException("Phase 7 Node plugin example '$name' must declare example.expectedProvider.")
                }
                if (packagedSupport.isBlank()) {
                    throw GradleException("Phase 7 Node plugin example '$name' must declare example.packagedSupport.")
                }
                if (limitations.isEmpty()) {
                    throw GradleException("Phase 7 Node plugin example '$name' must declare example.securityLimitations.")
                }
            }
            val packageJson = nodePluginExampleReadJsonObject(dir.resolve("package.json"))
            val packageName = packageJson["name"]?.toString().orEmpty()
            if (!packageName.startsWith("@autojs6-sample/")) {
                throw GradleException("Node plugin example '$name' package.json name must use @autojs6-sample/ scope.")
            }
            if (mainFile.extension.lowercase() !in setOf("mjs", "mts") && !nodePluginExampleMainHasNodeDirective(mainFile)) {
                throw GradleException("Node plugin example '$name' ${mainFile.name} must start with a node directive.")
            }
            val expectedOutput = dir.resolve("expected-output.txt").readText()
            val passMarker = "sample.$name=PASS"
            if (!expectedOutput.contains(passMarker)) {
                throw GradleException("Node plugin example '$name' expected-output.txt must contain '$passMarker'.")
            }
            if (!mainFile.readText().contains(passMarker)) {
                throw GradleException("Node plugin example '$name' ${mainFile.name} must print '$passMarker'.")
            }
            if (packagedCompatible && "packaged" !in tags) {
                throw GradleException("Node plugin example '$name' is packagedCompatible but is missing the packaged tag.")
            }
            if (mainAppSmoke && "main-app" !in tags) {
                throw GradleException("Node plugin example '$name' is mainAppSmoke but is missing the main-app tag.")
            }
            rows += linkedMapOf(
                "name" to name,
                "status" to status,
                "tags" to tags,
                "mainAppSmoke" to mainAppSmoke,
                "packagedCompatible" to packagedCompatible,
                "packageName" to packageName,
            )
        }

        val report = linkedMapOf<String, Any?>(
            "schema" to "autojs6-node-plugin-examples-report-v1",
            "manifest" to manifestFile.relativeTo(rootProject.projectDir).invariantSeparatorsPath,
            "exampleCount" to rows.size,
            "mainAppSmoke" to rows.filter { it["mainAppSmoke"] == true }.map { it["name"] },
            "packagedCompatible" to rows.filter { it["packagedCompatible"] == true }.map { it["name"] },
            "examples" to rows,
        )
        jsonReport.get().asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.prettyPrint(JsonOutput.toJson(report)) + "\n")
        }
        markdownReport.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                buildString {
                    appendLine("# AutoJs6 Node Plugin Example Validation")
                    appendLine()
                    appendLine("| Example | Status | Main app smoke | Packaged | Tags |")
                    appendLine("| --- | --- | --- | --- | --- |")
                    rows.forEach { row ->
                        appendLine(
                            "| ${row["name"]} | ${row["status"]} | ${row["mainAppSmoke"]} | ${row["packagedCompatible"]} | " +
                                    "${(row["tags"] as List<*>).joinToString(", ")} |"
                        )
                    }
                }
            )
        }
    }
}
