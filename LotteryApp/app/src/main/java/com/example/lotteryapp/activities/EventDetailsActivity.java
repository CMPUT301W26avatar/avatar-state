package com.example.lotteryapp.activities;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static androidx.core.content.ContentProviderCompat.requireContext;
import static com.example.lotteryapp.models.Entrant.EntrantStatus.DECLINED;
import static com.example.lotteryapp.models.Entrant.EntrantStatus.ENROLLED;
import static com.example.lotteryapp.models.Entrant.EntrantStatus.WAITLISTED;

import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.lotteryapp.R;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.models.Entrant;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.services.storage.EventPoolStorage;
import com.example.lotteryapp.services.storage.EventStorage;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class EventDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "eventId";

    private LinearLayout invitations_layout;
    private MaterialTextView tvName;
    private static final String MY_TAG = "InvitationDebug";
    private MaterialTextView tvLocation;
    private MaterialTextView tvDescription;
    private MaterialTextView tvDate;
    private MaterialTextView tvRegEndDate;
    private MaterialTextView tvCriteriaGuidelines;
    private MaterialButton btnClose;
    private MaterialButton btnJoin;
    private MaterialButton btnLeave;
    private MaterialButton btnAccept;
    private MaterialButton btnDecline;
    private MaterialButton btnDeleteImage;
    private MaterialButton btnRemoveEvent;
    private MaterialButton btnBeginLotterySelection;
    private ImageView ivEventPoster;

    // Admin Tech Details Views
    private View layoutAdminInfo;
    private TextView tvAdminEventId, tvAdminOrganizerId, tvAdminStatus, tvAdminCapacity,
            tvAdminWaitlistCap, tvAdminEnrolled, tvAdminWaitlistCount, tvAdminRegStart, tvAdminPosterUrl;

    private String eventId;
    private String currentUserId;
    private boolean isAdminMode = false;
    private Event currentEvent;

    private EventStorage eventStorage;
    private EventPoolStorage eventPoolStorage;
    private Entrant.EntrantStatus currentStatus = null;

    private final SimpleDateFormat sdf =
            new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

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
        eventStorage = ServiceLocator.getEventStorage();
        eventPoolStorage = ServiceLocator.getEventPoolStorage();

        // prefill any passed UI extras while backend data loads
        populateFromIntentExtras();
        loadEvent();
    }

    // associate all xml components to their application counterparts
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

    // render/load event to the screen
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
                    } else {
                        setupEntrantActions(currentEvent);
                    }

                    LinearLayout listOfX = findViewById(R.id.list_of_x_container);
                    listOfX.setVisibility(VISIBLE);
                    if (isOrganizer(event)) {
                        listOfX.setVisibility(VISIBLE);

                        listEntrants(eventId);
                        listWaitlisted(eventId);
                        listCancelled(eventId);
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

    // load event helper: returns a bool for if the current user is the event organizer inside of the Event lambda
    private boolean isOrganizer(Event event) {
        return event.getOrganizerId().equals(currentUserId);
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
                    e -> {}
            );

            linearLayout.addView(row);
        }
    }

    private void listEntrants(String eventId) {
        LinearLayout enrolledEntrants = findViewById(R.id.list_of_entrants);
        TextView tvEntrants = findViewById(R.id.tv_list_of_entrants);
        tvEntrants.setText("Current Entrants");

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
        tvWaitlisted.setText("Waitlisted Entrants");

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
    private void listCancelled(String eventId) {
        LinearLayout cancelledEntrants = findViewById(R.id.list_of_cancelled);
        TextView tvCancelled = findViewById(R.id.tv_list_of_cancelled);
        tvCancelled.setText("Cancelled Entrants");

        eventPoolStorage.getDeclinedEntrants(
                eventId,
                entrants -> {
                    renderEntrants(cancelledEntrants, tvCancelled, entrants);
                },
                e -> {
                    Log.e("EventDetailsActivity", "Failed to get declined entrants");
                    e.printStackTrace();
                }
        );
    }

    /**
     * Checks if user is admin
     *      shows button to access admin actions
     *      delete event button
     *      delete image button
     */
    private void setupAdminActions() {
        // Easier to read, not as indented
        if (!isAdminMode) {
            return;
        }

        btnRemoveEvent.setVisibility(VISIBLE);
        btnRemoveEvent.setEnabled(true);
        btnJoin.setEnabled(false);
        btnJoin.setVisibility(GONE);
        layoutAdminInfo.setVisibility(VISIBLE);
        btnDeleteImage.setVisibility(VISIBLE);

        btnRemoveEvent.setOnClickListener(v -> {
            eventStorage.deleteEvent(eventId);
            Toast.makeText(this, "Event Removed", Toast.LENGTH_SHORT).show();
            finish();
        });

        btnDeleteImage.setOnClickListener(v -> {
            eventStorage.getEvent(eventId, event -> {
                event.setPosterUrl(null);
                eventStorage.upsertEvent(event);
                Toast.makeText(this, "Image Deleted", Toast.LENGTH_SHORT).show();
                ivEventPoster.setImageResource(R.drawable.ic_image_placeholder);
                btnDeleteImage.setVisibility(GONE);
            }, e -> Toast.makeText(this, "Failed to delete image", Toast.LENGTH_SHORT).show());
        });
    }

    /**
     * Checks if user is entrant



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

    private void renderEntrantActions(Event event, Entrant.EntrantStatus status) {
        btnJoin.setVisibility(GONE);
        btnLeave.setVisibility(GONE);
        invitations_layout.setVisibility(GONE);

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
            // cannot join waitlist, or event again
            showJoinDisabled("Invitation Declined");
            return;
        }

        if (event.isRegistrationOpen()) {
            btnJoin.setVisibility(VISIBLE);
            btnJoin.setEnabled(true);
            btnJoin.setText("Join Waitlist");
            btnJoin.setOnClickListener(v -> joinWaitlist());
        } else {
            showJoinDisabled("Registration Closed");
        }
    }

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

        if (event.getLocation() != null && !event.getLocation().trim().isEmpty()) {
            tvLocation.setText(event.getLocation());
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
            btnDeleteImage.setVisibility(VISIBLE);
        }
        else {
            btnDeleteImage.setVisibility(GONE);
        }

        if (isAdminMode) {
            populateAdminTechDetails(event);
        }
    }

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

    private void showJoinDisabled(String text) {
        btnJoin.setVisibility(VISIBLE);
        btnJoin.setEnabled(false);
        btnJoin.setText(text);
        btnLeave.setVisibility(GONE);
        invitations_layout.setVisibility(GONE);
    }



    private void joinWaitlist() {
        //Add user to waitlist collection
        Entrant entrant = new Entrant(currentUserId, eventId, Entrant.EntrantStatus.WAITLISTED);

        btnJoin.setEnabled(false);

        eventPoolStorage.waitlistForEvent(
                eventId,
                entrant,
                unused -> {
                    Toast.makeText(this, "Waitlisted!", Toast.LENGTH_SHORT).show();
                    currentStatus = WAITLISTED;
                    loadEvent();
                },
                e -> {
                    Toast.makeText(this, "Failed to waitlist", Toast.LENGTH_SHORT).show();
                    Log.e(MY_TAG, "Operation failed: " + e.getMessage(), e);
                    btnJoin.setEnabled(true);
                }
        );
    }

    private void leaveWaitlist() {
        btnLeave.setEnabled(false);

        eventPoolStorage.removeFromWaitlist(
                eventId,
                currentUserId,
                unused -> {
                    Toast.makeText(this, "Left waitlist!", Toast.LENGTH_SHORT).show();
                    currentStatus = null;
                    loadEvent();
                },
                e -> {
                    Toast.makeText(this, "Failed to leave waitlist.", Toast.LENGTH_SHORT).show();
                    Log.e(MY_TAG, "Operation failed: " + e.getMessage(), e);
                    btnLeave.setEnabled(true);
                }
        );
    }

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

    private void declineInvitation() {
        btnDecline.setEnabled(false);

        eventPoolStorage.removeFromInvited(
                eventId,
                currentUserId,
                unused -> {
                    Toast.makeText(this, "Invitation declined", Toast.LENGTH_SHORT).show();

                    currentStatus = DECLINED;
                    invitations_layout.setVisibility(GONE);
                    renderEntrantActions(currentEvent, DECLINED);

                    btnDecline.setEnabled(true);
                },
                e -> {
                    Toast.makeText(this, "Failed to decline invitation", Toast.LENGTH_SHORT).show();
                    Log.e(MY_TAG, "Operation failed: " + e.getMessage(), e);
                    btnDecline.setEnabled(true);
                }
        );
    }

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
}