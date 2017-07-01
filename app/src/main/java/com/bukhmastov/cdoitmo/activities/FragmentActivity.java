package com.bukhmastov.cdoitmo.activities;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.ViewGroup;

import com.bukhmastov.cdoitmo.R;
import com.bukhmastov.cdoitmo.fragments.ConnectedFragment;
import com.bukhmastov.cdoitmo.utils.Log;
import com.bukhmastov.cdoitmo.utils.Static;

public class FragmentActivity extends ConnectedActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "FragmentActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (Static.darkTheme) setTheme(R.style.AppTheme_Dark);
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Activity created");
        setContentView(R.layout.activity_fragment);

        setSupportActionBar((Toolbar) findViewById(R.id.toolbar_fragment));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        ((NavigationView) findViewById(R.id.nav_view)).setNavigationItemSelectedListener(this);

        try {
            Bundle extras = getIntent().getExtras();
            invoke((Class) extras.get("class"), (Bundle) extras.get("extras"));
        } catch (Exception e) {
            Static.error(e);
            finish();
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        Static.NavigationMenu.displayEnableDisableOfflineButton(navigationView);
        Static.NavigationMenu.displayUserData(this, navigationView);
        Static.NavigationMenu.displayUserAvatar(this, navigationView);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Log.v(TAG, "NavigationItemSelected " + item.getTitle());
        DrawerLayout drawer_layout = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer_layout != null) {
            drawer_layout.closeDrawer(GravityCompat.START);
        }
        MainActivity.selectedMenuItem = item;
        finish();
        return true;
    }

    @Override
    protected int getRootViewId(){
        return R.id.activity_fragment;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: finish(); return true;
            default: return super.onOptionsItemSelected(item);
        }
    }

    public void invoke(Class connectedFragmentClass, Bundle extras){
        Log.v(TAG, "invoke | " + connectedFragmentClass.toString());
        try {
            ConnectedFragment.Data data = ConnectedFragment.getData(this, connectedFragmentClass);
            if (data == null) {
                throw new NullPointerException("data cannot be null");
            }
            if (Static.tablet) {
                updateToolbar(data.title, null);
            }
            ViewGroup root = (ViewGroup) findViewById(getRootViewId());
            if (root != null) {
                root.removeAllViews();
            }
            Fragment fragment = (Fragment) data.connectedFragmentClass.newInstance();
            if (extras != null) {
                fragment.setArguments(extras);
            }
            getSupportFragmentManager().beginTransaction().replace(getRootViewId(), fragment).commit();
        } catch (Exception e) {
            Static.error(e);
            finish();
        }
    }

}