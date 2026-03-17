package com.example.lotteryapp.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.lotteryapp.R;
import com.example.lotteryapp.activities.LoginActivity;
import com.example.lotteryapp.activities.AdminActivity;
import com.example.lotteryapp.activities.NotificationSettingsActivity;
import com.example.lotteryapp.activities.UserDetailsActivity;
import com.example.lotteryapp.activities.UserPrivacyActivity;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.AdminStorage;
/**
 * ProfileFragment displays user information and a settings tab menu.
 *      only settings tabs for halfway user stories have been set up for now
 *          Details -> UserDetailsActivity
 *          Notifications -> NotificationSettingsActivity
 *          and if admin,
 *              Admin Browse -> AdminActivity
 *      logout button sends the user to LoginActivity
 *      apply to be an administrator button sends a promotion request to current admin
 */

public class ProfileFragment extends Fragment {

    /**
     * Inflates the fragment layout and set up UI elements
     *      settings tabs
     *      buttons
     */
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

        setupListeners(view);
        return view;
    }

    /**
     * Setup on click listeners for settings tabs
     */
    private void setupListeners(View view) {

        // if is Admin, opens the Admin Browse tab
        checkAdminStatus(view);

        View savedEventsItem = view.findViewById(R.id.item_saved_events);
        if (savedEventsItem != null) {
            savedEventsItem.setOnClickListener(v -> {
                // TODO: Add savedEventsItem logic here
            });
        }

        View detailsItem = view.findViewById(R.id.item_details);
        if (detailsItem != null) {
            detailsItem.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), UserDetailsActivity.class);
                startActivity(intent);
            });
        }

        View devicesItem = view.findViewById(R.id.item_devices);
        if (devicesItem != null) {
            devicesItem.setOnClickListener(v -> {
                // TODO: Add devicesItem logic here
            });
        }

        View notificationsItem = view.findViewById(R.id.item_notifications);
        if (notificationsItem != null) {
            notificationsItem.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), NotificationSettingsActivity.class);
                startActivity(intent);
            });
        }

        View appearanceItem = view.findViewById(R.id.item_appearance);
        if (appearanceItem != null) {
            appearanceItem.setOnClickListener(v -> {
                // TODO: Add item_appearance logic here
            });
        }

        View languageItem = view.findViewById(R.id.item_language);
        if (languageItem != null) {
            languageItem.setOnClickListener(v -> {
                // TODO: Add item_language logic here
            });
        }

        View privacyItem = view.findViewById(R.id.item_privacy);
        if (privacyItem != null) {
            privacyItem.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), UserPrivacyActivity.class);
                startActivity(intent);
            });
        }

        View storageItem = view.findViewById(R.id.item_storage);
        if (storageItem != null) {
            storageItem.setOnClickListener(v -> {
                // TODO: Add item_storage logic here
            });
        }

        View btnLogout = view.findViewById(R.id.btn_logout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                ServiceLocator.getAuthService().userSignOut();
                Toast.makeText(getContext(), "Logging Out...", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(requireContext(), LoginActivity.class);
                startActivity(intent);
                if (getActivity() != null) {
                    getActivity().finish();
                }
            });
        }

        /*
                            admin logic
         */
        
        // pass current user uuid into AdminStorage.isAdmin()
        String uuid = ServiceLocator.uid();

        View btnApplyAdmin = view.findViewById(R.id.btn_apply_admin);
        AdminStorage astore = ServiceLocator.getAdminStorage();

        // call isAdmin to return bool inside of isAdminBool whether the current user is an admin
        astore.isAdmin(uuid, isAdminBool -> {
            if (isAdminBool) { // is admin, launch admin activity
                btnApplyAdmin.setVisibility(View.GONE);
            } else { // not admin, can apply or request
                btnApplyAdmin.setVisibility(View.VISIBLE);

                btnApplyAdmin.setOnClickListener(v -> {
                    astore.isThereAnAdmin(adminExists -> {
                        // no current admin
                        if (!adminExists) {
                            astore.setNewAdmin(uuid, unused -> {
                                Toast.makeText(requireContext(),
                                        "You are now the admin",
                                        Toast.LENGTH_LONG).show();
                                showAdminSection(view);
                            }, e -> {
                                Toast.makeText(requireContext(),
                                        "Failed to set admin status",
                                        Toast.LENGTH_LONG).show();
                                e.printStackTrace();
                            });
                        // request current admin to promote the user to admin status
                        } else {
                            astore.requestNewAdmin(uuid, unused -> {
                                Toast.makeText(requireContext(),
                                        "Requested to be an admin",
                                        Toast.LENGTH_LONG).show();
                                refreshProfileFragment();
                            }, e -> {
                                Toast.makeText(requireContext(),
                                        "Failed to request admin",
                                        Toast.LENGTH_LONG).show();
                                e.printStackTrace();
                            });
                        }
                    }, e -> {
                        // No admin doc exists → create one
                        astore.setNewAdmin(uuid, unused -> {
                            Toast.makeText(requireContext(),
                                    "You are now the admin",
                                    Toast.LENGTH_LONG).show();
                            showAdminSection(view);
                        }, err -> {
                            Toast.makeText(requireContext(),
                                    "Failed to set admin status",
                                    Toast.LENGTH_LONG).show();
                            err.printStackTrace();
                        });
                    });
                });
            }
        // something went wrong in the AdminStorage.isAdmin() call.
        }, e -> {
            Toast.makeText(requireContext(),
                    "Failed to check admin status",
                    Toast.LENGTH_LONG).show();
            e.printStackTrace();
        });
    }

    /**
     * reload the Fragment in case user is promoted to admin, or changes profile picture
     */
    private void refreshProfileFragment() {
        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new ProfileFragment())
                .commit();
    }

    /**
     * Asynchronous call to firebase via AdminStorage for whether or not the user is an admin
     */
    private void checkAdminStatus(View view) {
        String uid = ServiceLocator.uid();
        AdminStorage astore = ServiceLocator.getAdminStorage();
        astore.isAdmin(uid, isAdminBool -> {
            if (isAdminBool) {
                showAdminSection(view);
            }}, e -> e.printStackTrace());
    }

    /**
     * Set admin UI elements to visible
     *      set up settings tab for Admin Browse
     */
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

    /**
     * helper for initializing settings tab UI elements
     */
    private void setupSettingItem(View view, String title) {
        if (view != null) {
            TextView tv = view.findViewById(R.id.tv_settings_name);
            if (tv != null) {
                tv.setText(title);
            }
        }
    }
}