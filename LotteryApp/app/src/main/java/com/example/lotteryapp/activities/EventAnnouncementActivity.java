package com.example.lotteryapp.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.lotteryapp.R;
import com.example.lotteryapp.models.Entrant;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.models.NotificationLog;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventPoolStorage;
import com.example.lotteryapp.services.storage.EventStorage;
import com.example.lotteryapp.services.storage.NotificationLogStorage;

import java.util.ArrayList;
import java.util.List;

/**
 * Organizer-facing activity for sending event announcements
 * to every user currently on the waitlist for an event.
 */
public class EventAnnouncementActivity extends AppCompatActivity {

    private Spinner spinnerTemplate;
    private Spinner spinnerRecipients;
    private EditText etTitle;
    private EditText etMessage;
    private Switch switchUseTemplate;
    private Button btnPreviewTemplate;
    private Button btnSendAnnouncement;
    private Button btnCancelAnnouncement;

    private String eventId;
    private String currentUserId;

    private EventStorage eventStorage;
    private EventPoolStorage eventPoolStorage;
    private NotificationLogStorage notificationLogStorage;

    private Event currentEvent;

    private static final String TEMPLATE_EVENT_STARTS_ONE_DAY = "Event starts in 1 day";
    private static final String TEMPLATE_EVENT_STARTS_ONE_HOUR = "Event starts in 1 hour";
    private static final String TEMPLATE_REG_CLOSES_ONE_DAY = "Registration closes in 1 day";
    private static final String TEMPLATE_REG_CLOSES_ONE_HOUR = "Registration closes in 1 hour";
    private static final String TEMPLATE_GENERIC_UPDATE = "Generic organizer update";

    /**
     * Initializes the activity, validates required launch data, binds views, and loads the event used for template preview generation.
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_announcement);
        // read the event id passed from EventDetailsActivity
        eventId = getIntent().getStringExtra(EventDetailsActivity.EXTRA_EVENT_ID);

        // get user id from ServiceLocator
        currentUserId = ServiceLocator.uid();

        // validate event id
        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "Missing event ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // validate user id
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // get storage classes from ServiceLocator
        eventStorage = ServiceLocator.getEventStorage();
        eventPoolStorage = ServiceLocator.getEventPoolStorage();
        notificationLogStorage = ServiceLocator.getNotificationLogStorage();

        bindViews(); // bind UI components
        setupRecipientsDropDown();
        setupTemplateDropDown();
        setupListeners();
        loadEvent(); // load Event
    }

    /**
     * Binds all layout views used by this activity.
     */
    private void bindViews() {
        spinnerTemplate = findViewById(R.id.spinner_announcement_template);
        etTitle = findViewById(R.id.et_announcement_title);
        etMessage = findViewById(R.id.et_announcement_message);
        switchUseTemplate = findViewById(R.id.switch_use_template);
        btnPreviewTemplate = findViewById(R.id.btn_preview_template);
        btnSendAnnouncement = findViewById(R.id.btn_send_announcement);
        btnCancelAnnouncement = findViewById(R.id.btn_cancel_announcement);
        // spinner to select recipients
        spinnerRecipients = findViewById(R.id.recipientsSpinner);
    }

    /**
     * Populates the template dropdown with the predefined organizer announcement options
     */
    private void setupTemplateDropDown() {
        List<String> templates = new ArrayList<>();
        templates.add(TEMPLATE_EVENT_STARTS_ONE_DAY);
        templates.add(TEMPLATE_EVENT_STARTS_ONE_HOUR);
        templates.add(TEMPLATE_REG_CLOSES_ONE_DAY);
        templates.add(TEMPLATE_REG_CLOSES_ONE_HOUR);
        templates.add(TEMPLATE_GENERIC_UPDATE);

        // Use a simple built-in Android spinner item layout for the template list
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                templates
        );

