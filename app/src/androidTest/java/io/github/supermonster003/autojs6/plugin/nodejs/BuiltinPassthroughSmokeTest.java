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
                      assert.strictEqual(require('os').homedir(), process.cwd());
                      assert.throws(() => require('fs').readFileSync('/proc/self/status'),
                        error => (error.autojs6Code || error.code) === 'ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED');
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
