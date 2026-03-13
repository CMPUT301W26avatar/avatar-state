package com.example.lotteryapp.fragments;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;

import com.example.lotteryapp.R;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.activities.EventDetailsActivity;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.services.storage.EventStorage;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ManageFragment displays an event card, a list of managed events, and a create event button.
 *      event card yet to be implemented
 *      populates a list of events hosted by the user (as organizer)
 *          each event in the list is it's own special item with its own UI elements
 *              Enrolled List -> EnrolledListActivity
 *              Details -> EventDetailsActivity
 *              Pencil Icon -> launches dialog for updating event details
 *      create button launches a dialog for creating an event
 */

public class ManageFragment extends Fragment {
    //private Integer waitlistLimit = -1; // sentinel for unlimited waitlist capacity
    private Long startDateMs;
    private Long endDateMs;
    private Long eventDateMs;

    private LinearLayout upcomingEventListContainer;
    private EventStorage eventStorage;

    /**
     * Inflates the fragment layout and set up UI elements
     *      organizer event cards
     *      organizer events list
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manage, container, false);

        eventStorage = ServiceLocator.getEventStorage();
        upcomingEventListContainer = view.findViewById(R.id.layout_upcoming_event_item);

        FloatingActionButton fab = view.findViewById(R.id.fab_add_event);
        fab.setOnClickListener(v -> showCreateEventDialog());

        loadUpcomingEvents();

        return view;
    }

    /**
     * Reloads event lists whenever the fragment comes into view again.
     *      ensures the dashboard reflects the most recent event(s)
     */
    @Override
    public void onResume() {
        super.onResume();
        loadUpcomingEvents();
    }

