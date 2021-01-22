package com.bukhmastov.cdoitmo.fragment.presenter;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public interface UniversityPersonsFragmentPresenter {

    void setFragment(Fragment fragment);

    void onCreate(@Nullable Bundle savedInstanceState);

    void onDestroy();

    void onResume();

    void onPause();

    void onCreateView(View container);
}
