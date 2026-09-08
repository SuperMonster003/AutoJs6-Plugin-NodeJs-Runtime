package io.github.supermonster003.autojs6.plugin.nodejs;

import android.os.Bundle;
import android.os.RemoteException;

import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityBroker;
import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityCallback;
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract;
import org.json.JSONException;
import org.json.JSONObject;

/** A deterministic host endpoint; service tests still cross the real Binder boundary. */
final class ScreenStateTestBroker extends INodeJsHostCapabilityBroker.Stub {
    @Override
    public Bundle getBrokerInfo() {
        Bundle info = new Bundle();
        info.putString(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_BROKER_ID, "screen-state-test");
        info.putInt(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_BROKER_VERSION, 1);
        info.putStringArray(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_MODULES, new String[]{"device"});
        return info;
    }

    @Override public Bundle getNativeDiagnostics() { return new Bundle(); }
    @Override public void destroy(Bundle reason) { }

    @Override
    public void dispatch(Bundle request, INodeJsHostCapabilityCallback callback) throws RemoteException {
        try {
            JSONObject call = new JSONObject(request.getString(NodeJsRuntimeContract.KEY_BRIDGE_REQUEST_JSON));
            if (!"device".equals(call.getString("module")) || !"isScreenOn".equals(call.getString("method"))) {
                throw new AssertionError("unexpected test bridge call: " + call);
            }
            Bundle response = new Bundle();
            response.putString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON,
                    new JSONObject().put("id", call.getString("id")).put("ok", true)
                            .put("result", true).toString());
            callback.onResponse(response);
        } catch (JSONException error) {
            throw new AssertionError(error);
        }
    }
}
