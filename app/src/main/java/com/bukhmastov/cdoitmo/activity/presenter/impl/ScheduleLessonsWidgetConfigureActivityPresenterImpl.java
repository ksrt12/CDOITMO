package com.bukhmastov.cdoitmo.activity.presenter.impl;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.bukhmastov.cdoitmo.R;
import com.bukhmastov.cdoitmo.activity.ScheduleLessonsWidgetConfigureActivity;
import com.bukhmastov.cdoitmo.activity.presenter.ScheduleLessonsWidgetConfigureActivityPresenter;
import com.bukhmastov.cdoitmo.adapter.array.TeacherPickerAdapter;
import com.bukhmastov.cdoitmo.factory.AppComponentProvider;
import com.bukhmastov.cdoitmo.firebase.FirebaseAnalyticsProvider;
import com.bukhmastov.cdoitmo.model.schedule.lessons.SLessons;
import com.bukhmastov.cdoitmo.model.schedule.teachers.STeacher;
import com.bukhmastov.cdoitmo.model.widget.schedule.lessons.WSLSettings;
import com.bukhmastov.cdoitmo.model.widget.schedule.lessons.WSLTheme;
import com.bukhmastov.cdoitmo.network.model.Client;
import com.bukhmastov.cdoitmo.object.schedule.Schedule;
import com.bukhmastov.cdoitmo.object.schedule.ScheduleLessons;
import com.bukhmastov.cdoitmo.util.Log;
import com.bukhmastov.cdoitmo.util.NotificationMessage;
import com.bukhmastov.cdoitmo.util.Storage;
import com.bukhmastov.cdoitmo.util.Theme;
import com.bukhmastov.cdoitmo.util.Thread;
import com.bukhmastov.cdoitmo.util.singleton.CollectionUtils;
import com.bukhmastov.cdoitmo.util.singleton.StringUtils;
import com.bukhmastov.cdoitmo.view.dialog.ColorPickerDialog;
import com.bukhmastov.cdoitmo.widget.ScheduleLessonsWidget;
import com.bukhmastov.cdoitmo.widget.ScheduleLessonsWidgetStorage;

import java.util.ArrayList;
import java.util.Locale;

import javax.inject.Inject;

import static com.bukhmastov.cdoitmo.util.Thread.WSLC;

public class ScheduleLessonsWidgetConfigureActivityPresenterImpl implements ScheduleLessonsWidgetConfigureActivityPresenter {

    private static final String TAG = "SLWidgetConfigureActivity";
    private ScheduleLessonsWidgetConfigureActivity activity = null;
    private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean isDarkTheme = false;
    private Client.Request requestHandle = null;

    @Inject
    Log log;
    @Inject
    Thread thread;
    @Inject
    ScheduleLessons scheduleLessons;
    @Inject
    Storage storage;
    @Inject
    NotificationMessage notificationMessage;
    @Inject
    Theme theme;
    @Inject
    ScheduleLessonsWidgetStorage scheduleLessonsWidgetStorage;
    @Inject
    FirebaseAnalyticsProvider firebaseAnalyticsProvider;

    public ScheduleLessonsWidgetConfigureActivityPresenterImpl() {
        AppComponentProvider.getComponent().inject(this);
    }

