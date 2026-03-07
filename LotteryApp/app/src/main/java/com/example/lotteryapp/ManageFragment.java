package com.example.lotteryapp;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
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
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ManageFragment extends Fragment {
    private int waitlistLimit = -1; // sentinel for unlimited waitlist capacity
    private Long startDateMs;
    private Long endDateMs;
    private Long eventDateMs;

    private LinearLayout upcomingEventListContainer;
    private EventStorage eventStorage;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manage, container, false);

        eventStorage = ServiceLocator.eventStorage();
        upcomingEventListContainer = view.findViewById(R.id.layout_upcoming_event_item);

        FloatingActionButton fab = view.findViewById(R.id.fab_add_event);
        fab.setOnClickListener(v -> showCreateEventDialog());

        loadUpcomingEvents();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUpcomingEvents();
    }

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

    // build Event subtitle as event status, event capacity and waitlist capacity
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

    // launches EventDetailsActivity on details button press
    private void openEventDetails(Event event) {
        if (event == null || event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            Toast.makeText(requireContext(), "Missing event ID", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(requireContext(), EventDetailsActivity.class);
        intent.putExtra(EventDetailsActivity.EXTRA_EVENT_ID, event.getEventId());
        startActivity(intent);
    }

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
            ((EditText)view.findViewById(R.id.et_event_name)).setText("");
            ((EditText)view.findViewById(R.id.et_description)).setText("");
            ((EditText)view.findViewById(R.id.et_location)).setText("");
            etDate.setText("");
            etRegDates.setText("");
            ((EditText)view.findViewById(R.id.et_organizers)).setText("");
        });

        view.findViewById(R.id.btn_create_event).setOnClickListener(v -> {
            String title = etEventName.getText().toString().trim();
            String description = etDescription.getText().toString().trim();
            String location = etLocation.getText().toString().trim();
            String capacityText = etEventCapacity.getText().toString().trim();
            boolean waitlistEnabled = switchWaitlist.isChecked();

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
            if (organizerId == null || organizerId.trim().isEmpty()) {
                Toast.makeText(requireContext(), "Organizer not signed in", Toast.LENGTH_SHORT).show();
                return;
            }

            // call database connector
            EventStorage eventStorage = ServiceLocator.eventStorage();

            Event event = new Event(organizerId, Event.EventStatus.OPEN, eventCapacity);

            event.setTitle(title);
            event.setEventDateMs(eventDateMs);
            event.setRegStartMs(startDateMs);
            event.setRegEndMs(endDateMs);
            event.setEventCapacity(eventCapacity);
            event.setWaitlistCapacity(waitlistLimit);

            if (!description.isEmpty()) {
                setOptionalString(event, "setDescription", description);
            }

            if (!location.isEmpty()) {
                setOptionalString(event, "setLocation", location);
            }

            eventStorage.upsertEvent(event);

            Toast.makeText(requireContext(), "Event created!", Toast.LENGTH_SHORT).show();

            dialog.dismiss();
        });
        dialog.show();
    }


    private void showUpdateEventDialog(Event event) {
        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_update_event, null);
        dialog.setContentView(view);

        EditText etEventName = view.findViewById(R.id.et_event_name);
        EditText etDescription = view.findViewById(R.id.et_description);
        EditText etLocation = view.findViewById(R.id.et_location);
        EditText etEventCapacity = view.findViewById(R.id.et_event_capacity);
        EditText etWaitlistCapacity = view.findViewById(R.id.et_waitlist_capacity);

        View layoutWaitlistCapacity = view.findViewById(R.id.layout_waitlist_capacity);
        MaterialSwitch switchWaitlist = view.findViewById(R.id.switch_waitlist);

        androidx.appcompat.widget.Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> dialog.dismiss());

        // Pre-populate fields immediately
        etEventName.setText(event.getTitle());
        etDescription.setText(event.getDescription());
        etLocation.setText(event.getLocation());


        etEventCapacity.setText(String.valueOf(event.getEventCapacity()));


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

            EventStorage eventStorage = ServiceLocator.eventStorage();

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

            eventStorage.upsertEvent(event);

            Toast.makeText(requireContext(), "Event updated!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

        private void setOptionalString(Event event, String methodName, String value) {
            try {
                java.lang.reflect.Method method = event.getClass().getMethod(methodName, String.class);
                method.invoke(event, value);
            } catch (Exception ignored) {
            }
        }
}