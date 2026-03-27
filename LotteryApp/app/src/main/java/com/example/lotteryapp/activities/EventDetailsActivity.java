package com.example.lotteryapp.activities;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.example.lotteryapp.models.Entrant.EntrantStatus.DECLINED;
import static com.example.lotteryapp.models.Entrant.EntrantStatus.ENROLLED;
import static com.example.lotteryapp.models.Entrant.EntrantStatus.WAITLISTED;
import static com.example.lotteryapp.models.Event.EventStatus.EVENT_CLOSED;
import static com.example.lotteryapp.models.Event.EventStatus.EVENT_FULL;
import static com.example.lotteryapp.models.Event.EventStatus.EVENT_OPEN;
import static com.example.lotteryapp.models.Event.EventStatus.REG_CLOSED;
import static com.example.lotteryapp.models.Event.EventStatus.REG_FULL;
import static com.example.lotteryapp.models.Event.EventStatus.REG_OPEN;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.lotteryapp.R;
import com.example.lotteryapp.models.Entrant;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.models.EventAddress;
import com.example.lotteryapp.models.EventJoinedMap;
import com.example.lotteryapp.models.QRCode;
import com.example.lotteryapp.models.User;
import com.example.lotteryapp.services.ProfanityFilter;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventPoolStorage;
import com.example.lotteryapp.services.storage.EventStorage;
import com.example.lotteryapp.services.storage.UserStorage;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

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
    private MaterialTextView tvViewComments;

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
    private MaterialButton btnShowInvitesDashboard;

    private ImageButton btnCommentIcon;
    private ImageButton btnShareIcon;
    private ImageButton btnQRCodeIcon;
    private ImageButton btnSaveIcon;

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
    private QRCode qrCode = null;

    ActivityResultLauncher<Intent> resultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), res -> {
        if (res.getResultCode() == Activity.RESULT_OK) {
            Intent data = res.getData();
            if (data == null) {
                throw new RuntimeException();
            }

            Uri qrDownloadUri = data.getData();
            if (qrDownloadUri == null) {
                throw new RuntimeException();
            }

            ContentResolver resolver = getApplicationContext()
                    .getContentResolver();

            try (OutputStream outputStream = resolver.openOutputStream(qrDownloadUri, "w")) {
                Log.i("QR-CODE", qrDownloadUri.toString());
                byte[] imageData = qrCode.getPngData();
                if (outputStream != null) {
                    outputStream.write(imageData);
                    outputStream.flush();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    });

    private final SimpleDateFormat sdf =
            new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    /**
     * Initializes the activity, binds views and reads intent extras,
     * validates the event id and signed-in user
     * starts loading event data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_details);

        isAdminMode = getIntent().getBooleanExtra("isAdminMode", false);

        Intent intent = getIntent();
        Uri data = intent.getData();

        if (data != null) {
            Toast toast = Toast.makeText(getBaseContext(), "Hello, QR Code User!", Toast.LENGTH_SHORT);
            toast.show();
            eventId = data.getQueryParameter("event");

        } else {
            eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        }

        ProfanityFilter.init(this);

        bindViews();

        btnClose.setOnClickListener(v -> finish());


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

        setupActionBar();
    }

    /**
     * Associate all of the XML components with their application counterparts
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
        btnShowInvitesDashboard = findViewById(R.id.btn_show_invites_dashboard);

        btnCommentIcon = findViewById(R.id.btn_comment_icon);
        btnShareIcon = findViewById(R.id.btn_share_icon);
        btnSaveIcon = findViewById(R.id.btn_save_icon);
        btnQRCodeIcon = findViewById(R.id.btn_qr_code_icon);
        tvViewComments = findViewById(R.id.tv_view_comments);

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

    private void setupActionBar() {
        View.OnClickListener openComments = v -> {
            Intent intent = new Intent(this, CommentsActivity.class);
            intent.putExtra(CommentsActivity.EXTRA_EVENT_ID, eventId);
            startActivity(intent);
        };

        btnCommentIcon.setOnClickListener(openComments);
        tvViewComments.setOnClickListener(openComments);

        btnShareIcon.setOnClickListener(v -> {
            // TODO: Implement share functionality
            Toast.makeText(this, "Share functionality coming soon!", Toast.LENGTH_SHORT).show();
        });

        btnSaveIcon.setOnClickListener(v -> {
            // TODO: Implement save functionality
            Toast.makeText(this, "Save functionality coming soon!", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Loads the current event from storage and updates the UI.
     * actions set according to User role (entrant, organizer or admin)
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
                    } else if (isOrganizer(currentEvent, currentUserId)) {
                        setupQrCode();
                        setupOrganizerActions(currentEvent);
                    } else {
                        setupEntrantActions(currentEvent);
                    }

                    LinearLayout listOfX = findViewById(R.id.list_of_x_container);
                    TextView tvEntrants = findViewById(R.id.tv_list_of_entrants);
                    if (isOrganizer(event, currentUserId)) {
                        listOfX.setVisibility(VISIBLE);
                        Event.EventStatus status = event.getStatus();
                        if (status == REG_OPEN || status == REG_FULL || status == REG_CLOSED) {
                            listWaitlisted(eventId);
                        } else if (status == EVENT_OPEN || status == EVENT_FULL) {
                            listEntrants(eventId);
                            // showInvitesDashboard
                        } else if (status == EVENT_CLOSED) {
                            tvEntrants.setText("Final Entrants");
                            listEntrants(eventId);
                            //showInvitesDashboard
                        } else {
                            //showInvitesDashboard
                        }

                    } else {
                        listOfX.setVisibility(GONE);
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
    private boolean isOrganizer(Event event, String uid) {
        return event.getOrganizerId().equals(uid);
    }

    private void openInvitesDashboard() {
        Intent intent = new Intent(this, InvitesDashboardActivity.class);
        intent.putExtra(InvitesDashboardActivity.EXTRA_EVENT_ID, eventId);
        startActivity(intent);
    }

    private void renderEntrants(LinearLayout linearLayout, TextView textView, List<Entrant> entrants) {
        if (entrants == null || entrants.isEmpty()) {
            linearLayout.setVisibility(GONE);
            textView.setVisibility(GONE);
            return;
        }

        Log.d("RenderEntrants", "Size of Entrants: " + entrants.size());

        LayoutInflater inflater = getLayoutInflater();
        for (Entrant entrant : entrants) {
            View row = inflater.inflate(R.layout.item_entrant, linearLayout, false);

            TextView entrantName = row.findViewById(R.id.tv_entrant_name);
            TextView entrantEmail = row.findViewById(R.id.tv_entrant_email);

            ServiceLocator.getUserStorage().getUserProfile(
                    entrant.getEntrantId(),
                    user -> {
                        String name = user.getName();
                        entrantName.setText(Objects.requireNonNullElse(name, "UNKNOWN NAME :("));

                        String email = user.getEmail();
                        entrantEmail.setText(Objects.requireNonNullElse(email, "UNKNOWN EMAIL :("));
                    },
                    e -> {
                    }
            );

            linearLayout.addView(row);
        }
    }

    private void listEntrants(String eventId) {
        LinearLayout enrolledEntrants = findViewById(R.id.list_of_entrants);
        TextView tvEntrants = findViewById(R.id.tv_list_of_entrants);
        tvEntrants.setText("Entrants enrolled in event:");

        eventPoolStorage.getEnrolledEntrants(
                eventId,
                entrants -> {
                    renderEntrants(enrolledEntrants, tvEntrants, entrants);
                },
                e -> {
                    Log.e("EventDetailsActivity", "Failed to get enrolled entrants");
                    e.printStackTrace();
                }
        );
    }

    private void listWaitlisted(String eventId) {
        LinearLayout waitlistedEntrants = findViewById(R.id.list_of_waitlisted);
        TextView tvWaitlisted = findViewById(R.id.tv_list_of_waitlisted);
        tvWaitlisted.setText("Entrants enrolled in waitlist:");

        eventPoolStorage.getWaitlistedEntrants(
                eventId,
                entrants -> {
                    renderEntrants(waitlistedEntrants, tvWaitlisted, entrants);
                },
                e -> {
                    Log.e("EventDetailsActivity", "Failed to get waitlisted entrants");
                    e.printStackTrace();
                }
        );
    }

    public static String getTimeAgo(long postedMs) {
        long nowMs = System.currentTimeMillis();
        long diffMs = nowMs - postedMs;

        if (diffMs < 0) {
            return "Just now";
        }

        long seconds = diffMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (seconds < 60) return "Just now";
        if (minutes < 60) return minutes + " min ago";
        if (hours < 24) return hours + " hr ago";
        if (days < 7) return days + " day" + (days == 1 ? "" : "s") + " ago";

        long weeks = days / 7;
        if (weeks < 5) return weeks + " week" + (weeks == 1 ? "" : "s") + " ago";

        long months = days / 30;
        if (months < 12) return months + " month" + (months == 1 ? "" : "s") + " ago";

        long years = days / 365;
        return years + " year" + (years == 1 ? "" : "s") + " ago";
    }


    /**
     * Configures the UI and actions available in admin mode.
     * hides all buttons except
     * button to access admin actions which include:
     * remove event button
     * delete image button
     */
    private void setupAdminActions() {
        if (!isAdminMode) {
            return;
        }

        btnRemoveEvent.setVisibility(VISIBLE);
        btnRemoveEvent.setEnabled(true);
        btnJoin.setEnabled(false);
        btnJoin.setVisibility(GONE);
        btnViewEventMap.setVisibility(GONE);
        layoutAdminInfo.setVisibility(VISIBLE);
        btnDeleteImage.setVisibility(VISIBLE);

        btnRemoveEvent.setOnClickListener(v -> {
            eventStorage.deleteEvent(eventId);
            Toast.makeText(this, "Event Removed", Toast.LENGTH_SHORT).show();
            finish();
        });

        btnDeleteImage.setOnClickListener(v -> {
            eventStorage.getEvent(eventId, currentEvent -> {
                currentEvent.setPosterUrl(null);
                eventStorage.upsertEvent(
                        currentEvent,
                        unused -> {},
                        e -> Log.e("EventDetailsActivity:adminMode", "Failed to upsert event", e)
                );
                Toast.makeText(this, "Image Deleted", Toast.LENGTH_SHORT).show();
                ivEventPoster.setImageResource(R.drawable.ic_image_placeholder);
                btnDeleteImage.setVisibility(GONE);
            }, e -> Toast.makeText(this, "Failed to delete image", Toast.LENGTH_SHORT).show());
        });
    }

    /**
     * Configures the UI and actions available in organizer mode.
     * hides all buttons other than
     * button to begin the lottery selection
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
        btnShowInvitesDashboard.setVisibility(View.GONE);

        boolean registrationClosed = event.getStatus() == REG_CLOSED;
        boolean registrationFull = event.getStatus() == REG_FULL;

        boolean lotteryAvailable = registrationClosed || registrationFull;

        boolean hasSentInvites = event.hasDrawnLottery();

        if (lotteryAvailable && !hasSentInvites) {
            btnBeginLotterySelection.setVisibility(View.VISIBLE);
            btnBeginLotterySelection.setEnabled(true);
            btnBeginLotterySelection.setOnClickListener(v -> beginLotterySelection(event));
        }

        if (hasSentInvites) {
            if (!(event.getStatus() == EVENT_CLOSED)) {
                btnBeginLotterySelection.setText("Re-draw applicants");
                btnBeginLotterySelection.setVisibility(View.VISIBLE);
                btnBeginLotterySelection.setEnabled(true);
                btnBeginLotterySelection.setOnClickListener(v -> beginLotterySelection(event));

                btnShowInvitesDashboard.setVisibility(View.VISIBLE);
                btnShowInvitesDashboard.setEnabled(true);
                btnShowInvitesDashboard.setOnClickListener(v -> openInvitesDashboard());
            } else {
                btnBeginLotterySelection.setVisibility(View.GONE);
                btnBeginLotterySelection.setEnabled(false);
                btnShowInvitesDashboard.setVisibility(View.VISIBLE);
                btnShowInvitesDashboard.setEnabled(true);
                btnShowInvitesDashboard.setOnClickListener(v -> openInvitesDashboard());
            }
        }
    }

    /**
     * Configures the UI and actions available in user mode.
     * call EventPoolStorage.getEntrantStatus() query for entrant status
     * call to renderEntrantActions for state-based action selection
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
     * NONE: join waitlist
     * WAITLISTED: leave waitlist
     * INVITED: accept/decline invitation
     * DECLINED: join button closed -> cannot join again
     * ENROLLED: unenroll
     */
    private void renderEntrantActions(Event event, Entrant.EntrantStatus status) {
        btnJoin.setVisibility(View.GONE);
        btnLeave.setVisibility(View.GONE);
        invitations_layout.setVisibility(View.GONE);
        btnBeginLotterySelection.setVisibility(View.GONE);
        btnViewEventMap.setVisibility(View.GONE);

        if (status == Entrant.EntrantStatus.WAITLISTED) {
            btnLeave.setVisibility(VISIBLE);
            btnLeave.setEnabled(true);
            btnLeave.setOnClickListener(v -> leaveWaitlist());
            return;
        }

        if (status == Entrant.EntrantStatus.INVITED) {
            invitations_layout.setVisibility(VISIBLE);
            btnAccept.setOnClickListener(v -> acceptInvitation());
            btnDecline.setOnClickListener(v -> declineInvitation());
            return;
        }

        if (status == ENROLLED) {
            btnJoin.setVisibility(VISIBLE);
            btnJoin.setEnabled(true);
            btnJoin.setText("Unenroll");
            btnJoin.setOnClickListener(v -> unenroll());
            return;
        }

        if (status == DECLINED) {
            showJoinDisabled("Invitation Declined");
            return;
        }

        if (!event.isRegistrationOpen()) {
            showJoinDisabled("Registration Closed");
            return;
        }

        if (eventStorage.eventHasUsableGeoConstraint(event)) {
            renderGeoCheckedJoinButton(event);
            return;
        }

        btnJoin.setVisibility(VISIBLE);
        btnJoin.setEnabled(true);
        btnJoin.setText("Join Waitlist");
        btnJoin.setOnClickListener(v -> joinWaitlist());
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
     * needs Event object parameter
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
     * to the user.
     */
    private void showJoinDisabled(String text) {
        showButtonDisabled(btnJoin, text);
        btnLeave.setVisibility(GONE);
        invitations_layout.setVisibility(GONE);
    }

    /**
     * Generic disabled-state helper used by join and other action buttons.
     */
    private void showButtonDisabled(MaterialButton button, String text) {
        button.setVisibility(VISIBLE);
        button.setEnabled(false);
        button.setText(text);
    }

    /**
     * Resolves whether the current user can join this event based on the user's selected address
     * mode and the event's geolocation radius.
     */
    private void renderGeoCheckedJoinButton(Event event) {
        btnJoin.setVisibility(VISIBLE);
        btnJoin.setEnabled(false);
        btnJoin.setText("Checking Waitlist Radius...");

        resolveBestAddressForCurrentUser(address -> {
            if (eventStorage.isWithinEventRadius(address, event)) {
                btnJoin.setEnabled(true);
                btnJoin.setText("Join Waitlist");
                btnJoin.setOnClickListener(v -> joinWaitlist());
            } else {
                showButtonDisabled(btnJoin, "Outside of Event Waitlist Radius");
            }
        }, e -> showButtonDisabled(btnJoin, "Outside of Event Waitlist Radius"));
    }

    /**
     * Adds the current user to the event waitlist, NONE -> WAITLISTED and added to "waitlisted" collection.
     * Geo-constrained events are revalidated immediately before the waitlist write occurs.
     */
    private void joinWaitlist() {
        if (currentEvent != null && eventStorage.eventHasUsableGeoConstraint(currentEvent)) {
            btnJoin.setEnabled(false);

            resolveBestAddressForCurrentUser(address -> {
                if (!eventStorage.isWithinEventRadius(address, currentEvent)) {
                    showButtonDisabled(btnJoin, "Outside of Event Waitlist Radius");
                    return;
                }
                waitlistCurrentUser();
            }, e -> showButtonDisabled(btnJoin, "Outside of Event Waitlist Radius"));
            return;
        }

        waitlistCurrentUser();
    }

    /**
     * Removes the current user from the event waitlist, WAITLISTED -> NONE and removed from "waitlist" colleciton
     * EventPoolStorage for db query
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
     * Performs the actual waitlist write after all eligibility checks have passed.
     */
    private void waitlistCurrentUser() {
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
     * Resolves the user's best available address for geo checks and joined_map snapshots.
     * The selected address mode is tried first, then the opposite mode is used as a fallback.
     */
    private void resolveBestAddressForCurrentUser(
            com.google.android.gms.tasks.OnSuccessListener<com.example.lotteryapp.models.UserAddress> onSuccess,
            com.google.android.gms.tasks.OnFailureListener onFailure
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
                            onSuccess.onSuccess(address);
                            return;
                        }

                        loadAddressForJoinedMap(fallbackMode, fallbackAddress -> {
                            if (isUsableJoinedMapAddress(fallbackAddress)) {
                                onSuccess.onSuccess(fallbackAddress);
                            } else {
                                onFailure.onFailure(new IllegalStateException("No usable address found for geo check"));
                            }
                        }, onFailure);
                    }, e -> loadAddressForJoinedMap(fallbackMode, fallbackAddress -> {
                        if (isUsableJoinedMapAddress(fallbackAddress)) {
                            onSuccess.onSuccess(fallbackAddress);
                        } else {
                            onFailure.onFailure(new IllegalStateException("No usable address found for geo check"));
                        }
                    }, onFailure));
                },
                onFailure
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
        resolveBestAddressForCurrentUser(
                address -> writeJoinedMap(address, onSuccess, onFailure),
                e -> {
                    Log.e(MY_TAG, "Failed to resolve a usable address for joined_map upsert", e);
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
     * EventPoolStorage for db query
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
     * added to "declined" collection, removed from "invited" collection
     * EventPoolStorage for db query
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
     * EventPoolStorage for db query
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
     * adds all selected entrants to the "invited" subcollection
     * status marked INVITED
     */
    private void beginLotterySelection(Event event) {
        if (event == null) {
            Toast.makeText(this, "Event not loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean firstDraw = !event.hasDrawnLottery();

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

        if (firstDraw) {
            event.setHasDrawnLottery(true);
            currentEvent = event;
            setupOrganizerActions(event); // immediate UI update

            eventStorage.upsertEvent(
                    event,
                    unused -> runLotteryDraw(event, remainingSpots),
                    e -> {
                        Toast.makeText(this, "Failed to save lottery state", Toast.LENGTH_SHORT).show();
                        Log.e(MY_TAG, "Failed to persist hasDrawnLottery: " + e.getMessage(), e);
                        if (btnBeginLotterySelection != null) {
                            btnBeginLotterySelection.setEnabled(true);
                        }
                    }
            );
        } else {
            runLotteryDraw(event, remainingSpots);
        }
    }

    private void runLotteryDraw(Event event, int remainingSpots) {
        eventPoolStorage.drawWinners(
                eventId,
                remainingSpots,
                invitedCount -> {
                    Toast.makeText(this, "Lottery complete: sent " + invitedCount + " invite(s)", Toast.LENGTH_SHORT).show();

                    currentEvent = event;
                    setupOrganizerActions(event);

                    if (btnBeginLotterySelection != null) {
                        btnBeginLotterySelection.setEnabled(true);
                    }

                    loadEvent();
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

    private void setupQrCode() {
        qrCode = new QRCode(eventId);
        Bitmap qrCodeBitMap = qrCode.getBitmap();

        btnQRCodeIcon.setVisibility(VISIBLE);
        btnQRCodeIcon.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
            View view = LayoutInflater.from(v.getContext()).inflate(R.layout.dialog_qr_code, null);
            builder.setView(view);

            AlertDialog dialog = builder.create();
            dialog.show();

            ImageView imgQrCode = view.findViewById(R.id.img_event_qr_code);

            ImageButton btnCloseQrDialog = view.findViewById(R.id.btn_close_qr_dialog);
            btnCloseQrDialog.setOnClickListener(v1 -> {
                dialog.dismiss();
            });

            Button btnDownloadQrCode = view.findViewById(R.id.btn_download_qr_code);
            btnDownloadQrCode.setOnClickListener(v1 -> {

                Runnable downloadAction = () -> {
                    Intent downloadIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    // filter to only show openable items.
                    downloadIntent.addCategory(Intent.CATEGORY_OPENABLE);

                    // Create a file with the requested Mime type
                    downloadIntent.setType("image/png");
                    downloadIntent.putExtra(Intent.EXTRA_TITLE, "EVENT-" + eventId + "-QR-CODE.png");
                    resultLauncher.launch(downloadIntent);
                };

                // Run download action on separate thread as to not
                // slow down the current UI thread
                Thread downloadThread = new Thread(downloadAction);
                downloadThread.start();

                dialog.dismiss();
            });

            Glide
                    .with(v.getContext())
                    .load(qrCodeBitMap)
                    .centerCrop()
                    .placeholder(R.drawable.ic_image_placeholder)
                    .into(imgQrCode);

            dialog.show();
        });
    }
}