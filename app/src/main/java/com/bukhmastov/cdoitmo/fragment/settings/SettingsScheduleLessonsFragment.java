package com.bukhmastov.cdoitmo.fragment.settings;

import android.app.AlertDialog;
import android.content.Context;

import androidx.fragment.app.Fragment;

import com.bukhmastov.cdoitmo.R;
import com.bukhmastov.cdoitmo.activity.ConnectedActivity;
import com.bukhmastov.cdoitmo.model.entity.SettingsQuery;
import com.bukhmastov.cdoitmo.object.preference.Preference;
import com.bukhmastov.cdoitmo.object.preference.PreferenceBasic;
import com.bukhmastov.cdoitmo.object.preference.PreferenceList;
import com.bukhmastov.cdoitmo.object.preference.PreferenceSwitch;
import com.bukhmastov.cdoitmo.provider.InjectProvider;
import com.bukhmastov.cdoitmo.util.Storage;

import java.util.LinkedList;
import java.util.List;

public class SettingsScheduleLessonsFragment extends SettingsTemplatePreferencesFragment {

    private static final String TAG = "SettingsScheduleLessonsFragment";
    public static final List<Preference> preferences;
    static {
        preferences = new LinkedList<>();
        preferences.add(new PreferenceBasic("pref_schedule_lessons_default", "{\"query\":\"auto\",\"title\":\"\"}", R.string.default_schedule, true, new PreferenceBasic.Callback() {
            @Override
            public void onPreferenceClicked(ConnectedActivity activity, Preference preference, InjectProvider injectProvider, PreferenceBasic.OnPreferenceClickedCallback callback) {
                injectProvider.getSettingsScheduleLessons().show(activity, preference, value -> {
                    injectProvider.getStoragePref().put(activity, "pref_schedule_lessons_default", value);
                    callback.onSetSummary(activity, value);
                });
            }
            @Override
            public String onGetSummary(Context context, String value) {
                try {
                    SettingsQuery settingsQuery = new SettingsQuery().fromJsonString(value);
                    switch (settingsQuery.getQuery()) {
                        case "personal": return context.getString(R.string.personal_schedule);
                        case "auto": return context.getString(R.string.current_group);
                        default: return settingsQuery.getTitle();
                    }
                } catch (Exception e) {
                    return null;
                }
            }
        }));
        preferences.add(new PreferenceList("pref_schedule_lessons_source", "isu", R.string.source_picker, 0, R.string.source_picker_schedule_lessons_warning, R.array.pref_schedule_source_titles, R.array.pref_schedule_source_desc, R.array.pref_schedule_source_values, true));
        preferences.add(new PreferenceList("pref_schedule_lessons_week", "-1", R.string.week_picker, R.array.pref_schedule_lessons_week_titles, R.array.pref_schedule_lessons_week_values, true));
        preferences.add(new PreferenceList("pref_schedule_lessons_view_of_reduced_lesson", "compact", R.string.pref_schedule_lessons_view_of_reduced_lesson, R.array.pref_schedule_lessons_view_of_reduced_lesson_titles, R.array.pref_schedule_lessons_view_of_reduced_lesson_values, true));
        preferences.add(new PreferenceSwitch("pref_schedule_lessons_scroll_to_day", true, R.string.pref_schedule_lessons_scroll_to_day, null, null));
        preferences.add(new PreferenceSwitch("pref_schedule_lessons_use_cache", false, R.string.cache_schedule, null, null));
        preferences.add(new PreferenceBasic("pref_schedule_lessons_clear_cache", null, R.string.clear_schedule_cache, false, new PreferenceBasic.Callback() {
            @Override
            public void onPreferenceClicked(ConnectedActivity activity, Preference preference, InjectProvider injectProvider, PreferenceBasic.OnPreferenceClickedCallback callback) {
                injectProvider.getThread().standalone(() -> {
                    if (activity != null) {
                        boolean success = injectProvider.getStorage().clear(activity, Storage.CACHE, Storage.GLOBAL, "schedule_lessons");
                        if (success) {
                            injectProvider.getNotificationMessage().snackBar(activity, activity.getString(R.string.cache_cleared));
                        }
                    }
                });
            }
            @Override
            public String onGetSummary(Context context, String value) {
                return null;
            }
        }));
        preferences.add(new PreferenceBasic("pref_schedule_lessons_clear_additional", null, R.string.pref_schedule_lessons_clear_additional_title, false, new PreferenceBasic.Callback() {
            @Override
            public void onPreferenceClicked(ConnectedActivity activity, Preference preference, InjectProvider injectProvider, PreferenceBasic.OnPreferenceClickedCallback callback) {
                injectProvider.getThread().runOnUI(() -> {
                    if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }
                    new AlertDialog.Builder(activity)
                            .setTitle(R.string.pref_schedule_lessons_clear_additional_title)
                            .setMessage(R.string.pref_schedule_lessons_clear_additional_warning)
                            .setIcon(R.drawable.ic_warning)
                            .setPositiveButton(R.string.proceed, (dialog, which) -> injectProvider.getThread().standalone(() -> {
                                boolean success = injectProvider.getStorage().clear(activity, Storage.PERMANENT, Storage.USER, "schedule_lessons");
                                if (success) {
                                    injectProvider.getNotificationMessage().snackBar(activity, activity.getString(R.string.changes_cleared));
                                }
                            }))
                            .setNegativeButton(R.string.cancel, null)
                            .create().show();
                });
            }
            @Override
            public String onGetSummary(Context context, String value) {
                return null;
            }
        }));
    }

    @Override
    protected List<Preference> getPreferences() {
        return preferences;
    }

    @Override
    protected String getTAG() {
        return TAG;
    }

    @Override
    protected Fragment getSelf() {
        return this;
    }
}