    @Override
    public void setActivity(@NonNull ScheduleLessonsWidgetConfigureActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        thread.initialize(WSLC);
        thread.runOnUI(WSLC, () -> {
            log.i(TAG, "Activity created");
            firebaseAnalyticsProvider.logCurrentScreen(activity);
            activity.setResult(Activity.RESULT_CANCELED);
            final String th = theme.getAppTheme(activity);
            isDarkTheme = "dark".equals(th) || "black".equals(th);
            Toolbar toolbar = activity.findViewById(R.id.toolbar_widget);
            if (toolbar != null) {
                theme.applyToolbarTheme(activity, toolbar);
                toolbar.setTitle(R.string.configure_schedule_widget);
                activity.setSupportActionBar(toolbar);
            }
            Intent intent = activity.getIntent();
            Bundle extras = intent.getExtras();
            if (extras != null) {
                mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            }
            if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                log.w(TAG, "Wrong AppWidgetId provided by intent's extra");
                close(Activity.RESULT_CANCELED, null);
                return;
            }
            init();
        });
    }

    @Override
    public void onDestroy() {
        log.i(TAG, "Activity destroyed");
        thread.interrupt(WSLC);
    }

    private void init() {
        thread.run(WSLC, () -> {
            initDefaults();
            initPartPreview();
            thread.runOnUI(WSLC, () -> {
                initPartSchedule();
                initPartTheme();
                initPartUpdate();
                initPartDynamicShift();
                initFinishButton();
            }, throwable -> {
                log.exception(throwable);
                notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
            });
        }, throwable -> {
            log.exception(throwable);
            notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
        });
    }

    private void initDefaults() {
        log.v(TAG, "initDefaults");
        WSLSettings settings = getSettings(mAppWidgetId);
        if (settings == null) {
            settings = new WSLSettings();
        }
        WSLTheme theme = settings.getTheme();
        if (theme == null) {
            theme = new WSLTheme();
        }
        Settings.Schedule.query = StringUtils.nvlt(settings.getQuery(), Default.Schedule.query);
        Settings.Schedule.title = StringUtils.nvlt(settings.getQuery(), Default.Schedule.title);
        Settings.Theme.background = StringUtils.nvlt(theme.getBackground(), isDarkTheme ? Default.Theme.Dark.background : Default.Theme.Light.background);
        Settings.Theme.text       = StringUtils.nvlt(theme.getText(),       isDarkTheme ? Default.Theme.Dark.text       : Default.Theme.Light.text);
        Settings.Theme.opacity = theme.getOpacity() != 0 ? theme.getOpacity() : Default.Theme.Light.opacity;
        Settings.updateTime = settings.getUpdateTime() != 0 ? settings.getUpdateTime() : Default.updateTime;
        Settings.useShiftAutomatic = Default.useShiftAutomatic;
    }
    
    private void initPartPreview() {
        log.v(TAG, "initPartPreview");
        // Starting from Android 27 (8.1) there is no longer free access to current wallpaper
        // Getting wallpaper requires "dangerous" permission android.permission.READ_EXTERNAL_STORAGE
        // To avoid using this permission, we just not gonna use wallpaper for widget preview
        // UPD: Now we are using this permission. Here we gonna check for permission and _not_ gonna ask for it to be granted
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1 ||
                ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            try {
                final WallpaperManager wallpaperManager = WallpaperManager.getInstance(activity);
                if (wallpaperManager == null) {
                    throw new NullPointerException("WallpaperManager is null");
                }
                final Drawable wallpaperDrawable = wallpaperManager.getDrawable();
                if (wallpaperDrawable == null) {
                    throw new NullPointerException("WallpaperDrawable is null");
                }
                thread.runOnUI(WSLC, () -> {
                    ImageView partPreviewBackground = activity.findViewById(R.id.part_preview_background);
                    if (partPreviewBackground != null) {
                        partPreviewBackground.setImageDrawable(wallpaperDrawable);
                    }
                });
            } catch (Exception ignore) {
                // just ignore
            }
        }
        updateDemo();
    }
    
    private void initPartSchedule() {
        log.v(TAG, "initPartSchedule");
        ViewGroup partSchedule = activity.findViewById(R.id.part_schedule);
        partSchedule.setOnClickListener(view -> {
            if (StringUtils.isNotBlank(Settings.Schedule.query)) {
                activatePartSchedule(Settings.Schedule.title);
                return;
            }
            String group = storage.get(activity, Storage.PERMANENT, Storage.USER, "user#group");
            if (StringUtils.isBlank(group)) {
                activatePartSchedule("personal");
            } else {
                activatePartSchedule(group);
            }
        });
        updateScheduleSummary();
    }
    
    private void initPartTheme() {
        log.v(TAG, "initPartTheme");
        ViewGroup partTheme = activity.findViewById(R.id.part_theme);
        partTheme.setOnClickListener(view -> activatePartTheme());
        updateThemeSummary();
    }
    
    private void initPartUpdate() {
        log.v(TAG, "initPartUpdate");
        ViewGroup partUpdate = activity.findViewById(R.id.part_update);
        partUpdate.setOnClickListener(view -> activatePartUpdate());
        updateUpdateSummary();
    }
    
    private void initPartDynamicShift() {
        log.v(TAG, "initPartDynamicShift");
        ViewGroup partDynamicShift = activity.findViewById(R.id.part_automatic_shift);
        Switch partDynamicShiftSwitch = activity.findViewById(R.id.part_automatic_shift_switch);
        partDynamicShiftSwitch.setChecked(Default.useShiftAutomatic);
        partDynamicShiftSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            activatePartDynamicShift(isChecked);
        });
        partDynamicShift.setOnClickListener(view -> thread.runOnUI(WSLC, () -> {
            partDynamicShiftSwitch.setChecked(!partDynamicShiftSwitch.isChecked());
        }));
    }
    
    private void initFinishButton() {
        log.v(TAG, "initFinishButton");
        Button addButton = activity.findViewById(R.id.add_button);
        addButton.setText(R.string.add_widget);
        addButton.setVisibility(View.VISIBLE);
        addButton.setOnClickListener(view -> activateFinish());
    }

    private void activatePartSchedule() {
        activatePartSchedule(null);
    }
    
    private void activatePartSchedule(String title) {
        thread.runOnUI(WSLC, () -> {
            log.v(TAG, "activatePartSchedule | scope=", title);
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            ViewGroup layout = (ViewGroup) inflate(R.layout.widget_configure_schedule_lessons_create_search);
            if (layout == null) {
                return;
            }
            AlertDialog alertDialog = new AlertDialog.Builder(activity)
                    .setView(layout)
                    .setNegativeButton(R.string.do_cancel, null)
                    .create();
            AutoCompleteTextView searchTextView = layout.findViewById(R.id.search_text_view);
            ViewGroup searchAction = layout.findViewById(R.id.search_action);
            ViewGroup searchLoading = layout.findViewById(R.id.search_loading);
            TeacherPickerAdapter teacherPickerAdapter = new TeacherPickerAdapter(activity, new ArrayList<>());
            if (title != null) {
                searchTextView.setText(title);
            }
            teacherPickerAdapter.setNotifyOnChange(true);
            searchTextView.setAdapter(teacherPickerAdapter);
            searchTextView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
                @Override
                public void afterTextChanged(Editable editable) {
                    thread.runOnUI(WSLC, () -> {
                        teacherPickerAdapter.clear();
                        searchTextView.dismissDropDown();
                    });
                }
            });
            searchAction.setOnClickListener(view -> thread.run(WSLC, () -> {
                final String query = searchTextView.getText().toString().trim();
                log.v(TAG, "activatePartSchedule | search action | clicked | query=", query);
                if (StringUtils.isBlank(query)) {
                    return;
                }
                scheduleLessons.search(query, new Schedule.Handler<SLessons>() {
                    @Override
                    public void onSuccess(SLessons schedule, boolean fromCache) {
                        thread.runOnUI(WSLC, () -> {
                            log.v(TAG, "activatePartSchedule | search action | onSuccess | schedule=", (schedule == null ? "null" : "notnull"));
                            searchLoading.setVisibility(View.GONE);
                            searchAction.setVisibility(View.VISIBLE);
                            if (schedule == null || StringUtils.isBlank(schedule.getType())) {
                                notificationMessage.snackBar(activity, activity.getString(R.string.schedule_not_found));
                                return;
                            }
                            log.v(TAG, "activatePartSchedule | search action | onSuccess | type=", schedule.getType());
                            switch (schedule.getType()) {
                                case "group": case "room": case "teacher": {
                                    Settings.Schedule.query = schedule.getQuery();
                                    switch (schedule.getType()) {
                                        case "group": case "teacher": {
                                            Settings.Schedule.title = schedule.getTitle();
                                            break;
                                        }
                                        case "room": {
                                            Settings.Schedule.title = activity.getString(R.string.room) + " " + schedule.getTitle();
                                            break;
                                        }
                                    }
                                    log.v(TAG, "activatePartSchedule | search action | onSuccess | done | query=", Settings.Schedule.query, " | title=", Settings.Schedule.title);
                                    if (alertDialog != null && alertDialog.isShowing()) {
                                        alertDialog.cancel();
                                    }
                                    updateScheduleSummary();
                                    break;
                                }
                                case "teachers": {
                                    teacherPickerAdapter.clear();
                                    if (schedule.getTeachers() == null || CollectionUtils.isEmpty(schedule.getTeachers().getTeachers())) {
                                        return;
                                    }
                                    ArrayList<STeacher> teachers = schedule.getTeachers().getTeachers();
                                    log.v(TAG, "activatePartSchedule | search action | onSuccess | type=", schedule.getType(), " | length=", teachers.size());
                                    if (teachers.size() == 1) {
                                        STeacher teacher = teachers.get(0);
                                        if (teacher == null) {
                                            notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
                                            return;
                                        }
                                        Settings.Schedule.query = teacher.getPersonId();
                                        Settings.Schedule.title = teacher.getPerson();
                                        log.v(TAG, "activatePartSchedule | search action | onSuccess | done | query=", Settings.Schedule.query, " | title=", Settings.Schedule.title);
                                        if (alertDialog.isShowing()) {
                                            alertDialog.cancel();
                                        }
                                        updateScheduleSummary();
                                        return;
                                    }
                                    teacherPickerAdapter.addAll(teachers);
                                    teacherPickerAdapter.addTeachers(teachers);
                                    if (teachers.size() > 0) {
                                        searchTextView.showDropDown();
                                    }
                                    break;
                                }
                                default: {
                                    notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
                                    break;
                                }
                            }
                        }, throwable -> {
                            log.exception(throwable);
                            notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
                        });
                    }
                    @Override
                    public void onFailure(int code, Client.Headers headers, int state) {
                        log.v(TAG, "activatePartSchedule | search action | onFailure | state=", state, " | code=", code);
                        thread.runOnUI(WSLC, () -> {
                            searchLoading.setVisibility(View.GONE);
                            searchAction.setVisibility(View.VISIBLE);
                            notificationMessage.snackBar(activity, scheduleLessons.getFailedMessage(code, state));
                        });
                    }
                    @Override
                    public void onProgress(int state) {
                        log.v(TAG, "activatePartSchedule | search action | onProgress | state=", state);
                        thread.runOnUI(WSLC, () -> {
                            searchLoading.setVisibility(View.VISIBLE);
                            searchAction.setVisibility(View.GONE);
                        });
                    }
                    @Override
                    public void onNewRequest(Client.Request request) {
                        requestHandle = request;
                    }
                    @Override
                    public void onCancelRequest() {
                        if (requestHandle != null) {
                            requestHandle.cancel();
                        }
                    }
                });
            }));
            searchTextView.setOnItemClickListener((parent, view, position, id) -> {
                thread.runOnUI(WSLC, () -> {
                    log.v(TAG, "activatePartSchedule | search list selected");
                    STeacher teacher = teacherPickerAdapter.getItem(position);
                    if (teacher == null) {
                        notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
                        return;
                    }
                    Settings.Schedule.query = teacher.getPersonId();
                    Settings.Schedule.title = teacher.getPerson();
                    log.v(TAG, "activatePartSchedule | search list selected | query=", Settings.Schedule.query, " | title=", Settings.Schedule.title);
                    if (alertDialog.isShowing()) {
                        alertDialog.cancel();
                    }
                    updateScheduleSummary();
                }, throwable -> {
                    log.exception(throwable);
                    notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
                });
            });
            alertDialog.show();
            searchAction.setVisibility(View.VISIBLE);
        }, throwable -> {
            log.exception(throwable);
            notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
        });
    }
    
    private void activatePartTheme() {
        thread.runOnUI(WSLC, () -> {
            log.v(TAG, "activatePartTheme");

            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }

            // define variables
            final ViewGroup layout = (ViewGroup) inflate(R.layout.widget_configure_schedule_lessons_create_theme);
            if (layout == null) {
                return;
            }

            final ViewGroup default_theme_light = layout.findViewById(R.id.default_theme_light);
            final TextView default_theme_light_background = layout.findViewById(R.id.default_theme_light_background);
            final TextView default_theme_light_text = layout.findViewById(R.id.default_theme_light_text);
            final TextView default_theme_light_opacity = layout.findViewById(R.id.default_theme_light_opacity);

            final ViewGroup default_theme_dark = layout.findViewById(R.id.default_theme_dark);
            final TextView default_theme_dark_background = layout.findViewById(R.id.default_theme_dark_background);
            final TextView default_theme_dark_text = layout.findViewById(R.id.default_theme_dark_text);
            final TextView default_theme_dark_opacity = layout.findViewById(R.id.default_theme_dark_opacity);

            final ViewGroup background_color_picker = layout.findViewById(R.id.background_color_picker);
            final ImageView background_color_picker_image = layout.findViewById(R.id.background_color_picker_image);
            final TextView background_color_picker_header = layout.findViewById(R.id.background_color_picker_header);
            final TextView background_color_picker_value = layout.findViewById(R.id.background_color_picker_value);
            final TextView background_color_picker_hint = layout.findViewById(R.id.background_color_picker_hint);

            final ViewGroup text_color_picker = layout.findViewById(R.id.text_color_picker);
            final ImageView text_color_picker_image = layout.findViewById(R.id.text_color_picker_image);
            final TextView text_color_picker_header = layout.findViewById(R.id.text_color_picker_header);
            final TextView text_color_picker_value = layout.findViewById(R.id.text_color_picker_value);
            final TextView text_color_picker_hint = layout.findViewById(R.id.text_color_picker_hint);

            final ViewGroup background_opacity_picker = layout.findViewById(R.id.background_opacity_picker);
            final TextView background_opacity_picker_title = layout.findViewById(R.id.background_opacity_picker_title);
            final SeekBar background_opacity_picker_seek_bar = layout.findViewById(R.id.background_opacity_picker_seek_bar);

            // setup ui

            final AlertDialog alertDialog = new AlertDialog.Builder(activity)
                    .setView(layout)
                    .setPositiveButton(R.string.apply, (dialogInterface, i) -> thread.run(WSLC, () -> {
                        log.v(TAG, "activatePartTheme | apply");
                        try {
                            String background = background_color_picker_value.getText().toString().trim();
                            if (background.charAt(0) != '#') {
                                throw new Exception();
                            }
                            Color.parseColor(background);
                            Settings.Theme.background = background;
                        } catch (Exception ignore) {
                            // just ignore
                        }
                        try {
                            String text = text_color_picker_value.getText().toString().trim();
                            if (text.charAt(0) != '#') {
                                throw new Exception();
                            }
                            Color.parseColor(text);
                            Settings.Theme.text = text;
                        } catch (Exception ignore) {
                            // just ignore
                        }
                        try {
                            int opacity = background_opacity_picker_seek_bar.getProgress();
                            if (opacity >= 0 && opacity <= 255) {
                                Settings.Theme.opacity = opacity;
                            }
                        } catch (Exception ignore) {
                            // just ignore
                        }
                        log.v(TAG, "activatePartTheme | apply | background=" + Settings.Theme.background + " | text=" + Settings.Theme.text + " | opacity=" + Settings.Theme.opacity);
                        updateDemo();
                        updateThemeSummary();
                    }))
                    .setNegativeButton(R.string.do_cancel, null)
                    .create();

            default_theme_light.setOnClickListener(view -> thread.run(WSLC, () -> {
                log.v(TAG, "activatePartTheme | light theme selected");
                try {
                    Settings.Theme.text       = Default.Theme.Light.text;
                    Settings.Theme.background = Default.Theme.Light.background;
                    Settings.Theme.opacity    = Default.Theme.Light.opacity;
                    thread.runOnUI(WSLC, () -> {
                        if (alertDialog != null && alertDialog.isShowing()) {
                            alertDialog.cancel();
                        }
                    });
                    updateDemo();
                    updateThemeSummary();
                } catch (Exception e) {
                    log.exception(e);
                    notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
                }
            }));
            default_theme_light_background.setText(activity.getString(R.string.background_color) + ": " + Default.Theme.Light.background);
            default_theme_light_text.setText(activity.getString(R.string.text_color) + ": " + Default.Theme.Light.text);
            default_theme_light_opacity.setText(activity.getString(R.string.background_opacity) + ": " + opacity2percent(Default.Theme.Light.opacity) + "%");

            default_theme_dark.setOnClickListener(view -> thread.run(WSLC, () -> {
                log.v(TAG, "activatePartTheme | dark theme selected");
                try {
                    Settings.Theme.text       = Default.Theme.Dark.text;
                    Settings.Theme.background = Default.Theme.Dark.background;
                    Settings.Theme.opacity    = Default.Theme.Dark.opacity;
                    thread.runOnUI(WSLC, () -> {
                        if (alertDialog != null && alertDialog.isShowing()) {
                            alertDialog.cancel();
                        }
                    });
                    updateDemo();
                    updateThemeSummary();
                } catch (Exception e) {
                    log.exception(e);
                    notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
                }
            }));
            default_theme_dark_background.setText(activity.getString(R.string.background_color) + ": " + Default.Theme.Dark.background);
            default_theme_dark_text.setText(activity.getString(R.string.text_color) + ": " + Default.Theme.Dark.text);
            default_theme_dark_opacity.setText(activity.getString(R.string.background_opacity) + ": " + opacity2percent(Default.Theme.Dark.opacity) + "%");

            background_color_picker.setOnClickListener(view -> thread.run(WSLC, () -> {
                log.v(TAG, "activatePartTheme | background color picker clicked");
                new ColorPickerDialog(activity, new ColorPickerDialog.ColorPickerCallback() {
                    @Override
                    public void result(String hex) {
                        thread.runOnUI(WSLC, () -> {
                            log.v(TAG, "activatePartTheme | background color picker | hex=" + hex);
                            applyColor(hex, background_color_picker, background_color_picker_image, background_color_picker_value, background_color_picker_header, background_color_picker_hint);
                        });
                    }
                    @Override
                    public void exception(Exception e) {
                        log.exception(e);
                        notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
                    }
                }).show(Settings.Theme.background);
            }));
            applyColor(Settings.Theme.background, background_color_picker, background_color_picker_image, background_color_picker_value, background_color_picker_header, background_color_picker_hint);

            text_color_picker.setOnClickListener(view -> thread.run(WSLC, () -> {
                log.v(TAG, "activatePartTheme | text color picker clicked");
                new ColorPickerDialog(activity, new ColorPickerDialog.ColorPickerCallback() {
                    @Override
                    public void result(String hex) {
                        thread.runOnUI(WSLC, () -> {
                            log.v(TAG, "activatePartTheme | text color picker | hex=" + hex);
                            applyColor(hex, text_color_picker, text_color_picker_image, text_color_picker_value, text_color_picker_header, text_color_picker_hint);
                        });
                    }
                    @Override
                    public void exception(Exception e) {
                        log.exception(e);
                        notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
                    }
                }).show(Settings.Theme.text);
            }));
            applyColor(Settings.Theme.text, text_color_picker, text_color_picker_image, text_color_picker_value, text_color_picker_header, text_color_picker_hint);

            background_opacity_picker.getBackground().setAlpha((int) ((double) (255 - Settings.Theme.opacity) * 0.5));
            background_opacity_picker_title.setText(activity.getString(R.string.background_opacity) + " – " + opacity2percent(Settings.Theme.opacity) + "%");
            background_opacity_picker_seek_bar.setProgress(Settings.Theme.opacity);
            background_opacity_picker_seek_bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean b) {
                    try {
                        if (progress < 0) {
                            progress = 0;
                        }
                        if (progress > 255) {
                            progress = 255;
                        }
                        background_opacity_picker.getBackground().setAlpha((int) ((double) (255 - progress) * 0.5));
                        background_opacity_picker_title.setText(activity.getString(R.string.background_opacity) + " – " + opacity2percent(progress) + "%");
                    } catch (Exception e) {
                        log.exception(e);
                        notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
                    }
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            alertDialog.show();
        }, throwable -> {
            log.exception(throwable);
            notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
        });
    }
    
    private void activatePartUpdate() {
        thread.runOnUI(WSLC, () -> {
            log.v(TAG, "activatePartUpdate");
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            int select = 0;
            switch (Settings.updateTime){
                case 0: select = 0; break;
                case 12: select = 1; break;
                case 24: select = 2; break;
                case 168: select = 3; break;
                case 672: select = 4; break;
            }
            AlertDialog alertDialog = new AlertDialog.Builder(activity)
                    .setTitle(R.string.update_interval)
                    .setSingleChoiceItems(R.array.pref_widget_refresh_titles, select, (dialog, which) -> {
                        log.v(TAG, "activatePartUpdate | apply");
                        thread.run(WSLC, () -> {
                            switch (which){
                                case 0: Settings.updateTime = 0; break;
                                case 1: Settings.updateTime = 12; break;
                                case 2: Settings.updateTime = 24; break;
                                case 3: Settings.updateTime = 168; break;
                                case 4: Settings.updateTime = 672; break;
                            }
                            log.v(TAG, "activatePartUpdate | apply | which=", which, " | updateTime=", Settings.updateTime);
                            updateUpdateSummary();
                        });
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.do_cancel, null)
                    .create();
            alertDialog.show();
        }, throwable -> {
            log.exception(throwable);
            notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
        });
    }
    
    private void activatePartDynamicShift(boolean checked) {
        thread.run(WSLC, () -> {
            log.v(TAG, "activatePartDynamicShift | checked=", checked);
            Settings.useShiftAutomatic = checked;
        }, throwable -> {
            log.exception(throwable);
            notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
        });
    }
    
    private void activateFinish() {
        thread.run(WSLC, () -> {
            log.v(TAG, "activateFinish");
            if (Settings.Schedule.query == null || Settings.Schedule.query.trim().isEmpty()) {
                notificationMessage.snackBar(activity, activity.getString(R.string.need_to_choose_schedule));
                return;
            }
            WSLTheme theme = new WSLTheme();
            theme.setBackground(Settings.Theme.background);
            theme.setText(Settings.Theme.text);
            theme.setOpacity(Settings.Theme.opacity);
            theme.setBackground(Settings.Theme.background);
            WSLSettings settings = new WSLSettings();
            settings.setQuery(Settings.Schedule.query);
            settings.setTheme(theme);
            settings.setUpdateTime(Settings.updateTime);
            settings.setShift(0);
            settings.setShiftAutomatic(0);
            settings.setUseShiftAutomatic(Settings.useShiftAutomatic);
            log.v(TAG, "activateFinish | settings=", settings);
            scheduleLessonsWidgetStorage.save(mAppWidgetId, settings);
            (new ScheduleLessonsWidget()).updateAppWidget(activity, AppWidgetManager.getInstance(activity), mAppWidgetId, false);
            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
            close(Activity.RESULT_OK, resultValue);
            firebaseAnalyticsProvider.logEvent(
                    activity,
                    FirebaseAnalyticsProvider.Event.WIDGET_INSTALL,
                    firebaseAnalyticsProvider.getBundle(FirebaseAnalyticsProvider.Param.WIDGET_QUERY, Settings.Schedule.query)
            );
        }, throwable -> {
            log.w(TAG, "activateFinish | failed to create widget");
            log.exception(throwable);
            notificationMessage.snackBar(activity, activity.getString(R.string.failed_to_create_widget));
        });
    }

    private void updateDemo() {
        thread.runOnUI(WSLC, () -> {
            log.v(TAG, "updateDemo");
            int background = parseColor(Settings.Theme.background, Settings.Theme.opacity);
            int text = parseColor(Settings.Theme.text);
            ViewGroup widget_content = activity.findViewById(R.id.widget_content);
            if (widget_content != null) {
                ViewGroup widget_header = widget_content.findViewById(R.id.widget_header);
                TextView widget_title = widget_content.findViewById(R.id.widget_title);
                TextView widget_day_title = widget_content.findViewById(R.id.widget_day_title);
                ImageView widget_refresh_button = widget_content.findViewById(R.id.widget_refresh_button);
                ImageView widget_before_button = widget_content.findViewById(R.id.widget_before_button);
                ImageView widget_reset_button = widget_content.findViewById(R.id.widget_reset_button);
                ImageView widget_next_button = widget_content.findViewById(R.id.widget_next_button);
                TextView slw_item_time_start = widget_content.findViewById(R.id.slw_item_time_start);
                ImageView slw_item_time_icon = widget_content.findViewById(R.id.slw_item_time_icon);
                TextView slw_item_time_end = widget_content.findViewById(R.id.slw_item_time_end);
                TextView slw_item_title = widget_content.findViewById(R.id.slw_item_title);
                TextView slw_item_desc = widget_content.findViewById(R.id.slw_item_desc);
                TextView slw_item_meta = widget_content.findViewById(R.id.slw_item_meta);
                widget_content.setBackgroundColor(background);
                widget_header.setBackgroundColor(background);
                widget_title.setTextColor(text);
                widget_day_title.setTextColor(text);
                widget_refresh_button.setImageTintList(ColorStateList.valueOf(text));
                widget_before_button.setImageTintList(ColorStateList.valueOf(text));
                widget_reset_button.setImageTintList(ColorStateList.valueOf(text));
                widget_next_button.setImageTintList(ColorStateList.valueOf(text));
                slw_item_time_start.setTextColor(text);
                slw_item_time_icon.setImageTintList(ColorStateList.valueOf(text));
                slw_item_time_end.setTextColor(text);
                slw_item_title.setTextColor(text);
                slw_item_desc.setTextColor(text);
                slw_item_meta.setTextColor(text);
            }
        }, throwable -> {
            log.exception(throwable);
        });
    }
    
    private void updateScheduleSummary() {
        log.v(TAG, "updateScheduleSummary");
        thread.runOnUI(WSLC, () -> {
            TextView partScheduleSummary = activity.findViewById(R.id.part_schedule_summary);
            partScheduleSummary.setText(!Settings.Schedule.query.isEmpty() ? Settings.Schedule.title : activity.getString(R.string.need_to_choose_schedule));
        }, throwable -> {
            log.exception(throwable);
            notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
        });
    }
    
    private void updateThemeSummary() {
        log.v(TAG, "updateThemeSummary");
        thread.runOnUI(WSLC, () -> {
            TextView partThemeSummary = activity.findViewById(R.id.part_theme_summary);
            String summary;
            if (Settings.Theme.opacity == Default.Theme.Light.opacity && Settings.Theme.background.toUpperCase().equals(Default.Theme.Light.background) && Settings.Theme.text.toUpperCase().equals(Default.Theme.Light.text)) {
                summary = activity.getString(R.string.pref_light_theme);
            } else if (Settings.Theme.opacity == Default.Theme.Dark.opacity && Settings.Theme.background.toUpperCase().equals(Default.Theme.Dark.background) && Settings.Theme.text.toUpperCase().equals(Default.Theme.Dark.text)) {
                summary = activity.getString(R.string.pref_dark_theme);
            } else {
                summary  = "Фон" + ": " + Settings.Theme.background + ", ";
                summary += "Текст" + ": " + Settings.Theme.text + ", ";
                summary += "Прозрачность" + ": " + opacity2percent(Settings.Theme.opacity) + "%";
            }
            partThemeSummary.setText(summary);
        }, throwable -> {
            log.exception(throwable);
            notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
        });
    }
    
    private void updateUpdateSummary() {
        log.v(TAG, "updateUpdateSummary");
        thread.runOnUI(WSLC, () -> {
            TextView partUpdateSummary = activity.findViewById(R.id.part_update_summary);
            String summary;
            switch (Settings.updateTime){
                case 0: summary = activity.getString(R.string.manually); break;
                case 12: summary = activity.getString(R.string.once_per_12_hours); break;
                case 24: summary = activity.getString(R.string.once_per_1_day); break;
                case 168: summary = activity.getString(R.string.once_per_1_week); break;
                case 672: summary = activity.getString(R.string.once_per_4_weeks); break;
                default: summary = activity.getString(R.string.unknown); break;
            }
            partUpdateSummary.setText(summary);
        }, throwable -> {
            log.exception(throwable);
            notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
        });
    }

    private void applyColor(String hex, ViewGroup picker, ImageView image, TextView value, TextView header, TextView hint) {
        ColorStateList highlight = ColorStateList.valueOf(Color.parseColor(hex) > Color.parseColor("#757575") ? Color.BLACK : Color.WHITE);
        value.setText(hex);
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.LOLLIPOP) {
            picker.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(hex)));
            image.setImageTintList(highlight);
            header.setTextColor(highlight);
            value.setTextColor(highlight);
            hint.setTextColor(highlight);
        }
    }

    private int parseColor(String color) {
        return parseColor(color, 255);
    }
    
    private int parseColor(String color, int opacity) {
        log.v(TAG, "parseColor | color=" + color + " | opacity=" + opacity);
        int parsed = Color.parseColor(color);
        return Color.argb(opacity, Color.red(parsed), Color.green(parsed), Color.blue(parsed));
    }

    private String opacity2percent(int opacity) {
        double iOpacity = 255.0 - (double) opacity;
        double ratio = 100.0 / 255.0;
        double percent = iOpacity * ratio;
        return String.format(Locale.getDefault(), percent % 1.0 != 0 ? "%.2f" : "%.0f", percent);
    }

    private WSLSettings getSettings(int appWidgetId) {
        try {
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                return null;
            }
            return scheduleLessonsWidgetStorage.getSettings(appWidgetId);
        } catch (Exception ignore) {
            return null;
        }
    }

    private void close(int result, Intent intent) {
        log.v(TAG, "close | result=", result);
        if (requestHandle != null) {
            requestHandle.cancel();
        }
        thread.runOnUI(WSLC, () -> {
            if (intent == null) {
                activity.setResult(result);
            } else {
                activity.setResult(result, intent);
            }
            activity.finish();
        });
    }

    private View inflate(@LayoutRes int layout) throws InflateException {
        if (activity == null) {
            log.e(TAG, "Failed to inflate layout, activity is null");
            return null;
        }
        LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater == null) {
            log.e(TAG, "Failed to inflate layout, inflater is null");
            return null;
        }
        return inflater.inflate(layout, null);
    }
}
