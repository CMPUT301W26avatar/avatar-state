package com.example.lotteryapp.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.lotteryapp.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;

public class NotificationSettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "notification_prefs";
    private static final String KEY_INVITATIONS = "notifications_invitations";
    private static final String KEY_LOTTERY_RESULTS = "notifications_lottery_results";
    private static final String KEY_MESSAGES = "notifications_messages";
    private static final String KEY_ADMIN_ALERTS = "notifications_admin_alerts";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        setupSwitch(R.id.switch_invitations, KEY_INVITATIONS, prefs);
        setupSwitch(R.id.switch_lottery_results, KEY_LOTTERY_RESULTS, prefs);
        setupSwitch(R.id.switch_organizer_messages, KEY_MESSAGES, prefs);
        setupSwitch(R.id.switch_admin_alerts, KEY_ADMIN_ALERTS, prefs);
    }

    private void setupSwitch(int viewId, String prefKey, SharedPreferences prefs) {
        MaterialSwitch sw = findViewById(viewId);
        sw.setChecked(prefs.getBoolean(prefKey, true));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(prefKey, isChecked).apply();
        });
    }
}
