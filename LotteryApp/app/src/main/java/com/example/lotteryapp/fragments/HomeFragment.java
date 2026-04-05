package com.example.lotteryapp.fragments;

import static com.example.lotteryapp.models.NotificationLog.NotificationStatus.ACCEPTED;
import static com.example.lotteryapp.models.NotificationLog.NotificationStatus.DECLINED;
import static com.example.lotteryapp.models.NotificationLog.NotificationStatus.READ;

import android.graphics.drawable.GradientDrawable;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.example.lotteryapp.services.storage.EventPoolStorage;
import com.example.lotteryapp.services.storage.EventStorage;
import com.example.lotteryapp.services.storage.NotificationLogStorage;
import com.example.lotteryapp.activities.TermsOfServiceActivity;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.UserStorage;
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

    private EventStorage estore = ServiceLocator.getEventStorage();
    private EventPoolStorage eventPoolStorage = ServiceLocator.getEventPoolStorage();
    private UserStorage ustore = ServiceLocator.getUserStorage();
    private GridEventAdapter openAdapter;
    private GridEventAdapter upcomingAdapter;
    private GridEventAdapter fullAdapter;
    private List<HomeFragment.DisplayGridEvent> displayOpenGridEvents;
    private List<HomeFragment.DisplayGridEvent> displayUpcomingGridEvents;
    private List<HomeFragment.DisplayGridEvent> displayFullGridEvents;
    private boolean isCheckingTOS = false;
    private boolean isTOSActivityOpen = false;

    private final ActivityResultLauncher<Intent> tosLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        isTOSActivityOpen = false;
                        checkAndLaunchTOSIfNeeded();
                    }
            );



    private ViewPager2 notificationsPager;
    private NotificationLogStorage notificationLogStorage = ServiceLocator.getNotificationLogStorage();

    private NotificationPagerAdapter notificationPagerAdapter;
    private final List<NotificationLog> notifications = new ArrayList<>();
    private View invitationCard;

    /**
     * Inflates the HomeFragment layout and initializes UI components.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        invitationCard = view.findViewById(R.id.invitation_card);
        notificationsPager = view.findViewById(R.id.notifications_pager);

        notificationPagerAdapter = new NotificationPagerAdapter(
                notifications,
                this::dismissNotificationAt,
                this::handleNotificationClicked
        );
        notificationsPager.setAdapter(notificationPagerAdapter);

        if (invitationCard != null) {
            invitationCard.setVisibility(View.GONE);
        }
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

        checkAndLaunchTOSIfNeeded();
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
        checkAndLaunchTOSIfNeeded();
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
                e -> android.util.Log.e("HomeFragment", "Failed to load full events", e)
        );
    }

    /**
     * Load the pending notifications for the current user
     *      displayed inside a swipeable banner at the top of HomeFragment
     *      noti.s come from getPendingNotificationsForUser() from NotificationLogStorage
     */
    private void loadNotifications() {
        String uid = ServiceLocator.uid();
        if (uid == null || uid.trim().isEmpty()) {
            if (invitationCard != null) invitationCard.setVisibility(View.GONE);
            notificationsPager.setVisibility(View.GONE);
            return;
        }

        if (notificationLogStorage == null) {
            Log.e("HomeFragment", "NotificationLogStorage is null");
            if (invitationCard != null) invitationCard.setVisibility(View.GONE);
            notificationsPager.setVisibility(View.GONE);
            return;
        }

        notificationLogStorage.getPendingNotificationsForUser(
                uid,
                logs -> {
                    notifications.clear();
                    notifications.addAll(logs);
                    updateNotificationBannerVisibility();
                },
                e -> {
                    Log.e("HomeFragment", "Failed to load notifications", e);
                    if (invitationCard != null) invitationCard.setVisibility(View.GONE);
                    notificationsPager.setVisibility(View.GONE);
                }
        );
    }

    /**
     * Helper for setting the notification banner to show (has pending noti.s) or hide (no pending)
     */
    private void updateNotificationBannerVisibility() {
        if (notifications.isEmpty()) {
            if (invitationCard != null) invitationCard.setVisibility(View.GONE);
            notificationsPager.setVisibility(View.GONE);
            return;
        }

        if (invitationCard != null) invitationCard.setVisibility(View.VISIBLE);
        notificationsPager.setVisibility(View.VISIBLE);
        notificationPagerAdapter.notifyDataSetChanged();
        notificationsPager.setCurrentItem(0, false);
    }

    /**
     * called from the close button click listener
     * allows the user to dismiss a notification from their home view without accepting or declining
     *      this will cause the notification to reload on the next refresh of the homefragment
     *              an invite must be accepted or declined to be removed from the banner list
     */
    private void dismissNotificationAt(int position) {
        if (position < 0 || position >= notifications.size()) {
            return;
        }

        NotificationLog notification = notifications.get(position);
        if (notification == null) {
            return;
        }

        if (notification.getType() != NotificationLog.NotificationType.PRIVATE_INVITATION
                && notification.getType() != NotificationLog.NotificationType.COORGANIZER_INVITATION) {
            if (notification.getId() == null || notification.getId().trim().isEmpty()) {
                Toast.makeText(requireContext(), "Missing notification", Toast.LENGTH_SHORT).show();
                return;
            }

            notificationLogStorage.updateNotificationStatus(
                    notification.getId(),
                    READ.name(),
                    unused -> {
                        notifications.remove(position);
                        updateNotificationBannerVisibility();

                        if (!notifications.isEmpty()) {
                            int nextIndex = Math.min(position, notifications.size() - 1);
                            notificationsPager.setCurrentItem(nextIndex, false);
                        }

                        Toast.makeText(requireContext(), "Notification dismissed", Toast.LENGTH_SHORT).show();
                    },
                    e -> Toast.makeText(
                            requireContext(),
                            e.getMessage() == null ? "Failed to dismiss notification" : e.getMessage(),
                            Toast.LENGTH_SHORT
                    ).show()
            );
            return;
        }

        notifications.remove(position);
        updateNotificationBannerVisibility();

        if (!notifications.isEmpty()) {
            int nextIndex = Math.min(position, notifications.size() - 1);
            notificationsPager.setCurrentItem(nextIndex, false);
        }
    }

    /**
     * Declare actionable (has clicklistener on the notification xml item) notifications here
     *      for if you don't want a certain notification to be actionable, leave it out of here
     *      if all notifications turn out to be actionable, this is not required
     */
    private boolean isActionableNotification(NotificationLog notification) {
        if (notification == null || notification.getType() == null) {
            return false;
        }

        return notification.getType() == NotificationLog.NotificationType.PRIVATE_INVITATION
                || notification.getType() == NotificationLog.NotificationType.COORGANIZER_INVITATION
                || notification.getType() == NotificationLog.NotificationType.WON_LOTTERY;
    }

    /**
     * Set the state based on what the user chose to do with the invite/notification in the dialog
     */
    private void handleNotificationClicked(int position) {
        if (position < 0 || position >= notifications.size()) {
            return;
        }

        NotificationLog notification = notifications.get(position);

        if (!isActionableNotification(notification)) {
            return;
        }

        if (notification.getType() == NotificationLog.NotificationType.PRIVATE_INVITATION) {
            showPrivateInviteDialog(position, notification);
        } else if (notification.getType() == NotificationLog.NotificationType.COORGANIZER_INVITATION) {
            showCoorganizerInviteDialog(position, notification);
        } else if (notification.getType() == NotificationLog.NotificationType.WON_LOTTERY) {
            showLotteryInviteDialog(position, notification);
        }
    }

    /**
     * Show the dialog for a user accepting or declining an invite to a private event
     */
    private void showPrivateInviteDialog(int position, NotificationLog notification) {
        if (getContext() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Private event invitation")
                .setMessage("Would you like to accept this private event invitation?")
                .setNegativeButton("Decline", (dialog, which) -> declinePrivateInvite(position, notification))
                .setPositiveButton("Accept", (dialog, which) -> acceptPrivateInvite(position, notification))
                .show();
    }

    /**
     * Show the dialog for a user accepting or declining an invite to be a coorganizer for an event
     */
    private void showCoorganizerInviteDialog(int position, NotificationLog notification) {
        if (getContext() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Co-organizer invitation")
                .setMessage("Would you like to accept this co-organizer invitation?")
                .setNegativeButton("Decline", (dialog, which) -> declineCoorganizerInvite(position, notification))
                .setPositiveButton("Accept", (dialog, which) -> acceptCoorganizerInvite(position, notification))
                .show();
    }

    private void showLotteryInviteDialog(int position, NotificationLog notification) {
        if (getContext() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Lottery Selection")
                .setMessage("You have been selected! Would you like to accept your spot?")
                .setNegativeButton("Decline", (dialog, which) ->
                        declineLotteryInvite(position, notification))
                .setPositiveButton("Accept", (dialog, which) ->
                        acceptLotteryInvite(position, notification))
                .show();
    }



    /**
     * Runs on the user clicking confirm in the private event invite dialog
     *      enrolls the user in the event
     *      enrollInEvent:
     *          removes their entry from the invited subcollection and adds is to enrolled subcollection
     *      sets notification as ACCEPTED
     */
    private void acceptPrivateInvite(int position, NotificationLog notification) {
        String uid = ServiceLocator.uid();

        if (notification == null || notification.getEventId() == null || notification.getEventId().trim().isEmpty()) {
            Toast.makeText(requireContext(), "Missing event for invite", Toast.LENGTH_SHORT).show();
            return;
        }

        if (uid == null || uid.trim().isEmpty()) {
            Toast.makeText(requireContext(), "User not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        Entrant entrant = new Entrant(uid, notification.getEventId(), Entrant.EntrantStatus.ENROLLED);

        eventPoolStorage.waitlistForEvent(
                notification.getEventId(),
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

    /**
     * Runs on the user clicking decline in the private event invite dialog
     *          removes their entry from the invited subcollection and nothing else
     *          sets notification as DECLINED
     */

    private void declinePrivateInvite(int position, NotificationLog notification) {
        String uid = ServiceLocator.uid();

        if (notification == null || notification.getEventId() == null || notification.getEventId().trim().isEmpty()) {
            Toast.makeText(requireContext(), "Missing event for invite", Toast.LENGTH_SHORT).show();
            return;
        }

        if (uid == null || uid.trim().isEmpty()) {
            Toast.makeText(requireContext(), "User not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        resolveNotification(
                position,
                notification,
                DECLINED.name(),
                "Private invite declined"
        );
    }

    /**
     * Runs on the user clicking confirm in the private event invite dialog
     *      enrolls the user in the event
     *      enrollInEvent:
     *          removes their entry from the invited subcollection and adds is to enrolled subcollection
     *      sets notification as ACCEPTED
     */
    private void acceptCoorganizerInvite(int position, NotificationLog notification) {
        String uid = ServiceLocator.uid();

        if (notification == null || notification.getEventId() == null || notification.getEventId().trim().isEmpty()) {
            Toast.makeText(requireContext(), "Missing event for invite", Toast.LENGTH_SHORT).show();
            return;
        }

        if (uid == null || uid.trim().isEmpty()) {
            Toast.makeText(requireContext(), "User not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        estore.acceptInviteToBeCoorganizer(
                notification.getEventId(),
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

    /**
     * Runs on the user clicking decline in the private event invite dialog
     *          removes their entry from the coorganizer_invites subcollection and nothing else
     *          sets notification as DECLINED
     */
    private void declineCoorganizerInvite(int position, NotificationLog notification) {
        String uid = ServiceLocator.uid();

        if (notification == null || notification.getEventId() == null || notification.getEventId().trim().isEmpty()) {
            Toast.makeText(requireContext(), "Missing event for invite", Toast.LENGTH_SHORT).show();
            return;
        }

        if (uid == null || uid.trim().isEmpty()) {
            Toast.makeText(requireContext(), "User not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        estore.declineInviteToBeCoorganizer(
                notification.getEventId(),
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

    private void acceptLotteryInvite(int position, NotificationLog notification) {
        String uid = ServiceLocator.uid();

        if (notification == null || notification.getEventId() == null) {
            Toast.makeText(requireContext(), "Missing event", Toast.LENGTH_SHORT).show();
            return;
        }

        if (uid == null) {
            Toast.makeText(requireContext(), "User not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        eventPoolStorage.enrollInEvent(
                notification.getEventId(),
                uid,
                unused -> resolveNotification(
                        position,
                        notification,
                        ACCEPTED.name(),
                        "You are now enrolled!"
                ),
                e -> Toast.makeText(requireContext(),
                        "Failed to accept invite", Toast.LENGTH_SHORT).show()
        );
    }

    private void declineLotteryInvite(int position, NotificationLog notification) {
        String uid = ServiceLocator.uid();

        if (notification == null || notification.getEventId() == null) {
            Toast.makeText(requireContext(), "Missing event", Toast.LENGTH_SHORT).show();
            return;
        }

        if (uid == null) {
            Toast.makeText(requireContext(), "User not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        eventPoolStorage.removeFromInvited(
                notification.getEventId(),
                uid,
                unused -> resolveNotification(
                        position,
                        notification,
                        DECLINED.name(),
                        "Invite declined"
                ),
                e -> Toast.makeText(requireContext(),
                        "Failed to decline invite", Toast.LENGTH_SHORT).show()
        );
    }

    /**
     * Set the new status of the notification based on the users action in the dialog
     */
    private void resolveNotification(
            int position,
            NotificationLog notification,
            String status,
            String successMessage
    ) {
        if (notification == null || notification.getId() == null || notification.getId().trim().isEmpty()) {
            Toast.makeText(requireContext(), "Missing notification", Toast.LENGTH_SHORT).show();
            return;
        }

        notificationLogStorage.updateNotificationStatus(
                notification.getId(),
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


    /**
     * Notification Pager Adapter for setting actionable notifications (launches dialogs)
     */
    private static final class NotificationPagerAdapter
            extends RecyclerView.Adapter<NotificationPagerAdapter.NotificationViewHolder> {

        // call back interface for when a user clicks the notification dismissal button
        interface OnDismissClickListener {
            void onDismissClicked(int position);
        }

        // callback interface for when a user clicks the item
        interface OnNotificationClickListener {
            void onNotificationClicked(int position);
        }

        private final List<NotificationLog> notifications; // notifications to show
        private final OnDismissClickListener dismissClickListener;
        private final OnNotificationClickListener notificationClickListener;

        /**
         * Creates a new adapter for the HomeFragment notification pager
         */
        NotificationPagerAdapter(List<NotificationLog> notifications,
                                 OnDismissClickListener dismissClickListener,
                                 OnNotificationClickListener notificationClickListener) {
            this.notifications = notifications;
            this.dismissClickListener = dismissClickListener;
            this.notificationClickListener = notificationClickListener;
        }

        /**
         * Inflates a single notification card layout and wraps it up as a View
         */
        @NonNull
        @Override
        public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_home_notification, parent, false);
            return new NotificationViewHolder(view);
        }

        /**
         * Bind a single notification to a single notification banner card
         */
        @Override
        public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
            NotificationLog notification = notifications.get(position);

            String title = notification.getTitle();
            String messageText = notification.getMessage();

            String typeText = (title == null || title.trim().isEmpty())
                    ? "Notification"
                    : "Notification: " + title.trim();

            String message = (messageText == null || messageText.trim().isEmpty())
                    ? "You have a new notification."
                    : messageText.trim();

            holder.header.setText(typeText);
            holder.message.setText(message);
            bindDots(holder.dotsInline, position);

            // The root card click is used for actionable notifications (launch dialogs)
            holder.root.setOnClickListener(v -> {
                if (notificationClickListener != null) {
                    int adapterPosition = holder.getBindingAdapterPosition();
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        notificationClickListener.onNotificationClicked(adapterPosition);
                    }
                }
            });

            // dismiss button:
                // - EVENT_ANNOUNCEMENT notifications are marked ACCEPTED so they do not reappear
                // - other notifications are only removed from the current in-memory page load
            holder.closeButton.setOnClickListener(v -> {
                if (dismissClickListener != null) {
                    int adapterPosition = holder.getBindingAdapterPosition();
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        dismissClickListener.onDismissClicked(adapterPosition);
                    }
                }
            });
        }

        // number of notifications
        @Override
        public int getItemCount() {
            return notifications.size();
        }

        /**
         * ViewHolder for a single notification banner card
         */
        static final class NotificationViewHolder extends RecyclerView.ViewHolder {
            View root;
            TextView header;
            TextView message;
            MaterialButton closeButton;
            LinearLayout dotsInline;

            // creates a view holder and binds all child views
            NotificationViewHolder(@NonNull View itemView) {
                super(itemView);
                root = itemView.findViewById(R.id.notification_root);
                header = itemView.findViewById(R.id.tv_notification_header);
                message = itemView.findViewById(R.id.tv_notification_message);
                closeButton = itemView.findViewById(R.id.btn_close_notification);
                dotsInline = itemView.findViewById(R.id.notification_dots_inline);
            }
        }

        /**
         * Rebuilds the inline pager indicator dots for the current banner position
         */
        private void bindDots(LinearLayout container, int selectedIndex) {
            container.removeAllViews();

            // do pending notifications exist for this user?
            if (container.getContext() == null || notifications.size() <= 1) {
                container.setVisibility(View.GONE);
                return;
            }

            // notifications exist, so the pager indicator should be visible
            container.setVisibility(View.VISIBLE);

            // one dot per notification currently shown in the banner list
            int sizePx = dpToPx(container, 8);
            int marginPx = dpToPx(container, 3);

            for (int i = 0; i < notifications.size(); i++) {
                View dot = new View(container.getContext());

                // set a fixed circular size for each dot
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizePx, sizePx);
                params.setMargins(marginPx, 0, marginPx, 0);
                dot.setLayoutParams(params);

                // gradient drawable so each entry appears as a dot
                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.OVAL);
                drawable.setColor(resolveDotColor(container));
                dot.setBackground(drawable);

                // highlight selected dot, dim not-selected dots
                dot.setAlpha(i == selectedIndex ? 1f : 0.35f);

                container.addView(dot);
            }
        }

        /**
         * Converts a dp pixel value into raw pixels using the display density of the view context
         */
        private int dpToPx(View view, int dp) {
            float density = view.getResources().getDisplayMetrics().density;
            return Math.round(dp * density);
        }

        /**
         * Resolves the themed color used for notification pager dots
         */
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

    /*
     * Checks the current user's TOS acceptance state in Firestore.
     * If the user has not accepted, launch the full TermsOfServiceActivity.
     */
    private void checkAndLaunchTOSIfNeeded() {
        if (!isAdded() || getActivity() == null) return;
        if (isCheckingTOS || isTOSActivityOpen) return;

        String uid = ServiceLocator.uid();
        if (uid == null || uid.trim().isEmpty()) return;

        isCheckingTOS = true;

        ustore.getHasAcceptedTOS(
                uid,
                hasAccepted -> {
                    isCheckingTOS = false;

                    if (!isAdded() || getActivity() == null) return;

                    if (!hasAccepted && !isTOSActivityOpen) {
                        isTOSActivityOpen = true;
                        Intent intent = new Intent(requireContext(), TermsOfServiceActivity.class);
                        tosLauncher.launch(intent);
                    }
                },
                e -> {
                    isCheckingTOS = false;
                    android.util.Log.e("HomeFragment", "Failed to check Terms of Service acceptance", e);
                }
        );
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
        public String eventId, title, subtitle, description, tag, posterUrl;

        public DisplayGridEvent(String eventId, String title, String subtitle, String description, String tag, String posterUrl) {
            this.eventId = eventId;
            this.title = title;
            this.subtitle = subtitle;
            this.description = description;
            this.tag = tag;
            this.posterUrl = posterUrl;
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
                event.getTag(),
                event.getPosterUrl()
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
        if (waitlistCap != null && waitlistCap == -1) {
            sb.append("\nWaitlist: ")
                    .append(event.getWaitlistCount())
                    .append("/")
                    .append("∞");

        } else if (waitlistCap != null && waitlistCap > 0){
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
        if (status == null) return "Unknown";
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
        return "Unknown";
    }
}
