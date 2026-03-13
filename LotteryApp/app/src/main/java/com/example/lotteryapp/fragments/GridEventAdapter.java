package com.example.lotteryapp.fragments;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lotteryapp.R;
import com.example.lotteryapp.activities.EventDetailsActivity;

import java.util.List;

/**
 * RecyclerView adapter used to display events in a grid on the Home screen.
 * - Each grid item represents an event summarized with a title, subtitle,
 * description, and status tag.
 *      Selecting an event opens the EventDetailsActivity for that event
 */
public class GridEventAdapter extends RecyclerView.Adapter<GridEventAdapter.EventViewHolder> {

    private List<HomeFragment.DisplayGridEvent> events;

    public GridEventAdapter(List<HomeFragment.DisplayGridEvent> events) {
        this.events = events;
    }

    /**
     * Creates a new EventViewHolder for an event item view.
     *
     * Takes parameters:
     *      parent view group that the item view will be attached to
     *      view type of the new view
     * returns a new EventViewHolder containing the inflated layout
     */

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(view);
    }


    /**
     * Binds event data to a view holder for display.
     * - sets the event title, subtitle, description, and tag.
     * - attaches a click listener that opens the event details page
     * Takes parameters:
     *      view holder representing the event item
     *      position of the event in the dataset
     */

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        HomeFragment.DisplayGridEvent event = events.get(position);
        holder.tvTitle.setText(event.title);
        holder.tvSubtitle.setText(event.subtitle);
        holder.tvDesc.setText(event.description);
        holder.tvTag.setText(event.tag);

        View.OnClickListener openDetails =v -> {
            Intent intent = new Intent(v.getContext(), EventDetailsActivity.class);
            intent.putExtra(EventDetailsActivity.EXTRA_EVENT_ID, event.eventId);
            v.getContext().startActivity(intent);
        };
        // clicking on the item opens
        holder.itemView.setOnClickListener(openDetails);
    }

    /**
     * Returns the number of events currently displayed
     */

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