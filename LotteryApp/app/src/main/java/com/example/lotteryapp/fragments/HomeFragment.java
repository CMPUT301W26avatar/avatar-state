package com.example.lotteryapp.fragments;

import static com.example.lotteryapp.models.NotificationLog.NotificationStatus.ACCEPTED;
import static com.example.lotteryapp.models.NotificationLog.NotificationStatus.DECLINED;
import static com.example.lotteryapp.models.NotificationLog.NotificationType.PRIVATE_INVITATION;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.lotteryapp.R;
import com.example.lotteryapp.models.Entrant;
import com.example.lotteryapp.models.NotificationLog;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.services.storage.EventPoolStorage;
import com.example.lotteryapp.services.storage.EventStorage;
import com.example.lotteryapp.services.storage.NotificationLogStorage;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * HomeFragment displays the main event dashboard of the application.
 *
 * displays events in a grid (DisplayGridEvent), db query determines the type
 *      load Events from EventStorage,
 *          Event -turns-into> DisplayGridEvent,
 *          Events -into-> adapter
 *          adapter notifies of a change in the list
 *      GridEvents -into-> recyclerViews
 *      HomeFragment loads recyclerViews
 */
public class HomeFragment extends Fragment {

    private MaterialCardView invitationCard;
    private MaterialButton closeInvitation;
    private EventStorage estore = ServiceLocator.getEventStorage();
    private EventPoolStorage eventPoolStorage = ServiceLocator.getEventPoolStorage();
    private GridEventAdapter openAdapter;
    private GridEventAdapter upcomingAdapter;
    private GridEventAdapter fullAdapter;
    private List<HomeFragment.DisplayGridEvent> displayOpenGridEvents;
    private List<HomeFragment.DisplayGridEvent> displayUpcomingGridEvents;
    private List<HomeFragment.DisplayGridEvent> displayFullGridEvents;

    private ViewPager2 notificationsPager;
    private NotificationLogStorage notificationLogStorage = ServiceLocator.getNotificationLogStorage();

    private NotificationPagerAdapter notificationPagerAdapter;
    private final List<UserNotification> notifications = new ArrayList<>();

    /**
     * Inflates the HomeFragment layout and initializes UI components.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        notificationsPager = view.findViewById(R.id.notifications_pager);

        notificationPagerAdapter = new NotificationPagerAdapter(
                notifications,
                this::dismissNotificationAt,
                this::handleNotificationClicked
        );
        notificationsPager.setAdapter(notificationPagerAdapter);

        notificationsPager.setVisibility(View.GONE);

        RecyclerView recyclerViewOpen = view.findViewById(R.id.recycler_view_open);
        recyclerViewOpen.setLayoutManager(new GridLayoutManager(getContext(), 2));
        RecyclerView recyclerViewUpcoming = view.findViewById(R.id.recycler_view_upcoming);
        recyclerViewUpcoming.setLayoutManager(new GridLayoutManager(getContext(), 2));
        RecyclerView recyclerViewFull = view.findViewById(R.id.recycler_view_full);
        recyclerViewFull.setLayoutManager(new GridLayoutManager(getContext(), 2));

        displayOpenGridEvents = new ArrayList<>();
        displayUpcomingGridEvents = new ArrayList<>();
        displayFullGridEvents = new ArrayList<>();

        openAdapter = new GridEventAdapter(displayOpenGridEvents);
        upcomingAdapter = new GridEventAdapter(displayUpcomingGridEvents);
        fullAdapter = new GridEventAdapter(displayFullGridEvents);

        recyclerViewOpen.setAdapter(openAdapter);
        recyclerViewUpcoming.setAdapter(upcomingAdapter);
        recyclerViewFull.setAdapter(fullAdapter);

        loadEventsRegOpen();
        loadEventsRegUpcoming();
        loadEventsRegFull();
        loadNotifications();

        return view;
    }
    /**
     * Reloads event lists whenever the fragment comes into view again.
     *      ensures the dashboard reflects the most recent event(s)
     */
    @Override
    public void onResume() {
        super.onResume();
        loadEventsRegOpen();
        loadEventsRegUpcoming();
        loadEventsRegFull();
        loadNotifications();
    }

