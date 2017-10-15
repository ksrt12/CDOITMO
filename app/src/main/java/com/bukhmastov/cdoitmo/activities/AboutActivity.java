package com.bukhmastov.cdoitmo.activities;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.bukhmastov.cdoitmo.R;
import com.bukhmastov.cdoitmo.firebase.FirebaseAnalyticsProvider;
import com.bukhmastov.cdoitmo.utils.Log;
import com.bukhmastov.cdoitmo.utils.Static;

import java.util.Random;

public class AboutActivity extends ConnectedActivity {

    private static final String TAG = "AboutActivity";
    private final Random random = new Random();
    private int counterToPika = 0;
    private final int tapsToPika = 5;
    private Activity activity = this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Static.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Activity created");
        FirebaseAnalyticsProvider.logCurrentScreen(this);
        setContentView(R.layout.activity_about);
        Toolbar toolbar = findViewById(R.id.toolbar_about);
        if (toolbar != null) {
            Static.applyToolbarTheme(activity, toolbar);
            setSupportActionBar(toolbar);
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        TextView app_version = findViewById(R.id.app_version);
        if (app_version != null) {
            app_version.setText(activity.getString(R.string.version) + " " + Static.versionName + " (" + activity.getString(R.string.build) + " " + Static.versionCode + ")");
        }

        View block_pika = findViewById(R.id.block_pika);
        if (block_pika != null) {
            block_pika.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (counterToPika >= tapsToPika) {
                        if (random.nextInt(200) % 10 == 0) {
                            startActivity(new Intent(getBaseContext(), PikaActivity.class));
                        }
                    } else {
                        counterToPika++;
                    }
                }
            });
        }

        View block_send_mail = findViewById(R.id.block_send_mail);
        if (block_send_mail != null) {
            block_send_mail.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.v(TAG, "send_mail clicked");
                    try {
                        Intent emailIntent = new Intent(Intent.ACTION_SEND);
                        emailIntent.setType("message/rfc822");
                        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"bukhmastov-alex@ya.ru"});
                        startActivity(Intent.createChooser(emailIntent, activity.getString(R.string.send_mail) + "..."));
                    } catch (Exception e) {
                        Static.snackBar(activity, activity.getString(R.string.something_went_wrong));
                    }
                }
            });
        }

        View block_send_vk = findViewById(R.id.block_send_vk);
        if (block_send_vk != null) {
            block_send_vk.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.v(TAG, "send_vk clicked");
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://vk.com/write9780714")));
                    } catch (Exception e) {
                        Static.snackBar(activity, activity.getString(R.string.something_went_wrong));
                    }
                }
            });
        }

        View block_rate = findViewById(R.id.block_rate);
        if (block_rate != null) {
            block_rate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.v(TAG, "block_rate clicked");
                    FirebaseAnalyticsProvider.logBasicEvent(activity, "app rate clicked");
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.bukhmastov.cdoitmo")));
                    } catch (Exception e) {
                        Static.snackBar(activity, activity.getString(R.string.something_went_wrong));
                    }
                }
            });
        }

        View block_github = findViewById(R.id.block_github);
        if (block_github != null) {
            block_github.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.v(TAG, "block_github clicked");
                    FirebaseAnalyticsProvider.logBasicEvent(activity, "view github clicked");
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/bukhmastov/cdoitmo")));
                    } catch (Exception e) {
                        Static.snackBar(activity, activity.getString(R.string.something_went_wrong));
                    }
                }
            });
        }

        View block_donate = findViewById(R.id.block_donate);
        if (block_donate != null) {
            block_donate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.v(TAG, "block_donate clicked  ┬─┬ ノ( ゜-゜ノ)");
                    FirebaseAnalyticsProvider.logBasicEvent(activity, "donate clicked");
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://yasobe.ru/na/cdoifmo")));
                    } catch (Exception e) {
                        Static.snackBar(activity, activity.getString(R.string.something_went_wrong));
                    }
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Activity destroyed");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_about, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: finish(); return true;
            case R.id.action_log:
                startActivity(new Intent(this, LogActivity.class));
                return true;
            default: return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected int getRootViewId() {
        return R.id.about_content;
    }
}
