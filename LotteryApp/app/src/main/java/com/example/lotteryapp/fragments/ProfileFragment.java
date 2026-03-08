package com.example.lotteryapp.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.lotteryapp.R;
import com.example.lotteryapp.activities.AdminActivity;
import com.example.lotteryapp.activities.UserDetailsActivity;
import com.example.lotteryapp.models.User;
import com.example.lotteryapp.services.ServiceLocator;

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

        checkAdminStatus(view);

        return view;
    }

    private void checkAdminStatus(View view) {
        String uid = ServiceLocator.uid();
        if (uid != null) {
            ServiceLocator.userStorage().getUserProfile(uid, user -> {
                if (user != null && user.isAdmin()) {
                    showAdminSection(view);
                }
            }, e -> {
                // TODO: Handle error
            });
        }
    }

    private void showAdminSection(View view) {
        View adminLabel = view.findViewById(R.id.tv_admin_label);
        View adminCard = view.findViewById(R.id.card_admin);
        View adminBrowse = view.findViewById(R.id.item_admin_browse);

        if (adminLabel != null) {
            adminLabel.setVisibility(View.VISIBLE);
        }
        if (adminCard != null) {
            adminCard.setVisibility(View.VISIBLE);
        }
        
        setupSettingItem(adminBrowse, "Admin Browse");
        if (adminBrowse != null) {
            adminBrowse.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), AdminActivity.class);
                startActivity(intent);
            });
        }
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