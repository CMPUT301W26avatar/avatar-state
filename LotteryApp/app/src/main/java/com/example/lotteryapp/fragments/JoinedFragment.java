package com.example.lotteryapp.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lotteryapp.R;
import com.example.lotteryapp.services.ServiceLocator;

import java.util.ArrayList;

/**
 * Fragment that displays the events the user has joined (enrolled in).
 */
public class JoinedFragment extends Fragment {

    private RecyclerView recyclerView;
    private View emptyView;
    private JoinedEventsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_joined, container, false);

        recyclerView = view.findViewById(R.id.rv_joined_events);
        emptyView = view.findViewById(R.id.tv_empty_joined);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        loadJoinedEvents();

        return view;
    }

    private void loadJoinedEvents() {
        String userId = ServiceLocator.uid();
        if (userId == null) return;

        ServiceLocator.getEventPoolStorage().getEnrolledEvents(userId, events -> {
            if (isAdded()) {
                if (events == null || events.isEmpty()) {
                    emptyView.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyView.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
                adapter = new JoinedEventsAdapter(events != null ? events : new ArrayList<>(), userId);
                adapter.setOnOptOutListener(() -> {
                    emptyView.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                });
                recyclerView.setAdapter(adapter);
            }
        }, e -> {
            if (isAdded()) {
                Toast.makeText(getContext(), "Error loading events: " + e.getMessage(), Toast.LENGTH_LONG).show();
                android.util.Log.e("JoinedFragment", "Firestore Error", e);
            }
        });
    }
}