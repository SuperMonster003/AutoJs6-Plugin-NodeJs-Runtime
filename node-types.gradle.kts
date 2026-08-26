import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.tasks.Exec
import java.io.File

private val autoJs6NodeTypeDeclarationModules = listOf(
    "toast",
    "app",
    "dialogs",
    "clipboard",
    "device",
    "shell",
    "engines",
    "accessibility",
    "media_projection",
    "image",
    "images",
    "ocr",
    "storage",
    "storages",
    "database",
    "sqlite",
    "console",
    "timers",
    "files",
    "base64",
    "colors",
    "formatter",
    "converter",
    "s13n",
    "mime",
    "nanoid",
    "util",
    "opencc",
    "pinyin",
    "pinyin4j",
    "rhino",
    "notifications",
    "sensors",
    "fetch",
    "axios",
    "websocket",
    "work_manager",
    "plugins",
)

private fun nodeTypesDisplayPath(file: File): String =
    runCatching { file.relativeTo(rootProject.projectDir).invariantSeparatorsPath }
        .getOrElse { file.absolutePath }

private fun nodeTypesReadJsonObject(file: File): Map<*, *> =
    JsonSlurper().parse(file) as? Map<*, *>
        ?: throw GradleException("Expected JSON object in ${file.absolutePath}")

private fun nodeTypesStringList(value: Any?): List<String> =
    (value as? List<*>).orEmpty().map(Any?::toString)

val verifyAutoJs6NodeTypeDeclarations = tasks.register("verifyAutoJs6NodeTypeDeclarations") {
    group = "verification"
    description = "Verifies plugin-owned AutoJs6 Node TypeScript declarations and the TypeScript smoke sample."

    val examplesRoot = rootProject.projectDir.resolve("sample/nodejs")
    val typesRoot = rootProject.projectDir.resolve("docs/nodejs/types/autojs6-node")
    val sampleRoot = examplesRoot.resolve("typescript-smoke")
    val examplesManifestFile = examplesRoot.resolve("examples.json")
    val jsonReport = layout.buildDirectory.file("reports/nodejs/typescript-declarations.json")
    val markdownReport = layout.buildDirectory.file("reports/nodejs/typescript-declarations.md")

    inputs.dir(typesRoot)
    inputs.dir(sampleRoot)
    inputs.file(examplesManifestFile)
    outputs.file(jsonReport)
    outputs.file(markdownReport)

    doLast {
        val indexFile = typesRoot.resolve("index.d.ts")
        val commonFile = typesRoot.resolve("common.d.ts")
        val profileCapabilitiesFile = typesRoot.resolve("profile_capabilities.d.ts")
        val sampleTsConfig = sampleRoot.resolve("tsconfig.json")
        val sampleSource = sampleRoot.resolve("src/main.ts")
        val requiredFiles = listOf(indexFile, commonFile, profileCapabilitiesFile, sampleTsConfig, sampleSource)
        val missingFiles = requiredFiles.filterNot(File::isFile).map(::nodeTypesDisplayPath)
        if (missingFiles.isNotEmpty()) {
            throw GradleException("Missing AutoJs6 Node TypeScript required file(s): ${missingFiles.joinToString()}")
        }

        val indexText = indexFile.readText()
        val profileCapabilitiesText = profileCapabilitiesFile.readText()
        val sampleText = sampleSource.readText()
        val tsConfigText = sampleTsConfig.readText()
        val rows = autoJs6NodeTypeDeclarationModules.map { moduleName ->
            val declaration = typesRoot.resolve("$moduleName.d.ts")
            if (!declaration.isFile) {
                throw GradleException("Missing TypeScript declaration for Node bridge module '$moduleName'.")
            }
            val declarationText = declaration.readText()
            if (!declarationText.contains("""declare module "$moduleName"""")) {
                throw GradleException("${nodeTypesDisplayPath(declaration)} must declare module \"$moduleName\".")
            }
            if (!indexText.contains("$moduleName.d.ts")) {
                throw GradleException("index.d.ts must reference $moduleName.d.ts.")
            }
            if (!sampleText.contains("""require("$moduleName")""")) {
                throw GradleException("typescript-smoke sample must import '$moduleName'.")
            }
            linkedMapOf(
                "module" to moduleName,
                "declaration" to nodeTypesDisplayPath(declaration),
                "bytes" to declaration.length(),
            )
        }

        if (!tsConfigText.contains("../../../docs/nodejs/types/autojs6-node/**/*.d.ts")) {
            throw GradleException("typescript-smoke tsconfig.json must include the plugin-owned AutoJs6 Node declarations.")
        }
        if (!indexText.contains("profile_capabilities.d.ts")) {
            throw GradleException("index.d.ts must reference profile_capabilities.d.ts.")
        }
        if (!profileCapabilitiesText.contains("ProfileModuleCatalog") ||
            !profileCapabilitiesText.contains("ProfileModuleModeFor") ||
            !profileCapabilitiesText.contains("desktop_compat_opt_in") ||
            !profileCapabilitiesText.contains("NodeProfileV12CapabilityCatalog") ||
            !profileCapabilitiesText.contains("NodeProfileV12DeclarationMatrix")
        ) {
            throw GradleException("profile_capabilities.d.ts must declare the profile-aware module catalog and conditional helpers.")
        }
        if (!sampleText.contains("""ProfileModuleModeFor<"java", "safe_default">""") ||
            !sampleText.contains("""ProfileModuleModeFor<"worker_threads", "desktop_compat_opt_in">""") ||
            !sampleText.contains("ProfileDeclarationMatrix") ||
            !sampleText.contains("""NodeProfileV12ModeFor<"ui.overlay", "pro_compat_opt_in">""") ||
            !sampleText.contains("NodeProfileV12DeclarationMatrix")
        ) {
            throw GradleException("typescript-smoke sample must cover Safe/Pro/Desktop profile declaration differences.")
        }

        val examplesManifest = nodeTypesReadJsonObject(examplesManifestFile)
        val examples = (examplesManifest["examples"] as? List<*>).orEmpty().map {
            it as? Map<*, *> ?: throw GradleException("Each Node example manifest entry must be an object.")
        }
        val typeSmoke = examples.singleOrNull { it["name"] == "typescript-smoke" }
            ?: throw GradleException("${nodeTypesDisplayPath(examplesManifestFile)} must include the typescript-smoke example.")
        if ("typescript" !in nodeTypesStringList(typeSmoke["tags"])) {
            throw GradleException("typescript-smoke example must carry the 'typescript' tag.")
        }

        val report = linkedMapOf<String, Any?>(
            "schema" to "autojs6-node-types-report-v1",
            "owner" to "AutoJs6-Plugin-NodeJs-Runtime",
            "typesRoot" to nodeTypesDisplayPath(typesRoot),
            "sample" to nodeTypesDisplayPath(sampleRoot),
            "examplesManifest" to nodeTypesDisplayPath(examplesManifestFile),
            "moduleCount" to rows.size,
            "modules" to rows,
        )
        jsonReport.get().asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.prettyPrint(JsonOutput.toJson(report)) + "\n")
        }
        markdownReport.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                buildString {
                    appendLine("# AutoJs6 Node TypeScript Declaration Validation")
                    appendLine()
                    appendLine("| Module | Declaration | Bytes |")
                    appendLine("| --- | --- | ---: |")
                    rows.forEach { row ->
                        appendLine("| ${row["module"]} | ${row["declaration"]} | ${row["bytes"]} |")
                    }
                },
            )
        }
        logger.lifecycle("Verified ${rows.size} plugin-owned AutoJs6 Node TypeScript declaration modules.")
    }
}

