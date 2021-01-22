package com.bukhmastov.cdoitmo.util;

import android.app.Activity;
import android.content.Context;
import android.view.Menu;

import com.bukhmastov.cdoitmo.firebase.FirebaseConfigProvider;
import com.google.android.material.navigation.NavigationView;

public interface NavigationMenu {

    void displayEnableDisableOfflineButton(final NavigationView navigationView);

    void displayUserData(final Context context, final Storage storage, final NavigationView navigationView);

    void toggleOfflineIcon(final Menu menu);

    void displayRemoteMessage(final Activity activity, final FirebaseConfigProvider firebaseConfigProvider, final Storage storage);

    void hideIfUnauthorizedMode(final NavigationView navigationView);
}