        // Use the standard dropdown layout when the spinner expands.
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTemplate.setAdapter(adapter);
    }

    private void setupRecipientsDropDown() {
        // create string array adapter
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.notificationRecipients, android.R.layout.simple_spinner_item);
        // set layout of dropdown spinner
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // apply the adapter to the spinner
        spinnerRecipients.setAdapter(adapter);
    }
    /**
     * Attaches all click and state listeners for the activity controls
     */
    private void setupListeners() {
        // Close the activity without sending an announcement
        btnCancelAnnouncement.setOnClickListener(v -> finish());

        // Populate the title/message fields from the selected template
        btnPreviewTemplate.setOnClickListener(v -> applySelectedTemplate());

        // Enable or disable template controls depending on whether template mode is active
        switchUseTemplate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            spinnerTemplate.setEnabled(isChecked);
            btnPreviewTemplate.setEnabled(isChecked);
        });

        // Validate and send the current announcement.
        btnSendAnnouncement.setOnClickListener(v -> sendAnnouncement());
    }

    /**
     * Loads the current event so that template preview text can include the event title
     */
    private void loadEvent() {
        eventStorage.getEvent(
                eventId,
                event -> currentEvent = event, // local store
                e -> {
                    Toast.makeText(this, "Failed to load event", Toast.LENGTH_SHORT).show();
                    finish();
                }
        );
    }
    /**
     * Applies the selected template by filling in the title and message fields with an announcement draft
     */
    private void applySelectedTemplate() {
        // Prevent template application before the event has finished loading
        if (currentEvent == null) {
            Toast.makeText(this, "Event still loading", Toast.LENGTH_SHORT).show();
            return;
        }

        // Retrieve the currently selected template label from the spinner
        String selected = (String) spinnerTemplate.getSelectedItem();
        if (selected == null) {
            return;
        }

        String eventTitle = currentEvent.getTitle() == null ? "your event" : currentEvent.getTitle();

        // Fill the input fields with the template-specific title and message
        switch (selected) {
            case TEMPLATE_EVENT_STARTS_ONE_DAY:
                etTitle.setText("Event starts tomorrow");
                etMessage.setText(eventTitle + " begins in 1 day. Please be ready and watch for updates.");
                break;

            case TEMPLATE_EVENT_STARTS_ONE_HOUR:
                etTitle.setText("Event starts soon");
                etMessage.setText(eventTitle + " begins in 1 hour. Please prepare to attend.");
                break;

            case TEMPLATE_REG_CLOSES_ONE_DAY:
                etTitle.setText("Registration closes tomorrow");
                etMessage.setText("Registration for " + eventTitle + " closes in 1 day.");
                break;

            case TEMPLATE_REG_CLOSES_ONE_HOUR:
                etTitle.setText("Registration closes soon");
                etMessage.setText("Registration for " + eventTitle + " closes in 1 hour.");
                break;

            case TEMPLATE_GENERIC_UPDATE:
            default:
                etTitle.setText("Organizer update");
                etMessage.setText("There is a new update for " + eventTitle + ".");
                break;
        }
    }

    /**
     * Validates the current announcement input, loads the event waitlist, and writes one EVENT_ANNOUNCEMENT notification per waitlisted user.
     */
    private void sendAnnouncement() {
        String title = etTitle.getText() == null ? "" : etTitle.getText().toString().trim();
        String message = etMessage.getText() == null ? "" : etMessage.getText().toString().trim();

        // Require a non-empty title before continuing
        if (TextUtils.isEmpty(title)) {
            etTitle.setError("Title required");
            return;
        }

        // Require a non-empty message before continuing
        if (TextUtils.isEmpty(message)) {
            etMessage.setError("Message required");
            return;
        }

        // Disable the send button to avoid duplicate submissions while the request is running
        btnSendAnnouncement.setEnabled(false);

        String selectedRecipients = (String) spinnerRecipients.getSelectedItem();

        if (selectedRecipients.equals("waitlisted")) {
            // Load all waitlisted entrants for the current event
            eventPoolStorage.getWaitlistedEntrants(
                    eventId,
                    entrants -> {
                        List<String> userIds = new ArrayList<>();

                        // Extract only valid entrant user ids from the waitlist results
                        for (Entrant entrant : entrants) {
                            if (entrant != null && entrant.getEntrantId() != null
                                    && !entrant.getEntrantId().trim().isEmpty()) {
                                userIds.add(entrant.getEntrantId());
                            }
                        }

                        // If nobody is currently waitlisted, there is nobody to notify
                        if (userIds.isEmpty()) {
                            Toast.makeText(this, "No waitlisted users to notify", Toast.LENGTH_SHORT).show();
                            btnSendAnnouncement.setEnabled(true);
                            return;
                        }

                        // Write one notification log entry per waitlisted user
                        notificationLogStorage.logAnnouncementToUsers(
                                eventId,
                                currentUserId,
                                userIds,
                                title,
                                message,
                                NotificationLog.NotificationType.EVENT_ANNOUNCEMENT,
                                sentCount -> {
                                    Toast.makeText(
                                            this,
                                            "Announcement sent to " + userIds.size() + " waitlisted users",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                    finish();
                                },
                                e -> {
                                    Toast.makeText(this, "Failed to send announcement", Toast.LENGTH_SHORT).show();
                                    btnSendAnnouncement.setEnabled(true);
                                }
                        );
                    },
                    e -> {
                        Toast.makeText(this, "Failed to load waitlisted users", Toast.LENGTH_SHORT).show();
                        btnSendAnnouncement.setEnabled(true);
                    }
            );
        } else if (selectedRecipients.equals("invited")) {
            // Load all invited entrants for the current event
            eventPoolStorage.getInvitedEntrants(
                    eventId,
                    entrants -> {
                        List<String> userIds = new ArrayList<>();

                        // Extract only valid entrant user ids from the invited results
                        for (Entrant entrant : entrants) {
                            if (entrant != null && entrant.getEntrantId() != null
                                    && !entrant.getEntrantId().trim().isEmpty()) {
                                userIds.add(entrant.getEntrantId());
                            }
                        }

                        // If nobody is currently invited, there is nobody to notify
                        if (userIds.isEmpty()) {
                            Toast.makeText(this, "No invited users to notify", Toast.LENGTH_SHORT).show();
                            btnSendAnnouncement.setEnabled(true);
                            return;
                        }

                        // Write one notification log entry per invited user
                        notificationLogStorage.logAnnouncementToUsers(
                                eventId,
                                currentUserId,
                                userIds,
                                title,
                                message,
                                NotificationLog.NotificationType.EVENT_ANNOUNCEMENT,
                                sentCount -> {
                                    Toast.makeText(
                                            this,
                                            "Announcement sent to " + userIds.size() + " invited users",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                    finish();
                                },
                                e -> {
                                    Toast.makeText(this, "Failed to send announcement", Toast.LENGTH_SHORT).show();
                                    btnSendAnnouncement.setEnabled(true);
                                }
                        );
                    },
                    e -> {
                        Toast.makeText(this, "Failed to load invited users", Toast.LENGTH_SHORT).show();
                        btnSendAnnouncement.setEnabled(true);
                    }
            );
        }
        else if (selectedRecipients.equals("cancelled")) {
            // Load all cancelled entrants for the current event
            eventPoolStorage.getCancelledEntrants(
                    eventId,
                    entrants -> {
                        List<String> userIds = new ArrayList<>();

                        // Extract only valid entrant user ids from the cancelled results
                        for (Entrant entrant : entrants) {
                            if (entrant != null && entrant.getEntrantId() != null
                                    && !entrant.getEntrantId().trim().isEmpty()) {
                                userIds.add(entrant.getEntrantId());
                            }
                        }

                        // If nobody is currently cancelled, there is nobody to notify
                        if (userIds.isEmpty()) {
                            Toast.makeText(this, "No cancelled users to notify", Toast.LENGTH_SHORT).show();
                            btnSendAnnouncement.setEnabled(true);
                            return;
                        }

                        // Write one notification log entry per cancelled user
                        notificationLogStorage.logAnnouncementToUsers(
                                eventId,
                                currentUserId,
                                userIds,
                                title,
                                message,
                                NotificationLog.NotificationType.EVENT_ANNOUNCEMENT,
                                sentCount -> {
                                    Toast.makeText(
                                            this,
                                            "Announcement sent to " + userIds.size() + " cancelled users",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                    finish();
                                },
                                e -> {
                                    Toast.makeText(this, "Failed to send announcement", Toast.LENGTH_SHORT).show();
                                    btnSendAnnouncement.setEnabled(true);
                                }
                        );
                    },
                    e -> {
                        Toast.makeText(this, "Failed to load cancelled users", Toast.LENGTH_SHORT).show();
                        btnSendAnnouncement.setEnabled(true);
                    }
            );
        }
        else {
            // Load all enrolled entrants for the current event
            eventPoolStorage.getEnrolledEntrants(
                    eventId,
                    entrants -> {
                        List<String> userIds = new ArrayList<>();

                        // Extract only valid entrant user ids from the enrolled results
                        for (Entrant entrant : entrants) {
                            if (entrant != null && entrant.getEntrantId() != null
                                    && !entrant.getEntrantId().trim().isEmpty()) {
                                userIds.add(entrant.getEntrantId());
                            }
                        }

                        // If nobody is currently enrolled, there is nobody to notify
                        if (userIds.isEmpty()) {
                            Toast.makeText(this, "No enrolled users to notify", Toast.LENGTH_SHORT).show();
                            btnSendAnnouncement.setEnabled(true);
                            return;
                        }

                        // Write one notification log entry per enrolled user
                        notificationLogStorage.logAnnouncementToUsers(
                                eventId,
                                currentUserId,
                                userIds,
                                title,
                                message,
                                NotificationLog.NotificationType.EVENT_ANNOUNCEMENT,
                                sentCount -> {
                                    Toast.makeText(
                                            this,
                                            "Announcement sent to " + userIds.size() + " enrolled users",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                    finish();
                                },
                                e -> {
                                    Toast.makeText(this, "Failed to send announcement", Toast.LENGTH_SHORT).show();
                                    btnSendAnnouncement.setEnabled(true);
                                }
                        );
                    },
                    e -> {
                        Toast.makeText(this, "Failed to load enrolled users", Toast.LENGTH_SHORT).show();
                        btnSendAnnouncement.setEnabled(true);
                    }
            );
        }
    }
}