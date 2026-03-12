package com.example.lotteryapp.activities;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lotteryapp.R;
import com.example.lotteryapp.models.Entrant;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventPoolStorage;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * US 02.06.03
 * Displays final list of entrants enrolled in specified event.
 * POV: Organizer
 */
public class EnrolledListActivity extends AppCompatActivity {
    public static final String EXTRA_EVENT_ID = "eventId";

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private MaterialButton btnClose;
    private EntrantAdapter adapter;
    private EventPoolStorage eventPoolStorage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enrolled_list);

        recyclerView = findViewById(R.id.rv_enrolled);
        tvEmpty = findViewById(R.id.tv_empty);
        btnClose = findViewById(R.id.btn_close);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EntrantAdapter();
        recyclerView.setAdapter(adapter);

        btnClose.setOnClickListener(v -> finish());

        eventPoolStorage = ServiceLocator.getEventPoolStorage();

        String eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "Missing eventID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadEnrolledEntrants(eventId);
    }

    private void loadEnrolledEntrants(String eventId) {
        eventPoolStorage.getEnrolledEntrants(
                eventId,
                entrants -> {
                    if (entrants.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    } else {
                        tvEmpty.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                        adapter.setEntrants(entrants);
                    }
                },
                e -> {
                    Toast.makeText(this, "Failed to load entrants", Toast.LENGTH_SHORT).show();
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("Error loading entrants");
                }
        );
    }


    /**
     * display entrant ids in list
     */
    private static class EntrantAdapter extends RecyclerView.Adapter<EntrantAdapter.ViewHolder> {

        private final List<Entrant> entrants = new ArrayList<>();

        public void setEntrants(List<Entrant> newEntrants) {
            entrants.clear();
            entrants.addAll(newEntrants);
            notifyDataSetChanged();
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
            holder.tvEntrantId.setText(entrant.getEntrantId());
            holder.tvStatus.setText(entrant.getStatus().name());
        }

        @Override
        public int getItemCount() {
            return entrants.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvEntrantId;
            TextView tvStatus;

            ViewHolder(View itemView) {
                super(itemView);
                tvEntrantId = itemView.findViewById(R.id.tv_entrant_id);
                tvStatus = itemView.findViewById(R.id.tv_entrant_status);
            }
        }
    }
}