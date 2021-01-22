package com.bukhmastov.cdoitmo.view.dialog;

import android.content.Context;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.LayoutRes;

import com.bukhmastov.cdoitmo.factory.AppComponentProvider;
import com.bukhmastov.cdoitmo.util.Log;

import javax.inject.Inject;

import dagger.Lazy;

public abstract class Dialog {

    private static final String TAG = "Dialog";
    protected final Context context;

    @Inject
    Lazy<Log> log;

    public Dialog(Context context) {
        AppComponentProvider.getComponent().inject(this);
        this.context = context;
    }

    protected View inflate(@LayoutRes int layout) throws InflateException {
        if (context == null) {
            log.get().e(TAG, "Failed to inflate layout, context is null");
            return null;
        }
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater == null) {
            log.get().e(TAG, "Failed to inflate layout, inflater is null");
            return null;
        }
        return inflater.inflate(layout, null);
    }
}