    /**
     * Populate the grid event adapters with the most recent events with open registration window.
     *      (regStart < time.now < regEnd && waitlistCapacity > waitlistCount)
     *      call EventStorage.listEventsRegOpen query to fill events list
     *      convert Event to DisplayGridEvent
     *      notify change in the list of grid events
     */
    private void loadEventsRegOpen() {
        if (estore == null || openAdapter == null) return;

        estore.listEventsRegOpen(
                4,
                fetchedEvents -> {
                    displayOpenGridEvents.clear();
                    for (Event e : fetchedEvents) {
                        displayOpenGridEvents.add(eventToDisplayEvent(e));
                    }
                    openAdapter.notifyDataSetChanged();
                },
                e -> {
                    android.util.Log.e("HomeFragment", "Failed to load open events", e);
                }
        );
    }

    /**
     * Populate the grid event adapters with the most recent events with upcoming registration window.
     *      (time.now < regStart)
     *      call EventStorage.listEventsRegOpen query to fill events list
     *      convert Event to DisplayGridEvent
     *      notify change in the list of grid events
     */
    private void loadEventsRegUpcoming() {
        if (estore == null || upcomingAdapter == null) return;

        estore.listEventsRegUpcoming(
                4,
                fetchedEvents -> {
                    displayUpcomingGridEvents.clear();
                    for (Event e : fetchedEvents) {
                        displayUpcomingGridEvents.add(eventToDisplayEvent(e));
                    }
                    upcomingAdapter.notifyDataSetChanged();
                },
                e -> {
                    android.util.Log.e("HomeFragment", "Failed to load upcoming events", e);
                }
        );
    }

    /**
     * Populate the grid event adapters with the most recent events that are full.
     *      (waitlistCapacity = waitlistCount)
     *      call EventStorage.listEventsRegOpen query to fill events list
     *      convert Event to DisplayGridEvent
     *      notify change in the list of grid events
     *
     *      ** later: Popular Events should return events ordered by descending (waitlistCount/waitlistCapacity)**
     */
    private void loadEventsRegFull() {
        if (estore == null || fullAdapter == null) return;

        estore.listEventsRegFull(
                4,
                fetchedEvents -> {
                    displayFullGridEvents.clear();
                    for (Event e : fetchedEvents) {
                        displayFullGridEvents.add(eventToDisplayEvent(e));
                    }
                    fullAdapter.notifyDataSetChanged();
                },
                e -> {
                    android.util.Log.e("HomeFragment", "Failed to load full events", e);
                }
        );
    }

    private void loadNotifications() {
        String uid = ServiceLocator.uid();
        if (uid == null || uid.trim().isEmpty()) {
            notificationsPager.setVisibility(View.GONE);
            return;
        }

        if (notificationLogStorage == null) {
            Log.e("HomeFragment", "NotificationLogStorage is null");
            notificationsPager.setVisibility(View.GONE);
            return;
        }

        notificationLogStorage.getPendingNotificationsForUser(
                uid,
                logs -> {
                    notifications.clear();

                    for (NotificationLog log : logs) {
                        notifications.add(new UserNotification(
                                log.getId(),
                                log.getEventId(),
                                log.getType() == null ? null : log.getType().name(),
                                log.getTitle(),
                                log.getMessage(),
                                false
                        ));
                    }

                    notificationPagerAdapter.notifyDataSetChanged();
                    updateNotificationBannerVisibility();
                },
                e -> {
                    Log.e("HomeFragment", "Failed to load notifications", e);
                    notificationsPager.setVisibility(View.GONE);
                }
        );
    }

    private void updateNotificationBannerVisibility() {
        if (notifications.isEmpty()) {
            notificationsPager.setVisibility(View.GONE);
            return;
        }

        notificationsPager.setVisibility(View.VISIBLE);
        notificationPagerAdapter.notifyDataSetChanged();
        notificationsPager.setCurrentItem(0, false);
    }

