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

/**
 * Roadmap M23.1: {@code require("fs")} is Node's own module. The former scoped
 * wrapper answered every asynchronous call by running the synchronous variant
 * inside setImmediate, so a large read stalled the event loop. With native fs
 * the read goes through the libuv thread pool and timers keep firing while a
 * 64 MiB file is read; the module identities match desktop Node as well.
 */
@RunWith(AndroidJUnit4.class)
public final class FsAsyncParitySmokeTest {

    private static final long BIND_TIMEOUT_MS = 30_000L;

    @Test
    public void asyncReadsDoNotBlockTimersAndFsIsNodesOwnModule() throws Exception {
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
            assertTrue("bind timed out", connected.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS));
            INodeJsRuntimePlugin runtime = INodeJsRuntimePlugin.Stub.asInterface(binder.get());
            assertNotNull("runtime proxy unavailable", runtime);

            Bundle request = new Bundle();
            request.putString(NodeJsRuntimeContract.KEY_SOURCE, probeSource());
            request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 120_000L);
            Bundle result = runtime.runScript(request, new INodeJsRuntimeCallback.Stub() {
                @Override
                public void onEvent(Bundle event) {
                }
            });

            assertNotNull("runScript returned null", result);
            String stdout = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
            String stderr = result.getString(NodeJsRuntimeContract.KEY_STDERR, "");
            String error = result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "");
            assertTrue(
                    "fs parity probe failed: " + error + " / stderr: " + stderr + " / stdout: " + stdout,
                    result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED)
            );
            for (String marker : new String[] {
                    "m23.fs.identity=true",
                    "m23.fs.promises.identity=true",
                    "m23.fs.cwd=true",
                    "m23.fs.read.ok=true",
                    "m23.fs.ticks.during.read=ok",
                    "m23.fs.callback.ticks=ok",
                    "m23.fs=PASS"
            }) {
                assertTrue("stdout is missing '" + marker + "': " + stdout, stdout.contains(marker));
            }
            assertTrue("an AutoJs6 fs policy code leaked: " + stdout, !stdout.contains("ERR_AUTOJS6_FS_"));
        } finally {
            context.unbindService(connection);
        }
    }

    private static String probeSource() {
        return """
                const fs = require('fs');
                const fsp = require('fs/promises');
                const path = require('path');
                console.log('m23.fs.identity=' + (fs === process.getBuiltinModule('fs') && fs === require('node:fs')));
                console.log('m23.fs.promises.identity=' + (fsp === fs.promises && fsp === process.getBuiltinModule('fs/promises')));
                // The execution really entered its working directory: relative paths are resolved by Node itself.
                const file = 'm23-async-parity-' + process.pid + '.bin';
                fs.writeFileSync(file, Buffer.alloc(64 * 1024 * 1024, 7));
                console.log('m23.fs.cwd=' + (fs.realpathSync(file) === path.join(fs.realpathSync(process.cwd()), file)));
                (async () => {
                  try {
                    let ticks = 0;
                    const interval = setInterval(() => { ticks += 1; }, 5);
                    const started = Date.now();
                    const data = await fsp.readFile(file);
                    const elapsed = Date.now() - started;
                    clearInterval(interval);
                    console.log('m23.fs.read.ok=' + (data.length === 64 * 1024 * 1024 && data[0] === 7 && data[data.length - 1] === 7));
                    console.log('m23.fs.read.ms=' + elapsed + ' ticks=' + ticks);
                    // A synchronous read of the same size would let no timer run; the native async read must let several fire.
                    console.log('m23.fs.ticks.during.read=' + (ticks >= 2 ? 'ok' : 'blocked:' + ticks + '/' + elapsed + 'ms'));
                    let callbackTicks = 0;
                    const callbackInterval = setInterval(() => { callbackTicks += 1; }, 5);
                    await new Promise((resolve, reject) => fs.readFile(file, (error, buffer) => error ? reject(error) : resolve(buffer)));
                    clearInterval(callbackInterval);
                    console.log('m23.fs.callback.ticks=' + (callbackTicks >= 2 ? 'ok' : 'blocked:' + callbackTicks));
                    console.log('m23.fs=PASS');
                  } finally {
                    try { fs.unlinkSync(file); } catch (_) {}
                  }
                })().catch((error) => { console.error(error && error.stack || error); process.exitCode = 1; });
                """;
    }
}