    /**
     * Populate the list event adapters with user hosted events (as organizer).
     *      call helper renderUpcomingEvents to:
     *          call EventStorage.listEventsRegOpen query to fill events list
     *          add Event to list
     *          notify change in the list of grid events
     *
     * *right now* just calls a simple events by organizer query despite the naming convention and UI elements
     */
    private void loadUpcomingEvents() {
        String organizerId = ServiceLocator.uid();
        if (organizerId == null || organizerId.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Organizer not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        upcomingEventListContainer.removeAllViews();

        eventStorage.getEventsByOrganizer(
                organizerId,
                this::renderUpcomingEvents,
                e -> Toast.makeText(requireContext(), "Failed to load organizer events", Toast.LENGTH_SHORT).show()
        );
    }

    /**
     * helper for accessing the db and updating UI components with the db query
     *      call EventStorage.listEventsRegOpen query to fill events list
     *      add Event to list
     *      notify change in the list of grid events
     */
    private void renderUpcomingEvents(List<Event> events) {
        upcomingEventListContainer.removeAllViews();

        if (events == null || events.isEmpty()) {
            TextView emptyView = new TextView(requireContext());
            emptyView.setText("No organizer events yet.");
            upcomingEventListContainer.addView(emptyView);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (Event event : events) {
            View row = inflater.inflate(R.layout.managed_item_upcoming, upcomingEventListContainer, false);

            TextView tvTitle = row.findViewById(R.id.tv_event_title);
            TextView tvSubtitle = row.findViewById(R.id.tv_event_subtitle);
            View btnDetails = row.findViewById(R.id.btn_event_details);
            View btnEdit = row.findViewById(R.id.btn_edit_event);

            String title = event.getTitle();
            tvTitle.setText(title != null && !title.trim().isEmpty() ? title : event.getEventId());

            tvSubtitle.setText(buildSubtitle(event));

            btnDetails.setOnClickListener(v -> openEventDetails(event));
            btnEdit.setOnClickListener(v -> showUpdateEventDialog(event));

            upcomingEventListContainer.addView(row);
        }
    }

    /**
     * Status-based string builder for DisplayEventGrid subtitles
     *      need Event param
     */
    private String buildSubtitle(Event event) {
        String status = event.getStatus().toString();
        StringBuilder sb = new StringBuilder(status);
        if (status.equals("OPEN")) {
            sb.append(" | Capacity: ").append(event.getEventCapacity());
        } else if (status.equals("CLOSED")) {
            int waitlistCap = event.getWaitlistCapacity();
            if (waitlistCap > 0) {
                sb.append(" | Waitlist: ").append(event.getWaitlistCapacity());
            }
        }
        return sb.toString();
    }

    /**
     * logic that should follow the Details button press on each event item in list
     *      launch EventDetailsActivity
     */
    private void openEventDetails(Event event) {
        if (event == null || event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            Toast.makeText(requireContext(), "Missing event ID", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(requireContext(), EventDetailsActivity.class);
        intent.putExtra(EventDetailsActivity.EXTRA_EVENT_ID, event.getEventId());
        startActivity(intent);
    }

    /**
     * Displays a full-screen dialog for creating a new event
     *      enter event detail fields
     *      mandatory: event capacity, title, reg start/end and event date
     *      optional: description, location, waitlist capacity, geolocational data
     *
     *      can also clear all fields
     */
    private void showCreateEventDialog() {
        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_event, null);
        dialog.setContentView(view);

        EditText etEventName = view.findViewById(R.id.et_event_name);
        EditText etDescription = view.findViewById(R.id.et_description);
        EditText etDate = view.findViewById(R.id.et_date);
        EditText etLocation = view.findViewById(R.id.et_location);
        EditText etRegDates = view.findViewById(R.id.et_reg_dates);
        EditText etEventCapacity = view.findViewById(R.id.et_event_capacity);
        EditText etWaitlistCapacity = view.findViewById(R.id.et_waitlist_capacity);

        View layoutWaitlistCapacity = view.findViewById(R.id.layout_waitlist_capacity);
        MaterialSwitch switchWaitlist = view.findViewById(R.id.switch_waitlist);

        View btnClose = view.findViewById(R.id.toolbar); // Toolbar handles navigation click if configured, but let's be explicit if needed

        // Date of Event Picker
        etDate.setOnClickListener(v -> {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Event Date")
                    .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                eventDateMs = selection;
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                etDate.setText(sdf.format(new Date(selection)));
            });
            datePicker.show(getParentFragmentManager(), "DATE_PICKER");
        });

        // Registration Start/End Range Picker
        etRegDates.setOnClickListener(v -> {
            MaterialDatePicker<Pair<Long, Long>> rangePicker = MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText("Select Registration Range")
                    .build();

            rangePicker.addOnPositiveButtonClickListener(selection -> {
                if (selection == null || selection.first == null || selection.second == null) {
                    return;
                }
                // save dates in millisecond for easy comparison
                startDateMs = selection.first;
                endDateMs = selection.second;

                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
                String startDate = sdf.format(new Date(selection.first));
                String endDate = sdf.format(new Date(selection.second));
                etRegDates.setText(startDate + " - " + endDate);
            });
            rangePicker.show(getParentFragmentManager(), "RANGE_PICKER");
        });

        switchWaitlist.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutWaitlistCapacity.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (!isChecked) {
                etWaitlistCapacity.setText("");
            }
        });

