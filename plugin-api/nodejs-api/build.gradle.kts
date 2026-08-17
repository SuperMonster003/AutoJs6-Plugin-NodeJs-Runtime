import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Properties
import java.util.spi.ToolProvider
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

group = "org.autojs.plugin.nodejs"
version = "1.1.0"

android {
    namespace = "org.autojs.plugin.nodejs.api"

    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        minSdk = versions.sdkVersionMin
        consumerProguardFiles("consumer-rules.pro")
    }

    lint {
        targetSdk = versions.sdkVersionTarget
        abortOnError = false
    }

    buildFeatures {
        aidl = true
        buildConfig = false
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

val nodeJsApiCoordinate = "$group:${project.name}:$version"
val nodeJsApiArtifactName = "${project.name}-$version.aar"
val nodeJsApiReleaseAar = layout.buildDirectory.file("outputs/aar/${project.name}-release.aar")
val nodeJsApiPublicationDirectory = rootProject.layout.projectDirectory
    .dir("releases/nodejs-api/$version")
val nodeJsApiPublishedAar = nodeJsApiPublicationDirectory.file(nodeJsApiArtifactName)
val nodeJsApiPublicationLock = nodeJsApiPublicationDirectory.file("nodejs-api.lock")
val nodeJsApiPreviousPublicationLock = rootProject.layout.projectDirectory
    .file("releases/nodejs-api/1.0.0/nodejs-api.lock")
val nodeJsApiAidlSnapshot = layout.projectDirectory.file("compat/v1/aidl-transactions.txt")
val nodeJsApiReportDirectory = rootProject.layout.buildDirectory.dir("reports/nodejs")

fun ByteArray.nodeJsApiSha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

fun File.nodeJsApiSha256(): String = inputStream().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(256 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

fun nodeJsApiClassesJar(aar: File): ByteArray = ZipFile(aar).use { zip ->
    val entry = checkNotNull(zip.getEntry("classes.jar")) {
        "Node.js API AAR does not contain classes.jar: $aar"
    }
    zip.getInputStream(entry).use { it.readBytes() }
}

fun nodeJsApiClassEntries(classesJar: ByteArray): Map<String, ByteArray> =
    ZipInputStream(ByteArrayInputStream(classesJar)).use { zip ->
        buildMap {
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".class")) {
                    put(entry.name, zip.readBytes())
                }
            }
        }
    }

fun nodeJsApiSourceFiles(): List<File> = fileTree("src/main") {
    include("aidl/**/*.aidl", "java/**/*.kt", "java/**/*.java")
}.files.sortedBy { it.relativeTo(projectDir).invariantSeparatorsPath }

