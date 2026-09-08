package io.github.supermonster003.autojs6.plugin.nodejs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class NodeBridgePermissionManifest {

    public static final NodeBridgePermissionManifest INSTANCE = new NodeBridgePermissionManifest();
    public static final String RUNTIME_MODULE_NAME = "autojs6:bridge-permissions";

    private static final String TOAST = "toast";
    private static final String APP = "app";
    private static final String APP_LAUNCH = "app.launch";
    private static final String APP_SETTINGS = "app.settings";
    private static final String APP_ACTIVITY = "app.activity";
    private static final String APP_QUERY = "app.query";
    private static final String DIALOGS = "dialogs";
    private static final String ENGINES = "engines";
    private static final String ENGINES_EXEC = "engines.exec";
    private static final String ACCESSIBILITY = "accessibility";
    private static final String ACCESSIBILITY_GESTURE = "accessibility.gesture";
    private static final String SCREEN_CAPTURE = "screen_capture";
    private static final String IMAGE = "image";
    private static final String OCR = "ocr";
    private static final String BARCODE = "barcode";
    private static final String MEDIA = "media";
    private static final String MEDIA_AUDIO = "media.audio";
    private static final String MEDIA_METADATA = "media.metadata";
    private static final String MEDIA_RECORDING = "media.recording";
    private static final String STORAGE = "storage";
    private static final String DATABASE = "database";
    private static final String NOTIFICATIONS = "notifications";
    private static final String NOTIFICATIONS_SETTINGS = "notifications.settings";
    private static final String SENSORS = "sensors";
    private static final String UI = "ui";
    private static final String UI_OVERLAY = "ui.overlay";
    private static final String UI_OVERLAY_PERMISSION = "ui.overlay.permission";
    private static final String UI_OVERLAY_FOREGROUND = "ui.overlay.foreground";
    private static final String UI_OVERLAY_PACKAGED = "ui.overlay.packaged";
    private static final String CLIPBOARD = "clipboard";
    private static final String DEVICE = "device";
    private static final String DEVICE_POWER = "device.power";
    private static final String SHELL = "shell";
    private static final String SHELL_ROOT = "shell.root";
    private static final String SHELL_SHIZUKU = "shell.shizuku";
    private static final String NETWORK = "network";
    private static final String RAW_NETWORK = "raw_network";
    private static final String WORK_MANAGER = "work_manager";
    private static final String PACKAGE_MANAGER = "package_manager";
    private static final String PACKAGE_MANAGER_MUTATE = "package_manager.mutate";
    private static final String JAVA_INTEROP = "java_interop";
    private static final String RHINO = "rhino";
    private static final String INPUT_OBSERVER = "input_observer";
    private static final String INPUT_OBSERVER_KEYS = "input_observer.keys";
    private static final String INPUT_OBSERVER_TOUCH = "input_observer.touch";
    private static final String INPUT_OBSERVER_INTERCEPT = "input_observer.intercept";
    private static final String FLOATY_RAW = "floaty.raw";
    private static final String FLOATY_TOUCH = "floaty.touch";
    private static final String PINYIN = "pinyin";
    private static final String PINYIN4J = "pinyin4j";
    private static final String FILES = "files";
    private static final String FILES_WRITE = "files.write";
    private static final String FILES_DELETE = "files.delete";
    private static final String CONSOLE = "console";
    private static final String EVENTS = "events";
    private static final String EVENTS_NOTIFICATION = "events.notification";
    private static final String EVENTS_TOAST = "events.toast";
    private static final String EVENTS_KEY = "events.key";
    private static final String KEYS = "keys";

    private static final String ANDROID_PERMISSION_INTERNET = "android.permission.INTERNET";
    private static final String ANDROID_PERMISSION_FOREGROUND_SERVICE = "android.permission.FOREGROUND_SERVICE";
    private static final String ANDROID_PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION =
            "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION";
    private static final String ANDROID_PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE =
            "android.permission.FOREGROUND_SERVICE_SPECIAL_USE";
    private static final String ANDROID_PERMISSION_FOREGROUND_SERVICE_MICROPHONE =
            "android.permission.FOREGROUND_SERVICE_MICROPHONE";
    private static final String ANDROID_PERMISSION_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";
    private static final String ANDROID_PERMISSION_RECORD_AUDIO = "android.permission.RECORD_AUDIO";

    private static final String PROJECT_JSON = "project.json";
    private static final String PACKAGE_JSON = "package.json";
    private static final String PACKAGED_METADATA_SCHEMA = "autojs6-packaged-capability-metadata-v1";

    private static final List<String> DEFINED_CAPABILITIES = immutableList(
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
            ACCESSIBILITY_GESTURE,
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
            PINYIN,
            PINYIN4J,
            FILES,
            FILES_WRITE,
            FILES_DELETE,
            CONSOLE,
            EVENTS,
            EVENTS_NOTIFICATION,
            EVENTS_TOAST,
            EVENTS_KEY,
            KEYS
    );
    private static final Set<String> DEFINED_CAPABILITY_SET =
            Collections.unmodifiableSet(new HashSet<>(DEFINED_CAPABILITIES));
    private static final Map<String, String> ALIASES = aliases();
    private static final Map<String, List<String>> ANDROID_PERMISSIONS_BY_CAPABILITY = androidPermissionsByCapability();
    private static final List<String> ANDROID_PERMISSION_ORDER = immutableList(
            ANDROID_PERMISSION_POST_NOTIFICATIONS,
            ANDROID_PERMISSION_RECORD_AUDIO,
            ANDROID_PERMISSION_INTERNET,
            ANDROID_PERMISSION_FOREGROUND_SERVICE,
            ANDROID_PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION,
            ANDROID_PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE,
            ANDROID_PERMISSION_FOREGROUND_SERVICE_MICROPHONE
    );

    private NodeBridgePermissionManifest() {
    }

    public String runtimeModuleSourceForMetadata(
            String workingProjectJson,
            String workingPackageJson,
            String sandboxProjectJson,
            String sandboxPackageJson,
            boolean includeStableNetworkPolicy
    ) {
        return fromRuntimeMetadata(
                workingProjectJson,
                workingPackageJson,
                sandboxProjectJson,
                sandboxPackageJson,
                includeStableNetworkPolicy
        ).toJson();
    }

    private static Manifest fromRuntimeMetadata(
            String workingProjectJson,
            String workingPackageJson,
            String sandboxProjectJson,
            String sandboxPackageJson,
            boolean includeStableNetworkPolicy
    ) {
        Manifest primary = fromJsonSources(workingProjectJson, workingPackageJson);
        Manifest fallback = sandboxProjectJson != null || sandboxPackageJson != null
                ? fromJsonSources(sandboxProjectJson, sandboxPackageJson)
                : Manifest.empty();
        if (!includeStableNetworkPolicy && !fallback.enforced) {
            return primary;
        }

        List<String> permissions = new ArrayList<>();
        permissions.addAll(primary.permissions);
        permissions.addAll(fallback.permissions);
        if (includeStableNetworkPolicy) {
            permissions.add(NETWORK);
        }

        List<String> sources = new ArrayList<>();
        addNonNone(sources, primary.sources);
        addNonNone(sources, fallback.sources);
        if (includeStableNetworkPolicy) {
            sources.add("runtime:stable-network");
        }

        List<String> warnings = new ArrayList<>();
        warnings.addAll(primary.warnings);
        warnings.addAll(fallback.warnings);
        return buildManifest(
                primary.enforced || fallback.enforced || includeStableNetworkPolicy,
                normalizeDistinctSorted(permissions),
                distinctPreserveOrder(sources),
                distinctPreserveOrder(warnings),
                new PackagedMetadataBuilder()
        );
    }

    private static Manifest fromJsonSources(String projectJsonText, String packageJsonText) {
        List<PermissionSource> sources = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        PackagedMetadataBuilder packagedMetadata = new PackagedMetadataBuilder();
        if (projectJsonText != null) {
            JSONObject json = parseJsonObject(projectJsonText, PROJECT_JSON, warnings);
            if (json != null) {
                collectProjectJsonPermissions(json, sources, warnings);
                collectProjectJsonPackagedMetadata(json, packagedMetadata);
            }
        }
        if (packageJsonText != null) {
            JSONObject json = parseJsonObject(packageJsonText, PACKAGE_JSON, warnings);
            if (json != null) {
                collectPackageJsonPermissions(json, sources, warnings);
                collectPackageJsonPackagedMetadata(json, packagedMetadata);
            }
        }

        List<String> permissions = new ArrayList<>();
        for (PermissionSource source : sources) {
            permissions.addAll(source.permissions);
        }
        List<String> sourceNames = new ArrayList<>();
        for (PermissionSource source : sources) {
            sourceNames.add(source.name);
        }
        return buildManifest(
                !sources.isEmpty(),
                normalizeDistinctSorted(permissions),
                distinctPreserveOrder(sourceNames),
                warnings,
                packagedMetadata
        );
    }

    private static String normalizeCapability(String value) {
        String normalized = value.trim().toLowerCase(Locale.US).replace(':', '.');
        String alias = ALIASES.get(normalized);
        return alias == null ? normalized : alias;
    }

    private static List<String> androidPermissionsForCapabilities(Iterable<String> capabilities) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String capability : capabilities) {
            List<String> permissions = ANDROID_PERMISSIONS_BY_CAPABILITY.get(normalizeCapability(capability));
            if (permissions != null) {
                values.addAll(permissions);
            }
        }
        List<String> result = new ArrayList<>(values);
        result.sort(Comparator
                .comparingInt((String value) -> {
                    int index = ANDROID_PERMISSION_ORDER.indexOf(value);
                    return index < 0 ? Integer.MAX_VALUE : index;
                })
                .thenComparing(value -> value));
        return result;
    }

    private static Manifest buildManifest(
            boolean enforced,
            List<String> permissions,
            List<String> sources,
            List<String> warnings,
            PackagedMetadataBuilder packagedMetadata
    ) {
        List<String> unknown = new ArrayList<>();
        for (String permission : permissions) {
            if (!DEFINED_CAPABILITY_SET.contains(permission)) {
                unknown.add(permission);
            }
        }
        Collections.sort(unknown);

        List<String> mergedWarnings = new ArrayList<>(warnings);
        mergedWarnings.addAll(packagedMetadata.warnings);
        for (String permission : unknown) {
            mergedWarnings.add("Unknown Node bridge capability: " + permission);
        }

        List<String> normalizedPermissions = normalizeDistinct(permissions);
        return new Manifest(
                1,
                enforced,
                permissions,
                sources.isEmpty() ? Collections.singletonList("none") : sources,
                DEFINED_CAPABILITIES,
                unknown,
                androidPermissionsForCapabilities(permissions),
                !permissions.isEmpty(),
                distinctPreserveOrder(mergedWarnings),
                packagedMetadata.build(normalizedPermissions)
        );
    }

    private static void collectProjectJsonPermissions(
            JSONObject json,
            List<PermissionSource> sources,
            List<String> warnings
    ) {
        JSONObject node = json.optJSONObject("node");
        if (node == null) {
            return;
        }
        collectArray(node, "permissions", "project.json:node.permissions", sources, warnings);
        collectArray(node, "bridgePermissions", "project.json:node.bridgePermissions", sources, warnings);
    }

    private static void collectProjectJsonPackagedMetadata(
            JSONObject json,
            PackagedMetadataBuilder metadata
    ) {
        JSONObject node = json.optJSONObject("node");
        if (node != null) {
            collectPackagedMetadata(node, "project.json:node", metadata, metadata.warnings);
        }
    }

    private static void collectPackageJsonPermissions(
            JSONObject json,
            List<PermissionSource> sources,
            List<String> warnings
    ) {
        JSONObject autojs6 = json.optJSONObject("autojs6");
        if (autojs6 == null) {
            return;
        }
        collectArray(autojs6, "permissions", "package.json:autojs6.permissions", sources, warnings);
        collectArray(autojs6, "bridgePermissions", "package.json:autojs6.bridgePermissions", sources, warnings);
        JSONObject node = autojs6.optJSONObject("node");
        if (node != null) {
            collectArray(node, "permissions", "package.json:autojs6.node.permissions", sources, warnings);
            collectArray(node, "bridgePermissions", "package.json:autojs6.node.bridgePermissions", sources, warnings);
        }
    }

    private static void collectPackageJsonPackagedMetadata(
            JSONObject json,
            PackagedMetadataBuilder metadata
    ) {
        JSONObject autojs6 = json.optJSONObject("autojs6");
        if (autojs6 == null) {
            return;
        }
        collectPackagedMetadata(autojs6, "package.json:autojs6", metadata, metadata.warnings);
        JSONObject node = autojs6.optJSONObject("node");
        if (node != null) {
            collectPackagedMetadata(node, "package.json:autojs6.node", metadata, metadata.warnings);
        }
    }

    private static void collectPackagedMetadata(
            JSONObject json,
            String sourceName,
            PackagedMetadataBuilder metadata,
            List<String> warnings
    ) {
        String profile = collectOptionalString(json, "profile", sourceName + ".profile", warnings);
        if (profile != null) {
            metadata.profiles.add(profile);
            metadata.sources.add(sourceName + ".profile");
        }
        collectOptionalStringArray(json, "profiles", sourceName + ".profiles", warnings)
                .forEach(value -> {
                    metadata.profiles.add(value);
                    metadata.sources.add(sourceName + ".profiles");
                });
        collectOptionalStringArray(json, "builtins", sourceName + ".builtins", warnings)
                .forEach(value -> {
                    metadata.builtins.add(value);
                    metadata.sources.add(sourceName + ".builtins");
                });
        collectOptionalStringArray(json, "nodeBuiltins", sourceName + ".nodeBuiltins", warnings)
                .forEach(value -> {
                    metadata.builtins.add(value);
                    metadata.sources.add(sourceName + ".nodeBuiltins");
                });
        collectOptionalStringArray(json, "nativeAssets", sourceName + ".nativeAssets", warnings)
                .forEach(value -> {
                    metadata.nativeAssets.add(value);
                    metadata.sources.add(sourceName + ".nativeAssets");
                });
        // Compatibility-only diagnostics: packaged projects may describe the
        // roots they expect to use, but this metadata never grants, widens, or
        // narrows filesystem authority. Android app permissions define the
        // device-visible reach and the runtime independently rejects its
        // sensitive /proc, /sys, and /dev boundaries.
        collectOptionalStringArray(json, "filesystemRoots", sourceName + ".filesystemRoots", warnings)
                .forEach(value -> {
                    metadata.filesystemRoots.add(value);
                    metadata.sources.add(sourceName + ".filesystemRoots");
                });

        JSONObject filesystem = json.optJSONObject("filesystem");
        if (filesystem != null) {
            collectOptionalStringArray(filesystem, "roots", sourceName + ".filesystem.roots", warnings)
                    .forEach(value -> {
                        metadata.filesystemRoots.add(value);
                        metadata.sources.add(sourceName + ".filesystem.roots");
                    });
        }

        String executionMode = collectOptionalString(json, "executionMode", sourceName + ".executionMode", warnings);
        if (executionMode != null) {
            if (metadata.executionMode == null) {
                metadata.executionMode = executionMode;
            }
            metadata.sources.add(sourceName + ".executionMode");
        }

        if (json.has("network") && !json.isNull("network")) {
            Object network = json.opt("network");
            if (network instanceof Boolean) {
                metadata.networkRequested = metadata.networkRequested || (Boolean) network;
                metadata.sources.add(sourceName + ".network");
            } else if (network instanceof JSONObject) {
                Object enabled = ((JSONObject) network).opt("enabled");
                if (enabled instanceof Boolean) {
                    metadata.networkRequested = metadata.networkRequested || (Boolean) enabled;
                    metadata.sources.add(sourceName + ".network.enabled");
                } else {
                    warnings.add(sourceName + ".network.enabled must be a boolean when present.");
                }
            } else {
                warnings.add(sourceName + ".network must be a boolean or object.");
            }
        }
    }

    private static String collectOptionalString(
            JSONObject json,
            String key,
            String sourceName,
            List<String> warnings
    ) {
        if (!json.has(key) || json.isNull(key)) {
            return null;
        }
        Object value = json.opt(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            warnings.add(sourceName + " must be a non-empty string.");
            return null;
        }
        return ((String) value).trim();
    }

    private static List<String> collectOptionalStringArray(
            JSONObject json,
            String key,
            String sourceName,
            List<String> warnings
    ) {
        if (!json.has(key) || json.isNull(key)) {
            return Collections.emptyList();
        }
        Object value = json.opt(key);
        if (!(value instanceof JSONArray)) {
            warnings.add(sourceName + " must be a JSON string array.");
            return Collections.emptyList();
        }
        JSONArray array = (JSONArray) value;
        List<String> result = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            Object item = array.opt(index);
            if (!(item instanceof String) || ((String) item).trim().isEmpty()) {
                warnings.add(sourceName + "[" + index + "] must be a non-empty string.");
            } else {
                result.add(((String) item).trim());
            }
        }
        return result;
    }

    private static void collectArray(
            JSONObject json,
            String key,
            String sourceName,
            List<PermissionSource> sources,
            List<String> warnings
    ) {
        if (!json.has(key) || json.isNull(key)) {
            return;
        }
        Object value = json.opt(key);
        if (!(value instanceof JSONArray)) {
            warnings.add(sourceName + " must be a JSON string array.");
            return;
        }
        JSONArray array = (JSONArray) value;
        List<String> values = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            Object item = array.opt(index);
            if (!(item instanceof String) || ((String) item).trim().isEmpty()) {
                warnings.add(sourceName + "[" + index + "] must be a non-empty string.");
            } else {
                values.add((String) item);
            }
        }
        sources.add(new PermissionSource(sourceName, values));
    }

    private static JSONObject parseJsonObject(String text, String sourceName, List<String> warnings) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(text);
        } catch (Throwable error) {
            warnings.add(sourceName + " could not be parsed for Node bridge permissions: " + error.getMessage());
            return null;
        }
    }

    private static JSONObject put(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (Throwable ignored) {
            // The values written here are primitive types, strings, arrays, or nested JSON objects.
        }
        return json;
    }

    private static Map<String, String> aliases() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("media_projection", SCREEN_CAPTURE);
        map.put("media.projection", SCREEN_CAPTURE);
        map.put("screen.capture", SCREEN_CAPTURE);
        map.put("screencapture", SCREEN_CAPTURE);
        map.put("qrcode", BARCODE);
        map.put("qr_code", BARCODE);
        map.put("qr-code", BARCODE);
        map.put("barcode.detect", BARCODE);
        map.put("barcode.detectall", BARCODE);
        map.put("barcode.recognizetext", BARCODE);
        map.put("barcode.recognizetexts", BARCODE);
        map.put("qrcode.detect", BARCODE);
        map.put("qrcode.detectall", BARCODE);
        map.put("qrcode.recognizetext", BARCODE);
        map.put("qrcode.recognizetexts", BARCODE);
        map.put("audio", MEDIA_AUDIO);
        map.put("media:audio", MEDIA_AUDIO);
        map.put("media-audio", MEDIA_AUDIO);
        map.put("media.metadata", MEDIA_METADATA);
        map.put("media:metadata", MEDIA_METADATA);
        map.put("mediainfo", MEDIA_METADATA);
        map.put("media-info", MEDIA_METADATA);
        map.put("media_info", MEDIA_METADATA);
        map.put("app-query", APP_QUERY);
        map.put("app:query", APP_QUERY);
        map.put("package-query", APP_QUERY);
        map.put("package.query", APP_QUERY);
        map.put("media.record", MEDIA_RECORDING);
        map.put("media:recording", MEDIA_RECORDING);
        map.put("media-recording", MEDIA_RECORDING);
        map.put("recorder", MEDIA_RECORDING);
        map.put("audio.recording", MEDIA_RECORDING);
        map.put("notification", NOTIFICATIONS);
        map.put("notification.settings", NOTIFICATIONS_SETTINGS);
        map.put("notifications:settings", NOTIFICATIONS_SETTINGS);
        map.put("notifications-settings", NOTIFICATIONS_SETTINGS);
        map.put("device:power", DEVICE_POWER);
        map.put("device-power", DEVICE_POWER);
        map.put("power-manager", DEVICE_POWER);
        map.put("power_manager", DEVICE_POWER);
        map.put("engines:exec", ENGINES_EXEC);
        map.put("engine.exec", ENGINES_EXEC);
        map.put("shell:root", SHELL_ROOT);
        map.put("shell-root", SHELL_ROOT);
        map.put("shell:shizuku", SHELL_SHIZUKU);
        map.put("shell-shizuku", SHELL_SHIZUKU);
        map.put("storages", STORAGE);
        map.put("sqlite", DATABASE);
        map.put("package-manager", PACKAGE_MANAGER);
        map.put("package.manager", PACKAGE_MANAGER);
        map.put("android_package_manager", PACKAGE_MANAGER);
        map.put("android-package-manager", PACKAGE_MANAGER);
        map.put("package-manager.mutate", PACKAGE_MANAGER_MUTATE);
        map.put("package.manager.mutate", PACKAGE_MANAGER_MUTATE);
        map.put("android_package_manager.mutate", PACKAGE_MANAGER_MUTATE);
        map.put("android-package-manager.mutate", PACKAGE_MANAGER_MUTATE);
        map.put("java", JAVA_INTEROP);
        map.put("rhino.run", RHINO);
        map.put("input-observer", INPUT_OBSERVER);
        map.put("input.observer", INPUT_OBSERVER);
        map.put("inputobserver", INPUT_OBSERVER);
        map.put("input-observer.keys", INPUT_OBSERVER_KEYS);
        map.put("input.observer.keys", INPUT_OBSERVER_KEYS);
        map.put("input-observer.touch", INPUT_OBSERVER_TOUCH);
        map.put("input.observer.touch", INPUT_OBSERVER_TOUCH);
        map.put("input-observer.intercept", INPUT_OBSERVER_INTERCEPT);
        map.put("input.observer.intercept", INPUT_OBSERVER_INTERCEPT);
        map.put("ui-overlay", UI_OVERLAY);
        map.put("ui:overlay", UI_OVERLAY);
        map.put("overlay", UI_OVERLAY);
        map.put("overlay.permission", UI_OVERLAY_PERMISSION);
        map.put("ui-overlay.permission", UI_OVERLAY_PERMISSION);
        map.put("ui:overlay.permission", UI_OVERLAY_PERMISSION);
        map.put("overlay.foreground", UI_OVERLAY_FOREGROUND);
        map.put("ui-overlay.foreground", UI_OVERLAY_FOREGROUND);
        map.put("overlay.packaged", UI_OVERLAY_PACKAGED);
        map.put("ui-overlay.packaged", UI_OVERLAY_PACKAGED);
        map.put("raw.network", RAW_NETWORK);
        map.put("raw-network", RAW_NETWORK);
        map.put("raw.node.network", RAW_NETWORK);
        map.put("raw-node-network", RAW_NETWORK);
        map.put("raw_node_network_modules", RAW_NETWORK);
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, List<String>> androidPermissionsByCapability() {
        LinkedHashMap<String, List<String>> map = new LinkedHashMap<>();
        map.put(NOTIFICATIONS, immutableList(ANDROID_PERMISSION_POST_NOTIFICATIONS));
        map.put(MEDIA_RECORDING, immutableList(
                ANDROID_PERMISSION_RECORD_AUDIO,
                ANDROID_PERMISSION_FOREGROUND_SERVICE,
                ANDROID_PERMISSION_FOREGROUND_SERVICE_MICROPHONE
        ));
        map.put(NETWORK, immutableList(ANDROID_PERMISSION_INTERNET));
        map.put(RAW_NETWORK, immutableList(ANDROID_PERMISSION_INTERNET));
        map.put(SCREEN_CAPTURE, immutableList(
                ANDROID_PERMISSION_FOREGROUND_SERVICE,
                ANDROID_PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION,
                ANDROID_PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE
        ));
        return Collections.unmodifiableMap(map);
    }

    private static void addNonNone(List<String> destination, List<String> values) {
        for (String value : values) {
            if (!"none".equals(value)) {
                destination.add(value);
            }
        }
    }

    private static List<String> normalizeDistinct(Iterable<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = normalizeCapability(value);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return new ArrayList<>(result);
    }

    private static List<String> normalizeDistinctSorted(Iterable<String> values) {
        List<String> result = normalizeDistinct(values);
        Collections.sort(result);
        return result;
    }

    private static List<String> distinctPreserveOrder(Iterable<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            result.add(value);
        }
        return new ArrayList<>(result);
    }

    private static List<String> normalizedMetadataList(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = normalizeMetadataToken(value);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        List<String> list = new ArrayList<>(result);
        Collections.sort(list);
        return list;
    }

    private static String normalizeBuiltin(String value) {
        String normalized = normalizeMetadataToken(value);
        return normalized.startsWith("node.") ? normalized.substring("node.".length()) : normalized;
    }

    private static String normalizeMetadataToken(String value) {
        return value.trim().toLowerCase(Locale.US).replace(':', '.');
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static List<String> immutableList(String... values) {
        return Collections.unmodifiableList(Arrays.asList(values));
    }

    private static final class Manifest {
        final int version;
        final boolean enforced;
        final List<String> permissions;
        final List<String> sources;
        final List<String> knownCapabilities;
        final List<String> unknownPermissions;
        final List<String> androidPermissions;
        final boolean serviceRequired;
        final List<String> warnings;
        final PackagedMetadata packagedMetadata;

        Manifest(
                int version,
                boolean enforced,
                List<String> permissions,
                List<String> sources,
                List<String> knownCapabilities,
                List<String> unknownPermissions,
                List<String> androidPermissions,
                boolean serviceRequired,
                List<String> warnings,
                PackagedMetadata packagedMetadata
        ) {
            this.version = version;
            this.enforced = enforced;
            this.permissions = permissions;
            this.sources = sources;
            this.knownCapabilities = knownCapabilities;
            this.unknownPermissions = unknownPermissions;
            this.androidPermissions = androidPermissions;
            this.serviceRequired = serviceRequired;
            this.warnings = warnings;
            this.packagedMetadata = packagedMetadata;
        }

        JSONObject toJsonObject() {
            JSONObject json = new JSONObject();
            put(json, "version", version);
            put(json, "enforced", enforced);
            put(json, "permissions", new JSONArray(permissions));
            put(json, "sources", new JSONArray(sources));
            put(json, "knownCapabilities", new JSONArray(knownCapabilities));
            put(json, "unknownPermissions", new JSONArray(unknownPermissions));
            put(json, "androidPermissions", new JSONArray(androidPermissions));
            put(json, "serviceRequired", serviceRequired);
            put(json, "warnings", new JSONArray(warnings));
            put(json, "packagedMetadata", packagedMetadata.toJsonObject());
            return json;
        }

        String toJson() {
            return toJsonObject().toString();
        }

        static Manifest empty() {
            return buildManifest(
                    false,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    new PackagedMetadataBuilder()
            );
        }
    }

    private static final class PermissionSource {
        final String name;
        final List<String> permissions;

        PermissionSource(String name, List<String> permissions) {
            this.name = name;
            this.permissions = permissions;
        }
    }

    private static final class PackagedMetadata {
        final String schema;
        final String status;
        final String profile;
        final List<String> requestedProfiles;
        final List<String> builtins;
        final List<String> nativeAssets;
        final List<String> filesystemRoots;
        final String executionMode;
        final boolean longRunning;
        final NetworkMetadata network;
        final boolean metadataOnly;
        final boolean grantsAuthority;
        final List<String> sources;
        final List<String> warnings;

        PackagedMetadata(
                String schema,
                String status,
                String profile,
                List<String> requestedProfiles,
                List<String> builtins,
                List<String> nativeAssets,
                List<String> filesystemRoots,
                String executionMode,
                boolean longRunning,
                NetworkMetadata network,
                boolean metadataOnly,
                boolean grantsAuthority,
                List<String> sources,
                List<String> warnings
        ) {
            this.schema = schema;
            this.status = status;
            this.profile = profile;
            this.requestedProfiles = requestedProfiles;
            this.builtins = builtins;
            this.nativeAssets = nativeAssets;
            this.filesystemRoots = filesystemRoots;
            this.executionMode = executionMode;
            this.longRunning = longRunning;
            this.network = network;
            this.metadataOnly = metadataOnly;
            this.grantsAuthority = grantsAuthority;
            this.sources = sources;
            this.warnings = warnings;
        }

        JSONObject toJsonObject() {
            JSONObject json = new JSONObject();
            put(json, "schema", schema);
            put(json, "status", status);
            put(json, "profile", profile);
            put(json, "requestedProfiles", new JSONArray(requestedProfiles));
            put(json, "builtins", new JSONArray(builtins));
            put(json, "nativeAssets", new JSONArray(nativeAssets));
            put(json, "filesystemRoots", new JSONArray(filesystemRoots));
            put(json, "executionMode", executionMode);
            put(json, "longRunning", longRunning);
            put(json, "network", network.toJsonObject());
            put(json, "metadataOnly", metadataOnly);
            put(json, "grantsAuthority", grantsAuthority);
            put(json, "sources", new JSONArray(sources));
            put(json, "warnings", new JSONArray(warnings));
            return json;
        }
    }

    private static final class NetworkMetadata {
        final boolean requested;
        final boolean declaredCapability;
        final List<String> androidPermissions;

        NetworkMetadata(boolean requested, boolean declaredCapability, List<String> androidPermissions) {
            this.requested = requested;
            this.declaredCapability = declaredCapability;
            this.androidPermissions = androidPermissions;
        }

        JSONObject toJsonObject() {
            JSONObject json = new JSONObject();
            put(json, "requested", requested);
            put(json, "declaredCapability", declaredCapability);
            put(json, "androidPermissions", new JSONArray(androidPermissions));
            return json;
        }
    }

    private static final class PackagedMetadataBuilder {
        final List<String> profiles = new ArrayList<>();
        final List<String> builtins = new ArrayList<>();
        final List<String> nativeAssets = new ArrayList<>();
        final List<String> filesystemRoots = new ArrayList<>();
        final List<String> sources = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        String executionMode;
        boolean networkRequested;

        PackagedMetadata build(List<String> normalizedPermissions) {
            List<String> distinctProfiles = normalizedMetadataList(profiles);
            String profile = distinctProfiles.isEmpty() ? "safe_default" : distinctProfiles.get(0);
            String mode = executionMode == null ? "" : normalizeMetadataToken(executionMode);
            boolean networkCapabilityDeclared = normalizedPermissions.contains(NETWORK);
            List<String> androidPermissions = networkCapabilityDeclared || networkRequested
                    ? androidPermissionsForCapabilities(Collections.singletonList(NETWORK))
                    : Collections.emptyList();
            boolean hasMetadata = !distinctProfiles.isEmpty()
                    || !builtins.isEmpty()
                    || !nativeAssets.isEmpty()
                    || !filesystemRoots.isEmpty()
                    || !mode.isEmpty()
                    || networkRequested;
            return new PackagedMetadata(
                    PACKAGED_METADATA_SCHEMA,
                    hasMetadata || !normalizedPermissions.isEmpty() ? "partial" : "absent",
                    profile,
                    distinctProfiles,
                    normalizedBuiltins(),
                    normalizedMetadataList(nativeAssets),
                    normalizedMetadataList(filesystemRoots),
                    mode,
                    "interactive_long_running".equals(mode) || "long_running".equals(mode),
                    new NetworkMetadata(
                            networkRequested || networkCapabilityDeclared,
                            networkCapabilityDeclared,
                            androidPermissions
                    ),
                    true,
                    false,
                    sources.isEmpty()
                            ? Collections.singletonList("none")
                            : distinctPreserveOrder(sources),
                    distinctPreserveOrder(warnings)
            );
        }

        private List<String> normalizedBuiltins() {
            LinkedHashSet<String> result = new LinkedHashSet<>();
            for (String value : builtins) {
                String normalized = normalizeBuiltin(value);
                if (!normalized.isEmpty()) {
                    result.add(normalized);
                }
            }
            List<String> list = new ArrayList<>(result);
            Collections.sort(list);
            return list;
        }
    }
}
