package com.example.lotteryapp;

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
import com.google.android.material.search.SearchBar;
import com.google.android.material.search.SearchView;
import java.util.ArrayList;
import java.util.List;

public class SearchFragment extends Fragment {

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
            recyclerViewPopular.setAdapter(new EventAdapter(getPopularEvents()));
        }

        if (recyclerViewSuggested != null) {
            recyclerViewSuggested.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerViewSuggested.setAdapter(new EventAdapter(getSuggestedEvents()));
        }

        return view;
    }

    private List<HomeFragment.Event> getPopularEvents() {
        List<HomeFragment.Event> events = new ArrayList<>();
        events.add(new HomeFragment.Event("Tech Expo", "Innovation Hub", "Explore the latest in technology.", "TECH"));
        events.add(new HomeFragment.Event("Sports Meet", "City Stadium", "Annual sports competition.", "SPORTS"));
        events.add(new HomeFragment.Event("Music Fest", "Downtown Plaza", "Live music performances.", "MUSIC"));
        events.add(new HomeFragment.Event("Food Carnival", "Central Park", "Taste cuisines from around the world.", "FOOD"));
        return events;
    }

    private List<HomeFragment.Event> getSuggestedEvents() {
        List<HomeFragment.Event> events = new ArrayList<>();
        events.add(new HomeFragment.Event("Art Workshop", "Creative Studio", "Learn painting and sketching.", "ART"));
        events.add(new HomeFragment.Event("Coding Bootcamp", "Online", "Intensive web development training.", "TECH"));
        return events;
    }
}