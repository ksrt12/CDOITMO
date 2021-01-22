package com.bukhmastov.cdoitmo.adapter.rva;

import androidx.recyclerview.widget.RecyclerView;

import com.bukhmastov.cdoitmo.factory.AppComponentProvider;
import com.bukhmastov.cdoitmo.util.Log;

import javax.inject.Inject;

import dagger.Lazy;

public abstract class RVABase extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    @Inject
    protected Lazy<Log> log;

    RVABase() {
        AppComponentProvider.getComponent().inject(this);
    }
}
