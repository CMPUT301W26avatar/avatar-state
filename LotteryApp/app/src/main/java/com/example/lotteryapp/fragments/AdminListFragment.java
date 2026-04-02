package com.example.lotteryapp.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
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
import com.example.lotteryapp.activities.UserDetailsActivity;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.models.NotificationLog;
import com.example.lotteryapp.models.User;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.AdminStorage;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
/**
 * fragment for displaying admin lists inside of AdminActivity
 *      shows events profiles, images or requested admins
 */
public class AdminListFragment extends Fragment {

    public static final String TYPE_EVENTS = "events";
    public static final String TYPE_PROFILES = "profiles";
    public static final String TYPE_IMAGES = "images";
    private static final String ARG_TYPE = "type";

    public static final String TYPE_REQUESTED_ADMINS = "requested_admins";
    public static final String TYPE_NOTIFICATION_LOGS = "notification_logs";

    private String type;
    private RecyclerView recyclerView;
    private View progressBar;
    private View tvEmpty;
    private AdminAdapter adapter;

    private List<Object> allItems = new ArrayList<>();
    private String filterText = "";
    private boolean sortAscending = true;

    /**
     * creates and returns fragment for the given admin list type
     *      needs String type for selecting which admin list to show
     */
    public static AdminListFragment newInstance(String type) {
        AdminListFragment fragment = new AdminListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * initializes fragment state from arguments
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            type = getArguments().getString(ARG_TYPE);
        }
    }

    /**
     * inflates the fragment layout and sets up the recycler view
     */
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

    /**
     * reloads fragment data when the fragment becomes active
     */
    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    /**
     * sets the current filter text
     */
    public void setFilter(String filter) {
        this.filterText = filter.toLowerCase();
        applyFilterAndSort();
    }

    /**
     * sets the current sort order
     */
    public void setSortAscending(boolean ascending) {
        this.sortAscending = ascending;
        applyFilterAndSort();
    }

    /**
     * loads data for the current fragment type
     *      calls EventStorage.getAllUsers() for users
     */
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

        if (TYPE_NOTIFICATION_LOGS.equals(type)) {
            progressBar.setVisibility(View.VISIBLE);
            ServiceLocator.getNotificationLogStorage().getAllNotificationLogs(logs -> {
                allItems.clear();
                allItems.addAll(logs);
                applyFilterAndSort();
            }, e -> {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(requireContext(), "Failed to load notification logs", Toast.LENGTH_SHORT).show();
            });
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
        if (item instanceof User) {
            if (TYPE_REQUESTED_ADMINS.equals(type)) {
                String uid = ((User) item).getUUID();
                String name = ((User) item).getName();
                return "Requested Admin: "+ name + "\n(" + uid + ")";

            } else {
                User user = (User) item;
                String name = user.getName();
                String uid = user.getUUID();
                return (name != null && !name.isEmpty())
                        ? name
                        : "User (" + uid + ")";
            }
        }

        if (item instanceof NotificationLog) {
            return formatNotificationRow((NotificationLog) item);
        }

        return "Unknown Item";
    }

    /**
     * Safely formats nullable text with a fallback.
     */
    private String safeText(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    /**
     * Creates a compact display string for notification log rows.
     */
    private String formatNotificationRow(NotificationLog log) {
        String typeStr = formatNotificationType(log.getType());
        String title = safeText(log.getTitle(), "Untitled Notification");

        return typeStr + " • " + title;
    }

    private String formatNotificationType(NotificationLog.NotificationType type) {
        if (type == null) {
            return "Notification";
        }
        String typeStr;
        switch (type) {
            case WON_LOTTERY:
                typeStr = "Invitation";
            case LOST_LOTTERY:
                typeStr = "Lottery Result";
            case MESSAGE:
                typeStr = "Message";
            case PRIVATE_INVITATION:
                typeStr = "Private Event Invitation";
            case EVENT_ANNOUNCEMENT:
                typeStr = "Event Announcement";
            default:
                typeStr = "Notification";
        }
        return typeStr;
    }

    /**
     * Adapter for the RecyclerView in the fragment
     */
    private class AdminAdapter extends RecyclerView.Adapter<AdminAdapter.ViewHolder> {
        private List<Object> displayItems = new ArrayList<>();

        /**
         * Replaces the displayed items in the adapter
         */
        public void setItems(List<Object> items) {
            this.displayItems = items;
            notifyDataSetChanged();
        }

        ///  create new view holder
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_settings, parent, false);
            return new ViewHolder(view);
        }

        /// bind data to new view holder
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Object item = displayItems.get(position);
            holder.title.setText(getItemDisplayName(item));
            holder.itemView.setOnClickListener(v -> {
                if (TYPE_REQUESTED_ADMINS.equals(type) && item instanceof User) {
                    showPromoteNewAdminDialog((User) item);
                } else if (item instanceof NotificationLog) {
                    showNotificationLogDialog((NotificationLog) item);
                } else {
                openDetails(item);
                }
            });
        }

        /**
         * Opens the EventDetailsActivity screen for the selected Event
         */
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
        /**
         * Shows the promote admin dialog for a requested admin
         *      get requested admins from firbase via AdminStorage
         */
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

        /**
         * Shows a full dialog for a notification log entry.
         */
        private void showNotificationLogDialog(NotificationLog log) {
            StringBuilder sb = new StringBuilder();

            sb.append("Type: ").append(safeText(formatNotificationType(log.getType()), "N/A")).append("\n\n");
            sb.append("Title: ").append(safeText(log.getTitle(), "N/A")).append("\n\n");
            sb.append("Message: ").append(safeText(log.getMessage(), "N/A")).append("\n\n");
            sb.append("Organizer ID: ").append(safeText(log.getOrganizerId(), "N/A")).append("\n");
            sb.append("Event ID: ").append(safeText(log.getEventId(), "N/A")).append("\n");
            sb.append("Time: ").append(log.getTimestamp());

            new AlertDialog.Builder(requireContext())
                    .setTitle("Notification Log")
                    .setMessage(sb.toString())
                    .setPositiveButton("Close", null)
                    .show();
        }

        /**
         * Returns the number of displayed items
         */
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
