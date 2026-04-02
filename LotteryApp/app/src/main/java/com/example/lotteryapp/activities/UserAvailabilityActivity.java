package com.example.lotteryapp.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import androidx.gridlayout.widget.GridLayout;

import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.applandeo.materialcalendarview.CalendarView;
import com.applandeo.materialcalendarview.listeners.OnCalendarDayClickListener;
import com.applandeo.materialcalendarview.CalendarDay;
import com.example.lotteryapp.R;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.UserStorage;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * UserAvailabilityActivity displays a multi-date calendar picker for the user.
 * Selected dates are saved as normalized local-day epoch ms values inside:
 *      /users/{uid}/availability/dates
 * User can tap to select multiple dates and those dates are populates in a gridLayout for selected days
 *      hitting the save availability stores the state of the selected list to firebase, and saves the state of the picked days on the calendar
 */
public class UserAvailabilityActivity extends AppCompatActivity {

    private CalendarView calendarView;
    private GridLayout selectedDatesContainer;
    private MaterialButton btnSaveAvailability;

    private UserStorage userStorage;
    private String uid;


    /**
     * Stored in memory, source of truth for selected dates.
     */
    private final Set<Long> selectedDateMsSet = new LinkedHashSet<>();

    /**
     * bind UI, validate the user, attach click listeners, load existing availability from firestore
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_availability);

        userStorage = ServiceLocator.getUserStorage();
        uid = ServiceLocator.uid(); // get user id

        // bind UI
        Toolbar toolbar = findViewById(R.id.toolbar);
        calendarView = findViewById(R.id.calendar_view);
        selectedDatesContainer = findViewById(R.id.layout_selected_dates);
        btnSaveAvailability = findViewById(R.id.btn_save_availability);

        toolbar.setNavigationOnClickListener(v -> finish());

        // validate the user
        if (uid == null || uid.trim().isEmpty()) {
            Toast.makeText(this, "User not signed in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // setup calendar date click listeners, save button listener and load availability from firestore
        setupCalendarListeners();
        btnSaveAvailability.setOnClickListener(v -> saveAvailability());
        loadAvailability();
    }


    /**
     * Listen for date taps directly from the multi-date calendar widget
     *      After every click we read the widget's selected dates, normalize them, and refresh the selected-days list shown below the calendar.
     */
    private void setupCalendarListeners() {
        calendarView.setOnCalendarDayClickListener(new OnCalendarDayClickListener() {
            @Override
            public void onClick(CalendarDay calendarDay) {
                // raw calendar to normalized local date
                long clickedDateMs = calendarToLocalDateMs(calendarDay.getCalendar());

                // toggle selection
                if (selectedDateMsSet.contains(clickedDateMs)) {
                    selectedDateMsSet.remove(clickedDateMs);
                } else {
                    selectedDateMsSet.add(clickedDateMs);
                }

                // reload grid UI
                renderSelectedDates();
            }
        });
    }

    /**
     * Loads the saved availability dates from Firestore and preselects them inside the multi-date calendar widget.
     */
    private void loadAvailability() {
        userStorage.getUserAvailabilityDates(
                uid,
                dates -> {
                    selectedDateMsSet.clear();
                    selectedDateMsSet.addAll(dates);

                    // convert stored ms value from firebase to calendar objects for the widget
                    List<Calendar> selectedCalendars = new ArrayList<>();
                    for (Long dateMs : selectedDateMsSet) {
                        selectedCalendars.add(localDateMsToCalendar(dateMs));
                    }

                    calendarView.setSelectedDates(selectedCalendars);
                    renderSelectedDates();
                },
                e -> Toast.makeText(this, "Failed to load availability", Toast.LENGTH_SHORT).show()
        );
    }

    /**
     * Saves the current selected dates from the calendar widget into the
     *      single /availability/dates document as one array write
     */
    private void saveAvailability() {
        List<Long> sortedDates = new ArrayList<>(selectedDateMsSet);
        Collections.sort(sortedDates); // store sorted by recency (for ms values)

        userStorage.setUserAvailabilityDates(
                uid,
                sortedDates,
                unused -> Toast.makeText(this, "Availability saved", Toast.LENGTH_SHORT).show(),
                e -> Toast.makeText(this, "Failed to save availability", Toast.LENGTH_SHORT).show()
        );
    }

