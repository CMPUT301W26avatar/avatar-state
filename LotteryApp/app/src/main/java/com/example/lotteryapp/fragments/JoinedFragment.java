package com.example.lotteryapp.fragments;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.lotteryapp.R;
import com.example.lotteryapp.activities.EventDetailsActivity;
import com.example.lotteryapp.activities.UserEventHistoryActivity;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventPoolStorage;
import com.example.lotteryapp.services.storage.EventStorage;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JoinedFragment displays the user's enrolled events in a swipeable pager
 * and provides a shortcut to the full event history screen.
 */
public class JoinedFragment extends Fragment {

    private EventStorage eventStorage;
    private EventPoolStorage eventPoolStorage;

    private ViewPager2 joinedEventsPager;
    private LinearLayout joinedEventDots;
    private TextView tvJoinedEmpty;
    private View joinedEventCard;

    private JoinedEventPagerAdapter pagerAdapter;
    private final List<Event> enrolledEvents = new ArrayList<>();
    private MaterialButton btnMoreEvents;
    private View extraEventCard;
    private TextView tvExtraSectionTitle;
    private TextView tvExtraEmpty;
    private ViewPager2 extraEventsPager;
    private LinearLayout extraEventDots;

    private JoinedEventPagerAdapter extraPagerAdapter;
    private final List<Event> extraEvents = new ArrayList<>();

    private enum ExtraEventFilter {
        SELECTED,
        WAITLISTED,
        PRIVATE_INVITE
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.fragment_joined, container, false);

        eventStorage = ServiceLocator.getEventStorage();
        eventPoolStorage = ServiceLocator.getEventPoolStorage();