val generateNodeTypescriptSmokeTsconfig = tasks.register("generateNodeTypescriptSmokeTsconfig") {
    group = "verification"
    description = "Generates a tsconfig for the plugin-owned Node TypeScript smoke sample."

    val sampleRoot = rootProject.projectDir.resolve("sample/nodejs/typescript-smoke")
    val typesRoot = rootProject.projectDir.resolve("docs/nodejs/types/autojs6-node")
    val generatedTsconfig = layout.buildDirectory.file("generated/nodejs/typescript-smoke/tsconfig.json")

    inputs.file(sampleRoot.resolve("tsconfig.json"))
    inputs.dir(sampleRoot.resolve("src"))
    inputs.dir(typesRoot)
    outputs.file(generatedTsconfig)

    doLast {
        val sourceInclude = sampleRoot.resolve("src").absolutePath.replace('\\', '/') + "/**/*.ts"
        val typesInclude = typesRoot.absolutePath.replace('\\', '/') + "/**/*.d.ts"
        val tsconfig = linkedMapOf<String, Any?>(
            "compilerOptions" to linkedMapOf(
                "target" to "ES2020",
                "module" to "CommonJS",
                "moduleResolution" to "node",
                "strict" to true,
                "noEmit" to true,
                "skipLibCheck" to false,
                "lib" to listOf("ES2020"),
                "types" to emptyList<String>(),
            ),
            "include" to listOf(sourceInclude, typesInclude),
        )
        generatedTsconfig.get().asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.prettyPrint(JsonOutput.toJson(tsconfig)) + "\n")
        }
    }
}

tasks.register<Exec>("typeCheckNodeTypescriptSmoke") {
    group = "verification"
    description = "Runs tsc against the plugin-owned Node TypeScript smoke sample and declarations."
    dependsOn(verifyAutoJs6NodeTypeDeclarations, generateNodeTypescriptSmokeTsconfig)

    val tsconfig = layout.buildDirectory.file("generated/nodejs/typescript-smoke/tsconfig.json")
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        commandLine("cmd", "/c", "tsc", "-p", tsconfig.get().asFile.absolutePath, "--pretty", "false")
    } else {
        commandLine("tsc", "-p", tsconfig.get().asFile.absolutePath, "--pretty", "false")
    }
}
