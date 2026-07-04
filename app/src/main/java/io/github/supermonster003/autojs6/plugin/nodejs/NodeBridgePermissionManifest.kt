package io.github.supermonster003.autojs6.plugin.nodejs

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale

object NodeBridgePermissionManifest {

    const val RUNTIME_MODULE_NAME = "autojs6:bridge-permissions"

    private const val TOAST = "toast"
    private const val APP = "app"
    private const val APP_LAUNCH = "app.launch"
    private const val APP_SETTINGS = "app.settings"
    private const val APP_ACTIVITY = "app.activity"
    private const val APP_QUERY = "app.query"
    private const val DIALOGS = "dialogs"
    private const val ENGINES = "engines"
    private const val ENGINES_EXEC = "engines.exec"
    private const val ACCESSIBILITY = "accessibility"
    private const val SCREEN_CAPTURE = "screen_capture"
    private const val IMAGE = "image"
    private const val OCR = "ocr"
    private const val BARCODE = "barcode"
    private const val MEDIA = "media"
    private const val MEDIA_AUDIO = "media.audio"
    private const val MEDIA_METADATA = "media.metadata"
    private const val MEDIA_RECORDING = "media.recording"
    private const val STORAGE = "storage"
    private const val DATABASE = "database"
    private const val NOTIFICATIONS = "notifications"
    private const val NOTIFICATIONS_SETTINGS = "notifications.settings"
    private const val SENSORS = "sensors"
    private const val UI = "ui"
    private const val UI_OVERLAY = "ui.overlay"
    private const val UI_OVERLAY_PERMISSION = "ui.overlay.permission"
    private const val UI_OVERLAY_FOREGROUND = "ui.overlay.foreground"
    private const val UI_OVERLAY_PACKAGED = "ui.overlay.packaged"
    private const val CLIPBOARD = "clipboard"
    private const val DEVICE = "device"
    private const val DEVICE_POWER = "device.power"
    private const val SHELL = "shell"
    private const val SHELL_ROOT = "shell.root"
    private const val SHELL_SHIZUKU = "shell.shizuku"
    private const val NETWORK = "network"
    private const val RAW_NETWORK = "raw_network"
    private const val WORK_MANAGER = "work_manager"
    private const val PACKAGE_MANAGER = "package_manager"
    private const val PACKAGE_MANAGER_MUTATE = "package_manager.mutate"
    private const val JAVA_INTEROP = "java_interop"
    private const val RHINO = "rhino"
    private const val INPUT_OBSERVER = "input_observer"
    private const val INPUT_OBSERVER_KEYS = "input_observer.keys"
    private const val INPUT_OBSERVER_TOUCH = "input_observer.touch"
    private const val INPUT_OBSERVER_INTERCEPT = "input_observer.intercept"
    private const val FLOATY_RAW = "floaty.raw"
    private const val FLOATY_TOUCH = "floaty.touch"

    private const val ANDROID_PERMISSION_INTERNET = "android.permission.INTERNET"
    private const val ANDROID_PERMISSION_FOREGROUND_SERVICE = "android.permission.FOREGROUND_SERVICE"
    private const val ANDROID_PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION =
        "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"
    private const val ANDROID_PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE =
        "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
    private const val ANDROID_PERMISSION_FOREGROUND_SERVICE_MICROPHONE =
        "android.permission.FOREGROUND_SERVICE_MICROPHONE"
    private const val ANDROID_PERMISSION_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    private const val ANDROID_PERMISSION_RECORD_AUDIO = "android.permission.RECORD_AUDIO"

