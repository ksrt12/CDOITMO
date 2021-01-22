package com.bukhmastov.cdoitmo.network;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bukhmastov.cdoitmo.model.JsonEntity;
import com.bukhmastov.cdoitmo.network.handlers.RestResponseHandler;
import com.bukhmastov.cdoitmo.network.model.Isu;

import java.util.Map;

public abstract class IsuRestClient extends Isu {

    public IsuRestClient() {
        super();
    }

    public abstract <T extends JsonEntity> void get(@NonNull Context context, @NonNull String url,
                             @Nullable Map<String, String> query, @NonNull RestResponseHandler<T> handler);

    public abstract <T extends JsonEntity> void get(@NonNull Context context,
                             @NonNull @Protocol String protocol, @NonNull String url,
                             @Nullable Map<String, String> query, @NonNull RestResponseHandler<T> handler);
}
