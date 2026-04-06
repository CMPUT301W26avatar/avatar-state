package com.example.lotteryapp.fragments;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.example.lotteryapp.dialogs.FilterDialog.FilterType.AVAILABILITY_FILTER;
import static com.example.lotteryapp.dialogs.FilterDialog.FilterType.FULL_EVENTS_FILTER;
import static com.example.lotteryapp.dialogs.FilterDialog.FilterType.OPEN_EVENTS_FILTER;
import static com.example.lotteryapp.dialogs.FilterDialog.FilterType.UPCOMING_EVENTS_FILTER;
import static com.example.lotteryapp.fragments.ManageFragment.UNLIMITED_WAITLIST_SENTINEL;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lotteryapp.R;
import com.example.lotteryapp.dialogs.FilterDialog;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventStorage;
import com.example.lotteryapp.services.storage.UserStorage;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.search.SearchBar;
import com.google.android.material.search.SearchView;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.Arrays;
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

    private static final int SECTION_LIMIT = 4;

    private final EventStorage eventStorage = ServiceLocator.getEventStorage();
    private final UserStorage userStorage = ServiceLocator.getUserStorage();

    private boolean isSearchActive = false;
    private boolean suppressNextSearchHideReset = false;
    private String currentSearchQuery = "";

    private MaterialButton filtersButton;
    private FilterDialog filterDialog;
    private List<Long> userAvailability;

    private SearchBar searchBar;
    private SearchView searchView;

    private MaterialTextView openHeader;
    private MaterialTextView upcomingHeader;
    private MaterialTextView fullHeader;

    private RecyclerView openRecycler;
    private RecyclerView upcomingRecycler;
    private RecyclerView fullRecycler;

    private GridEventAdapter openAdapter;
    private GridEventAdapter upcomingAdapter;
    private GridEventAdapter fullAdapter;

    private final List<HomeFragment.DisplayGridEvent> openEvents = new ArrayList<>();
    private final List<HomeFragment.DisplayGridEvent> upcomingEvents = new ArrayList<>();
    private final List<HomeFragment.DisplayGridEvent> fullEvents = new ArrayList<>();

    Runnable LOAD_EVENTS_ACTION = () -> {
        if (!filterDialog.isActive(OPEN_EVENTS_FILTER)) {
            loadOpenEvents(SECTION_LIMIT);
        } else {
            openEvents.clear();
            openAdapter.notifyDataSetChanged();
            if (openHeader != null) openHeader.setVisibility(GONE);
            if (openRecycler != null) openRecycler.setVisibility(GONE);
        }

        if (!filterDialog.isActive(UPCOMING_EVENTS_FILTER)) {
            loadUpcomingEvents(SECTION_LIMIT);
        } else {
            upcomingEvents.clear();
            upcomingAdapter.notifyDataSetChanged();
            if (upcomingHeader != null) upcomingHeader.setVisibility(GONE);
            if (upcomingRecycler != null) upcomingRecycler.setVisibility(GONE);
        }

        if (!filterDialog.isActive(FULL_EVENTS_FILTER)) {
            loadFullEvents(SECTION_LIMIT);
        } else {
            fullEvents.clear();
            fullAdapter.notifyDataSetChanged();
            if (fullHeader != null) fullHeader.setVisibility(GONE);
            if (fullRecycler != null) fullRecycler.setVisibility(GONE);
        }
    };

    /**
     * Inflates the HomeFragment layout and initializes UI components.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        searchBar = view.findViewById(R.id.search_bar);
        searchView = view.findViewById(R.id.search_view);
        filtersButton = view.findViewById(R.id.event_filters_button);

        openHeader = view.findViewById(R.id.open_header);
        upcomingHeader = view.findViewById(R.id.upcoming_header);
        fullHeader = view.findViewById(R.id.full_header);

        openRecycler = view.findViewById(R.id.open_recycler);
        upcomingRecycler = view.findViewById(R.id.upcoming_recycler);
        fullRecycler = view.findViewById(R.id.full_recycler);

        openRecycler.setLayoutManager(new GridLayoutManager(getContext(), 2));
        upcomingRecycler.setLayoutManager(new GridLayoutManager(getContext(), 2));
        fullRecycler.setLayoutManager(new GridLayoutManager(getContext(), 2));

        openAdapter = new GridEventAdapter(openEvents);
        upcomingAdapter = new GridEventAdapter(upcomingEvents);
        fullAdapter = new GridEventAdapter(fullEvents);

        openRecycler.setAdapter(openAdapter);
        upcomingRecycler.setAdapter(upcomingAdapter);
        fullRecycler.setAdapter(fullAdapter);

        filterDialog = new FilterDialog(view.getContext());
        userAvailability = Arrays.asList(1123L, 80L, 67L);

        if (searchView != null) {
            searchView.getEditText().setOnEditorActionListener((v, actionId, event) -> {
                String query = searchView.getEditText().getText().toString().trim();
                if (!query.isEmpty()) {
                    currentSearchQuery = query;
                    isSearchActive = true;
                    suppressNextSearchHideReset = true;
                    performSearch(query);
                    searchView.hide();
                }
                return false;
            });

            searchView.addTransitionListener((searchView1, previousState, newState) -> {
                if (newState == SearchView.TransitionState.HIDDEN) {
                    if (suppressNextSearchHideReset) {
                        suppressNextSearchHideReset = false;
                        return;
                    }

                    String text = searchView1.getEditText().getText().toString().trim();
                    if (text.isEmpty() && isSearchActive) {
                        resetDashboard();
                    }
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
        eventStorage.syncAllEventKeywords(
                unused -> android.util.Log.d("Migration", "All events updated with keywords!"),
                e -> android.util.Log.e("Migration", "Failed to update events", e)
        );
        setupFilterDialog(view);
        loadAllEvents();
    }

    /**
     * Executes the search query and updates the popular section with results.
     */
    private void performSearch(String query) {
        currentSearchQuery = query;

        if (openHeader != null) {
            openHeader.setText("Search Results");
            openHeader.setVisibility(VISIBLE);
        }
        if (openRecycler != null) openRecycler.setVisibility(VISIBLE);

        if (upcomingHeader != null) upcomingHeader.setVisibility(GONE);
        if (upcomingRecycler != null) upcomingRecycler.setVisibility(GONE);

        if (fullHeader != null) fullHeader.setVisibility(GONE);
        if (fullRecycler != null) fullRecycler.setVisibility(GONE);

        eventStorage.searchEvents(query, fetchedEvents -> {
            List<HomeFragment.DisplayGridEvent> searchResults = new ArrayList<>();

            for (Event event : fetchedEvents) {
                if (!passesSearchFilters(event)) {
                    continue;
                }
                searchResults.add(eventToDisplayEvent(event));
            }

            openEvents.clear();
            openEvents.addAll(searchResults);
            openAdapter.notifyDataSetChanged();

            if (openHeader != null) openHeader.setVisibility(VISIBLE);
            if (openRecycler != null) openRecycler.setVisibility(VISIBLE);
        }, error -> {
            Log.e("SearchFragment", "Failed to search events", error);

            openEvents.clear();
            openAdapter.notifyDataSetChanged();

            if (openHeader != null) openHeader.setVisibility(VISIBLE);
            if (openRecycler != null) openRecycler.setVisibility(VISIBLE);
        });
    }

    private boolean passesSearchFilters(Event event) {
        if (event == null) {
            return false;
        }

        Event.EventStatus status = event.getStatus();

        if (filterDialog.isActive(FULL_EVENTS_FILTER)
                && (status == Event.EventStatus.REG_FULL
                || status == Event.EventStatus.EVENT_OPEN
                || status == Event.EventStatus.EVENT_CLOSED
                || status == Event.EventStatus.EVENT_FULL)) {
            return false;
        }

        if (filterDialog.isActive(OPEN_EVENTS_FILTER)
                && status == Event.EventStatus.REG_OPEN) {
            return false;
        }

        if (filterDialog.isActive(UPCOMING_EVENTS_FILTER)
                && status == Event.EventStatus.REG_UPCOMING) {
            return false;
        }

        return true;
    }

    /**
     * Reset the dashboard to its original state by clearing search results.
     */
    private void resetDashboard() {
        isSearchActive = false;
        suppressNextSearchHideReset = false;
        currentSearchQuery = "";

        if (searchView != null) {
            searchView.getEditText().setText("");
        }

        if (openHeader != null) {
            openHeader.setText("Open");
            openHeader.setVisibility(VISIBLE);
        }
        if (openRecycler != null) openRecycler.setVisibility(VISIBLE);

        if (upcomingHeader != null) upcomingHeader.setVisibility(VISIBLE);
        if (upcomingRecycler != null) upcomingRecycler.setVisibility(VISIBLE);

        if (fullHeader != null) fullHeader.setVisibility(VISIBLE);
        if (fullRecycler != null) fullRecycler.setVisibility(VISIBLE);

        loadAllEvents();
    }

    /**
     * Reloads event lists whenever the fragment comes into view again.
     *      ensures the dashboard reflects the most recent event(s)
     */
    @Override
    public void onResume() {
        super.onResume();
        if (isSearchActive && !currentSearchQuery.isEmpty()) {
            performSearch(currentSearchQuery);
        } else {
            loadAllEvents();
        }
    }

    /**
     * Adds an onClickListener to "filtersButton" that
     * opens up the FilterDialog dialog.
     * @param filterView the view representing the FilterDialog.
     * @param filterDialog the FilterDialog obj.
     */
    private void setupFilterButton(@NonNull View filterView, @NonNull FilterDialog filterDialog) {
        filtersButton.setOnClickListener(v -> {
            filterDialog.show();

            MaterialButton applyButton = filterView.findViewById(R.id.filter_apply_button);
            applyButton.setOnClickListener(_NA -> {
                Log.d("SearchFragment", "User clicked on the filter apply button");
                boolean isValidInput = filterDialog.validateInput(filterView);
                if (!isValidInput) {
                    return;
                }

                if (isSearchActive && !currentSearchQuery.isEmpty()) {
                    performSearch(currentSearchQuery);
                } else {
                    loadAllEvents();
                }

                filterDialog.dismiss();
            });

            MaterialButton cancelButton = filterView.findViewById(R.id.filter_cancel_button);
            cancelButton.setOnClickListener(_NA -> {
                Log.d("SearchFragment", "User clicked on the filter cancel button");
                filterDialog.cancel();
            });
        });
    }

    /**
     *  Sets up the FilterDialog w/ Listeners and adds an onClickListener to "filtersButton"
     * @param view The parent view
     */
    private void setupFilterDialog(@NonNull View view) {
        Log.d("SearchFragment", "Hello Filters :)");

        View filterView = LayoutInflater.from(view.getContext()).inflate(R.layout.dialog_filter, null);
        filterDialog.setupListeners(filterView);
        filterDialog.setContentView(filterView);
        setupFilterButton(filterView, filterDialog);
    }

    /**
     * Helper function that loads events into their respective lists
     * <br>
     * Will automatically account for the FilterDialog availability filter
     */
    private void loadAllEvents() {
        if (filterDialog == null) return;

        if (filterDialog.isActive(AVAILABILITY_FILTER)) {
            userStorage.getUserAvailabilityDates(
                    ServiceLocator.uid(),
                    dates -> {
                        userAvailability = dates;
                        Log.d("SearchFragment", userAvailability.toString());
                        LOAD_EVENTS_ACTION.run();
                    },
                    e -> {
                        Log.e("SearchFragment", "Failed to load user availability", e);
                        Toast.makeText(
                                requireContext(),
                                "Failed to load user availability",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
            );
            return;
        }

        LOAD_EVENTS_ACTION.run();
    }

    /**
     * only queries open events despite the naming convention -> for later implementation
     * Populate the grid event adapters with the most recent events with open registration window.
     *      (regStart < time.now < regEnd && waitlistCapacity > waitlistCount)
     *      call EventStorage.listEventsRegOpen query to fill events list
     *      convert Event to DisplayGridEvent
     *      notify change in the list of grid events
     */
    private void loadOpenEvents(Integer limit) {
        loadFilteredEvents(openEvents, openAdapter, openHeader, openRecycler, Event.EventStatus.REG_OPEN, limit);
    }

    /**
     * only queries open events despite the naming convention -> for later implementation
     * Populate the grid event adapters with the most recent events with open registration window.
     *      (regStart < time.now < regEnd && waitlistCapacity > waitlistCount)
     *      call EventStorage.listEventsRegOpen query to fill events list
     *      convert Event to DisplayGridEvent
     *      notify change in the list of grid events
     */
    private void loadUpcomingEvents(Integer limit) {
        loadFilteredEvents(upcomingEvents, upcomingAdapter, upcomingHeader, upcomingRecycler, Event.EventStatus.REG_UPCOMING, limit);
    }

    private void loadFullEvents(Integer limit) {
        loadFilteredEvents(fullEvents, fullAdapter, fullHeader, fullRecycler, Event.EventStatus.REG_FULL, limit);
    }

    /**
     * loads the events tagged with the given status
     * <br>
     * will automatically filter out events if the user
     * turns on the availability filter in the filter dialog
     */
    private void loadFilteredEvents(
            List<HomeFragment.DisplayGridEvent> targetList,
            GridEventAdapter adapter,
            MaterialTextView header,
            RecyclerView recycler,
            Event.EventStatus status,
            Integer limit
    ) {
        if (eventStorage == null || userStorage == null || adapter == null || targetList == null) return;

        Pair<Integer, Integer> capFilterVals = filterDialog.getCapacityFilterValues();
        int minCap = capFilterVals.first;
        int maxCap = capFilterVals.second;

        @SuppressLint("NotifyDataSetChanged")
        OnSuccessListener<List<Event>> successListener = fetchedEvents -> {
            targetList.clear();
            for (Event event : fetchedEvents) {
                targetList.add(eventToDisplayEvent(event));
            }
            adapter.notifyDataSetChanged();

            boolean hasEvents = !targetList.isEmpty();
            if (header != null) header.setVisibility(hasEvents ? VISIBLE : GONE);
            if (recycler != null) recycler.setVisibility(hasEvents ? VISIBLE : GONE);
        };

        OnFailureListener failureListener = e -> {
            Log.e("SearchFragment", "Failed to load events for status " + status, e);
            targetList.clear();
            adapter.notifyDataSetChanged();

            if (header != null) header.setVisibility(GONE);
            if (recycler != null) recycler.setVisibility(GONE);
        };

        if (filterDialog.isActive(AVAILABILITY_FILTER)) {
            if (userAvailability == null || userAvailability.isEmpty()) {
                userAvailability = Arrays.asList(67L, 301L);
            }
            eventStorage.getEventsByStatus(
                    minCap,
                    maxCap,
                    userAvailability,
                    status,
                    limit,
                    successListener,
                    failureListener
            );
        } else {
            eventStorage.getEventsByStatus(
                    minCap,
                    maxCap,
                    status,
                    limit,
                    successListener,
                    failureListener
            );
        }
    }

    private boolean passesCapacityFilter(Event event, int minCap, int maxCap) {
        Integer cap = event.getWaitlistCapacity();
        if (cap == null) {
            return false;
        }

        if (cap == UNLIMITED_WAITLIST_SENTINEL) {
            return maxCap == UNLIMITED_WAITLIST_SENTINEL;
        }

        return cap >= minCap && cap <= maxCap;
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
        if (waitlistCap != null && waitlistCap == UNLIMITED_WAITLIST_SENTINEL) {
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