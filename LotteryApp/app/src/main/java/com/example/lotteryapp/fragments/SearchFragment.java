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
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventStorage;
import com.google.android.material.search.SearchBar;
import com.google.android.material.search.SearchView;

import java.util.ArrayList;
import java.util.List;

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

    private final EventStorage eventStorage = ServiceLocator.getEventStorage();
    private GridEventAdapter suggestedAdapter, popularAdapter;
    private List<HomeFragment.DisplayGridEvent> suggestedEvents, popularEvents;
    private boolean isSearchActive = false;

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

            loadPopularEvents(popularEvents, popularAdapter, 4);
        }

        if (recyclerViewSuggested != null) {
            recyclerViewSuggested.setLayoutManager(new LinearLayoutManager(getContext()));

            suggestedEvents = new ArrayList<>();
            suggestedAdapter = new GridEventAdapter(suggestedEvents);
            recyclerViewSuggested.setAdapter(suggestedAdapter);

            loadSuggestedEvents(suggestedEvents, suggestedAdapter, 4);
        }

        if (searchView != null) {
            searchView.getEditText().setOnEditorActionListener((v, actionId, event) -> {
                String query = searchView.getEditText().getText().toString();
                if (!query.isEmpty()) {
                    isSearchActive = true;
                    performSearch(query);
                    searchView.hide();
                }
                return false;
            });

            searchView.addTransitionListener((searchView1, previousState, newState) -> {
                if (newState == SearchView.TransitionState.HIDDEN && searchView1.getEditText().getText().length() == 0 && isSearchActive) {
                    resetDashboard();
                }
            });
        }

        if (searchBar != null) {
            searchBar.setNavigationOnClickListener(v -> {
                if (isSearchActive) {
                    resetDashboard();
                }
            });
        }

        return view;
    }

    /**
     * initializes fragment view state after creation and triggers a one time migration to backfill event keywords
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Temporary migration call to fix existing events
        eventStorage.syncAllEventKeywords(
                unused -> android.util.Log.d("Migration", "All events updated with keywords!"),
                e -> android.util.Log.e("Migration", "Failed to update events", e)
        );
    }

    /**
     * Executes the search query and updates the popular section with results.
     */
    private void performSearch(String query) {
        View view = getView();
        if (view != null) {
            View categories = view.findViewById(R.id.categories_section);
            View nearYouTitle = view.findViewById(R.id.near_you_title);
            View yourEventsHeader = view.findViewById(R.id.your_events_header);
            View yourEventsRecycler = view.findViewById(R.id.recycler_view_suggested);
            View upcomingHeader = view.findViewById(R.id.upcoming_header);
            View upcomingContent = view.findViewById(R.id.upcoming_content);

            if (categories != null) categories.setVisibility(View.GONE);
            if (nearYouTitle instanceof android.widget.TextView) {
                ((android.widget.TextView) nearYouTitle).setText("Search Results");
            }
            if (yourEventsHeader != null) yourEventsHeader.setVisibility(View.GONE);
            if (yourEventsRecycler != null) yourEventsRecycler.setVisibility(View.GONE);
            if (upcomingHeader != null) upcomingHeader.setVisibility(View.GONE);
            if (upcomingContent != null) upcomingContent.setVisibility(View.GONE);
        }

        eventStorage.searchEvents(query, fetchedEvents -> {
            popularEvents.clear();
            for (Event event : fetchedEvents) {
                popularEvents.add(eventToDisplayEvent(event));
            }
            popularAdapter.notifyDataSetChanged();
        }, error -> error.printStackTrace());
    }

    /**
     * Reset the dashboard to its original state by clearing search results.
     */
    private void resetDashboard() {
        isSearchActive = false;
        View view = getView();
        if (view != null) {
            View categories = view.findViewById(R.id.categories_section);
            View nearYouTitle = view.findViewById(R.id.near_you_title);
            View yourEventsHeader = view.findViewById(R.id.your_events_header);
            View yourEventsRecycler = view.findViewById(R.id.recycler_view_suggested);
            View upcomingHeader = view.findViewById(R.id.upcoming_header);
            View upcomingContent = view.findViewById(R.id.upcoming_content);

            if (categories != null) categories.setVisibility(View.VISIBLE);
            if (nearYouTitle instanceof android.widget.TextView) {
                ((android.widget.TextView) nearYouTitle).setText("Near You");
            }
            if (yourEventsHeader != null) yourEventsHeader.setVisibility(View.VISIBLE);
            if (yourEventsRecycler != null) yourEventsRecycler.setVisibility(View.VISIBLE);
            if (upcomingHeader != null) upcomingHeader.setVisibility(View.VISIBLE);
            if (upcomingContent != null) upcomingContent.setVisibility(View.VISIBLE);
        }
        loadPopularEvents(popularEvents, popularAdapter, 4);
        loadSuggestedEvents(suggestedEvents, suggestedAdapter, 4);
    }

    /**
     * Reloads event lists whenever the fragment comes into view again.
     *      ensures the dashboard reflects the most recent event(s)
     */
    @Override
    public void onResume() {
        super.onResume();
        if (!isSearchActive) {
            loadPopularEvents(popularEvents, popularAdapter, 4);
            loadSuggestedEvents(suggestedEvents, suggestedAdapter, 4);
        }
    }

    /**
     * only queries open events despite the naming convention -> for later implementation
     * Populate the grid event adapters with the most recent events with open registration window.
     *      (regStart < time.now < regEnd && waitlistCapacity > waitlistCount)
     *      call EventStorage.listEventsRegOpen query to fill events list
     *      convert Event to DisplayGridEvent
     *      notify change in the list of grid events
     */
    private void loadPopularEvents(List<HomeFragment.DisplayGridEvent> displayGridEvents, GridEventAdapter adapter, Integer limit) {
        eventStorage.getEventsByStatus(-1, -1, Event.EventStatus.REG_OPEN, limit, fetchedEvents -> { // replace listOpenEvents with listPopularEvents when implemented
            displayGridEvents.clear();
                for (Event event : fetchedEvents) {
                    displayGridEvents.add(eventToDisplayEvent(event));
                }
                adapter.notifyDataSetChanged();
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
        eventStorage.getEventsByStatus(-1, -1, Event.EventStatus.REG_OPEN, limit, fetchedEvents -> { // replace listOpenEvents with listPopularEvents when implemented
                displayGridEvents.clear();
                for (Event event : fetchedEvents) {
                    displayGridEvents.add(eventToDisplayEvent(event));
                }
                adapter.notifyDataSetChanged();
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
                event.getTag(),
                event.getPosterUrl()
        );
    }

    /**
     * Converts an Event parameter into a DisplayGridEvent
     */
    private String buildSubtitle(Event event) {
        Event.EventStatus status = event.getStatus();

        StringBuilder sb = new StringBuilder(statusString(status));

        Integer waitlistCap = event.getWaitlistCapacity();
        if (waitlistCap != null && waitlistCap == -1) {
            sb.append("\nWaitlist: ")
                    .append(event.getWaitlistCount())
                    .append("/")
                    .append("∞");

        } else if (waitlistCap != null && waitlistCap > 0){
            sb.append("\nWaitlist: ")
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
        if (status == null) return "Unknown";
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
        return "Unknown";
    }
}