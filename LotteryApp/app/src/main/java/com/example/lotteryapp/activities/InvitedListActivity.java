package com.example.lotteryapp.activities;

import android.content.Intent;
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
import com.example.lotteryapp.models.User;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventPoolStorage;
import com.example.lotteryapp.services.storage.UserStorage;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * US 02.06.01
 * Displays list of entrants invited (winners) to specified event.
 * Accessible by the organizer through Manage Events screen.
 * Gets invited entrants from EventPoolStorage and displays them.
 * Shows empty message if none invited.
 */
public class InvitedListActivity extends AppCompatActivity {
    public static final String EXTRA_EVENT_ID = "eventId";

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private MaterialButton btnClose;
    private EntrantAdapter adapter;
    private EventPoolStorage eventPoolStorage;
    private UserStorage userStorage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invited_list);

        recyclerView = findViewById(R.id.rv_invited);
        tvEmpty = findViewById(R.id.tv_empty_invited);
        btnClose = findViewById(R.id.btn_close_invited);

        eventPoolStorage = ServiceLocator.getEventPoolStorage();
        userStorage = ServiceLocator.getUserStorage();

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EntrantAdapter(userStorage);
        recyclerView.setAdapter(adapter);

        btnClose.setOnClickListener(v -> finish());

        String eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "Missing eventID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadInvitedEntrants(eventId);
    }

    private void loadInvitedEntrants(String eventId) {
        eventPoolStorage.getInvitedEntrants(
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
                    Toast.makeText(this, "Failed to load invited entrants", Toast.LENGTH_SHORT).show();
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("Error loading invited entrants");
                }
        );
    }

    private static class EntrantAdapter extends RecyclerView.Adapter<EntrantAdapter.ViewHolder> {
        private final List<Entrant> entrants = new ArrayList<>();
        private final UserStorage userStorage;

        public EntrantAdapter(UserStorage userStorage) {
            this.userStorage = userStorage;
        }

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
