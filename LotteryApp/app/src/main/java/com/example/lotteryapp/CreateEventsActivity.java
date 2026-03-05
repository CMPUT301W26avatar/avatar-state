package com.example.lotteryapp;

import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CreateEventsActivity extends AppCompatActivity {
    enum FieldType {
        TEXT,
        NUMBER,
        DATE,
        TOGGLE
    }
    static class EventField {
        final String label;
        final FieldType type;
        String displayValue;
        Object rawValue;

        EventField(String label, FieldType type, String placeholder) {
            this.label = label;
            this.type = type;
            this.displayValue = placeholder;
            this.rawValue = null;
        }
    }
    // set attr. indexes in row to a readable LABEL
    private static final int IDX_TITLE        = 0;
    private static final int IDX_CAPACITY     = 1;
    private static final int IDX_WAITLIST_CAP = 2;
    private static final int IDX_WAITLIST_ON  = 3;
    private static final int IDX_REG_START    = 4;
    private static final int IDX_REG_END      = 5;
    private static final int IDX_DRAW_TIME    = 6;
    private static final int IDX_POSTER_URL   = 7;

    private final List<EventField> fields = new ArrayList<>();
    private FieldAdapter adapter;

    private EventStorage eventStorage; // access to Firebase
    private String organizerId;

    private final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_events);

        // get access to Firebase via EventStorage from ServiceLocator
        eventStorage = ServiceLocator.eventStorage();

        // eventId is Firebase doc_id
        organizerId  = getIntent().getStringExtra("organizerId");

        buildFields();

        adapter = new FieldAdapter(this, fields);
        ListView listView = findViewById(R.id.listFields);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> onFieldClicked(position));

        findViewById(R.id.btnSaveEvent).setOnClickListener(v -> saveEvent());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    // field definition helper
    private void buildFields() {
        fields.add(new EventField("Title *",              FieldType.TEXT,   "Tap to enter"));
        fields.add(new EventField("Capacity *",           FieldType.NUMBER, "Tap to set"));
        fields.add(new EventField("Waitlist Capacity",    FieldType.NUMBER, "Tap to set (blank = unlimited)"));
        fields.add(new EventField("Waitlist Enabled",     FieldType.TOGGLE, "No"));
        fields.add(new EventField("Registration Start *", FieldType.DATE,   "Tap to pick date"));
        fields.add(new EventField("Registration End *",   FieldType.DATE,   "Tap to pick date"));
        fields.add(new EventField("Draw Date",            FieldType.DATE,   "Tap to pick date (optional)"));
        fields.add(new EventField("Poster URL",           FieldType.TEXT,   "Tap to enter URL"));
    }

    // field "what to do when clicked?" helper
    private void onFieldClicked(int index) {
        EventField field = fields.get(index);
        switch (field.type) {
            case TEXT :
            case NUMBER:
                showTextDialog(index);
                break;
            case DATE:
                showDatePicker(index);
                break;
            case TOGGLE:
                toggleBoolean(index);
                break;
        }
    }

    // handles NUMBER and TEXT fields
    private void showTextDialog(int index) {
        EventField field = fields.get(index);
        EditText input = new EditText(this);
        input.setInputType(field.type == FieldType.NUMBER
                ? InputType.TYPE_CLASS_NUMBER
                : InputType.TYPE_CLASS_TEXT);
        if (field.rawValue != null) {
            input.setText(field.rawValue.toString());
        }
        input.setPadding(48, 32, 48, 32);

        new AlertDialog.Builder(this)
                .setTitle(field.label)
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    String val = input.getText().toString().trim();
                    if (val.isEmpty()) {
                        field.rawValue = null;
                        field.displayValue = "Not set";
                    } else if (field.type == FieldType.NUMBER) {
                        field.rawValue = Integer.parseInt(val);
                        field.displayValue = val;
                    } else {
                        field.rawValue = val;
                        field.displayValue = val;
                    }
                    adapter.notifyDataSetChanged();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // launches an interactive calendar for picking dates
    private void showDatePicker(int index) {
        EventField field = fields.get(index);
        Calendar cal = Calendar.getInstance();
        if (field.rawValue instanceof Timestamp) {
            cal.setTimeInMillis(((Timestamp) field.rawValue).getTime());
        }
        new DatePickerDialog(this, (view, year, month, day) -> {
            cal.set(year, month, day, 0, 0, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Timestamp ts = new Timestamp(cal.getTimeInMillis());
            field.rawValue = ts;
            field.displayValue = sdf.format(ts);
            adapter.notifyDataSetChanged();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void toggleBoolean(int index) {
        EventField field = fields.get(index);
        boolean current = Boolean.TRUE.equals(field.rawValue);
        field.rawValue = !current;
        field.displayValue = !current ? "Yes" : "No";
        adapter.notifyDataSetChanged();
    }

    // save an event
    private void saveEvent() {
        Log.d("CreateEvent", "saveEvent() called");

        Object titleRaw = fields.get(IDX_TITLE).rawValue;
        String title = titleRaw instanceof String ? ((String) titleRaw).trim() : "";

        if (title.isEmpty()) {
            Toast.makeText(this, "Title is required", Toast.LENGTH_SHORT).show();
            return;
        }

        if (fields.get(IDX_CAPACITY).rawValue == null) {
            Toast.makeText(this, "Capacity is required", Toast.LENGTH_SHORT).show();
            return;
        }
        if (fields.get(IDX_REG_START).rawValue == null || fields.get(IDX_REG_END).rawValue == null) {
            Toast.makeText(this, "Registration period is required", Toast.LENGTH_SHORT).show();
            return;
        }

        java.sql.Timestamp regStart = (java.sql.Timestamp) fields.get(IDX_REG_START).rawValue;
        java.sql.Timestamp regEnd   = (java.sql.Timestamp) fields.get(IDX_REG_END).rawValue;

        if (regEnd.before(regStart)) {
            Toast.makeText(this, "End date must be after start date", Toast.LENGTH_SHORT).show();
            return;
        }

        Event event = buildEvent();
        Log.d("CreateEvent", "eventId: " + event.getEventId());
        Log.d("CreateEvent", "organizerId: " + event.getOrganizerId());

        eventStorage.createEvent(event);
        Toast.makeText(this, "Event created!", Toast.LENGTH_SHORT).show();
        finish();
    }

    // save event helper
    private Event buildEvent() {
        int capacity = (Integer) fields.get(IDX_CAPACITY).rawValue;
        Event event = new Event(organizerId, Event.EventStatus.OPEN, capacity);

        event.setTitle(((String) fields.get(IDX_TITLE).rawValue).trim());
        event.setRegStart((java.sql.Timestamp) fields.get(IDX_REG_START).rawValue);
        event.setRegEnd((java.sql.Timestamp) fields.get(IDX_REG_END).rawValue);

        if (fields.get(IDX_DRAW_TIME).rawValue != null) {
            event.setDrawTime((java.sql.Timestamp) fields.get(IDX_DRAW_TIME).rawValue);
        }
        if (fields.get(IDX_POSTER_URL).rawValue != null) {
            event.setPosterUrl((String) fields.get(IDX_POSTER_URL).rawValue);
        }

        boolean waitlistEnabled = Boolean.TRUE.equals(fields.get(IDX_WAITLIST_ON).rawValue);
        if (waitlistEnabled) {
            Object wlCap = fields.get(IDX_WAITLIST_CAP).rawValue;
            event.setWaitlistCapacity(wlCap == null ? -1 : (Integer) wlCap);
        }

        return event;
    }

    // field adapter for displaying fields inside the ListView
    static class FieldAdapter extends ArrayAdapter<EventField> {

        FieldAdapter(Context context, List<EventField> items) {
            super(context, android.R.layout.simple_list_item_2, items);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(android.R.layout.simple_list_item_2, parent, false);
            }
            EventField field = getItem(position);

            TextView text1 = convertView.findViewById(android.R.id.text1);
            TextView text2 = convertView.findViewById(android.R.id.text2);

            text1.setText(field.label);
            text2.setText(field.displayValue);

            return convertView;
        }
    }
}