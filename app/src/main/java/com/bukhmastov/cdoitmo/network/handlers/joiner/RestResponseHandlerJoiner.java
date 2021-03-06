package com.bukhmastov.cdoitmo.network.handlers.joiner;

import com.bukhmastov.cdoitmo.model.JsonEntity;
import com.bukhmastov.cdoitmo.network.handlers.RestResponseHandler;
import com.bukhmastov.cdoitmo.network.model.Client;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class RestResponseHandlerJoiner<T extends JsonEntity> implements RestResponseHandler<T> {

    private final RestResponseHandler<T> handler;

    public RestResponseHandlerJoiner(RestResponseHandler<T> handler) {
        this.handler = handler;
    }

    @Override
    public void onSuccess(int code, Client.Headers headers, T response) throws Exception {
        handler.onSuccess(code, headers, response);
    }

    @Override
    public void onFailure(int code, Client.Headers headers, int state) {
        handler.onFailure(code, headers, state);
    }

    @Override
    public void onProgress(int state) {
        handler.onProgress(state);
    }

    @Override
    public void onNewRequest(Client.Request request) {
        handler.onNewRequest(request);
    }

    @Override
    public T newInstance() {
        return handler.newInstance();
    }

    @Override
    public JSONObject convertArray(JSONArray arr) throws JSONException {
        return handler.convertArray(arr);
    }
}
