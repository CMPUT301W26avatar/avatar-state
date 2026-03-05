package com.example.lotteryapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private MaterialCardView invitationCard;
    private ImageButton closeInvitation;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        invitationCard = view.findViewById(R.id.invitation_card);
        closeInvitation = view.findViewById(R.id.btn_close_invitation);

        closeInvitation.setOnClickListener(v -> invitationCard.setVisibility(View.GONE));

        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));

        // Dummy data
        List<Event> events = new ArrayList<>();
        events.add(new Event("Title", "Subtitle", "Description. Lorem ipsum dolor sit amet consectetur adipiscing elit, sed do", "TAG"));
        events.add(new Event("Title", "Subtitle", "Description. Lorem ipsum dolor sit amet consectetur adipiscing elit, sed do", "TAG"));
        events.add(new Event("Title", "Subtitle", "Description. Lorem ipsum dolor sit amet consectetur adipiscing elit, sed do", "TAG"));
        events.add(new Event("Title", "Subtitle", "Description. Lorem ipsum dolor sit amet consectetur adipiscing elit, sed do", "TAG"));

        recyclerView.setAdapter(new EventAdapter(events));

        return view;
    }

    // Simple Event model for the UI
    public static class Event {
        public String title, subtitle, description, tag;
        public Event(String title, String subtitle, String description, String tag) {
            this.title = title;
            this.subtitle = subtitle;
            this.description = description;
            this.tag = tag;
        }
    }
}