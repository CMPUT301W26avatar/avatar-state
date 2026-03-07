package com.example.lotteryapp;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
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

public class UpdateEventActivity extends AppCompatActivity {

    enum FieldType { TEXT, NUMBER, DATE, TOGGLE }

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

    // indexes
    private static final int IDX_EVENT_ID     = 0;
    private static final int IDX_TITLE        = 1;
    private static final int IDX_STATUS       = 2; // toggle OPEN/CLOSED/ENDED via dialog
    private static final int IDX_CAPACITY     = 3;
    private static final int IDX_WAITLIST_ON  = 4;
    private static final int IDX_WAITLIST_CAP = 5;
    private static final int IDX_REG_START    = 6;
    private static final int IDX_REG_END      = 7;
    private static final int IDX_DRAW_TIME    = 8;
    private static final int IDX_POSTER_URL   = 9;

    private final List<EventField> fields = new ArrayList<>();
    private FieldAdapter adapter;

    private EventStorage eventStorage;

    private Button btnLoad, btnSave, btnBack;

    private final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_event);

        eventStorage = ServiceLocator.eventStorage();

        buildFields();

        adapter = new FieldAdapter(fields);
        ListView listView = findViewById(R.id.listFields);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> onFieldClicked(position));

        btnLoad = findViewById(R.id.btnLoadEvent);
        btnSave = findViewById(R.id.btnSaveEvent);
        btnBack = findViewById(R.id.btnBack);

        btnLoad.setOnClickListener(v -> loadEvent());
        btnSave.setOnClickListener(v -> saveChanges());
        btnBack.setOnClickListener(v -> finish());

        // until an event is loaded, saving isn't meaningful
        btnSave.setEnabled(false);
    }

    private void buildFields() {
        fields.add(new EventField("Event ID *",          FieldType.TEXT,   "Tap to enter"));
        fields.add(new EventField("Title *",             FieldType.TEXT,   "—"));
        fields.add(new EventField("Status",              FieldType.TEXT,   "—")); // handled via picker dialog
        fields.add(new EventField("Capacity *",          FieldType.NUMBER, "—"));
        fields.add(new EventField("Waitlist Enabled",    FieldType.TOGGLE, "No"));
        fields.add(new EventField("Waitlist Capacity",   FieldType.NUMBER, "—"));
        fields.add(new EventField("Registration Start *",FieldType.DATE,   "—"));
        fields.add(new EventField("Registration End *",  FieldType.DATE,   "—"));
        fields.add(new EventField("Draw Date",           FieldType.DATE,   "—"));
        fields.add(new EventField("Poster URL",          FieldType.TEXT,   "—"));
    }

    private void onFieldClicked(int index) {
        // Event ID is editable; everything else is editable after load, too.
        EventField field = fields.get(index);

        if (index == IDX_STATUS) {
            showStatusPicker();
            return;
        }

        switch (field.type) {
            case TEXT:
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

    private void showTextDialog(int index) {
        EventField field = fields.get(index);
        EditText input = new EditText(this);
        input.setInputType(field.type == FieldType.NUMBER
                ? InputType.TYPE_CLASS_NUMBER
                : InputType.TYPE_CLASS_TEXT);

        if (field.rawValue != null) input.setText(String.valueOf(field.rawValue));
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

    private void showStatusPicker() {
        final String[] options = new String[] {
                Event.EventStatus.OPEN.name(),
                Event.EventStatus.CLOSED.name(),
                Event.EventStatus.ENDED.name()
        };

        new AlertDialog.Builder(this)
                .setTitle("Status")
                .setItems(options, (d, which) -> {
                    String picked = options[which];
                    EventField f = fields.get(IDX_STATUS);
                    f.rawValue = picked;
                    f.displayValue = picked;
                    adapter.notifyDataSetChanged();
                })
                .show();
    }

    private void loadEvent() {
        Object raw = fields.get(IDX_EVENT_ID).rawValue;
        String eventId = raw instanceof String ? ((String) raw).trim() : "";
        if (eventId.isEmpty()) {
            Toast.makeText(this, "Event ID is required", Toast.LENGTH_SHORT).show();
            return;
        }

        eventStorage.getEvent(
                eventId,
                event -> {
                    // Fill fields from loaded event
                    setText(IDX_TITLE, event.getTitle());
                    setText(IDX_STATUS, event.getStatus() != null ? event.getStatus().name() : Event.EventStatus.OPEN.name());
                    setNumber(IDX_CAPACITY, event.getCapacity());

                    boolean hasWaitlist = event.getWaitlistCapacity() != null;
                    fields.get(IDX_WAITLIST_ON).rawValue = hasWaitlist;
                    fields.get(IDX_WAITLIST_ON).displayValue = hasWaitlist ? "Yes" : "No";

                    if (hasWaitlist) {
                        Integer wl = event.getWaitlistCapacity();
                        setNumberNullable(IDX_WAITLIST_CAP, wl);
                    } else {
                        fields.get(IDX_WAITLIST_CAP).rawValue = null;
                        fields.get(IDX_WAITLIST_CAP).displayValue = "Not set";
                    }

                    setDate(IDX_REG_START, event.getRegStart());
                    setDate(IDX_REG_END, event.getRegEnd());
                    setDate(IDX_DRAW_TIME, event.getDrawTime());

                    setText(IDX_POSTER_URL, event.getPosterUrl());

                    adapter.notifyDataSetChanged();
                    btnSave.setEnabled(true);

                    Toast.makeText(this, "Event loaded", Toast.LENGTH_SHORT).show();
                },
                e -> Toast.makeText(this, "Failed to load event", Toast.LENGTH_SHORT).show()
        );
    }

    private void saveChanges() {
        String eventId = stringField(IDX_EVENT_ID);
        if (eventId.isEmpty()) {
            Toast.makeText(this, "Event ID is required", Toast.LENGTH_SHORT).show();
            return;
        }

        String title = stringField(IDX_TITLE);
        if (title.isEmpty()) {
            Toast.makeText(this, "Title is required", Toast.LENGTH_SHORT).show();
            return;
        }

        Integer capacity = intField(IDX_CAPACITY);
        if (capacity == null) {
            Toast.makeText(this, "Capacity is required", Toast.LENGTH_SHORT).show();
            return;
        }

        Timestamp regStart = (Timestamp) fields.get(IDX_REG_START).rawValue;
        Timestamp regEnd   = (Timestamp) fields.get(IDX_REG_END).rawValue;
        if (regStart == null || regEnd == null) {
            Toast.makeText(this, "Registration start/end required", Toast.LENGTH_SHORT).show();
            return;
        }
        if (regEnd.before(regStart)) {
            Toast.makeText(this, "End date must be after start date", Toast.LENGTH_SHORT).show();
            return;
        }

        Event.EventStatus status = parseStatus(stringField(IDX_STATUS));

        boolean waitlistEnabled = Boolean.TRUE.equals(fields.get(IDX_WAITLIST_ON).rawValue);
        Integer waitlistCapacity = null;
        if (waitlistEnabled) {
            // Blank = unlimited => store -1 (same convention you used in Create)
            Integer wlCap = intField(IDX_WAITLIST_CAP);
            waitlistCapacity = (wlCap == null) ? -1 : wlCap;
        } else {
            waitlistCapacity = null; // disables waitlist
        }

        Timestamp drawTime = (Timestamp) fields.get(IDX_DRAW_TIME).rawValue; // optional (null ok)
        String posterUrl = stringOrNull(IDX_POSTER_URL);

        // Call storage
        eventStorage.updateEvent(
                eventId,
                title,
                status,
                capacity,
                waitlistCapacity,
                regStart,
                regEnd,
                drawTime,
                posterUrl
        );

        Toast.makeText(this, "Event updated!", Toast.LENGTH_SHORT).show();
        finish();
    }

    private Event.EventStatus parseStatus(String s) {
        if (s == null) return Event.EventStatus.OPEN;
        try { return Event.EventStatus.valueOf(s); }
        catch (IllegalArgumentException ignored) { return Event.EventStatus.OPEN; }
    }

    private String stringField(int idx) {
        Object raw = fields.get(idx).rawValue;
        return raw instanceof String ? ((String) raw).trim() : "";
    }

    private String stringOrNull(int idx) {
        String s = stringField(idx);
        return s.isEmpty() ? null : s;
    }

    private Integer intField(int idx) {
        Object raw = fields.get(idx).rawValue;
        if (raw instanceof Integer) return (Integer) raw;
        if (raw instanceof String) {
            String s = ((String) raw).trim();
            if (s.isEmpty()) return null;
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private void setText(int idx, String val) {
        fields.get(idx).rawValue = val;
        fields.get(idx).displayValue = (val == null || val.trim().isEmpty()) ? "Not set" : val;
    }

    private void setNumber(int idx, int val) {
        fields.get(idx).rawValue = val;
        fields.get(idx).displayValue = String.valueOf(val);
    }

    private void setNumberNullable(int idx, Integer val) {
        fields.get(idx).rawValue = val;
        fields.get(idx).displayValue = (val == null) ? "Not set" : String.valueOf(val);
    }

    private void setDate(int idx, Timestamp ts) {
        fields.get(idx).rawValue = ts;
        fields.get(idx).displayValue = (ts == null) ? "Not set" : sdf.format(ts);
    }

    // Adapter (same idea as CreateEventsActivity)
    static class FieldAdapter extends ArrayAdapter<EventField> {
        FieldAdapter(List<EventField> items) {
            super(ServiceLocator.firebase().getDb().getApp().getApplicationContext(),
                    android.R.layout.simple_list_item_2, items);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext())
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