    private val definedCapabilities: List<String> = listOf(
        TOAST,
        APP,
        APP_LAUNCH,
        APP_SETTINGS,
        APP_ACTIVITY,
        APP_QUERY,
        DIALOGS,
        ENGINES,
        ENGINES_EXEC,
        ACCESSIBILITY,
        SCREEN_CAPTURE,
        IMAGE,
        OCR,
        BARCODE,
        MEDIA,
        MEDIA_AUDIO,
        MEDIA_METADATA,
        MEDIA_RECORDING,
        STORAGE,
        DATABASE,
        NOTIFICATIONS,
        NOTIFICATIONS_SETTINGS,
        SENSORS,
        UI,
        UI_OVERLAY,
        UI_OVERLAY_PERMISSION,
        UI_OVERLAY_FOREGROUND,
        UI_OVERLAY_PACKAGED,
        CLIPBOARD,
        DEVICE,
        DEVICE_POWER,
        SHELL,
        SHELL_ROOT,
        SHELL_SHIZUKU,
        NETWORK,
        RAW_NETWORK,
        WORK_MANAGER,
        PACKAGE_MANAGER,
        PACKAGE_MANAGER_MUTATE,
        JAVA_INTEROP,
        RHINO,
        INPUT_OBSERVER,
        INPUT_OBSERVER_KEYS,
        INPUT_OBSERVER_TOUCH,
        INPUT_OBSERVER_INTERCEPT,
        FLOATY_RAW,
        FLOATY_TOUCH,
    )

    private val definedCapabilitySet = definedCapabilities.toSet()

    private val aliases = mapOf(
        "media_projection" to SCREEN_CAPTURE,
        "media.projection" to SCREEN_CAPTURE,
        "screen.capture" to SCREEN_CAPTURE,
        "screencapture" to SCREEN_CAPTURE,
        "qrcode" to BARCODE,
        "qr_code" to BARCODE,
        "qr-code" to BARCODE,
        "barcode.detect" to BARCODE,
        "barcode.detectall" to BARCODE,
        "barcode.recognizetext" to BARCODE,
        "barcode.recognizetexts" to BARCODE,
        "qrcode.detect" to BARCODE,
        "qrcode.detectall" to BARCODE,
        "qrcode.recognizetext" to BARCODE,
        "qrcode.recognizetexts" to BARCODE,
        "audio" to MEDIA_AUDIO,
        "media:audio" to MEDIA_AUDIO,
        "media-audio" to MEDIA_AUDIO,
        "media.metadata" to MEDIA_METADATA,
        "media:metadata" to MEDIA_METADATA,
        "mediainfo" to MEDIA_METADATA,
        "media-info" to MEDIA_METADATA,
        "media_info" to MEDIA_METADATA,
        "app-query" to APP_QUERY,
        "app:query" to APP_QUERY,
        "package-query" to APP_QUERY,
        "package.query" to APP_QUERY,
        "media.record" to MEDIA_RECORDING,
        "media:recording" to MEDIA_RECORDING,
        "media-recording" to MEDIA_RECORDING,
        "recorder" to MEDIA_RECORDING,
        "audio.recording" to MEDIA_RECORDING,
        "notification" to NOTIFICATIONS,
        "notification.settings" to NOTIFICATIONS_SETTINGS,
        "notifications:settings" to NOTIFICATIONS_SETTINGS,
        "notifications-settings" to NOTIFICATIONS_SETTINGS,
        "device:power" to DEVICE_POWER,
        "device-power" to DEVICE_POWER,
        "power-manager" to DEVICE_POWER,
        "power_manager" to DEVICE_POWER,
        "engines:exec" to ENGINES_EXEC,
        "engine.exec" to ENGINES_EXEC,
        "shell:root" to SHELL_ROOT,
        "shell-root" to SHELL_ROOT,
        "shell:shizuku" to SHELL_SHIZUKU,
        "shell-shizuku" to SHELL_SHIZUKU,
        "storages" to STORAGE,
        "sqlite" to DATABASE,
        "package-manager" to PACKAGE_MANAGER,
        "package.manager" to PACKAGE_MANAGER,
        "android_package_manager" to PACKAGE_MANAGER,
        "android-package-manager" to PACKAGE_MANAGER,
        "package-manager.mutate" to PACKAGE_MANAGER_MUTATE,
        "package.manager.mutate" to PACKAGE_MANAGER_MUTATE,
        "android_package_manager.mutate" to PACKAGE_MANAGER_MUTATE,
        "android-package-manager.mutate" to PACKAGE_MANAGER_MUTATE,
        "java" to JAVA_INTEROP,
        "rhino.run" to RHINO,
        "input-observer" to INPUT_OBSERVER,
        "input.observer" to INPUT_OBSERVER,
        "inputobserver" to INPUT_OBSERVER,
        "input-observer.keys" to INPUT_OBSERVER_KEYS,
        "input.observer.keys" to INPUT_OBSERVER_KEYS,
        "input-observer.touch" to INPUT_OBSERVER_TOUCH,
        "input.observer.touch" to INPUT_OBSERVER_TOUCH,
        "input-observer.intercept" to INPUT_OBSERVER_INTERCEPT,
        "input.observer.intercept" to INPUT_OBSERVER_INTERCEPT,
        "ui-overlay" to UI_OVERLAY,
        "ui:overlay" to UI_OVERLAY,
        "overlay" to UI_OVERLAY,
        "overlay.permission" to UI_OVERLAY_PERMISSION,
        "ui-overlay.permission" to UI_OVERLAY_PERMISSION,
        "ui:overlay.permission" to UI_OVERLAY_PERMISSION,
        "overlay.foreground" to UI_OVERLAY_FOREGROUND,
        "ui-overlay.foreground" to UI_OVERLAY_FOREGROUND,
        "overlay.packaged" to UI_OVERLAY_PACKAGED,
        "ui-overlay.packaged" to UI_OVERLAY_PACKAGED,
        "raw.network" to RAW_NETWORK,
        "raw-network" to RAW_NETWORK,
        "raw.node.network" to RAW_NETWORK,
        "raw-node-network" to RAW_NETWORK,
        "raw_node_network_modules" to RAW_NETWORK,
    )

