package com.bukhmastov.cdoitmo.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bukhmastov.cdoitmo.R;
import com.bukhmastov.cdoitmo.firebase.FirebaseAnalyticsProvider;
import com.bukhmastov.cdoitmo.utils.Log;
import com.bukhmastov.cdoitmo.utils.Static;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SubjectShowFragment extends ConnectedFragment {

    private static final String TAG = "SubjectShowFragment";
    private JSONObject subject = null;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseAnalyticsProvider.logCurrentScreen(activity, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_subject_show, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        display();
    }

    @Override
    public void onResume() {
        super.onResume();
        FirebaseAnalyticsProvider.setCurrentScreen(activity, this);
    }

    private void display() {
        Static.T.runThread(new Runnable() {
            @Override
            public void run() {
                Log.v(TAG, "display");
                final String groupSub, nameSub;
                final int termSub;
                try {
                    Bundle extras = getArguments();
                    if (extras == null) {
                        throw new NullPointerException("extras are null");
                    }
                    groupSub = extras.getString("group");
                    termSub = Integer.parseInt(extras.getString("term"));
                    nameSub = extras.getString("name");
                    if (groupSub == null || termSub < 0 || nameSub == null) {
                        throw new Exception("Wrong extras provided: " + extras.toString());
                    }
                } catch (Exception e) {
                    Static.error(e);
                    activity.back();
                    return;
                }
                // проверяем целостность данных
                try {
                    if (ERegisterFragment.data == null) {
                        activity.back();
                        return;
                    }
                    boolean groupFound = false;
                    JSONArray groups = ERegisterFragment.data.getJSONArray("groups");
                    for (int i = 0; i < groups.length(); i++) {
                        JSONObject group = groups.getJSONObject(i);
                        if (Objects.equals(group.getString("name"), groupSub)) {
                            boolean termFound = false;
                            JSONArray terms = group.getJSONArray("terms");
                            for (int j = 0; j < terms.length(); j++) {
                                JSONObject term = terms.getJSONObject(j);
                                if (term.getInt("number") == termSub) {
                                    boolean subjectFound = false;
                                    JSONArray subjects = term.getJSONArray("subjects");
                                    for (int k = 0; k < subjects.length(); k++) {
                                        JSONObject subj = subjects.getJSONObject(k);
                                        if (Objects.equals(subj.getString("name"), nameSub)) {
                                            subject = subj;
                                            subjectFound = true;
                                            break;
                                        }
                                    }
                                    if (!subjectFound) throw new Exception("Subject not found");
                                    termFound = true;
                                    break;
                                }
                            }
                            if (!termFound) throw new Exception("Term not found");
                            groupFound = true;
                            break;
                        }
                    }
                    if (!groupFound) throw new Exception("Group not found");
                    Static.T.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                // отображаем заголовок
                                activity.updateToolbar(subject.getString("name"), R.drawable.ic_e_journal);
                                // отображаем шапку
                                TextView as_info_term = activity.findViewById(R.id.as_info_term);
                                TextView as_info_type = activity.findViewById(R.id.as_info_type);
                                TextView as_result = activity.findViewById(R.id.as_result);
                                TextView as_current_points = activity.findViewById(R.id.as_current_points);
                                if (as_info_term != null) {
                                    as_info_term.setText(termSub + " " + activity.getString(R.string.semester));
                                }
                                if (as_info_type != null) {
                                    if (!Objects.equals(subject.getString("type"), "")) {
                                        as_info_type.setText(subject.getString("type"));
                                    } else {
                                        ((ViewGroup) as_info_type.getParent()).removeView(as_info_type);
                                    }
                                }
                                if (as_result != null) {
                                    if (Objects.equals(subject.getString("mark"), "")) {
                                        ((ViewGroup) as_result.getParent()).removeView(as_result);
                                    } else {
                                        as_result.setText(subject.getString("mark"));
                                    }
                                }
                                if (as_current_points != null) {
                                    Double currentPoints = subject.getDouble("currentPoints");
                                    String pointsStr = String.valueOf(currentPoints);
                                    if (currentPoints != -1.0) {
                                        if (currentPoints == Double.parseDouble(currentPoints.intValue() + ".0")) {
                                            pointsStr = currentPoints.intValue() + "";
                                        }
                                    } else {
                                        pointsStr = "";
                                    }
                                    if (!pointsStr.isEmpty()) {
                                        as_current_points.setText(pointsStr);
                                    } else {
                                        Static.removeView(as_current_points);
                                    }
                                }
                                // отображаем список оценок
                                JSONArray points = subject.getJSONArray("points");
                                LinearLayout as_container = activity.findViewById(R.id.as_container);
                                if (as_container != null) {
                                    if (points.length() == 0) {
                                        View view = inflate(R.layout.nothing_to_display);
                                        ((TextView) view.findViewById(R.id.ntd_text)).setText(R.string.no_points);
                                        as_container.addView(view);
                                    } else {
                                        boolean remove_separator_blocker, remove_separator = false;
                                        List<String> exams = Arrays.asList("экзамен", "зачет", "промежуточная аттестация");
                                        for (int i = 0; i < points.length(); i++) {
                                            try {
                                                JSONObject point = points.getJSONObject(i);
                                                String name = point.getString("name");
                                                String name_lc = name.trim().toLowerCase();
                                                View view;
                                                if (Pattern.compile("^модуль\\s\\d+$").matcher(name_lc).find() || exams.contains(name_lc)) {
                                                    view = inflate(R.layout.layout_subject_point_header);
                                                    remove_separator = true;
                                                    remove_separator_blocker = false;
                                                } else {
                                                    view = inflate(R.layout.layout_subject_point_item);
                                                    remove_separator_blocker = true;
                                                }
                                                if (remove_separator_blocker && remove_separator) {
                                                    remove_separator = false;
                                                    view.findViewById(R.id.sp_separator).setLayoutParams(new LinearLayout.LayoutParams(0, 0));
                                                }
                                                Matcher m = Pattern.compile("^(.*)\\(мод(уль)?.?\\d+\\)(.*)$").matcher(name);
                                                if (m.find()) name = m.group(1) + m.group(3);
                                                ((TextView) view.findViewById(R.id.sp_title)).setText(name);
                                                ((TextView) view.findViewById(R.id.sp_desc)).setText("[0 / " + markConverter(String.valueOf(point.getDouble("limit"))) + " / " + markConverter(String.valueOf(point.getDouble("max"))) + "]");
                                                ((TextView) view.findViewById(R.id.sp_value)).setText(markConverter(String.valueOf(point.getDouble("value"))));
                                                as_container.addView(view);
                                            } catch (Exception e) {
                                                Static.error(e);
                                                Static.snackBar(activity, activity.getString(R.string.something_went_wrong));
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                Static.error(e);
                                activity.back();
                            }
                        }
                    });
                } catch (Exception e) {
                    Static.error(e);
                    activity.back();
                }
            }
        });
    }

    private String markConverter(String value) {
        Matcher m;
        value = value.replace(",", ".").trim();
        m = Pattern.compile("^\\.(.+)").matcher(value);
        if (m.find()) value = "0." + m.group(1);
        m = Pattern.compile("(.+)\\.0$").matcher(value);
        if (m.find()) value = m.group(1);
        return value;
    }
    private View inflate(int layoutId) throws InflateException {
        return ((LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(layoutId, null);
    }
}
