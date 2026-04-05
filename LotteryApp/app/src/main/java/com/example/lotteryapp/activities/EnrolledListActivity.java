package com.example.lotteryapp.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lotteryapp.R;
import com.example.lotteryapp.models.Entrant;
import com.example.lotteryapp.models.User;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventPoolStorage;
import com.example.lotteryapp.services.storage.UserStorage;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * US 02.06.03 & 02.06.05
 * Displays final list of entrants enrolled in specified event and allows CSV export as a file.
 * Accessible by the organizer through Manage Events screen.
 */
public class EnrolledListActivity extends AppCompatActivity {
    public static final String EXTRA_EVENT_ID = "eventId";

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private MaterialButton btnExportCsv;
    private EntrantAdapter adapter;
    private EventPoolStorage eventPoolStorage;
    private UserStorage userStorage;
    private com.example.lotteryapp.services.storage.EventStorage eventStorage;
    private String eventId;
    private boolean isFinalList = false;

    /**
     * Initialize UI components for the enrolled entrants screen
     *  validate ids for entrants
     *  trigger initial data load
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enrolled_list);

        recyclerView = findViewById(R.id.rv_enrolled);
        tvEmpty = findViewById(R.id.tv_empty);
        MaterialButton btnClose = findViewById(R.id.btn_close);
        btnExportCsv = findViewById(R.id.btn_export_csv);

        eventPoolStorage = ServiceLocator.getEventPoolStorage();
        userStorage = ServiceLocator.getUserStorage();
        eventStorage = ServiceLocator.getEventStorage();

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EntrantAdapter(userStorage);
        recyclerView.setAdapter(adapter);

        btnClose.setOnClickListener(v -> finish());
        btnExportCsv.setOnClickListener(v -> exportToCSV());

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "Missing eventID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        checkEventStatus();
        loadEnrolledEntrants(eventId);
    }

    /**
     * Reads the event status so the export button can reflect whether the current export represents a final entrant list
     */
    private void checkEventStatus() {
        eventStorage.getEvent(eventId, event -> {
            if (event != null && (event.getStatus() == com.example.lotteryapp.models.Event.EventStatus.REG_FULL 
                    || event.getStatus() == com.example.lotteryapp.models.Event.EventStatus.EVENT_CLOSED)) {
                isFinalList = true;
                btnExportCsv.setText("Export Final CSV");
            }
        }, e -> {
            // Ignore status check failure
        });
    }

    /**
     * Loads all enrolled entrants for the supplied event and updates the empty list
     */
    private void loadEnrolledEntrants(String eventId) {
        eventPoolStorage.getEnrolledEntrants(
                eventId,
                entrants -> {
                    if (entrants.isEmpty()) {
                        // hide the list and disable exporting.
                        tvEmpty.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                        btnExportCsv.setEnabled(false);
                    } else {
                        // render entrants and allow export.
                        tvEmpty.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                        btnExportCsv.setEnabled(true);
                        adapter.setEntrants(entrants);
                    }
                },
                e -> {
                    Toast.makeText(this, "Failed to load entrants", Toast.LENGTH_SHORT).show();
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("Error loading entrants");
                    btnExportCsv.setEnabled(false);
                }
        );
    }

    /**
     * Gathers all enrolled entrants and their profiles for CSV export.
     */
    private void exportToCSV() {
        List<Entrant> entrants = adapter.getEntrants();
        if (entrants.isEmpty()) {
            Toast.makeText(this, "No entrants to export", Toast.LENGTH_SHORT).show();
            return;
        }

        // User profile reads are asynchronous, so count completions until every entrant lookup has either succeeded or failed.
        Map<String, User> userMap = new HashMap<>();
        AtomicInteger count = new AtomicInteger(0);
        int total = entrants.size();

        for (Entrant entrant : entrants) {
            userStorage.getUserProfile(entrant.getEntrantId(), user -> {
                if (user != null) {
                    userMap.put(entrant.getEntrantId(), user);
                }
                // Once all profile reads complete, build the CSV with whatever user details were successfully resolved
                if (count.incrementAndGet() == total) {
                    generateAndShareCSVFile(entrants, userMap);
                }
            }, e -> {
                // Once all profile reads complete, build the CSV with whatever user details were successfully resolved
                if (count.incrementAndGet() == total) {
                    generateAndShareCSVFile(entrants, userMap);
                }
            });
        }
    }

