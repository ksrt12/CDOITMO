package com.bukhmastov.cdoitmo.fragment.presenter.impl;

import android.content.Context;
import android.os.Bundle;
import android.view.InflateException;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bukhmastov.cdoitmo.App;
import com.bukhmastov.cdoitmo.R;
import com.bukhmastov.cdoitmo.activity.UniversityPersonCardActivity;
import com.bukhmastov.cdoitmo.adapter.rva.RVA;
import com.bukhmastov.cdoitmo.adapter.rva.UniversityPersonsRVA;
import com.bukhmastov.cdoitmo.adapter.rva.UniversityRVA;
import com.bukhmastov.cdoitmo.event.bus.EventBus;
import com.bukhmastov.cdoitmo.event.bus.annotation.Event;
import com.bukhmastov.cdoitmo.event.events.ClearCacheEvent;
import com.bukhmastov.cdoitmo.event.events.OpenActivityEvent;
import com.bukhmastov.cdoitmo.factory.AppComponentProvider;
import com.bukhmastov.cdoitmo.firebase.FirebaseAnalyticsProvider;
import com.bukhmastov.cdoitmo.fragment.presenter.UniversityPersonsFragmentPresenter;
import com.bukhmastov.cdoitmo.model.rva.RVASingleValue;
import com.bukhmastov.cdoitmo.model.university.persons.UPerson;
import com.bukhmastov.cdoitmo.model.university.persons.UPersons;
import com.bukhmastov.cdoitmo.network.IfmoRestClient;
import com.bukhmastov.cdoitmo.network.handlers.RestResponseHandler;
import com.bukhmastov.cdoitmo.network.model.Client;
import com.bukhmastov.cdoitmo.util.Log;
import com.bukhmastov.cdoitmo.util.Storage;
import com.bukhmastov.cdoitmo.util.StoragePref;
import com.bukhmastov.cdoitmo.util.Thread;
import com.bukhmastov.cdoitmo.util.Time;
import com.bukhmastov.cdoitmo.util.singleton.CollectionUtils;
import com.bukhmastov.cdoitmo.util.singleton.Color;
import com.bukhmastov.cdoitmo.util.singleton.StringUtils;
import com.bukhmastov.cdoitmo.view.RecyclerViewOnScrollListener;

import java.util.ArrayList;
import java.util.Collection;

import javax.inject.Inject;

import static com.bukhmastov.cdoitmo.util.Thread.UP;