    private val androidPermissionsByCapability = linkedMapOf(
        NOTIFICATIONS to listOf(ANDROID_PERMISSION_POST_NOTIFICATIONS),
        MEDIA_RECORDING to listOf(
            ANDROID_PERMISSION_RECORD_AUDIO,
            ANDROID_PERMISSION_FOREGROUND_SERVICE,
            ANDROID_PERMISSION_FOREGROUND_SERVICE_MICROPHONE,
        ),
        NETWORK to listOf(ANDROID_PERMISSION_INTERNET),
        RAW_NETWORK to listOf(ANDROID_PERMISSION_INTERNET),
        SCREEN_CAPTURE to listOf(
            ANDROID_PERMISSION_FOREGROUND_SERVICE,
            ANDROID_PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION,
            ANDROID_PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE,
        ),
    )

    private val androidPermissionOrder = listOf(
        ANDROID_PERMISSION_POST_NOTIFICATIONS,
        ANDROID_PERMISSION_RECORD_AUDIO,
        ANDROID_PERMISSION_INTERNET,
        ANDROID_PERMISSION_FOREGROUND_SERVICE,
        ANDROID_PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION,
        ANDROID_PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE,
        ANDROID_PERMISSION_FOREGROUND_SERVICE_MICROPHONE,
    )

    fun runtimeModuleSourceForWorkingDirectory(
        workingDirectory: String?,
        sandboxRoot: String?,
        includeBuildNetworkPolicy: Boolean,
    ): String = fromRuntimeWorkingDirectory(
        workingDirectory = workingDirectory,
        sandboxRoot = sandboxRoot,
        includeBuildNetworkPolicy = includeBuildNetworkPolicy,
    ).toJson()

    private fun fromRuntimeWorkingDirectory(
        workingDirectory: String?,
        sandboxRoot: String?,
        includeBuildNetworkPolicy: Boolean,
    ): Manifest {
        val primary = fromWorkingDirectory(workingDirectory)
        val fallback = sandboxRoot
            ?.takeIf { it.isNotBlank() && it != workingDirectory }
            ?.let { fromWorkingDirectory(it) }
            ?: Manifest.empty()
        if (!includeBuildNetworkPolicy && !fallback.enforced) {
            return primary
        }
        val permissions = (
                primary.permissions +
                        fallback.permissions +
                        if (includeBuildNetworkPolicy) listOf(NETWORK) else emptyList()
                )
            .map(::normalizeCapability)
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        val sources = (
                primary.sources.filterNot { it == "none" } +
                        fallback.sources.filterNot { it == "none" } +
                        if (includeBuildNetworkPolicy) listOf("gradle:autojs.nodejs.network.experimental") else emptyList()
                )
            .distinct()
        return buildManifest(
            enforced = primary.enforced || fallback.enforced || includeBuildNetworkPolicy,
            permissions = permissions,
            sources = sources,
            warnings = (primary.warnings + fallback.warnings).distinct(),
        )
    }

