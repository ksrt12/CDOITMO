package com.bukhmastov.cdoitmo.object.preference;

import android.content.Context;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.bukhmastov.cdoitmo.R;
import com.bukhmastov.cdoitmo.activity.ConnectedActivity;

public class PreferenceHeader {

    private final @DrawableRes int icon;
    private final @StringRes int title;
    private final Class fragment;

    public PreferenceHeader(@StringRes int title, @DrawableRes int icon, Class fragment) {
        this.title = title;
        this.icon = icon;
        this.fragment = fragment;
    }

    @Nullable
    public static View getView(final ConnectedActivity activity, final PreferenceHeader preference) {
        View header = inflate(activity, R.layout.preference_header);
        if (header == null) {
            return null;
        }
        ((ImageView) header.findViewById(R.id.preference_header_icon)).setImageResource(preference.icon);
        ((TextView) header.findViewById(R.id.preference_header_title)).setText(preference.title);
        header.findViewById(R.id.preference_header).setOnClickListener(v -> {
            activity.openFragment(ConnectedActivity.TYPE.STACKABLE, preference.fragment, null);
        });
        return header;
    }

    @Nullable
    protected static View inflate(final Context context, @LayoutRes final int layout) throws InflateException {
        if (context == null) {
            return null;
        }
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater == null) {
            return null;
        }
        return inflater.inflate(layout, null);
    }
}
