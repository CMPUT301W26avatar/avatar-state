package com.example.lotteryapp.activities;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;
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
import com.google.android.material.button.MaterialButton;
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

    public static final String EXTRA_IS_COORGANIZER_MODE = "isCoorganizerMode";

    private boolean isCoorganizerMode = false;
    private ImageButton btnClose;

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

    /**
     * initialize and associate all UI components with their xml counterparts
     * setup activity with required data such as intent extras, storage access, current uid
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_users_to_invite);

        // associate UI components with their xml counterparts
        bindViews();

        // init. storage classes from the service locator
        db = FirebaseFirestore.getInstance();
        eventStorage = ServiceLocator.getEventStorage();
        eventPoolStorage = ServiceLocator.getEventPoolStorage();

        // get event and coOrganizer mode from intent extras
        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        isCoorganizerMode = getIntent().getBooleanExtra(EXTRA_IS_COORGANIZER_MODE, false);

        // get the user uuid from the service locator
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

        // setup UI components
        btnClose.setOnClickListener(v -> finish());
        setupRecycler();
        setupSearchBar();
        validateAccessAndLoad();
    }

    /**
     *  associate all of the activity's UI components with their xml counterparts
     *      - minus the recyclerView and search bar
     */

    private void bindViews() {
        searchBar = findViewById(R.id.search_bar);
        btnClose = findViewById(R.id.btn_close);
        resultsRecycler = findViewById(R.id.search_results_recycler);
    }

    /**
     * associates the RecyclerView from xml to its respective layout and list item adapter on the app side
     */
    private void setupRecycler() {
        adapter = new InviteSearchAdapter(filteredUsers, this::confirmInviteUser);
        resultsRecycler.setLayoutManager(new LinearLayoutManager(this));
        resultsRecycler.setAdapter(adapter);
    }

    /**
     * associates the Search Bar from xml to its app-side counterpart, and sets its text based on the user search mode
     */
    private void setupSearchBar() {
        if (isCoorganizerMode) {
            searchBar.setHint("Search for co-organizers");
        } else {
            searchBar.setHint("Search for entrants");
        }

        searchBar.setOnClickListener(v -> showSearchDialog(searchBar.getText() == null
                ? ""
                : searchBar.getText().toString()));
    }

    /**
     * validate that the current user is an organizer, and that the event is valid
     * preload a list of users to remove from the search query such as organizer, and all current entrants
     */
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

                    // load users to remove from the search query
                    preloadBlockedInviteUsers();
                },
                e -> {
                    Toast.makeText(this, "Failed to load event", Toast.LENGTH_SHORT).show();
                    finish();
                }
        );
    }

    /**
     * add users to a blocked users list for the search query to prevent duplicate invites
     *      users who get blocked: the organizer, those who are currently enrolled in the event, or have a pending invitation.
     */
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

    /**
     * load all of the users from the database and clear those in the blocked list from the result
     */
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

    /**
     * Instantiate a User object from a firebase document
     *      events/{eventId}/coorganizer_invites/{userId} for coorganizer invites, and
     *      events/{eventId}/invited/{userId} for private events
     */
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

    /**
     * Show the search dialog that pops up when clicking on a list item (User)
     *      confirms whether the user wants to send an invite to the user or not
     */
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
                .setView(inputLayout)
                .setTitle(isCoorganizerMode
                        ? "Invite a user to co-organize your event"
                        : "Invite a user to your private event")
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

    /**
     * Resolve the user's input into a search query, run the query
     *  for each User returned by the query, add these users to the list of users to display as a search result
     */
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

    /**
     * Helper for determining if a portion, or whole of a Users profile attributes match the users search query input
     */
    private boolean matchesQuery(User user, String query) {
        return normalize(user.getName()).contains(query)
                || normalize(user.getEmail()).contains(query)
                || normalize(user.getPhoneNumber()).contains(query);
    }

    /**
     * Helper for normalizing strings to lowercase, and stripping whitespace
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.CANADA);
    }

    /**
     * One extra layer of confirmation for the user
     *      when the user clicks confirm, this dialog is launched so that the user can confirm they selected the right user
     *      displays the User's display name (username) for final confirmation for the user
     */
    private void confirmInviteUser(User user) {
        if (user == null) {
            return;
        }

        String displayName = user.getName() == null || user.getName().trim().isEmpty()
                ? "this user"
                : user.getName().trim();

        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(isCoorganizerMode ? "Send co-organizer invite" : "Send invite")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Invite", (DialogInterface d, int which) -> sendInvite(user));

        if (isCoorganizerMode) {
            dialog.setMessage("Send a co-organizer invite to " + displayName + "?");
        } else {
            dialog.setMessage("Send a private event invite to " + displayName + "?");
        }

        dialog.show();
    }

    /**
     * Call the EventPoolStorage service to add the selected User to the correct subcollection
     *      - invited for private event inviting entrants
     */
    private void sendInvite(User user) {
        if (isCoorganizerMode) {
            sendCoorganizerInvite(user);
            return;
        }

        Entrant entrant = new Entrant(
                user.getUUID(),
                eventId,
                Entrant.EntrantStatus.INVITED
        );

        eventPoolStorage.inviteDirectToPrivateEvent(
                eventId,
                entrant,
                unused -> {
                    sendPrivateInviteNotification(user.getUUID(), eventId);

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
     * Call the EventPoolStorage service to add the selected User to the correct subcollection
     *      - coorganizer invites for coorganizers
     */
    private void sendCoorganizerInvite(User user) {
        if (user == null || user.getUUID() == null || user.getUUID().trim().isEmpty()) {
            Toast.makeText(this, "Invalid user", Toast.LENGTH_SHORT).show();
            return;
        }

        eventStorage.inviteCoorganizer(
                eventId,
                currentUserId,
                user.getUUID(),
                unused -> {
                    blockedInviteUserIds.add(user.getUUID());
                    allEligibleUsers.remove(user);
                    filteredUsers.remove(user);
                    adapter.notifyDataSetChanged();

                    sendCoorganizerInviteNotification(user.getUUID(), eventId);

                    String invitedName = user.getName() == null || user.getName().trim().isEmpty()
                            ? "Co-organizer invite sent"
                            : "Co-organizer invite sent to " + user.getName().trim();

                    Toast.makeText(this, invitedName, Toast.LENGTH_SHORT).show();
                },
                e -> Toast.makeText(
                        this,
                        e.getMessage() == null
                                ? "Failed to send co-organizer invite"
                                : e.getMessage(),
                        Toast.LENGTH_SHORT
                ).show()
        );
    }

    /**
     * Sends a notification to a user that they have been invited to a private event.
     * - TO DO!!
     */
    private void sendPrivateInviteNotification(String invitedUserId, String eventId) {
        // TODO: Send a notification to a user that they have been invited to the private event...
    }

    /**
     * Sends a notification to a user that they have been invited to be a co-organizer for an event
     * - TO DO!!
     */
    private void sendCoorganizerInviteNotification(String invitedUserId, String eventId) {
        // TODO: Send a notification to a user that they have been invited to be a co-organizer...
    }

    /**
     * Recycler View adapter so we can set an onClickListener for each User resolved to a list item
     *      clicking on the user launches the first confirmation dialog
     *      binds views and sets subtitles for the item
     *      the InviteSearchAdapter also tracks the number of items in the recycler
     */
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