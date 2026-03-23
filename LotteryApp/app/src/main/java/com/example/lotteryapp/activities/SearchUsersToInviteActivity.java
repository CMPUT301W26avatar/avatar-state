package com.example.lotteryapp.activities;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lotteryapp.R;
import com.example.lotteryapp.models.Entrant;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.models.User;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventPoolStorage;
import com.example.lotteryapp.services.storage.EventStorage;
import com.google.android.material.search.SearchBar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Allows the organizer of a private event to search for users and send invites directly.
 *  - launched from EventDetailsActivity: invite Users to a private event.
 *  - launched from Create/UpdateEventDialog: invite Users to be a co-organizer for an event.
 */
public class SearchUsersToInviteActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "eventId";

    private SearchBar searchBar;
    private RecyclerView resultsRecycler;

    private FirebaseFirestore db;
    private EventStorage eventStorage;
    private EventPoolStorage eventPoolStorage;

    private String eventId;
    private String currentUserId;
    private Event currentEvent;

    private final List<User> allEligibleUsers = new ArrayList<>();
    private final List<User> filteredUsers = new ArrayList<>();
    private final Set<String> blockedInviteUserIds = new HashSet<>();

    private InviteSearchAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_users_to_invite);

        bindViews();

        db = FirebaseFirestore.getInstance();
        eventStorage = ServiceLocator.getEventStorage();
        eventPoolStorage = ServiceLocator.getEventPoolStorage();

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        currentUserId = ServiceLocator.uid();

        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "Missing event ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupRecycler();
        setupSearchBar();
        validateAccessAndLoad();
    }

    private void bindViews() {
        searchBar = findViewById(R.id.search_bar);
        resultsRecycler = findViewById(R.id.search_results_recycler);
    }

    private void setupRecycler() {
        adapter = new InviteSearchAdapter(filteredUsers, this::confirmInviteUser);
        resultsRecycler.setLayoutManager(new LinearLayoutManager(this));
        resultsRecycler.setAdapter(adapter);
    }

    private void setupSearchBar() {
        searchBar.setHint("Search users");

        // SearchBar in your current layout is treated like a trigger that opens an input dialog.
        searchBar.setOnClickListener(v -> showSearchDialog(searchBar.getText() == null
                ? ""
                : searchBar.getText().toString()));

        searchBar.setNavigationOnClickListener(v -> finish());
    }

    private void validateAccessAndLoad() {
        eventStorage.getEvent(
                eventId,
                event -> {
                    currentEvent = event;

                    if (event == null) {
                        Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    if (!Objects.equals(event.getOrganizerId(), currentUserId)) {
                        Toast.makeText(this, "Only the organizer can send invites", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    if (!isPrivateEvent(event)) {
                        Toast.makeText(this, "Direct invites are only available for private events", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    preloadBlockedInviteUsers();
                },
                e -> {
                    Toast.makeText(this, "Failed to load event", Toast.LENGTH_SHORT).show();
                    finish();
                }
        );
    }

    private boolean isPrivateEvent(Event event) {
        if (event == null) {
            return false;
        }

        try {
            Method method = event.getClass().getMethod("isPrivateEvent");
            Object result = method.invoke(event);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void preloadBlockedInviteUsers() {
        blockedInviteUserIds.clear();
        blockedInviteUserIds.add(currentUserId);

        eventPoolStorage.getInvitedEntrants(
                eventId,
                invited -> {
                    for (Entrant entrant : invited) {
                        if (entrant != null && entrant.getEntrantId() != null) {
                            blockedInviteUserIds.add(entrant.getEntrantId());
                        }
                    }

                    eventPoolStorage.getEnrolledEntrants(
                            eventId,
                            enrolled -> {
                                for (Entrant entrant : enrolled) {
                                    if (entrant != null && entrant.getEntrantId() != null) {
                                        blockedInviteUserIds.add(entrant.getEntrantId());
                                    }
                                }
                                loadAllUsers();
                            },
                            e -> loadAllUsers()
                    );
                },
                e -> loadAllUsers()
        );
    }

    private void loadAllUsers() {
        db.collection("users")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    allEligibleUsers.clear();

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        User user = documentToUser(doc);
                        if (user == null) {
                            continue;
                        }

                        if (blockedInviteUserIds.contains(user.getUUID())) {
                            continue;
                        }

                        allEligibleUsers.add(user);
                    }

                    filteredUsers.clear();
                    adapter.notifyDataSetChanged();

                    Toast.makeText(
                            this,
                            "Tap the search bar to search by username, email, or phone",
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load users", Toast.LENGTH_SHORT).show()
                );
    }

    private User documentToUser(QueryDocumentSnapshot doc) {
        String uid = doc.getId();
        if (uid == null || uid.trim().isEmpty()) {
            return null;
        }

        User user = new User(uid);
        user.setName(doc.getString("name"));
        user.setEmail(doc.getString("email"));
        user.setPhoneNumber(doc.getString("phoneNumber"));

        Boolean isAnon = doc.getBoolean("isAnon");
        if (isAnon != null) {
            user.setAnon(isAnon);
        }

        Boolean isAdmin = doc.getBoolean("isAdmin");
        if (isAdmin != null) {
            user.setAdmin(isAdmin);
        }

        user.setProfilePicUrl(doc.getString("profilePicUrl"));
        return user;
    }

    private void showSearchDialog(String existingQuery) {
        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint("Username, email, or phone");

        TextInputEditText editText = new TextInputEditText(this);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        editText.setSingleLine(true);
        editText.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        editText.setText(existingQuery);

        int horizontal = (int) (20 * getResources().getDisplayMetrics().density);
        int top = (int) (12 * getResources().getDisplayMetrics().density);
        int bottom = (int) (4 * getResources().getDisplayMetrics().density);
        inputLayout.setPadding(horizontal, top, horizontal, bottom);
        inputLayout.addView(editText);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Search users")
                .setView(inputLayout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Search", (d, which) ->
                        applySearch(editText.getText() == null ? "" : editText.getText().toString()))
                .create();

        editText.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applySearch(editText.getText() == null ? "" : editText.getText().toString());
                dialog.dismiss();
                return true;
            }
            return false;
        });

        dialog.show();
    }

    private void applySearch(String rawQuery) {
        String query = normalize(rawQuery);
        searchBar.setText(rawQuery == null ? "" : rawQuery.trim());

        filteredUsers.clear();

        if (query.isEmpty()) {
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Enter a search term", Toast.LENGTH_SHORT).show();
            return;
        }

        for (User user : allEligibleUsers) {
            if (matchesQuery(user, query)) {
                filteredUsers.add(user);
            }
        }

        adapter.notifyDataSetChanged();

        if (filteredUsers.isEmpty()) {
            Toast.makeText(this, "No matching users found", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean matchesQuery(User user, String query) {
        return normalize(user.getName()).contains(query)
                || normalize(user.getEmail()).contains(query)
                || normalize(user.getPhoneNumber()).contains(query);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.CANADA);
    }

    private void confirmInviteUser(User user) {
        if (user == null) {
            return;
        }

        String displayName = user.getName() == null || user.getName().trim().isEmpty()
                ? "this user"
                : user.getName().trim();

        new AlertDialog.Builder(this)
                .setTitle("Send invite")
                .setMessage("Send a private event invite to " + displayName + "?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Invite", (DialogInterface dialog, int which) -> sendInvite(user))
                .show();
    }

    private void sendInvite(User user) {
        Entrant entrant = new Entrant(
                user.getUUID(),
                eventId,
                Entrant.EntrantStatus.INVITED
        );

        eventPoolStorage.inviteDirectToPrivateEvent(
                eventId,
                entrant,
                unused -> {
                    // send an Invite Notification to the user to let them know they can join the event if they would like to
                    sendInviteNotification(user.getUUID(), eventId);

                    blockedInviteUserIds.add(user.getUUID());
                    allEligibleUsers.remove(user);
                    filteredUsers.remove(user);
                    adapter.notifyDataSetChanged();

                    String invitedName = user.getName() == null || user.getName().trim().isEmpty()
                            ? "User invited"
                            : user.getName().trim() + " invited";

                    Toast.makeText(this, invitedName, Toast.LENGTH_SHORT).show();
                },
                e -> Toast.makeText(
                        this,
                        e.getMessage() == null ? "Failed to send invite" : e.getMessage(),
                        Toast.LENGTH_SHORT
                ).show()
        );
    }

    /**
     * Sends a notification to a user that they have been invited to a private event.
     * - TO DO!!
     */
    private void sendInviteNotification(String invitedUserId, String eventId) {
        // TODO: Send a notification to a user that they have been invited to the private event...
    }

    private static final class InviteSearchAdapter
            extends RecyclerView.Adapter<InviteSearchAdapter.InviteViewHolder> {

        interface OnUserClickListener {
            void onUserClicked(User user);
        }

        private final List<User> users;
        private final OnUserClickListener listener;

        InviteSearchAdapter(List<User> users, OnUserClickListener listener) {
            this.users = users;
            this.listener = listener;
        }

        @NonNull
        @Override
        public InviteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new InviteViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull InviteViewHolder holder, int position) {
            User user = users.get(position);

            String name = user.getName() == null || user.getName().trim().isEmpty()
                    ? "Unnamed user"
                    : user.getName().trim();

            String email = user.getEmail() == null ? "" : user.getEmail().trim();
            String phone = user.getPhoneNumber() == null ? "" : user.getPhoneNumber().trim();

            StringBuilder subtitle = new StringBuilder();

            if (!email.isEmpty()) {
                subtitle.append(email);
            }

            if (!phone.isEmpty()) {
                if (subtitle.length() > 0) {
                    subtitle.append(" • ");
                }
                subtitle.append(phone);
            }

            if (subtitle.length() == 0) {
                subtitle.append("Tap to invite");
            }

            holder.title.setText(name);
            holder.subtitle.setText(subtitle.toString());

            holder.itemView.setOnClickListener(v -> listener.onUserClicked(user));
        }

        @Override
        public int getItemCount() {
            return users.size();
        }

        static final class InviteViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            TextView subtitle;

            InviteViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(android.R.id.text1);
                subtitle = itemView.findViewById(android.R.id.text2);
            }
        }
    }
}