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
import com.example.lotteryapp.services.TicketService;
import com.example.lotteryapp.services.storage.EventPoolStorage;
import com.example.lotteryapp.services.storage.EventStorage;
import com.example.lotteryapp.services.storage.NotificationLogStorage;
import com.example.lotteryapp.utils.DepthPageTransformer;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
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
    private NotificationLogStorage notificationLogStorage;
    private TicketService ticketService;

    private ViewPager2 joinedEventsPager;
    private LinearLayout joinedEventDots;
    private TextView tvJoinedEmpty;

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

    /**
     * inflates the joined fragment layout, initializes storage dependencies, binds views, and sets up pager and button interactions
     */
    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        // inflate the fragment layout and keep the root view for view binding
        View view = inflater.inflate(R.layout.fragment_joined, container, false);

        // resolve shared services used to load events, notifications, and tickets
        eventStorage = ServiceLocator.getEventStorage();
        eventPoolStorage = ServiceLocator.getEventPoolStorage();
        notificationLogStorage = ServiceLocator.getNotificationLogStorage();
        ticketService = ServiceLocator.getTicketService();

        // open the full user event history screen when the history button is pressed
        View historyButton = view.findViewById(R.id.btn_history);
        historyButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), UserEventHistoryActivity.class);
            startActivity(intent);
        });

        // bind primary enrolled events pager and its related empty state and dot indicator views
        joinedEventsPager = view.findViewById(R.id.joined_events_pager);
        joinedEventDots = view.findViewById(R.id.joined_event_dots);
        tvJoinedEmpty = view.findViewById(R.id.tv_joined_empty);

        // attach adapter backed by enrolled events list
        pagerAdapter = new JoinedEventPagerAdapter(enrolledEvents, true);
        joinedEventsPager.setAdapter(pagerAdapter);
        joinedEventsPager.setPageTransformer(new DepthPageTransformer());

        // keep pager dots in sync with the currently visible enrolled event card
        joinedEventsPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                renderPagerDots(position);
            }
        });

        // bind views for the secondary extra events section shown from the dropdown filter
        btnMoreEvents = view.findViewById(R.id.btn_more_events);
        extraEventCard = view.findViewById(R.id.extra_event_card);
        tvExtraSectionTitle = view.findViewById(R.id.tv_extra_section_title);
        tvExtraEmpty = view.findViewById(R.id.tv_extra_empty);
        extraEventsPager = view.findViewById(R.id.extra_events_pager);
        extraEventDots = view.findViewById(R.id.extra_event_dots);

        // attach adapter backed by the currently selected extra events category
        extraPagerAdapter = new JoinedEventPagerAdapter(extraEvents, false);
        extraEventsPager.setAdapter(extraPagerAdapter);
        extraEventsPager.setPageTransformer(new DepthPageTransformer());

        // keep extra section dots in sync with the visible extra event card
        extraEventsPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                renderExtraPagerDots(position);
            }
        });

        // show popup menu allowing the user to switch between extra event categories
        btnMoreEvents.setOnClickListener(this::showMoreEventsMenu);

        return view;
    }

    /**
     * On fragment reload, reload the JoinedEvents
     */
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
        if (!isAdded()) {
            return;
        }

        String uid = ServiceLocator.uid();
        if (uid == null || uid.trim().isEmpty()) {
            Toast.makeText(requireContext(), "User not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        if (event == null) {
            Toast.makeText(requireContext(), "Missing event", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ticketService == null) {
            Toast.makeText(requireContext(), "Ticket service unavailable", Toast.LENGTH_SHORT).show();
            return;
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Confirmation Ticket")
                .setItems(new CharSequence[]{"View Ticket", "Download Ticket"}, (dialog, which) -> {
                    if (which == 0) {
                        ticketService.previewTicketPDF(requireContext(), event, uid);
                    } else if (which == 1) {
                        ticketService.downloadTicketPDF(requireContext(), event, uid);
                    }
                })
                .show();
    }

    /**
     * shows a popup menu anchored to the given view allowing the user to select which extra event category to load
     */
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
                loadExtraEvents(ExtraEventFilter.PRIVATE_INVITE);
                return true;
            }
            return false;
        });

        menu.show();
    }

    /**
     * loads enrolled event ids for the current user and prepares ui for async resolution into full events
     */
    private void loadJoinedEvents() {
        String uid = ServiceLocator.uid();

        // guard against missing authentication since all queries depend on uid
        if (uid == null || uid.trim().isEmpty()) {
            showEmptyState("User not signed in");
            return;
        }

        // ensure required storage dependencies exist before querying
        if (eventPoolStorage == null || eventStorage == null) {
            showEmptyState("Unable to load joined events");
            return;
        }

        // reset existing data so stale events are not shown during reload
        enrolledEvents.clear();
        if (pagerAdapter != null) {
            pagerAdapter.notifyDataSetChanged();
        }

        // hide ui elements during loading to avoid flicker of empty state
        if (tvJoinedEmpty != null) tvJoinedEmpty.setVisibility(View.GONE);
        if (joinedEventsPager != null) joinedEventsPager.setVisibility(View.GONE);
        if (joinedEventDots != null) joinedEventDots.setVisibility(View.GONE);

        // fetch enrolled event ids then resolve them into full event objects
        eventPoolStorage.getEnrolledEventIdsForUser(
                uid,
                this::resolveEnrolledEvents,
                e -> {
                    // fallback ui and user feedback on failure
                    showEmptyState("Failed to load joined events");
                    if (getContext() != null) {
                        Toast.makeText(requireContext(), "Failed to load joined events", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    /**
     * loads additional event categories based on selected filter and prepares ui for async resolution
     */
    private void loadExtraEvents(ExtraEventFilter filter) {
        String uid = ServiceLocator.uid();

        // validate user before attempting any queries
        if (uid == null || uid.trim().isEmpty()) {
            showExtraEmptyState("User not signed in");
            return;
        }

        // ensure dependencies are available
        if (eventPoolStorage == null || eventStorage == null) {
            showExtraEmptyState("Unable to load events");
            return;
        }

        // hide section while loading so stale titles or content do not persist
        if (extraEventCard != null) {
            extraEventCard.setVisibility(View.GONE);
        }

        // reset previous results
        extraEvents.clear();
        if (extraPagerAdapter != null) {
            extraPagerAdapter.notifyDataSetChanged();
        }

        // hide all ui elements until new data arrives
        if (tvExtraEmpty != null) tvExtraEmpty.setVisibility(View.GONE);
        if (extraEventsPager != null) extraEventsPager.setVisibility(View.GONE);
        if (extraEventDots != null) extraEventDots.setVisibility(View.GONE);

        // route query based on selected filter type
        if (filter == ExtraEventFilter.SELECTED) {
            tvExtraSectionTitle.setText("Selected Events");

            // invited events correspond to lottery selected users
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

            // waitlisted events represent users not yet selected
            eventPoolStorage.getWaitlistedEventIdsForUser(
                    uid,
                    this::resolveExtraEvents,
                    e -> {
                        Toast.makeText(requireContext(),
                                "Failed to load waitlisted events: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        showExtraEmptyState("Failed to load waitlisted events");
                        Log.e("JoinedFragment", "Failed to load waitlisted events: " + e.getMessage());
                    }
            );
            return;
        }

        if (filter == ExtraEventFilter.PRIVATE_INVITE) {
            tvExtraSectionTitle.setText("Private Invite Events");

            // private invites are stored in notification logs rather than event pool
            notificationLogStorage.getPrivateInviteEventIdsForUser(
                    uid,
                    this::resolveExtraEvents,
                    e -> {
                        Toast.makeText(requireContext(),
                                "Failed to load private invite events: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        showExtraEmptyState("Failed to load private invite events");
                        Log.e("JoinedFragment", "Failed to load private invite events: " + e.getMessage());
                    }
            );
        }
    }


    /**
     * Resolves event IDs into full Event objects, then sorts them by upcoming event date ascending.
     */
    private void resolveEnrolledEvents(List<String> eventIds) {
        enrolledEvents.clear();

        // no ids means nothing to resolve so update ui immediately
        if (eventIds == null || eventIds.isEmpty()) {
            updatePagerVisibility();
            return;
        }

        // remove null, empty, and duplicate ids to avoid redundant fetches
        List<String> uniqueEventIds = new ArrayList<>();
        for (String eventId : eventIds) {
            if (eventId != null
                    && !eventId.trim().isEmpty()
                    && !uniqueEventIds.contains(eventId)) {
                uniqueEventIds.add(eventId);
            }
        }

        // if all ids were invalid exit early
        if (uniqueEventIds.isEmpty()) {
            updatePagerVisibility();
            return;
        }

        // track remaining async fetches so we know when all events are resolved
        AtomicInteger remaining = new AtomicInteger(uniqueEventIds.size());
        long now = System.currentTimeMillis();

        for (String eventId : uniqueEventIds) {
            eventStorage.getEvent(
                    eventId,
                    fetchedEvent -> {

                        // only add valid events to the list
                        if (fetchedEvent != null) {
                            enrolledEvents.add(fetchedEvent);
                        }

                        // when all async calls complete finalize sorting and ui update
                        if (remaining.decrementAndGet() == 0) {
                            finalizeJoinedEventLoad(now);
                        }
                    },
                    e -> {
                        // still decrement on failure to avoid hanging completion
                        if (remaining.decrementAndGet() == 0) {
                            finalizeJoinedEventLoad(now);
                        }
                    }
            );
        }
    }

    /**
     * loads additional event categories based on selected filter and prepares ui for async resolution
     */
    private void resolveExtraEvents(List<String> eventIds) {
        extraEvents.clear();

        // no ids means nothing to resolve so update ui immediately
        if (eventIds == null || eventIds.isEmpty()) {
            updateExtraPagerVisibility();
            return;
        }

        // remove null, empty, and duplicate ids to avoid redundant fetches
        List<String> uniqueEventIds = new ArrayList<>();
        for (String eventId : eventIds) {
            if (eventId != null
                    && !eventId.trim().isEmpty()
                    && !uniqueEventIds.contains(eventId)) {
                uniqueEventIds.add(eventId);
            }
        }

        // if all ids were invalid exit early
        if (uniqueEventIds.isEmpty()) {
            updateExtraPagerVisibility();
            return;
        }

        // track remaining async fetches so we know when all events are resolved
        AtomicInteger remaining = new AtomicInteger(uniqueEventIds.size());
        long now = System.currentTimeMillis();

        for (String eventId : uniqueEventIds) {
            eventStorage.getEvent(
                    eventId,
                    fetchedEvent -> {
                        // only add valid events to the list
                        if (fetchedEvent != null) {
                            extraEvents.add(fetchedEvent);
                        }
                        // when all async calls complete finalize sorting and ui update
                        if (remaining.decrementAndGet() == 0) {
                            finalizeExtraEventLoad(now);
                        }
                    },
                    e -> {
                        // still decrement on failure to avoid hanging completion
                        if (remaining.decrementAndGet() == 0) {
                            finalizeExtraEventLoad(now);
                        }
                    }
            );
        }
    }

    /**
     * sorts enrolled events by upcoming date and updates pager ui once all async loads complete
     */
    private void finalizeJoinedEventLoad(long now) {

        // sort events by closeness to current time placing past events at the end
        enrolledEvents.sort(Comparator.comparingLong(ev -> {
            Long eventDateMs = ev.getEventDateMs();

            // null dates treated as lowest priority
            if (eventDateMs == null) {
                return Long.MAX_VALUE;
            }

            long diff = eventDateMs - now;

            // push past events to the end while preserving relative ordering
            return diff < 0 ? Long.MAX_VALUE - Math.abs(diff) : diff;
        }));

        if (pagerAdapter != null) {
            pagerAdapter.notifyDataSetChanged();
        }

        // refresh visibility based on final dataset
        updatePagerVisibility();
    }

    private void finalizeExtraEventLoad(long now) {

        // sort events by closeness to current time placing past events at the end
        extraEvents.sort(Comparator.comparingLong(ev -> {
            Long eventDateMs = ev.getEventDateMs();

            // null dates treated as lowest priority
            if (eventDateMs == null) {
                return Long.MAX_VALUE;
            }

            long diff = eventDateMs - now;

            // push past events to the end while preserving relative ordering
            return diff < 0 ? Long.MAX_VALUE - Math.abs(diff) : diff;
        }));

        extraPagerAdapter.notifyDataSetChanged();

        // refresh visibility based on final dataset
        updateExtraPagerVisibility();
    }


    /**
     * updates visibility of enrolled pager and empty state based on enrolled event data
     */
    private void updatePagerVisibility() {

        // ensure views are initialized before attempting updates
        if (joinedEventsPager == null || joinedEventDots == null || tvJoinedEmpty == null) {
            return;
        }

        // show empty state when no events exist
        if (enrolledEvents == null || enrolledEvents.isEmpty()) {
            tvJoinedEmpty.setText("No enrolled events yet.");
            tvJoinedEmpty.setVisibility(View.VISIBLE);
            joinedEventsPager.setVisibility(View.GONE);
            joinedEventDots.setVisibility(View.GONE);
            return;
        }

        // show pager when data exists
        tvJoinedEmpty.setVisibility(View.GONE);
        joinedEventsPager.setVisibility(View.VISIBLE);

        pagerAdapter.notifyDataSetChanged();

        // only show dots if multiple pages exist
        if (pagerAdapter.getItemCount() > 1) {
            joinedEventDots.setVisibility(View.VISIBLE);
        } else {
            joinedEventDots.setVisibility(View.GONE);
        }

        // reset pager position to first item after data update
        if (pagerAdapter.getItemCount() > 0) {
            joinedEventsPager.setCurrentItem(0, false);
            renderPagerDots(0);
        }
    }

    /**
     * updates visibility of extra pager and empty state based on extra event data
     */
    private void updateExtraPagerVisibility() {
        if (extraEventCard != null) {
            extraEventCard.setVisibility(View.VISIBLE);
        }

        // show empty state when no events exist
        if (extraEvents.isEmpty()) {
            tvExtraEmpty.setVisibility(View.VISIBLE);
            extraEventsPager.setVisibility(View.GONE);
            extraEventDots.setVisibility(View.GONE);
            return;
        }

        // show pager when data exists
        tvExtraEmpty.setVisibility(View.GONE);
        extraEventsPager.setVisibility(View.VISIBLE);

        // only show dots if multiple pages exist
        extraEventDots.setVisibility(extraPagerAdapter.getItemCount() > 1 ? View.VISIBLE : View.GONE);

        // reset pager position to first item after data update
        if (extraPagerAdapter.getItemCount() > 0) {
            extraEventsPager.setCurrentItem(0, false);
            renderExtraPagerDots(0);
        }
    }

    /**
     * clears enrolled data and displays an empty state message while hiding pager ui
     */
    private void showEmptyState(String message) {

        // remove all enrolled events so stale data is not shown
        enrolledEvents.clear();

        // notify adapter so recycler reflects cleared data
        if (pagerAdapter != null) {
            pagerAdapter.notifyDataSetChanged();
        }

        // display empty state message to user
        if (tvJoinedEmpty != null) {
            tvJoinedEmpty.setText(message);
            tvJoinedEmpty.setVisibility(View.VISIBLE);
        }

        // hide pager since there is no data to display
        if (joinedEventsPager != null) {
            joinedEventsPager.setVisibility(View.GONE);
        }

        // hide dots since pager is not visible
        if (joinedEventDots != null) {
            joinedEventDots.setVisibility(View.GONE);
        }
    }

    /**
     * clears extra events and displays empty state for the extra events section
     */
    private void showExtraEmptyState(String message) {

        // clear extra event data to prevent stale results
        extraEvents.clear();

        // notify adapter so ui reflects cleared dataset
        if (extraPagerAdapter != null) {
            extraPagerAdapter.notifyDataSetChanged();
        }

        // ensure section container remains visible even if empty
        if (extraEventCard != null) {
            extraEventCard.setVisibility(View.VISIBLE);
        }

        // show empty state message for extra events
        if (tvExtraEmpty != null) {
            tvExtraEmpty.setText(message);
            tvExtraEmpty.setVisibility(View.VISIBLE);
        }

        // hide pager since no extra events exist
        if (extraEventsPager != null) {
            extraEventsPager.setVisibility(View.GONE);
        }

        // hide dots since pager is hidden or has no items
        if (extraEventDots != null) {
            extraEventDots.setVisibility(View.GONE);
        }
    }

    /**
     * Renders pager indicator dots similar to HomeFragment.
     */
    private void renderPagerDots(int selectedIndex) {
        joinedEventDots.removeAllViews();

        // hide dots if context is unavailable or only one page exists
        if (getContext() == null || enrolledEvents.size() <= 1) {
            joinedEventDots.setVisibility(View.GONE);
            return;
        }

        joinedEventDots.setVisibility(View.VISIBLE);

        // convert dp values to pixels for consistent sizing across devices
        int sizePx = dpToPx(8);
        int marginPx = dpToPx(3);

        for (int i = 0; i < enrolledEvents.size(); i++) {
            // create dot view representing a page
            View dot = new View(requireContext());

            // apply size and spacing between dots
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizePx, sizePx);
            params.setMargins(marginPx, 0, marginPx, 0);
            dot.setLayoutParams(params);

            // create circular drawable for dot appearance
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(resolveDotColor());
            dot.setBackground(drawable);

            // highlight selected dot and dim others
            dot.setAlpha(i == selectedIndex ? 1f : 0.35f);

            joinedEventDots.addView(dot);
        }
    }
    /**
     * renders pagination dots for extra events pager and highlights the selected index
     */
    private void renderExtraPagerDots(int selectedIndex) {

        // clear existing dots before rebuilding
        extraEventDots.removeAllViews();

        // hide dots if context missing or only one item exists
        if (getContext() == null || extraEvents.size() <= 1) {
            extraEventDots.setVisibility(View.GONE);
            return;
        }

        extraEventDots.setVisibility(View.VISIBLE);

        int sizePx = dpToPx(8);
        int marginPx = dpToPx(3);

        for (int i = 0; i < extraEvents.size(); i++) {

            // create visual dot for each page
            View dot = new View(requireContext());

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizePx, sizePx);
            params.setMargins(marginPx, 0, marginPx, 0);
            dot.setLayoutParams(params);

            // style dot as circle with theme color
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(resolveDotColor());
            dot.setBackground(drawable);

            // set opacity based on selection state
            dot.setAlpha(i == selectedIndex ? 1f : 0.35f);

            extraEventDots.addView(dot);
        }
    }

    /**
     * converts density independent pixels to actual pixels based on device density
     */
    private int dpToPx(int dp) {

        // retrieve screen density to scale dp values correctly
        float density = requireContext().getResources().getDisplayMetrics().density;

        return Math.round(dp * density);
    }

    /**
     * resolves theme color used for pager dots from material theme attributes
     */
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
     * adapter that binds event data into swipeable pager cards for display
     */
    private final class JoinedEventPagerAdapter
            extends RecyclerView.Adapter<JoinedEventPagerAdapter.JoinedEventViewHolder> {

        private final List<Event> events;
        private final boolean showConfirmationTicketButton;

        JoinedEventPagerAdapter(List<Event> events, boolean showConfirmationTicketButton) {
            this.events = events;
            this.showConfirmationTicketButton = showConfirmationTicketButton;
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

            if (showConfirmationTicketButton) {
                holder.btnTicket.setVisibility(View.VISIBLE);
                holder.btnTicket.setOnClickListener(v -> openConfirmationTicket(event));
            } else {
                holder.btnTicket.setVisibility(View.GONE);
            }

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