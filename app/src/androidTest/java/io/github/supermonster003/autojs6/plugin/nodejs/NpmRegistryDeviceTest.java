package io.github.supermonster003.autojs6.plugin.nodejs;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Explicit online acceptance; excluded from the standard offline SmokeTest group. */
@RunWith(AndroidJUnit4.class)
public final class NpmRegistryDeviceTest {
    @Test public void installsFromThePublicRegistryAndRequiresThePackage() throws Exception {
        Assume.assumeTrue("Pass -e nodeNpmOnline true for the manual network check",
                "true".equals(InstrumentationRegistry.getArguments().getString("nodeNpmOnline")));
        NpmCliSmokeTest.run("""
                const assert=require('node:assert/strict');
                const result=await require('package_manager').install('is-number@7.0.0',{
                  registry:'https://registry.npmjs.org/',npmArgs:['--no-audit','--no-fund','--fetch-retries=0','--fetch-timeout=20000'],
                  timeoutMs:30_000,env:{npm_config_update_notifier:'false'}
                });
                assert.equal(result.exitCode,0);
                assert.equal(require('is-number')('42'),true);
                assert.equal(require('is-number')('not a number'),false);
                console.log('m23.npm.online=PASS');
                """,45_000L);
    }
}
