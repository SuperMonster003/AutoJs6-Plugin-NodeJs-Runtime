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

/** Native web globals use Node networking; explicit host bridge modules remain available. */
@RunWith(AndroidJUnit4.class)
public final class NativeWebGlobalsSmokeTest {

    private static final long BIND_TIMEOUT_MS = 30_000L;

    @Test
    public void nativeFetchAndWebSocketUseLoopbackWithoutHostPermissions() throws Exception {
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
                      const http = require('node:http');
                      const crypto = require('node:crypto');
                      assert.strictEqual(require('autojs6:fetch'), require('fetch'));
                      assert.strictEqual(require('autojs6:websocket'), require('websocket'));
                      assert.notStrictEqual(fetch, require('autojs6:fetch'));
                      const server = http.createServer((req, res) => {
                        let body = '';
                        req.on('data', chunk => body += chunk);
                        req.on('end', () => {
                          res.writeHead(200, {'content-type':'application/json','x-transport':'node','connection':'close'});
                          res.end(JSON.stringify({body, method:req.method}));
                        });
                      });
                      server.on('upgrade', (req, socket) => {
                        const accept = crypto.createHash('sha1').update(req.headers['sec-websocket-key'] + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest('base64');
                        socket.write(['HTTP/1.1 101 Switching Protocols','Upgrade: websocket','Connection: Upgrade','Sec-WebSocket-Accept: ' + accept,'',''].join(String.fromCharCode(13,10)));
                        let frames = Buffer.alloc(0);
                        socket.on('data', chunk => {
                          frames = Buffer.concat([frames,chunk]);
                          while (frames.length >= 2) {
                            const length = frames[1] & 127;
                            assert.ok(length < 126);
                            const masked = !!(frames[1] & 128), offset = masked ? 6 : 2;
                            if (frames.length < offset + length) return;
                            const opcode = frames[0] & 15;
                            const payload = Buffer.from(frames.subarray(offset,offset+length));
                            if (masked) for (let i=0; i<length; ++i) payload[i] ^= frames[2 + (i & 3)];
                            frames = frames.subarray(offset + length);
                            if (opcode === 1) socket.write(Buffer.concat([Buffer.from([129,payload.length]),payload]));
                            else if (opcode === 8) socket.end(Buffer.from([136,2,3,232]));
                          }
                        });
                      });
                      await new Promise(resolve => server.listen(0,'127.0.0.1',resolve));
                      try {
                        const url = 'http://127.0.0.1:' + server.address().port + '/native';
                        const headers = new Headers({'content-type':'text/plain'});
                        const request = new Request(url,{method:'POST',headers,body:'native-fetch'});
                        const response = await fetch(request);
                        assert.ok(response instanceof Response);
                        assert.strictEqual(response.headers.get('x-transport'),'node');
                        assert.deepStrictEqual(await response.json(),{body:'native-fetch',method:'POST'});
                        const form = new FormData(); form.set('native','yes');
                        assert.strictEqual(form.get('native'),'yes');
                        const socket = new WebSocket(url.replace('http:','ws:'));
                        const echo = await new Promise((resolve,reject) => {
                          socket.addEventListener('open',()=>socket.send('native-ws'),{once:true});
                          socket.addEventListener('message',event=>resolve(event.data),{once:true});
                          socket.addEventListener('error',()=>reject(new Error('native WebSocket failed')),{once:true});
                        });
                        assert.strictEqual(echo,'native-ws');
                        const closed = new Promise(resolve=>socket.addEventListener('close',resolve,{once:true}));
                        socket.close(1000); await closed;
                        console.log('m13.native.web=PASS');
                      } finally {
                        server.closeAllConnections();
                        await new Promise(resolve => server.close(resolve));
                      }
                    })().catch(error => { console.error(error.stack); process.exitCode = 1; });
                    """
            );
            request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 20_000L);
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
            assertTrue("stdout missing marker: " + stdout, stdout.contains("m13.native.web=PASS"));

            Bundle disabled = new Bundle();
            disabled.putBoolean(NodeJsRuntimeContract.KEY_RAW_NODE_NETWORK_MODULES_ENABLED, false);
            disabled.putString(NodeJsRuntimeContract.KEY_SOURCE, """
                    (async () => {
                      const assert = require('assert/strict');
                      const denied = e => e.code === 'ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED';
                      await assert.rejects(fetch('http://127.0.0.1:9/'), denied);
                      assert.throws(() => new WebSocket('ws://127.0.0.1:9/'), denied);
                      assert.strictEqual(typeof require('autojs6:fetch'), 'function');
                      console.log('m13.native.web.optout=PASS');
                    })().catch(e => { console.error(e.stack); process.exitCode = 1; });
                    """);
            Bundle optedOut = runtime.runScript(disabled, null);
            assertTrue(optedOut.getString(NodeJsRuntimeContract.KEY_STDERR), optedOut.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            assertTrue(optedOut.getString(NodeJsRuntimeContract.KEY_STDOUT, "").contains("m13.native.web.optout=PASS"));
        } finally {
            context.unbindService(connection);
        }
    }
}
