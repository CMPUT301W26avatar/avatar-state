package com.example.lotteryapp.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.lotteryapp.R;
import com.example.lotteryapp.activities.UserEventHistoryActivity;


public class JoinedFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.fragment_joined, container, false);

        View historyButton = view.findViewById(R.id.btn_history);
        historyButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), UserEventHistoryActivity.class);
            startActivity(intent);
        });

        return view;
    }
}