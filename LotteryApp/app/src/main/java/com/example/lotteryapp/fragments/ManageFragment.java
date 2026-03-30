package com.example.lotteryapp.fragments;

import android.app.Dialog;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;

import com.example.lotteryapp.R;
import com.example.lotteryapp.models.Entrant;
import com.example.lotteryapp.models.User;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.activities.EventDetailsActivity;
import com.example.lotteryapp.activities.EnrolledListActivity;
import com.example.lotteryapp.activities.InvitedListActivity;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.models.EventAddress;
import com.example.lotteryapp.services.storage.EventPoolStorage;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventStorage;
import com.example.lotteryapp.services.storage.UserStorage;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ManageFragment displays a list of organizer-managed events and supports
 * creating/updating events. Location resolution uses Android Geocoder
 * (no API key required).
 */
public class ManageFragment extends Fragment {

    private static final int UNLIMITED_WAITLIST_SENTINEL = 2147483647;

    private LinearLayout upcomingEventListContainer;
    private EventStorage eventStorage;

    /**
     * Inflates the fragment layout and set up UI elements
     *      organizer event cards
     *      organizer events list
     */
    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
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
            View btnEnrolled = row.findViewById(R.id.btn_view_enrolled);
            View btnInvited = row.findViewById(R.id.btn_view_invited);

            String title = event.getTitle();
            tvTitle.setText(title != null && !title.trim().isEmpty() ? title : event.getEventId());
            tvSubtitle.setText(buildSubtitle(event));

            btnDetails.setOnClickListener(v -> openEventDetails(event));
            btnEdit.setOnClickListener(v ->
                    eventStorage.getEvent(
                            event.getEventId(),
                            this::showUpdateEventDialog,
                            e -> Toast.makeText(requireContext(), "Failed to load event", Toast.LENGTH_SHORT).show()
                    )
            );

