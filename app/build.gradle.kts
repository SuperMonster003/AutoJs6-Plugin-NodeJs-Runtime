import com.android.build.api.variant.FilterConfiguration
import com.android.apksig.ApkVerifier
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    id("io.github.supermonster003.autojs6-native-alignment")
    id("org.autojs.build.utils")
    id("org.autojs.build.versions")
    id("org.autojs.build.signs")
    id("org.autojs.build.jvm-convention")
    id("com.android.application")
    id("org.autojs.build.node-runtime-kit")
}

val globalApplicationId = "io.github.supermonster003.autojs6.plugin.nodejs"

val buildTypeDebug = "debug"
val buildTypeRelease = "release"
val commonPluginApiSha256 = "104004ce6f498d9928759709c373370a79f612b401998ccac1cb3fcaa35f5710"
val commonPluginApiAar = rootProject.file("libs/common-plugin-api.aar").also { artifact ->
    require(artifact.isFile) { "Missing common plugin API AAR: ${artifact.absolutePath}" }
    val digest = MessageDigest.getInstance("SHA-256")
    val actual = digest.digest(artifact.readBytes())
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    require(actual == commonPluginApiSha256) {
        "Common plugin API AAR SHA-256 mismatch: expected $commonPluginApiSha256, actual $actual"
    }
}

// Terminal launcher (libnodexe.so) and the npm / corepack asset archive; see docs/nodejs/TERMINAL.md.
// The lock is written by tools/nodejs/cli/build-node-cli-archive.py and re-checked before every build.
val nodeCliLockFile = rootProject.file("tools/nodejs/cli/node-cli.lock.json")
@Suppress("UNCHECKED_CAST")
val nodeCliLock = groovy.json.JsonSlurper().parse(nodeCliLockFile) as Map<String, Any>
@Suppress("UNCHECKED_CAST")
val nodeCliArchiveFacts = nodeCliLock.getValue("archive") as Map<String, Any>
@Suppress("UNCHECKED_CAST")
val nodeCliPackageVersions = nodeCliLock.getValue("packages") as Map<String, String>
val nodeCliArchiveFile = file("src/main/assets/${nodeCliArchiveFacts.getValue("assetPath")}")
val nodeCliExecutableName = "libnodexe.so"
val nodeCliCommands = "node,npm,npx,corepack,yarn,yarnpkg,pnpm,pnpx"
val nodeCliCmakeOutputRoot = layout.buildDirectory.dir("generated/nodexe/cmake")
val nativeLibraryNames = listOf("libnode.so", "libautojs6-node.so", "libc++_shared.so", nodeCliExecutableName)

