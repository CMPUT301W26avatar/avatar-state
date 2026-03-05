package com.example.lotteryapp;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ManageFragment extends Fragment {

    private int waitlistLimit = 123;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manage, container, false);

        FloatingActionButton fab = view.findViewById(R.id.fab_add_event);
        fab.setOnClickListener(v -> showCreateEventDialog());

        return view;
    }

    private void showCreateEventDialog() {
        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_event, null);
        dialog.setContentView(view);

        EditText etDate = view.findViewById(R.id.et_date);
        EditText etRegDates = view.findViewById(R.id.et_reg_dates);
        TextView tvWaitlistLimit = view.findViewById(R.id.tv_waitlist_limit);
        View btnDecrement = view.findViewById(R.id.btn_decrement);
        View btnIncrement = view.findViewById(R.id.btn_increment);
        View btnClose = view.findViewById(R.id.toolbar); // Toolbar handles navigation click if configured, but let's be explicit if needed

        // Date of Event Picker
        etDate.setOnClickListener(v -> {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Event Date")
                    .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
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
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
                String startDate = sdf.format(new Date(selection.first));
                String endDate = sdf.format(new Date(selection.second));
                etRegDates.setText(startDate + " - " + endDate);
            });
            rangePicker.show(getParentFragmentManager(), "RANGE_PICKER");
        });

        // Waitlist Counter
        tvWaitlistLimit.setText(String.valueOf(waitlistLimit));
        btnDecrement.setOnClickListener(v -> {
            if (waitlistLimit > -1) {
                waitlistLimit--;
                tvWaitlistLimit.setText(String.valueOf(waitlistLimit));
            }
        });
        btnIncrement.setOnClickListener(v -> {
            waitlistLimit++;
            tvWaitlistLimit.setText(String.valueOf(waitlistLimit));
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

        view.findViewById(R.id.btn_create_event).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}