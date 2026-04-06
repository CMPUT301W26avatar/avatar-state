package com.example.lotteryapp.fragments;

import android.app.Dialog;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.lotteryapp.R;
import com.example.lotteryapp.activities.EventDetailsActivity;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.activities.EnrolledListActivity;
import com.example.lotteryapp.activities.InvitedListActivity;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.models.EventAddress;
import com.example.lotteryapp.services.storage.EventPoolStorage;
import com.example.lotteryapp.services.storage.EventStorage;
import com.example.lotteryapp.services.storage.UserStorage;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.storage.StorageReference;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.lang.reflect.Method;

/**
 * ManageFragment displays a list of organizer-managed events and supports
 * creating/updating events. Location resolution uses Android Geocoder
 * (no API key required).
 */
public class ManageFragment extends Fragment {

    private static final int UNLIMITED_WAITLIST_SENTINEL = 2147483647;

    private LinearLayout upcomingEventListContainer;
    private EventStorage eventStorage;

    // Image picking state
    private Uri selectedImageUri;
    private ImageView currentPreviewImage;
    private View currentRemovePosterButton;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    if (currentPreviewImage != null) {
                        currentPreviewImage.setImageURI(uri);
                        currentPreviewImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        currentPreviewImage.setImageTintList(null); // Clear placeholder tint
                    }
                    if (currentRemovePosterButton != null) {
                        currentRemovePosterButton.setVisibility(View.VISIBLE);
                    }
                }
            });

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
     * *right now* just calls a simple events by organizer query despite the naming convention and UI elements
     */
    private void loadUpcomingEvents() {
        String organizerId = ServiceLocator.uid();
        if (organizerId == null || organizerId.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Organizer not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        upcomingEventListContainer.removeAllViews();

        eventStorage.getManagedEventsForUser(
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
        if (!isAdded()) return;
        upcomingEventListContainer.removeAllViews();

        if (events == null || events.isEmpty()) {
            TextView emptyView = new TextView(requireContext());
            emptyView.setText("No Events Yet, Click + to Add an Event");
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


    private MaterialSwitch findOptionalSwitch(View root, String idName) {
        int id = requireContext().getResources().getIdentifier(idName, "id", requireContext().getPackageName());
        if (id == 0) {
            return null;
        }
        View found = root.findViewById(id);
        return found instanceof MaterialSwitch ? (MaterialSwitch) found : null;
    }

    private View findOptionalView(View root, String idName) {
        int id = requireContext().getResources().getIdentifier(idName, "id", requireContext().getPackageName());
        return id == 0 ? null : root.findViewById(id);
    }

    private boolean isPrivateEvent(Event event) {
        if (event == null) {
            return false;
        }
        try {
            Method method = event.getClass().getMethod("isPrivateEvent");
            Object result = method.invoke(event);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void setPrivateEventFlag(Event event, boolean isPrivate) {
        if (event == null) {
            return;
        }
        try {
            Method method = event.getClass().getMethod("setPrivateEvent", boolean.class);
            method.invoke(event, isPrivate);
        } catch (Exception ignored) {
        }
    }

    private void syncPrivateEventUi(
            boolean isPrivate,
            MaterialSwitch switchWaitlist,
            MaterialSwitch switchGeo,
            EditText etWaitlistCapacity,
            EditText etLocationRadiusKm,
            View layoutWaitlistCapacity,
            View layoutLocationRadius,
            @Nullable View privateInviteLayout
    ) {
        if (privateInviteLayout != null) {
            privateInviteLayout.setVisibility(isPrivate ? View.VISIBLE : View.GONE);
        }

        // Private events should STILL be allowed to configure waitlist capacity
        if (switchWaitlist != null) {
            boolean waitlistEnabled = switchWaitlist.isChecked();
            if (layoutWaitlistCapacity != null) {
                layoutWaitlistCapacity.setVisibility(waitlistEnabled ? View.VISIBLE : View.GONE);
            }
        }

        // Private events should NOT allow geolocation constraints
        if (switchGeo != null) {
            switchGeo.setEnabled(!isPrivate);
            if (isPrivate) {
                switchGeo.setChecked(false);
            }
        }

        if (layoutLocationRadius != null) {
            boolean geoEnabled = switchGeo != null && switchGeo.isChecked() && !isPrivate;
            layoutLocationRadius.setVisibility(geoEnabled ? View.VISIBLE : View.GONE);
        }

        if (isPrivate && etLocationRadiusKm != null) {
            etLocationRadiusKm.setText("");
        }
    }

    private Event.EventStatus resolveStatusForDialog(boolean isPrivateEvent, long now, Long regStartMs, Long regEndMs, Long eventDateMs) {
        if (regStartMs == null || regEndMs == null || eventDateMs == null) {
            return Event.EventStatus.REG_UPCOMING;
        }

        if (now < regStartMs) {
            return Event.EventStatus.REG_UPCOMING;
        }
        if (now <= regEndMs) {
            return Event.EventStatus.REG_OPEN;
        }
        if (now < eventDateMs) {
            return isPrivateEvent ? Event.EventStatus.EVENT_OPEN : Event.EventStatus.REG_CLOSED;
        }
        return Event.EventStatus.EVENT_CLOSED;
    }

    /**
     * Resolves the event status from the event's current fields
     *      This must be called after any create/update dialog changes are applied
     *      and before any call to save in firebase
     *          - so the saved status reflects the latest capacity/date state.
     */
    private Event.EventStatus resolveStatusForDialog(@NonNull Event event, long now) {
        Long regStartMs = event.getRegStartMs();
        Long regEndMs = event.getRegEndMs();
        Long eventDateMs = event.getEventDateMs();

        if (regStartMs == null || regEndMs == null || eventDateMs == null) {
            return Event.EventStatus.REG_UPCOMING;
        }

        if (now < regStartMs) {
            return Event.EventStatus.REG_UPCOMING;
        }

        if (now <= regEndMs) {
            Integer waitlistCapacity = event.getWaitlistCapacity();
            int waitlistCount = event.getWaitlistCount();

            boolean hasWaitlistLimit =
                    waitlistCapacity != null
                            && waitlistCapacity != UNLIMITED_WAITLIST_SENTINEL;

            boolean waitlistFull =
                    hasWaitlistLimit && waitlistCount >= waitlistCapacity;

            return waitlistFull ? Event.EventStatus.REG_FULL : Event.EventStatus.REG_OPEN;
        }

        if (now < eventDateMs) {
            if (!event.hasDrawnLottery()) {
                return Event.EventStatus.REG_CLOSED;
            }

            int occupiedSpots = event.getEnrolledCount() + event.getInvitationCount();
            boolean eventFull = occupiedSpots >= event.getEventCapacity();

            return eventFull ? Event.EventStatus.EVENT_FULL : Event.EventStatus.EVENT_OPEN;
        }

        return Event.EventStatus.EVENT_CLOSED;
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
        MaterialSwitch switchPrivateEvent = findOptionalSwitch(view, "switch_private_event");
        View layoutPrivateInvites = findOptionalView(view, "layout_private_invites");

        // Poster handling
        View cardAddMedia = view.findViewById(R.id.card_add_media);
        currentPreviewImage = view.findViewById(R.id.iv_event_poster_preview);
        currentRemovePosterButton = view.findViewById(R.id.btn_remove_poster);
        selectedImageUri = null; // Reset for new dialog

        cardAddMedia.setOnClickListener(v -> {
            pickMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        currentRemovePosterButton.setOnClickListener(v -> {
            selectedImageUri = null;
            currentPreviewImage.setImageResource(R.drawable.ic_image_placeholder);
            currentPreviewImage.setScaleType(ImageView.ScaleType.CENTER);
            currentRemovePosterButton.setVisibility(View.GONE);
        });

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

        // private Event toggle disables all other toggles
        if (switchPrivateEvent != null) {
            switchPrivateEvent.setOnCheckedChangeListener((buttonView, isChecked) ->
                    syncPrivateEventUi(
                            isChecked,
                            switchWaitlist,
                            switchGeo,
                            etWaitlistCapacity,
                            etLocationRadiusKm,
                            layoutWaitlistCapacity,
                            layoutLocationRadius,
                            layoutPrivateInvites
                    )
            );
            syncPrivateEventUi(
                    switchPrivateEvent.isChecked(),
                    switchWaitlist,
                    switchGeo,
                    etWaitlistCapacity,
                    etLocationRadiusKm,
                    layoutWaitlistCapacity,
                    layoutLocationRadius,
                    layoutPrivateInvites
            );
        }

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

                if (switchPrivateEvent != null) {
                    switchPrivateEvent.setChecked(false);
                }
                if (layoutPrivateInvites != null) {
                    layoutPrivateInvites.setVisibility(View.GONE);
                }

                resolvedLat[0] = null;
                resolvedLng[0] = null;
                resolvedLocation[0] = null;

                localEventDateMs[0] = null;
                localStartDateMs[0] = null;
                localEndDateMs[0] = null;

                selectedImageUri = null;
                currentPreviewImage.setImageResource(R.drawable.ic_image_placeholder);
                currentPreviewImage.setScaleType(ImageView.ScaleType.CENTER);
                currentRemovePosterButton.setVisibility(View.GONE);

//                EditText organizers = view.findViewById(R.id.et_organizers);
//                if (organizers != null) {
//                    organizers.setText("");
//                }
            });
        }

        // get values from EditText views
        view.findViewById(R.id.btn_create_event).setOnClickListener(v -> {
            String title = etEventName.getText().toString().trim();
            String description = etDescription.getText().toString().trim();
            String locationInput = etLocation.getText().toString().trim();
            String capacityText = etEventCapacity.getText().toString().trim();
            String radiusText = etLocationRadiusKm.getText().toString().trim();
            boolean isPrivateEvent = switchPrivateEvent != null && switchPrivateEvent.isChecked();
            boolean waitlistHasLimit = switchWaitlist.isChecked();
            boolean geoConstraint = !isPrivateEvent && switchGeo.isChecked();

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

                setPrivateEventFlag(newEvent, isPrivateEvent);
                newEvent.setTitle(title);
                newEvent.setEventDateMs(localEventDateMs[0]);
                newEvent.setRegStartMs(localStartDateMs[0]);
                newEvent.setRegEndMs(localEndDateMs[0]);
                newEvent.setEventCapacity(eventCapacity);
                newEvent.setWaitlistCapacity(finalWaitlistCapacity);
                newEvent.setWaitlistCount(0);
                newEvent.setInvitationCount(0);
                newEvent.setHasDrawnLottery(false);
                newEvent.setHasGeoConstraint(!isPrivateEvent && geoConstraint);

                String criteriaGuidelines = getString(R.string.criteriaGuidelines);
                newEvent.setCriteriaGuidelines(criteriaGuidelines);
                setOptionalString(newEvent, "setDescription", description);

                // only create a geo/address doc when geolocation restriction is enabled
                EventAddress eventAddress = null;
                // save an address doc whenever the user entered a location OR enabled geo
                if (!finalLocationInput.isEmpty()) {
                    eventAddress = new EventAddress(newEvent.getEventId());

                    eventAddress.setLocation(resolvedLocation[0] == null ? finalLocationInput : resolvedLocation[0]);
                    eventAddress.setLatitude(resolvedLat[0]);
                    eventAddress.setLongitude(resolvedLng[0]);
                    eventAddress.setRadiusKm((!isPrivateEvent && geoConstraint) ? locationRadiusKm : null);
                }
                newEvent.setAddress(eventAddress);

                // get new event status before upserting event values to firebase
                long now = System.currentTimeMillis();
                newEvent.setStatus(resolveStatusForDialog(newEvent, now));

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

                if (selectedImageUri != null) {
                    uploadImage(selectedImageUri, newEvent.getEventId(), url -> {
                        newEvent.setPosterUrl(url);
                        performUpsert(newEvent, dialog);
                    }, e -> {
                        Toast.makeText(requireContext(), "Failed to upload image", Toast.LENGTH_SHORT).show();
                        performUpsert(newEvent, dialog);
                    });
                } else {
                    performUpsert(newEvent, dialog);
                }
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

    private void performUpsert(Event event, Dialog dialog) {
        eventStorage.upsertEvent(
                event,
                unused -> eventStorage.setEventAddress(
                        event.getEventId(),
                        event.getAddress(),
                        unused2 -> {
                            Toast.makeText(requireContext(), "Event saved!", Toast.LENGTH_SHORT).show();
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
                        "Failed to save event: " + e.getMessage(),
                        Toast.LENGTH_LONG
                ).show()
        );
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
        MaterialSwitch switchPrivateEvent = findOptionalSwitch(view, "switch_private_event");
        View layoutPrivateInvites = findOptionalView(view, "layout_private_invites");

        // Poster handling
        View cardAddMedia = view.findViewById(R.id.card_add_media);
        currentPreviewImage = view.findViewById(R.id.iv_event_poster_preview);
        currentRemovePosterButton = view.findViewById(R.id.btn_remove_poster);
        selectedImageUri = null;

        final boolean[] removePoster = {false};

        if (event.getPosterUrl() != null && !event.getPosterUrl().isEmpty()) {
            Glide.with(this)
                    .load(event.getPosterUrl())
                    .placeholder(R.drawable.ic_image_placeholder)
                    .centerCrop()
                    .into(currentPreviewImage);
            currentPreviewImage.setImageTintList(null);
            currentRemovePosterButton.setVisibility(View.VISIBLE);
        }

        currentRemovePosterButton.setOnClickListener(v -> {
            removePoster[0] = true;
            selectedImageUri = null;
            currentPreviewImage.setImageResource(R.drawable.ic_image_placeholder);
            currentPreviewImage.setScaleType(ImageView.ScaleType.CENTER);
            currentRemovePosterButton.setVisibility(View.GONE);
        });

        cardAddMedia.setOnClickListener(v -> {
            pickMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

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

        boolean initialPrivateEvent = isPrivateEvent(event);

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

        if (switchPrivateEvent != null) {
            switchPrivateEvent.setChecked(initialPrivateEvent);
        }
        if (layoutPrivateInvites != null) {
            layoutPrivateInvites.setVisibility(initialPrivateEvent ? View.VISIBLE : View.GONE);
        }

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

        if (switchPrivateEvent != null) {
            switchPrivateEvent.setOnCheckedChangeListener((buttonView, isChecked) ->
                    syncPrivateEventUi(
                            isChecked,
                            switchWaitlist,
                            switchGeo,
                            etWaitlistCapacity,
                            etLocationRadiusKm,
                            layoutWaitlistCapacity,
                            layoutLocationRadius,
                            layoutPrivateInvites
                    )
            );
            syncPrivateEventUi(
                    switchPrivateEvent.isChecked(),
                    switchWaitlist,
                    switchGeo,
                    etWaitlistCapacity,
                    etLocationRadiusKm,
                    layoutWaitlistCapacity,
                    layoutLocationRadius,
                    layoutPrivateInvites
            );
        }

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

                if (switchPrivateEvent != null) {
                    switchPrivateEvent.setChecked(false);
                }
                if (layoutPrivateInvites != null) {
                    layoutPrivateInvites.setVisibility(View.GONE);
                }

                resolvedLat[0] = null;
                resolvedLng[0] = null;
                resolvedLocation[0] = null;

                localEventDateMs[0] = null;
                localStartDateMs[0] = null;
                localEndDateMs[0] = null;

                selectedImageUri = null;
                currentPreviewImage.setImageResource(R.drawable.ic_image_placeholder);
                currentPreviewImage.setScaleType(ImageView.ScaleType.CENTER);
                removePoster[0] = true;
                currentRemovePosterButton.setVisibility(View.GONE);
            });
        }

        // get values from EditText views
        view.findViewById(R.id.btn_update_event).setOnClickListener(v -> {
            String title = etEventName.getText().toString().trim();
            String description = etDescription.getText().toString().trim();
            String locationInput = etLocation.getText().toString().trim();
            String capacityText = etEventCapacity.getText().toString().trim();
            String radiusText = etLocationRadiusKm.getText().toString().trim();
            boolean isPrivateEvent = switchPrivateEvent != null && switchPrivateEvent.isChecked();
            boolean waitlistHasLimit = switchWaitlist.isChecked();
            boolean geoConstraint = !isPrivateEvent && switchGeo.isChecked();

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

                setPrivateEventFlag(event, isPrivateEvent);
                event.setWaitlistCapacity(finalWaitlistCapacity);
                setOptionalString(event, "setDescription", description);

                EventAddress updatedAddress = null;

                // CHANGED: keep/save a normal location even when private
                if (!finalLocationInput.isEmpty()) {
                    updatedAddress = event.getAddress();
                    if (updatedAddress == null) {
                        updatedAddress = new EventAddress(event.getEventId());
                    }

                    updatedAddress.setLocation(
                            resolvedLocation[0] == null ? finalLocationInput : resolvedLocation[0]
                    );
                    updatedAddress.setLatitude(resolvedLat[0]);
                    updatedAddress.setLongitude(resolvedLng[0]);

                    // only public geo-constrained events keep radius
                    updatedAddress.setRadiusKm((!isPrivateEvent && geoConstraint) ? locationRadiusKm : null);
                }

                event.setHasGeoConstraint(!isPrivateEvent && geoConstraint);
                event.setAddress(updatedAddress);

                // get new event status before upserting event values to firebase
                long now = System.currentTimeMillis();
                event.setStatus(resolveStatusForDialog(
                        isPrivateEvent,
                        now,
                        localStartDateMs[0],
                        localEndDateMs[0],
                        localEventDateMs[0]
                ));

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

                if (selectedImageUri != null) {
                    uploadImage(selectedImageUri, event.getEventId(), url -> {
                        event.setPosterUrl(url);
                        performUpsert(event, dialog);
                    }, e -> {
                        Toast.makeText(requireContext(), "Failed to upload image", Toast.LENGTH_SHORT).show();
                        performUpsert(event, dialog);
                    });
                } else {
                    if (removePoster[0]) {
                        event.setPosterUrl(null);
                    }
                    performUpsert(event, dialog);
                }
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

    private void uploadImage(Uri uri, String eventId, OnSuccessListener<String> onSuccess, OnFailureListener onFailure) {
        StorageReference ref = ServiceLocator.getFirebase().getStorage().getReference()
                .child("posters/" + eventId + ".jpg");

        ref.putFile(uri)
                .addOnSuccessListener(taskSnapshot -> ref.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                    onSuccess.onSuccess(downloadUri.toString());
                }))
                .addOnFailureListener(onFailure);
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
            Method method = event.getClass().getMethod(methodName, String.class);
            method.invoke(event, value);
        } catch (Exception ignored) {
        }
    }

    /**
     * Status-based string builder for DisplayEventGrid subtitles
     */
    private String buildSubtitle(Event event) {
        StringBuilder subtitle = new StringBuilder();

        if (isPrivateEvent(event)) {
            subtitle.append("Private •");
        }

        if (event != null && event.getStatus() != null) {
            subtitle.append(event.getStatus().name());
        }

        if (!event.hasDrawnLottery) {
            Integer waitlistCap = event.getWaitlistCapacity();

            if (waitlistCap == UNLIMITED_WAITLIST_SENTINEL) {
                subtitle.append("\nWaitlist: ")
                        .append(event.getWaitlistCount())
                        .append("/")
                        .append("∞");

            } else if (waitlistCap > 0) {
                subtitle.append("\nWaitlist: ")
                        .append(event.getWaitlistCount())
                        .append("/")
                        .append(waitlistCap);
            }
        } else {
            subtitle.append("\nEnrolled:")
                    .append(event.getEnrolledCount())
                    .append("/")
                    .append(event.getEventCapacity());
        }

        if (event != null && event.getEventDateMs() != null) {
            subtitle.append(formatLocalDate(event.getEventDateMs()));
        }

        return subtitle.length() == 0 ? "Event" : subtitle.toString();
    }

}