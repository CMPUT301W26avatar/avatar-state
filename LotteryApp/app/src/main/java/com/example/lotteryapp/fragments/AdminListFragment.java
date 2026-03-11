package com.example.lotteryapp.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lotteryapp.R;
import com.example.lotteryapp.activities.EventDetailsActivity;
import com.example.lotteryapp.activities.MainActivity;
import com.example.lotteryapp.activities.UserDetailsActivity;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.models.User;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.AdminStorage;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AdminListFragment extends Fragment {

    public static final String TYPE_EVENTS = "events";
    public static final String TYPE_PROFILES = "profiles";
    public static final String TYPE_IMAGES = "images";
    private static final String ARG_TYPE = "type";

    public static final String TYPE_REQUESTED_ADMINS = "requested_admins";

    private String type;
    private RecyclerView recyclerView;
    private View progressBar;
    private View tvEmpty;
    private AdminAdapter adapter;

    private List<Object> allItems = new ArrayList<>();
    private String filterText = "";
    private boolean sortAscending = true;

    public static AdminListFragment newInstance(String type) {
        AdminListFragment fragment = new AdminListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            type = getArguments().getString(ARG_TYPE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_list, container, false);
        recyclerView = view.findViewById(R.id.recycler_view);
        progressBar = view.findViewById(R.id.progress_bar);
        tvEmpty = view.findViewById(R.id.tv_empty);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AdminAdapter();
        recyclerView.setAdapter(adapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    public void setFilter(String filter) {
        this.filterText = filter.toLowerCase();
        applyFilterAndSort();
    }

    public void setSortAscending(boolean ascending) {
        this.sortAscending = ascending;
        applyFilterAndSort();
    }

    private void loadData() {
        // TODO: Implement loading all user profiles from Firestore "users" collection
        if (TYPE_PROFILES.equals(type)) {
            progressBar.setVisibility(View.VISIBLE);
            ServiceLocator.getUserStorage().getAllUsers(users -> {
                allItems.clear();
                allItems.addAll(users);
                applyFilterAndSort();
            }, e -> progressBar.setVisibility(View.GONE));
            return;
        }

        // TODO: Implement loading all images from Firestore "images" collection
        // note: image collection does not exist, still haven't figured out the best way to do images
        if (TYPE_IMAGES.equals(type)) {
            progressBar.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            adapter.setItems(new ArrayList<>());
            return;
        }

        // Load events from Firestore
        if (TYPE_EVENTS.equals(type)) {
            progressBar.setVisibility(View.VISIBLE);
            ServiceLocator.getEventStorage().getAllEvents(events -> {
                allItems.clear();
                allItems.addAll(events);
                applyFilterAndSort();
            }, e -> progressBar.setVisibility(View.GONE));
        }

        if (TYPE_REQUESTED_ADMINS.equals(type)) {
            progressBar.setVisibility(View.VISIBLE);
            ServiceLocator.getAdminStorage().getRequestedAdmins(requestIds -> {
                ServiceLocator.getUserStorage().getAllUsers(users -> {
                    allItems.clear();
                    for (User user : users) {
                        if (requestIds.contains(user.getUUID())) {
                            allItems.add(user);
                        }
                    }
                    applyFilterAndSort();
                }, e -> progressBar.setVisibility(View.GONE));
            }, e -> progressBar.setVisibility(View.GONE));
            return;
        }
    }

    /**
     * Apply the current filter and sort to the list of items and update the RecyclerView
     */
    private void applyFilterAndSort() {
        List<Object> filtered = allItems.stream()
                .filter(item -> {
                    if (filterText.isEmpty()) return true;
                    String name = getItemDisplayName(item).toLowerCase();
                    return name.contains(filterText);
                })
                .sorted((o1, o2) -> {
                    String n1 = getItemDisplayName(o1);
                    String n2 = getItemDisplayName(o2);
                    return sortAscending ? n1.compareToIgnoreCase(n2) : n2.compareToIgnoreCase(n1);
                })
                .collect(Collectors.toList());

        progressBar.setVisibility(View.GONE);
        adapter.setItems(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private String getItemDisplayName(Object item) {
        if (item instanceof Event) {
            String title = ((Event) item).getTitle();
            return (title != null && !title.isEmpty()) ? title : "Untitled Event (" + ((Event) item).getEventId() + ")";
        }
        else if (item instanceof User) {
            String name = ((User) item).getName();
            return (name != null && !name.isEmpty()) ? name : "Anonymous User (" + ((User) item).getUUID() + ")";
        }
        return "Unknown Item";
    }

    /**
     * Adapter for the RecyclerView in the fragment
     */
    private class AdminAdapter extends RecyclerView.Adapter<AdminAdapter.ViewHolder> {
        private List<Object> displayItems = new ArrayList<>();

        public void setItems(List<Object> items) {
            this.displayItems = items;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_settings, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Object item = displayItems.get(position);
            holder.title.setText(getItemDisplayName(item));
            holder.itemView.setOnClickListener(v -> {
                if (TYPE_REQUESTED_ADMINS.equals(type) && item instanceof User) {
                    showPromoteNewAdminDialog((User) item);
                } else {
                openDetails(item);
                }
            });
        }

        private void openDetails(Object item) {
            if (item instanceof Event) {
                Intent intent = new Intent(getContext(), EventDetailsActivity.class);
                intent.putExtra(EventDetailsActivity.EXTRA_EVENT_ID, ((Event) item).getEventId());
                intent.putExtra("isAdminMode", true);
                startActivity(intent);
            }
            else if (item instanceof User) {
                // Implement opening UserDetailsActivity in Admin Mode when User object is clicked
                Intent intent = new Intent(getContext(), UserDetailsActivity.class);
                intent.putExtra(UserDetailsActivity.EXTRA_USER_ID, ((User) item).getUUID());
                intent.putExtra(UserDetailsActivity.EXTRA_ADMIN_MODE, true);
                startActivity(intent);
            }
        }

        private void showPromoteNewAdminDialog(User user) {
            View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_promote_admin, null);

            ImageButton btnClose = dialogView.findViewById(R.id.btn_close_dialog);
            TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
            TextView tvMessage = dialogView.findViewById(R.id.tv_dialog_message);
            MaterialButton btnCancelRequest = dialogView.findViewById(R.id.btn_cancel_request);
            MaterialButton btnAcceptRequest = dialogView.findViewById(R.id.btn_accept_request);
            AdminStorage astore = ServiceLocator.getAdminStorage();

            tvTitle.setText("Admin Request");
            tvMessage.setText("What would you like to do with " + user.getName() + "'s admin request?");

            AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(dialogView).create();

            if (btnClose != null) {
                btnClose.setOnClickListener(v -> dialog.dismiss());
            }
            if (btnCancelRequest != null) {
                btnCancelRequest.setOnClickListener(v -> {
                    astore.removeRequestedAdmin(user.getUUID(), unused -> {
                        Toast.makeText(requireContext(), user.getName() + "'s request was cancelled", Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                        loadData();
                        }, e -> {
                            Toast.makeText(requireContext(), "Failed to cancel request", Toast.LENGTH_LONG).show();
                            e.printStackTrace();
                        }
                    );
                });
            }
            if (btnAcceptRequest != null) {
                btnAcceptRequest.setOnClickListener(v -> {
                    astore.promoteToAdmin(user.getUUID(), unused -> {
                        Toast.makeText(requireContext(), user.getName() + " is now an admin", Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                        loadData();

                    }, e -> {
                        Toast.makeText(requireContext(), "Failed to promote new admin", Toast.LENGTH_LONG).show();
                        e.printStackTrace();
                    });
                });
            }
            dialog.show();
        }

        @Override
        public int getItemCount() {
            return displayItems.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            ViewHolder(View view) {
                super(view);
                title = view.findViewById(R.id.tv_settings_name);
            }
        }
    }
}
