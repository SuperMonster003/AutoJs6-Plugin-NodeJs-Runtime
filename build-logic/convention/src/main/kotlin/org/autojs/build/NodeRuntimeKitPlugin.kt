package org.autojs.build

import groovy.json.JsonSlurper
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.security.MessageDigest

/**
 * Reads the Runtime Kit manifest and exposes its identity as BuildConfig fields
 * consumed by the plugin info and runtime services.
 *
 * This used to be an `apply(from = "node-runtime-kit.gradle.kts")` fragment.
 * Moving it into build-logic keeps the Android plugin classpath available after
 * the settings build adopts the shared platform-version resolver.
 */
class NodeRuntimeKitPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.plugins.withId("com.android.application") {
            configureRuntimeKitBuildConfig(project)
        }
    }

    private fun configureRuntimeKitBuildConfig(project: Project) {
        val manifestFile = project.layout.projectDirectory
            .file("src/main/assets/nodejs/node-plugin-runtime-kit.json")
            .asFile

        @Suppress("UNCHECKED_CAST")
        val manifest = JsonSlurper().parse(manifestFile) as Map<String, Any?>
        val fields = linkedMapOf(
            "NODE_PLUGIN_RUNTIME_KIT_SCHEMA" to manifest.getValue("schema").toString(),
            "NODE_PLUGIN_RUNTIME_KIT_VERSION" to manifest.getValue("kitVersion").toString(),
            "NODE_PLUGIN_RUNTIME_KIT_SHA256" to sha256Of(manifestFile),
            "NODE_PLUGIN_RUNTIME_KIT_ID" to manifest.getValue("kitId").toString(),
        )

        val android = project.extensions.findByName("android")
            ?: error("The android extension is missing while configuring the Runtime Kit BuildConfig fields")
        val defaultConfig = android.javaClass.methods
            .first { it.name == "getDefaultConfig" && it.parameterCount == 0 }
            .invoke(android)
        val buildConfigField = defaultConfig.javaClass.methods
            .first { it.name == "buildConfigField" && it.parameterCount == 3 }

        fields.forEach { (name, value) ->
            buildConfigField.invoke(defaultConfig, "String", name, "\"$value\"")
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
