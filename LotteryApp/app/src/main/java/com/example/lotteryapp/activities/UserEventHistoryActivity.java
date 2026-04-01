package com.example.lotteryapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.lotteryapp.R;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.models.UserEventHistory;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventStorage;
import com.example.lotteryapp.services.storage.UserStorage;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Displays the current user's event history as a scrollable list of event cards.
 *      event cards have a details button that launches EventDetailsActivity
 */
public class UserEventHistoryActivity extends AppCompatActivity {

    private LinearLayout historyContainer;
    private UserStorage userStorage;
    private EventStorage eventStorage;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_event_history);

        userStorage = ServiceLocator.getUserStorage();
        eventStorage = ServiceLocator.getEventStorage();

        MaterialToolbar toolbar = findViewById(R.id.toolbar_history);
        historyContainer = findViewById(R.id.layout_history_items);

        toolbar.setNavigationOnClickListener(v -> finish());

        loadUserEventHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserEventHistory();
    }

    /**
     * Loads the current user's event history entries.
     */
    private void loadUserEventHistory() {
        String userId = ServiceLocator.uid();
        if (userId == null || userId.trim().isEmpty()) {
            Toast.makeText(this, "User not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        historyContainer.removeAllViews();

        userStorage.getUserEventHistory(
                userId,
                this::loadEventsForHistory,
                e -> Toast.makeText(this, "Failed to load event history", Toast.LENGTH_SHORT).show()
        );
    }

    /**
     * Loads the event document for each history entry so the UI can show proper event cards.
     */
    private void loadEventsForHistory(List<UserEventHistory> historyEntries) {
        historyContainer.removeAllViews();

        if (historyEntries == null || historyEntries.isEmpty()) {
            showEmptyState();
            return;
        }

        List<HistoryRow> rows = new ArrayList<>();
        AtomicInteger remaining = new AtomicInteger(historyEntries.size());

        for (UserEventHistory history : historyEntries) {
            String eventId = history.getEventId();
            if (eventId == null || eventId.trim().isEmpty()) {
                if (remaining.decrementAndGet() == 0) {
                    renderRows(rows);
                }
                continue;
            }

            eventStorage.getEvent(
                    eventId,
                    event -> {
                        rows.add(new HistoryRow(event, history));
                        if (remaining.decrementAndGet() == 0) {
                            renderRows(rows);
                        }
                    },
                    e -> {
                        if (remaining.decrementAndGet() == 0) {
                            renderRows(rows);
                        }
                    }
            );
        }
    }

    /**
     * Renders the history cards in the same order returned by Firestore.
     */
    private void renderRows(List<HistoryRow> rows) {
        historyContainer.removeAllViews();

        if (rows.isEmpty()) {
            showEmptyState();
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);

        for (HistoryRow rowData : rows) {
            Event event = rowData.event;
            UserEventHistory history = rowData.history;

            View row = inflater.inflate(R.layout.item_event_history, historyContainer, false);

            TextView tvTitle = row.findViewById(R.id.tv_event_title);
            TextView tvStatus = row.findViewById(R.id.tv_history_status);
            TextView tvUpdatedAt = row.findViewById(R.id.tv_updated_at);
            View btnDetails = row.findViewById(R.id.btn_event_details);

            String title = event.getTitle();
            tvTitle.setText(title != null && !title.trim().isEmpty() ? title : event.getEventId());

            tvStatus.setText(formatStatus(history.getStatus()));

            Long updatedAtMs = history.getUpdatedAtMs();
            tvUpdatedAt.setText(
                    updatedAtMs != null
                            ? "Updated " + EventDetailsActivity.getTimeAgo(updatedAtMs)
                            : "Updated time unknown"
            );

            View.OnClickListener openListener = v -> openEventDetails(event);

            row.setOnClickListener(openListener);
            btnDetails.setOnClickListener(openListener);

            historyContainer.addView(row);
        }
    }

    /**
     * Formats stored history status values into user-friendly text.
     */
    private String formatStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.trim().isEmpty()) {
            return "Unknown status";
        }

        String normalized = rawStatus.replace('_', ' ').toLowerCase(Locale.getDefault());
        String pretty = Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
        return "Status: " + pretty;
    }

    /**
     * Opens EventDetailsActivity for the given event.
     */
    private void openEventDetails(Event event) {
        if (event == null || event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            Toast.makeText(this, "Missing event ID", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, EventDetailsActivity.class);
        intent.putExtra(EventDetailsActivity.EXTRA_EVENT_ID, event.getEventId());
        startActivity(intent);
    }

    /**
     * Shows a simple empty-state message.
     */
    private void showEmptyState() {
        TextView emptyView = new TextView(this);
        emptyView.setText("No event history yet.");
        historyContainer.addView(emptyView);
    }

    /**
     * Small holder pairing a history entry with its event.
     */
    private static class HistoryRow {
        final Event event;
        final UserEventHistory history;

        HistoryRow(Event event, UserEventHistory history) {
            this.event = event;
            this.history = history;
        }
    }
}