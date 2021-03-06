package com.bukhmastov.cdoitmo.fragment.presenter.impl;

import android.util.SparseArray;

import com.bukhmastov.cdoitmo.R;
import com.bukhmastov.cdoitmo.activity.ConnectedActivity;
import com.bukhmastov.cdoitmo.factory.AppComponentProvider;
import com.bukhmastov.cdoitmo.fragment.presenter.ScheduleExamsTabHostFragmentPresenter;
import com.bukhmastov.cdoitmo.util.NotificationMessage;
import com.bukhmastov.cdoitmo.util.Thread;

import javax.inject.Inject;

import static com.bukhmastov.cdoitmo.util.Thread.SE;

public class ScheduleExamsTabHostFragmentPresenterImpl implements ScheduleExamsTabHostFragmentPresenter {

    private static final String FRAGMENT_NAME = "com.bukhmastov.cdoitmo.fragments.ScheduleExamsTabHostFragment";
    private ConnectedActivity activity = null;
    private String lastQuery = null;
    private String query = null;
    private final SparseArray<Scroll> scroll = new SparseArray<>();
    private final SparseArray<TabProvider> tabs = new SparseArray<>();

    @Inject
    Thread thread;
    @Inject
    NotificationMessage notificationMessage;

    public ScheduleExamsTabHostFragmentPresenterImpl() {
        AppComponentProvider.getComponent().inject(this);
    }

    @Override
    public void onAttach(ConnectedActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onDetach() {
        if (!isActive()) {
            activity = null;
        }
    }

    @Override
    public void onDestroy() {
        if (!isActive()) {
            scroll.clear();
            tabs.clear();
        }
    }

    @Override
    public boolean isActive() {
        for (int i = 0; i < TAB_TOTAL_COUNT; i++) {
            if (tabs.get(i) != null) return true;
        }
        return false;
    }

    @Override
    public void setQuery(String query) {
        this.lastQuery = this.query;
        this.query = query;
        storeData(query);
    }

    @Override
    public String getQuery() {
        return query;
    }

    @Override
    public boolean isSameQueryRequested() {
        return lastQuery != null && lastQuery.equals(query);
    }

    @Override
    public void invalidate() {
        invalidate(false);
    }

    @Override
    public void invalidate(boolean refresh) {
        invalidate(-1, refresh);
    }

    @Override
    public void invalidate(int exclude, boolean refresh) {
        for (int i = 0; i < TAB_TOTAL_COUNT; i++) {
            if (i == exclude) continue;
            TabProvider tabProvider = tabs.get(i);
            if (tabProvider != null) {
                tabProvider.onInvalidate(refresh);
            }
        }
    }

    @Override
    public void invalidateOnDemand() {
        if (!isActive() || activity == null) return;
        notificationMessage.snackBar(activity, activity.findViewById(android.R.id.content), activity.getString(R.string.schedule_refresh), activity.getString(R.string.update), v -> thread.run(SE, () -> {
            setQuery(getQuery());
            invalidate();
        }));
    }

    @Override
    public void storeData(String data) {
        if (activity != null) {
            ConnectedActivity.storedFragmentName = FRAGMENT_NAME;
            ConnectedActivity.storedFragmentData = data;
            ConnectedActivity.storedFragmentExtra = null;
        }
    }

    @Override
    public String restoreData() {
        if (activity != null && FRAGMENT_NAME.equals(ConnectedActivity.storedFragmentName)) {
            return ConnectedActivity.storedFragmentData;
        } else {
            return null;
        }
    }

    @Override
    public SparseArray<Scroll> scroll() {
        return scroll;
    }

    @Override
    public SparseArray<TabProvider> tabs() {
        return tabs;
    }
}
