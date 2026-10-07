package io.github.supermonster003.autojs6.plugin.nodejs;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ABI facts of the installed plugin package.
 *
 * The supported set is no longer a hand-written constant: the shared native-alignment Gradle
 * plugin (1.9.0+) derives it from the ABIs the build actually packages and writes it into the
 * manifest as {@code org.autojs.plugin.contract.SUPPORTED_ABIS}. A package built without that
 * meta-data falls back to the ABIs whose runtime library is installed.
 */
final class NodePluginAbis {

    static final String META_DATA_SUPPORTED_ABIS = "org.autojs.plugin.contract.SUPPORTED_ABIS";
    static final String RUNTIME_LIBRARY = "libnode.so";

    private NodePluginAbis() {
    }

    /** ABIs the plugin is built for: the manifest meta-data, or the installed set when absent. */
    static String[] supported(Context context) {
        String[] declared = declared(context);
        return declared != null ? declared : installed(context);
    }

    /** The manifest meta-data value split by commas, or null when the package carries none. */
    static String[] declared(Context context) {
        try {
            ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            Bundle metaData = info.metaData;
            String value = metaData == null ? null : metaData.getString(META_DATA_SUPPORTED_ABIS);
            if (value == null) {
                return null;
            }
            List<String> abis = new ArrayList<>();
            for (String token : value.split(",")) {
                String abi = token.trim();
                if (!abi.isEmpty() && !"universal".equals(abi) && !abis.contains(abi)) {
                    abis.add(abi);
                }
            }
            return abis.isEmpty() ? null : abis.toArray(new String[0]);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** ABIs whose runtime library is packaged in the installed base or split APKs. */
    static String[] installed(Context context) {
        ApplicationInfo info = context.getApplicationInfo();
        List<String> paths = new ArrayList<>();
        paths.add(info.sourceDir);
        if (info.splitSourceDirs != null) {
            paths.addAll(Arrays.asList(info.splitSourceDirs));
        }
        List<String> packaged = new ArrayList<>();
        try {
            for (String path : paths) {
                try (ZipFile apk = new ZipFile(path)) {
                    Enumeration<? extends ZipEntry> entries = apk.entries();
                    while (entries.hasMoreElements()) {
                        String name = entries.nextElement().getName();
                        if (!name.startsWith("lib/") || !name.endsWith("/" + RUNTIME_LIBRARY)) {
                            continue;
                        }
                        String abi = name.substring("lib/".length(), name.length() - RUNTIME_LIBRARY.length() - 1);
                        if (!abi.isEmpty() && !abi.contains("/") && !packaged.contains(abi)) {
                            packaged.add(abi);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Installed Node.js runtime package cannot be read", e);
        }
        if (packaged.isEmpty()) {
            throw new IllegalStateException("Installed Node.js runtime libraries are missing");
        }
        return packaged.toArray(new String[0]);
    }
}
