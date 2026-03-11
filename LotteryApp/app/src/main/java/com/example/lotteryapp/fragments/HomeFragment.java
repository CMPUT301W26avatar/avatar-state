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

public class HomeFragment extends Fragment {

    private MaterialCardView invitationCard;
    private MaterialButton closeInvitation;
    private EventStorage estore = ServiceLocator.getEventStorage();
    private GridEventAdapter openAdapter;
    private GridEventAdapter closedAdapter;
    private List<HomeFragment.DisplayGridEvent> displayOpenGridEvents;
    private List<HomeFragment.DisplayGridEvent> displayClosedGridEvents;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        invitationCard = view.findViewById(R.id.invitation_card);
        closeInvitation = view.findViewById(R.id.btn_close_invitation);

        closeInvitation.setOnClickListener(v -> invitationCard.setVisibility(View.GONE));

        RecyclerView recyclerViewOpen = view.findViewById(R.id.recycler_view_open);
        recyclerViewOpen.setLayoutManager(new GridLayoutManager(getContext(), 2));
        RecyclerView recyclerViewClosed = view.findViewById(R.id.recycler_view_closed);
        recyclerViewClosed.setLayoutManager(new GridLayoutManager(getContext(), 2));

        displayOpenGridEvents = new ArrayList<>();
        displayClosedGridEvents = new ArrayList<>();

        openAdapter = new GridEventAdapter(displayOpenGridEvents);
        closedAdapter = new GridEventAdapter(displayClosedGridEvents);

        recyclerViewOpen.setAdapter(openAdapter);
        recyclerViewClosed.setAdapter(closedAdapter);

        loadOpenEvents();
        loadClosedEvents();

        return view;
    }
    @Override
    public void onResume() {
        super.onResume();
        loadOpenEvents();
        loadClosedEvents();
    }

    private void loadOpenEvents() {
        if (estore == null || openAdapter == null) return;

        estore.listOpenEvents(
                4,
                fetchedEvents -> {
                    displayOpenGridEvents.clear();
                    for (Event e : fetchedEvents) {
                        displayOpenGridEvents.add(eventToDisplayEvent(e));
                    }
                    openAdapter.notifyDataSetChanged();
                },
                e -> {
                    android.util.Log.e("HomeFragment", "Failed to load open events", e);
                }
        );
    }

    private void loadClosedEvents() {
        if (estore == null || closedAdapter == null) return;

        estore.listClosedEvents(
                4,
                fetchedEvents -> {
                    displayClosedGridEvents.clear();
                    for (Event e : fetchedEvents) {
                        displayClosedGridEvents.add(eventToDisplayEvent(e));
                    }
                    closedAdapter.notifyDataSetChanged();
                },
                e -> {
                    android.util.Log.e("HomeFragment", "Failed to load closed events", e);
                }
        );
    }

    // Simple Event model for the UI
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

    private HomeFragment.DisplayGridEvent eventToDisplayEvent(Event event) {
        return new HomeFragment.DisplayGridEvent(
                event.getEventId(),
                event.getTitle(),
                buildSubtitle(event),
                event.getDescription(),
                event.getTag()
        );
    }

    private String buildSubtitle(Event event) {
        int enrolledCount = event.getEnrolledCount();
        int waitlistCount = event.getWaitlistCount();
        int eventCapacity = event.getEventCapacity();
        int waitlistCapacity = event.getWaitlistCapacity();

        if (enrolledCount < eventCapacity) {
            return "OPEN | Enrolled: " + enrolledCount + "/" + eventCapacity;
        } else {
            return "CLOSED | Waitlist: " + waitlistCount + "/" + waitlistCapacity;
        }
    }
}