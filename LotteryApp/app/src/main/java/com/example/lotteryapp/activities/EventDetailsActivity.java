package com.example.lotteryapp.activities;

import static com.example.lotteryapp.models.Entrant.EntrantStatus.DECLINED;
import static com.example.lotteryapp.models.Entrant.EntrantStatus.ENROLLED;
import static com.example.lotteryapp.models.Entrant.EntrantStatus.WAITLISTED;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.lotteryapp.R;
import com.example.lotteryapp.models.EventAddress;
import com.example.lotteryapp.models.EventJoinedMap;
import com.example.lotteryapp.models.User;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.models.Entrant;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.services.storage.EventPoolStorage;
import com.example.lotteryapp.services.storage.EventStorage;
import com.example.lotteryapp.services.storage.UserStorage;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Displays the details of a selected event and provides actions
 * based on the current user's role and status.
 *      user: join/leave waitlist, accept/decline invitation, unenroll from event
 *      organizer: begin lottery selection
 *      admin: remove event , delete image
 */
public class EventDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "eventId";

    private LinearLayout invitations_layout;
    private MaterialTextView tvName;
    private static final String MY_TAG = "InvitationDebug";

    // text views
    private MaterialTextView tvLocation;
    private MaterialTextView tvDescription;
    private MaterialTextView tvDate;
    private MaterialTextView tvRegEndDate;
    private MaterialTextView tvCriteriaGuidelines;

    // buttons
    private MaterialButton btnClose;
    private MaterialButton btnJoin;
    private MaterialButton btnLeave;
    private MaterialButton btnAccept;
    private MaterialButton btnDecline;
    private MaterialButton btnDeleteImage;
    private MaterialButton btnRemoveEvent;
    private MaterialButton btnBeginLotterySelection;
    private MaterialButton btnViewEventMap;
    // poster
    private ImageView ivEventPoster;

    // Admin Tech Details Views
    private View layoutAdminInfo;
    private TextView tvAdminEventId, tvAdminOrganizerId, tvAdminStatus, tvAdminCapacity,
            tvAdminWaitlistCap, tvAdminEnrolled, tvAdminWaitlistCount, tvAdminRegStart, tvAdminPosterUrl;

    private String eventId;
    private String currentUserId;
    private boolean isAdminMode = false;
    private Event currentEvent;
    // services
    private UserStorage userStorage;

    private EventStorage eventStorage;
    private EventPoolStorage eventPoolStorage;
    private Entrant.EntrantStatus currentStatus = null;

    private final SimpleDateFormat sdf =
            new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    /**
     * Initializes the activity, binds views and reads intent extras,
     *      validates the event id and signed-in user
     *      starts loading event data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_details);

        isAdminMode = getIntent().getBooleanExtra("isAdminMode", false);

        bindViews();

        btnClose.setOnClickListener(v -> finish());

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);

        if (eventId == null || eventId.trim().isEmpty()) {
            populateFromIntentExtras();
            btnJoin.setEnabled(false);
            btnJoin.setText("Event unavailable");
            Toast.makeText(this, "Missing eventId", Toast.LENGTH_SHORT).show();
            return;
        }

        // get user id from ServiceLocator
        currentUserId = ServiceLocator.uid();
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            populateFromIntentExtras();
            btnJoin.setEnabled(false);
            btnJoin.setText("Not signed in");
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        // get db connections for models from ServiceLocator
        userStorage = ServiceLocator.getUserStorage();
        eventStorage = ServiceLocator.getEventStorage();
        eventPoolStorage = ServiceLocator.getEventPoolStorage();

        // prefill any passed UI extras while backend data loads
        populateFromIntentExtras();

        loadEvent();
    }

    /**
    * Associate all of the xml components with their application counterparts
    */
    private void bindViews() {
        tvName = findViewById(R.id.tv_event_name);
        tvLocation = findViewById(R.id.tv_location);
        tvDescription = findViewById(R.id.tv_description);
        tvDate = findViewById(R.id.tv_event_date);
        tvRegEndDate = findViewById(R.id.tv_reg_end_date);
        tvCriteriaGuidelines = findViewById(R.id.tv_criteria_guidelines);
        invitations_layout = findViewById(R.id.invitations_layout);
        btnClose = findViewById(R.id.btn_close);
        btnJoin = findViewById(R.id.btn_join_waitlist);
        btnLeave = findViewById(R.id.btn_leave_waitlist);
        btnAccept = findViewById(R.id.btn_accept_invitation);
        btnDecline = findViewById(R.id.btn_decline_invitation);
        btnDeleteImage = findViewById(R.id.btn_delete_image);
        btnRemoveEvent = findViewById(R.id.btn_remove_event);
        btnBeginLotterySelection = findViewById(R.id.btn_begin_lottery_selection);
        btnViewEventMap = findViewById(R.id.btn_view_event_map);
        ivEventPoster = findViewById(R.id.iv_event_poster);

        // Admin Info
        layoutAdminInfo = findViewById(R.id.layout_admin_info);
        tvAdminEventId = findViewById(R.id.tv_admin_event_id);
        tvAdminOrganizerId = findViewById(R.id.tv_admin_organizer_id);
        tvAdminStatus = findViewById(R.id.tv_admin_status);
        tvAdminCapacity = findViewById(R.id.tv_admin_capacity);
        tvAdminWaitlistCap = findViewById(R.id.tv_admin_waitlist_cap);
        tvAdminEnrolled = findViewById(R.id.tv_admin_enrolled);
        tvAdminWaitlistCount = findViewById(R.id.tv_admin_waitlist_count);
        tvAdminRegStart = findViewById(R.id.tv_admin_reg_start);
        tvAdminPosterUrl = findViewById(R.id.tv_admin_poster_url);
    }

    /**
     * Loads the current event from storage and updates the UI.
     *      actions set according to User role (entrant, organizer or admin)
     */
    private void loadEvent() {
        eventStorage.getEvent(
                eventId,
                event -> {
                    this.currentEvent = event;
                    android.util.Log.d("EventDetailsActivity",
                            "Event loaded: id=" + currentEvent.getEventId()
                                    + ", title=" + currentEvent.getTitle()
                                    + ", eventDateMs=" + currentEvent.getEventDateMs()
                                    + ", regStartMs=" + currentEvent.getRegStartMs()
                                    + ", regEndMs=" + currentEvent.getRegEndMs()
                                    + ", capacity=" + currentEvent.getEventCapacity());

                    populateInfo(currentEvent);
                    if (isAdminMode) {
                        setupAdminActions();
                    } else if (isOrganizer(currentEvent)) {
                        setupOrganizerActions(currentEvent);
                    } else {
                        setupEntrantActions(currentEvent);
                    }
                },
                e -> {
                    android.util.Log.e("EventDetailsActivity",
                            "Failed to load event: " + e.getMessage(), e);
                    e.printStackTrace();
                }
        );
    }

    /**
     * Determines whether the current user is the organizer of the given event.
     */
    private boolean isOrganizer(Event event) {
        return event.getOrganizerId().equals(currentUserId);
    }

    /**
     * Configures the UI and actions available in admin mode.
     *      hides all buttons except
     *          button to access admin actions which include:
     *              remove event button
     *              delete image button
     */
    private void setupAdminActions() {
        if (isAdminMode) {

            btnRemoveEvent.setVisibility(View.VISIBLE);
            btnRemoveEvent.setEnabled(true);

            btnJoin.setEnabled(false);
            btnJoin.setVisibility(View.GONE);

            btnViewEventMap.setVisibility(View.GONE);

            layoutAdminInfo.setVisibility(View.VISIBLE);
            btnDeleteImage.setVisibility(View.VISIBLE);

            btnRemoveEvent.setOnClickListener(v -> {
                eventStorage.deleteEvent(eventId);
                Toast.makeText(this, "Event Removed", Toast.LENGTH_SHORT).show();
                finish();
            });

            btnDeleteImage.setOnClickListener(v -> {
                eventStorage.getEvent(eventId, event -> {
                    event.setPosterUrl(null);
                    //eventStorage.upsertEvent(event);
                    Toast.makeText(this, "Image Deleted", Toast.LENGTH_SHORT).show();
                    ivEventPoster.setImageResource(R.drawable.ic_image_placeholder);
                    btnDeleteImage.setVisibility(View.GONE);
                }, e -> Toast.makeText(this, "Failed to delete image", Toast.LENGTH_SHORT).show());
            });
        }
    }

    /**
     * Configures the UI and actions available in organizer mode.
     *      hides all buttons other than
     *          button to begin the lottery selection
     */
    private void setupOrganizerActions(Event event) {
        btnJoin.setVisibility(View.GONE);
        btnLeave.setVisibility(View.GONE);
        invitations_layout.setVisibility(View.GONE);

        btnRemoveEvent.setVisibility(View.GONE);
        btnDeleteImage.setVisibility(View.GONE);
        layoutAdminInfo.setVisibility(View.GONE);

        btnViewEventMap.setVisibility(View.VISIBLE);
        btnViewEventMap.setOnClickListener(v -> openEventMap());

        btnBeginLotterySelection.setVisibility(View.GONE);
        btnBeginLotterySelection.setEnabled(false);
        btnBeginLotterySelection.setOnClickListener(null);

        boolean registrationClosed = event.getStatus() == Event.EventStatus.REG_CLOSED;
        boolean registrationFull = event.getStatus() == Event.EventStatus.REG_FULL;

        if (registrationClosed || registrationFull) {
            btnBeginLotterySelection.setVisibility(View.VISIBLE);
            btnBeginLotterySelection.setEnabled(true);
            btnBeginLotterySelection.setOnClickListener(v -> {
                beginLotterySelection(event);
            });
        }
    }

    /**
     * Configures the UI and actions available in user mode.
     *      call EventPoolStorage.getEntrantStatus() query for entrant status
     *      call to renderEntrantActions for state-based action selection
     */
    private void setupEntrantActions(Event event) {
        if (event.getRegStartMs() == null || event.getRegEndMs() == null) {
            showJoinDisabled("Registration Unavailable");
            return;
        }
        eventPoolStorage.getEntrantStatus(
                event.getEventId(),
                currentUserId,
                status -> {
                    currentStatus = status;
                    renderEntrantActions(event, status);
                },
                e -> {
                    currentStatus = null;
                    renderEntrantActions(event, null);
                }
        );
    }

    /**
     * Determine which actions/buttons are valid to show a User based on their state as an Entrant
     *      NONE: join waitlist
     *      WAITLISTED: leave waitlist
     *      INVITED: accept/decline invitation
     *      DECLINED: join button closed -> cannot join again
     *      ENROLLED: unenroll
     */
    private void renderEntrantActions(Event event, Entrant.EntrantStatus status) {
        btnJoin.setVisibility(View.GONE);
        btnLeave.setVisibility(View.GONE);
        invitations_layout.setVisibility(View.GONE);
        btnBeginLotterySelection.setVisibility(View.GONE);
        btnViewEventMap.setVisibility(View.GONE);

        if (status == Entrant.EntrantStatus.WAITLISTED) {
            btnLeave.setVisibility(View.VISIBLE);
            btnLeave.setEnabled(true);
            btnLeave.setOnClickListener(v -> leaveWaitlist());
            return;
        }

        if (status == Entrant.EntrantStatus.INVITED) {
            invitations_layout.setVisibility(View.VISIBLE);
            btnAccept.setOnClickListener(v -> acceptInvitation());
            btnDecline.setOnClickListener(v -> declineInvitation());
            return;
        }

        if (status == ENROLLED) {
            btnJoin.setVisibility(View.VISIBLE);
            btnJoin.setEnabled(true);
            btnJoin.setText("Unenroll");
            btnJoin.setOnClickListener(v -> unenroll());
            return;
        }

        if (status == DECLINED) {
            // cannot join waitlist, or event again
            showJoinDisabled("Invitation Declined");
            return;
        }

        if (event.isRegistrationOpen()) {
            btnJoin.setVisibility(View.VISIBLE);
            btnJoin.setEnabled(true);
            btnJoin.setText("Join Waitlist");
            btnJoin.setOnClickListener(v -> joinWaitlist());
        } else {
            showJoinDisabled("Registration Closed");
        }
    }

    /**
     * Populates visible fields using intent extras when full backend event data is not yet available.
     */
    private void populateFromIntentExtras() {
        String name = getIntent().getStringExtra("event_name");
        String location = getIntent().getStringExtra("event_location");
        String description = getIntent().getStringExtra("event_description");

        if (name != null && !name.trim().isEmpty()) {
            tvName.setText(name);
        }

        if (location != null && !location.trim().isEmpty()) {
            tvLocation.setText(location);
        }

        if (description != null && !description.trim().isEmpty()) {
            tvDescription.setText(description);
        }

        // Leave existing placeholders unless real backend data overrides them.
        if (tvDate.getText() == null || tvDate.getText().toString().trim().isEmpty()) {
            tvDate.setText("Date of Event");
        }

        if (tvRegEndDate.getText() == null || tvRegEndDate.getText().toString().trim().isEmpty()) {
            tvRegEndDate.setText("Register End Date");
        }

        if (tvCriteriaGuidelines.getText() == null || tvCriteriaGuidelines.getText().toString().trim().isEmpty()) {
            String criteriaGuidelines = getString(R.string.criteriaGuidelines);
            tvCriteriaGuidelines.setText("Criteria/Guidelines: " + criteriaGuidelines);
        }
    }

    /**
     * Populates the activity UI with information from the given event.
     *      needs Event object parameter
     */
    private void populateInfo(Event event) {
        if (event == null) {
            Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (event.getTitle() != null && !event.getTitle().trim().isEmpty()) {
            tvName.setText(event.getTitle());
        } else if (event.getEventId() != null) {
            tvName.setText(event.getEventId());
        }

        EventAddress address = event.getAddress();
        if (address != null && address.getLocation() != null && !address.getLocation().trim().isEmpty()) {
            tvLocation.setText(address.getLocation());
        } else {
            tvLocation.setText("Location unavailable");
        }

        if (event.getDescription() != null && !event.getDescription().trim().isEmpty()) {
            tvDescription.setText(event.getDescription());
        } else {
            tvDescription.setText("No description provided.");
        }

        if (event.getEventDateMs() != null) {
            tvDate.setText(sdf.format(new java.util.Date(event.getEventDateMs())));
        } else {
            tvDate.setText("Date unavailable");
        }

        if (event.getRegEndMs() != null) {
            tvRegEndDate.setText("Registration Ends: " +
                    sdf.format(new java.util.Date(event.getRegEndMs())));
        } else {
            tvRegEndDate.setText("Registration end unavailable");
        }

        if (event.getCriteriaGuidelines() != null) {
            tvCriteriaGuidelines.setText("Criteria/Guidelines: " + event.getCriteriaGuidelines());
        }

        if (isAdminMode && event.getPosterUrl() != null && !event.getPosterUrl().isEmpty()) {
            btnDeleteImage.setVisibility(View.VISIBLE);
        } else {
            btnDeleteImage.setVisibility(View.GONE);
        }

        if (isAdminMode) {
            populateAdminTechDetails(event);
        }
    }
    /**
     * Populates the admin-only technical details section with raw event data
     */
    private void populateAdminTechDetails(Event event) {
        tvAdminEventId.setText("eventId: \"" + event.getEventId() + "\"");
        tvAdminOrganizerId.setText("organizerId: \"" + event.getOrganizerId() + "\"");
        tvAdminStatus.setText("status: \"" + (event.getStatus() != null ? event.getStatus().name() : "null") + "\"");
        tvAdminCapacity.setText("eventCapacity: " + event.getEventCapacity());
        tvAdminWaitlistCap.setText("waitlistCapacity: " + (event.getWaitlistCapacity() != null ? event.getWaitlistCapacity() : "null"));
        tvAdminEnrolled.setText("enrolledCount: " + event.getEnrolledCount());
        tvAdminWaitlistCount.setText("waitlistCount: " + event.getWaitlistCount());
        tvAdminRegStart.setText("regStartMs: " + event.getRegStartMs());
        tvAdminPosterUrl.setText("posterUrl: " + (event.getPosterUrl() != null ? "\"" + event.getPosterUrl() + "\"" : "null"));
    }

    private void openEventMap() {
        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "Missing event ID", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, EventMapActivity.class);
        intent.putExtra(EXTRA_EVENT_ID, eventId);
        startActivity(intent);
    }

    /**
     * Disables the join button and displays the parameter text as the reason for it being disabled
     *  to the user
     */
    private void showJoinDisabled(String text) {
        btnJoin.setVisibility(View.VISIBLE);
        btnJoin.setEnabled(false);
        btnJoin.setText(text);
        btnLeave.setVisibility(View.GONE);
        invitations_layout.setVisibility(View.GONE);
    }

    /**
     * Adds the current user to the event waitlist, NONE -> WAITLISTED and added to "waitlisted" collection
     *      EventPoolStorage for db query
     */
    private void joinWaitlist() {
        Entrant entrant = new Entrant(currentUserId, eventId, WAITLISTED);

        btnJoin.setEnabled(false);

        eventPoolStorage.waitlistForEvent(
                eventId,
                entrant,
                unused -> upsertJoinedMapForCurrentUser(
                        () -> {
                            Toast.makeText(this, "Waitlisted!", Toast.LENGTH_SHORT).show();
                            currentStatus = WAITLISTED;
                            loadEvent();
                        },
                        () -> {
                            Toast.makeText(this, "Waitlisted, but failed to save joined map.", Toast.LENGTH_SHORT).show();
                            currentStatus = WAITLISTED;
                            loadEvent();
                        }
                ),
                e -> {
                    Toast.makeText(this, "Failed to waitlist", Toast.LENGTH_SHORT).show();
                    Log.e(MY_TAG, "Operation failed: " + e.getMessage(), e);
                    btnJoin.setEnabled(true);
                }
        );
    }

    /**
     * Removes the current user from the event waitlist, WAITLISTED -> NONE and removed from "waitlist" colleciton
     *      EventPoolStorage for db query
     */
    private void leaveWaitlist() {
        btnLeave.setEnabled(false);

        eventPoolStorage.removeFromWaitlist(
                eventId,
                currentUserId,
                unused -> removeJoinedMapForCurrentUser(
                        () -> {
                            Toast.makeText(this, "Left waitlist!", Toast.LENGTH_SHORT).show();
                            currentStatus = null;
                            loadEvent();
                        },
                        () -> {
                            Toast.makeText(this, "Left waitlist, but failed to remove joined map.", Toast.LENGTH_SHORT).show();
                            currentStatus = null;
                            loadEvent();
                        }
                ),
                e -> {
                    Toast.makeText(this, "Failed to leave waitlist.", Toast.LENGTH_SHORT).show();
                    Log.e(MY_TAG, "Operation failed: " + e.getMessage(), e);
                    btnLeave.setEnabled(true);
                }
        );
    }

    /**
     * After the user successfully joins the waitlist, upsert their joined_map document.
     * This stores a snapshot of the user's address for this event
     */
    private void upsertJoinedMapForCurrentUser(
            Runnable onSuccess,
            Runnable onFailure
    ) {
        userStorage.getUserProfile(
                currentUserId,
                user -> {
                    User.UserAddressMode preferredMode = user.getAddressMode() != null
                            ? user.getAddressMode()
                            : User.UserAddressMode.DEFAULT;

                    User.UserAddressMode fallbackMode =
                            preferredMode == User.UserAddressMode.CURRENT
                                    ? User.UserAddressMode.DEFAULT
                                    : User.UserAddressMode.CURRENT;

                    loadAddressForJoinedMap(preferredMode, address -> {
                        if (isUsableJoinedMapAddress(address)) {
                            writeJoinedMap(address, onSuccess, onFailure);
                            return;
                        }

                        loadAddressForJoinedMap(fallbackMode, fallbackAddress -> {
                            if (isUsableJoinedMapAddress(fallbackAddress)) {
                                writeJoinedMap(fallbackAddress, onSuccess, onFailure);
                            } else {
                                Log.w(MY_TAG, "No usable address found in either preferred or fallback mode for joined_map upsert");
                                onFailure.run();
                            }
                        }, e -> {
                            Log.e(MY_TAG, "Fallback address lookup failed for joined_map upsert", e);
                            onFailure.run();
                        });
                    }, e -> {
                        Log.e(MY_TAG, "Preferred address lookup failed for joined_map upsert", e);

                        loadAddressForJoinedMap(fallbackMode, fallbackAddress -> {
                            if (isUsableJoinedMapAddress(fallbackAddress)) {
                                writeJoinedMap(fallbackAddress, onSuccess, onFailure);
                            } else {
                                Log.w(MY_TAG, "Preferred lookup failed and fallback address is unusable for joined_map upsert");
                                onFailure.run();
                            }
                        }, fallbackError -> {
                            Log.e(MY_TAG, "Fallback address lookup also failed for joined_map upsert", fallbackError);
                            onFailure.run();
                        });
                    });
                },
                e -> {
                    Log.e(MY_TAG, "Failed to load user profile for joined_map upsert", e);
                    onFailure.run();
                }
        );
    }

    /**
     * Remove the current user's joined_map document for this event.
     */
    private void removeJoinedMapForCurrentUser(
            Runnable onSuccess,
            Runnable onFailure
    ) {
        eventStorage.removeEventJoinedMap(
                eventId,
                currentUserId,
                unused -> onSuccess.run(),
                e -> {
                    Log.e(MY_TAG, "Failed to remove joined_map document", e);
                    onFailure.run();
                }
        );
    }

    private void loadAddressForJoinedMap(
            User.UserAddressMode addressMode,
            com.google.android.gms.tasks.OnSuccessListener<com.example.lotteryapp.models.UserAddress> onSuccess,
            com.google.android.gms.tasks.OnFailureListener onFailure
    ) {
        userStorage.getPreferredUserAddress(
                currentUserId,
                addressMode,
                onSuccess,
                onFailure
        );
    }

    private boolean isUsableJoinedMapAddress(com.example.lotteryapp.models.UserAddress address) {
        return address != null
                && address.getLatitude() != null
                && address.getLongitude() != null;
    }

    private void writeJoinedMap(
            com.example.lotteryapp.models.UserAddress address,
            Runnable onSuccess,
            Runnable onFailure
    ) {
        EventJoinedMap joinedMap = new EventJoinedMap(
                eventId,
                address,
                System.currentTimeMillis()
        );

        eventStorage.setEventJoinedMap(
                eventId,
                joinedMap,
                unused -> onSuccess.run(),
                e -> {
                    Log.e(MY_TAG, "Failed to upsert joined_map document", e);
                    onFailure.run();
                }
        );
    }


    /**
     * Signs a user up for the event, INVITED -> ENROLLED in db and added to "enrolled" collection
     *      EventPoolStorage for db query
     */
    private void acceptInvitation() {
        Entrant entrant = new Entrant(currentUserId, eventId, ENROLLED);

        btnAccept.setEnabled(false);

        eventPoolStorage.enrollInEvent(
                eventId,
                entrant,
                unused -> {
                    Toast.makeText(this, "Enrolled!", Toast.LENGTH_SHORT).show();
                    currentStatus = ENROLLED;
                    loadEvent();
                },
                e -> {
                    Toast.makeText(this, "Failed to enroll", Toast.LENGTH_SHORT).show();
                    Log.e(MY_TAG, "Operation failed: " + e.getMessage(), e);
                    btnAccept.setEnabled(true);
                }
        );
    }

    /**
     * Declines the sign-up invitation, INVITED -> DECLINED in db and
     *      added to "declined" collection, removed from "invited" collection
     *      EventPoolStorage for db query
     */
    private void declineInvitation() {
        btnDecline.setEnabled(false);

        eventPoolStorage.removeFromWaitlist(
                eventId,
                currentUserId,
                unused -> removeJoinedMapForCurrentUser(
                        () -> {
                            Toast.makeText(this, "Invitation declined!", Toast.LENGTH_SHORT).show();
                            currentStatus = DECLINED;
                            loadEvent();
                        },
                        () -> {
                            Toast.makeText(this, "Invitation declined, but failed to remove joined map.", Toast.LENGTH_SHORT).show();
                            currentStatus = DECLINED;
                            loadEvent();
                        }
                ),
                e -> {
                    Toast.makeText(this, "Failed to decline invitation", Toast.LENGTH_SHORT).show();
                    Log.e(MY_TAG, "Operation failed: " + e.getMessage(), e);
                    btnDecline.setEnabled(true);
                }
        );
    }

    /**
     * Removes the current user from the event, ENROLLED -> NONE and removed from "enrolled" colleciton
     *      EventPoolStorage for db query
     */
    private void unenroll() {
        btnJoin.setEnabled(false);

        eventPoolStorage.unenroll(
                eventId,
                currentUserId,
                unused -> {
                    Toast.makeText(this, "Unenrolled!", Toast.LENGTH_SHORT).show();
                    currentStatus = null;
                    loadEvent();
                },
                e -> {
                    Toast.makeText(this, "Failed to unenroll", Toast.LENGTH_SHORT).show();
                    Log.e(MY_TAG, "Operation failed: " + e.getMessage(), e);
                    btnJoin.setEnabled(true);
                }
        );

    }

    /**
     * Starts lottery selection for the given event
     *      adds all selected entrants to the "invited" subcollection
     *      status marked INVITED
     */
    private void beginLotterySelection(Event event) {
        if (event == null) {
            Toast.makeText(this, "Event not loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        int remainingSpots = event.getEventCapacity()
                - event.getEnrolledCount()
                - event.getInvitationCount();

        if (remainingSpots <= 0) {
            Toast.makeText(this, "No spots available for invitations", Toast.LENGTH_SHORT).show();
            return;
        }

        if (btnBeginLotterySelection != null) {
            btnBeginLotterySelection.setEnabled(false);
        }

        eventPoolStorage.drawWinners(
                eventId,
                remainingSpots,
                invitedCount  -> {
                    Toast.makeText(this, "Lottery complete: sent " + invitedCount + "invite(s)", Toast.LENGTH_SHORT).show();
                    loadEvent();
                    if (btnBeginLotterySelection != null) {
                        btnBeginLotterySelection.setEnabled(true);
                    }
                },
                e -> {
                    Toast.makeText(this, "Failed to run lottery", Toast.LENGTH_SHORT).show();
                    Log.e(MY_TAG, "Lottery selection failed: " + e.getMessage(), e);
                    if (btnBeginLotterySelection != null) {
                        btnBeginLotterySelection.setEnabled(true);
                    }
                }
        );
    }
}