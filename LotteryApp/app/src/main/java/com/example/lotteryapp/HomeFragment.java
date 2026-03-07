package com.example.lotteryapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private MaterialCardView invitationCard;
    private MaterialButton closeInvitation;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        invitationCard = view.findViewById(R.id.invitation_card);
        closeInvitation = view.findViewById(R.id.btn_close_invitation);

        closeInvitation.setOnClickListener(v -> invitationCard.setVisibility(View.GONE));

        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));

        List<HomeFragment.DisplayGridEvent> displayGridEvents = new ArrayList<>();
        EventAdapter adapter = new EventAdapter(displayGridEvents);
        recyclerView.setAdapter(adapter);

        EventStorage estore = ServiceLocator.eventStorage();

        estore.listOpenEvents(
                4,
                fetchedEvents -> {
                    displayGridEvents.clear();

                    for (Event e : fetchedEvents) {
                        displayGridEvents.add(EventToDisplayEvent(e));
                    }

                    adapter.notifyDataSetChanged();
                },
                error -> {
                    error.printStackTrace();
                }
        );

        return view;
    }

    // Simple Event model for the UI
    public static class DisplayGridEvent {
        public String title, subtitle, description, tag;
        public DisplayGridEvent(String title, String subtitle, String description, String tag) {
            this.title = title;
            this.subtitle = subtitle;
            this.description = description;
            this.tag = tag;
        }
    }

    private DisplayGridEvent EventToDisplayEvent(Event event) {
        DisplayGridEvent dpge =  new DisplayGridEvent(
                event.getTitle(),
                buildSubtitle(event),
                event.getDescription(),
                event.getTag()
        );
        return dpge;

    }
    private String buildSubtitle(com.example.lotteryapp.Event event) {
        String status = event.getStatus().toString();
        StringBuilder sb = new StringBuilder(status);
        if (status.equals("OPEN")) {
            sb.append(" | Capacity: ").append(event.getEventCapacity());
        } else if (status.equals("CLOSED")) {
            int waitlistCap = event.getWaitlistCapacity();
            if (waitlistCap > 0) {
                sb.append(" | Waitlist: ").append(event.getWaitlistCapacity());
            }
        }
        return sb.toString();
    }
}