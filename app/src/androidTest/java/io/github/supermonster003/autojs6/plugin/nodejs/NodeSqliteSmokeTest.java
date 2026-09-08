package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class NodeSqliteSmokeTest {
    private Context context;
    private ServiceConnection connection;
    private INodeJsRuntimePlugin runtime;
    private File unsafeLink;

    @Before public void connect() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<IBinder> binder = new AtomicReference<>();
        connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) { binder.set(service); ready.countDown(); }
            @Override public void onServiceDisconnected(ComponentName name) {}
        };
        assertTrue(context.bindService(new Intent().setComponent(new ComponentName(context.getPackageName(),
                context.getPackageName() + ".NodeJsRuntimePluginService")), connection, Context.BIND_AUTO_CREATE));
        assertTrue(ready.await(30, TimeUnit.SECONDS));
        runtime = INodeJsRuntimePlugin.Stub.asInterface(binder.get());
        assertNotNull(runtime);
    }

    @After public void disconnect() {
        if (connection != null) context.unbindService(connection);
        //noinspection ResultOfMethodCallIgnored
        if (unsafeLink != null) unsafeLink.delete();
    }

    private Bundle run(String source) throws Exception {
        Bundle request = new Bundle();
        request.putString(NodeJsRuntimeContract.KEY_SOURCE, source);
        request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, 20_000L);
        Bundle result = runtime.runScript(request, null);
        assertNotNull(result);
        return result;
    }

    private String succeeded(Bundle result) {
        String output = result.getString(NodeJsRuntimeContract.KEY_STDOUT, "");
        assertTrue(result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "") + " / "
                        + result.getString(NodeJsRuntimeContract.KEY_STDERR, "") + " / " + output,
                result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
        Bundle status = new Bundle(); status.putString("stream", "\n" + output);
        InstrumentationRegistry.getInstrumentation().sendStatus(0, status);
        return output;
    }

    @Test public void sqliteCrudTransactionsBackupAndFileBoundaries() throws Exception {
        unsafeLink = new File(context.getCacheDir(), "sqlite-link-" + UUID.randomUUID());
        android.system.Os.symlink("/proc/self/status", unsafeLink.getAbsolutePath());
        String output = succeeded(run("""
                (async()=>{
                  const assert=require('node:assert/strict'), sqlite=require('node:sqlite'), fs=require('node:fs');
                  const {DatabaseSync}=sqlite;
                  assert.strictEqual(require('node:module').isBuiltin('node:sqlite'),true);
                  assert.strictEqual(process.getBuiltinModule('node:sqlite'),sqlite);
                  assert.strictEqual((await import('node:sqlite')).DatabaseSync,DatabaseSync);
                  const db=new DatabaseSync('native.sqlite');
                  try {
                    db.exec('CREATE TABLE items (id INTEGER PRIMARY KEY, label TEXT NOT NULL); BEGIN');
                    assert.strictEqual(db.isTransaction,true);
                    const insert=db.prepare('INSERT INTO items(label) VALUES (?)');
                    insert.run('one'); insert.run('two');
                    db.exec('COMMIT; BEGIN'); insert.run('rolled back'); db.exec('ROLLBACK');
                    assert.strictEqual(db.isTransaction,false);
                    db.prepare('UPDATE items SET label=? WHERE id=?').run('updated',2);
                    assert.deepStrictEqual(db.prepare('SELECT id,label FROM items ORDER BY id').all().map(x=>[x.id,x.label]),[[1,'one'],[2,'updated']]);
                    db.prepare('DELETE FROM items WHERE id=?').run(1);
                    db.function('twice',value=>value*2);
                    assert.strictEqual(db.prepare('SELECT twice(21) AS value').get().value,42);
                    assert.strictEqual(db.prepare("SELECT 'ATTACH; VACUUM INTO' AS text").get().text,'ATTACH; VACUUM INTO');
                    await sqlite.backup(db,'copy.sqlite');
                    const copy=new DatabaseSync(Buffer.from('copy.sqlite'),{readOnly:true});
                    try { assert.strictEqual(copy.prepare('SELECT count(*) AS count FROM items').get().count,1); }
                    finally { copy.close(); }
                    assert.strictEqual(fs.readFileSync('native.sqlite').subarray(0,15).toString(),'SQLite format 3');
                    const sqlDenied=e=>e.code==='ERR_AUTOJS6_SQLITE_FILE_OPERATION_UNSUPPORTED';
                    assert.throws(()=>db.exec("SELECT 1; /* file */ ATTACH '/proc/self/status' AS outside"),sqlDenied);
                    assert.throws(()=>db.prepare('ATTACH ? AS outside'),sqlDenied);
                    assert.throws(()=>db.exec("VACUUM main INTO '/dev/sqlite-test'"),sqlDenied);
                    assert.throws(()=>db.exec('PRAGMA main."temp_store_directory" = "/dev"'),sqlDenied);
                    assert.throws(()=>db.enableLoadExtension(true),e=>e.code==='ERR_AUTOJS6_NATIVE_ADDON_DISABLED');
                    assert.throws(()=>new DatabaseSync(':memory:',{allowExtension:true}),e=>e.code==='ERR_AUTOJS6_NATIVE_ADDON_DISABLED');
                    const fileDenied=e=>(e.autojs6Code||e.code).startsWith('ERR_AUTOJS6_FS_');
                    for(const path of ['/proc/self/status','/sys/sqlite-test','/dev/sqlite-test']) assert.throws(()=>new DatabaseSync(path),fileDenied);
                    assert.throws(()=>new DatabaseSync('file:/proc/self/status?mode=ro'),e=>e.code==='ERR_AUTOJS6_SQLITE_PATH_UNSUPPORTED');
                    assert.throws(()=>sqlite.backup(db,'/dev/sqlite-test'),fileDenied);
                    assert.throws(()=>new DatabaseSync(UNSAFE_SQLITE_LINK,{readOnly:true}),fileDenied);
                    const url=require('node:url').pathToFileURL(require('node:path').join(process.cwd(),'copy.sqlite'));
                    const deferred=new DatabaseSync(url,{open:false,readOnly:true});
                    deferred.open(); assert.strictEqual(deferred.isOpen,true); deferred.close();
                    console.log('m13.sqlite=PASS');
                  } finally { db.close(); }
                })().catch(e=>{console.error(e.stack);process.exitCode=1;});
                """.replace("UNSAFE_SQLITE_LINK", org.json.JSONObject.quote(unsafeLink.getAbsolutePath()))));
        assertTrue(output.contains("m13.sqlite=PASS"));
    }

    @Test public void nativeTestReporterAndFailureExitCode() throws Exception {
        String source;
        try (var input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("node-test-project/main.cjs");
             var bytes = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            source = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
        String output = succeeded(run(source));
        assertTrue(output.contains("sample.node-test-project=PASS"));
        assertTrue("native reporter missing: " + output, output.contains("addition") && output.contains("match"));
        Bundle failed = run("""
                const {test}=require('node:test'), assert=require('node:assert/strict');
                assert.strictEqual(typeof require('node:test/reporters').spec,'function');
                test('intentional failure',()=>assert.strictEqual(1,2));
                """);
        assertFalse("failing native test must fail the execution", failed.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
        assertEquals(1, failed.getInt(NodeJsRuntimeContract.KEY_EXIT_CODE));
        assertTrue(failed.getString(NodeJsRuntimeContract.KEY_STDOUT, "").contains("intentional failure"));
    }
}