    private fun fromWorkingDirectory(workingDirectory: String?): Manifest {
        val root = workingDirectory
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { File(it).canonicalFile }.getOrNull() }
            ?: return Manifest.empty()
        return fromJsonSources(
            projectJsonText = readTextIfFile(File(root, PROJECT_JSON)),
            packageJsonText = readTextIfFile(File(root, PACKAGE_JSON)),
        )
    }

    private fun fromJsonSources(projectJsonText: String?, packageJsonText: String?): Manifest {
        val sources = mutableListOf<PermissionSource>()
        val warnings = mutableListOf<String>()
        val packagedMetadata = PackagedMetadataBuilder()
        projectJsonText?.let { text ->
            val json = parseJsonObject(text, PROJECT_JSON, warnings)
            if (json != null) {
                collectProjectJsonPermissions(json, sources, warnings)
                collectProjectJsonPackagedMetadata(json, packagedMetadata)
            }
        }
        packageJsonText?.let { text ->
            val json = parseJsonObject(text, PACKAGE_JSON, warnings)
            if (json != null) {
                collectPackageJsonPermissions(json, sources, warnings)
                collectPackageJsonPackagedMetadata(json, packagedMetadata)
            }
        }
        val permissions = sources
            .flatMap { it.permissions }
            .map(::normalizeCapability)
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        return buildManifest(
            enforced = sources.isNotEmpty(),
            permissions = permissions,
            sources = sources.map { it.name }.distinct(),
            warnings = warnings,
            packagedMetadata = packagedMetadata,
        )
    }

    private fun normalizeCapability(value: String): String {
        val normalized = value
            .trim()
            .lowercase(Locale.US)
            .replace(':', '.')
        return aliases[normalized] ?: normalized
    }

    private fun androidPermissionsForCapabilities(capabilities: Iterable<String>): List<String> {
        val values = capabilities
            .map(::normalizeCapability)
            .flatMap { capability -> androidPermissionsByCapability[capability].orEmpty() }
            .distinct()
        return values.sortedWith(
            compareBy(
                { androidPermissionOrder.indexOf(it).let { index -> if (index < 0) Int.MAX_VALUE else index } },
                { it },
            ),
        )
    }

    private fun buildManifest(
        enforced: Boolean,
        permissions: List<String>,
        sources: List<String>,
        warnings: List<String>,
        packagedMetadata: PackagedMetadataBuilder = PackagedMetadataBuilder(),
    ): Manifest {
        val unknown = permissions.filterNot { it in definedCapabilitySet }.sorted()
        val normalizedPermissions = permissions.map(::normalizeCapability).filter { it.isNotEmpty() }.distinct()
        return Manifest(
            version = 1,
            enforced = enforced,
            permissions = permissions,
            sources = sources.ifEmpty { listOf("none") },
            knownCapabilities = definedCapabilities,
            unknownPermissions = unknown,
            androidPermissions = androidPermissionsForCapabilities(permissions),
            serviceRequired = permissions.isNotEmpty(),
            warnings = (warnings + packagedMetadata.warnings + unknown.map { "Unknown Node bridge capability: $it" }).distinct(),
            packagedMetadata = packagedMetadata.build(normalizedPermissions),
        )
    }

    private fun collectProjectJsonPermissions(
        json: JSONObject,
        sources: MutableList<PermissionSource>,
        warnings: MutableList<String>,
    ) {
        val node = json.optJSONObject("node") ?: return
        collectArray(node, "permissions", "project.json:node.permissions", sources, warnings)
        collectArray(node, "bridgePermissions", "project.json:node.bridgePermissions", sources, warnings)
    }

    private fun collectProjectJsonPackagedMetadata(
        json: JSONObject,
        metadata: PackagedMetadataBuilder,
    ) {
        val node = json.optJSONObject("node") ?: return
        collectPackagedMetadata(node, "project.json:node", metadata, metadata.warnings)
    }

    private fun collectPackageJsonPermissions(
        json: JSONObject,
        sources: MutableList<PermissionSource>,
        warnings: MutableList<String>,
    ) {
        val autojs6 = json.optJSONObject("autojs6") ?: return
        collectArray(autojs6, "permissions", "package.json:autojs6.permissions", sources, warnings)
        collectArray(autojs6, "bridgePermissions", "package.json:autojs6.bridgePermissions", sources, warnings)
        autojs6.optJSONObject("node")?.let { node ->
            collectArray(node, "permissions", "package.json:autojs6.node.permissions", sources, warnings)
            collectArray(node, "bridgePermissions", "package.json:autojs6.node.bridgePermissions", sources, warnings)
        }
    }

    private fun collectPackageJsonPackagedMetadata(
        json: JSONObject,
        metadata: PackagedMetadataBuilder,
    ) {
        val autojs6 = json.optJSONObject("autojs6") ?: return
        collectPackagedMetadata(autojs6, "package.json:autojs6", metadata, metadata.warnings)
        autojs6.optJSONObject("node")?.let { node ->
            collectPackagedMetadata(node, "package.json:autojs6.node", metadata, metadata.warnings)
        }
    }

    private fun collectPackagedMetadata(
        json: JSONObject,
        sourceName: String,
        metadata: PackagedMetadataBuilder,
        warnings: MutableList<String>,
    ) {
        collectOptionalString(json, "profile", "$sourceName.profile", warnings)?.let { profile ->
            metadata.profiles += profile
            metadata.sources += "$sourceName.profile"
        }
        collectOptionalStringArray(json, "profiles", "$sourceName.profiles", warnings).let { profiles ->
            if (profiles.isNotEmpty()) {
                metadata.profiles += profiles
                metadata.sources += "$sourceName.profiles"
            }
        }
        collectOptionalStringArray(json, "builtins", "$sourceName.builtins", warnings).let { builtins ->
            if (builtins.isNotEmpty()) {
                metadata.builtins += builtins
                metadata.sources += "$sourceName.builtins"
            }
        }
        collectOptionalStringArray(json, "nodeBuiltins", "$sourceName.nodeBuiltins", warnings).let { builtins ->
            if (builtins.isNotEmpty()) {
                metadata.builtins += builtins
                metadata.sources += "$sourceName.nodeBuiltins"
            }
        }
        collectOptionalStringArray(json, "nativeAssets", "$sourceName.nativeAssets", warnings).let { nativeAssets ->
            if (nativeAssets.isNotEmpty()) {
                metadata.nativeAssets += nativeAssets
                metadata.sources += "$sourceName.nativeAssets"
            }
        }
        collectOptionalStringArray(json, "filesystemRoots", "$sourceName.filesystemRoots", warnings).let { roots ->
            if (roots.isNotEmpty()) {
                metadata.filesystemRoots += roots
                metadata.sources += "$sourceName.filesystemRoots"
            }
        }
        json.optJSONObject("filesystem")?.let { filesystem ->
            collectOptionalStringArray(filesystem, "roots", "$sourceName.filesystem.roots", warnings).let { roots ->
                if (roots.isNotEmpty()) {
                    metadata.filesystemRoots += roots
                    metadata.sources += "$sourceName.filesystem.roots"
                }
            }
        }
        collectOptionalString(json, "executionMode", "$sourceName.executionMode", warnings)?.let { executionMode ->
            if (metadata.executionMode == null) {
                metadata.executionMode = executionMode
            }
            metadata.sources += "$sourceName.executionMode"
        }
        if (json.has("network") && !json.isNull("network")) {
            when (val network = json.opt("network")) {
                is Boolean -> {
                    metadata.networkRequested = metadata.networkRequested || network
                    metadata.sources += "$sourceName.network"
                }
                is JSONObject -> {
                    val enabled = network.opt("enabled")
                    if (enabled is Boolean) {
                        metadata.networkRequested = metadata.networkRequested || enabled
                        metadata.sources += "$sourceName.network.enabled"
                    } else {
                        warnings += "$sourceName.network.enabled must be a boolean when present."
                    }
                }
                else -> warnings += "$sourceName.network must be a boolean or object."
            }
        }
    }

    private fun collectOptionalString(
        json: JSONObject,
        key: String,
        sourceName: String,
        warnings: MutableList<String>,
    ): String? {
        if (!json.has(key) || json.isNull(key)) {
            return null
        }
        val value = json.opt(key)
        if (value !is String || value.isBlank()) {
            warnings += "$sourceName must be a non-empty string."
            return null
        }
        return value.trim()
    }

    private fun collectOptionalStringArray(
        json: JSONObject,
        key: String,
        sourceName: String,
        warnings: MutableList<String>,
    ): List<String> {
        if (!json.has(key) || json.isNull(key)) {
            return emptyList()
        }
        val array = json.opt(key) as? JSONArray
        if (array == null) {
            warnings += "$sourceName must be a JSON string array."
            return emptyList()
        }
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.opt(index)
                if (value !is String || value.isBlank()) {
                    warnings += "$sourceName[$index] must be a non-empty string."
                } else {
                    add(value.trim())
                }
            }
        }
    }

    private fun collectArray(
        json: JSONObject,
        key: String,
        sourceName: String,
        sources: MutableList<PermissionSource>,
        warnings: MutableList<String>,
    ) {
        if (!json.has(key) || json.isNull(key)) {
            return
        }
        val array = json.opt(key) as? JSONArray
        if (array == null) {
            warnings += "$sourceName must be a JSON string array."
            return
        }
        val values = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val value = array.opt(index)
            if (value !is String || value.isBlank()) {
                warnings += "$sourceName[$index] must be a non-empty string."
            } else {
                values += value
            }
        }
        sources += PermissionSource(sourceName, values)
    }

    private fun parseJsonObject(
        text: String,
        sourceName: String,
        warnings: MutableList<String>,
    ): JSONObject? {
        return runCatching { JSONObject(text) }
            .onFailure { error ->
                warnings += "$sourceName could not be parsed for Node bridge permissions: ${error.message.orEmpty()}"
            }
            .getOrNull()
    }

    private fun readTextIfFile(file: File): String? {
        return if (file.isFile) {
            runCatching { file.readText(StandardCharsets.UTF_8) }.getOrNull()
        } else {
            null
        }
    }

    private data class Manifest(
        val version: Int,
        val enforced: Boolean,
        val permissions: List<String>,
        val sources: List<String>,
        val knownCapabilities: List<String>,
        val unknownPermissions: List<String>,
        val androidPermissions: List<String>,
        val serviceRequired: Boolean,
        val warnings: List<String>,
        val packagedMetadata: PackagedMetadata,
    ) {
        fun toJsonObject(): JSONObject = JSONObject().apply {
            put("version", version)
            put("enforced", enforced)
            put("permissions", JSONArray(permissions))
            put("sources", JSONArray(sources))
            put("knownCapabilities", JSONArray(knownCapabilities))
            put("unknownPermissions", JSONArray(unknownPermissions))
            put("androidPermissions", JSONArray(androidPermissions))
            put("serviceRequired", serviceRequired)
            put("warnings", JSONArray(warnings))
            put("packagedMetadata", packagedMetadata.toJsonObject())
        }

        fun toJson(): String = toJsonObject().toString()

        companion object {
            fun empty(): Manifest = buildManifest(
                enforced = false,
                permissions = emptyList(),
                sources = emptyList(),
                warnings = emptyList(),
            )
        }
    }

    private data class PermissionSource(
        val name: String,
        val permissions: List<String>,
    )

    private data class PackagedMetadata(
        val schema: String,
        val status: String,
        val profile: String,
        val requestedProfiles: List<String>,
        val builtins: List<String>,
        val nativeAssets: List<String>,
        val filesystemRoots: List<String>,
        val executionMode: String,
        val longRunning: Boolean,
        val network: NetworkMetadata,
        val metadataOnly: Boolean,
        val grantsAuthority: Boolean,
        val sources: List<String>,
        val warnings: List<String>,
    ) {
        fun toJsonObject(): JSONObject = JSONObject().apply {
            put("schema", schema)
            put("status", status)
            put("profile", profile)
            put("requestedProfiles", JSONArray(requestedProfiles))
            put("builtins", JSONArray(builtins))
            put("nativeAssets", JSONArray(nativeAssets))
            put("filesystemRoots", JSONArray(filesystemRoots))
            put("executionMode", executionMode)
            put("longRunning", longRunning)
            put("network", network.toJsonObject())
            put("metadataOnly", metadataOnly)
            put("grantsAuthority", grantsAuthority)
            put("sources", JSONArray(sources))
            put("warnings", JSONArray(warnings))
        }
    }

    private data class NetworkMetadata(
        val requested: Boolean,
        val declaredCapability: Boolean,
        val androidPermissions: List<String>,
    ) {
        fun toJsonObject(): JSONObject = JSONObject().apply {
            put("requested", requested)
            put("declaredCapability", declaredCapability)
            put("androidPermissions", JSONArray(androidPermissions))
        }
    }

    private data class PackagedMetadataBuilder(
        val profiles: MutableList<String> = mutableListOf(),
        val builtins: MutableList<String> = mutableListOf(),
        val nativeAssets: MutableList<String> = mutableListOf(),
        val filesystemRoots: MutableList<String> = mutableListOf(),
        val sources: MutableList<String> = mutableListOf(),
        val warnings: MutableList<String> = mutableListOf(),
        var executionMode: String? = null,
        var networkRequested: Boolean = false,
    ) {
        fun build(normalizedPermissions: List<String>): PackagedMetadata {
            val distinctProfiles = profiles.map(::normalizeMetadataToken).filter { it.isNotEmpty() }.distinct()
            val profile = distinctProfiles.firstOrNull() ?: "safe_default"
            val mode = executionMode?.let(::normalizeMetadataToken).orEmpty()
            val networkCapabilityDeclared = NETWORK in normalizedPermissions
            val androidPermissions = if (networkCapabilityDeclared || networkRequested) {
                androidPermissionsForCapabilities(listOf(NETWORK))
            } else {
                emptyList()
            }
            val hasMetadata =
                distinctProfiles.isNotEmpty() ||
                        builtins.isNotEmpty() ||
                        nativeAssets.isNotEmpty() ||
                        filesystemRoots.isNotEmpty() ||
                        mode.isNotEmpty() ||
                        networkRequested
            return PackagedMetadata(
                schema = PACKAGED_METADATA_SCHEMA,
                status = if (hasMetadata || normalizedPermissions.isNotEmpty()) "partial" else "absent",
                profile = profile,
                requestedProfiles = distinctProfiles.sorted(),
                builtins = builtins.map(::normalizeBuiltin).filter { it.isNotEmpty() }.distinct().sorted(),
                nativeAssets = nativeAssets.map(::normalizeMetadataToken).filter { it.isNotEmpty() }.distinct().sorted(),
                filesystemRoots = filesystemRoots.map(::normalizeMetadataToken).filter { it.isNotEmpty() }.distinct().sorted(),
                executionMode = mode,
                longRunning = mode == "interactive_long_running" || mode == "long_running",
                network = NetworkMetadata(
                    requested = networkRequested || networkCapabilityDeclared,
                    declaredCapability = networkCapabilityDeclared,
                    androidPermissions = androidPermissions,
                ),
                metadataOnly = true,
                grantsAuthority = false,
                sources = sources.distinct().ifEmpty { listOf("none") },
                warnings = warnings.distinct(),
            )
        }

        private fun normalizeBuiltin(value: String): String {
            val normalized = normalizeMetadataToken(value)
            return normalized.removePrefix("node.")
        }

        private fun normalizeMetadataToken(value: String): String =
            value.trim().lowercase(Locale.US).replace(':', '.')
    }

    private const val PROJECT_JSON = "project.json"
    private const val PACKAGE_JSON = "package.json"
    private const val PACKAGED_METADATA_SCHEMA = "autojs6-packaged-capability-metadata-v1"
}
