package com.example.lotteryapp.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lotteryapp.R;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.services.storage.EventStorage;
import com.google.android.material.search.SearchBar;
import com.google.android.material.search.SearchView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * SeachFragment displays the search dashboard of the application.
 *      has a search bar in the
 *
 * has sections of grid events
 * - displays events in a grid (DisplayGridEvent), db query determines the type
 *      - load Events from EventStorage,
 *          - Event -turns-into> DisplayGridEvent,
 *          - Events -into-> adapter
 *          - adapter notifies of a change in the list
 *      - GridEvents -into-> recyclerViews
 *      - HomeFragment loads recyclerViews
 *
 *  *sections currently just use basic open event queries despite what the UI says*
 *
 *  add more buttons in the top right corner of grid event sections show all events
 *      by that particular query/filter (to implement)
 */

public class SearchFragment extends Fragment {

    private EventStorage estore = ServiceLocator.getEventStorage();
    private GridEventAdapter suggestedAdapter, popularAdapter;
    private List<HomeFragment.DisplayGridEvent> suggestedEvents, popularEvents;

    /**
     * Inflates the HomeFragment layout and initializes UI components.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        SearchBar searchBar = view.findViewById(R.id.search_bar);
        SearchView searchView = view.findViewById(R.id.search_view);

        RecyclerView recyclerViewPopular = view.findViewById(R.id.recycler_view_popular);
        RecyclerView recyclerViewSuggested = view.findViewById(R.id.recycler_view_suggested);

        if (recyclerViewPopular != null) {
            recyclerViewPopular.setLayoutManager(new GridLayoutManager(getContext(), 2));

            popularEvents = new ArrayList<>();
            popularAdapter = new GridEventAdapter(popularEvents);
            recyclerViewPopular.setAdapter(popularAdapter);

            loadPopularEvents(popularEvents, popularAdapter);
        }

        if (recyclerViewSuggested != null) {
            recyclerViewSuggested.setLayoutManager(new LinearLayoutManager(getContext()));

            suggestedEvents = new ArrayList<>();
            suggestedAdapter = new GridEventAdapter(suggestedEvents);
            recyclerViewSuggested.setAdapter(suggestedAdapter);

            loadSuggestedEvents(suggestedEvents, suggestedAdapter, 4);
        }

        return view;
    }

    /**
     * Reloads event lists whenever the fragment comes into view again.
     *      ensures the dashboard reflects the most recent event(s)
     */
    @Override
    public void onResume() {
        super.onResume();
        loadPopularEvents(popularEvents, popularAdapter);
        loadSuggestedEvents(suggestedEvents, suggestedAdapter, 4);
    }

    /**
     * only queries open events despite the naming convention -> for later implementation
     * Populate the grid event adapters with the most recent events with open registration window.
     *      (regStart < time.now < regEnd && waitlistCapacity > waitlistCount)
     *      call EventStorage.listEventsRegOpen query to fill events list
     *      convert Event to DisplayGridEvent
     *      notify change in the list of grid events
     */
    private void loadPopularEvents(List<HomeFragment.DisplayGridEvent> displayGridEvents, GridEventAdapter adapter) {
        estore.listEventsRegOpen(4, fetchedEvents -> { // replace listOpenEvents with listPopularEvents when implemented
                displayGridEvents.clear();
                for (int i = 0; i < fetchedEvents.size(); i++) {
                    final Event event = fetchedEvents.get(i);
                    displayGridEvents.add(eventToDisplayEvent(event));
                    adapter.notifyItemInserted(i);
                }
            },
            error -> error.printStackTrace()
        );
    }

    /**
     * only queries open events despite the naming convention -> for later implementation
     * Populate the grid event adapters with the most recent events with open registration window.
     *      (regStart < time.now < regEnd && waitlistCapacity > waitlistCount)
     *      call EventStorage.listEventsRegOpen query to fill events list
     *      convert Event to DisplayGridEvent
     *      notify change in the list of grid events
     */
    private void loadSuggestedEvents(List<HomeFragment.DisplayGridEvent> displayGridEvents, GridEventAdapter adapter, Integer limit) {
        estore.listEventsRegOpen(limit, fetchedEvents -> { // replace listOpenEvents with listSuggestedEvents when implemented
                displayGridEvents.clear();
                for (int i = 0; i < fetchedEvents.size(); i++) {
                    final Event event = fetchedEvents.get(i);
                    displayGridEvents.add(eventToDisplayEvent(event));
                    adapter.notifyItemInserted(i);
                }
            },
                error -> error.printStackTrace()
        );
    }

    /**
     * Simple event model for the UI
     *      Displays the
     *          title,
     *          description,
     *          tag (to implement),
     *          and a subtitle (EventStatus based String)
     *       for an event
     */
    private HomeFragment.DisplayGridEvent eventToDisplayEvent(Event event) {
        return new HomeFragment.DisplayGridEvent(
                event.getEventId(),
                event.getTitle(),
                buildSubtitle(event),
                event.getDescription(),
                event.getTag()
        );
    }

    /**
     * Converts an Event parameter into a DisplayGridEvent
     */
    private String buildSubtitle(Event event) {
        StringBuilder sb;

        String eventStatus = statusString(event.getStatus());
        sb = new StringBuilder(Objects.requireNonNullElse(eventStatus, "Unknown Status"));

        Integer waitlistCap = event.getWaitlistCapacity();
        if (waitlistCap != null) {
            sb.append(" | Waitlist: ")
                    .append(event.getWaitlistCount())
                    .append("/")
                    .append(waitlistCap);
        }

        return sb.toString();
    }

    /**
     * Status-based string builder for DisplayEventGrid subtitles
     *      need Event param
     */
    private String statusString(Event.EventStatus status) {
        switch (status) {
            case REG_OPEN:
                return "Open for registration";
            case REG_CLOSED:
                return "Closed for registration";
            case REG_FULL:
                return "Waitlist full";
            case REG_UPCOMING:
                return "Register soon";
            case EVENT_CLOSED:
                return "Event date passed";
            case EVENT_OPEN:
                return "Invitations Sent";
            case EVENT_FULL:
                return "Event is full";
        }
        return null;
    }
}