    /**
     * Shows the selected dates below the calendar in sorted order.
     *      Each grid item also has a close button that updates the calendar selection.
     */
    private void renderSelectedDates() {
        selectedDatesContainer.removeAllViews();

        List<Long> sortedDates = new ArrayList<>(selectedDateMsSet);
        Collections.sort(sortedDates);

        if (sortedDates.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("No available days selected.");
            int pad = dp(12);
            emptyView.setPadding(pad, pad, pad, pad);
            selectedDatesContainer.addView(emptyView);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Long dateMs : sortedDates) {
            // inflate title
            View row = inflater.inflate(R.layout.item_availability_date, selectedDatesContainer, false);

            // compute fixed width so tiles don't stretch to fit the screen
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            int tileWidth = getAvailabilityTileWidthPx();

            params.width = tileWidth;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.setMargins(dp(4), dp(4), dp(4), dp(4)); // uniform spacing between tiles
            row.setLayoutParams(params);

            // bind availability item views
            TextView tvDayLabel = row.findViewById(R.id.tv_day_label);
            TextView tvDateValue = row.findViewById(R.id.tv_date_value);
            ImageButton btnRemove = row.findViewById(R.id.btn_remove_date);

            // Remove action updates both Set + calendar widget
            tvDayLabel.setText(formatDayAbbreviation(dateMs));
            tvDateValue.setText(formatGridDate(dateMs));
            btnRemove.setOnClickListener(v -> removeSelectedDate(dateMs));

            selectedDatesContainer.addView(row);
        }
    }

    /**
     * Removes one selected day from the widget by rebuilding the selected date list
     * without that day, then pushing the revised list back into the calendar.
     */
    private void removeSelectedDate(long dateMsToRemove) {
        selectedDateMsSet.remove(dateMsToRemove);

        List<Calendar> updatedSelections = new ArrayList<>();
        for (Long dateMs : selectedDateMsSet) {
            updatedSelections.add(localDateMsToCalendar(dateMs));
        }

        calendarView.setSelectedDates(updatedSelections);
        renderSelectedDates();
    }

    /**
     * Normalizes a Calendar object into a stable local-day epoch value.
     * We intentionally store local noon to avoid timezone/day-boundary drift.
     */
    private long calendarToLocalDateMs(@NonNull Calendar sourceCalendar) {
        Calendar localCal = Calendar.getInstance();
        localCal.clear();
        localCal.set(Calendar.YEAR, sourceCalendar.get(Calendar.YEAR));
        localCal.set(Calendar.MONTH, sourceCalendar.get(Calendar.MONTH));
        localCal.set(Calendar.DAY_OF_MONTH, sourceCalendar.get(Calendar.DAY_OF_MONTH));
        // Normalize to noon
        localCal.set(Calendar.HOUR_OF_DAY, 12);
        localCal.set(Calendar.MINUTE, 0);
        localCal.set(Calendar.SECOND, 0);
        localCal.set(Calendar.MILLISECOND, 0);
        return localCal.getTimeInMillis();
    }

    /**
     * Converts a normalized local-day ms value back into a Calendar object
     * so the widget can display it as selected.
     */
    @NonNull
    private Calendar localDateMsToCalendar(long localDateMs) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(localDateMs);
        return calendar;
    }

    /**
     * Compute the tile width for a 4 column grid layout
     *      get container width, subtract all margins then divide by 4
     *      use screen width if container width not specified
     */
    private int getAvailabilityTileWidthPx() {
        int containerWidth = selectedDatesContainer.getWidth();

        if (containerWidth == 0) {
            containerWidth = getResources().getDisplayMetrics().widthPixels - dp(32);
        }

        int totalHorizontalSpacing = dp(8 * 4); // each tile has 4dp margin on left + right
        return (containerWidth - totalHorizontalSpacing) / 4;
    }

    /**
     * Returns compact weekday label
     */
    @NonNull
    private String formatDayAbbreviation(@Nullable Long localMs) {
        if (localMs == null) {
            return "";
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(localMs);

        switch (calendar.get(Calendar.DAY_OF_WEEK)) {
            case Calendar.SUNDAY:
                return "SU";
            case Calendar.MONDAY:
                return "M";
            case Calendar.TUESDAY:
                return "T";
            case Calendar.WEDNESDAY:
                return "W";
            case Calendar.THURSDAY:
                return "TH";
            case Calendar.FRIDAY:
                return "F";
            case Calendar.SATURDAY:
                return "S";
            default:
                return "";
        }
    }

    /**
     * Formats date for tile display
     */
    @NonNull
    private String formatGridDate(@Nullable Long localMs) {
        if (localMs == null) {
            return "";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
        return sdf.format(new Date(localMs));
    }

    /**
     * helper for dp to px conversion.
     */
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}