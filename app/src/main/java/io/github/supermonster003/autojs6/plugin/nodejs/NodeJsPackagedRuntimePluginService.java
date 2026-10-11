package io.github.supermonster003.autojs6.plugin.nodejs;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback;
import org.autojs.plugin.nodejs.api.INodeJsRuntimePlugin;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Normal-permission entry that shares the existing dispatcher and process pool. */
public final class NodeJsPackagedRuntimePluginService extends Service {
    private final CountDownLatch connected = new CountDownLatch(1);
    private volatile INodeJsRuntimePlugin runtime;
    private boolean bound;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            runtime = INodeJsRuntimePlugin.Stub.asInterface(binder);
            connected.countDown();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            runtime = null;
        }
        @Override public void onNullBinding(ComponentName name) {
            connected.countDown();
        }
    };

    private final INodeJsRuntimePlugin.Stub binder = new INodeJsRuntimePlugin.Stub() {
        @Override public Bundle getRuntimeInfo() throws RemoteException {
            return runtime().getRuntimeInfo();
        }
        @Override public Bundle runScript(Bundle request, INodeJsRuntimeCallback callback) throws RemoteException {
            return runtime().runScript(request, callback);
        }
        @Override public boolean cancelScript(String executionId) throws RemoteException {
            return runtime().cancelScript(executionId);
        }
        @Override public Bundle prewarmRuntime(Bundle request) throws RemoteException {
            return runtime().prewarmRuntime(request);
        }
        @Override public boolean postMessage(String executionId, Bundle message) throws RemoteException {
            return runtime().postMessage(executionId, message);
        }
        @Override public Bundle startScript(Bundle request, INodeJsRuntimeCallback callback) throws RemoteException {
            return runtime().startScript(request, callback);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        bound = bindService(new Intent(this, NodeJsRuntimePluginService.class), connection, BIND_AUTO_CREATE);
        if (!bound) connected.countDown();
    }

    @Override public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override public void onDestroy() {
        if (bound) unbindService(connection);
        runtime = null;
        super.onDestroy();
    }

    private INodeJsRuntimePlugin runtime() throws RemoteException {
        try {
            if (!connected.await(10, TimeUnit.SECONDS)) {
                throw new RemoteException("Node.js runtime dispatcher connection timed out");
            }
            INodeJsRuntimePlugin target = runtime;
            if (target == null) throw new RemoteException("Node.js runtime dispatcher is unavailable");
            return target;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new RemoteException("Interrupted while connecting to the Node.js runtime");
        }
    }
}