fun nodeJsApiSourceSha256(files: List<File>): String {
    val canonical = ByteArrayOutputStream()
    files.forEach { source ->
        val relative = source.relativeTo(projectDir).invariantSeparatorsPath
        val normalized = source.readText(StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        canonical.write(relative.toByteArray(StandardCharsets.UTF_8))
        canonical.write(0)
        canonical.write(normalized.toByteArray(StandardCharsets.UTF_8))
        canonical.write(0)
    }
    return canonical.toByteArray().nodeJsApiSha256()
}

fun nodeJsApiBinarySha256(entries: Map<String, ByteArray>): String {
    val canonical = buildString {
        entries.toSortedMap().forEach { (name, bytes) ->
            append(name)
            append('=')
            append(bytes.nodeJsApiSha256())
            append('\n')
        }
    }
    return canonical.toByteArray(StandardCharsets.UTF_8).nodeJsApiSha256()
}

fun nodeJsApiPublicDump(classesJar: File, classEntries: Set<String>): String {
    val classNames = classEntries
        .asSequence()
        .filter { it.startsWith("org/autojs/plugin/nodejs/api/") }
        .map { it.removeSuffix(".class").replace('/', '.') }
        .sorted()
        .toList()
    val output = StringWriter()
    val errors = StringWriter()
    val javap = ToolProvider.findFirst("javap").orElseThrow {
        GradleException("The selected JDK does not provide javap")
    }
    val arguments = listOf(
        "-public",
        "-constants",
        "-s",
        "-classpath",
        classesJar.absolutePath,
    ) + classNames
    val exitCode = javap.run(PrintWriter(output), PrintWriter(errors), *arguments.toTypedArray())
    check(exitCode == 0) {
        "javap failed for the Node.js API ($exitCode): ${errors.toString().trim()}"
    }
    return output.toString()
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trimEnd() + "\n"
}

fun nodeJsApiAidlTransactions(files: List<File>): String {
    val packagePattern = Regex("""(?m)^\s*package\s+([\w.]+)\s*;""")
    val interfacePattern = Regex("""\binterface\s+(\w+)""")
    val methodPattern = Regex(
        """(?m)^\s*(?:[\w.<>\[\]]+\s+)+(\w+)\s*\([^;]*\)\s*;\s*$""",
    )
    return files
        .filter { it.extension == "aidl" }
        .map { source ->
            val normalized = source.readText(StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
            val packageName = checkNotNull(packagePattern.find(normalized)?.groupValues?.get(1)) {
                "Missing AIDL package in $source"
            }
            val interfaceName = checkNotNull(interfacePattern.find(normalized)?.groupValues?.get(1)) {
                "Missing AIDL interface in $source"
            }
            val transactions = methodPattern.findAll(normalized)
                .mapIndexed { index, match -> "${match.groupValues[1]}=${index + 1}" }
                .toList()
            check(transactions.isNotEmpty()) { "Missing AIDL methods in $source" }
            "$packageName.$interfaceName|${transactions.joinToString(",")}"
        }
        .sorted()
        .joinToString(separator = "\n", postfix = "\n")
}

fun nodeJsApiLoadLock(file: File): Properties = Properties().apply {
    check(file.isFile) { "Node.js API publication lock is missing: $file" }
    file.inputStream().use(::load)
}

fun nodeJsApiGit(vararg arguments: String): String? = runCatching {
    val process = ProcessBuilder(listOf("git") + arguments)
        .directory(rootProject.projectDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
    check(process.waitFor() == 0) { output }
    output
}.getOrNull()

fun String.nodeJsApiJson(): String = buildString {
    append('"')
    this@nodeJsApiJson.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
    append('"')
}

val nodeJsApiExpectedClasses = buildSet {
    addAll(
        listOf(
            "NodeJsPluginActions",
            "NodeJsPluginCapabilityKeys",
            "NodeJsPluginIds",
            "NodeJsRuntimeContract",
        ).map { "org/autojs/plugin/nodejs/api/$it.class" },
    )
    listOf(
        "INodeJsHostCapabilityBroker",
        "INodeJsHostCapabilityCallback",
        "INodeJsModuleSourceProvider",
        "INodeJsRuntimeCallback",
        "INodeJsRuntimePlugin",
    ).forEach { interfaceName ->
        val prefix = "org/autojs/plugin/nodejs/api/$interfaceName"
        add("$prefix.class")
        add("$prefix\$Default.class")
        add("$prefix\$Stub.class")
        add("$prefix\$Stub\$Proxy.class")
        add("$prefix\$_Parcel.class")
    }
}

val verifyNodeJsApiPublication = tasks.register("verifyNodeJsApiPublication") {
    group = "verification"
    description = "Verifies the immutable Node.js API artifact, source, ABI, and AIDL identity."
    dependsOn("bundleReleaseAar")
    inputs.files(
        nodeJsApiPublicationLock,
        nodeJsApiPublishedAar,
        nodeJsApiPreviousPublicationLock,
        nodeJsApiAidlSnapshot,
    )
    inputs.files(nodeJsApiSourceFiles())
    inputs.file(nodeJsApiReleaseAar)
    inputs.files(rootProject.file("settings.gradle.kts"), rootProject.file("app/build.gradle.kts"))
    outputs.file(nodeJsApiReportDirectory.map { it.file("api-publication.json") })
    outputs.file(nodeJsApiReportDirectory.map { it.file("nodejs-api-public-abi.txt") })
    // This gate also checks that forbidden legacy paths do not exist. Those
    // negative inputs cannot be modeled reliably as ordinary Gradle inputs,
    // so the inexpensive verification must run on every invocation.
    outputs.upToDateWhen { false }

    doLast {
        val currentAar = nodeJsApiReleaseAar.get().asFile
        val publishedAar = nodeJsApiPublishedAar.asFile
        val lock = nodeJsApiLoadLock(nodeJsApiPublicationLock.asFile)
        check(lock.getProperty("format") == "1") { "Unsupported Node.js API publication lock format" }
        check(lock.getProperty("coordinate") == nodeJsApiCoordinate) {
            "Node.js API coordinate drift: ${lock.getProperty("coordinate")}"
        }
        check(lock.getProperty("file") == nodeJsApiArtifactName) {
            "Node.js API artifact name drift: ${lock.getProperty("file")}"
        }
        check(lock.getProperty("contract.version") == "1") { "Node.js API contract version drift" }
        check(lock.getProperty("contract.min") == "1") { "Node.js API minimum contract drift" }
        check(lock.getProperty("contract.max") == "1") { "Node.js API maximum contract drift" }
        check(lock.getProperty("module.source.provider.contract.version") == "2") {
            "Node.js API module-source provider contract version drift"
        }
        check(publishedAar.isFile) { "Published Node.js API AAR is missing: $publishedAar" }
        check(!rootProject.file("libs/nodejs-api.aar").exists()) {
            "The legacy flat libs/nodejs-api.aar must be removed after publishing the versioned API"
        }
        val duplicateAppAidlDirectory = rootProject.file("app/src/main/aidl/org/autojs/plugin/nodejs/api")
        check(
            !duplicateAppAidlDirectory.isDirectory ||
                duplicateAppAidlDirectory.walkTopDown().none(File::isFile),
        ) {
            "The app must not carry duplicate Node.js API AIDL sources"
        }
        val settingsText = rootProject.file("settings.gradle.kts").readText(StandardCharsets.UTF_8)
        check(Regex("""(?m)^\s*\":nodejs-api\",?\s*$""").containsMatchIn(settingsText)) {
            "The plugin settings must register the Node.js API producer module"
        }
        val appBuildText = rootProject.file("app/build.gradle.kts").readText(StandardCharsets.UTF_8)
        check("implementation(project(\":nodejs-api\"))" in appBuildText) {
            "The plugin app must compile against the Node.js API producer project"
        }
        check("libs/nodejs-api.aar" !in appBuildText) {
            "The plugin app must not reference the legacy flat Node.js API AAR"
        }

        val sourceFiles = nodeJsApiSourceFiles()
        val sourceSha256 = nodeJsApiSourceSha256(sourceFiles)
        val currentAarSha256 = currentAar.nodeJsApiSha256()
        val publishedAarSha256 = publishedAar.nodeJsApiSha256()
        val currentClassesJar = nodeJsApiClassesJar(currentAar)
        val publishedClassesJar = nodeJsApiClassesJar(publishedAar)
        val currentEntries = nodeJsApiClassEntries(currentClassesJar)
        val publishedEntries = nodeJsApiClassEntries(publishedClassesJar)
        val nodeEntries = currentEntries.filterKeys { it.startsWith("org/autojs/plugin/nodejs/api/") }
        check(nodeEntries.keys == nodeJsApiExpectedClasses) {
            val missing = nodeJsApiExpectedClasses - nodeEntries.keys
            val unexpected = nodeEntries.keys - nodeJsApiExpectedClasses
            "Node.js API class set drift; missing=$missing unexpected=$unexpected"
        }
        check(currentEntries.keys.none { it.startsWith("org/autojs/plugin/common/api/") }) {
            "Node.js API must not embed common-plugin-api classes"
        }
        check(currentEntries.keys == publishedEntries.keys) {
            "Published Node.js API classes differ from the source-built AAR"
        }
        val changedClasses = currentEntries.keys.filter { name ->
            !checkNotNull(currentEntries[name]).contentEquals(checkNotNull(publishedEntries[name]))
        }
        check(changedClasses.isEmpty()) {
            "Published Node.js API binary differs from the source-built AAR: $changedClasses"
        }

        val temporaryDirectory = layout.buildDirectory.dir("tmp/verifyNodeJsApiPublication").get().asFile
        temporaryDirectory.mkdirs()
        val classesJarFile = temporaryDirectory.resolve("classes.jar")
        classesJarFile.writeBytes(currentClassesJar)
        val publicDump = nodeJsApiPublicDump(classesJarFile, currentEntries.keys)
        val publicAbiSha256 = publicDump.toByteArray(StandardCharsets.UTF_8).nodeJsApiSha256()
        val binaryApiSha256 = nodeJsApiBinarySha256(nodeEntries)
        val aidlTransactions = nodeJsApiAidlTransactions(sourceFiles)
        val expectedAidlTransactions = nodeJsApiAidlSnapshot.asFile.readText(StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        check(aidlTransactions == expectedAidlTransactions) {
            "Node.js API AIDL descriptor or transaction order drifted"
        }
        val aidlTransactionsSha256 = aidlTransactions
            .toByteArray(StandardCharsets.UTF_8)
            .nodeJsApiSha256()
        val previousLock = nodeJsApiLoadLock(nodeJsApiPreviousPublicationLock.asFile)
        check(previousLock.getProperty("aidl.transactions.sha256") == aidlTransactionsSha256) {
            "Node.js API 1.1.0 must preserve the immutable 1.0.0 AIDL transaction identity"
        }
        val classesJarSha256 = currentClassesJar.nodeJsApiSha256()

        mapOf(
            "sha256" to publishedAarSha256,
            "aar.sha256" to publishedAarSha256,
            "classes.jar.sha256" to classesJarSha256,
            "source.sha256" to sourceSha256,
            "abi.sha256" to publicAbiSha256,
            "binary.api.sha256" to binaryApiSha256,
            "public.abi.sha256" to publicAbiSha256,
            "aidl.transactions.sha256" to aidlTransactionsSha256,
        ).forEach { (key, actual) ->
            check(lock.getProperty(key) == actual) {
                "Node.js API $key mismatch: expected ${lock.getProperty(key)}, actual $actual"
            }
        }
        check(currentAarSha256 == publishedAarSha256) {
            "Published Node.js API AAR is not the exact reproducible source build"
        }

        val reportDirectory = nodeJsApiReportDirectory.get().asFile
        reportDirectory.mkdirs()
        reportDirectory.resolve("nodejs-api-public-abi.txt").writeText(publicDump)
        val gitHead = nodeJsApiGit("rev-parse", "HEAD").orEmpty()
        val gitDirty = !nodeJsApiGit("status", "--porcelain", "--", "plugin-api/nodejs-api", "settings.gradle.kts", "app")
            .isNullOrBlank()
        reportDirectory.resolve("api-publication.json").writeText(
            """{
              |  "schemaVersion": 1,
              |  "decision": "completed",
              |  "coordinate": ${nodeJsApiCoordinate.nodeJsApiJson()},
              |  "artifact": ${publishedAar.relativeTo(rootProject.projectDir).invariantSeparatorsPath.nodeJsApiJson()},
              |  "aarSha256": ${publishedAarSha256.nodeJsApiJson()},
              |  "classesJarSha256": ${classesJarSha256.nodeJsApiJson()},
              |  "sourceSha256": ${sourceSha256.nodeJsApiJson()},
              |  "binaryApiSha256": ${binaryApiSha256.nodeJsApiJson()},
              |  "publicAbiSha256": ${publicAbiSha256.nodeJsApiJson()},
              |  "aidlTransactionsSha256": ${aidlTransactionsSha256.nodeJsApiJson()},
              |  "apiClassCount": ${nodeEntries.size},
              |  "contractVersion": 1,
              |  "minContractVersion": 1,
              |  "maxContractVersion": 1,
              |  "moduleSourceProviderContractVersion": 2,
              |  "aidlCompatibleWith": "1.0.0",
              |  "sourceOwner": "AutoJs6-Plugin-NodeJs-Runtime",
              |  "gitHead": ${gitHead.nodeJsApiJson()},
              |  "gitDirty": $gitDirty
              |}
            """.trimMargin(),
        )
    }
}

tasks.register("stageNodeJsApiPublication") {
    group = "release"
    description = "Stages a new immutable Node.js API version; an existing version is never overwritten."
    dependsOn("bundleReleaseAar")

    doLast {
        val publishedAar = nodeJsApiPublishedAar.asFile
        val lockFile = nodeJsApiPublicationLock.asFile
        check(!publishedAar.exists() && !lockFile.exists()) {
            "Node.js API $version is immutable. Bump the artifact version instead of replacing it."
        }
        val currentAar = nodeJsApiReleaseAar.get().asFile
        val sourceFiles = nodeJsApiSourceFiles()
        val classesJar = nodeJsApiClassesJar(currentAar)
        val entries = nodeJsApiClassEntries(classesJar)
        val nodeEntries = entries.filterKeys { it.startsWith("org/autojs/plugin/nodejs/api/") }
        val temporaryDirectory = layout.buildDirectory.dir("tmp/stageNodeJsApiPublication").get().asFile
        temporaryDirectory.mkdirs()
        val classesJarFile = temporaryDirectory.resolve("classes.jar")
        classesJarFile.writeBytes(classesJar)
        val publicDump = nodeJsApiPublicDump(classesJarFile, entries.keys)
        val aidlTransactions = nodeJsApiAidlTransactions(sourceFiles)
        check(
            aidlTransactions == nodeJsApiAidlSnapshot.asFile.readText(StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n'),
        ) { "Refusing to stage an artifact with unreviewed AIDL transaction drift" }
        val aidlTransactionsSha256 = aidlTransactions
            .toByteArray(StandardCharsets.UTF_8)
            .nodeJsApiSha256()
        val previousLock = nodeJsApiLoadLock(nodeJsApiPreviousPublicationLock.asFile)
        check(previousLock.getProperty("aidl.transactions.sha256") == aidlTransactionsSha256) {
            "Refusing to stage Node.js API 1.1.0 with AIDL transaction drift from 1.0.0"
        }

        publishedAar.parentFile.mkdirs()
        currentAar.copyTo(publishedAar)
        lockFile.writeText(
            """# Immutable Node.js runtime API distribution lock.
              |format=1
              |coordinate=$nodeJsApiCoordinate
              |file=$nodeJsApiArtifactName
              |sha256=${publishedAar.nodeJsApiSha256()}
              |aar.sha256=${publishedAar.nodeJsApiSha256()}
              |classes.jar.sha256=${classesJar.nodeJsApiSha256()}
              |source.sha256=${nodeJsApiSourceSha256(sourceFiles)}
              |binary.api.sha256=${nodeJsApiBinarySha256(nodeEntries)}
              |abi.sha256=${publicDump.toByteArray(StandardCharsets.UTF_8).nodeJsApiSha256()}
              |public.abi.sha256=${publicDump.toByteArray(StandardCharsets.UTF_8).nodeJsApiSha256()}
              |aidl.transactions.sha256=${aidlTransactions.toByteArray(StandardCharsets.UTF_8).nodeJsApiSha256()}
              |contract.version=1
              |contract.min=1
              |contract.max=1
              |module.source.provider.contract.version=2
              |source.owner=AutoJs6-Plugin-NodeJs-Runtime
              |source.repository=AutoJs6-Plugin-NodeJs-Runtime
              |source.gitBase=${nodeJsApiGit("rev-parse", "HEAD").orEmpty()}
            """.trimMargin(),
        )
    }
}

// Roadmap M0.3: publication verification is manual-only
// (`gradlew :nodejs-api:verifyNodeJsApiPublication`); `check` stays lean.