        // Toolbar Close
        androidx.appcompat.widget.Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> dialog.dismiss());

        view.findViewById(R.id.btn_clear_all).setOnClickListener(v -> {
            // Logic to clear fields
            etEventName.setText("");
            etDescription.setText("");
            etLocation.setText("");
            etEventCapacity.setText("");
            etWaitlistCapacity.setText("");
            //Clear waitlist
            switchWaitlist.setChecked(false);
            layoutWaitlistCapacity.setVisibility(View.GONE);
            etDate.setText("");
            etRegDates.setText("");
            etEventCapacity.setText("");

            eventDateMs = null;
            startDateMs = null;
            endDateMs = null;
            //NOTE: need to implement clearing image
            //edit organizers
            ((EditText)view.findViewById(R.id.et_organizers)).setText("");
        });

        view.findViewById(R.id.btn_create_event).setOnClickListener(v -> {
            String title = etEventName.getText().toString().trim();
            String description = etDescription.getText().toString().trim();
            String location = etLocation.getText().toString().trim();
            String capacityText = etEventCapacity.getText().toString().trim();
            String waitlistText = etWaitlistCapacity.getText().toString().trim();
            boolean waitlistHasLimit = switchWaitlist.isChecked();

            // input entry enforcement

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Title is required", Toast.LENGTH_SHORT).show();
                return;
            }
            int eventCapacity = Integer.parseInt(capacityText);

            if (eventCapacity <= 0) {
                Toast.makeText(requireContext(), "Capacity must be greater than 0", Toast.LENGTH_SHORT).show();
                return;
            }

            int waitlistCapacity = Integer.parseInt(waitlistText);

            if (waitlistCapacity <= 0) {
                Toast.makeText(requireContext(), "Capacity must be greater than 0", Toast.LENGTH_SHORT).show();
                return;
            }

            if (startDateMs == null || endDateMs == null) {
                Toast.makeText(requireContext(), "Registration period is required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (startDateMs >= endDateMs) {
                Toast.makeText(requireContext(), "End date must be after start date", Toast.LENGTH_SHORT).show();
                return;
            }

            if (endDateMs > eventDateMs) {
                Toast.makeText(requireContext(), "End date must be before the event date", Toast.LENGTH_SHORT).show();
                return;
            }

            String organizerId = ServiceLocator.uid();
            Log.d("Create Event", "OrganizerID: " + organizerId);

            if (organizerId == null || organizerId.trim().isEmpty()) {
                Toast.makeText(requireContext(), "Organizer not signed in", Toast.LENGTH_SHORT).show();
                return;
            }

            // call database connector
            EventStorage eventStorage = ServiceLocator.getEventStorage();

            Event event = new Event(organizerId, eventCapacity, waitlistCapacity);

            event.setTitle(title);
            event.setEventDateMs(eventDateMs);
            event.setRegStartMs(startDateMs);
            event.setRegEndMs(endDateMs);
            event.setEventCapacity(eventCapacity);
            event.setWaitlistCapacity(waitlistCapacity);
            event.setInvitationCount(0);
            String criteriaGuidelines = getString(R.string.criteriaGuidelines);
            event.setCriteriaGuidelines(criteriaGuidelines);

            if (!description.isEmpty()) {
                setOptionalString(event, "setDescription", description);
            }

            if (!location.isEmpty()) {
                setOptionalString(event, "setLocation", location);
            }

            long now = System.currentTimeMillis();
            if (now < startDateMs) {
                event.setStatus(Event.EventStatus.REG_UPCOMING);
            } else if (now <= endDateMs) {
                event.setStatus(Event.EventStatus.REG_OPEN);
            } else if (now < eventDateMs) {
                event.setStatus(Event.EventStatus.REG_CLOSED);
            } else {
                event.setStatus(Event.EventStatus.EVENT_CLOSED);
            }

            eventStorage.upsertEvent(event);

            Toast.makeText(requireContext(), "Event created!", Toast.LENGTH_SHORT).show();

            dialog.dismiss();
        });
        dialog.show();
    }



    /**
     * Displays a full-screen dialog for updating an existing event
     *      enter event detail fields
     *      mandatory: event capacity, title, reg start/end and event date (saved from create)
     *      optional: description, location, waitlist capacity, geolocational data
     *      unable to: edit event date, or reg start/end
     *
     *      can also clear all fields
     */
    private void showUpdateEventDialog(Event event) {
        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_update_event, null);
        dialog.setContentView(view);

        EditText etEventName = view.findViewById(R.id.et_event_name);
        EditText etDescription = view.findViewById(R.id.et_description);
        EditText etLocation = view.findViewById(R.id.et_location);
        EditText etEventCapacity = view.findViewById(R.id.et_event_capacity);
        EditText etWaitlistCapacity = view.findViewById(R.id.et_waitlist_capacity);
        EditText etOrganizers = view.findViewById(R.id.et_organizers);

        View layoutWaitlistCapacity = view.findViewById(R.id.layout_waitlist_capacity);
        MaterialSwitch switchWaitlist = view.findViewById(R.id.switch_waitlist);

        androidx.appcompat.widget.Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> dialog.dismiss());

        // Pre-populate fields immediately
        etEventName.setText(event.getTitle());
        etDescription.setText(event.getDescription());
        etLocation.setText(event.getLocation());
        etEventCapacity.setText(String.valueOf(event.getEventCapacity()));
        etOrganizers.setText(String.valueOf(event.getOrganizerId()));
        //NOTE: Implement image

        if (event.getWaitlistCapacity() > 0) {
            switchWaitlist.setChecked(true);
            layoutWaitlistCapacity.setVisibility(View.VISIBLE);
            etWaitlistCapacity.setText(String.valueOf(event.getWaitlistCapacity()));
        } else {
            switchWaitlist.setChecked(false);
            layoutWaitlistCapacity.setVisibility(View.GONE);
            etWaitlistCapacity.setText("");
        }

        // Waitlist toggle
        switchWaitlist.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutWaitlistCapacity.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (!isChecked) {
                etWaitlistCapacity.setText("");
            }
        });

        // Clear all
        view.findViewById(R.id.btn_clear_all).setOnClickListener(v -> {
            etEventName.setText("");
            etDescription.setText("");
            etLocation.setText("");
            etEventCapacity.setText("");
            etWaitlistCapacity.setText("");

            switchWaitlist.setChecked(false);
            layoutWaitlistCapacity.setVisibility(View.GONE);

            //NOTE: implement clearing image

            eventDateMs = null;
            startDateMs = null;
            endDateMs = null;
        });

        // Update button
        view.findViewById(R.id.btn_update_event).setOnClickListener(v -> {
            String title = etEventName.getText().toString().trim();
            String description = etDescription.getText().toString().trim();
            String location = etLocation.getText().toString().trim();
            String capacityText = etEventCapacity.getText().toString().trim();
            boolean waitlistEnabled = switchWaitlist.isChecked();

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Title is required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (capacityText.isEmpty()) {
                Toast.makeText(requireContext(), "Event capacity is required", Toast.LENGTH_SHORT).show();
                return;
            }

            int eventCapacity;
            try {
                eventCapacity = Integer.parseInt(capacityText);
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "Capacity must be a valid number", Toast.LENGTH_SHORT).show();
                return;
            }

            if (eventCapacity <= 0) {
                Toast.makeText(requireContext(), "Capacity must be greater than 0", Toast.LENGTH_SHORT).show();
                return;
            }

            Integer waitlistCapacity = null;
            if (waitlistEnabled) {
                String waitlistText = etWaitlistCapacity.getText().toString().trim();

                if (waitlistText.isEmpty()) {
                    Toast.makeText(requireContext(), "Waitlist capacity is required when waitlist is enabled", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    waitlistCapacity = Integer.parseInt(waitlistText);
                } catch (NumberFormatException e) {
                    Toast.makeText(requireContext(), "Waitlist capacity must be a valid number", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (waitlistCapacity < 0) {
                    Toast.makeText(requireContext(), "Waitlist capacity must be 0 or greater", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            String organizerId = ServiceLocator.uid();
            if (organizerId == null || organizerId.trim().isEmpty()) {
                Toast.makeText(requireContext(), "Organizer not signed in", Toast.LENGTH_SHORT).show();
                return;
            }

            EventStorage eventStorage = ServiceLocator.getEventStorage();

            // Update the existing event object
            event.setTitle(title);
            event.setEventCapacity(eventCapacity);
            event.setWaitlistCapacity(waitlistCapacity);
            if (!description.isEmpty()) {
                setOptionalString(event, "setDescription", description);
            } else {
                setOptionalString(event, "setDescription", "");
            }
            if (!location.isEmpty()) {
                setOptionalString(event, "setLocation", location);
            } else {
                setOptionalString(event, "setLocation", "");
            }

            // upsert to database
            eventStorage.upsertEvent(event);

            Toast.makeText(requireContext(), "Event updated!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialog.show();
    }

    /**
     * helper for parsing fields where input is optional
    */
    private void setOptionalString(Event event, String methodName, String value) {
        try {
            java.lang.reflect.Method method = event.getClass().getMethod(methodName, String.class);
            method.invoke(event, value);
        } catch (Exception ignored) {
        }
    }
}