package com.example.lotteryapp.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lotteryapp.R;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.services.storage.EventStorage;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * HomeFragment displays the main event dashboard of the application.
 * <p>
 * displays events in a grid (DisplayGridEvent), db query determines the type
 *      load Events from EventStorage,
 *          Event -turns-into> DisplayGridEvent,
 *          Events -into-> adapter
 *          adapter notifies of a change in the list
 *      GridEvents -into-> recyclerViews
 *      HomeFragment loads recyclerViews
 */
public class HomeFragment extends Fragment {

    private MaterialCardView invitationCard;
    private final EventStorage eventStorage = ServiceLocator.getEventStorage();
    private GridEventAdapter openAdapter;
    private GridEventAdapter upcomingAdapter;
    private GridEventAdapter fullAdapter;
    private List<HomeFragment.DisplayGridEvent> displayOpenGridEvents;
    private List<HomeFragment.DisplayGridEvent> displayUpcomingGridEvents;
    private List<HomeFragment.DisplayGridEvent> displayFullGridEvents;

    /**
     * Inflates the HomeFragment layout and initializes UI components.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        invitationCard = view.findViewById(R.id.invitation_card);
        MaterialButton closeInvitation = view.findViewById(R.id.btn_close_invitation);

        closeInvitation.setOnClickListener(v -> invitationCard.setVisibility(View.GONE));

        RecyclerView recyclerViewOpen = view.findViewById(R.id.recycler_view_open);
        recyclerViewOpen.setLayoutManager(new GridLayoutManager(getContext(), 2));
        RecyclerView recyclerViewUpcoming = view.findViewById(R.id.recycler_view_upcoming);
        recyclerViewUpcoming.setLayoutManager(new GridLayoutManager(getContext(), 2));
        RecyclerView recyclerViewFull = view.findViewById(R.id.recycler_view_full);
        recyclerViewFull.setLayoutManager(new GridLayoutManager(getContext(), 2));

        displayOpenGridEvents = new ArrayList<>();
        displayUpcomingGridEvents = new ArrayList<>();
        displayFullGridEvents = new ArrayList<>();

        openAdapter = new GridEventAdapter(displayOpenGridEvents);
        upcomingAdapter = new GridEventAdapter(displayUpcomingGridEvents);
        fullAdapter = new GridEventAdapter(displayFullGridEvents);

        recyclerViewOpen.setAdapter(openAdapter);
        recyclerViewUpcoming.setAdapter(upcomingAdapter);
        recyclerViewFull.setAdapter(fullAdapter);

        loadEventsRegOpen();
        loadEventsRegUpcoming();
        loadEventsRegFull();

        return view;
    }
    /**
     * Reloads event lists whenever the fragment comes into view again.
     *      ensures the dashboard reflects the most recent event(s)
     */
    @Override
    public void onResume() {
        super.onResume();
        loadEventsRegOpen();
        loadEventsRegUpcoming();
        loadEventsRegFull();
    }

    /**
     * Populate the grid event adapters with the most recent events with open registration window.
     *      (regStart < time.now < regEnd && waitlistCapacity > waitlistCount)
     *      call EventStorage.listEventsRegOpen query to fill events list
     *      convert Event to DisplayGridEvent
     *      notify change in the list of grid events
     */
    private void loadEventsRegOpen() {
        if (eventStorage == null || openAdapter == null) return;

        eventStorage.listEventsRegOpen(
                4,
                fetchedEvents -> {
                    displayOpenGridEvents.clear();
                    for (int i = 0; i < fetchedEvents.size(); i++) {
                        final Event event = fetchedEvents.get(i);
                        displayOpenGridEvents.add(eventToDisplayEvent(event));
                        openAdapter.notifyItemInserted(i);
                    }
                    for (Event e : fetchedEvents) {
                        displayOpenGridEvents.add(eventToDisplayEvent(e));
                    }
                },
                e -> {
                    android.util.Log.e("HomeFragment", "Failed to load open events", e);
                }
        );
    }

    /**
     * Populate the grid event adapters with the most recent events with upcoming registration window.
     *      (time.now < regStart)
     *      call EventStorage.listEventsRegOpen query to fill events list
     *      convert Event to DisplayGridEvent
     *      notify change in the list of grid events
     */
    private void loadEventsRegUpcoming() {
        if (eventStorage == null || upcomingAdapter == null) return;

        eventStorage.listEventsRegUpcoming(
                4,
                fetchedEvents -> {
                    displayUpcomingGridEvents.clear();
                    for (int i = 0; i < fetchedEvents.size(); i++) {
                        final Event event = fetchedEvents.get(i);
                        displayUpcomingGridEvents.add(eventToDisplayEvent(event));
                        upcomingAdapter.notifyItemInserted(i);
                    }
                },
                e -> {
                    android.util.Log.e("HomeFragment", "Failed to load upcoming events", e);
                }
        );
    }

    /**
     * Populate the grid event adapters with the most recent events that are full.
     *      (waitlistCapacity = waitlistCount)
     *      call EventStorage.listEventsRegOpen query to fill events list
     *      convert Event to DisplayGridEvent
     *      notify change in the list of grid events
     * <p>
     *      ** later: Popular Events should return events ordered by descending (waitlistCount/waitlistCapacity)**
     */
    private void loadEventsRegFull() {
        if (eventStorage == null || fullAdapter == null) return;

        eventStorage.listEventsRegFull(
                4,
                fetchedEvents -> {
                    displayFullGridEvents.clear();
                    for (int i = 0; i < fetchedEvents.size(); i++) {
                        final Event event = fetchedEvents.get(i);
                        displayFullGridEvents.add(eventToDisplayEvent(event));
                        fullAdapter.notifyItemInserted(i);
                    }
                },
                e -> {
                    android.util.Log.e("HomeFragment", "Failed to load full events", e);
                }
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
    public static class DisplayGridEvent {
        public String eventId, title, subtitle, description, tag;

        public DisplayGridEvent(String eventId, String title, String subtitle, String description, String tag) {
            this.eventId = eventId;
            this.title = title;
            this.subtitle = subtitle;
            this.description = description;
            this.tag = tag;
        }
    }

    /**
     * Converts an Event parameter into a DisplayGridEvent
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
     * Status-based string builder for DisplayEventGrid subtitles
     *      need Event param
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
     * Subtitle builder helper for status logic
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