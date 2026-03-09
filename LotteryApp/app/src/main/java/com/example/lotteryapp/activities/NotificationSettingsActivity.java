package com.example.lotteryapp.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import com.example.lotteryapp.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;

public class NotificationSettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "notification_prefs";
    private static final String KEY_INVITATIONS = "notifications_invitations";
    private static final String KEY_LOTTERY_RESULTS = "notifications_lottery_results";
    private static final String KEY_MESSAGES = "notifications_messages";
    private static final String KEY_ADMIN_ALERTS = "notifications_admin_alerts";

    private MaterialSwitch switchAll;
    private MaterialSwitch switchInvitations;
    private MaterialSwitch switchResults;
    private MaterialSwitch switchMessages;
    private MaterialSwitch switchAdmin;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        switchAll = findViewById(R.id.switch_all_notifications);
        switchInvitations = findViewById(R.id.switch_invitations);
        switchResults = findViewById(R.id.switch_lottery_results);
        switchMessages = findViewById(R.id.switch_organizer_messages);
        switchAdmin = findViewById(R.id.switch_admin_alerts);

        // clicking the master switch opens system settings
        switchAll.setOnClickListener(v -> {
            openSystemNotificationSettings();
        });

        setupSubSwitch(switchInvitations, KEY_INVITATIONS);
        setupSubSwitch(switchResults, KEY_LOTTERY_RESULTS);
        setupSubSwitch(switchMessages, KEY_MESSAGES);
        setupSubSwitch(switchAdmin, KEY_ADMIN_ALERTS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkSystemNotificationStatus();
    }

    private void checkSystemNotificationStatus() {
        boolean areEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled();
        switchAll.setChecked(areEnabled);
        updateSubSwitchesState(areEnabled);
    }

    private void openSystemNotificationSettings() {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        }
        else {
            intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
            intent.putExtra("app_package", getPackageName());
            intent.putExtra("app_uid", getApplicationInfo().uid);
        }
        startActivity(intent);
    }

    private void setupSubSwitch(MaterialSwitch sw, String prefKey) {
        sw.setChecked(prefs.getBoolean(prefKey, true));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(prefKey, isChecked).apply();
        });
    }

    private void updateSubSwitchesState(boolean enabled) {
        float alpha = enabled ? 1.0f : 0.5f;
        switchInvitations.setEnabled(enabled);
        switchInvitations.setAlpha(alpha);
        switchResults.setEnabled(enabled);
        switchResults.setAlpha(alpha);
        switchMessages.setEnabled(enabled);
        switchMessages.setAlpha(alpha);
        switchAdmin.setEnabled(enabled);
        switchAdmin.setAlpha(alpha);
    }
}