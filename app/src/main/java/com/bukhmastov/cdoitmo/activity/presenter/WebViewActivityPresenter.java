package com.bukhmastov.cdoitmo.activity.presenter;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bukhmastov.cdoitmo.activity.WebViewActivity;

public interface WebViewActivityPresenter {

    void setActivity(@NonNull WebViewActivity activity);

    void onCreate(@Nullable Bundle savedInstanceState);

    void onDestroy();

    boolean onToolbarSelected(MenuItem item);

    boolean onBackPressed();
}