android {
    namespace = globalApplicationId
    compileSdk = versions.sdkVersionCompile
    testBuildType = providers.gradleProperty("nodeAndroidTestBuildType").orElse("debug").get()

    // Device tests execute the published examples from their canonical sources.
    sourceSets.getByName("androidTest").assets.directories.add(rootProject.file("sample/nodejs").path)

    androidResources {
        // npm packages such as date-fns require directories beginning with an underscore.
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~"
    }

    defaultConfig {
        applicationId = globalApplicationId

        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget

        versionCode = versions.appVersionCode
        versionName = versions.appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        multiDexEnabled = true
        multiDexKeepProguard = file("multidex-keep.pro")

        buildConfigField("String", "VERSION_DATE", "\"${utils.getDateString("MMM d, yyyy", "GMT+08:00")}\"")

        // Manifest meta-data and BuildConfig share the lock so the declared archive facts,
        // the packaged asset and the runtimeInfo mirror cannot disagree.
        manifestPlaceholders += mapOf(
            "nodeCliArchive" to nodeCliArchiveFacts.getValue("assetPath"),
            "nodeCliArchiveSha256" to nodeCliArchiveFacts.getValue("sha256"),
            "nodeCliArchiveRoot" to nodeCliArchiveFacts.getValue("root"),
            "nodeCliArchiveEntryCount" to nodeCliArchiveFacts.getValue("entryCount"),
            "nodeCliArchiveBytes" to nodeCliArchiveFacts.getValue("uncompressedBytes"),
            "nodeCliNpmVersion" to nodeCliPackageVersions.getValue("npm"),
            "nodeCliCorepackVersion" to nodeCliPackageVersions.getValue("corepack"),
        )
        buildConfigField("String", "NODE_CLI_EXECUTABLE", "\"$nodeCliExecutableName\"")
        buildConfigField("String", "NODE_CLI_COMMANDS", "\"$nodeCliCommands\"")
        buildConfigField("String", "NODE_CLI_ARCHIVE", "\"${nodeCliArchiveFacts.getValue("assetPath")}\"")
        buildConfigField("String", "NODE_CLI_ARCHIVE_SHA256", "\"${nodeCliArchiveFacts.getValue("sha256")}\"")
        buildConfigField("String", "NODE_CLI_ARCHIVE_ROOT", "\"${nodeCliArchiveFacts.getValue("root")}\"")
        buildConfigField("int", "NODE_CLI_ARCHIVE_ENTRY_COUNT", "${nodeCliArchiveFacts.getValue("entryCount")}")
        buildConfigField("long", "NODE_CLI_ARCHIVE_BYTES", "${nodeCliArchiveFacts.getValue("uncompressedBytes")}L")
        buildConfigField("String", "NODE_CLI_NPM_VERSION", "\"${nodeCliPackageVersions.getValue("npm")}\"")
        buildConfigField("String", "NODE_CLI_COREPACK_VERSION", "\"${nodeCliPackageVersions.getValue("corepack")}\"")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
                arguments += "-DAUTOJS6_NODE_ENABLE_EMBEDDED_LIFECYCLE_PROBE=OFF"
                arguments += "-DAUTOJS6_NODE_ENABLE_EMBEDDED_SCRIPT_EXECUTION=ON"
                arguments += "-DAUTOJS6_NODE_RUNTIME_SLOT=node24_21"
                arguments += "-DAUTOJS6_NODE_CLI_OUTPUT_ROOT=${nodeCliCmakeOutputRoot.get().asFile.invariantSeparatorsPath}"
                cppFlags += "-std=c++20"
            }
        }
    }

    lint {
        abortOnError = true
    }

    signingConfigs {
        if (signs.isValid) {
            create(buildTypeRelease) {
                storeFile = signs.properties["storeFile"]?.let { file(it as String) }
                keyPassword = signs.properties["keyPassword"] as String
                keyAlias = signs.properties["keyAlias"] as String
                storePassword = signs.properties["storePassword"] as String
            }
        }
    }

    buildTypes {
        val proguardFiles = arrayOf<Any>(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro",
        )
        val niceSigningConfig = takeIf { signs.isValid }?.let {
            signingConfigs.getByName(buildTypeRelease)
        }
        debug {
            isMinifyEnabled = false
            proguardFiles(*proguardFiles)
            niceSigningConfig?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            proguardFiles(*proguardFiles)
            niceSigningConfig?.let { signingConfig = it }
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    @Suppress("DEPRECATION")
    packagingOptions {
        // M17.3: keep compressed downloads; direct APK mapping saves about 27-30 MiB
        // of installed code but increases each ABI download by about 50-60 MiB.
        jniLibs.useLegacyPackaging = true

        listOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE",
            "META-INF/LICENSE.*",
            "META-INF/LICENSE-notice.*",
            "META-INF/license.*",
            "META-INF/NOTICE",
            "META-INF/NOTICE.*",
            "META-INF/notice.*",
            "META-INF/ASL2.0",
            "META-INF/*.kotlin_module",
        ).let { resources.pickFirsts.addAll(it) }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
}

/**
 * AGP packages only shared-library targets from CMake, so the PIE launcher written by the
 * `nodexe` target is collected into a generated jniLibs directory per variant.
 * zh-CN: AGP 只打包 CMake 的共享库目标, 因此把 `nodexe` 目标产出的 PIE 可执行文件按变体收集到生成的 jniLibs 目录.
 */
abstract class CollectNodeCliLauncher : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val launchers: ConfigurableFileCollection

    @get:Input
    abstract val abis: ListProperty<String>

    @get:Input
    abstract val executableName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun collect() {
        val destination = outputDirectory.get().asFile
        destination.deleteRecursively()
        val byAbi = launchers.files.associateBy { it.parentFile.name }
        for (abi in abis.get()) {
            val source = byAbi[abi]
            check(source != null && source.isFile) { "CMake did not produce ${executableName.get()} for $abi" }
            check(source.inputStream().use { it.readNBytes(4) }.contentEquals(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))) {
                "${source.absolutePath} is not an ELF file"
            }
            source.copyTo(destination.resolve(abi).resolve(executableName.get()), overwrite = true)
        }
    }
}

