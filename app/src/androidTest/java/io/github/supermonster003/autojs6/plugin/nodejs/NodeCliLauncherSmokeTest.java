package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.system.Os;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Runs the packaged multi-call launcher the way the AutoJs6 terminal does: directly from the
 * installed native library directory, with npm / corepack extracted from the asset archive and
 * reached through command-named symlinks. The instrumentation process shares the plugin uid, so
 * this covers the ELF, RUNPATH and script wiring; cross-uid execution is verified by the host.
 * zh-CN: 以 AutoJs6 终端的方式运行打包的多入口启动器: 直接从安装后的 native 库目录执行, npm /
 * corepack 从资产解压并经命令名符号链接调用. 测试进程与插件同 uid, 覆盖 ELF / RUNPATH / 脚本接线;
 * 跨 uid 执行由宿主验证.
 */
@RunWith(AndroidJUnit4.class)
public final class NodeCliLauncherSmokeTest {

    private static final long TIMEOUT_SECONDS = 120;

    private Context context;
    private File launcher;
    private File workDir;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        launcher = new File(context.getApplicationInfo().nativeLibraryDir, BuildConfig.NODE_CLI_EXECUTABLE);
        assertTrue("launcher is not installed: " + launcher, launcher.isFile());
        workDir = new File(context.getCacheDir(), "node-cli-smoke");
        deleteRecursively(workDir);
        assertTrue(workDir.mkdirs());
    }

    @Test
    public void launcherReportsThePinnedNodeVersion() throws Exception {
        Result result = run(launcher, Arrays.asList("--version"), null);
        assertEquals(result.describe(), 0, result.exitCode);
        assertEquals("v" + NodeJsRuntimePluginService.NODE_VERSION, result.stdout.trim());
    }

    @Test
    public void launcherEvaluatesInlineScripts() throws Exception {
        Result result = run(launcher, Arrays.asList("-e", "console.log(6 * 7, process.execPath === require('fs').realpathSync('/proc/self/exe'))"), null);
        assertEquals(result.describe(), 0, result.exitCode);
        assertEquals("42 true", result.stdout.trim());
    }

    @Test
    public void runtimeProvidesIntlAndUnicodePropertyEscapes() throws Exception {
        Result result = run(launcher, Arrays.asList("-p",
                "typeof Intl + ' ' + /\\p{Lu}/u.test('A') + ' ' + process.versions.icu.split('.')[0]"), null);
        assertEquals(result.describe(), 0, result.exitCode);
        assertEquals("object true 78", result.stdout.trim());
    }

    @Test
    public void fatalErrorsReachStderr() throws Exception {
        Result result = run(launcher, Arrays.asList("-e", "throw new Error('stderr-marker-7f3a')"), null);
        assertEquals(result.describe(), 1, result.exitCode);
        assertTrue(result.describe(), result.stderr.contains("stderr-marker-7f3a"));
    }

    @Test
    public void npmCommandsFailClearlyWithoutTheCliRoot() throws Exception {
        File npm = link("npm");
        Result result = run(npm, Arrays.asList("--version"), null);
        assertEquals(result.describe(), 1, result.exitCode);
        assertTrue(result.describe(), result.stderr.contains("AUTOJS6_NODE_CLI_ROOT"));
    }

    @Test
    public void npmAndCorepackRunFromTheExtractedArchive() throws Exception {
        File root = extractArchive();
        Map<String, String> env = new java.util.HashMap<>();
        env.put("AUTOJS6_NODE_CLI_ROOT", root.getAbsolutePath());
        env.put("HOME", workDir.getAbsolutePath());
        env.put("TMPDIR", workDir.getAbsolutePath());
        env.put("npm_config_cache", new File(workDir, ".npm").getAbsolutePath());
        env.put("npm_config_update_notifier", "false");
        env.put("COREPACK_HOME", new File(workDir, ".corepack").getAbsolutePath());
        env.put("COREPACK_ENABLE_NETWORK", "0");

        Result npm = run(link("npm"), Arrays.asList("--version"), env);
        assertEquals(npm.describe(), 0, npm.exitCode);
        assertEquals(BuildConfig.NODE_CLI_NPM_VERSION, npm.stdout.trim());

        Result npx = run(link("npx"), Arrays.asList("--version"), env);
        assertEquals(npx.describe(), 0, npx.exitCode);
        assertEquals(BuildConfig.NODE_CLI_NPM_VERSION, npx.stdout.trim());

        Result corepack = run(link("corepack"), Arrays.asList("--version"), env);
        assertEquals(corepack.describe(), 0, corepack.exitCode);
        assertEquals(BuildConfig.NODE_CLI_COREPACK_VERSION, corepack.stdout.trim());

        // A name that is not in the launcher table behaves like node (argv untouched).
        Result node = run(link("node"), Arrays.asList("-p", "process.argv.length"), env);
        assertEquals(node.describe(), 0, node.exitCode);
        assertEquals("1", node.stdout.trim());
    }

    private File link(String name) throws Exception {
        File bin = new File(workDir, "bin");
        if (!bin.isDirectory()) {
            assertTrue(bin.mkdirs());
        }
        File target = new File(bin, name);
        if (!target.exists()) {
            Os.symlink(launcher.getAbsolutePath(), target.getAbsolutePath());
        }
        return target;
    }

    private File extractArchive() throws Exception {
        String rootPrefix = BuildConfig.NODE_CLI_ARCHIVE_ROOT + "/";
        File destination = new File(workDir, "cli");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        int entries = 0;
        try (InputStream asset = context.getAssets().open(BuildConfig.NODE_CLI_ARCHIVE);
             DigestInputStream digesting = new DigestInputStream(asset, digest);
             ZipInputStream zip = new ZipInputStream(digesting)) {
            ZipEntry entry;
            byte[] buffer = new byte[1 << 16];
            while ((entry = zip.getNextEntry()) != null) {
                assertTrue("entry outside the root: " + entry.getName(), entry.getName().startsWith(rootPrefix));
                File file = new File(destination, entry.getName().substring(rootPrefix.length()));
                assertTrue(file.getCanonicalPath().startsWith(destination.getCanonicalPath() + File.separator));
                File parent = file.getParentFile();
                if (!parent.isDirectory()) {
                    assertTrue(parent.mkdirs());
                }
                try (OutputStream out = new FileOutputStream(file)) {
                    int read;
                    while ((read = zip.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                    }
                }
                entries++;
            }
            // Drain the trailing central directory so the digest covers the whole asset.
            while (digesting.read(buffer) > 0) {
                // consume
            }
        }
        assertEquals(BuildConfig.NODE_CLI_ARCHIVE_ENTRY_COUNT, entries);
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format("%02x", b & 0xff));
        }
        assertEquals(BuildConfig.NODE_CLI_ARCHIVE_SHA256, hex.toString());
        return destination;
    }

    private Result run(File executable, List<String> arguments, Map<String, String> extraEnvironment) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(executable.getAbsolutePath());
        command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command).directory(workDir);
        Map<String, String> environment = builder.environment();
        environment.remove("LD_LIBRARY_PATH");
        environment.remove("LD_PRELOAD");
        environment.remove("NODE_OPTIONS");
        if (extraEnvironment != null) {
            environment.putAll(extraEnvironment);
        }
        Process process = builder.start();
        byte[][] captured = new byte[2][];
        Thread stdout = capture(process.getInputStream(), captured, 0);
        Thread stderr = capture(process.getErrorStream(), captured, 1);
        process.getOutputStream().close();
        assertTrue("timed out: " + command, process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        stdout.join();
        stderr.join();
        return new Result(process.exitValue(),
                new String(captured[0], StandardCharsets.UTF_8),
                new String(captured[1], StandardCharsets.UTF_8));
    }

    private static Thread capture(InputStream stream, byte[][] into, int index) {
        Thread thread = new Thread(() -> {
            try (InputStream input = stream) {
                // InputStream#readAllBytes needs API 33; the plugin still runs on API 24+.
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    bytes.write(buffer, 0, read);
                }
                into[index] = bytes.toByteArray();
            } catch (IOException e) {
                into[index] = e.toString().getBytes(StandardCharsets.UTF_8);
            }
        });
        thread.start();
        return thread;
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static final class Result {
        final int exitCode;
        final String stdout;
        final String stderr;

        Result(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        String describe() {
            return "exit=" + exitCode + "\nstdout:\n" + stdout + "\nstderr:\n" + stderr;
        }
    }
}
