package com.bukhmastov.cdoitmo.fragment.presenter.impl;

import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bukhmastov.cdoitmo.App;
import com.bukhmastov.cdoitmo.R;
import com.bukhmastov.cdoitmo.adapter.rva.ProtocolRVA;
import com.bukhmastov.cdoitmo.event.bus.EventBus;
import com.bukhmastov.cdoitmo.event.bus.annotation.Event;
import com.bukhmastov.cdoitmo.event.events.ClearCacheEvent;
import com.bukhmastov.cdoitmo.event.events.ShareTextEvent;
import com.bukhmastov.cdoitmo.factory.AppComponentProvider;
import com.bukhmastov.cdoitmo.firebase.FirebaseAnalyticsProvider;
import com.bukhmastov.cdoitmo.fragment.presenter.ProtocolFragmentPresenter;
import com.bukhmastov.cdoitmo.model.converter.ProtocolConverter;
import com.bukhmastov.cdoitmo.model.protocol.PChange;
import com.bukhmastov.cdoitmo.model.protocol.Protocol;
import com.bukhmastov.cdoitmo.network.DeIfmoRestClient;
import com.bukhmastov.cdoitmo.network.handlers.RestResponseHandler;
import com.bukhmastov.cdoitmo.network.model.Client;
import com.bukhmastov.cdoitmo.util.DateUtils;
import com.bukhmastov.cdoitmo.util.Log;
import com.bukhmastov.cdoitmo.util.NotificationMessage;
import com.bukhmastov.cdoitmo.util.Storage;
import com.bukhmastov.cdoitmo.util.StoragePref;
import com.bukhmastov.cdoitmo.util.Thread;
import com.bukhmastov.cdoitmo.util.Time;
import com.bukhmastov.cdoitmo.util.singleton.CollectionUtils;
import com.bukhmastov.cdoitmo.util.singleton.Color;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import static com.bukhmastov.cdoitmo.util.Thread.PR;

