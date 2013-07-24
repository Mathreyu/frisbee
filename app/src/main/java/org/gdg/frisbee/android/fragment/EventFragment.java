/*
 * Copyright 2013 The GDG Frisbee Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gdg.frisbee.android.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.AdapterView;
import android.widget.ListView;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import java.util.ArrayList;
import org.gdg.frisbee.android.R;
import org.gdg.frisbee.android.adapter.EventAdapter;
import org.gdg.frisbee.android.api.ApiRequest;
import org.gdg.frisbee.android.api.GroupDirectory;
import org.gdg.frisbee.android.api.model.Event;
import org.gdg.frisbee.android.api.model.EventDetail;
import org.gdg.frisbee.android.app.App;
import org.gdg.frisbee.android.cache.ModelCache;
import org.gdg.frisbee.android.utils.Utils;
import org.joda.time.DateTime;
import org.joda.time.MutableDateTime;
import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;

/**
 * GDG Aachen
 * org.gdg.frisbee.android.fragment
 * <p/>
 * User: maui
 * Date: 22.04.13
 * Time: 23:10
 */
public class EventFragment extends GdgListFragment {

    private static final String LOG_TAG = "GDG-EventFragment";
    private GroupDirectory mClient;

    private int mSelectedMonth;
    private EventAdapter mAdapter;

    private ArrayList<Event> mEvents;
    private ApiRequest mFetchEvents;
    private int mRetries = 5;
    private int mFetchFuture = 1;
    private Response.Listener<ArrayList<Event>> mListener;
    private Response.ErrorListener mErrorListener;
    private DateTime mStart, mEnd;

    public static EventFragment newInstance(String plusId) {
        EventFragment fragment = new EventFragment();
        Bundle arguments = new Bundle();
        arguments.putString("plus_id", plusId);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mSelectedMonth = DateTime.now().getMonthOfYear()+1;
        mClient = new GroupDirectory();

        if(getListView() instanceof ListView) {
            ListView listView = (ListView) getListView();
            listView.setDivider(null);
            listView.setDividerHeight(0);
        }

        registerForContextMenu(getListView());

        mAdapter = new EventAdapter(getActivity());
        setListAdapter(mAdapter);

        mStart = new DateTime().plusMonths(mFetchFuture).dayOfMonth().withMinimumValue();
        mEnd = new DateTime().plusMonths(mFetchFuture).dayOfMonth().withMaximumValue();
        setIsLoading(true);

        mEvents = new ArrayList<Event>();
        mListener = new Response.Listener<ArrayList<Event>>() {
            @Override
            public void onResponse(final ArrayList<Event> events) {
                mEvents.addAll(events);

                if(mFetchFuture > 0 || (mEvents.size() == 0 && mRetries > 0)) {
                    if(mFetchFuture > 0) {
                        mFetchFuture--;
                    } else {
                        mRetries--;
                    }

                    mStart = mStart.minusMonths(1).dayOfMonth().withMinimumValue();
                    mEnd = mEnd.minusMonths(1).dayOfMonth().withMaximumValue();
                    mFetchEvents = mClient.getChapterEventList(mStart, mEnd, getArguments().getString("plus_id"), mListener , mErrorListener);
                    mFetchEvents.execute();
                } else {
                    mRetries = 5;
                    mFetchFuture = 1;
                    App.getInstance().getModelCache().putAsync("event_"+ getArguments().getString("plus_id"), mEvents, DateTime.now().plusHours(2), new ModelCache.CachePutListener() {
                        @Override
                        public void onPutIntoCache() {
                            mAdapter.addAll(mEvents);
                            setIsLoading(false);
                        }
                    });
                }
            }
        };
        mErrorListener = new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                setIsLoading(false);
                Crouton.makeText(getActivity(), getString(R.string.fetch_events_failed), Style.ALERT).show();
            }
        };
        mFetchEvents = mClient.getChapterEventList(mStart, mEnd, getArguments().getString("plus_id"), mListener , mErrorListener);

        if(Utils.isOnline(getActivity())) {
            mFetchEvents.execute();
        } else {
            App.getInstance().getModelCache().getAsync("event_"+ getArguments().getString("plus_id"), false, new ModelCache.CacheListener() {
                @Override
                public void onGet(Object item) {
                    ArrayList<Event> events = (ArrayList<Event>)item;

                    mAdapter.addAll(events);
                    setIsLoading(false);
                    Crouton.makeText(getActivity(), getString(R.string.cached_content), Style.INFO).show();
                }

                @Override
                public void onNotFound(String key) {
                    setIsLoading(false);
                    Crouton.makeText(getActivity(), getString(R.string.offline_alert), Style.ALERT).show();
                }
            });
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        Event event = (Event) mAdapter.getItem(info.position);
        menu.setHeaderTitle(event.getTitle());
        getActivity().getMenuInflater().inflate(R.menu.event_context, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        Event event = (Event) mAdapter.getItem(info.position);

        switch(item.getItemId()) {
            case R.id.add_calendar:
                addEventToCalendar(event);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        final Event event = (Event) mAdapter.getItem(position);

        App.getInstance().getModelCache().getAsync("event_detail_"+event.getId(), new ModelCache.CacheListener() {
            @Override
            public void onGet(Object item) {
                EventDetail eventDetail = (EventDetail) item;
                openEventInGPlus(eventDetail.getGplusEventUrl());
            }

            @Override
            public void onNotFound(String key) {
                mClient.getEventDetail(event.getId(), new Response.Listener<EventDetail>() {
                            @Override
                            public void onResponse(final EventDetail eventDetail) {
                                App.getInstance().getModelCache().putAsync("event_detail_"+event.getId(), eventDetail, new ModelCache.CachePutListener() {
                                    @Override
                                    public void onPutIntoCache() {
                                        openEventInGPlus(eventDetail.getGplusEventUrl());
                                    }
                                });
                            }
                        }, new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError volleyError) {
                                //To change body of implemented methods use File | Settings | File Templates.
                            }
                        }).execute();
            }
        });
    }

    public void openEventInGPlus(String uri) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(uri));
        startActivity(i);
    }

    public void addEventToCalendar(Event event) {
        Intent intent = new Intent(Intent.ACTION_EDIT);
        intent.setType("vnd.android.cursor.item/event");

        intent.putExtra("beginTime", event.getStart().getMillis());
        intent.putExtra("endTime", event.getStart().getMillis());
        intent.putExtra("allDay", event.isAllDay());
        intent.putExtra("title", event.getTitle());
        intent.putExtra("eventLocation", event.getLocation());
        startActivity(intent);
    }

    private DateTime getMonthStart(int month) {
        MutableDateTime date = new MutableDateTime();
        date.setDayOfMonth(1);
        date.setMillisOfDay(0);
        date.setMonthOfYear(month);
        return date.toDateTime();
    }

    private DateTime getMonthEnd(int month) {
        MutableDateTime date = MutableDateTime.now();
        date.setMillisOfDay(0);
        date.setMonthOfYear(month);

        return date.toDateTime().dayOfMonth().withMaximumValue().millisOfDay().withMaximumValue();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_events, null);
    }
}
