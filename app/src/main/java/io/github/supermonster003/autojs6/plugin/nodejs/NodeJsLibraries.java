package io.github.supermonster003.autojs6.plugin.nodejs;

import android.content.Context;
import android.content.pm.PackageManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.UUID;

/**
 * Lazily materializes the bundled Rhino global libraries (dayjs, pangu, cheerio, banana-i18n) that the shared JS tools
 * core compiles on first use (AutoJs6 api-coverage Q61). The directory is keyed by version code and install time, so a
 * reinstalled debug build with the same version code gets fresh files.
 */
public final class NodeJsLibraries {
    static final String ASSET_DIRECTORY = "nodejs/libraries";
    static final String ENV_DIRECTORY = "AUTOJS6_NODE_JS_LIBRARIES_DIR";
    static final String ENV_ERROR = "AUTOJS6_NODE_JS_LIBRARIES_ERROR";

    private static volatile String prepared;

    private NodeJsLibraries() {}

    public static String prepare(Context context) throws IOException {
        String directory = prepared;
        if (directory != null && new File(directory, ".complete").isFile()) return directory;
        synchronized (NodeJsLibraries.class) {
            File base = new File(context.getFilesDir(), "node-libraries");
            mkdirs(base);
            long installed;
            try {
                installed = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).lastUpdateTime;
            } catch (PackageManager.NameNotFoundException error) {
                throw new IOException("Cannot read the plugin package info", error);
            }
            String name = BuildConfig.VERSION_CODE + "-" + installed;
            File published = new File(base, name);
            try (RandomAccessFile lockFile = new RandomAccessFile(new File(base, "extract.lock"), "rw");
                 var lock = lockFile.getChannel().lock()) {
                // The two runtime slot processes share filesDir. Publish only complete extractions.
                if (!new File(published, ".complete").isFile()) {
                    File staging = new File(base, ".extract-" + UUID.randomUUID());
                    mkdirs(staging);
                    try {
                        extract(context, staging);
                        if (!new File(staging, ".complete").createNewFile() || !staging.renameTo(published)) {
                            throw new IOException("Cannot publish the bundled JS libraries at " + published);
                        }
                    } finally {
                        deleteTree(staging);
                    }
                    File[] stale = base.listFiles();
                    if (stale != null) for (File child : stale) {
                        if (child.isDirectory() && !child.getName().equals(name)) deleteTree(child);
                    }
                }
            }
            prepared = published.getAbsolutePath();
            return prepared;
        }
    }

    private static void extract(Context context, File destination) throws IOException {
        String[] names = context.getAssets().list(ASSET_DIRECTORY);
        if (names == null || names.length == 0) throw new IOException("No bundled JS libraries in the APK");
        byte[] buffer = new byte[65536];
        for (String name : names) {
            try (InputStream input = context.getAssets().open(ASSET_DIRECTORY + "/" + name);
                 OutputStream output = new FileOutputStream(new File(destination, name))) {
                int size;
                while ((size = input.read(buffer)) != -1) output.write(buffer, 0, size);
            }
        }
    }

    private static void mkdirs(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create " + directory);
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
