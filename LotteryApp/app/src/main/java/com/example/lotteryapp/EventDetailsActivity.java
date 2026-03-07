package com.example.lotteryapp;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

public class EventDetailsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_details);

        MaterialTextView tvName = findViewById(R.id.tv_event_name);
        MaterialTextView tvLocation = findViewById(R.id.tv_location);
        MaterialTextView tvDescription = findViewById(R.id.tv_description);
        MaterialTextView tvDate = findViewById(R.id.tv_event_date);
        MaterialTextView tvRegEndDate = findViewById(R.id.tv_reg_end_date);
        MaterialButton btnClose = findViewById(R.id.btn_close);
        MaterialButton btnJoin = findViewById(R.id.btn_join_waitlist);

        // Get data from intent
        String name = getIntent().getStringExtra("event_name");
        String location = getIntent().getStringExtra("event_location");
        String description = getIntent().getStringExtra("event_description");
        String tag = getIntent().getStringExtra("event_tag");

        if (name != null) tvName.setText(name);
        if (location != null) tvLocation.setText(location);
        if (description != null) tvDescription.setText(description);

        // Mock data for dates as they aren't in the current Event model
        tvDate.setText("MAR 05");
        tvRegEndDate.setText("Registration Ends: Feb 28");

        btnClose.setOnClickListener(v -> finish());

        btnJoin.setOnClickListener(v -> {
            // Handle join waitlist logic
            btnJoin.setText("Joined Waitlist");
            btnJoin.setEnabled(false);
        });
    }
}