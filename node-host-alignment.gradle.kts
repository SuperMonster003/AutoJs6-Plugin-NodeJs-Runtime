import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File

private fun nodeHostRoot(): File = providers.gradleProperty("autojs.host.root")
    .orNull
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.let(rootProject::file)
    ?.canonicalFile
    ?: rootProject.projectDir.parentFile.resolve("AutoJs6").canonicalFile

private fun nodeHostDisplayPath(file: File): String =
    runCatching { file.relativeTo(rootProject.projectDir).invariantSeparatorsPath }
        .getOrElse { file.absolutePath }

private fun nodeHostSourceInventory(root: File): Map<String, String> = root.walkTopDown()
    .filter(File::isFile)
    .associate { file ->
        file.relativeTo(root).invariantSeparatorsPath to
                file.readText(Charsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n')
    }
    .toSortedMap()

private fun nodeHostCapabilityValues(
    sourceFile: File,
    constantsPattern: Regex,
    listPattern: Regex,
): Set<String> {
    if (!sourceFile.isFile) {
        throw GradleException("Missing Node capability manifest: ${sourceFile.absolutePath}")
    }
    val source = sourceFile.readText(Charsets.UTF_8)
    val constants = constantsPattern.findAll(source)
        .associate { match -> match.groupValues[1] to match.groupValues[2] }
    val listBody = listPattern.find(source)?.groupValues?.get(1)
        ?: throw GradleException("Cannot locate the defined capability list in ${sourceFile.absolutePath}")
    val referencedConstants = Regex("\\b[A-Z][A-Z0-9_]+\\b")
        .findAll(listBody)
        .map { it.value }
        .toList()
    val unresolvedConstants = referencedConstants.filterNot(constants::containsKey)
    if (unresolvedConstants.isNotEmpty()) {
        throw GradleException(
            "Unresolved Node capability constants in ${sourceFile.absolutePath}: ${unresolvedConstants.joinToString()}",
        )
    }
    return referencedConstants.map(constants::getValue).toSortedSet()
}

val verifyNodeHostApiMirror = tasks.register("verifyNodeHostApiMirror") {
    group = "verification"
    description = "Verifies the host mirror of the plugin-owned Node.js API contract."

    val hostRoot = nodeHostRoot()
    val pluginSources = rootProject.projectDir.resolve("plugin-api/nodejs-api/src")
    val hostSources = hostRoot.resolve("plugin-api/nodejs-api/src")
    inputs.dir(pluginSources)
    inputs.dir(hostSources)

    doLast {
        if (!hostSources.isDirectory) {
            throw GradleException(
                "Missing AutoJs6 host contract mirror: ${hostSources.absolutePath}. " +
                        "Set -Pautojs.host.root=<dir> when the repositories are not siblings.",
            )
        }
        val plugin = nodeHostSourceInventory(pluginSources)
        val host = nodeHostSourceInventory(hostSources)
        val missingFromHost = (plugin.keys - host.keys).sorted()
        val extraInHost = (host.keys - plugin.keys).sorted()
        val contentMismatches = plugin.keys.intersect(host.keys).filter { plugin[it] != host[it] }.sorted()
        if (missingFromHost.isNotEmpty() || extraInHost.isNotEmpty() || contentMismatches.isNotEmpty()) {
            throw GradleException(
                "Host Node.js API mirror is out of sync: missingFromHost=$missingFromHost, " +
                        "extraInHost=$extraInHost, contentMismatches=$contentMismatches.",
            )
        }
        logger.lifecycle("Verified ${plugin.size} host-mirrored Node.js API contract files.")
    }
}

val verifyNodeHostCapabilityManifestAlignment = tasks.register("verifyNodeHostCapabilityManifestAlignment") {
    group = "verification"
    description = "Verifies host and plugin Node bridge capability manifests against the plugin-owned catalog."

    val hostRoot = nodeHostRoot()
    val hostManifest = hostRoot.resolve(
        "app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt",
    )
    val hostNodeSources = hostRoot.resolve("app/src/main/java/org/autojs/autojs/engine")
    val pluginManifest = rootProject.projectDir.resolve(
        "app/src/main/java/io/github/supermonster003/autojs6/plugin/nodejs/NodeBridgePermissionManifest.java",
    )
    val catalogFile = rootProject.projectDir.resolve(
        "releases/nodejs-capability-catalog/1.4.0/node-capability-catalog.json",
    )
    val reportFile = layout.buildDirectory.file("reports/nodejs/host-capability-manifest-alignment.json")

    inputs.file(hostManifest)
    inputs.dir(hostNodeSources)
    inputs.file(pluginManifest)
    inputs.file(catalogFile)
    outputs.file(reportFile)

    doLast {
        val hostCapabilities = nodeHostCapabilityValues(
            hostManifest,
            Regex("const\\s+val\\s+([A-Z0-9_]+)\\s*=\\s*\"([^\"]+)\""),
            Regex(
                "val\\s+definedCapabilities:\\s+List<String>\\s*=\\s*listOf\\((.*?)\\r?\\n\\s*\\)",
                RegexOption.DOT_MATCHES_ALL,
            ),
        )
        val pluginCapabilities = nodeHostCapabilityValues(
            pluginManifest,
            Regex("(?:public|private)\\s+static\\s+final\\s+String\\s+([A-Z0-9_]+)\\s*=\\s*\"([^\"]+)\""),
            Regex(
                "private\\s+static\\s+final\\s+List<String>\\s+DEFINED_CAPABILITIES\\s*=\\s*immutableList\\((.*?)\\r?\\n\\s*\\);",
                RegexOption.DOT_MATCHES_ALL,
            ),
        )

        val catalog = JsonSlurper().parse(catalogFile) as? Map<*, *>
            ?: throw GradleException("Expected JSON object in ${catalogFile.absolutePath}")
        val bridge = catalog["bridge"] as? Map<*, *>
            ?: throw GradleException("Missing bridge object in ${catalogFile.absolutePath}")
        val permissionEntries = bridge["permissionCapabilities"] as? List<*>
            ?: throw GradleException("Missing bridge.permissionCapabilities in ${catalogFile.absolutePath}")
        val catalogCapabilities = permissionEntries.mapIndexed { index, rawEntry ->
            val entry = rawEntry as? Map<*, *>
                ?: throw GradleException("bridge.permissionCapabilities[$index] must be an object")
            entry["id"]?.toString()?.takeIf(String::isNotBlank)
                ?: throw GradleException("bridge.permissionCapabilities[$index].id must be non-empty")
        }.toSortedSet()
        val operations = bridge["operations"] as? List<*>
            ?: throw GradleException("Missing bridge.operations in ${catalogFile.absolutePath}")
        val operationCapabilities = operations.flatMap { rawOperation ->
            val operation = rawOperation as? Map<*, *>
                ?: throw GradleException("Every bridge operation must be an object")
            (operation["requiredCapabilities"] as? List<*>).orEmpty().map(Any?::toString)
        }.toSortedSet()

        val differences = buildList {
            (hostCapabilities - pluginCapabilities).forEach { add("host_only:$it") }
            (pluginCapabilities - hostCapabilities).forEach { add("plugin_only:$it") }
            (pluginCapabilities - catalogCapabilities).forEach { add("catalog_missing:$it") }
            (catalogCapabilities - pluginCapabilities).forEach { add("catalog_only:$it") }
            (operationCapabilities - catalogCapabilities).forEach { add("catalog_operation_undefined:$it") }
        }.sorted()
        val dottedCapabilities = hostCapabilities.filter { '.' in it }
        val quotedCapability = dottedCapabilities.takeIf { it.isNotEmpty() }?.let { capabilities ->
            Regex("\"(" + capabilities.sortedByDescending(String::length).joinToString("|") { Regex.escape(it) } + ")\"")
        }
        val hostManifestPath = hostManifest.canonicalFile
        val hostLiteralOccurrences = hostNodeSources.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" && file.canonicalFile != hostManifestPath }
            .flatMap { file ->
                file.readLines(Charsets.UTF_8).asSequence().mapIndexedNotNull { index, line ->
                    quotedCapability?.find(line)?.let { match ->
                        "${file.relativeTo(hostRoot).invariantSeparatorsPath}:${index + 1}:${match.groupValues[1]}"
                    }
                }
            }
            .sorted()
            .toList()
        val matches = differences.isEmpty() && hostLiteralOccurrences.isEmpty()
        val report = linkedMapOf<String, Any?>(
            "schema" to "autojs6-node-host-capability-manifest-alignment-v1",
            "hostRoot" to hostRoot.absolutePath,
            "hostManifest" to hostManifest.absolutePath,
            "pluginManifest" to nodeHostDisplayPath(pluginManifest),
            "catalog" to nodeHostDisplayPath(catalogFile),
            "hostCapabilityCount" to hostCapabilities.size,
            "pluginCapabilityCount" to pluginCapabilities.size,
            "catalogCapabilityCount" to catalogCapabilities.size,
            "matches" to matches,
            "differences" to differences,
            "hostDottedCapabilityLiteralsOutsideManifest" to hostLiteralOccurrences,
        )
        reportFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.prettyPrint(JsonOutput.toJson(report)) + "\n")
        }
        if (!matches) {
            throw GradleException(
                "Node capability manifests are not aligned: differences=$differences, " +
                        "hostLiterals=$hostLiteralOccurrences. See ${reportFile.get().asFile.absolutePath}.",
            )
        }
        logger.lifecycle(
            "Verified Node capability manifests: host=${hostCapabilities.size}, " +
                    "plugin=${pluginCapabilities.size}, catalog=${catalogCapabilities.size}.",
        )
    }
}

tasks.register("verifyNodeHostIntegration") {
    group = "verification"
    description = "Runs plugin-owned alignment checks against the AutoJs6 host repository."
    dependsOn(verifyNodeHostApiMirror, verifyNodeHostCapabilityManifestAlignment)
}
