package io.github.supermonster003.autojs6.plugin.nodejs;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Lazily materializes the existing, build-verified CLI asset for runtime npm calls. */
public final class NodeRuntimeCli {
    private NodeRuntimeCli() {}

    public static synchronized JSONObject prepare(Context context) throws Exception {
        File base = new File(context.getFilesDir(), "node-cli");
        mkdirs(base);
        File cli = new File(base, BuildConfig.NODE_CLI_ARCHIVE_SHA256);
        File bin = new File(base, "bin");
        File executable = new File(context.getApplicationInfo().nativeLibraryDir, BuildConfig.NODE_CLI_EXECUTABLE);
        try (RandomAccessFile lockFile = new RandomAccessFile(new File(base, "extract.lock"), "rw");
             var lock = lockFile.getChannel().lock()) {
            // The two runtime slot processes share filesDir. Publish only complete extractions.
            if (!new File(cli, ".complete").isFile()) {
                File staging = new File(base, ".extract-" + UUID.randomUUID());
                mkdirs(staging);
                try {
                    extract(context, staging);
                    try (FileOutputStream marker = new FileOutputStream(new File(staging, ".complete"))) {
                        marker.write(BuildConfig.NODE_CLI_ARCHIVE_SHA256.getBytes(StandardCharsets.US_ASCII));
                    }
                    if (!staging.renameTo(cli)) throw new IOException("Cannot publish bundled npm at " + cli);
                } finally {
                    deleteTree(staging);
                }
            }
            mkdirs(bin);
            for (String command : BuildConfig.NODE_CLI_COMMANDS.split(",")) {
                File link = new File(bin, command);
                try {
                    if (executable.getAbsolutePath().equals(Os.readlink(link.getAbsolutePath()))) continue;
                } catch (ErrnoException ignored) {
                    // New install, or the APK nativeLibraryDir changed after an update.
                }
                // File.delete also removes a dangling symlink, without following its target.
                link.delete();
                Os.symlink(executable.getAbsolutePath(), link.getAbsolutePath());
            }
        }
        return new JSONObject()
                .put("executable", executable.getAbsolutePath())
                .put("root", cli.getAbsolutePath())
                .put("bin", bin.getAbsolutePath())
                .put("npmVersion", BuildConfig.NODE_CLI_NPM_VERSION);
    }

    private static void extract(Context context, File destination) throws Exception {
        String prefix = BuildConfig.NODE_CLI_ARCHIVE_ROOT + "/";
        String directory = destination.getCanonicalPath() + File.separator;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        int count = 0;
        long bytes = 0;
        byte[] buffer = new byte[65536];
        try (InputStream asset = context.getAssets().open(BuildConfig.NODE_CLI_ARCHIVE);
             DigestInputStream digesting = new DigestInputStream(asset, digest);
             ZipInputStream zip = new ZipInputStream(digesting)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.getName().startsWith(prefix) || entry.isDirectory()) {
                    throw new IOException("Unexpected bundled npm entry: " + entry.getName());
                }
                File target = new File(destination, entry.getName().substring(prefix.length()));
                if (!target.getCanonicalPath().startsWith(directory)) throw new IOException("Invalid bundled npm path");
                mkdirs(target.getParentFile());
                try (FileOutputStream output = new FileOutputStream(target)) {
                    int size;
                    while ((size = zip.read(buffer)) != -1) { output.write(buffer, 0, size); bytes += size; }
                }
                count++;
            }
            while (digesting.read(buffer) != -1) { /* Include the ZIP central directory in the digest. */ }
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        if (!BuildConfig.NODE_CLI_ARCHIVE_SHA256.contentEquals(hex) ||
                count != BuildConfig.NODE_CLI_ARCHIVE_ENTRY_COUNT || bytes != BuildConfig.NODE_CLI_ARCHIVE_BYTES) {
            throw new IOException("Bundled npm archive does not match the CLI asset lock");
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