    /**
     * Builds the CSV string, writes it to a file, and triggers a share intent.
     */
    private void generateAndShareCSVFile(List<Entrant> entrants, Map<String, User> userMap) {
        StringBuilder csv = new StringBuilder();
        // header row expected by spreadsheet tools when the file is shared/opened
        csv.append("Name,Email,Phone,Status\n");

        for (Entrant entrant : entrants) {
            User user = userMap.get(entrant.getEntrantId());
            String name = (user != null && user.getName() != null) ? user.getName() : "Unknown";
            String email = (user != null && user.getEmail() != null) ? user.getEmail() : "N/A";
            String phone = (user != null && user.getPhoneNumber() != null) ? user.getPhoneNumber() : "N/A";
            String status = "Enrolled";

            csv.append(sanitize(name)).append(",")
                    .append(sanitize(email)).append(",")
                    .append(sanitize(phone)).append(",")
                    .append(sanitize(status)).append("\n");
        }

        try {
            // 1. Generate Physical File
            File cachePath = new File(getCacheDir(), "exports");
            cachePath.mkdirs();
            String fileName = isFinalList ? "Enrolled_Entrants_Final.csv" : "Enrolled_Entrants.csv";
            File csvFile = new File(cachePath, fileName);
            FileOutputStream stream = new FileOutputStream(csvFile);
            stream.write(csv.toString().getBytes());
            stream.close();

            // 2. Use FileProvider for URI
            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", csvFile);

            // 3. Update Intent to send file stream
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_SUBJECT, fileName);
            intent.putExtra(Intent.EXTRA_STREAM, contentUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            startActivity(Intent.createChooser(intent, isFinalList ? "Export Final Enrolled Entrants" : "Export Enrolled Entrants"));

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error generating CSV file", Toast.LENGTH_SHORT).show();
        }
    }

    /**
    * Removes CSV-breaking commas/newlines from exported values
     * takes in non-normalized value and returns it normalized
    */
    private String sanitize(String value) {
        if (value == null) return "";
        return value.replace(",", " ").replace("\n", " ").trim();
    }
    /**
     * RecyclerView adapter for displaying enrolled entrants with asynchronously loaded profile details
     */
    private static class EntrantAdapter extends RecyclerView.Adapter<EntrantAdapter.ViewHolder> {
        private final List<Entrant> entrants = new ArrayList<>();
        private final UserStorage userStorage;

        /**
         * Creates an adapter that can resolve entrant ids into user profiles
         */
        public EntrantAdapter(UserStorage userStorage) {
            this.userStorage = userStorage;
        }

        /**
         * replaces the current entrant list and refreshes the recycler view
         */
        public void setEntrants(List<Entrant> newEntrants) {
            entrants.clear();
            entrants.addAll(newEntrants);
            notifyDataSetChanged();
        }

        /**
        * Returns a copy of the current entrant list for export
        */
        public List<Entrant> getEntrants() {
            return new ArrayList<>(entrants);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_entrant, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Entrant entrant = entrants.get(position);
            // Show immediate placeholders until the user profile lookup finishes
            holder.tvEntrantName.setText("Loading...");
            holder.tvEntrantEmail.setText(entrant.getEntrantId());

            userStorage.getUserProfile(entrant.getEntrantId(), user -> {
                if (user != null) {
                    holder.tvEntrantName.setText(user.getName() != null && !user.getName().isEmpty() 
                            ? user.getName() : "Unknown Name");
                    holder.tvEntrantEmail.setText(user.getEmail() != null && !user.getEmail().isEmpty() 
                            ? user.getEmail() : "No email provided");
                } else {
                    holder.tvEntrantName.setText("User not found");
                    holder.tvEntrantEmail.setText(entrant.getEntrantId());
                }
            }, e -> {
                holder.tvEntrantName.setText("Error loading");
                holder.tvEntrantEmail.setText(entrant.getEntrantId());
            });

            // Organizer/admin can tap through to the entrant's full user details
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), UserDetailsActivity.class);
                intent.putExtra(UserDetailsActivity.EXTRA_USER_ID, entrant.getEntrantId());
                intent.putExtra(UserDetailsActivity.EXTRA_ADMIN_MODE, true);
                v.getContext().startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return entrants.size();
        }
        /**
         * ViewHolder that caches the two TextViews used in each entrant row
         */
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvEntrantName;
            TextView tvEntrantEmail;

            ViewHolder(View itemView) {
                super(itemView);
                tvEntrantName = itemView.findViewById(R.id.tv_entrant_name);
                tvEntrantEmail = itemView.findViewById(R.id.tv_entrant_email);
            }
        }
    }
}
