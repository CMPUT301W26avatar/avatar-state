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

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.search.SearchBar;
import com.google.android.material.search.SearchView;
import java.util.ArrayList;
import java.util.List;

public class SearchFragment extends Fragment {

    private EventStorage estore = ServiceLocator.eventStorage();
    private EventAdapter suggestedAdapter, popularAdapter;
    private List<HomeFragment.DisplayGridEvent> suggestedEvents, popularEvents;

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
            popularAdapter = new EventAdapter(popularEvents);
            recyclerViewPopular.setAdapter(popularAdapter);

            loadPopularEvents(popularEvents, popularAdapter, 4);
        }

        if (recyclerViewSuggested != null) {
            recyclerViewSuggested.setLayoutManager(new LinearLayoutManager(getContext()));

            suggestedEvents = new ArrayList<>();
            suggestedAdapter = new EventAdapter(suggestedEvents);
            recyclerViewSuggested.setAdapter(suggestedAdapter);

            loadSuggestedEvents(suggestedEvents, suggestedAdapter, 4);
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadPopularEvents(popularEvents, popularAdapter, 4);
        loadSuggestedEvents(suggestedEvents, suggestedAdapter, 4);
    }

    private void loadPopularEvents(List<HomeFragment.DisplayGridEvent> displayGridEvents, EventAdapter adapter, Integer limit) {
        estore.listOpenEvents(limit, fetchedEvents -> { // replace listOpenEvents with listPopularEvents when implemented
            displayGridEvents.clear();
                for (Event event : fetchedEvents) {
                    displayGridEvents.add(eventToDisplayEvent(event));
                }
                adapter.notifyDataSetChanged();
            },
            error -> error.printStackTrace()
        );
    }

    private void loadSuggestedEvents(List<HomeFragment.DisplayGridEvent> displayGridEvents, EventAdapter adapter, Integer limit) {
        estore.listOpenEvents(limit, fetchedEvents -> { // replace listOpenEvents with listSuggestedEvents when implemented
                displayGridEvents.clear();
                for (Event event : fetchedEvents) {
                    displayGridEvents.add(eventToDisplayEvent(event));
                }
                adapter.notifyDataSetChanged();
            },
                error -> error.printStackTrace()
        );
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