        View historyButton = view.findViewById(R.id.btn_history);
        historyButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), UserEventHistoryActivity.class);
            startActivity(intent);
        });

        joinedEventCard = view.findViewById(R.id.joined_event_card);
        joinedEventsPager = view.findViewById(R.id.joined_events_pager);
        joinedEventDots = view.findViewById(R.id.joined_event_dots);
        tvJoinedEmpty = view.findViewById(R.id.tv_joined_empty);

        pagerAdapter = new JoinedEventPagerAdapter(enrolledEvents);
        joinedEventsPager.setAdapter(pagerAdapter);

        joinedEventsPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                renderPagerDots(position);
            }
        });

        btnMoreEvents = view.findViewById(R.id.btn_more_events);
        extraEventCard = view.findViewById(R.id.extra_event_card);
        tvExtraSectionTitle = view.findViewById(R.id.tv_extra_section_title);
        tvExtraEmpty = view.findViewById(R.id.tv_extra_empty);
        extraEventsPager = view.findViewById(R.id.extra_events_pager);
        extraEventDots = view.findViewById(R.id.extra_event_dots);

        extraPagerAdapter = new JoinedEventPagerAdapter(extraEvents);
        extraEventsPager.setAdapter(extraPagerAdapter);

        extraEventsPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                renderExtraPagerDots(position);
            }
        });

        btnMoreEvents.setOnClickListener(v -> showMoreEventsMenu(v));

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadJoinedEvents();
    }

    /**
     * Launches EventDetailsActivity for the selected event.
     */
    private void openEventDetails(Event event) {
        if (event == null || event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            Toast.makeText(requireContext(), "Missing eventId", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(requireContext(), EventDetailsActivity.class);
        intent.putExtra(EventDetailsActivity.EXTRA_EVENT_ID, event.getEventId());
        startActivity(intent);
    }

    /**
     * Placeholder for the confirmation ticket action.
     * Replace with your real confirmation ticket activity once wired.
     */
    private void openConfirmationTicket(Event event) {
        openEventDetails(event);
    }

    private void showMoreEventsMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, "Selected");
        menu.getMenu().add(0, 2, 1, "Waitlisted");
        menu.getMenu().add(0, 3, 2, "Private Invite");

        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                loadExtraEvents(ExtraEventFilter.SELECTED);
                return true;
            }
            if (item.getItemId() == 2) {
                loadExtraEvents(ExtraEventFilter.WAITLISTED);
                return true;
            }
            if (item.getItemId() == 3) {
                Toast.makeText(requireContext(), "Private Invite not implemented yet", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });

        menu.show();
    }

    /**
     * Loads enrolled events for the current user and resolves them into full Event objects.
     */
    private void loadJoinedEvents() {
        String uid = ServiceLocator.uid();
        if (uid == null || uid.trim().isEmpty()) {
            showEmptyState("User not signed in");
            return;
        }

        if (eventPoolStorage == null || eventStorage == null) {
            showEmptyState("Unable to load joined events");
            return;
        }

        enrolledEvents.clear();
        if (pagerAdapter != null) {
            pagerAdapter.notifyDataSetChanged();
        }
        updatePagerVisibility();

        eventPoolStorage.getEnrolledEventIdsForUser(
                uid,
                this::resolveEnrolledEvents,
                e -> {
                    showEmptyState("Failed to load joined events");
                    if (getContext() != null) {
                        Toast.makeText(requireContext(), "Failed to load joined events", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void loadExtraEvents(ExtraEventFilter filter) {
        String uid = ServiceLocator.uid();
        if (uid == null || uid.trim().isEmpty()) {
            showExtraEmptyState("User not signed in");
            return;
        }

        if (eventPoolStorage == null || eventStorage == null) {
            showExtraEmptyState("Unable to load events");
            return;
        }

        extraEventCard.setVisibility(View.VISIBLE);
        extraEvents.clear();
        extraPagerAdapter.notifyDataSetChanged();
        updateExtraPagerVisibility();

        if (filter == ExtraEventFilter.SELECTED) {
            tvExtraSectionTitle.setText("Selected Events");
            eventPoolStorage.getInvitedEventIdsForUser(
                    uid,
                    this::resolveExtraEvents,
                    e -> {
                        Toast.makeText(requireContext(),
                                "Failed to load selected events: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        showExtraEmptyState("Failed to load selected events");
                        Log.e("JoinedFragment", "Failed to load selected events: " + e.getMessage());
                    }
            );
            return;
        }

        if (filter == ExtraEventFilter.WAITLISTED) {
            tvExtraSectionTitle.setText("Waitlisted Events");
            eventPoolStorage.getWaitlistedEventIdsForUser(
                    uid,
                    this::resolveExtraEvents,
                    e -> {
                        Toast.makeText(requireContext(),
                                "Failed to load waitlisted events: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        showExtraEmptyState("Failed to load waitlisted events");
                        Log.e("JoinedFragment", "Failed to load selected events: " + e.getMessage());
                    }
            );
            return;
        }

        if (filter == ExtraEventFilter.PRIVATE_INVITE) {
            tvExtraSectionTitle.setText("Private Invite Events");
            showExtraEmptyState("Private Invite not implemented yet.");
        }
    }


    /**
     * Resolves event IDs into full Event objects, then sorts them by upcoming event date ascending.
     */
    private void resolveEnrolledEvents(List<String> eventIds) {
        enrolledEvents.clear();

        if (eventIds == null || eventIds.isEmpty()) {
            updatePagerVisibility();
            return;
        }

        List<String> uniqueEventIds = new ArrayList<>();
        for (String eventId : eventIds) {
            if (eventId != null
                    && !eventId.trim().isEmpty()
                    && !uniqueEventIds.contains(eventId)) {
                uniqueEventIds.add(eventId);
            }
        }

        if (uniqueEventIds.isEmpty()) {
            updatePagerVisibility();
            return;
        }

        AtomicInteger remaining = new AtomicInteger(uniqueEventIds.size());
        long now = System.currentTimeMillis();

        for (String eventId : uniqueEventIds) {
            eventStorage.getEvent(
                    eventId,
                    fetchedEvent -> {
                        if (fetchedEvent != null) {
                            enrolledEvents.add(fetchedEvent);
                        }

                        if (remaining.decrementAndGet() == 0) {
                            finalizeJoinedEventLoad(now);
                        }
                    },
                    e -> {
                        if (remaining.decrementAndGet() == 0) {
                            finalizeJoinedEventLoad(now);
                        }
                    }
            );
        }
    }

    private void resolveExtraEvents(List<String> eventIds) {
        extraEvents.clear();

        if (eventIds == null || eventIds.isEmpty()) {
            updateExtraPagerVisibility();
            return;
        }

        List<String> uniqueEventIds = new ArrayList<>();
        for (String eventId : eventIds) {
            if (eventId != null
                    && !eventId.trim().isEmpty()
                    && !uniqueEventIds.contains(eventId)) {
                uniqueEventIds.add(eventId);
            }
        }

        if (uniqueEventIds.isEmpty()) {
            updateExtraPagerVisibility();
            return;
        }

        AtomicInteger remaining = new AtomicInteger(uniqueEventIds.size());
        long now = System.currentTimeMillis();

        for (String eventId : uniqueEventIds) {
            eventStorage.getEvent(
                    eventId,
                    fetchedEvent -> {
                        if (fetchedEvent != null) {
                            extraEvents.add(fetchedEvent);
                        }

                        if (remaining.decrementAndGet() == 0) {
                            finalizeExtraEventLoad(now);
                        }
                    },
                    e -> {
                        if (remaining.decrementAndGet() == 0) {
                            finalizeExtraEventLoad(now);
                        }
                    }
            );
        }
    }

    private void finalizeExtraEventLoad(long now) {
        Collections.sort(extraEvents, Comparator.comparingLong(ev -> {
            Long eventDateMs = ev.getEventDateMs();
            if (eventDateMs == null) return Long.MAX_VALUE;
            long diff = eventDateMs - now;
            return diff < 0 ? Long.MAX_VALUE - Math.abs(diff) : diff;
        }));

        extraPagerAdapter.notifyDataSetChanged();
        updateExtraPagerVisibility();
    }

    private void finalizeJoinedEventLoad(long now) {
        Collections.sort(enrolledEvents, Comparator.comparingLong(ev -> {
            Long eventDateMs = ev.getEventDateMs();
            if (eventDateMs == null) {
                return Long.MAX_VALUE;
            }
            long diff = eventDateMs - now;
            return diff < 0 ? Long.MAX_VALUE - Math.abs(diff) : diff;
        }));

        if (pagerAdapter != null) {
            pagerAdapter.notifyDataSetChanged();
        }
        updatePagerVisibility();
    }

    /**
     * Shows pager/dots when events exist, or an empty state when none exist.
     */
    private void updatePagerVisibility() {
        if (joinedEventsPager == null || joinedEventDots == null || tvJoinedEmpty == null) {
            return;
        }

        if (enrolledEvents == null || enrolledEvents.isEmpty()) {
            tvJoinedEmpty.setText("No enrolled events yet.");
            tvJoinedEmpty.setVisibility(View.VISIBLE);
            joinedEventsPager.setVisibility(View.GONE);
            joinedEventDots.setVisibility(View.GONE);
            return;
        }

        tvJoinedEmpty.setVisibility(View.GONE);
        joinedEventsPager.setVisibility(View.VISIBLE);

        pagerAdapter.notifyDataSetChanged();

        if (pagerAdapter.getItemCount() > 1) {
            joinedEventDots.setVisibility(View.VISIBLE);
        } else {
            joinedEventDots.setVisibility(View.GONE);
        }

        if (pagerAdapter.getItemCount() > 0) {
            joinedEventsPager.setCurrentItem(0, false);
            renderPagerDots(0);
        }
    }

    private void updateExtraPagerVisibility() {
        if (extraEvents.isEmpty()) {
            tvExtraEmpty.setVisibility(View.VISIBLE);
            extraEventsPager.setVisibility(View.GONE);
            extraEventDots.setVisibility(View.GONE);
            return;
        }

        tvExtraEmpty.setVisibility(View.GONE);
        extraEventsPager.setVisibility(View.VISIBLE);
        extraEventDots.setVisibility(extraPagerAdapter.getItemCount() > 1 ? View.VISIBLE : View.GONE);

        if (extraPagerAdapter.getItemCount() > 0) {
            extraEventsPager.setCurrentItem(0, false);
            renderExtraPagerDots(0);
        }
    }

    private void showEmptyState(String message) {
        enrolledEvents.clear();

        if (pagerAdapter != null) {
            pagerAdapter.notifyDataSetChanged();
        }

        if (tvJoinedEmpty != null) {
            tvJoinedEmpty.setText(message);
            tvJoinedEmpty.setVisibility(View.VISIBLE);
        }

        if (joinedEventsPager != null) {
            joinedEventsPager.setVisibility(View.GONE);
        }

        if (joinedEventDots != null) {
            joinedEventDots.setVisibility(View.GONE);
        }
    }

    private void showExtraEmptyState(String message) {
        extraEvents.clear();

        if (extraPagerAdapter != null) {
            extraPagerAdapter.notifyDataSetChanged();
        }

        if (tvExtraEmpty != null) {
            tvExtraEmpty.setText(message);
            tvExtraEmpty.setVisibility(View.VISIBLE);
        }

        if (extraEventsPager != null) {
            extraEventsPager.setVisibility(View.GONE);
        }

        if (extraEventDots != null) {
            extraEventDots.setVisibility(View.GONE);
        }
    }

    /**
     * Renders pager indicator dots similar to HomeFragment.
     */
    private void renderPagerDots(int selectedIndex) {
        joinedEventDots.removeAllViews();

        if (getContext() == null || enrolledEvents.size() <= 1) {
            joinedEventDots.setVisibility(View.GONE);
            return;
        }

        joinedEventDots.setVisibility(View.VISIBLE);

        int sizePx = dpToPx(8);
        int marginPx = dpToPx(3);

        for (int i = 0; i < enrolledEvents.size(); i++) {
            View dot = new View(requireContext());

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizePx, sizePx);
            params.setMargins(marginPx, 0, marginPx, 0);
            dot.setLayoutParams(params);

            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(resolveDotColor());
            dot.setBackground(drawable);
            dot.setAlpha(i == selectedIndex ? 1f : 0.35f);

            joinedEventDots.addView(dot);
        }
    }

    private void renderExtraPagerDots(int selectedIndex) {
        extraEventDots.removeAllViews();

        if (getContext() == null || extraEvents.size() <= 1) {
            extraEventDots.setVisibility(View.GONE);
            return;
        }

        extraEventDots.setVisibility(View.VISIBLE);

        int sizePx = dpToPx(8);
        int marginPx = dpToPx(3);

        for (int i = 0; i < extraEvents.size(); i++) {
            View dot = new View(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizePx, sizePx);
            params.setMargins(marginPx, 0, marginPx, 0);
            dot.setLayoutParams(params);

            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(resolveDotColor());
            dot.setBackground(drawable);
            dot.setAlpha(i == selectedIndex ? 1f : 0.35f);

            extraEventDots.addView(dot);
        }
    }

    private int dpToPx(int dp) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private int resolveDotColor() {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorOnTertiaryContainer,
                typedValue,
                true
        );
        return typedValue.data;
    }

    /**
     * Builds the small subtitle for the joined-event card.
     */
    private String buildJoinedSubtitle(Event event) {
        String location = "Unknown location";
        if (event != null
                && event.getAddress() != null
                && event.getAddress().getLocation() != null
                && !event.getAddress().getLocation().trim().isEmpty()) {
            location = event.getAddress().getLocation();
        }

        String dateText = "Unknown date";
        if (event != null && event.getEventDateMs() != null) {
            dateText = DateFormat.format("MMM dd, yyyy", new Date(event.getEventDateMs())).toString();
        }

        return location + " • " + dateText;
    }

    /**
     * Adapter for the joined-event pager.
     */
    private final class JoinedEventPagerAdapter
            extends RecyclerView.Adapter<JoinedEventPagerAdapter.JoinedEventViewHolder> {

        private final List<Event> events;

        JoinedEventPagerAdapter(List<Event> events) {
            this.events = events;
        }

        @NonNull
        @Override
        public JoinedEventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_swipeable_large_event_card, parent, false);
            return new JoinedEventViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull JoinedEventViewHolder holder, int position) {
            Event event = events.get(position);

            String title = event.getTitle();
            holder.title.setText(title != null && !title.trim().isEmpty() ? title : event.getEventId());
            holder.subtitle.setText(buildJoinedSubtitle(event));

            holder.btnTicket.setOnClickListener(v -> openConfirmationTicket(event));
            holder.btnDetails.setOnClickListener(v -> openEventDetails(event));
        }

        @Override
        public int getItemCount() {
            return events.size();
        }

        final class JoinedEventViewHolder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView subtitle;
            final MaterialButton btnTicket;
            final MaterialButton btnDetails;

            JoinedEventViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.tv_joined_event_title);
                subtitle = itemView.findViewById(R.id.tv_joined_event_subtitle);
                btnTicket = itemView.findViewById(R.id.btn_view_confirmation_ticket);
                btnDetails = itemView.findViewById(R.id.btn_joined_event_details);
            }
        }
    }
}