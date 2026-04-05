package com.example.lotteryapp.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.util.Pair;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.lotteryapp.R;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

public class FilterDialog extends Dialog {

    public enum FilterType {
        AVAILABILITY_FILTER,
        UPCOMING_EVENTS_FILTER,
        OPEN_EVENTS_FILTER,
        FULL_EVENTS_FILTER,
    }

    public static final int NOT_SET = -1;

    private boolean useAvailabilityFilter;
    private boolean showUpcomingEvents;
    private boolean showOpenEvents;
    private boolean showFullEvents;

    private TextInputEditText minCapTextInput;
    private int minCapValue;
    private TextInputEditText maxCapTextInput;
    private int maxCapValue;

    public FilterDialog(@NonNull Context context) {
        super(context);

        useAvailabilityFilter = false;
        showUpcomingEvents = true;
        showOpenEvents = true;
        showFullEvents = true;

        minCapValue = NOT_SET;
        maxCapValue = NOT_SET;
    }

    public boolean isActive(FilterType type) {
        switch (type) {
            case AVAILABILITY_FILTER:
                return useAvailabilityFilter;
            case UPCOMING_EVENTS_FILTER:
                return showUpcomingEvents;
            case OPEN_EVENTS_FILTER:
                return showOpenEvents;
            case FULL_EVENTS_FILTER:
                return showFullEvents;
        }

        return false;
    }

    public Pair<Integer, Integer> getCapacityFilterValues() {
        return new Pair<>(minCapValue, maxCapValue);
    }

    public boolean validateInput(@NonNull View filterView) {
        // Get min. max. cap
        int val;
        String minCapInput = minCapTextInput.getText().toString();
        if (!minCapInput.isEmpty()) {
            val = Integer.parseInt(minCapInput);
            if (val < 1) {
                Toast.makeText(filterView.getContext(),
                        "Min. capacity must be greater than 0.", Toast.LENGTH_LONG).show();
                return false;
            }

            minCapValue = val;
        }

        String maxCapInput = maxCapTextInput.getText().toString();
        if (!maxCapInput.isEmpty()) {
            val = Integer.parseInt(maxCapInput);
            if (val <= minCapValue) {
                Toast.makeText(filterView.getContext(),
                        "Max. capacity must by greater than min. capacity.", Toast.LENGTH_LONG).show();
                return false;
            }

            maxCapValue = val;
        }

        return true;
    }

    public void setupListeners(@NonNull View filterView) {
        MaterialSwitch avbFilterSwitch = filterView.findViewById(R.id.availability_filter);
        avbFilterSwitch.setChecked(useAvailabilityFilter);
        avbFilterSwitch.setOnCheckedChangeListener((_NA, isChecked) -> {
            useAvailabilityFilter = isChecked;
        });

        MaterialSwitch upcomingFilterSwitch = filterView.findViewById(R.id.showUpcoming_filter);
        upcomingFilterSwitch.setChecked(showUpcomingEvents);
        upcomingFilterSwitch.setOnCheckedChangeListener((_NA, isChecked) -> {
            showUpcomingEvents = isChecked;
        });

        MaterialSwitch openFilterSwitch = filterView.findViewById(R.id.showOpen_filter);
        openFilterSwitch.setChecked(showOpenEvents);
        openFilterSwitch.setOnCheckedChangeListener((_NA, isChecked) -> {
            showOpenEvents = isChecked;
        });

        MaterialSwitch fullFilterSwitch = filterView.findViewById(R.id.fullEvent_filter);
        fullFilterSwitch.setChecked(showFullEvents);
        fullFilterSwitch.setOnCheckedChangeListener((_NA, isChecked) -> {
            showFullEvents = isChecked;
        });

        minCapTextInput = filterView.findViewById(R.id.event_min_filter);
        if (minCapValue == NOT_SET) {
            minCapTextInput.setText("");
        } else {
            minCapTextInput.setText(String.valueOf(minCapValue));
        }


        maxCapTextInput = filterView.findViewById(R.id.event_max_filter);
        if (maxCapValue == NOT_SET) {
            maxCapTextInput.setText("");
        } else {
            maxCapTextInput.setText(String.valueOf(maxCapValue));
        }
    }
}
