package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.*;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.autojs.plugin.nodejs.api.INodeJsModuleSourceProvider;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Runs real Android Node-API binaries through workspace transport and the published Binder. */
@RunWith(AndroidJUnit4.class)
public final class NativeAddonSmokeTest {
    @Test public void loadsAddonInMainAndWorkersAndReportsNativeLoaderErrors() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AssetManager assets = InstrumentationRegistry.getInstrumentation().getContext().getAssets();
        File root = new File(context.getCacheDir(), "native-addon-" + UUID.randomUUID());
        assertTrue(root.mkdirs());
        File workspace = new File(root, "project");
        copyAssets(assets, "native-addon", workspace);
        File projectFile = new File(workspace, "project.json");
        JSONObject project = new JSONObject(new String(Files.readAllBytes(projectFile.toPath()), StandardCharsets.UTF_8));
        project.put("node", new JSONObject().put("permissions",
                new JSONArray().put("plugins").put("plugin.org.example.native_addon")));
        Files.write(projectFile.toPath(), project.toString().getBytes(StandardCharsets.UTF_8));
        File pluginDir = new File(workspace, "plugins/org.example.native_addon");
        copyAssets(assets, "native-addon", pluginDir);
        Files.write(new File(pluginDir, "package.json").toPath(), """
                {"name":"org.example.native_addon","version":"1.0.0","main":"index.cjs",
                 "autojs6":{"plugin":{"id":"org.example.native_addon","permissions":[],"dependencies":[],"exports":{}}}}
                """.getBytes(StandardCharsets.UTF_8));
        File input = new File(root, "input.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(input.toPath()));
             var paths = Files.walk(workspace.toPath())) {
            JSONArray names = new JSONArray();
            for (var path : (Iterable<java.nio.file.Path>) paths.filter(Files::isRegularFile)::iterator) {
                String relative = workspace.toPath().relativize(path).toString().replace(File.separatorChar, '/');
                names.put(relative);
                zip.putNextEntry(new ZipEntry(relative));
                Files.copy(path, zip);
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry(".autojs6-workspace-transport/input-manifest-v1.json"));
            zip.write(new JSONObject().put("version", 1).put("deleteEligibleFiles", names).toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBinder> binder = new AtomicReference<>();
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) { binder.set(service); connected.countDown(); }
            @Override public void onServiceDisconnected(ComponentName name) {}
        };
        assertTrue(context.bindService(new Intent().setComponent(new ComponentName(context.getPackageName(),
                context.getPackageName() + ".NodeJsRuntimePluginService")), connection, Context.BIND_AUTO_CREATE));
        try {
            assertTrue(connected.await(30, TimeUnit.SECONDS));
            INodeJsRuntimePlugin runtime = INodeJsRuntimePlugin.Stub.asInterface(binder.get());
            AtomicInteger binaryProviderCalls = new AtomicInteger();
            INodeJsModuleSourceProvider provider = new INodeJsModuleSourceProvider.Stub() {
                @Override public Bundle resolveModuleSource(Bundle request) {
                    String path = request.getString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_PATH, "");
                    if (path.endsWith(".node")) binaryProviderCalls.incrementAndGet();
                    Bundle response = new Bundle();
                    response.putInt(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION,
                            NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_CONTRACT_VERSION);
                    response.putString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_REQUEST_ID,
                            request.getString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_REQUEST_ID));
                    response.putString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_OPERATION,
                            request.getString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_OPERATION));
                    response.putString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_STATUS,
                            new File(path).isFile() ? NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_NOT_ENCRYPTED
                                    : NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_NOT_FOUND);
                    response.putString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_RESOLVED_PATH, path);
                    response.putLong(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_SOURCE_BYTES, 0L);
                    return response;
                }
                @Override public Bundle getNativeDiagnostics() { return new Bundle(); }
                @Override public void cancel(String reason) {}
            };
            int previousPid = -1;
            for (int iteration = 0; iteration < 3; iteration++) {
                Bundle request = new Bundle();
                request.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, "addon-" + UUID.randomUUID());
                request.putString(NodeJsRuntimeContract.KEY_SOURCE_NAME, new File(workspace, "main.cjs").getAbsolutePath());
                request.putString(NodeJsRuntimeContract.KEY_WORKING_DIRECTORY, workspace.getAbsolutePath());
                request.putString(NodeJsRuntimeContract.KEY_SANDBOX_ROOT, workspace.getAbsolutePath());
                request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 20_000L);
                request.putBinder(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER, provider.asBinder());
                request.putString(NodeJsRuntimeContract.KEY_SOURCE, """
                        const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
                        (async()=>{
                          const addon=require('./index.cjs');
                          assert.equal(addon.hello(),'Hello from Node-API');
                          assert.equal(addon.add(20,22),42);
                          assert.throws(()=>addon.add('20',22),{code:'ERR_INVALID_ARG_TYPE'});
                          const filename=path.join(__dirname,'prebuilds',addon.abi,'hello.node');
                          assert.equal(require(filename.slice(0,-5)),addon);
                          assert.equal(require.cache[require.resolve(filename)].exports,addon);
                          const loaded={exports:{}};
                          process.dlopen(loaded,filename);
                          assert.equal(loaded.exports.add(40,2),42);
                          const pluginId='org.example.native_addon';
                          const plugins=require('plugins');
                          plugins.install(pluginId,{source:'./plugins/'+pluginId});
                          try {assert.equal(plugins.load(pluginId).add(20,22),42);}
                          finally {await plugins.unload(pluginId);}
                          const wrongAbi=addon.abi==='x86_64'?'arm64-v8a':'x86_64';
                          assert.throws(()=>require('./prebuilds/'+wrongAbi+'/hello.node'),e=>e.code==='ERR_DLOPEN_FAILED' && !e.autojs6Code);
                          fs.writeFileSync('invalid.node','not an ELF library');
                          assert.throws(()=>require('./invalid.node'),e=>e.code==='ERR_DLOPEN_FAILED' && !e.autojs6Code);
                          const {Worker}=require('node:worker_threads');
                          const code="const {parentPort,workerData}=require('node:worker_threads');const addon=require(workerData);parentPort.postMessage(addon.add(19,23));";
                          const worker=new Worker(code,{eval:true,workerData:filename});
                          try {assert.equal(await new Promise((r,j)=>{worker.once('message',r);worker.once('error',j);}),42);}
                          finally {await worker.terminate();}
                          const profile=require('autojs6:profile');
                          assert.equal(profile.nativeAddonProfile.processDlopen,'native');
                          assert.equal(profile.packageIntegrityProfile.unsignedNativeAddonLoad,true);
                          assert.equal(addon.add(21,21),42);
                          console.log('m23.addon='+addon.abi+':PASS');
                        })().catch(error=>{console.error(error.stack);process.exitCode=1;});
                        """);
                request.putInt(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_TRANSPORT_VERSION,
                        NodeJsRuntimeContract.WORKSPACE_ARCHIVE_TRANSPORT_CONTRACT_VERSION);
                request.putString(NodeJsRuntimeContract.KEY_WORKSPACE_RELATIVE_WORKING_DIRECTORY, "");
                Bundle result;
                try (var in = ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY);
                     var out = ParcelFileDescriptor.open(new File(root, "output.zip"), ParcelFileDescriptor.MODE_CREATE |
                             ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_TRUNCATE)) {
                    request.putParcelable(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_INPUT_FD, in);
                    request.putParcelable(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_OUTPUT_FD, out);
                    result = runtime.runScript(request, null);
                }
                assertNotNull(result);
                String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
                assertTrue(result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "") + "\n"
                                + result.getString(NodeJsRuntimeContract.KEY_STDERR, "") + "\n" + stdout,
                        result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
                assertTrue(stdout, stdout.contains(":PASS"));
                int pid = result.getInt(NodeJsRuntimeContract.KEY_PID, -1);
                assertTrue(pid > 0);
                if (previousPid != -1) assertEquals("runtime restarted after addon load/error", previousPid, pid);
                previousPid = pid;
            }
            assertEquals("binary addons must bypass the JavaScript source provider", 0, binaryProviderCalls.get());
        } finally {
            context.unbindService(connection);
            deleteTree(root);
        }
    }

    private static void copyAssets(AssetManager assets, String name, File target) throws Exception {
        String[] children = assets.list(name);
        if (children != null && children.length > 0) {
            assertTrue(target.mkdirs() || target.isDirectory());
            for (String child : children) copyAssets(assets, name + "/" + child, new File(target, child));
        } else {
            try (var source = assets.open(name); var output = Files.newOutputStream(target.toPath())) {
                byte[] buffer = new byte[8192]; int size;
                while ((size = source.read(buffer)) != -1) output.write(buffer, 0, size);
            }
        }
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