public class UniversityPersonsFragmentPresenterImpl implements UniversityPersonsFragmentPresenter, SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = "UniversityPersonsFragment";
    private FragmentActivity activity = null;
    private Fragment fragment = null;
    private View container;
    private Client.Request requestHandle = null;
    private boolean loaded = false;
    private UPersons persons = null;
    private final int limit = 20;
    private int offset = 0;
    private String search = "";
    private UniversityPersonsRVA personsRecyclerViewAdapter = null;

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
    IfmoRestClient ifmoRestClient;
    @Inject
    Time time;
    @Inject
    FirebaseAnalyticsProvider firebaseAnalyticsProvider;

    public UniversityPersonsFragmentPresenterImpl() {
        AppComponentProvider.getComponent().inject(this);
        eventBus.register(this);
    }

    @Event
    public void onClearCacheEvent(ClearCacheEvent event) {
        if (event.isNot(ClearCacheEvent.UNIVERSITY)) {
            return;
        }
        persons = null;
    }

    @Override
    public void setFragment(Fragment fragment) {
        this.fragment = fragment;
        this.activity = fragment.getActivity();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        thread.initialize(UP);
        log.v(TAG, "onCreate");
        firebaseAnalyticsProvider.logCurrentScreen(activity, fragment);
    }

    @Override
    public void onDestroy() {
        log.v(TAG, "onDestroy");
        loaded = false;
        thread.interrupt(UP);
    }

    @Override
    public void onResume() {
        thread.run(UP, () -> {
            log.v(TAG, "onResume");
            firebaseAnalyticsProvider.setCurrentScreen(activity, fragment);
            if (!loaded) {
                loaded = true;
                load();
            }
        });
    }

    @Override
    public void onPause() {
        log.v(TAG, "onPause");
        thread.standalone(() -> {
            if (requestHandle != null && requestHandle.cancel()) {
                loaded = false;
            }
        });
    }

    @Override
    public void onCreateView(View container) {
        this.container = container;
    }

    @Override
    public void onRefresh() {
        log.v(TAG, "refreshing");
        load(search, true);
    }

    private void load() {
        thread.run(UP, () -> load(""));
    }

    private void load(String search) {
        thread.run(UP, () -> {
            load(search, storagePref.get(activity, "pref_use_cache", true) && storagePref.get(activity, "pref_use_university_cache", false)
                    ? Integer.parseInt(storagePref.get(activity, "pref_static_refresh", "168"))
                    : 0);
        });
    }

    private void load(String search, int refresh_rate) {
        thread.run(UP, () -> {
            log.v(TAG, "load | search=", search, " | refresh_rate=", refresh_rate);
            if (!(storagePref.get(activity, "pref_use_cache", true) && storagePref.get(activity, "pref_use_university_cache", false))) {
                load(search, false);
                return;
            }
            UPersons cache = getFromCache();
            if (cache == null) {
                load(search, true);
                return;
            }
            persons = cache;
            load(search, cache.getTimestamp() + refresh_rate * 3600000L < time.getTimeInMillis());
        }, throwable -> {
            loadFailed();
        });
    }

    private void load(String search, boolean force) {
        thread.run(UP, () -> {
            log.v(TAG, "load | search=", search, " | force=", force);
            if ((!force || Client.isOffline(activity)) && persons != null) {
                display();
                return;
            }
            if (App.OFFLINE_MODE) {
                thread.runOnUI(UP, () -> {
                    draw(R.layout.state_offline_text);
                    View reload = container.findViewById(R.id.offline_reload);
                    if (reload != null) {
                        reload.setOnClickListener(v -> load(search));
                    }
                }, throwable -> {
                    loadFailed();
                });
                return;
            }
            this.offset = 0;
            this.search = search;
            loadProvider(new RestResponseHandler<UPersons>() {
                @Override
                public void onSuccess(int code, Client.Headers headers, UPersons response) throws Exception {
                    if (code == 200 && response != null) {
                        response.setTimestamp(time.getTimeInMillis());
                        if (storagePref.get(activity, "pref_use_cache", true) && storagePref.get(activity, "pref_use_university_cache", false)) {
                            storage.put(activity, Storage.CACHE, Storage.GLOBAL, "university#persons", response.toJsonString());
                        }
                        persons = response;
                        display();
                        return;
                    }
                    loadFailed();
                }
                @Override
                public void onFailure(int code, Client.Headers headers, int state) {
                    thread.runOnUI(UP, () -> {
                        log.v(TAG, "load | failure ", state);
                        if (state == Client.FAILED_OFFLINE) {
                            draw(R.layout.state_offline_text);
                            View reload = container.findViewById(R.id.offline_reload);
                            if (reload != null) {
                                reload.setOnClickListener(v -> load());
                            }
                            return;
                        }
                        draw(R.layout.state_failed_button);
                        TextView message = activity.findViewById(R.id.try_again_message);
                        if (message != null) {
                            message.setText(ifmoRestClient.getFailedMessage(activity, code, state));
                        }
                        View reload = container.findViewById(R.id.try_again_reload);
                        if (reload != null) {
                            reload.setOnClickListener(v -> load());
                        }
                    }, throwable -> {
                        loadFailed();
                    });
                }
                @Override
                public void onProgress(int state) {
                    thread.runOnUI(UP, () -> {
                        log.v(TAG, "load | progress ", state);
                        draw(R.layout.state_loading_text);
                        TextView message = container.findViewById(R.id.loading_message);
                        if (message != null) {
                            message.setText(ifmoRestClient.getProgressMessage(activity, state));
                        }
                    });
                }
                @Override
                public void onNewRequest(Client.Request request) {
                    requestHandle = request;
                }
                @Override
                public UPersons newInstance() {
                    return new UPersons();
                }
            });
        }, throwable -> {
            loadFailed();
        });
    }

    private void loadProvider(RestResponseHandler<UPersons> handler) {
        log.v(TAG, "loadProvider");
        ifmoRestClient.get(activity, "person?limit=" + limit + "&offset=" + offset + "&search=" + search, null, handler);
    }

    private void loadFailed() {
        thread.runOnUI(UP, () -> {
            log.v(TAG, "loadFailed");
            draw(R.layout.state_failed_button);
            TextView message = container.findViewById(R.id.try_again_message);
            if (message != null) {
                message.setText(R.string.load_failed);
            }
            View reload = container.findViewById(R.id.try_again_reload);
            if (reload != null) {
                reload.setOnClickListener(v -> load());
            }
        }, throwable -> {
            log.exception(throwable);
        });
    }

    private void display() {
        thread.runOnUI(UP, () -> {
            log.v(TAG, "display");
            if (persons == null) {
                loadFailed();
                return;
            }
            draw(R.layout.layout_university_list_infinite);
            // поиск
            final EditText searchInput = container.findViewById(R.id.search_input);
            final FrameLayout searchAction = container.findViewById(R.id.search_action);
            searchAction.setOnClickListener(v -> load(searchInput.getText().toString().trim(), true));
            searchInput.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                    load(searchInput.getText().toString().trim(), true);
                    return true;
                }
                return false;
            });
            searchInput.setText(search);
            // очищаем сообщение
            ViewGroup listInfo = container.findViewById(R.id.infinite_list_info);
            listInfo.removeAllViews();
            listInfo.setPadding(0, 0, 0, 0);
            // список
            if (CollectionUtils.isNotEmpty(persons.getPeople())) {
                personsRecyclerViewAdapter = new UniversityPersonsRVA(activity);
                RecyclerView list = container.findViewById(R.id.infinite_list);
                if (list != null) {
                    list.setLayoutManager(new LinearLayoutManager(activity));
                    list.setAdapter(personsRecyclerViewAdapter);
                    list.addOnScrollListener(new RecyclerViewOnScrollListener(container));
                }
                personsRecyclerViewAdapter.setClickListener(R.id.person, (v, entity) -> thread.run(UP, () -> {
                    if (!(entity.getEntity() instanceof UPerson)) {
                        return;
                    }
                    UPerson person = (UPerson) entity.getEntity();
                    Bundle extras = new Bundle();
                    extras.putInt("pid", person.getId());
                    extras.putSerializable("person", person);
                    eventBus.fire(new OpenActivityEvent(UniversityPersonCardActivity.class, extras));
                }));
                personsRecyclerViewAdapter.setClickListener(R.id.load_more, (v, entity) -> thread.run(UP, () -> {
                    offset += limit;
                    thread.runOnUI(UP, () -> personsRecyclerViewAdapter.setState(R.id.loading_more));
                    loadProvider(new RestResponseHandler<UPersons>() {
                        @Override
                        public void onSuccess(int code, Client.Headers headers, UPersons response) throws Exception {
                            if (code == 200 && response != null) {
                                persons.getPeople().addAll(response.getPeople());
                                persons.setCount(response.getCount());
                                persons.setLimit(response.getLimit());
                                persons.setOffset(response.getOffset());
                                persons.setTimestamp(time.getTimeInMillis());
                                if (storagePref.get(activity, "pref_use_cache", true) && storagePref.get(activity, "pref_use_university_cache", false)) {
                                    storage.put(activity, Storage.CACHE, Storage.GLOBAL, "university#persons", persons.toJsonString());
                                }
                                displayPeople(response.getPeople());
                                return;
                            }
                            thread.runOnUI(UP, () -> personsRecyclerViewAdapter.setState(R.id.load_more));
                        }
                        @Override
                        public void onFailure(int code, Client.Headers headers, int state) {
                            thread.runOnUI(UP, () -> personsRecyclerViewAdapter.setState(R.id.load_more));
                        }
                        @Override
                        public void onProgress(int state) {}
                        @Override
                        public void onNewRequest(Client.Request request) {
                            requestHandle = request;
                        }
                        @Override
                        public UPersons newInstance() {
                            return new UPersons();
                        }
                    });
                }));
                if (persons.getTimestamp() > 0 && persons.getTimestamp() + 5000 < time.getCalendar().getTimeInMillis()) {
                    personsRecyclerViewAdapter.addItem(new UniversityRVA.Item<>(
                            UniversityRVA.TYPE_INFO_ABOUT_UPDATE_TIME,
                            new RVASingleValue(activity.getString(R.string.update_date) + " " + time.getUpdateTime(activity, persons.getTimestamp()))
                    ));
                }
                displayPeople(persons.getPeople());
            } else {
                View view = inflate(R.layout.state_nothing_to_display_compact);
                ((TextView) view.findViewById(R.id.ntd_text)).setText(R.string.no_persons);
                listInfo.addView(view);
            }
            // добавляем отступ
            container.findViewById(R.id.top_panel).post(() -> {
                try {
                    int height = container.findViewById(R.id.top_panel).getHeight();
                    RecyclerView list = container.findViewById(R.id.infinite_list);
                    list.setPadding(0, height, 0, 0);
                    list.scrollToPosition(0);
                    if (listInfo.getChildCount() > 0) {
                        listInfo.setPadding(0, height, 0, 0);
                    }
                } catch (Exception ignore) {
                    // ignore
                }
            });
            // работаем со свайпом
            SwipeRefreshLayout swipe = container.findViewById(R.id.infinite_list_swipe);
            if (swipe != null) {
                swipe.setColorSchemeColors(Color.resolve(activity, R.attr.colorAccent));
                swipe.setProgressBackgroundColorSchemeColor(Color.resolve(activity, R.attr.colorBackgroundRefresh));
                swipe.setOnRefreshListener(this);
            }
        }, throwable -> {
            log.exception(throwable);
            loadFailed();
        });
    }

    private void displayPeople(Collection<UPerson> people) {
        thread.run(UP, () -> {
            Collection<RVA.Item> items = new ArrayList<>();
            for (UPerson person : people) {
                items.add(new UniversityPersonsRVA.Item<>(
                        UniversityPersonsRVA.TYPE_MAIN,
                        person
                ));
            }
            if (items.size() == 0) {
                items.add(new UniversityRVA.Item(UniversityPersonsRVA.TYPE_NO_DATA));
            }
            thread.runOnUI(UP, () -> {
                if (personsRecyclerViewAdapter != null) {
                    personsRecyclerViewAdapter.addItems(items);
                    if (persons != null && offset + limit < persons.getCount()) {
                        personsRecyclerViewAdapter.setState(R.id.load_more);
                    } else {
                        personsRecyclerViewAdapter.setState(R.id.no_more);
                    }
                }
            }, throwable -> {
                log.exception(throwable);
                loadFailed();
            });
        }, throwable -> {
            log.exception(throwable);
            loadFailed();
        });
    }

    private UPersons getFromCache() {
        thread.assertNotUI();
        String cache = storage.get(activity, Storage.CACHE, Storage.GLOBAL, "university#persons").trim();
        if (StringUtils.isBlank(cache)) {
            return null;
        }
        try {
            return new UPersons().fromJsonString(cache);
        } catch (Exception e) {
            storage.delete(activity, Storage.CACHE, Storage.GLOBAL, "university#persons");
            return null;
        }
    }

    private void draw(int layoutId) {
        try {
            ViewGroup vg = ((ViewGroup) container);
            if (vg != null) {
                vg.removeAllViews();
                vg.addView(inflate(layoutId), 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
        } catch (Exception e){
            log.exception(e);
        }
    }

    private View inflate(@LayoutRes int layoutId) throws InflateException {
        return ((LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(layoutId, null);
    }
}