androidComponents {
    onVariants { variant ->
        val capitalized = variant.name.replaceFirstChar { it.uppercase() }
        val collectLauncher = tasks.register<CollectNodeCliLauncher>("collect${capitalized}NodeCliLauncher") {
            dependsOn("externalNativeBuild$capitalized")
            launchers.from(fileTree(nodeCliCmakeOutputRoot) { include("*/$nodeCliExecutableName") })
            abis.set(android.defaultConfig.ndk.abiFilters.toList().sorted())
            executableName.set(nodeCliExecutableName)
        }
        variant.sources.jniLibs?.addGeneratedSourceDirectory(collectLauncher, CollectNodeCliLauncher::outputDirectory)
        variant.outputs.forEach { output ->
            val architecture = output.filters.find {
                it.filterType == FilterConfiguration.FilterType.ABI
            }?.identifier ?: "universal"
            val outputFileNameProperty = output.javaClass.methods.firstOrNull {
                it.name == "getOutputFileName" && it.parameterTypes.isEmpty()
            }?.invoke(output) as? Property<*>

            @Suppress("UNCHECKED_CAST")
            (outputFileNameProperty as? Property<String>)?.set(
                output.versionName.map { versionName ->
                    val version = versionName.replace("\\s".toRegex(), "-")
                    val extension = utils.FILE_EXTENSION_APK
                    "${rootProject.name}-v$version-$architecture.$extension".lowercase()
                }
            )
        }
    }
}

val releaseTestKotlinRuntime = configurations.create("releaseTestKotlinRuntime") {
    isCanBeConsumed = false
    isTransitive = false
}
val releaseTestKotlinJar = layout.buildDirectory.file("intermediates/release_test_runtime/kotlin-stdlib.jar")
val prepareReleaseTestKotlinRuntime = tasks.register<Copy>("prepareReleaseTestKotlinRuntime") {
    from(releaseTestKotlinRuntime)
    into(releaseTestKotlinJar.map { it.asFile.parentFile })
    rename { "kotlin-stdlib.jar" }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
    implementation("org.jetbrains:annotations:26.0.2")
    implementation(files(commonPluginApiAar))
    implementation(project(":nodejs-api"))
    testImplementation("junit:junit:4.13.2")
    // JVM tests of the Node diagnostic report parser (M12.5); Android ships org.json at runtime.
    testImplementation("org.json:json:20160810")
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.test.runner)
    // AndroidX's optional annotations are needed when R8 processes release tests.
    androidTestCompileOnly("com.google.errorprone:error_prone_annotations:2.36.0")
    if (android.testBuildType == buildTypeRelease) {
        add(releaseTestKotlinRuntime.name, "org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
        // AGP subtracts app dependencies from androidTest before R8 strips the app.
        // A distinct file avoids that subtraction by both component ID and file path.
        androidTestImplementation(files(releaseTestKotlinJar).builtBy(prepareReleaseTestKotlinRuntime))
    }
}

