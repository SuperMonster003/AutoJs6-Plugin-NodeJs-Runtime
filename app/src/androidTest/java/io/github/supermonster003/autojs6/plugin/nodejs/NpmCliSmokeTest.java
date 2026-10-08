package io.github.supermonster003.autojs6.plugin.nodejs;

import static org.junit.Assert.*;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Real bundled npm; the fixture registry is loopback-only and needs no external network. */
@RunWith(AndroidJUnit4.class)
public final class NpmCliSmokeTest {
    @Test public void registryInstallUpdateAndLifecycleFollowNpmConfig() throws Exception {
        run("""
                const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
                const npm=require('npm'),pm=require('package_manager');
                fs.writeFileSync('package.json',JSON.stringify({name:'autojs6-npm-test',version:'1.0.0',private:true}));
                const options={env:{npm_config_cache:path.join(process.cwd(),'.npm'),npm_config_update_notifier:'false'}};
                const version=await npm.cli(['--version'],options);
                assert.equal(version.stdout.trim(),version.npmVersion);
                const name='autojs6-npm-fixture',versions={},tarballs={};
                for(const version of ['1.0.0','1.1.0']) {
                  const dir=path.join(process.cwd(),'fixture-'+version);fs.mkdirSync(dir);
                  const manifest={name,version,main:'index.cjs',scripts:{postinstall:'node install.cjs'}};
                  fs.writeFileSync(path.join(dir,'package.json'),JSON.stringify(manifest));
                  fs.writeFileSync(path.join(dir,'index.cjs'),'module.exports={answer:42,version:'+JSON.stringify(version)+'};');
                  fs.writeFileSync(path.join(dir,'install.cjs'),"require('node:fs').writeFileSync(require('node:path').join(process.env.INIT_CWD,'installed-marker'),process.env.npm_package_version);");
                  const packed=await npm.pack(dir,{...options,offline:true,npmArgs:['--json']});
                  assert.equal(packed.command,'pack');
                  const filename=JSON.parse(packed.stdout)[0].filename;
                  tarballs[filename]=fs.readFileSync(filename);
                  versions[version]={...manifest,dist:{tarball:filename,integrity:'sha512-'+require('node:crypto').createHash('sha512').update(tarballs[filename]).digest('base64')}};
                }
                let metadataRequests=0;
                const server=require('node:http').createServer((req,res)=>{
                  if(req.url==='/'+name) {metadataRequests++;res.setHeader('Content-Type','application/json');res.setHeader('Cache-Control','no-store');res.end(JSON.stringify({name,'dist-tags':{latest:'1.1.0'},versions}));}
                  else {const body=tarballs[req.url.slice(1)];res.statusCode=body?200:404;res.end(body||'not found');}
                });
                await new Promise(r=>server.listen(0,'127.0.0.1',r));
                const registry='http://127.0.0.1:'+server.address().port;
                for(const entry of Object.values(versions)) entry.dist.tarball=registry+'/'+entry.dist.tarball;
                fs.writeFileSync('.npmrc',['registry='+registry,'ignore-scripts=true','audit=false','fund=false',''].join(String.fromCharCode(10)));
                try {
                  await pm.install(name+'@1.0.0',options);
                  assert.equal(require(name).answer,42);
                  assert.equal(fs.existsSync('installed-marker'),false);
                  await pm.remove(name,options);
                  await pm.install(name+'@1.0.0',{...options,ignoreScripts:false});
                  assert.equal(fs.readFileSync('installed-marker','utf8'),'1.0.0');
                  await pm.update(name,{...options,ignoreScripts:false});
                  delete require.cache[require.resolve(name)];
                  assert.equal(require(name).version,'1.1.0');
                  assert.equal(fs.readFileSync('installed-marker','utf8'),'1.1.0');
                  assert.ok(metadataRequests>0);
                  assert.ok(fs.existsSync('package-lock.json'));
                  console.log('m23.npm.registry-config-lifecycle=PASS');
                } finally {await new Promise(r=>server.close(r));}
                """, 120_000L, BuildConfig.NODE_CLI_NPM_VERSION);
    }