    private void dismissNotificationAt(int position) {
        if (position < 0 || position >= notifications.size()) {
            return;
        }

        notifications.remove(position);
        notificationPagerAdapter.notifyDataSetChanged();

        if (notifications.isEmpty()) {
            notificationsPager.setVisibility(View.GONE);
            return;
        }

        int nextIndex = Math.min(position, notifications.size() - 1);
        notificationsPager.setCurrentItem(nextIndex, false);
    }

    private boolean isActionableNotification(UserNotification notification) {
        if (notification == null || notification.type == null) {
            return false;
        }

        return "PRIVATE_INVITATION".equals(notification.type)
                || "COORGANIZER_INVITATION".equals(notification.type);
    }

    private void handleNotificationClicked(int position) {
        if (position < 0 || position >= notifications.size()) {
            return;
        }

        UserNotification notification = notifications.get(position);
        if (!isActionableNotification(notification)) {
            return;
        }

        if ("PRIVATE_INVITATION".equals(notification.type)) {
            showPrivateInviteDialog(position, notification);
        } else if ("COORGANIZER_INVITATION".equals(notification.type)) {
            showCoorganizerInviteDialog(position, notification);
        }
    }

    private void showPrivateInviteDialog(int position, UserNotification notification) {
        if (getContext() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Private event invitation")
                .setMessage("Would you like to accept this private event invitation?")
                .setNegativeButton("Decline", (dialog, which) -> declinePrivateInvite(position, notification))
                .setPositiveButton("Accept", (dialog, which) -> acceptPrivateInvite(position, notification))
                .show();
    }

    private void showCoorganizerInviteDialog(int position, UserNotification notification) {
        if (getContext() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Co-organizer invitation")
                .setMessage("Would you like to accept this co-organizer invitation?")
                .setNegativeButton("Decline", (dialog, which) -> declineCoorganizerInvite(position, notification))
                .setPositiveButton("Accept", (dialog, which) -> acceptCoorganizerInvite(position, notification))
                .show();
    }

    private void acceptPrivateInvite(int position, UserNotification notification) {
        String uid = ServiceLocator.uid();

        if (notification == null || notification.eventId == null || notification.eventId.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Missing event for invite", Toast.LENGTH_SHORT).show();
            return;
        }

        if (uid == null || uid.trim().isEmpty()) {
            Toast.makeText(requireContext(), "User not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        Entrant entrant = new Entrant(uid, notification.eventId, Entrant.EntrantStatus.ENROLLED);

        eventPoolStorage.enrollInEvent(
                notification.eventId,
                entrant,
                unused -> resolveNotification(
                        position,
                        notification,
                        ACCEPTED.name(),
                        "Joined private event"
                ),
                e -> Toast.makeText(
                        requireContext(),
                        e.getMessage() == null ? "Failed to join private event" : e.getMessage(),
                        Toast.LENGTH_SHORT
                ).show()
        );
    }

    private void declinePrivateInvite(int position, UserNotification notification) {
        String uid = ServiceLocator.uid();

        if (notification == null || notification.eventId == null || notification.eventId.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Missing event for invite", Toast.LENGTH_SHORT).show();
            return;
        }

        if (uid == null || uid.trim().isEmpty()) {
            Toast.makeText(requireContext(), "User not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        eventPoolStorage.deleteEntryByStatus(
                notification.eventId,
                uid,
                Entrant.EntrantStatus.INVITED,
                unused -> resolveNotification(
                        position,
                        notification,
                        DECLINED.name(),
                        "Private invite declined"
                ),
                e -> Toast.makeText(
                        requireContext(),
                        e.getMessage() == null ? "Failed to decline private invite" : e.getMessage(),
                        Toast.LENGTH_SHORT
                ).show()
        );
    }

    private void acceptCoorganizerInvite(int position, UserNotification notification) {
        String uid = ServiceLocator.uid();

        if (notification == null || notification.eventId == null || notification.eventId.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Missing event for invite", Toast.LENGTH_SHORT).show();
            return;
        }

        if (uid == null || uid.trim().isEmpty()) {
            Toast.makeText(requireContext(), "User not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        estore.acceptInviteToBeCoorganizer(
                notification.eventId,
                uid,
                unused -> resolveNotification(
                        position,
                        notification,
                        ACCEPTED.name(),
                        "You are now a co-organizer"
                ),
                e -> Toast.makeText(
                        requireContext(),
                        e.getMessage() == null ? "Failed to accept co-organizer invite" : e.getMessage(),
                        Toast.LENGTH_SHORT
                ).show()
        );
    }

    private void declineCoorganizerInvite(int position, UserNotification notification) {
        String uid = ServiceLocator.uid();

        if (notification == null || notification.eventId == null || notification.eventId.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Missing event for invite", Toast.LENGTH_SHORT).show();
            return;
        }

        if (uid == null || uid.trim().isEmpty()) {
            Toast.makeText(requireContext(), "User not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        estore.declineInviteToBeCoorganizer(
                notification.eventId,
                uid,
                unused -> resolveNotification(
                        position,
                        notification,
                        DECLINED.name(),
                        "Co-organizer invite declined"
                ),
                e -> Toast.makeText(
                        requireContext(),
                        e.getMessage() == null ? "Failed to decline co-organizer invite" : e.getMessage(),
                        Toast.LENGTH_SHORT
                ).show()
        );
    }



    private static final class UserNotification {
        String id;
        String eventId;
        String type;
        String title;
        String message;
        boolean read;

        UserNotification(
                String id,
                String eventId,
                String type,
                String title,
                String message,
                boolean read
        ) {
            this.id = id;
            this.eventId = eventId;
            this.type = type;
            this.title = title;
            this.message = message;
            this.read = read;
        }
    }

    private void resolveNotification(
            int position,
            UserNotification notification,
            String status,
            String successMessage
    ) {
        if (notification == null || notification.id == null || notification.id.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Missing notification", Toast.LENGTH_SHORT).show();
            return;
        }

        notificationLogStorage.updateNotificationStatus(
                notification.id,
                status,
                unused -> {
                    Toast.makeText(requireContext(), successMessage, Toast.LENGTH_SHORT).show();
                    dismissNotificationAt(position);
                    loadNotifications();
                },
                e -> Toast.makeText(
                        requireContext(),
                        e.getMessage() == null ? "Failed to update notification" : e.getMessage(),
                        Toast.LENGTH_SHORT
                ).show()
        );
    }


    private static final class NotificationPagerAdapter
            extends RecyclerView.Adapter<NotificationPagerAdapter.NotificationViewHolder> {

        interface OnDismissClickListener {
            void onDismissClicked(int position);
        }

        interface OnNotificationClickListener {
            void onNotificationClicked(int position);
        }

        private final List<UserNotification> notifications;
        private final OnDismissClickListener dismissClickListener;
        private final OnNotificationClickListener notificationClickListener;

        NotificationPagerAdapter(List<UserNotification> notifications,
                                 OnDismissClickListener dismissClickListener,
                                 OnNotificationClickListener notificationClickListener) {
            this.notifications = notifications;
            this.dismissClickListener = dismissClickListener;
            this.notificationClickListener = notificationClickListener;
        }

        @NonNull
        @Override
        public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_home_notification, parent, false);
            return new NotificationViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
            UserNotification notification = notifications.get(position);

            String typeText = notification.title == null || notification.title.trim().isEmpty()
                    ? "Notification"
                    : "Notification: " + notification.title.trim();

            String message = notification.message == null || notification.message.trim().isEmpty()
                    ? "You have a new notification."
                    : notification.message.trim();

            holder.header.setText(typeText);
            holder.message.setText(message);
            bindDots(holder.dotsInline, position);


            holder.root.setOnClickListener(v -> {
                if (notificationClickListener != null) {
                    int adapterPosition = holder.getBindingAdapterPosition();
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        notificationClickListener.onNotificationClicked(adapterPosition);
                    }
                }
            });

            holder.closeButton.setOnClickListener(v -> {
                if (dismissClickListener != null) {
                    int adapterPosition = holder.getBindingAdapterPosition();
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        dismissClickListener.onDismissClicked(adapterPosition);
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return notifications.size();
        }

        static final class NotificationViewHolder extends RecyclerView.ViewHolder {
            View root;
            TextView header;
            TextView message;
            MaterialButton closeButton;
            LinearLayout dotsInline;

            NotificationViewHolder(@NonNull View itemView) {
                super(itemView);
                root = itemView.findViewById(R.id.notification_root);
                header = itemView.findViewById(R.id.tv_notification_header);
                message = itemView.findViewById(R.id.tv_notification_message);
                closeButton = itemView.findViewById(R.id.btn_close_notification);
                dotsInline = itemView.findViewById(R.id.notification_dots_inline);
            }
        }

        private void bindDots(LinearLayout container, int selectedIndex) {
            container.removeAllViews();

            if (container.getContext() == null || notifications.size() <= 1) {
                container.setVisibility(View.GONE);
                return;
            }

            container.setVisibility(View.VISIBLE);

            int sizePx = dpToPx(container, 8);
            int marginPx = dpToPx(container, 3);

            for (int i = 0; i < notifications.size(); i++) {
                View dot = new View(container.getContext());

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizePx, sizePx);
                params.setMargins(marginPx, 0, marginPx, 0);
                dot.setLayoutParams(params);

                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.OVAL);
                drawable.setColor(resolveDotColor(container));
                dot.setBackground(drawable);
                dot.setAlpha(i == selectedIndex ? 1f : 0.35f);

                container.addView(dot);
            }
        }

        private int dpToPx(View view, int dp) {
            float density = view.getResources().getDisplayMetrics().density;
            return Math.round(dp * density);
        }

        private int resolveDotColor(View view) {
            android.util.TypedValue typedValue = new android.util.TypedValue();
            view.getContext().getTheme().resolveAttribute(
                    com.google.android.material.R.attr.colorOnTertiaryContainer,
                    typedValue,
                    true
            );
            return typedValue.data;
        }
    }


    /**
     * Simple event model for the UI
     *      Displays the
     *          title,
     *          description,
     *          "Home fragment" (to implement),
     *          and a subtitle (EventStatus based String)
     *       for an event
     */
    public static class DisplayGridEvent {
        public String eventId, title, subtitle, description, tag;

        public DisplayGridEvent(String eventId, String title, String subtitle, String description, String tag) {
            this.eventId = eventId;
            this.title = title;
            this.subtitle = subtitle;
            this.description = description;
            this.tag = "Home fragment";
        }
    }

    /**
     * Converts an Event parameter into a DisplayGridEvent
     */
    private HomeFragment.DisplayGridEvent eventToDisplayEvent(Event event) {
        return new HomeFragment.DisplayGridEvent(
                event.getEventId(),
                event.getTitle(),
                buildSubtitle(event),
                event.getDescription(),
                event.getTag()
        );
    }

    /**
     * Status-based string builder for DisplayEventGrid subtitles
     *      need Event param
     */
    private String buildSubtitle(Event event) {
        Event.EventStatus status = event.getStatus();

        StringBuilder sb = new StringBuilder(statusString(status));

        Integer waitlistCap = event.getWaitlistCapacity();
        if (waitlistCap == -1) {
            sb.append("\nWaitlist: ")
                    .append(event.getWaitlistCount())
                    .append("/")
                    .append("∞");

        } else if (waitlistCap > 0){
            sb.append("\nWaitlist: ")
                    .append(event.getWaitlistCount())
                    .append("/")
                    .append(waitlistCap);
        }

        return sb.toString();
    }

    /**
     * Subtitle builder helper for status logic
     */
    private String statusString(Event.EventStatus status) {
        switch (status) {
            case REG_OPEN:
                return "Open for registration";
            case REG_CLOSED:
                return "Closed for registration";
            case REG_FULL:
                return "Waitlist full";
            case REG_UPCOMING:
                return "Register soon";
            case EVENT_CLOSED:
                return "Event date passed";
            case EVENT_OPEN:
                return "Invitations Sent";
            case EVENT_FULL:
                return "Event is full";
        }
        return null;
    }
}