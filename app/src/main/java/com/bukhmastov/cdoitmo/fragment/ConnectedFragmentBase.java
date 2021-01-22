package com.bukhmastov.cdoitmo.fragment;

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.fragment.app.Fragment;

import com.bukhmastov.cdoitmo.factory.AppComponentProvider;
import com.bukhmastov.cdoitmo.util.Log;
import com.bukhmastov.cdoitmo.util.Thread;

import javax.inject.Inject;

public abstract class ConnectedFragmentBase extends Fragment {

    @Inject
    Log log;
    @Inject
    Thread thread;

    @Override
    @CallSuper
    public void onAttach(Context context) {
        AppComponentProvider.getComponent().inject(this);
        super.onAttach(context);
    }
}
