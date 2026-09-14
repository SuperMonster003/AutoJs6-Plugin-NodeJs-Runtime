package io.github.supermonster003.autojs6.plugin.nodejs;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The npm / corepack asset archive, the lock-derived BuildConfig facts and the launcher's
 * script table must agree; the AutoJs6 host trusts exactly these values.
 * zh-CN: npm / corepack 资产、由 lock 生成的 BuildConfig 事实与启动器脚本表必须一致; 宿主信任的正是这些值.
 */
public class NodeCliArchiveTest {

    private static final String ROOT = BuildConfig.NODE_CLI_ARCHIVE_ROOT + "/";
    private static final Pattern VERSION = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");
    // Mirrors kCommands in app/src/main/cpp/node_cli_main.cpp.
    private static final List<String> LAUNCHER_SCRIPTS = Arrays.asList(
            "npm/bin/npm-cli.js",
            "npm/bin/npx-cli.js",
            "corepack/dist/corepack.js",
            "corepack/dist/yarn.js",
            "corepack/dist/yarnpkg.js",
            "corepack/dist/pnpm.js",
            "corepack/dist/pnpx.js"
    );

    private static File archiveFile() {
        File file = new File("src/main/assets/" + BuildConfig.NODE_CLI_ARCHIVE);
        assertTrue("archive is missing: " + file.getAbsolutePath(), file.isFile());
        return file;
    }

    @Test
    public void archiveDigestAndSizeMatchTheLockDerivedBuildConfig() throws Exception {
        File file = archiveFile();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream stream = new FileInputStream(file)) {
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = stream.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format("%02x", b & 0xff));
        }
        assertEquals(BuildConfig.NODE_CLI_ARCHIVE_SHA256, hex.toString());
        assertTrue(BuildConfig.NODE_CLI_ARCHIVE_SHA256.matches("[0-9a-f]{64}"));

        long uncompressed = 0;
        int count = 0;
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                count++;
                uncompressed += entry.getSize();
            }
        }
        assertEquals(BuildConfig.NODE_CLI_ARCHIVE_ENTRY_COUNT, count);
        assertEquals(BuildConfig.NODE_CLI_ARCHIVE_BYTES, uncompressed);
    }

    @Test
    public void everyEntryStaysBelowTheArchiveRootAndOnlyContainsNpmAndCorepack() throws Exception {
        Set<String> packages = new HashSet<>();
        try (ZipFile zip = new ZipFile(archiveFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                assertFalse("directory entries are not expected: " + name, entry.isDirectory());
                assertTrue("outside the root: " + name, name.startsWith(ROOT));
                assertFalse("unsafe entry: " + name, name.contains("..") || name.contains("\\") || name.contains("//"));
                assertFalse("documentation should be pruned: " + name,
                        name.startsWith(ROOT + "npm/docs/") || name.startsWith(ROOT + "npm/man/"));
                packages.add(name.substring(ROOT.length()).split("/", 2)[0]);
            }
        }
        assertEquals(new HashSet<>(Arrays.asList("npm", "corepack")), packages);
    }

    @Test
    public void launcherScriptsAndDeclaredVersionsArePresent() throws Exception {
        try (ZipFile zip = new ZipFile(archiveFile())) {
            for (String script : LAUNCHER_SCRIPTS) {
                assertNotNull("launcher script missing: " + script, zip.getEntry(ROOT + script));
            }
            assertNotNull(zip.getEntry(ROOT + "npm/LICENSE"));
            assertEquals(BuildConfig.NODE_CLI_NPM_VERSION, packageVersion(zip, "npm"));
            assertEquals(BuildConfig.NODE_CLI_COREPACK_VERSION, packageVersion(zip, "corepack"));
        }
    }

    @Test
    public void declaredCommandsCoverTheLauncherTableAndNode() {
        List<String> commands = new ArrayList<>(Arrays.asList(BuildConfig.NODE_CLI_COMMANDS.split(",")));
        assertEquals("node", commands.remove(0));
        List<String> scripted = new ArrayList<>();
        for (String script : LAUNCHER_SCRIPTS) {
            String file = script.substring(script.lastIndexOf('/') + 1);
            scripted.add(file.replace("-cli.js", "").replace(".js", ""));
        }
        assertEquals(scripted, commands);
        assertEquals("libnodexe.so", BuildConfig.NODE_CLI_EXECUTABLE);
        assertEquals("lib/node_modules", BuildConfig.NODE_CLI_ARCHIVE_ROOT);
    }

    private static String packageVersion(ZipFile zip, String packageName) throws Exception {
        ZipEntry entry = zip.getEntry(ROOT + packageName + "/package.json");
        assertNotNull(packageName + "/package.json is missing", entry);
        try (InputStream stream = zip.getInputStream(entry)) {
            String json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            Matcher matcher = VERSION.matcher(json);
            assertTrue(packageName + " version is missing", matcher.find());
            return matcher.group(1);
        }
    }
}
