package com.bukhmastov.cdoitmo.object.preference;

import android.content.Context;
import android.support.annotation.DrawableRes;
import android.support.annotation.StringRes;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bukhmastov.cdoitmo.R;
import com.bukhmastov.cdoitmo.activity.ConnectedActivity;

public class PreferenceHeader {
    public @DrawableRes final int icon;
    public @StringRes final int title;
    public final Class fragment;
    public PreferenceHeader(@StringRes int title, @DrawableRes int icon, Class fragment) {
        this.title = title;
        this.icon = icon;
        this.fragment = fragment;
    }
    public static View getView(final ConnectedActivity activity, final PreferenceHeader preference) {
        View header = inflate(activity, R.layout.preference_header);
        ((ImageView) header.findViewById(R.id.preference_header_icon)).setImageResource(preference.icon);
        ((TextView) header.findViewById(R.id.preference_header_title)).setText(preference.title);
        header.findViewById(R.id.preference_header).setOnClickListener(v -> activity.openFragment(ConnectedActivity.TYPE.STACKABLE, preference.fragment, null));
        return header;
    }
    private static View inflate(final Context context, final int layoutId) throws InflateException {
        return ((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(layoutId, null);
    }
}
