package com.example.lotteryapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListEventsActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID    = "eventId";
    public static final String EXTRA_ORGANIZER_ID = "organizerId";

    private EventAdapter adapter;
    private final List<Event> events = new ArrayList<>();
    private final Map<String, Integer> entrantCounts = new HashMap<>();
    private EventPoolStorage eventPoolStorage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_events);

        eventPoolStorage = ServiceLocator.eventPoolStorage();

        adapter = new EventAdapter(this, events, entrantCounts);

        ListView listView = findViewById(R.id.listEvents);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) ->
                onEventClicked(events.get(position)));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadEvents();
    }

    private void loadEvents() {
        eventPoolStorage.listOpenEvents(
                fetched -> {
                    events.clear();
                    events.addAll(fetched);

                    // reset counts and show placeholder rows immediately
                    entrantCounts.clear();
                    adapter.notifyDataSetChanged();

                    // fetch entrant counts per event
                    //      for ListView capacity display later
                    for (Event e : events) {
                        String eventId = e.getEventId();

                        eventPoolStorage.countEntrants(
                                eventId,
                                count -> {
                                    entrantCounts.put(eventId, count);
                                    adapter.notifyDataSetChanged(); // refresh row text
                                },
                                err -> {
                                    // show unknown if count fails
                                    entrantCounts.put(eventId, -1);
                                    adapter.notifyDataSetChanged();
                                }
                        );
                    }
                },
                e -> Toast.makeText(this, "Failed to load events", Toast.LENGTH_SHORT).show()
        );
    }

    private void onEventClicked(Event event) {
        Intent intent = new Intent(this, EventDetailActivity.class);
        intent.putExtra(EXTRA_EVENT_ID, event.getEventId());
        intent.putExtra(EXTRA_ORGANIZER_ID, event.getOrganizerId());
        startActivity(intent);
    }

    // ArrayAdapter for EventDetails view
    //      properly set the two list items pertaining to the ListView in EventDetailsActivity
    //      text1: Title
    //      text2: Event Details

    static class EventAdapter extends ArrayAdapter<Event> {
        private final Map<String, Integer> entrantCounts;


        EventAdapter(Context context, List<Event> items, java.util.Map<String, Integer> entrantCounts) {
            super(context, android.R.layout.simple_list_item_2, items);
            this.entrantCounts = entrantCounts;
        }


        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(android.R.layout.simple_list_item_2, parent, false);
            }
            Event event = getItem(position);

            TextView text1 = convertView.findViewById(android.R.id.text1);
            TextView text2 = convertView.findViewById(android.R.id.text2);

            String title = event.getTitle();
            text1.setText(title != null && !title.trim().isEmpty() ? title : event.getEventId());

            Integer countObj = entrantCounts.get(event.getEventId());
            String currentStr;
            if (countObj == null) currentStr = "?";        // still loading
            else if (countObj < 0) currentStr = "?";       // failed
            else currentStr = String.valueOf(countObj);

            text2.setText("Capacity: " + currentStr + "/" + event.getCapacity()
                    + (event.getWaitlistCapacity() != null
                    ? "  |  Waitlist: " + (event.getWaitlistCapacity() == -1
                    ? "Unlimited" : event.getWaitlistCapacity())
                    : ""));

            return convertView;
        }
    }
}