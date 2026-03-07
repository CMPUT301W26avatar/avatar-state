package com.example.lotteryapp;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    private List<HomeFragment.DisplayGridEvent> events;

    public EventAdapter(List<HomeFragment.DisplayGridEvent> events) {
        this.events = events;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        HomeFragment.DisplayGridEvent event = events.get(position);
        holder.tvTitle.setText(event.title);
        holder.tvSubtitle.setText(event.subtitle);
        holder.tvDesc.setText(event.description);
        holder.tvTag.setText(event.tag);

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), EventDetailsActivity.class);
            intent.putExtra("event_name", event.title);
            intent.putExtra("event_location", event.subtitle);
            intent.putExtra("event_description", event.description);
            intent.putExtra("event_tag", event.tag);
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSubtitle, tvDesc, tvTag;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvSubtitle = itemView.findViewById(R.id.tv_subtitle);
            tvDesc = itemView.findViewById(R.id.tv_desc);
            tvTag = itemView.findViewById(R.id.tv_tag);
        }
    }
}