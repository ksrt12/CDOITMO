package com.bukhmastov.cdoitmo.fragment.presenter;

import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface LinkAccountFragmentPresenter extends ConnectedFragmentPresenter {

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({ISU})
    @interface Type {}
    String ISU = "isu";
}
