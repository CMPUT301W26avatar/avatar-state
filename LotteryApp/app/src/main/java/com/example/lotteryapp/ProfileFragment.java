package com.example.lotteryapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        
        setupSettingItem(view.findViewById(R.id.item_saved_events), "Saved Events");
        setupSettingItem(view.findViewById(R.id.item_details), "Details");
        setupSettingItem(view.findViewById(R.id.item_devices), "Devices");
        setupSettingItem(view.findViewById(R.id.item_notifications), "Notifications");
        setupSettingItem(view.findViewById(R.id.item_appearance), "Appearance");
        setupSettingItem(view.findViewById(R.id.item_language), "Language");
        setupSettingItem(view.findViewById(R.id.item_privacy), "Privacy & Security");
        setupSettingItem(view.findViewById(R.id.item_storage), "Storage");

        View detailsItem = view.findViewById(R.id.item_details);
        if (detailsItem != null) {
            detailsItem.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), UserDetailsActivity.class);
                startActivity(intent);
            });
        }

        return view;
    }

    private void setupSettingItem(View view, String title) {
        if (view != null) {
            TextView tv = view.findViewById(R.id.tv_settings_name);
            if (tv != null) {
                tv.setText(title);
            }
        }
    }
}