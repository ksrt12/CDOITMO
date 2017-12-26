package com.bukhmastov.cdoitmo.converters.schedule.lessons;

import com.bukhmastov.cdoitmo.utils.Log;
import com.bukhmastov.cdoitmo.utils.Static;

import org.json.JSONArray;
import org.json.JSONObject;

public class ScheduleLessonsConverterIfmo extends ScheduleLessonsConverter {

    private static final String TAG = "SLConverterIfmo";

    public ScheduleLessonsConverterIfmo(JSONObject data, JSONObject template, response delegate) {
        super(data, template, delegate);
    }

    @Override
    public void run() {
        Log.v(TAG, "converting");
        try {
            templateType = template.getString("type");
            templateTitle = templateType.equals("mine") ? "" : null;
            final JSONArray scheduleConverted = getEmptySchedule();
            final JSONArray schedule = data.getJSONArray("schedule");
            for (int i = 0; i < schedule.length(); i++) {
                final JSONObject lesson = schedule.getJSONObject(i);
                final int weekday = lesson.has("data_day") ? (lesson.isNull("data_day") ? 7 : lesson.getInt("data_day")) : -1;
                if (weekday == -1) continue;
                if (weekday > 6 && scheduleConverted.length() < weekday + 1) {
                    scheduleConverted.put(weekday, new JSONObject()
                            .put("weekday", weekday)
                            .put("type", "unknown")
                            .put("title", "")
                            .put("lessons", new JSONArray())
                    );
                }
                final String group_name = getString(lesson, "gr");
                if (templateTitle == null && templateType.equals("group")) templateTitle = group_name;
                JSONObject dayConverted = scheduleConverted.getJSONObject(weekday);
                JSONArray lessonsConverted = dayConverted.getJSONArray("lessons");
                lessonsConverted.put(getConvertedLesson(lesson, group_name));
            }
            // sort lessons of each day
            for (int i = 0; i < scheduleConverted.length(); i++) {
                JSONObject dayConverted = scheduleConverted.getJSONObject(i);
                dayConverted.put("lessons", sortLessonsByTime(dayConverted.getJSONArray("lessons")));
            }
            // finish
            template.put("title", templateTitle == null ? "" : templateTitle);
            template.put("schedule", scheduleConverted);
            Log.v(TAG, "converted");
            delegate.finish(template);
        } catch (Exception e) {
            Log.v(TAG, "conversion failed");
            Static.error(e);
            delegate.finish(null);
        }
    }

    @Override
    protected JSONObject getConvertedLesson(JSONObject lesson, String group_name) throws Exception {
        JSONObject lessonConverted = new JSONObject();
        // title
        String subject = Static.escapeString(getString(lesson, "title"));
        String note = getString(lesson, "note", null);
        if (note != null && subject != null) {
            if (!subject.isEmpty()) subject += ": ";
            subject += Static.escapeString(note.trim());
        }
        lessonConverted.put("subject", subject);
        // type
        String type = getString(lesson, "status");
        switch (type) {
            case "Лек": case "Лекция": type = "lecture"; break;
            case "Прак": case "Практика": type = "practice"; break;
            case "Лаб": case "Лабораторная": type = "lab"; break;
        }
        lessonConverted.put("type", type);
        // parity
        int parity = lesson.has("data_week") ? lesson.getInt("data_week") : 0;
        switch (parity) {
            case 2: parity = 1; break; // even
            case 1: parity = 0; break; // odd
            case 0:
            default: parity = 2; break; // both
        }
        lessonConverted.put("week", parity);
        // time
        lessonConverted.put("timeStart", getString(lesson, "start_time", "∞"));
        lessonConverted.put("timeEnd", getString(lesson, "end_time", "∞"));
        // group
        lessonConverted.put("group", getString(lesson, "gr"));
        // teacher
        String teacher_name = getString(lesson, "person");
        lessonConverted.put("teacher", teacher_name);
        lessonConverted.put("teacher_id", getString(lesson, "pid"));
        if (templateTitle == null && templateType.equals("teacher") && !teacher_name.isEmpty()) templateTitle = teacher_name;
        // place
        String auditory_name = getString(lesson, "room");
        lessonConverted.put("room", auditory_name);
        lessonConverted.put("building", getString(lesson, "place"));
        if (templateTitle == null && templateType.equals("room") && !auditory_name.isEmpty()) templateTitle = auditory_name;
        // in app type
        lessonConverted.put("cdoitmo_type", "normal");
        // that's all
        return lessonConverted;
    }
}
