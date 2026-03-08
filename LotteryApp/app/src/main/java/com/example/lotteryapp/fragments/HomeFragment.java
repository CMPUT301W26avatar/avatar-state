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
    private GridEventAdapter adapter;
    private List<HomeFragment.DisplayGridEvent> displayGridEvents;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        invitationCard = view.findViewById(R.id.invitation_card);
        closeInvitation = view.findViewById(R.id.btn_close_invitation);

        closeInvitation.setOnClickListener(v -> invitationCard.setVisibility(View.GONE));

        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));

        displayGridEvents = new ArrayList<>();
        adapter = new GridEventAdapter(displayGridEvents);
        recyclerView.setAdapter(adapter);

        loadEvents();

        return view;
    }
    @Override
    public void onResume() {
        super.onResume();
        loadEvents();
    }

    private void loadEvents() {
        if (estore == null || adapter == null) return;

        estore.listOpenEvents(
                4,
                fetchedEvents -> {
                    displayGridEvents.clear();
                    for (Event e : fetchedEvents) {
                        displayGridEvents.add(eventToDisplayEvent(e));
                    }
                    adapter.notifyDataSetChanged();
                },
                Throwable::printStackTrace
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