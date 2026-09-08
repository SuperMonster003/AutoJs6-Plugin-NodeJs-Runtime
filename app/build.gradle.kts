import com.android.build.api.variant.FilterConfiguration
import com.android.apksig.ApkVerifier
import org.gradle.api.provider.Property
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
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

android {
    namespace = globalApplicationId
    compileSdk = versions.sdkVersionCompile
    testBuildType = providers.gradleProperty("nodeAndroidTestBuildType").orElse("debug").get()

    // Device tests execute the published examples from their canonical sources.
    sourceSets.getByName("androidTest").assets.directories.add(rootProject.file("sample/nodejs").path)

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

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
                arguments += "-DAUTOJS6_NODE_ENABLE_EMBEDDED_LIFECYCLE_PROBE=OFF"
                arguments += "-DAUTOJS6_NODE_ENABLE_EMBEDDED_SCRIPT_EXECUTION=ON"
                arguments += "-DAUTOJS6_NODE_RUNTIME_SLOT=node24_5"
                cppFlags += "-std=c++20"
            }
        }
    }

    lint {
        abortOnError = false
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
        // The host app loads libnode.so from this plugin with System.load(absPath).
        // Keep native libraries extracted under applicationInfo.nativeLibraryDir.
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

androidComponents {
    onVariants { variant ->
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
                        listOf("libnode.so", "libautojs6-node.so", "libc++_shared.so")
                            .map { "lib/$packagedAbi/$it" }
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