            btnEnrolled.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), EnrolledListActivity.class);
                intent.putExtra(EnrolledListActivity.EXTRA_EVENT_ID, event.getEventId());
                startActivity(intent);
            });

            btnInvited.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), InvitedListActivity.class);
                intent.putExtra(InvitedListActivity.EXTRA_EVENT_ID, event.getEventId());
                startActivity(intent);
            });

            upcomingEventListContainer.addView(row);
        }
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
        EditText etLocationRadiusKm = view.findViewById(R.id.et_location_radius_km);

        View layoutWaitlistCapacity = view.findViewById(R.id.layout_waitlist_capacity);
        MaterialSwitch switchWaitlist = view.findViewById(R.id.switch_waitlist);

        View layoutLocationRadius = view.findViewById(R.id.layout_location_radius);
        MaterialSwitch switchGeo = view.findViewById(R.id.switch_geolocation);

        // use dialog-local holders to keep a fresh state
        //  prevent CreateEventDialog and UpdateEventDialog from leaking into eachother
        final Long[] localEventDateMs = {null};
        final Long[] localStartDateMs = {null};
        final Long[] localEndDateMs = {null};

        // resolved results of the user inputted string for etLocation
        final Double[] resolvedLat = {null};
        final Double[] resolvedLng = {null};
        final String[] resolvedLocation = {null};

        // prevent location TextWatcher from clearing resolved values during setText() calls
        final boolean[] suppressLocationWatcher = {false};

        attachLocationWatcher(etLocation, resolvedLat, resolvedLng, resolvedLocation, suppressLocationWatcher);

        // datePicker returns values for dates at UTC midnight, if we store these and then
        //  display them in a local time zone, then dates will be one day behind
        //  call formatLocalDate to convert this utc time to noon.
        etDate.setOnClickListener(v -> {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Event Date")
                    .setSelection(dateSelectionForPicker(localEventDateMs[0]))
                    .build();

            // convert to noon, noon is within the UTC -> MT threshold
            datePicker.addOnPositiveButtonClickListener(selection -> {
                localEventDateMs[0] = utcEpochMsToLocalDate(selection);
                etDate.setText(formatLocalDate(localEventDateMs[0]));
            });

            datePicker.show(getParentFragmentManager(), "DATE_PICKER");
        });

        // same as above
        etRegDates.setOnClickListener(v -> {
            Pair<Long, Long> currentRange = new Pair<>(
                    dateSelectionForPicker(localStartDateMs[0]),
                    dateSelectionForPicker(localEndDateMs[0])
            );

            MaterialDatePicker<Pair<Long, Long>> rangePicker = MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText("Select Registration Range")
                    .setSelection(currentRange)
                    .build();

            rangePicker.addOnPositiveButtonClickListener(selection -> {
                if (selection == null || selection.first == null || selection.second == null) {
                    return;
                }

                localStartDateMs[0] = utcEpochMsToLocalDate(selection.first);
                localEndDateMs[0] = utcEpochMsToLocalDate(selection.second);

                etRegDates.setText(formatLocalDate(localStartDateMs[0]) + " - " + formatLocalDate(localEndDateMs[0]));
            });

            rangePicker.show(getParentFragmentManager(), "RANGE_PICKER");
        });

        // set waitlist capacity toggle listener
        switchWaitlist.setOnCheckedChangeListener((buttonView, isWaitlistChecked) -> {
            layoutWaitlistCapacity.setVisibility(isWaitlistChecked ? View.VISIBLE : View.GONE);
            if (!isWaitlistChecked) {
                etWaitlistCapacity.setText("");
            }
        });

        // set radius toggle listener
        switchGeo.setOnCheckedChangeListener((buttonView, isGeoChecked) -> {
            layoutLocationRadius.setVisibility(isGeoChecked ? View.VISIBLE : View.GONE);
            if (!isGeoChecked) {
                etLocationRadiusKm.setText("");
            }
        });

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> dialog.dismiss());

        // set clear all button to clear all fields
        View btnClearAll = view.findViewById(R.id.btn_clear_all);
        if (btnClearAll != null) {
            btnClearAll.setOnClickListener(v -> {
                etEventName.setText("");
                etDescription.setText("");

                suppressLocationWatcher[0] = true;
                etLocation.setText("");
                suppressLocationWatcher[0] = false;

                etEventCapacity.setText("");
                etWaitlistCapacity.setText("");
                etDate.setText("");
                etRegDates.setText("");

                switchWaitlist.setChecked(false);
                layoutWaitlistCapacity.setVisibility(View.GONE);

                switchGeo.setChecked(false);
                layoutLocationRadius.setVisibility(View.GONE);

                resolvedLat[0] = null;
                resolvedLng[0] = null;
                resolvedLocation[0] = null;

                localEventDateMs[0] = null;
                localStartDateMs[0] = null;
                localEndDateMs[0] = null;

                EditText organizers = view.findViewById(R.id.et_organizers);
                if (organizers != null) {
                    organizers.setText("");
                }
            });
        }

        // get values from EditText views
        view.findViewById(R.id.btn_create_event).setOnClickListener(v -> {
            String title = etEventName.getText().toString().trim();
            String description = etDescription.getText().toString().trim();
            String locationInput = etLocation.getText().toString().trim();
            String capacityText = etEventCapacity.getText().toString().trim();
            String radiusText = etLocationRadiusKm.getText().toString().trim();
            boolean waitlistHasLimit = switchWaitlist.isChecked();
            boolean geoConstraint = switchGeo.isChecked();

            // input entry enforcement

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Title is required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (capacityText.isEmpty()) {
                Toast.makeText(requireContext(), "Event capacity is required", Toast.LENGTH_SHORT).show();
                return;
            }

            final int eventCapacity;
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

            // default: unlimited
            int waitlistCapacity = UNLIMITED_WAITLIST_SENTINEL;
            if (waitlistHasLimit) {
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

                if (waitlistCapacity <= 0) {
                    Toast.makeText(requireContext(), "Waitlist capacity must be greater than 0", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // when geolocation restriction is enabled, require both a location and a radius
            // radius is stored inside the geo/address document, not on the main event doc
            final Integer locationRadiusKm;
            if (geoConstraint) {
                if (locationInput.isEmpty()) {
                    Toast.makeText(requireContext(), "Location is required when geolocation is enabled", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (radiusText.isEmpty()) {
                    Toast.makeText(requireContext(), "Location radius is required when geolocation is enabled", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    locationRadiusKm = Integer.parseInt(radiusText);
                } catch (NumberFormatException e) {
                    Toast.makeText(requireContext(), "Location radius must be a valid number", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (locationRadiusKm <= 0) {
                    Toast.makeText(requireContext(), "Location radius must be greater than 0", Toast.LENGTH_SHORT).show();
                    return;
                }
            } else {
                // if geo restriction is off, do not persist a radius value
                etLocationRadiusKm.setText("");
                locationRadiusKm = null;
            }

            if (localEventDateMs[0] == null) {
                Toast.makeText(requireContext(), "Event date is required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (localStartDateMs[0] == null || localEndDateMs[0] == null) {
                Toast.makeText(requireContext(), "Registration period is required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (localStartDateMs[0] >= localEndDateMs[0]) {
                Toast.makeText(requireContext(), "End date must be after start date", Toast.LENGTH_SHORT).show();
                return;
            }

            if (localEndDateMs[0] > localEventDateMs[0]) {
                Toast.makeText(requireContext(), "End date must be before the event date", Toast.LENGTH_SHORT).show();
                return;
            }

            String organizerId = ServiceLocator.uid();
            if (organizerId == null || organizerId.trim().isEmpty()) {
                Toast.makeText(requireContext(), "Organizer not signed in", Toast.LENGTH_SHORT).show();
                return;
            }

            final int finalWaitlistCapacity = waitlistCapacity;
            final String finalLocationInput = locationInput;

            // run the save Event pathway as an atomic transaction to avoid geocoder failures being inserted in the database
            Runnable saveAction = () -> {
                Event newEvent = new Event(organizerId, eventCapacity, finalWaitlistCapacity);

                newEvent.setTitle(title);
                newEvent.setEventDateMs(localEventDateMs[0]);
                newEvent.setRegStartMs(localStartDateMs[0]);
                newEvent.setRegEndMs(localEndDateMs[0]);
                newEvent.setEventCapacity(eventCapacity);
                newEvent.setWaitlistCapacity(finalWaitlistCapacity);
                newEvent.setInvitationCount(0);
                newEvent.setHasDrawnLottery(false); // force waitlisting state
                newEvent.setHasGeoConstraint(geoConstraint);

                String criteriaGuidelines = getString(R.string.criteriaGuidelines);
                newEvent.setCriteriaGuidelines(criteriaGuidelines);
                setOptionalString(newEvent, "setDescription", description);

                // only create a geo/address doc when geolocation restriction is enabled
                EventAddress eventAddress = null;
                // save an address doc whenever the user entered a location OR enabled geo
                if (!finalLocationInput.isEmpty() || geoConstraint) {
                    eventAddress = new EventAddress(newEvent.getEventId());

                    if (geoConstraint) {
                        eventAddress.setLocation(resolvedLocation[0] == null ? finalLocationInput : resolvedLocation[0]);
                        eventAddress.setLatitude(resolvedLat[0]);
                        eventAddress.setLongitude(resolvedLng[0]);
                        eventAddress.setRadiusKm(locationRadiusKm);
                    } else {
                        eventAddress.setLocation(resolvedLocation[0] == null ? finalLocationInput : resolvedLocation[0]);
                        eventAddress.setLatitude(resolvedLat[0]);
                        eventAddress.setLongitude(resolvedLng[0]);
                        eventAddress.setRadiusKm(null);
                    }
                }
                newEvent.setAddress(eventAddress);

                long now = System.currentTimeMillis();
                if (now < localStartDateMs[0]) {
                    newEvent.setStatus(Event.EventStatus.REG_UPCOMING);
                } else if (now <= localEndDateMs[0]) {
                    newEvent.setStatus(Event.EventStatus.REG_OPEN);
                } else if (now < localEventDateMs[0]) {
                    newEvent.setStatus(Event.EventStatus.REG_CLOSED);
                } else {
                    newEvent.setStatus(Event.EventStatus.EVENT_CLOSED);
                }

                // eventStorage.upsertEvent got refactored to require onSuccess and onFailure listeners
                eventStorage.upsertEvent(
                        newEvent,
                        unused -> eventStorage.setEventAddress(
                                newEvent.getEventId(),
                                newEvent.getAddress(),
                                unused2 -> {
                                    Toast.makeText(requireContext(), "Event created!", Toast.LENGTH_SHORT).show();
                                    dialog.dismiss();
                                    loadUpcomingEvents();
                                },
                                e -> Toast.makeText(
                                        requireContext(),
                                        "Failed to save event address: " + e.getMessage(),
                                        Toast.LENGTH_LONG
                                ).show()
                        ),
                        e -> Toast.makeText(
                                requireContext(),
                                "Failed to create event: " + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
            };

            if (finalLocationInput.isEmpty()) {
                resolvedLat[0] = null;
                resolvedLng[0] = null;
                resolvedLocation[0] = null;
                saveAction.run();
            } else {
                resolveLocationAsync(finalLocationInput, new ResolveLocationCallback() {
                    @Override
                    public void onResolved(@NonNull String resolvedAddress,
                                           @Nullable Double latitude,
                                           @Nullable Double longitude) {
                        resolvedLocation[0] = resolvedAddress;
                        resolvedLat[0] = latitude;
                        resolvedLng[0] = longitude;
                        saveAction.run();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
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
        EditText etDate = view.findViewById(R.id.et_date);
        EditText etLocation = view.findViewById(R.id.et_location);
        EditText etRegDates = view.findViewById(R.id.et_reg_dates);
        EditText etEventCapacity = view.findViewById(R.id.et_event_capacity);
        EditText etWaitlistCapacity = view.findViewById(R.id.et_waitlist_capacity);
        EditText etLocationRadiusKm = view.findViewById(R.id.et_location_radius_km);


        View layoutWaitlistCapacity = view.findViewById(R.id.layout_waitlist_capacity);
        MaterialSwitch switchWaitlist = view.findViewById(R.id.switch_waitlist);

        View layoutLocationRadius = view.findViewById(R.id.layout_location_radius);
        MaterialSwitch switchGeo = view.findViewById(R.id.switch_geolocation);

        // use dialog-local holders to keep a fresh state
        //  prevent CreateEventDialog and UpdateEventDialog from leaking into eachother
        final Long[] localEventDateMs = {event.getEventDateMs()};
        final Long[] localStartDateMs = {event.getRegStartMs()};
        final Long[] localEndDateMs = {event.getRegEndMs()};

        // read any existing geo/address data so the dialog can be pre-populated
        EventAddress existingAddress = event.getAddress();
        boolean hadExistingGeo = event.hasGeoConstraint();

        if (existingAddress == null) {
            existingAddress = new EventAddress(event.getEventId());
        }

        String initialResolvedLocation = existingAddress.getLocation();
        Double initialResolvedLat = existingAddress.getLatitude();
        Double initialResolvedLng = existingAddress.getLongitude();
        Integer initialRadiusKm = existingAddress.getRadiusKm();

        if (initialResolvedLocation == null) {
            initialResolvedLocation = "";
        }

        // resolved (or existing) results of the user inputted string for etLocation
        final Double[] resolvedLat = {initialResolvedLat};
        final Double[] resolvedLng = {initialResolvedLng};
        final String[] resolvedLocation = {initialResolvedLocation};
        final boolean[] suppressLocationWatcher = {false};

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> dialog.dismiss());

        etEventName.setText(event.getTitle());
        etDescription.setText(event.getDescription());

        // suppress the LocationWatcher from reporting values during entry
        suppressLocationWatcher[0] = true;
        etLocation.setText(resolvedLocation[0]);
        etLocation.setSelection(etLocation.getText().length());
        suppressLocationWatcher[0] = false;

        attachLocationWatcher(etLocation, resolvedLat, resolvedLng, resolvedLocation, suppressLocationWatcher);

        etEventCapacity.setText(String.valueOf(event.getEventCapacity()));

        // prefill geo controls from the existing address subdocument
        switchGeo.setChecked(hadExistingGeo);
        layoutLocationRadius.setVisibility(hadExistingGeo ? View.VISIBLE : View.GONE);

        if (initialRadiusKm != null) {
            etLocationRadiusKm.setText(String.valueOf(initialRadiusKm));
        } else {
            etLocationRadiusKm.setText("");
        }

        if (localEventDateMs[0] != null) {
            etDate.setText(formatLocalDate(localEventDateMs[0]));
        }
        if (localStartDateMs[0] != null && localEndDateMs[0] != null) {
            etRegDates.setText(formatLocalDate(localStartDateMs[0]) + " - " + formatLocalDate(localEndDateMs[0]));
        }

        // hide the waitlist capacity EditText if it was not toggled before
        if (event.getWaitlistCapacity() == UNLIMITED_WAITLIST_SENTINEL) {
            switchWaitlist.setChecked(false);
            layoutWaitlistCapacity.setVisibility(View.GONE);
            etWaitlistCapacity.setText("");
        } else if (event.getWaitlistCapacity() != null && event.getWaitlistCapacity() > 0) {
            switchWaitlist.setChecked(true);
            layoutWaitlistCapacity.setVisibility(View.VISIBLE);
            etWaitlistCapacity.setText(String.valueOf(event.getWaitlistCapacity()));
        } else {
            switchWaitlist.setChecked(false);
            layoutWaitlistCapacity.setVisibility(View.GONE);
            etWaitlistCapacity.setText("");
        }

        // datePicker returns values for dates at UTC midnight, if we store these and then
        //  display them in a local time zone, then dates will be one day behind
        //  call formatLocalDate to convert this utc time to noon.
        etDate.setOnClickListener(v -> {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Event Date")
                    .setSelection(dateSelectionForPicker(localEventDateMs[0]))
                    .build();

            // convert to noon
            datePicker.addOnPositiveButtonClickListener(selection -> {
                localEventDateMs[0] = utcEpochMsToLocalDate(selection);
                etDate.setText(formatLocalDate(localEventDateMs[0]));
            });

            datePicker.show(getParentFragmentManager(), "UPDATE_DATE_PICKER");
        });

        // same as above
        etRegDates.setOnClickListener(v -> {
            Pair<Long, Long> currentRange = new Pair<>(
                    dateSelectionForPicker(localStartDateMs[0]),
                    dateSelectionForPicker(localEndDateMs[0])
            );

            MaterialDatePicker<Pair<Long, Long>> rangePicker = MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText("Select Registration Range")
                    .setSelection(currentRange)
                    .build();

            rangePicker.addOnPositiveButtonClickListener(selection -> {
                if (selection == null || selection.first == null || selection.second == null) {
                    return;
                }

                localStartDateMs[0] = utcEpochMsToLocalDate(selection.first);
                localEndDateMs[0] = utcEpochMsToLocalDate(selection.second);

                etRegDates.setText(formatLocalDate(localStartDateMs[0]) + " - " + formatLocalDate(localEndDateMs[0]));
            });

            rangePicker.show(getParentFragmentManager(), "UPDATE_RANGE_PICKER");
        });

        // set waitlist toggle listener
        switchWaitlist.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutWaitlistCapacity.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (!isChecked) {
                etWaitlistCapacity.setText("");
            }
        });

        // set radius toggle listener
        switchGeo.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutLocationRadius.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (!isChecked) {
                etLocationRadiusKm.setText("");
            }
        });

        // set clear all button to clear all fields
        View btnClearAll = view.findViewById(R.id.btn_clear_all);
        if (btnClearAll != null) {
            btnClearAll.setOnClickListener(v -> {
                etEventName.setText("");
                etDescription.setText("");

                suppressLocationWatcher[0] = true;
                etLocation.setText("");
                suppressLocationWatcher[0] = false;

                etEventCapacity.setText("");
                etWaitlistCapacity.setText("");
                etDate.setText("");
                etRegDates.setText("");
                etLocationRadiusKm.setText("");

                switchWaitlist.setChecked(false);
                layoutWaitlistCapacity.setVisibility(View.GONE);

                switchGeo.setChecked(false);
                layoutLocationRadius.setVisibility(View.GONE);

                resolvedLat[0] = null;
                resolvedLng[0] = null;
                resolvedLocation[0] = null;

                localEventDateMs[0] = null;
                localStartDateMs[0] = null;
                localEndDateMs[0] = null;
            });
        }

        // get values from EditText views
        view.findViewById(R.id.btn_update_event).setOnClickListener(v -> {
                    String title = etEventName.getText().toString().trim();
                    String description = etDescription.getText().toString().trim();
                    String locationInput = etLocation.getText().toString().trim();
                    String capacityText = etEventCapacity.getText().toString().trim();
                    String radiusText = etLocationRadiusKm.getText().toString().trim();
                    boolean waitlistHasLimit = switchWaitlist.isChecked();
                    boolean geoConstraint = switchGeo.isChecked();

                    // input entry enforcement

                    if (title.isEmpty()) {
                        Toast.makeText(requireContext(), "Title is required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (capacityText.isEmpty()) {
                        Toast.makeText(requireContext(), "Event capacity is required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    final int eventCapacity;
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

                    int waitlistCapacity = UNLIMITED_WAITLIST_SENTINEL;
                    if (waitlistHasLimit) {
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

                        if (waitlistCapacity <= 0) {
                            Toast.makeText(requireContext(), "Waitlist capacity must be greater than 0", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }

                    // when geolocation restriction is enabled, require both a location and a radius
                    // radius is persisted on EventAddress under /geo/address
                    final Integer locationRadiusKm;
                    if (geoConstraint) {
                        if (locationInput.isEmpty()) {
                            Toast.makeText(requireContext(), "Location is required when geolocation is enabled", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (radiusText.isEmpty()) {
                            Toast.makeText(requireContext(), "Location radius is required when geolocation is enabled", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        try {
                            locationRadiusKm = Integer.parseInt(radiusText);
                        } catch (NumberFormatException e) {
                            Toast.makeText(requireContext(), "Location radius must be a valid number", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (locationRadiusKm <= 0) {
                            Toast.makeText(requireContext(), "Location radius must be greater than 0", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } else {
                        etLocationRadiusKm.setText("");
                        locationRadiusKm = null;
                    }

                    if (localEventDateMs[0] == null) {
                        Toast.makeText(requireContext(), "Event date is required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (localStartDateMs[0] == null || localEndDateMs[0] == null) {
                        Toast.makeText(requireContext(), "Registration period is required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (localStartDateMs[0] >= localEndDateMs[0]) {
                        Toast.makeText(requireContext(), "End date must be after start date", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (localEndDateMs[0] > localEventDateMs[0]) {
                        Toast.makeText(requireContext(), "End date must be before the event date", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    final int finalWaitlistCapacity = waitlistCapacity;
                    final String finalLocationInput = locationInput;

                    // run as one atomic transaction
                    // eventStorage.upsertEvent got refactored to require onSuccess and onFailure listeners
                    Runnable saveAction = () -> {
                        event.setTitle(title);
                        event.setEventDateMs(localEventDateMs[0]);
                        event.setRegStartMs(localStartDateMs[0]);
                        event.setRegEndMs(localEndDateMs[0]);
                        event.setEventCapacity(eventCapacity);
                        event.setWaitlistCapacity(finalWaitlistCapacity);

                        setOptionalString(event, "setDescription", description);

                        // store geo settings only in the geo/address subdocument
                        EventAddress updatedAddress = event.getAddress();
                        if (updatedAddress == null) {
                            updatedAddress = new EventAddress(event.getEventId());
                        }

                        if (geoConstraint) {
                            updatedAddress.setLocation(resolvedLocation[0] == null ? finalLocationInput : resolvedLocation[0]);
                            updatedAddress.setLatitude(resolvedLat[0]);
                            updatedAddress.setLongitude(resolvedLng[0]);
                            updatedAddress.setRadiusKm(locationRadiusKm);
                        } else {
                            updatedAddress.setLocation(resolvedLocation[0] == null ? finalLocationInput : resolvedLocation[0]);
                            updatedAddress.setLatitude(resolvedLat[0]);
                            updatedAddress.setLongitude(resolvedLng[0]);
                            updatedAddress.setRadiusKm(null);
                        }

                        event.setHasGeoConstraint(geoConstraint);
                        event.setAddress(updatedAddress);

                        eventStorage.upsertEvent(
                                event,
                                unused -> eventStorage.setEventAddress(
                                        event.getEventId(),
                                        event.getAddress(),
                                        unused2 -> {
                                            Toast.makeText(requireContext(), "Event updated!", Toast.LENGTH_SHORT).show();
                                            dialog.dismiss();
                                            loadUpcomingEvents();
                                        },
                                        e -> Toast.makeText(
                                                requireContext(),
                                                "Failed to save event address: " + e.getMessage(),
                                                Toast.LENGTH_LONG
                                        ).show()
                                ),
                                e -> Toast.makeText(
                                        requireContext(),
                                        "Failed to update event: " + e.getMessage(),
                                        Toast.LENGTH_LONG
                                ).show()
                        );
                    };

                    String currentDisplayedLocation = etLocation.getText().toString().trim();

                    boolean locationUnchanged =
                            !currentDisplayedLocation.isEmpty()
                                    && resolvedLocation[0] != null
                                    && currentDisplayedLocation.equals(resolvedLocation[0]);

                    if (geoConstraint && finalLocationInput.isEmpty()) {
                        Toast.makeText(requireContext(), "Location is required when geolocation is enabled", Toast.LENGTH_SHORT).show();
                    } else if (finalLocationInput.isEmpty()) {
                        resolvedLat[0] = null;
                        resolvedLng[0] = null;
                        resolvedLocation[0] = null;
                        saveAction.run();
                    } else if (locationUnchanged && resolvedLat[0] != null && resolvedLng[0] != null) {
                        saveAction.run();
                    } else {
                        resolveLocationAsync(finalLocationInput, new ResolveLocationCallback() {
                            @Override
                            public void onResolved(@NonNull String resolvedAddress,
                                                   @Nullable Double latitude,
                                                   @Nullable Double longitude) {
                                resolvedLocation[0] = resolvedAddress;
                                resolvedLat[0] = latitude;
                                resolvedLng[0] = longitude;
                                saveAction.run();
                            }

                            @Override
                            public void onError(@NonNull String message) {
                                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });

        dialog.show();
    }

    /**
     * Attaches a watcher to the location field that invalidates stored resolved coordinates
     *  whenever the user edits the raw text
     *  - Suppressing this via flag prevents setText() calls from in-app from being registered
     *      as user inputs
     */
    private void attachLocationWatcher(
            EditText etLocation,
            Double[] resolvedLat,
            Double[] resolvedLng,
            String[] resolvedLocation,
            boolean[] suppressWatcher
    ) {
        etLocation.addTextChangedListener(new TextWatcher() {
            private String previous = etLocation.getText() == null ? "" : etLocation.getText().toString();

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                previous = s == null ? "" : s.toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (suppressWatcher[0]) {
                    return;
                }

                String current = s == null ? "" : s.toString();
                if (!current.equals(previous)) {
                    resolvedLat[0] = null;
                    resolvedLng[0] = null;
                    resolvedLocation[0] = null;
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // &none
            }
        });
    }

    /**
     * Callback for resolving a text address into a displayable address and coordinates.
     */
    private interface ResolveLocationCallback {
        // handles a successful address resolution
        void onResolved(@NonNull String resolvedLocation,
                        @Nullable Double latitude,
                        @Nullable Double longitude);

        // handles a failed resolution
        void onError(@NonNull String message);
    }

    /**
     * Callback for resolving a text address into a displayable address and coordinates.
     * - runs on background thread to not interfere with the application thread
     */
    private void resolveLocationAsync(
            @NonNull String addressText,
            @NonNull ResolveLocationCallback callback
    ) {
        if (!Geocoder.isPresent()) {
            callback.onError("Geocoder is not available on this device");
            return;
        }

        // prevent conflicts between the two running processes (app, geocoder)
        //  allows for async callback to be done in one atomic transaction
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
                List<Address> results = geocoder.getFromLocationName(addressText, 1);

                if (!isAdded()) {
                    return;
                }

                // merge back to main thread with call back added success
                requireActivity().runOnUiThread(() -> {
                    if (results == null || results.isEmpty()) {
                        callback.onError("Could not resolve location");
                        return;
                    }

                    // merge back to main thread with location resolved success
                    Address address = results.get(0);
                    callback.onResolved(
                            buildResolvedAddress(address, addressText),
                            address.getLatitude(),
                            address.getLongitude()
                    );
                });

                // merge back to main thread with failure
            } catch (IOException e) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() ->
                        callback.onError("Failed to resolve location")
                );
            } catch (Exception e) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() ->
                        callback.onError("Unexpected geocoder error")
                );
            }
        }).start();
    }

    /**
     * Returns a resolved display string built from an Address.
     * - needs a non-null android Address and a fallback string if no readable fields exist
     */
    @NonNull
    private String buildResolvedAddress(@NonNull Address address, @NonNull String fallback) {
        StringBuilder resolved = new StringBuilder();

        // Depending on precise the geocoder match was,
        //  some fields may be null, or we may get some unneeded fields
        //  build a nice display string from only what matters from the geocoder callback

        if (address.getFeatureName() != null && !address.getFeatureName().isEmpty()) {
            resolved.append(address.getFeatureName());
        }
        if (address.getThoroughfare() != null && !address.getThoroughfare().isEmpty()) {
            if (resolved.length() > 0) resolved.append(", ");
            resolved.append(address.getThoroughfare());
        }
        if (address.getLocality() != null && !address.getLocality().isEmpty()) {
            if (resolved.length() > 0) resolved.append(", ");
            resolved.append(address.getLocality());
        }
        if (address.getAdminArea() != null && !address.getAdminArea().isEmpty()) {
            if (resolved.length() > 0) resolved.append(", ");
            resolved.append(address.getAdminArea());
        }
        if (address.getCountryName() != null && !address.getCountryName().isEmpty()) {
            if (resolved.length() > 0) resolved.append(", ");
            resolved.append(address.getCountryName());
        }

        return resolved.length() > 0 ? resolved.toString() : fallback;
    }

    /**
     * Converts a MaterialDatePicker UTC day selection into a local-noon epoch value.
     *
     * Why local noon:
     *      storing picker UTC midnight directly can display as the previous day
     *      in time zones behind UTC
     *      local noon safely preserves the intended calendar day for UI use
     */
    private long utcEpochMsToLocalDate(long pickerUtcMs) {
        Calendar utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utcCal.setTimeInMillis(pickerUtcMs);

        int year = utcCal.get(Calendar.YEAR);
        int month = utcCal.get(Calendar.MONTH);
        int day = utcCal.get(Calendar.DAY_OF_MONTH);

        Calendar localCal = Calendar.getInstance();
        localCal.clear();
        localCal.set(Calendar.YEAR, year);
        localCal.set(Calendar.MONTH, month);
        localCal.set(Calendar.DAY_OF_MONTH, day);
        localCal.set(Calendar.HOUR_OF_DAY, 12);
        localCal.set(Calendar.MINUTE, 0);
        localCal.set(Calendar.SECOND, 0);
        localCal.set(Calendar.MILLISECOND, 0);

        return localCal.getTimeInMillis();
    }

    private long dateSelectionForPicker(@Nullable Long storedLocalMs) {
        if (storedLocalMs == null) {
            return MaterialDatePicker.todayInUtcMilliseconds();
        }

        Calendar localCal = Calendar.getInstance();
        localCal.setTimeInMillis(storedLocalMs);

        int year = localCal.get(Calendar.YEAR);
        int month = localCal.get(Calendar.MONTH);
        int day = localCal.get(Calendar.DAY_OF_MONTH);

        Calendar utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utcCal.clear();
        utcCal.set(Calendar.YEAR, year);
        utcCal.set(Calendar.MONTH, month);
        utcCal.set(Calendar.DAY_OF_MONTH, day);
        utcCal.set(Calendar.HOUR_OF_DAY, 0);
        utcCal.set(Calendar.MINUTE, 0);
        utcCal.set(Calendar.SECOND, 0);
        utcCal.set(Calendar.MILLISECOND, 0);

        return utcCal.getTimeInMillis();
    }

    /**
     * helper for formatting epoch ms value into local date
     */
    @NonNull
    private String formatLocalDate(@Nullable Long localMs) {
        if (localMs == null) {
            return "";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        return sdf.format(new Date(localMs));
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

    /**
     * Status-based string builder for DisplayEventGrid subtitles
     */
    private String buildSubtitle(Event event) {
        Event.EventStatus status = event.getStatus();
        StringBuilder sb = new StringBuilder(statusString(status));

        Integer waitlistCap = event.getWaitlistCapacity();
        if (waitlistCap != null && waitlistCap == UNLIMITED_WAITLIST_SENTINEL) {
            sb.append("\nWaitlist: ")
                    .append(event.getWaitlistCount())
                    .append("/∞");
        } else if (waitlistCap != null && waitlistCap > 0) {
            sb.append("\nWaitlist: ")
                    .append(event.getWaitlistCount())
                    .append("/")
                    .append(waitlistCap);
        }

        return sb.toString();
    }

    /**
     * Subtitle builder helper for status logic
     */
    private String statusString(Event.EventStatus status) {
        if (status == null) {
            return "Unknown";
        }

        switch (status) {
            case REG_OPEN:
                return "Registration started";
            case REG_CLOSED:
                return "Registration closed";
            case REG_FULL:
                return "Waitlist full";
            case REG_UPCOMING:
                return "Upcoming";
            case EVENT_CLOSED:
                return "Finished";
            case EVENT_OPEN:
                return "Invitations Sent";
            case EVENT_FULL:
                return "Event is full";
            default:
                return "Unknown";
        }
    }
}
