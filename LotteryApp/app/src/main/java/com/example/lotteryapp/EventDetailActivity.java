package com.example.lotteryapp;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class EventDetailActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "eventId";
    public static final String EXTRA_ORGANIZER_ID = "organizerId";

    private TextView tvStatus, tvCapacity, tvWaitlistCapacity,
            tvRegStart, tvRegEnd, tvDrawDate, tvRegClosed;
    private Button btnEnroll, btnBack;

    // ─── State ───────────────────────────────────────────────────────────────

    private String eventId;
    private String currentUserId;

    private EventStorage     eventStorage;
    private EventPoolStorage eventPoolStorage;

    private boolean isEnrolled = false;

    private final SimpleDateFormat sdf =
            new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_detail);

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Missing eventId", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentUserId = ServiceLocator.uid();
        if (currentUserId == null || currentUserId.isEmpty()) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        eventStorage = ServiceLocator.eventStorage();
        eventPoolStorage = ServiceLocator.eventPoolStorage();

        bindViews();
        loadEvent();
    }

    // Attach views to variable helper

    private void bindViews() {
        tvStatus                = findViewById(R.id.tvStatus);
        tvCapacity              = findViewById(R.id.tvCapacity);
        tvWaitlistCapacity      = findViewById(R.id.tvWaitlistCapacity);
        tvRegStart              = findViewById(R.id.tvRegStart);
        tvRegEnd                = findViewById(R.id.tvRegEnd);
        tvDrawDate              = findViewById(R.id.tvDrawDate);
        tvRegClosed             = findViewById(R.id.tvRegClosed);

        btnEnroll               = findViewById(R.id.btnEnroll);
        btnBack                 = findViewById(R.id.btnBack);

        // enroll button toggling back and forth between enroll/unenroll
        View waitlistBtn = findViewById(R.id.btnJoinWaitlist);
        if (waitlistBtn != null) waitlistBtn.setVisibility(View.GONE);

        btnBack.setOnClickListener(v -> finish());

        btnEnroll.setVisibility(View.GONE);
        tvRegClosed.setVisibility(View.GONE);
    }

    // load the event in question into view

    private void loadEvent() {
        eventStorage.getEvent(eventId,
                event -> {
                    populateInfo(event); // populate information fields
                    configureActions(event); // configure the valid actions for the user
                },
                e -> Toast.makeText(this, "Failed to load event", Toast.LENGTH_SHORT).show()
        );
    }

    // load event helper - populate the info fields for metadata tied to the event

    private void populateInfo(Event event) {
        tvStatus.setText(event.getStatus() != null ? event.getStatus().name() : "—");
        tvCapacity.setText("Capacity: " + event.getCapacity());

        if (event.hasWaitlist()) {
            tvWaitlistCapacity.setVisibility(View.VISIBLE);
            tvWaitlistCapacity.setText("Waitlist: " +
                    (event.getWaitlistCapacity() == -1
                            ? "Unlimited"
                            : event.getWaitlistCapacity() + " spots"));
        } else {
            tvWaitlistCapacity.setVisibility(View.GONE);
        }

        tvRegStart.setText("Opens:  " + (event.getRegStart() != null ? sdf.format(event.getRegStart()) : "—"));
        tvRegEnd.setText("Closes: " + (event.getRegEnd() != null ? sdf.format(event.getRegEnd()) : "—"));
        tvDrawDate.setText("Draw:   " + (event.getDrawTime() != null ? sdf.format(event.getDrawTime()) : "—"));
    }

    // load event helper - configure the valid actions a user would be presented with based on the values of the event

    private void configureActions(Event event) {
        if (!event.isRegistrationOpen()) {
            tvRegClosed.setVisibility(View.VISIBLE);
            btnEnroll.setVisibility(View.GONE);
            return;
        }

        tvRegClosed.setVisibility(View.GONE);
        btnEnroll.setVisibility(View.VISIBLE);

        refreshEnrollmentState(event.getEventId());
    }

    // load event helper - refresh state for when toggling back and forth b/w enroll and unenroll
    private void refreshEnrollmentState(String eventId) {
        eventPoolStorage.getEntrantStatus(
                eventId,
                currentUserId,
                status -> {
                    isEnrolled = Entrant.EntrantStatus.ENROLLED.name().equals(status);
                    updateEnrollButton();
                },
                e -> {
                    // If we can't check, default to showing Enroll.
                    isEnrolled = false;
                    updateEnrollButton();
                }
        );
    }

    // load event helper - toggles back and forth b/w enroll and unenroll
    private void updateEnrollButton() {
        btnEnroll.setEnabled(true);
        btnEnroll.setText(isEnrolled ? "Unenroll" : "Enroll");
        btnEnroll.setOnClickListener(v -> {
            if (isEnrolled) {
                unenroll();
            } else {
                enroll();
            }
        });
    }

    // call EventPoolStorage to add a user to the event pool
    private void enroll() {
        Entrant entrant = new Entrant(currentUserId, eventId, Entrant.EntrantStatus.ENROLLED);

        btnEnroll.setEnabled(false);

        eventPoolStorage.enrollInEvent(
                eventId,
                entrant,
                unused -> {
                    Toast.makeText(this, "Enrolled!", Toast.LENGTH_SHORT).show();
                    isEnrolled = true;
                    updateEnrollButton();
                },
                e -> {
                    Toast.makeText(this, "Failed to enroll", Toast.LENGTH_SHORT).show();
                    btnEnroll.setEnabled(true);
                }
        );
    }

    // call EventPoolStorage to remove a user from the event pool
    private void unenroll() {
        btnEnroll.setEnabled(false);

        // Choose ONE behavior:
        // A) hard delete entry doc (matches your original db.delete)
        eventPoolStorage.deleteEntry(
                eventId,
                currentUserId,
                unused -> {
                    Toast.makeText(this, "Unenrolled!", Toast.LENGTH_SHORT).show();
                    isEnrolled = false;
                    updateEnrollButton();
                },
                e -> {
                    Toast.makeText(this, "Failed to unenroll", Toast.LENGTH_SHORT).show();
                    btnEnroll.setEnabled(true);
                }
        );

    }
}