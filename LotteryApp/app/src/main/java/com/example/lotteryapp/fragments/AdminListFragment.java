package com.example.lotteryapp.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.lotteryapp.R;
import com.example.lotteryapp.activities.EventDetailsActivity;
import com.example.lotteryapp.activities.UserDetailsActivity;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.models.NotificationLog;
import com.example.lotteryapp.models.User;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.AdminStorage;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
/**
 * fragment for displaying admin lists inside of AdminActivity
 *      shows events profiles, images, events, notifications and requested admins
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

    // Storage navigation
    private StorageReference currentStorageFolder;
    private View layoutPathNav;
    private TextView tvCurrentPath;
    private ImageButton btnNavBack;

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
        if (TYPE_IMAGES.equals(type)) {
            currentStorageFolder = ServiceLocator.getFirebase().getStorage().getReference();
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

        layoutPathNav = view.findViewById(R.id.layout_path_navigation);
        tvCurrentPath = view.findViewById(R.id.tv_current_path);
        btnNavBack = view.findViewById(R.id.btn_nav_back);

        if (TYPE_IMAGES.equals(type)) {
            layoutPathNav.setVisibility(View.VISIBLE);
            btnNavBack.setOnClickListener(v -> navigateUp());
            updatePathUI();
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AdminAdapter();
        recyclerView.setAdapter(adapter);

        return view;
    }

    /**
     * updates the storage path label and back button state for image navigation
     */
    private void updatePathUI() {
        if (tvCurrentPath != null && currentStorageFolder != null) {
            String path = currentStorageFolder.getPath();
            tvCurrentPath.setText(path.equals("/") ? "Root" : path);
            btnNavBack.setEnabled(!path.equals("/"));
            btnNavBack.setAlpha(path.equals("/") ? 0.5f : 1.0f);
        }
    }


    /**
     * navigates to the parent storage folder when not already at the root
     */
    private void navigateUp() {
        if (currentStorageFolder != null && !currentStorageFolder.getPath().equals("/")) {
            currentStorageFolder = currentStorageFolder.getParent();
            updatePathUI();
            loadData();
        }
    }


    /**
     * switches the storage view to the selected folder and reloads its contents
     */
    private void navigateToFolder(StorageReference folder) {
        currentStorageFolder = folder;
        updatePathUI();
        loadData();
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
        // profiles mode loads every user document and displays them through the shared adapter pipeline
        if (TYPE_PROFILES.equals(type)) {
            progressBar.setVisibility(View.VISIBLE);
            ServiceLocator.getUserStorage().getAllUsers(users -> {
                allItems.clear();
                allItems.addAll(users);
                applyFilterAndSort();
            }, e -> progressBar.setVisibility(View.GONE));
            return;
        }

        // File viewer mode for Firebase Storage
        if (TYPE_IMAGES.equals(type)) {
            progressBar.setVisibility(View.VISIBLE);
            if (currentStorageFolder == null) {
                currentStorageFolder = ServiceLocator.getFirebase().getStorage().getReference();
            }

            currentStorageFolder.listAll().addOnSuccessListener(listResult -> {
                allItems.clear();
                // Folders come first
                allItems.addAll(listResult.getPrefixes());
                // Then files
                allItems.addAll(listResult.getItems());
                applyFilterAndSort();
            }).addOnFailureListener(e -> {
                Log.e("AdminListFragment", "Failed to list storage content", e);
                progressBar.setVisibility(View.GONE);
                tvEmpty.setVisibility(View.VISIBLE);
                Toast.makeText(getContext(), "Failed to load storage", Toast.LENGTH_SHORT).show();
            });
            return;
        }

        // events mode loads all events for admin review
        if (TYPE_EVENTS.equals(type)) {
            progressBar.setVisibility(View.VISIBLE);
            ServiceLocator.getEventStorage().getAllEvents(events -> {
                allItems.clear();
                allItems.addAll(events);
                applyFilterAndSort();
            }, e -> progressBar.setVisibility(View.GONE));
        }

        // notification logs mode loads every saved log entry for inspection
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

        // requested admins mode first fetches the requested admin ids then resolves them for user details
        if (TYPE_REQUESTED_ADMINS.equals(type)) {
            progressBar.setVisibility(View.VISIBLE);
            ServiceLocator.getAdminStorage().getRequestedAdmins(requestIds -> ServiceLocator.getUserStorage().getAllUsers(users -> {
                allItems.clear();
                for (User user : users) {
                    if (requestIds.contains(user.getUUID())) {
                        allItems.add(user);
                    }
                }
                applyFilterAndSort();
            }, e -> progressBar.setVisibility(View.GONE)), e -> progressBar.setVisibility(View.GONE));
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
                    // Always put folders at the top in storage view
                    if (TYPE_IMAGES.equals(type)) {
                        boolean isFolder1 = isStorageFolder(o1);
                        boolean isFolder2 = isStorageFolder(o2);
                        if (isFolder1 && !isFolder2) return -1;
                        if (!isFolder1 && isFolder2) return 1;
                    }
                    
                    String n1 = getItemDisplayName(o1);
                    String n2 = getItemDisplayName(o2);
                    return sortAscending ? n1.compareToIgnoreCase(n2) : n2.compareToIgnoreCase(n1);
                })
                .collect(Collectors.toList());

        progressBar.setVisibility(View.GONE);
        adapter.setItems(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /**
     * determines whether a storage reference should be treated as a folder in the current list
     */
    private boolean isStorageFolder(Object item) {
        if (!(item instanceof StorageReference)) return false;
        // StorageReference.listAll() prefixes are folders, items are files.
        // We can't easily distinguish them later without context,
        // so we check if it was in the getPrefixes() part.
        // A hacky but effective way is to see if it has a file extension or not,
        // but it's better to handle it in the adapter where we know.
        // Actually, prefixes and items are both StorageReferences.
        return allItems.stream()
                .filter(it -> it instanceof StorageReference)
                .limit(adapter.folderCount)
                .anyMatch(it -> it == item);
    }

    /**
     * returns the display label used for filtering sorting and row text across all supported item types
     */
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

        if (item instanceof StorageReference) {
            return ((StorageReference) item).getName();
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

    /**
     * maps notification types to user facing labels for the admin list
     */
    private String formatNotificationType(NotificationLog.NotificationType type) {
        if (type == null) {
            return "Notification";
        }
        String typeStr;
        switch (type) {
            case WON_LOTTERY:
                typeStr = "Invitation";
                break;
            case LOST_LOTTERY:
                typeStr = "Lottery Result";
                break;
            case MESSAGE:
                typeStr = "Message";
                break;
            case PRIVATE_INVITATION:
                typeStr = "Private Event Invitation";
                break;
            case EVENT_ANNOUNCEMENT:
                typeStr = "Event Announcement";
                break;
            default:
                typeStr = "Notification";
                break;
        }
        return typeStr;
    }

    /**
     * Adapter for the RecyclerView in the fragment
     */
    private class AdminAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int VIEW_TYPE_DEFAULT = 0;
        private static final int VIEW_TYPE_IMAGE = 1;
        private static final int VIEW_TYPE_FOLDER = 2;

        private List<Object> displayItems = new ArrayList<>();
        private int folderCount = 0;

        /**
         * Replaces the displayed items in the adapter
         */
        public void setItems(List<Object> items) {
            this.displayItems = items;
            // Recalculate folder count based on original allItems list if possible, 
            // or just identify them here.
            this.folderCount = 0;
            // Since we sorted folders to the top in applyFilterAndSort, we can count them
            for (Object item : items) {
                if (item instanceof StorageReference && isStorageFolderReference((StorageReference) item)) {
                    folderCount++;
                } else {
                    break; 
                }
            }
            notifyDataSetChanged();
        }

        /**
         * determines whether a storage reference belongs to the folder prefix portion of the storage result
         */
        private boolean isStorageFolderReference(StorageReference ref) {
            return allItems.indexOf(ref) < getPrefixCount();
        }

        /**
         * returns how many items should be treated as folders
         */
        private int getPrefixCount() {
            return 0; // fallback
        }

        /**
         * chooses the row view type for the item at the given position
         */
        @Override
        public int getItemViewType(int position) {
            if (TYPE_IMAGES.equals(type)) {
                Object item = displayItems.get(position);
                if (item instanceof StorageReference) {
                    // Check if it's an image based on extension or prefix list
                    String name = ((StorageReference) item).getName().toLowerCase();
                    boolean isImage = name.endsWith(".jpg") || name.endsWith(".jpeg") || 
                                     name.endsWith(".png") || name.endsWith(".webp");
                    
                    // We also need to know if it's a prefix (folder). 
                    // Let's use a simpler heuristic for now: if it's in the listResult.prefixes it's a folder.
                    // I will change the allItems to hold a custom wrapper to make this robust, maybe
                    if (isImage) return VIEW_TYPE_IMAGE;
                    return VIEW_TYPE_FOLDER;
                }
            }
            return VIEW_TYPE_DEFAULT;
        }

        /**
         * inflates the correct row layout for each supported view type
         */
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_IMAGE) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_image, parent, false);
                return new ImageViewHolder(view);
            }
            if (viewType == VIEW_TYPE_FOLDER) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_settings, parent, false);
                return new FolderViewHolder(view);
            }
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_settings, parent, false);
            return new ViewHolder(view);
        }

        /**
         * binds each row according to whether the item is an image folder or default admin list entry
         */
        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object item = displayItems.get(position);

            if (holder instanceof ImageViewHolder) {
                ImageViewHolder imgHolder = (ImageViewHolder) holder;
                StorageReference fileRef = (StorageReference) item;
                imgHolder.title.setText(fileRef.getName());

                // Fetch download URL to display with Glide
                fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    if (isAdded()) {
                        Glide.with(imgHolder.itemView.getContext())
                                .load(uri)
                                .placeholder(R.drawable.ic_image_placeholder)
                                .error(R.drawable.ic_image_placeholder)
                                .into(imgHolder.poster);
                    }
                }).addOnFailureListener(e -> imgHolder.poster.setImageResource(R.drawable.ic_image_placeholder));

                imgHolder.btnDelete.setOnClickListener(v -> deleteStorageFile(fileRef));
            }
            else if (holder instanceof FolderViewHolder) {
                FolderViewHolder folderHolder = (FolderViewHolder) holder;
                StorageReference folderRef = (StorageReference) item;
                folderHolder.title.setText(folderRef.getName());
                folderHolder.icon.setImageResource(R.drawable.ic_manage); // Use a folder-like icon
                folderHolder.itemView.setOnClickListener(v -> navigateToFolder(folderRef));
            }
            else {
                ViewHolder vh = (ViewHolder) holder;
                vh.title.setText(getItemDisplayName(item));
                vh.itemView.setOnClickListener(v -> {
                    if (TYPE_REQUESTED_ADMINS.equals(type) && item instanceof User) {
                        showPromoteNewAdminDialog((User) item);
                    } else if (item instanceof NotificationLog) {
                        showNotificationLogDialog((NotificationLog) item);
                    } else {
                        openDetails(item);
                    }
                });
            }
        }

        /**
         * shows a confirmation dialog and deletes the given storage file from firebase then refreshes data on success
         */
        private void deleteStorageFile(StorageReference fileRef) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete Image")
                    .setMessage("Are you sure you want to delete " + fileRef.getName() + " from Firebase Storage?")
                    .setPositiveButton("Delete", (dialog, which) -> fileRef.delete().addOnSuccessListener(unused -> {
                        Toast.makeText(requireContext(), "File deleted", Toast.LENGTH_SHORT).show();
                        loadData();
                    }).addOnFailureListener(e -> Toast.makeText(requireContext(), "Failed to delete file: " + e.getMessage(), Toast.LENGTH_SHORT).show()))
                    .setNegativeButton("Cancel", null)
                    .show();
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
         *      get requested admins from firebase via AdminStorage
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
                btnCancelRequest.setOnClickListener(v -> astore.removeRequestedAdmin(user.getUUID(), unused -> {
                    Toast.makeText(requireContext(), user.getName() + "'s request was cancelled", Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                    loadData();
                    }, e -> {
                        Toast.makeText(requireContext(), "Failed to cancel request", Toast.LENGTH_LONG).show();
                        e.printStackTrace();
                    }
                ));
            }
            if (btnAcceptRequest != null) {
                btnAcceptRequest.setOnClickListener(v -> astore.promoteToAdmin(user.getUUID(), unused -> {
                    Toast.makeText(requireContext(), user.getName() + " is now an admin", Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                    loadData();

                }, e -> {
                    Toast.makeText(requireContext(), "Failed to promote new admin", Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }));
            }
            dialog.show();
        }

        /**
         * Shows a full dialog for a notification log entry.
         */
        private void showNotificationLogDialog(NotificationLog log) {

            String sb = "Type: " + safeText(formatNotificationType(log.getType()), "N/A") + "\n\n" +
                    "Title: " + safeText(log.getTitle(), "N/A") + "\n\n" +
                    "Message: " + safeText(log.getMessage(), "N/A") + "\n\n" +
                    "Organizer ID: " + safeText(log.getOrganizerId(), "N/A") + "\n" +
                    "Event ID: " + safeText(log.getEventId(), "N/A") + "\n" +
                    "Time: " + log.getTimestamp();

            new AlertDialog.Builder(requireContext())
                    .setTitle("Notification Log")
                    .setMessage(sb)
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

        /**
         * view holder for simple text based items in the list
         */
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            ViewHolder(View view) {
                super(view);
                title = view.findViewById(R.id.tv_settings_name);
            }
        }

        /**
         * view holder for items that include an image preview
         */
        class ImageViewHolder extends RecyclerView.ViewHolder {
            ImageView poster;
            TextView title;
            MaterialButton btnDelete;

            ImageViewHolder(View view) {
                super(view);
                poster = view.findViewById(R.id.iv_admin_poster);
                title = view.findViewById(R.id.tv_admin_event_title);
                btnDelete = view.findViewById(R.id.btn_admin_delete_image);
            }
        }

        /**
         * view holder for folder style items used for navigation or grouping
         */
        class FolderViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            ImageView icon;
            FolderViewHolder(View view) {
                super(view);
                title = view.findViewById(R.id.tv_settings_name);
                icon = view.findViewById(R.id.iv_arrow); // Reusing arrow as placeholder for folder icon
            }
        }
    }
}
