package com.example.lotteryapp.fragments;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.lotteryapp.R;
import com.example.lotteryapp.activities.EventDetailsActivity;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.services.ServiceLocator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class JoinedEventsAdapter extends RecyclerView.Adapter<JoinedEventsAdapter.ViewHolder> {

    public interface OnOptOutListener {
        void onOptOut();
    }

    private final List<Event> events;
    private final String userId;
    private OnOptOutListener optOutListener;

    public JoinedEventsAdapter(List<Event> events, String userId) {
        this.events = events;
        this.userId = userId;
    }

    public void setOnOptOutListener(OnOptOutListener listener) {
        this.optOutListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_joined_event, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Event event = events.get(position);
        holder.tvTitle.setText(event.getTitle());

        String location = (event.getAddress() != null) ? event.getAddress().getLocation() : "TBD";
        String dateStr = "TBD";
        if (event.getEventDateMs() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            dateStr = sdf.format(new Date(event.getEventDateMs()));
        }

        holder.tvDetails.setText(location + " • " + dateStr);

        // Load poster image
        if (event.getPosterUrl() != null && !event.getPosterUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(event.getPosterUrl())
                    .placeholder(R.drawable.ic_image_placeholder)
                    .centerCrop()
                    .into(holder.ivPoster);
        } else {
            holder.ivPoster.setImageResource(R.drawable.ic_image_placeholder);
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), EventDetailsActivity.class);
            intent.putExtra(EventDetailsActivity.EXTRA_EVENT_ID, event.getEventId());
            intent.putExtra("HIDE_JOIN_BUTTON", true);
            v.getContext().startActivity(intent);
        });

        holder.btnDownload.setOnClickListener(v -> {
            ServiceLocator.getEventPoolStorage().generateTicketPDF(v.getContext(), event, userId);
        });

        holder.btnOptOut.setOnClickListener(v -> {
            ServiceLocator.getEventPoolStorage().optOutEvent(
                    event.getEventId(),
                    userId,
                    aVoid -> {
                        Toast.makeText(v.getContext(), "Opted out successfully", Toast.LENGTH_SHORT).show();
                        int currentPos = holder.getBindingAdapterPosition();
                        if (currentPos != RecyclerView.NO_POSITION) {
                            events.remove(currentPos);
                            notifyItemRemoved(currentPos);
                            notifyItemRangeChanged(currentPos, events.size());
                            if (events.isEmpty() && optOutListener != null) {
                                optOutListener.onOptOut();
                            }
                        }
                    },
                    e -> Toast.makeText(v.getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
            );
        });
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDetails;
        ImageView ivPoster;
        Button btnDownload, btnOptOut;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_event_title);
            tvDetails = itemView.findViewById(R.id.tv_event_details);
            ivPoster = itemView.findViewById(R.id.iv_event_poster);
            btnDownload = itemView.findViewById(R.id.btn_download_ticket);
            btnOptOut = itemView.findViewById(R.id.btn_opt_out);
        }
    }
}
