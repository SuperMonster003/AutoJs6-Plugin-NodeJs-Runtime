package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Native builtin behavior and filesystem policy coexist after M13.1. */
@RunWith(AndroidJUnit4.class)
public final class BuiltinPassthroughSmokeTest {

    private static final long BIND_TIMEOUT_MS = 30_000L;

    @Test
    public void nativeBuiltinsExposeFullExportsAndKeepProcReadsDenied() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext()
                .getApplicationContext();
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBinder> binder = new AtomicReference<>();
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                binder.set(service);
                connected.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };
        ComponentName component = new ComponentName(
                context.getPackageName(),
                context.getPackageName() + ".NodeJsRuntimePluginService"
        );
        assertTrue(
                "bindService was rejected",
                context.bindService(new Intent().setComponent(component), connection, Context.BIND_AUTO_CREATE)
        );
        try {
            assertTrue(
                    "bind timed out",
                    connected.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            );
            INodeJsRuntimePlugin runtime = INodeJsRuntimePlugin.Stub.asInterface(binder.get());
            assertNotNull("runtime proxy unavailable", runtime);

            Bundle request = new Bundle();
            request.putString(
                    NodeJsRuntimeContract.KEY_SOURCE,
                    """
                    (async () => {
                      const assert = require('node:assert/strict');
                      const builtins = ['buffer','events','path','path/posix','path/win32','util','util/types',
                        'url','querystring','string_decoder','assert','assert/strict','punycode','stream',
                        'stream/promises','stream/web','stream/consumers','zlib','timers','timers/promises',
                        'perf_hooks','async_hooks','diagnostics_channel','v8','vm','tty','readline','readline/promises','crypto','constants'];
                      for (const name of builtins) assert.strictEqual(require(name), require('node:' + name), name);
                      // M23.2: Node's own builtin inventory, no denylist; only execution gates and two thin wrappers
                      // (module for createRequire until M23.3, console for stdout capture) remain.
                      const Module = require('node:module');
                      const wrapped = new Set(['module', 'console', 'worker_threads', 'child_process', 'inspector', 'inspector/promises']);
                      for (const id of Module.builtinModules) {
                        if (wrapped.has(id)) continue;
                        // A builtin that Node itself refuses (trace_events without tracing support) must refuse
                        // identically through require() and process.getBuiltinModule(), with Node's own code.
                        let viaRequire, viaGet, requireError = null, getError = null;
                        try { viaRequire = require(id); } catch (error) { requireError = error; }
                        try { viaGet = process.getBuiltinModule(id); } catch (error) { getError = error; }
                        if (requireError || getError) {
                          assert.ok(requireError && getError && requireError.code === getError.code && !String(requireError.code).startsWith('ERR_AUTOJS6'),
                            id + ': ' + String((requireError || getError).message));
                          continue;
                        }
                        assert.strictEqual(viaRequire, viaGet, id);
                      }
                      for (const id of ['cluster', 'domain', 'repl', 'wasi', 'constants', 'node:sea']) assert.ok(require(id), id);
                      try { assert.strictEqual(typeof require('trace_events').createTracing, 'function'); }
                      catch (error) { assert.strictEqual(error.code, 'ERR_TRACE_EVENTS_UNAVAILABLE'); }
                      assert.strictEqual(Module.isBuiltin('cluster'), true);
                      assert.strictEqual(Module.isBuiltin('node:sqlite'), true);
                      assert.strictEqual(Module.isBuiltin('sqlite'), false);
                      assert.strictEqual(typeof Module.registerHooks, 'function');
                      assert.strictEqual(typeof Module.enableCompileCache, 'function');
                      assert.strictEqual(require.resolve('node:fs'), 'node:fs');
                      assert.strictEqual(typeof process.umask(), 'number');
                      assert.strictEqual(typeof process.report.getReport().header.nodejsVersion, 'string');
                      assert.strictEqual(typeof process.binding, 'function');
                      assert.strictEqual(typeof process.kill, 'function');
                      assert.strictEqual(require('os').userInfo().uid, process.getuid());
                      // HOME / TMPDIR name the working directory; the service may spell it through the
                      // /data/user/0 alias while the kernel reports /data/data, so compare real paths.
                      const realpath = (p) => require('fs').realpathSync(p);
                      assert.strictEqual(realpath(require('os').tmpdir()), realpath(process.cwd()));
                      assert.strictEqual(realpath(require('os').homedir()), realpath(process.cwd()));
                      // WASI is Node's own: a minimal module (memory export, empty _start) runs to completion.
                      const { WASI } = require('node:wasi');
                      const wasi = new WASI({ version: 'preview1', args: ['wasi-smoke'], env: {}, preopens: { '/sandbox': process.cwd() } });
                      const wasiBytes = new Uint8Array([0x00,0x61,0x73,0x6d,0x01,0x00,0x00,0x00, 0x01,0x04,0x01,0x60,0x00,0x00, 0x03,0x02,0x01,0x00,
                        0x05,0x03,0x01,0x00,0x01, 0x07,0x13,0x02,0x06,0x6d,0x65,0x6d,0x6f,0x72,0x79,0x02,0x00,0x06,0x5f,0x73,0x74,0x61,0x72,0x74,0x00,0x00,
                        0x0a,0x04,0x01,0x02,0x00,0x0b]);
                      const wasiInstance = (await WebAssembly.instantiate(wasiBytes, wasi.getImportObject())).instance;
                      const wasiExit = wasi.start(wasiInstance);
                      assert.ok(wasiExit === 0 || wasiExit === undefined, 'wasi exit ' + wasiExit);
                      assert.strictEqual(require('path/posix'), require('path').posix);
                      assert.strictEqual(require('util/types'), require('util').types);
                      assert.strictEqual(require('stream/web').ReadableStream, globalThis.ReadableStream);
                      assert.strictEqual(typeof require('readline/promises').Readline, 'function');
                      assert.strictEqual(typeof require('crypto').generateKeyPair, 'function');
                      assert.strictEqual(typeof require('events').EventEmitterAsyncResource, 'function');
                      const crypto = require('crypto');
                      const random = await new Promise((resolve, reject) => crypto.randomBytes(131072, (e, b) => e ? reject(e) : resolve(b)));
                      assert.strictEqual(random.length, 131072);
                      const data = Buffer.alloc(2 * 1024 * 1024, 42);
                      const zlib = require('zlib');
                      assert.deepStrictEqual(zlib.gunzipSync(zlib.gzipSync(data)), data);
                      const v8 = require('v8');
                      assert.deepStrictEqual(v8.deserialize(v8.serialize(data)), data);
                      const storage = new (require('async_hooks').AsyncLocalStorage)();
                      await storage.run(42, async () => {
                        await require('timers/promises').setTimeout(1);
                        assert.strictEqual(storage.getStore(), 42);
                      });
                      storage.disable();
                      assert.ok(require('os').totalmem() > 0);
                      assert.ok(require('os').availableParallelism() > 0);
                      assert.strictEqual(require('fs').realpathSync(require('os').homedir()), require('fs').realpathSync(process.cwd()));
                      // M23.1: fs is Node's own module and /proc follows Android like any other path.
                      assert.strictEqual(require('fs'), process.getBuiltinModule('fs'));
                      assert.match(require('fs').readFileSync('/proc/self/status', 'utf8'), /^Pid:\\s+\\d+/m);
                      if (Number(process.versions.node.split('.')[1]) >= 21) {
                        const fs = require('node:fs');
                        const path = require('node:path');
                        const keyDir = fs.mkdtempSync(path.join(process.cwd(), 'm17-store-'));
                        const keyPath = path.join(keyDir, 'key.pem');
                        const privateKey = crypto.generateKeyPairSync('ed25519').privateKey;
                        const pem = privateKey.export({format: 'pem', type: 'pkcs8'});
                        let fd;
                        try {
                          fs.writeFileSync(keyPath, pem);
                          fd = fs.openSync(keyPath, 'r');
                          const denied = error => error.code === 'ERR_ACCESS_DENIED';
                          for (const keyUrl of [new URL('file:///proc/self/fd/' + fd), require('node:url').pathToFileURL(keyPath)]) {
                            assert.throws(() => crypto.createPrivateKey(keyUrl), denied);
                            assert.throws(() => crypto.sign(null, Buffer.from('test'), {key: keyUrl}), denied);
                          }
                          assert.deepStrictEqual(crypto.createPrivateKey(pem).export({format: 'der', type: 'pkcs8'}),
                            privateKey.export({format: 'der', type: 'pkcs8'}));
                          const data = Buffer.from('store-policy');
                          const signature = crypto.sign(null, data, crypto.createPrivateKey(fs.readFileSync(keyPath)));
                          assert.ok(crypto.verify(null, data, crypto.createPublicKey(privateKey), signature));
                        } finally {
                          if (fd !== undefined) fs.closeSync(fd);
                          fs.rmSync(keyPath, {force: true});
                          fs.rmdirSync(keyDir);
                        }
                      }
                      const test = require('node:test');
                      await test('m13 native test runner', t => {
                        assert.strictEqual(typeof t.mock.method, 'function');
                        assert.strictEqual(6 * 7, 42);
                      });
                      console.log('m13.native.builtins=30');
                    })().catch(error => { console.error(error.stack); process.exitCode = 1; });
                    """
            );
            Bundle result = runtime.runScript(request, new INodeJsRuntimeCallback.Stub() {
                @Override
                public void onEvent(Bundle event) {
                }
            });

            assertNotNull("runScript returned null", result);
            String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
            String error = result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "");
            assertTrue(
                    "execution failed: " + error + " / stderr: " + result.getString(NodeJsRuntimeContract.KEY_STDERR) + " / stdout: " + stdout,
                    result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED)
            );
            assertTrue("stdout missing marker: " + stdout, stdout.contains("m13.native.builtins=30"));

            Bundle lateRequest = new Bundle();
            lateRequest.putString(NodeJsRuntimeContract.KEY_SOURCE, """
                    process.once('beforeExit', () => require('timers').setImmediate(() => {
                      console.log('m13.native.late-exit=7');
                      process.exitCode = 7;
                    }));
                    """);
            Bundle late = runtime.runScript(lateRequest, null);
            org.junit.Assert.assertFalse("late native failure was reported as success", late.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            org.junit.Assert.assertEquals(7, late.getInt(NodeJsRuntimeContract.KEY_EXIT_CODE));
            assertTrue(late.getString(NodeJsRuntimeContract.KEY_STDOUT, "").contains("m13.native.late-exit=7"));


        } finally {
            context.unbindService(connection);
        }
    }
}
