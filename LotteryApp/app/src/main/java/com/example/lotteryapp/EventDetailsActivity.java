package com.example.lotteryapp;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class EventDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "eventId";
    public static final String EXTRA_ORGANIZER_ID = "organizerId";

    private MaterialTextView tvName;
    private MaterialTextView tvLocation;
    private MaterialTextView tvDescription;
    private MaterialTextView tvDate;
    private MaterialTextView tvRegEndDate;
    private MaterialButton btnClose;
    private MaterialButton btnJoin;

    private String eventId;
    private String currentUserId;

    private EventStorage eventStorage;
    private EventPoolStorage eventPoolStorage;

    private boolean isEnrolled = false;

    private final SimpleDateFormat sdf =
            new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_details);

        bindViews();

        btnClose.setOnClickListener(v -> finish());

        // Keep support for the new adapter's temporary extras,
        // but prefer the real backend eventId when present.
        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);

        if (eventId == null || eventId.trim().isEmpty()) {
            // Fallback display-only mode for older callers that still pass UI text extras only.
            populateFromIntentExtras();
            btnJoin.setEnabled(false);
            btnJoin.setText("Event unavailable");
            Toast.makeText(this, "Missing eventId", Toast.LENGTH_SHORT).show();
            return;
        }

        currentUserId = ServiceLocator.uid();
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            populateFromIntentExtras();
            btnJoin.setEnabled(false);
            btnJoin.setText("Not signed in");
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        eventStorage = ServiceLocator.eventStorage();
        eventPoolStorage = ServiceLocator.eventPoolStorage();

        // Optional: prefill any passed UI extras while backend data loads
        populateFromIntentExtras();

        loadEvent();
    }

    private void bindViews() {
        tvName = findViewById(R.id.tv_event_name);
        tvLocation = findViewById(R.id.tv_location);
        tvDescription = findViewById(R.id.tv_description);
        tvDate = findViewById(R.id.tv_event_date);
        tvRegEndDate = findViewById(R.id.tv_reg_end_date);
        btnClose = findViewById(R.id.btn_close);
        btnJoin = findViewById(R.id.btn_join_waitlist);
    }

    /**
     * Keeps compatibility with your current adapter/navigation while backend wiring is added.
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
    }

    private void loadEvent() {
        eventStorage.getEvent(
                eventId,
                event -> {
                    populateInfo(event);
                    configureActions(event);
                },
                e -> Toast.makeText(this, "Failed to load event", Toast.LENGTH_SHORT).show()
        );
    }

    private void populateInfo(Event event) {
        if (event == null) {
            Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Prefer backend values over intent extras
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

        // Use registration end in the bottom metadata field
        if (event.getRegEndMs() != null) {
            tvRegEndDate.setText("Registration Ends: " + sdf.format(event.getRegEndMs()));
        } else {
            tvRegEndDate.setText("Registration end unavailable");
        }
    }

    private void configureActions(Event event) {
        if (event == null) {
            btnJoin.setEnabled(false);
            btnJoin.setText("Event unavailable");
            return;
        }

        if (!event.isRegistrationOpen()) {
            btnJoin.setEnabled(false);
            btnJoin.setText("Registration Closed");
            return;
        }

        btnJoin.setEnabled(true);
        refreshEnrollmentState(event.getEventId());
    }

    private void refreshEnrollmentState(String eventId) {
        eventPoolStorage.getEntrantStatus(
                eventId,
                currentUserId,
                status -> {
                    isEnrolled = Entrant.EntrantStatus.ENROLLED.name().equals(status);
                    updateJoinButton();
                },
                e -> {
                    isEnrolled = false;
                    updateJoinButton();
                }
        );
    }

    /**
     * Adapts old enroll/unenroll logic onto the single fixed bottom button in the new XML.
     */
    private void updateJoinButton() {
        btnJoin.setEnabled(true);
        btnJoin.setText(isEnrolled ? "Unenroll" : "Join Event Waitlist");

        btnJoin.setOnClickListener(v -> {
            if (isEnrolled) {
                unenroll();
            } else {
                enroll();
            }
        });
    }

    private void enroll() {
        Entrant entrant = new Entrant(currentUserId, eventId, Entrant.EntrantStatus.ENROLLED);

        btnJoin.setEnabled(false);

        eventPoolStorage.enrollInEvent(
                eventId,
                entrant,
                unused -> {
                    Toast.makeText(this, "Enrolled!", Toast.LENGTH_SHORT).show();
                    isEnrolled = true;
                    updateJoinButton();
                },
                e -> {
                    Toast.makeText(this, "Failed to enroll", Toast.LENGTH_SHORT).show();
                    btnJoin.setEnabled(true);
                }
        );
    }

    private void unenroll() {
        btnJoin.setEnabled(false);

        eventPoolStorage.deleteEntry(
                eventId,
                currentUserId,
                unused -> {
                    Toast.makeText(this, "Unenrolled!", Toast.LENGTH_SHORT).show();
                    isEnrolled = false;
                    updateJoinButton();
                },
                e -> {
                    Toast.makeText(this, "Failed to unenroll", Toast.LENGTH_SHORT).show();
                    btnJoin.setEnabled(true);
                }
        );
    }
}