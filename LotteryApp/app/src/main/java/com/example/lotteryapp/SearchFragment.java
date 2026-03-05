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

public class SearchFragment extends Fragment {

    private MaterialCardView invitationCard;
    private ImageButton closeInvitation;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        invitationCard = view.findViewById(R.id.invitation_card);
        closeInvitation = view.findViewById(R.id.btn_close_invitation);

        if (invitationCard != null && closeInvitation != null) {
            closeInvitation.setOnClickListener(v -> invitationCard.setVisibility(View.GONE));
        }

        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));

            // Dummy data for the search/explore view
            List<HomeFragment.Event> events = new ArrayList<>();
            events.add(new HomeFragment.Event("Tech Expo", "Innovation Hub", "Explore the latest in technology and gadgets.", "TECH"));
            events.add(new HomeFragment.Event("Sports Meet", "City Stadium", "Annual sports competition for all ages.", "SPORTS"));
            events.add(new HomeFragment.Event("Music Fest", "Downtown Plaza", "Live music performances by local artists.", "MUSIC"));
            events.add(new HomeFragment.Event("Food Carnival", "Central Park", "Taste cuisines from around the world.", "FOOD"));

            recyclerView.setAdapter(new EventAdapter(events));
        }

        return view;
    }
}