    @Test public void offlineInstallReportsTheNpmError() throws Exception {
        run("""
                const assert=require('node:assert/strict'),path=require('node:path');
                const pm=require('package_manager');
                await assert.rejects(pm.install('autojs6-not-cached-fixture@1.0.0',{
                  offline:true,env:{npm_config_cache:path.join(process.cwd(),'empty-cache'),npm_config_update_notifier:'false'},
                  npmArgs:['--no-audit','--no-fund']
                }),error=>error.code==='ENOTCACHED' && error.exitCode!==0 && error.stderr.includes('ENOTCACHED') && !error.autojs6Code);
                console.log('m23.npm.offline=PASS');
                """, 30_000L, "ENOTCACHED");
    }

    static void run(String source, long timeoutMs) throws Exception { run(source, timeoutMs, "added"); }

    private static void run(String source, long timeoutMs, String expectedStreamText) throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root=new File(context.getCacheDir(),"npm-runtime-"+UUID.randomUUID());
        assertTrue(root.mkdirs());
        File project=new File(root,"project");
        assertTrue(project.mkdirs());
        File input=new File(root,"input.zip");
        try (ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(input))) {
            zip.putNextEntry(new ZipEntry(".autojs6-workspace-transport/input-manifest-v1.json"));
            zip.write("{\"version\":1,\"deleteEligibleFiles\":[]}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        AtomicReference<IBinder> binder=new AtomicReference<>();
        CountDownLatch connected=new CountDownLatch(1);
        ServiceConnection connection=new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name,IBinder service) {binder.set(service);connected.countDown();}
            @Override public void onServiceDisconnected(ComponentName name) {}
        };
        assertTrue(context.bindService(new Intent().setComponent(new ComponentName(context.getPackageName(),
                context.getPackageName()+".NodeJsRuntimePluginService")),connection,Context.BIND_AUTO_CREATE));
        try {
            assertTrue(connected.await(30,TimeUnit.SECONDS));
            Bundle request=new Bundle();
            request.putString(NodeJsRuntimeContract.KEY_EXECUTION_ID,"npm-"+UUID.randomUUID());
            request.putString(NodeJsRuntimeContract.KEY_SOURCE_NAME,new File(project,"main.cjs").getAbsolutePath());
            request.putString(NodeJsRuntimeContract.KEY_WORKING_DIRECTORY,project.getAbsolutePath());
            request.putString(NodeJsRuntimeContract.KEY_SANDBOX_ROOT,project.getAbsolutePath());
            request.putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS,timeoutMs);
            request.putString(NodeJsRuntimeContract.KEY_SOURCE,"(async()=>{\n"+source+"\n})().catch(e=>{console.error(e.stack);process.exitCode=1;});");
            request.putInt(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_TRANSPORT_VERSION,NodeJsRuntimeContract.WORKSPACE_ARCHIVE_TRANSPORT_CONTRACT_VERSION);
            request.putString(NodeJsRuntimeContract.KEY_WORKSPACE_RELATIVE_WORKING_DIRECTORY, "");
            StringBuilder streamed=new StringBuilder();
            Bundle result;
            try (var in=ParcelFileDescriptor.open(input,ParcelFileDescriptor.MODE_READ_ONLY);
                 var out=ParcelFileDescriptor.open(new File(root,"output.zip"),ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_READ_WRITE|ParcelFileDescriptor.MODE_TRUNCATE)) {
                request.putParcelable(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_INPUT_FD,in);
                request.putParcelable(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_OUTPUT_FD,out);
            result=INodeJsRuntimePlugin.Stub.asInterface(binder.get()).runScript(request,new INodeJsRuntimeCallback.Stub() {
                @Override public void onEvent(Bundle event) {
                    String type=event.getString(NodeJsRuntimeContract.KEY_EVENT_TYPE, "");
                    if ("stdout".equals(type) || "stderr".equals(type)) {
                        synchronized (streamed) { streamed.append(event.getString(NodeJsRuntimeContract.KEY_EVENT_TEXT, "")); }
                    }
                }
            });
            }
            assertNotNull(result);
            String output=result.getString(NodeJsRuntimeContract.KEY_STDOUT,"");
            assertTrue(result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE,"")+"\n"+result.getString(NodeJsRuntimeContract.KEY_STDERR,"")+"\n"+output,
                    result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED));
            assertTrue(output,output.contains("=PASS"));
            synchronized (streamed) { assertTrue("Missing streamed npm output: "+streamed,streamed.toString().contains(expectedStreamText)); }
        } finally {context.unbindService(connection);deleteTree(root);}
    }

    private static void deleteTree(File file) {
        File[] children=file.listFiles();
        if(children!=null) for(File child:children) deleteTree(child);
        file.delete();
    }
}
