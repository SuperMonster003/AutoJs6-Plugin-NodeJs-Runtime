package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class WorkerDesktopSmokeTest {
    private Context context;
    private File root;
    private INodeJsRuntimePlugin runtime;
    private ServiceConnection connection;

    @Before public void connect() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        root = new File(context.getCacheDir(), "worker-desktop/" + UUID.randomUUID());
        assertTrue(root.mkdirs());
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBinder> binder = new AtomicReference<>();
        connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {
                binder.set(service); connected.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name) {}
        };
        assertTrue(context.bindService(new Intent().setComponent(new ComponentName(context.getPackageName(),
                context.getPackageName() + ".NodeJsRuntimePluginService")), connection, Context.BIND_AUTO_CREATE));
        assertTrue(connected.await(30, TimeUnit.SECONDS));
        runtime = INodeJsRuntimePlugin.Stub.asInterface(binder.get());
        assertNotNull(runtime);
    }

    @After public void disconnect() {
        if (connection != null) context.unbindService(connection);
        if (root != null) {
            deleteTree(root);
            deleteTree(new File(root.getParentFile(), root.getName() + ".zip"));
            deleteTree(new File(root.getParentFile(), root.getName() + "-output.zip"));
        }
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private void source(String name, String content) throws Exception {
        File file = new File(root, name);
        assertTrue(file.getParentFile().mkdirs() || file.getParentFile().isDirectory());
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private String run(String name, String source, String policy, boolean network) throws Exception {
        source(name, source);
        File inputArchive = new File(root.getParentFile(), root.getName() + ".zip");
        try (var zip = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(inputArchive));
             var files = Files.walk(root.toPath())) {
            org.json.JSONArray names = new org.json.JSONArray();
            for (var path : (Iterable<java.nio.file.Path>) files.filter(Files::isRegularFile)::iterator) {
                String relative = root.toPath().relativize(path).toString();
                names.put(relative);
                zip.putNextEntry(new java.util.zip.ZipEntry(relative));
                Files.copy(path, zip);
                zip.closeEntry();
            }
            zip.putNextEntry(new java.util.zip.ZipEntry(".autojs6-workspace-transport/input-manifest-v1.json"));
            zip.write(new org.json.JSONObject().put("version", 1).put("deleteEligibleFiles", names)
                    .toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Bundle request = new Bundle();
        request.putString(NodeJsRuntimeContract.KEY_SOURCE, source);
        request.putString(NodeJsRuntimeContract.KEY_SOURCE_NAME, new File(root, name).getAbsolutePath());
        request.putString(NodeJsRuntimeContract.KEY_WORKING_DIRECTORY, root.getAbsolutePath());
        request.putString(NodeJsRuntimeContract.KEY_SANDBOX_ROOT, root.getAbsolutePath());
        request.putBoolean(NodeJsRuntimeContract.KEY_RAW_NODE_NETWORK_MODULES_ENABLED, network);
        request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 45_000L);
        if (policy != null) {
            request.putStringArray(NodeJsRuntimeContract.KEY_RUNTIME_MODULE_SOURCE_NAMES, new String[]{"autojs6:worker-policy"});
            request.putStringArray(NodeJsRuntimeContract.KEY_RUNTIME_MODULE_SOURCES, new String[]{policy});
        }
        request.putInt(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_TRANSPORT_VERSION,
                NodeJsRuntimeContract.WORKSPACE_ARCHIVE_TRANSPORT_CONTRACT_VERSION);
        request.putString(NodeJsRuntimeContract.KEY_WORKSPACE_RELATIVE_WORKING_DIRECTORY, "");
        Bundle result;
        try (var input = ParcelFileDescriptor.open(inputArchive, ParcelFileDescriptor.MODE_READ_ONLY);
             var output = ParcelFileDescriptor.open(new File(root.getParentFile(), root.getName() + "-output.zip"),
                     ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE)) {
            request.putParcelable(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_INPUT_FD, input);
            request.putParcelable(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_OUTPUT_FD, output);
            result = runtime.runScript(request, null);
        }
        assertNotNull(result);
        String output = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
        assertTrue(result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "") + " / "
                        + result.getString(NodeJsRuntimeContract.KEY_STDERR, "") + " / " + output,
                result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
        Bundle status = new Bundle();
        status.putString("stream", "\n" + output);
        InstrumentationRegistry.getInstrumentation().sendStatus(0, status);
        return output;
    }

    @Test public void workersUseNativeBuiltinsNetworkAndUserResourceLimits() throws Exception {
        source("worker.cjs", """
                const assert = require('node:assert/strict');
                const { Buffer } = require('node:buffer');
                const { parentPort, resourceLimits, workerData } = require('node:worker_threads');
                (async () => {
                  for (const name of workerData.builtins) assert.ok(require(name), name);
                  assert.strictEqual(resourceLimits.maxOldGenerationSizeMb, 192);
                  assert.strictEqual(require('node:module').createRequire(__filename)('node:crypto'), require('node:crypto'));
                  const fs = require('node:fs');
                  assert.throws(() => fs.readFileSync('/proc/self/status'), e => e.code === 'ERR_AUTOJS6_FS_PATH_ESCAPE');
                  assert.strictEqual(fs.readFileSync(workerData.outside,'utf8'),'outside');
                  for (const name of ['autojs6:fetch','autojs6:websocket','sqlite','ui','inspector','node:inspector']) {
                    assert.throws(() => require(name), e => e.code === 'ERR_AUTOJS6_WORKER_BRIDGE_DENIED');
                  }
                  const {DatabaseSync}=require('node:sqlite');
                  const db=new DatabaseSync(':memory:');
                  try { assert.strictEqual(db.prepare('SELECT 42 AS value').get().value,42); assert.strictEqual(db.isOpen,true); }
                  finally { db.close(); }
                  assert.throws(()=>new DatabaseSync('/proc/self/status'),e=>e.code==='ERR_AUTOJS6_FS_PATH_ESCAPE');
                  const http = require('node:http');
                  const server = http.createServer((req,res)=>res.end('worker-native'));
                  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
                  try {
                    const response = await fetch('http://127.0.0.1:'+server.address().port);
                    assert.strictEqual(await response.text(),'worker-native');
                  } finally { server.closeAllConnections(); await new Promise(resolve=>server.close(resolve)); }
                  parentPort.postMessage('native-pass');
                })().catch(e=>{setImmediate(()=>{throw e;});});
                """);
        Files.write(new File(root.getParentFile(), "outside.txt").toPath(), "outside".getBytes(StandardCharsets.UTF_8));
        String output = run("main.cjs", """
                (async () => {
                  const assert=require('node:assert/strict'), wt=require('node:worker_threads');
                  // Android may change CPU affinity between bootstrap and this assertion.
                  assert.ok(Number.isInteger(wt.policy.maxWorkers) && wt.policy.maxWorkers>=1 && wt.policy.maxWorkers<=8);
                  assert.strictEqual(wt.policy.workerPool.taskTimeoutMs,0);
                  const builtins = require('node:module').builtinModules.filter(name=>name!=='inspector');
                  const workers = Array.from({length:4},()=>new wt.Worker(__dirname+'/worker.cjs',{
                    resourceLimits:{maxOldGenerationSizeMb:192},workerData:{builtins,outside:OUTSIDE_PATH}
                  }));
                  try {
                    const values=await Promise.all(workers.map(w=>new Promise((resolve,reject)=>{
                      w.once('message',resolve);w.once('error',reject);
                    })));
                    assert.deepStrictEqual(values,Array(4).fill('native-pass'));
                    console.log('m13.workers.native=PASS');
                  } finally { await Promise.all(workers.map(w=>w.terminate())); }
                })().catch(e=>{console.error(e.stack);process.exitCode=1;});
                """.replace("OUTSIDE_PATH", org.json.JSONObject.quote(new File(root.getParentFile(), "outside.txt").getAbsolutePath())), null, true);
        assertTrue(output.contains("m13.workers.native=PASS"));
    }

    @Test public void requestCapsApplyAndWorkerNetworkCanBeDisabled() throws Exception {
        source("worker.cjs", """
                const assert=require('assert/strict'), wt=require('worker_threads');
                (async()=>{
                  assert.strictEqual(wt.resourceLimits.maxOldGenerationSizeMb,wt.workerData.expected);
                  assert.throws(()=>require('node:http'),e=>e.code==='ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED');
                  await assert.rejects(fetch('http://127.0.0.1:9'),e=>e.code==='ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED');
                  assert.throws(()=>new WebSocket('ws://127.0.0.1:9'),e=>e.code==='ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED');
                  wt.parentPort.postMessage('policy-pass');
                })().catch(e=>{setImmediate(()=>{throw e;});});
                """);
        String output = run("main.cjs", """
                (async()=>{
                  const assert=require('assert/strict'), wt=require('worker_threads');
                  assert.strictEqual(wt.policy.maxWorkers,1);
                  assert.strictEqual(wt.policy.workerPool.taskTimeoutMs,200);
                  assert.throws(()=>new wt.Worker(__dirname+'/worker.cjs',{resourceLimits:{maxOldGenerationSizeMb:192}}));
                  for (const expected of [96,80]) {
                    const options={workerData:{expected}};
                    if(expected===80) options.resourceLimits={maxOldGenerationSizeMb:80};
                    const worker=new wt.Worker(__dirname+'/worker.cjs',options);
                    assert.throws(()=>new wt.Worker(__dirname+'/worker.cjs'),e=>e.code==='ERR_AUTOJS6_WORKER_LIMIT_EXCEEDED');
                    try { assert.strictEqual(await new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);}), 'policy-pass'); }
                    finally { await worker.terminate(); }
                  }
                  console.log('m13.workers.policy=PASS');
                })().catch(e=>{console.error(e.stack);process.exitCode=1;});
                """, "{\"maxWorkers\":1,\"taskTimeoutMs\":200,\"resourceLimits\":{\"maxOldGenerationSizeMb\":96}}", false);
        assertTrue(output.contains("m13.workers.policy=PASS"));
    }

    @Test public void poolHasNoDefaultTaskTimeoutAndCleansUpExplicitTimeout() throws Exception {
        source("pool.cjs", """
                const {parentPort}=require('worker_threads');
                parentPort.on('message',task=>setTimeout(()=>parentPort.postMessage({__autojs6PoolTaskId:task.__autojs6PoolTaskId,value:42}),task.value));
                """);
        String output = run("main.cjs", """
                (async()=>{
                  const assert=require('assert/strict'), {WorkerPool}=require('worker_threads');
                  const pool=new WorkerPool(__dirname+'/pool.cjs',{maxWorkers:1});
                  try {
                    assert.strictEqual(await pool.run(5500),42);
                    await assert.rejects(pool.run(1000,{timeoutMs:50}),e=>e.code==='ERR_AUTOJS6_WORKER_POOL_TASK_TIMEOUT');
                    assert.strictEqual(await pool.run(0),42);
                    console.log('m13.workers.pool=PASS');
                  } finally { await pool.close(); }
                })().catch(e=>{console.error(e.stack);process.exitCode=1;});
                """, null, true);
        assertTrue(output.contains("m13.workers.pool=PASS"));
    }

    @Test public void publishedCpuAndWasmSamplesRunInNativeWorkers() throws Exception {
        AssetManager assets = InstrumentationRegistry.getInstrumentation().getContext().getAssets();
        for (String example : new String[]{"worker-cpu", "wasm-worker"}) {
            for (String file : assets.list(example)) {
                if (file.endsWith(".cjs")) {
                    try (var input = assets.open(example + "/" + file)) {
                        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                        byte[] buffer = new byte[4096];
                        int count;
                        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                        source(example + "/" + file, new String(output.toByteArray(), StandardCharsets.UTF_8));
                    }
                }
            }
            String entry = new String(Files.readAllBytes(new File(root, example + "/main.cjs").toPath()), StandardCharsets.UTF_8);
            assertTrue(run(example + "/main.cjs", entry, null, true).contains("sample." + example + "=PASS"));
        }
    }

    @Test public void esmWorkersFileUrlsAndTransferListsRemainUsable() throws Exception {
        source("value.mjs", "export const value = 42;");
        source("worker.mjs", """
                import { parentPort, workerData } from 'node:worker_threads';
                import { value } from './value.mjs';
                workerData.port.postMessage(new Uint8Array(workerData.buffer)[0]);
                workerData.port.close();
                parentPort.postMessage(value);
                parentPort.close();
                """);
        assertTrue(run("main.cjs", """
                (async()=>{
                  const assert=require('assert/strict'), {Worker,MessageChannel}=require('worker_threads');
                  const {port1,port2}=new MessageChannel();
                  const buffer=new Uint8Array([7]).buffer;
                  const worker=new Worker(require('url').pathToFileURL(__dirname+'/worker.mjs'),{
                    workerData:{port:port2,buffer},transferList:[port2,buffer]
                  });
                  try {
                    assert.strictEqual(buffer.byteLength,0);
                    const got=await Promise.all([
                      new Promise((resolve,reject)=>{worker.once('message',resolve);worker.once('error',reject);}),
                      new Promise(resolve=>port1.once('message',resolve))
                    ]);
                    assert.deepStrictEqual(got,[42,7]);
                    console.log('m13.workers.transfer=PASS');
                  } finally { port1.close(); await worker.terminate(); }
                })().catch(e=>{console.error(e.stack);process.exitCode=1;});
                """, null, true).contains("m13.workers.transfer=PASS"));
    }
}