public class ProtocolFragmentPresenterImpl extends ConnectedFragmentWithDataPresenterImpl<Protocol>
        implements ProtocolFragmentPresenter, SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = "ProtocolFragment";
    private static final int maxAttempts = 3;
    private int numberOfWeeks = 1;
    private boolean spinnerWeeksBlocker = true;

    @Inject
    Log log;
    @Inject
    Thread thread;
    @Inject
    EventBus eventBus;
    @Inject
    Storage storage;
    @Inject
    StoragePref storagePref;
    @Inject
    DeIfmoRestClient deIfmoRestClient;
    @Inject
    NotificationMessage notificationMessage;
    @Inject
    Time time;
    @Inject
    DateUtils textUtils;
    @Inject
    FirebaseAnalyticsProvider firebaseAnalyticsProvider;

    public ProtocolFragmentPresenterImpl() {
        super(Protocol.class);
        AppComponentProvider.getComponent().inject(this);
        eventBus.register(this);
    }

    @Event
    public void onClearCacheEvent(ClearCacheEvent event) {
        if (event.isNot(ClearCacheEvent.PROTOCOL)) {
            return;
        }
        clearData();
    }

    @Override
    public void onStart() {
        super.onStart();
        thread.run(PR, () -> {
            if (App.UNAUTHORIZED_MODE) {
                forbidden = true;
                log.w(TAG, "UNAUTHORIZED_MODE not allowed, closing fragment...");
                thread.runOnUI(PR, () -> fragment.close());
                return;
            }
            firebaseAnalyticsProvider.logCurrentScreen(activity, fragment);
            numberOfWeeks = Integer.parseInt(storagePref.get(activity, "pref_protocol_changes_weeks", "1"));
        });
    }

    @Override
    public void onToolbarSetup(Menu menu) {
        try {
            thread.assertUI();
            if (menu == null) {
                return;
            }
            MenuItem share = menu.findItem(R.id.action_share);
            MenuItem simple = menu.findItem(R.id.action_mode_simple);
            MenuItem advanced = menu.findItem(R.id.action_mode_post_process);
            if (simple != null && advanced != null) {
                switch (storagePref.get(activity, "pref_protocol_changes_mode", "advanced")) {
                    case "simple": advanced.setVisible(true); break;
                    case "advanced": simple.setVisible(true); break;
                }
                simple.setOnMenuItemClickListener(item -> {
                    thread.runOnUI(PR, () -> {
                        storagePref.put(activity, "pref_protocol_changes_mode", "simple");
                        simple.setVisible(false);
                        advanced.setVisible(true);
                        load(false);
                    });
                    return false;
                });
                advanced.setOnMenuItemClickListener(item -> {
                    thread.runOnUI(PR, () -> {
                        storagePref.put(activity, "pref_protocol_changes_mode", "advanced");
                        simple.setVisible(true);
                        advanced.setVisible(false);
                        load(false);
                    });
                    return false;
                });
            }
            thread.run(PR, () -> {
                if (share == null) {
                    return;
                }
                Protocol data = getData();
                if (data == null || CollectionUtils.isEmpty(data.getChanges())) {
                    thread.runOnUI(PR, () -> share.setVisible(false));
                    return;
                }
                thread.runOnUI(PR, () -> {
                    share.setVisible(true);
                    share.setOnMenuItemClickListener(menuItem -> {
                        View view = activity.findViewById(R.id.action_share);
                        if (view == null) {
                            view = activity.findViewById(android.R.id.content);
                        }
                        if (view != null) {
                            share(view);
                        }
                        return true;
                    });
                });
            });
        } catch (Throwable throwable) {
            log.exception(throwable);
        }
    }

    @Override
    public void onRefresh() {
        thread.run(PR, () -> {
            log.v(TAG, "refreshing");
            load(true);
        });
    }

    protected void load() {
        thread.run(PR, () -> {
            load(storagePref.get(activity, "pref_use_cache", true) ?
                    Integer.parseInt(storagePref.get(activity, "pref_dynamic_refresh", "0")) :
                    0);
        });
    }

    private void load(int refresh_rate) {
        thread.run(PR, () -> {
            log.v(TAG, "load | refresh_rate=" + refresh_rate);
            if (!storagePref.get(activity, "pref_use_cache", true)) {
                load(false);
                return;
            }
            Protocol cache = getFromCache();
            if (cache == null) {
                load(true, null);
                return;
            }
            setData(cache);
            load(cache.getTimestamp() + refresh_rate * 3600000L < time.getTimeInMillis(), cache);
        });
    }

    private void load(boolean force) {
        thread.run(PR, () -> load(force, null, 0));
    }

    private void load(boolean force, Protocol cached) {
        thread.run(PR, () -> load(force, cached, 0));
    }

    private void load(boolean force, Protocol cached, int attempt) {
        thread.run(PR, () -> {
            log.v(TAG, "load | force=" + (force ? "true" : "false") + " | attempt=" + attempt);
            if ((!force || Client.isOffline(activity)) && storagePref.get(activity, "pref_use_cache", true)) {
                try {
                    Protocol cache = cached == null ? getFromCache() : cached;
                    if (cache != null) {
                        log.v(TAG, "load | from cache");
                        setData(cache);
                        display();
                        return;
                    }
                } catch (Exception e) {
                    log.v(TAG, "load | failed to load from cache");
                }
            }
            if (App.OFFLINE_MODE) {
                if (getData() != null) {
                    display();
                    return;
                }
                thread.runOnUI(PR, () -> {
                    fragment.draw(R.layout.state_offline_text);
                    View reload = fragment.container().findViewById(R.id.offline_reload);
                    if (reload != null) {
                        reload.setOnClickListener(v -> load());
                    }
                });
                return;
            }
            if (attempt >= maxAttempts) {
                if (force) {
                    load(false, cached, attempt + 1);
                    return;
                }
                if (getData() != null) {
                    display();
                } else {
                    loadFailed();
                }
                return;
            }
            String url = "eregisterlog?days=" + numberOfWeeks * 7;
            deIfmoRestClient.get(activity, url, null, new RestResponseHandler<Protocol>() {
                @Override
                public void onSuccess(int code, Client.Headers headers, Protocol response) throws Exception {
                    log.v(TAG, "load | success | code=", code, " | response=", response);
                    if (code == 200 && response != null) {
                        response.setTimestamp(time.getTimeInMillis());
                        response.setNumberOfWeeks(numberOfWeeks);
                        response = new ProtocolConverter(response).convert();
                        if (response != null && storagePref.get(activity, "pref_use_cache", true)) {
                            String json = response.toJsonString();
                            storage.put(activity, Storage.CACHE, Storage.USER, "protocol#core", json);
                            storage.put(activity, Storage.PERMANENT, Storage.USER, "protocol_tracker#protocol", json);
                        }
                        setData(response);
                        display();
                        return;
                    }
                    load(force, cached, attempt + 1);
                }
                @Override
                public void onFailure(int code, Client.Headers headers, int state) {
                    thread.run(PR, () -> {
                        log.v(TAG, "load | failure ", state);
                        if (state == Client.FAILED_OFFLINE) {
                            if (getData() != null) {
                                display();
                                return;
                            }
                            thread.runOnUI(PR, () -> {
                                fragment.draw(R.layout.state_offline_text);
                                View reload = fragment.container().findViewById(R.id.offline_reload);
                                if (reload != null) {
                                    reload.setOnClickListener(v -> load());
                                }
                            }, throwable -> {
                                loadFailed();
                            });
                            return;
                        }
                        thread.runOnUI(PR, () -> {
                            fragment.draw(R.layout.state_failed_button);
                            TextView message = fragment.container().findViewById(R.id.try_again_message);
                            if (message != null) {
                                message.setText(deIfmoRestClient.getFailedMessage(activity, code, state));
                            }
                            View reload = fragment.container().findViewById(R.id.try_again_reload);
                            if (reload != null) {
                                reload.setOnClickListener(v -> load());
                            }
                        }, throwable -> {
                            loadFailed();
                        });
                    }, throwable -> {
                        loadFailed();
                    });
                }
                @Override
                public void onProgress(int state) {
                    thread.runOnUI(PR, () -> {
                        log.v(TAG, "load | progress ", state);
                        fragment.draw(R.layout.state_loading_text);
                        TextView message = fragment.container().findViewById(R.id.loading_message);
                        if (message != null) {
                            message.setText(deIfmoRestClient.getProgressMessage(activity, state));
                        }
                    });
                }
                @Override
                public void onNewRequest(Client.Request request) {
                    requestHandle = request;
                }
                @Override
                public Protocol newInstance() {
                    return new Protocol();
                }
                @Override
                public JSONObject convertArray(JSONArray arr) throws JSONException {
                    return new JSONObject().put("protocol", arr);
                }
            });
        }, throwable -> {
            loadFailed();
        });
    }

    private void loadFailed() {
        thread.runOnUI(PR, () -> {
            log.v(TAG, "loadFailed");
            fragment.draw(R.layout.state_failed_button);
            TextView message = fragment.container().findViewById(R.id.try_again_message);
            if (message != null) {
                message.setText(R.string.load_failed_retry_in_minute);
            }
            View reload = fragment.container().findViewById(R.id.try_again_reload);
            if (reload != null) {
                reload.setOnClickListener(v -> load());
            }
        }, throwable -> {});
    }

    protected void display() {
        thread.run(PR, () -> {
            log.v(TAG, "display");
            Protocol data = getData();
            if (data == null) {
                loadFailed();
                return;
            }
            ProtocolRVA adapter = new ProtocolRVA(activity, data, "advanced".equals(storagePref.get(activity, "pref_protocol_changes_mode", "advanced")));
            thread.runOnUI(PR, () -> {
                onToolbarSetup(fragment.toolbar());
                fragment.draw(R.layout.layout_protocol);
                // set adapter to recycler view
                LinearLayoutManager layoutManager = new LinearLayoutManager(activity, RecyclerView.VERTICAL, false);
                RecyclerView recyclerView = fragment.container().findViewById(R.id.protocol_list);
                if (recyclerView != null) {
                    recyclerView.setLayoutManager(layoutManager);
                    recyclerView.setAdapter(adapter);
                    recyclerView.setHasFixedSize(true);
                }
                // setup swipe
                SwipeRefreshLayout swipe = fragment.container().findViewById(R.id.swipe_container);
                if (swipe != null) {
                    swipe.setColorSchemeColors(Color.resolve(activity, R.attr.colorAccent));
                    swipe.setProgressBackgroundColorSchemeColor(Color.resolve(activity, R.attr.colorBackgroundRefresh));
                    swipe.setOnRefreshListener(this);
                }
                // setup spinner: weeks
                Spinner spinner = fragment.container().findViewById(R.id.pl_weeks_spinner);
                if (spinner != null) {
                    final ArrayList<String> weekLabelArr = new ArrayList<>();
                    final ArrayList<Integer> weekArr = new ArrayList<>();
                    for (int i = 1; i <= 4; i++) {
                        String value = activity.getString(R.string.for_the) + " ";
                        switch (i){
                            case 1: value += activity.getString(R.string.last_week); break;
                            case 2: value += activity.getString(R.string.last_2_weeks); break;
                            case 3: value += activity.getString(R.string.last_3_weeks); break;
                            case 4: value += activity.getString(R.string.last_4_weeks); break;
                        }
                        weekArr.add(i);
                        weekLabelArr.add(value);
                    }
                    spinner.setAdapter(new ArrayAdapter<>(activity, R.layout.spinner_center_single_line, weekLabelArr));
                    spinner.setSelection(data.getNumberOfWeeks() - 1);
                    spinnerWeeksBlocker = true;
                    spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        public void onItemSelected(AdapterView<?> parent, View item, int position, long selectedId) {
                            thread.run(PR, () -> {
                                if (spinnerWeeksBlocker) {
                                    spinnerWeeksBlocker = false;
                                    return;
                                }
                                numberOfWeeks = weekArr.get(position);
                                log.v(TAG, "Number of weeks selected | numberOfWeeks=" + numberOfWeeks);
                                load(true);
                            });
                        }
                        public void onNothingSelected(AdapterView<?> parent) {}
                    });
                }
                // show update time
                notificationMessage.showUpdateTime(activity, data.getTimestamp());
            }, throwable -> {
                log.exception(throwable);
                loadFailed();
            });
        }, throwable -> {
            log.exception(throwable);
            loadFailed();
        });
    }

    private void share(View view) {
        thread.run(PR, () -> {
            Protocol data = getData();
            if (data == null || data.getChanges() == null) {
                return;
            }
            Map<String, List<PChange>> subjects = new HashMap<>();
            for (PChange change : data.getChanges()) {
                if (change == null) {
                    continue;
                }
                String sbj = change.getSubject();
                if (!subjects.containsKey(sbj)) {
                    subjects.put(sbj, new ArrayList<>());
                }
                subjects.get(sbj).add(change);
            }
            thread.runOnUI(PR, () -> {
                SparseArray<Map<String, List<PChange>>> menuSubjectsMap = new SparseArray<>();
                PopupMenu popup = new PopupMenu(activity, view);
                popup.inflate(R.menu.protocol_share);
                if (subjects.size() == 0) {
                    popup.getMenu().findItem(R.id.share_protocol_subject).setVisible(false);
                } else {
                    Menu subMenu = popup.getMenu().findItem(R.id.share_protocol_subject).getSubMenu();
                    for (Map.Entry<String, List<PChange>> entry : subjects.entrySet()) {
                        String subject = entry.getKey();
                        List<PChange> changes = entry.getValue();
                        int id = View.generateViewId();
                        subMenu.add(Menu.NONE, id, Menu.NONE, subject);
                        Map<String, List<PChange>> currentSubjects = new HashMap<>();
                        currentSubjects.put(subject, changes);
                        menuSubjectsMap.put(id, currentSubjects);
                    }
                }
                popup.setOnMenuItemClickListener(menuItem -> {
                    thread.run(PR, () -> {
                        switch (menuItem.getItemId()) {
                            case R.id.share_all_protocol: share(subjects); break;
                            default:
                                Map<String, List<PChange>> sbj = menuSubjectsMap.get(menuItem.getItemId());
                                if (sbj != null) {
                                    share(sbj);
                                }
                                break;
                        }
                    }, throwable -> {
                        log.exception(throwable);
                        notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
                    });
                    popup.dismiss();
                    return true;
                });
                popup.show();
            }, throwable -> {
                log.exception(throwable);
                notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
            });
        }, throwable -> {
            log.exception(throwable);
            notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
        });
    }

    private void share(Map<String, List<PChange>> subjects) {
        thread.run(PR, () -> {
            if (subjects == null) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Мой протокол изменений:");
            if (subjects.size() == 0) {
                sb.append(activity.getString(R.string.no_changes_for_period));
            } else {
                for (Map.Entry<String, List<PChange>> entry : subjects.entrySet()) {
                    sb.append("\n");
                    sb.append(entry.getKey());
                    for (PChange change : entry.getValue()) {
                        sb.append("\n");
                        sb.append(change.getName());
                        sb.append(" (");
                        sb.append(change.getDate());
                        sb.append("): ");
                        sb.append(change.getValue());
                        sb.append("/");
                        sb.append(change.getMax());
                    }
                    sb.append("\n");
                }
            }
            eventBus.fire(new ShareTextEvent(sb.toString().trim(), "txt_protocol"));
        }, throwable -> {
            log.exception(throwable);
            notificationMessage.snackBar(activity, activity.getString(R.string.something_went_wrong));
        });
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected String getCacheType() {
        return Storage.USER;
    }

    @Override
    protected String getCachePath() {
        return "protocol#core";
    }

    @Override
    protected String getThreadToken() {
        return PR;
    }
}