tasks {
    withType(JavaCompile::class.java) {
        options.encoding = "UTF-8"
    }

    register("verifyNodeCliArchive") {
        group = "verification"
        description = "Checks the packaged npm / corepack archive against tools/nodejs/cli/node-cli.lock.json"
        inputs.files(nodeCliLockFile, nodeCliArchiveFile)

        doLast {
            check(nodeCliArchiveFile.isFile) { "Missing terminal launcher archive: ${nodeCliArchiveFile.absolutePath}" }
            val expected = nodeCliArchiveFacts.getValue("sha256").toString()
            val actual = MessageDigest.getInstance("SHA-256").digest(nodeCliArchiveFile.readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            check(actual == expected) {
                "Terminal launcher archive SHA-256 mismatch for ${nodeCliArchiveFile.name}: lock $expected, actual $actual. " +
                    "Re-run tools/nodejs/cli/build-node-cli-archive.py."
            }
            ZipFile(nodeCliArchiveFile).use { zip ->
                val root = nodeCliArchiveFacts.getValue("root").toString() + "/"
                val entries = zip.entries().asSequence().map { it.name }.toList()
                check(entries.size == (nodeCliArchiveFacts.getValue("entryCount") as Number).toInt()) {
                    "Terminal launcher archive entry count drifted from the lock: ${entries.size}"
                }
                check(entries.all { it.startsWith(root) && !it.contains("..") }) { "Terminal launcher archive contains entries outside $root" }
            }
        }
    }

    named("preBuild") {
        dependsOn("verifyNodeCliArchive")
    }

    register("appendDigestToReleasedFiles") {
        group = "distribution"
        description = "Builds and verifies four signed release APKs, then appends their CRC32 digests"
        dependsOn("assembleRelease")

        doLast {
            check(signs.isValid) { "Release collection requires a valid local signing configuration." }
            val abis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            val prefix = "${rootProject.name}-v${versions.appVersionName}"
            val expected = (abis + "universal").associateBy { "$prefix-$it.apk" }
            val source = layout.buildDirectory.dir("outputs/apk/release").get().asFile
            val apks = source.listFiles { candidate -> candidate.extension == "apk" }.orEmpty()
            check(apks.map { it.name }.toSet() == expected.keys) {
                "Expected exactly ${expected.keys} in $source; found ${apks.map { it.name }}"
            }
            val collected = apks.sortedBy { it.name }.associateWith { apk ->
                val verification = ApkVerifier.Builder(apk).build().verify()
                check(verification.isVerified) { "Invalid or unsigned release APK: ${apk.name}: ${verification.errors}" }
                val abi = expected.getValue(apk.name)
                val packagedAbis = if (abi == "universal") abis else listOf(abi)
                ZipFile(apk).use { zip ->
                    val nativeLibraries = zip.entries().asSequence()
                        .filter { it.name.startsWith("lib/") && it.name.endsWith(".so") }
                        .map { it.name }.toSet()
                    val expectedLibraries = packagedAbis.flatMap { packagedAbi ->
                        nativeLibraryNames.map { "lib/$packagedAbi/$it" }
                    }.toSet()
                    check(nativeLibraries == expectedLibraries) { "Unexpected native library set in ${apk.name}: $nativeLibraries" }
                    for (name in expectedLibraries) {
                        zip.getInputStream(zip.getEntry(name)).use { stream ->
                            check(stream.readNBytes(4).contentEquals(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))) {
                                "Expected ELF bytes in ${apk.name}:$name; Git LFS pointers must be materialized."
                            }
                        }
                    }
                }
                "$prefix-$abi-${utils.digestCRC32(apk)}.apk"
            }
            val destination = file("releases/${versions.appVersionName}")
            check(destination.isDirectory || destination.mkdirs()) { "Cannot create $destination" }
            collected.forEach { (apk, name) -> apk.copyTo(destination.resolve(name), overwrite = true) }
            // Only retire superseded artifacts for this exact version after all inputs verify.
            destination.listFiles { file -> file.name.startsWith("$prefix-") && file.extension == "apk" }
                .orEmpty().filter { it.name !in collected.values }.forEach { stale ->
                    check(stale.delete()) { "Cannot remove superseded artifact: $stale" }
                }
            println("Destination: $destination")
            collected.values.forEach { println(it) }
        }
    }
}

extra {
    versions.handleIfNeeded(project, listOf(buildTypeDebug, buildTypeRelease))
}
