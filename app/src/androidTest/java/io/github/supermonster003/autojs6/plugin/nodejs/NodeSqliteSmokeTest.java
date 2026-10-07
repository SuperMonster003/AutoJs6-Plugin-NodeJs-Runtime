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

    @Test public void sqliteCrudTransactionsBackupAndSystemPathsFollowAndroid() throws Exception {
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
                    const nativeError=e=>e.code==='ERR_SQLITE_ERROR'&&e.autojs6Code===undefined;
                    // M23.1: node:sqlite is Node's own module and /proc, /sys, /dev follow Android. SQLite decides what a
                    // system path means: a procfs file has st_size 0, so it opens as an empty read-only database whose first
                    // write fails; /sys and /dev targets cannot be created and fail at open. Either way the error is SQLite's own.
                    const systemPathFollowsSqlite=(open)=>{ let probe=null; try { probe=open(); } catch(e){ assert.ok(nativeError(e),String(e&&e.stack||e)); return; }
                      try { assert.throws(()=>probe.exec('CREATE TABLE t (x)'),nativeError); } finally { try { probe.close(); } catch(_) {} } };
                    const attachFollowsSqlite=(target,sql)=>{ try { target.exec(sql); } catch(e){ assert.ok(nativeError(e),String(e&&e.stack||e)); return; }
                      try { assert.throws(()=>target.exec('CREATE TABLE outside.t (x)'),nativeError); } finally { try { target.exec('DETACH outside'); } catch(_) {} } };
                    assert.strictEqual(typeof db.setAuthorizer,'function');
                    attachFollowsSqlite(db,"SELECT 1; /* file */ ATTACH '/proc/self/status' AS outside");
                    attachFollowsSqlite(db,"ATTACH 'file:/proc/self/status?mode=ro' AS outside");
                    attachFollowsSqlite(db,"ATTACH '"+'../'.repeat(16)+"proc/self/status' AS outside");
                    // Bound and computed ATTACH filenames are plain SQLite now.
                    db.prepare('ATTACH ? AS bound').run('bound.sqlite'); db.exec('CREATE TABLE bound.t (x); DETACH bound');
                    assert.strictEqual(fs.readFileSync('bound.sqlite').subarray(0,15).toString(),'SQLite format 3');
                    db.exec("ATTACH 'computed' || '.sqlite' AS computed; CREATE TABLE computed.t (x); DETACH computed");
                    assert.strictEqual(fs.readFileSync('computed.sqlite').subarray(0,15).toString(),'SQLite format 3');
                    db.exec("ATTACH 'attached.sqlite' AS extra; CREATE TABLE extra.notes (text TEXT); INSERT INTO extra.notes VALUES ('kept'); DETACH extra");
                    assert.strictEqual(fs.readFileSync('attached.sqlite').subarray(0,15).toString(),'SQLite format 3');
                    db.exec("ATTACH 'file:copy.sqlite?mode=ro' AS ro");
                    assert.strictEqual(db.prepare('SELECT count(*) AS count FROM ro.items').get().count,1);
                    assert.throws(()=>db.exec("INSERT INTO ro.items(label) VALUES ('nope')"),nativeError);
                    db.exec('DETACH ro');
                    db.exec("VACUUM main INTO 'vacuumed.sqlite'");
                    assert.strictEqual(fs.readFileSync('vacuumed.sqlite').subarray(0,15).toString(),'SQLite format 3');
                    // VACUUM INTO targets are SQLite's business, literal or bound; /dev and /proc targets cannot be created.
                    db.prepare('VACUUM main INTO ?').run('vacuumed-bound.sqlite');
                    assert.strictEqual(fs.readFileSync('vacuumed-bound.sqlite').subarray(0,15).toString(),'SQLite format 3');
                    assert.throws(()=>db.exec("VACUUM main INTO '/dev/sqlite-test'"),nativeError);
                    assert.throws(()=>db.prepare('VACUUM main INTO ?').run('/proc/sqlite-test'),nativeError);
                    db.exec("PRAGMA temp_store_directory = '"+process.cwd()+"'");
                    assert.strictEqual(db.prepare('PRAGMA temp_store_directory').get().temp_store_directory,process.cwd());
                    db.exec("PRAGMA temp_store_directory = ''");
                    // The user's authorizer is the only authorizer; null clears it.
                    db.setAuthorizer(action=>action===sqlite.constants.SQLITE_INSERT?sqlite.constants.SQLITE_DENY:sqlite.constants.SQLITE_OK);
                    assert.throws(()=>db.prepare("INSERT INTO items(label) VALUES ('denied')"),nativeError);
                    attachFollowsSqlite(db,"ATTACH '/proc/self/status' AS outside");
                    db.setAuthorizer(null);
                    db.prepare("INSERT INTO items(label) VALUES ('allowed')").run();
                    attachFollowsSqlite(db,"ATTACH '/sys/sqlite-test' AS outside");
                    assert.throws(()=>db.setAuthorizer('nope'),e=>e.code==='ERR_INVALID_ARG_TYPE');
                    // Extension loading is Node's own state machine: disabled at construction unless allowExtension is set.
                    assert.throws(()=>db.enableLoadExtension(true),e=>e.code==='ERR_INVALID_STATE');
                    const extensible=new DatabaseSync(':memory:',{allowExtension:true});
                    try { extensible.enableLoadExtension(true); extensible.enableLoadExtension(false); } finally { extensible.close(); }
                    for(const path of ['/proc/self/status','/sys/sqlite-test','/dev/sqlite-test']) systemPathFollowsSqlite(()=>new DatabaseSync(path));
                    systemPathFollowsSqlite(()=>new DatabaseSync('file:/proc/self/status?mode=ro'));
                    // SQLite URI strings, the empty temporary database and a missing read-only file are SQLite's business.
                    const viaUri=new DatabaseSync('file:'+process.cwd()+'/uri.sqlite?mode=rwc');
                    try { viaUri.exec('CREATE TABLE t (x)'); } finally { viaUri.close(); }
                    assert.strictEqual(fs.readFileSync('uri.sqlite').subarray(0,15).toString(),'SQLite format 3');
                    const relativeUri=new DatabaseSync('file:uri-relative.sqlite');
                    try { relativeUri.exec('CREATE TABLE t (x)'); } finally { relativeUri.close(); }
                    assert.ok(fs.existsSync('uri-relative.sqlite'));
                    const temporary=new DatabaseSync('');
                    try { temporary.exec('CREATE TABLE t (x); INSERT INTO t VALUES (1)'); assert.strictEqual(temporary.prepare('SELECT x FROM t').get().x,1); } finally { temporary.close(); }
                    assert.throws(()=>new DatabaseSync('missing.sqlite',{readOnly:true}),nativeError);
                    await assert.rejects(sqlite.backup(db,'/dev/sqlite-test'),nativeError);
                    systemPathFollowsSqlite(()=>new DatabaseSync(UNSAFE_SQLITE_LINK,{readOnly:true}));
                    const url=require('node:url').pathToFileURL(require('node:path').join(process.cwd(),'copy.sqlite'));
                    const deferred=new DatabaseSync(url,{open:false,readOnly:true});
                    deferred.open(); assert.strictEqual(deferred.isOpen,true);
                    // A reopened handle is plain SQLite too.
                    attachFollowsSqlite(deferred,"ATTACH '/proc/self/status' AS outside");
                    deferred.close();
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
