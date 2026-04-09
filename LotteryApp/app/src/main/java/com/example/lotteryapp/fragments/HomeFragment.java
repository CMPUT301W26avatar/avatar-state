package com.example.lotteryapp.fragments;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.example.lotteryapp.fragments.ManageFragment.UNLIMITED_WAITLIST_SENTINEL;
import static com.example.lotteryapp.models.NotificationLog.NotificationStatus.ACCEPTED;
import static com.example.lotteryapp.models.NotificationLog.NotificationStatus.DECLINED;
import static com.example.lotteryapp.models.NotificationLog.NotificationStatus.READ;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
import com.example.lotteryapp.activities.TermsOfServiceActivity;
import com.example.lotteryapp.activities.UserLocationSettingsActivity;
import com.example.lotteryapp.models.Entrant;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.models.NotificationLog;
import com.example.lotteryapp.models.User;
import com.example.lotteryapp.models.UserAddress;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventPoolStorage;
import com.example.lotteryapp.services.storage.EventStorage;
import com.example.lotteryapp.services.storage.NotificationLogStorage;
import com.example.lotteryapp.services.storage.UserStorage;
import com.example.lotteryapp.utils.DepthPageTransformer;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

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

    private static final String HOME_PREFS = "home_fragment_prefs";
    private static final String KEY_ACTIVE_NEARBY_RADIUS_KM = "active_nearby_radius_km";
    private static final int DEFAULT_NEARBY_RADIUS_KM = 20;
    private static final String PREFS_NAME = "privacy_prefs";
    private static final String KEY_LOCATION_MODE = "location_mode";

    private final EventStorage eventStorage = ServiceLocator.getEventStorage();
    private final EventPoolStorage eventPoolStorage = ServiceLocator.getEventPoolStorage();
    private final UserStorage userStorage = ServiceLocator.getUserStorage();
    private final NotificationLogStorage notificationLogStorage = ServiceLocator.getNotificationLogStorage();

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
    private NotificationPagerAdapter notificationPagerAdapter;
    private final List<NotificationLog> notifications = new ArrayList<>();
    private View invitationCard;
    private View notificationsEmptyState;

    private MaterialTextView popularText;
    private RecyclerView recyclerViewPopular;

    private MaterialTextView nearbyText;
    private View nearbyHeaderRow;
    private RecyclerView recyclerViewNearby;
    private View nearbyLocationYield;
    private View nearbyEmptyRadiusYield;
    private MaterialTextView nearbyEmptyRadiusTitle;
    private ImageView homeLocationStatusIcon;
    private View nearbyRadiusButton;

    private LinearLayout loadingIndicator;
    private LinearLayout emptyIndicator;

    private GridEventAdapter popularAdapter;
    private List<DisplayGridEvent> displayPopularGridEvents;

    private GridEventAdapter nearbyAdapter;
    private List<DisplayGridEvent> displayNearbyGridEvents;

    private boolean popularLoaded = false;
    private boolean nearbyLoaded = false;
    private boolean hasResolvedNearbyLocation = false;
    private List<DisplayGridEvent> pendingPopularGridEvents = new ArrayList<>();
    private List<DisplayGridEvent> pendingNearbyGridEvents = new ArrayList<>();
    private int activeNearbyRadiusKm = DEFAULT_NEARBY_RADIUS_KM;

    /**
     * Restores the last selected nearby search radius for this app session.
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activeNearbyRadiusKm = getSavedNearbyRadiusKm();
    }

    /**
     * Inflates the HomeFragment layout and initializes UI components.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        homeLocationStatusIcon = view.findViewById(R.id.iv_home_location_status);
        nearbyHeaderRow = view.findViewById(R.id.nearby_header_row);
        nearbyRadiusButton = view.findViewById(R.id.btn_nearby_radius);

        homeLocationStatusIcon.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), UserLocationSettingsActivity.class);
            startActivity(intent);
        });

        if (nearbyRadiusButton != null) {
            nearbyRadiusButton.setOnClickListener(v -> showNearbyRadiusDialog());
        }

        invitationCard = view.findViewById(R.id.invitation_card);
        notificationsPager = view.findViewById(R.id.notifications_pager);
        notificationsEmptyState = view.findViewById(R.id.notifications_empty_state);

        notificationPagerAdapter = new NotificationPagerAdapter(
                notifications,
                this::dismissNotificationAt,
                this::handleNotificationClicked
        );
        notificationsPager.setAdapter(notificationPagerAdapter);
        notificationsPager.setPageTransformer(new DepthPageTransformer());

        recyclerViewPopular = view.findViewById(R.id.recycler_view_popular);
        popularText = view.findViewById(R.id.popular_text);

        recyclerViewNearby = view.findViewById(R.id.recycler_view_nearby);
        nearbyText = view.findViewById(R.id.nearby_text);
        nearbyLocationYield = view.findViewById(R.id.nearby_location_yield);
        nearbyEmptyRadiusYield = view.findViewById(R.id.nearby_empty_radius_yield);
        nearbyEmptyRadiusTitle = view.findViewById(R.id.nearby_empty_radius_title);

        if (nearbyLocationYield != null) {
            nearbyLocationYield.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), UserLocationSettingsActivity.class);
                startActivity(intent);
            });
        }

        if (nearbyEmptyRadiusYield != null) {
            nearbyEmptyRadiusYield.setOnClickListener(v -> showNearbyRadiusDialog());
        }

        loadingIndicator = view.findViewById(R.id.loading_indicator);
        emptyIndicator = view.findViewById(R.id.empty_indicator);

        recyclerViewPopular.setLayoutManager(new GridLayoutManager(getContext(), 2));
        recyclerViewNearby.setLayoutManager(new GridLayoutManager(getContext(), 2));

        displayPopularGridEvents = new ArrayList<>();
        popularAdapter = new GridEventAdapter(displayPopularGridEvents);
        recyclerViewPopular.setAdapter(popularAdapter);

        displayNearbyGridEvents = new ArrayList<>();
        nearbyAdapter = new GridEventAdapter(displayNearbyGridEvents);
        recyclerViewNearby.setAdapter(nearbyAdapter);

        updateNearbyRadiusMessage();
        return view;
    }

    /**
     * Shows or hides the central loading indicator while home data is being fetched.
     */
    private void toggleLoadingIndicator(boolean isVisible) {
        if (loadingIndicator == null) return;
        loadingIndicator.setVisibility(isVisible ? VISIBLE : GONE);
    }

    /**
     * Updates the top-right home header icon based on whether the user has a resolved location.
     */
    private void updateHomeLocationStatusIcon(boolean hasResolvedLocation) {
        hasResolvedNearbyLocation = hasResolvedLocation;

        if (homeLocationStatusIcon != null) {
            homeLocationStatusIcon.setImageResource(
                    hasResolvedLocation ? R.drawable.ic_nearme : R.drawable.ic_nearmeoff
            );
        }
    }

    /**
     * Reads the last selected nearby radius from shared preferences.
     */
    private int getSavedNearbyRadiusKm() {
        if (getContext() == null) {
            return DEFAULT_NEARBY_RADIUS_KM;
        }

        SharedPreferences prefs = requireContext().getSharedPreferences(HOME_PREFS, 0);
        return prefs.getInt(KEY_ACTIVE_NEARBY_RADIUS_KM, DEFAULT_NEARBY_RADIUS_KM);
    }

    /**
     * Saves the selected nearby radius so it survives fragment switches.
     */
    private void saveNearbyRadiusKm(int radiusKm) {
        activeNearbyRadiusKm = radiusKm;

        if (getContext() == null) {
            return;
        }

        SharedPreferences prefs = requireContext().getSharedPreferences(HOME_PREFS, 0);
        prefs.edit().putInt(KEY_ACTIVE_NEARBY_RADIUS_KM, radiusKm).apply();
    }

    /**
     * Updates the empty nearby result message to reflect the active radius.
     */
    private void updateNearbyRadiusMessage() {
        if (nearbyEmptyRadiusTitle != null) {
            nearbyEmptyRadiusTitle.setText(
                    "No events found within a " + activeNearbyRadiusKm + " km radius"
            );
        }
    }

    /**
     * initializes fragment view
     *      check tos acceptance
     *      set up filters
     *      data loads
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        checkAndLaunchTOSIfNeeded();
        loadHomeSections();
        loadNotifications();
    }

    /**
     * Reloads event lists whenever the fragment comes into view again.
     *      ensures the dashboard reflects the most recent event(s)
     */
    @Override
    public void onResume() {
        super.onResume();
        activeNearbyRadiusKm = getSavedNearbyRadiusKm();
        updateNearbyRadiusMessage();
        checkAndLaunchTOSIfNeeded();
        loadHomeSections();
        loadNotifications();
    }

    /**
     * loads all visible event sections and only resolves the empty state after all section queries finish
     */
    private void loadHomeSections() {
        if (eventStorage == null) {
            return;
        }

        popularLoaded = false;
        nearbyLoaded = false;
        updateHomeLocationStatusIcon(false);
        updateNearbyRadiusMessage();

        pendingPopularGridEvents.clear();
        pendingNearbyGridEvents.clear();

        displayPopularGridEvents.clear();
        popularAdapter.notifyDataSetChanged();

        displayNearbyGridEvents.clear();
        nearbyAdapter.notifyDataSetChanged();

        if (popularText != null) popularText.setVisibility(GONE);
        if (recyclerViewPopular != null) recyclerViewPopular.setVisibility(GONE);

        if (nearbyHeaderRow != null) nearbyHeaderRow.setVisibility(GONE);
        if (nearbyLocationYield != null) nearbyLocationYield.setVisibility(GONE);
        if (nearbyEmptyRadiusYield != null) nearbyEmptyRadiusYield.setVisibility(GONE);
        if (recyclerViewNearby != null) recyclerViewNearby.setVisibility(GONE);
        updateHomeLocationStatusIcon(false);

        if (emptyIndicator != null) {
            emptyIndicator.setVisibility(GONE);
        }

        toggleLoadingIndicator(true);

        loadPopularEvents();
        loadNearbyEvents();
    }

    /**
     * resolves the final UI state after both popular and nearby queries finish.
     *      populates adapters, toggles visibility of sections, and determines empty state behavior
     */
    private void resolveHomeEmptyState() {
        if (!popularLoaded || !nearbyLoaded) {
            return;
        }

        toggleLoadingIndicator(false);

        displayPopularGridEvents.clear();
        displayPopularGridEvents.addAll(pendingPopularGridEvents);
        popularAdapter.notifyDataSetChanged();

        displayNearbyGridEvents.clear();
        displayNearbyGridEvents.addAll(pendingNearbyGridEvents);
        nearbyAdapter.notifyDataSetChanged();

        boolean hasPopular = !displayPopularGridEvents.isEmpty();
        boolean hasNearby = !displayNearbyGridEvents.isEmpty();

        if (popularText != null) popularText.setVisibility(hasPopular ? VISIBLE : GONE);
        if (recyclerViewPopular != null) {
            recyclerViewPopular.setVisibility(hasPopular ? VISIBLE : GONE);
            if (hasPopular) recyclerViewPopular.scheduleLayoutAnimation();
        }

        if (hasResolvedNearbyLocation) {
            if (nearbyHeaderRow != null) nearbyHeaderRow.setVisibility(VISIBLE);
            if (nearbyLocationYield != null) nearbyLocationYield.setVisibility(GONE);

            if (hasNearby) {
                if (recyclerViewNearby != null) {
                    recyclerViewNearby.setVisibility(VISIBLE);
                    recyclerViewNearby.scheduleLayoutAnimation();
                }
                if (nearbyEmptyRadiusYield != null) nearbyEmptyRadiusYield.setVisibility(GONE);
            } else {
                if (recyclerViewNearby != null) recyclerViewNearby.setVisibility(GONE);
                if (nearbyEmptyRadiusYield != null) nearbyEmptyRadiusYield.setVisibility(VISIBLE);
            }

            updateHomeLocationStatusIcon(true);
        } else {
            if (nearbyHeaderRow != null) nearbyHeaderRow.setVisibility(GONE);
            if (nearbyLocationYield != null) nearbyLocationYield.setVisibility(VISIBLE);
            if (recyclerViewNearby != null) recyclerViewNearby.setVisibility(GONE);
            if (nearbyEmptyRadiusYield != null) nearbyEmptyRadiusYield.setVisibility(GONE);
            updateHomeLocationStatusIcon(false);
        }

        if (emptyIndicator != null) {
            emptyIndicator.setVisibility(hasPopular || hasNearby || !hasResolvedNearbyLocation ? GONE : VISIBLE);
        }
    }

    /**
     * loads events sorted by the highest comment count and prepares them for grid display
     */
    private void loadPopularEvents() {
        if (eventStorage == null || popularAdapter == null || displayPopularGridEvents == null) {
            popularLoaded = true;
            resolveHomeEmptyState();
            return;
        }

        eventStorage.listEventsByMostComments(
                4,
                fetchedEvents -> {
                    pendingPopularGridEvents.clear();

                    if (fetchedEvents != null) {
                        for (Event event : fetchedEvents) {
                            if (!event.isPrivateEvent()) {
                                pendingPopularGridEvents.add(eventToDisplayEvent(event));

                            }
                        }
                    }

                    popularLoaded = true;
                    resolveHomeEmptyState();
                },
                e -> {
                    Log.e("HomeFragment", "Failed to load popular events by comments", e);
                    pendingPopularGridEvents.clear();
                    popularLoaded = true;
                    resolveHomeEmptyState();
                }
        );
    }

    /**
     * Shows a radius selection dialog for widening the nearby search.
     */
    private void showNearbyRadiusDialog() {
        if (getContext() == null) return;

        final String[] radiusOptions = {"20 km", "50 km", "100 km", "200 km"};
        final int[] radiusValues = {20, 50, 100, 200};

        new AlertDialog.Builder(requireContext())
                .setTitle("Widen nearby search")
                .setItems(radiusOptions, (dialog, which) -> {
                    saveNearbyRadiusKm(radiusValues[which]);
                    updateNearbyRadiusMessage();
                    reloadNearbyEventsForSelectedRadius();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Reloads the nearby section using the currently selected radius.
     */
    private void reloadNearbyEventsForSelectedRadius() {
        String uid = ServiceLocator.uid();
        if (uid == null || uid.trim().isEmpty()) {
            return;
        }

        nearbyLoaded = false;
        pendingNearbyGridEvents.clear();

        if (nearbyEmptyRadiusYield != null) nearbyEmptyRadiusYield.setVisibility(GONE);
        if (recyclerViewNearby != null) recyclerViewNearby.setVisibility(GONE);

        toggleLoadingIndicator(true);

        User.UserAddressMode selectedMode = getSavedNearbyMode();

        if (selectedMode == User.UserAddressMode.CURRENT) {
            userStorage.getCurrentUserAddress(
                    uid,
                    currentAddress -> {
                        if (isResolvedAddress(currentAddress)) {
                            loadNearbyEventsWithMode(
                                    uid,
                                    User.UserAddressMode.CURRENT,
                                    false,
                                    activeNearbyRadiusKm
                            );
                        } else {
                            pendingNearbyGridEvents.clear();
                            updateHomeLocationStatusIcon(false);
                            nearbyLoaded = true;
                            resolveHomeEmptyState();
                        }
                    },
                    e -> {
                        Log.e("HomeFragment", "Failed to reload current user address", e);
                        pendingNearbyGridEvents.clear();
                        updateHomeLocationStatusIcon(false);
                        nearbyLoaded = true;
                        resolveHomeEmptyState();
                    }
            );
        } else {
            userStorage.getDefaultUserAddress(
                    uid,
                    defaultAddress -> {
                        if (isResolvedAddress(defaultAddress)) {
                            loadNearbyEventsWithMode(
                                    uid,
                                    User.UserAddressMode.DEFAULT,
                                    false,
                                    activeNearbyRadiusKm
                            );
                        } else {
                            pendingNearbyGridEvents.clear();
                            updateHomeLocationStatusIcon(false);
                            nearbyLoaded = true;
                            resolveHomeEmptyState();
                        }
                    },
                    e -> {
                        Log.e("HomeFragment", "Failed to reload default user address", e);
                        pendingNearbyGridEvents.clear();
                        updateHomeLocationStatusIcon(false);
                        nearbyLoaded = true;
                        resolveHomeEmptyState();
                    }
            );
        }
    }

    /**
     * loads events sorted by proximity to the user's location
     *      resolves user address -> if not enabled, prompts user to set location
     *      if enabled, shows events within the active radius by calling the helper
     */
    private void loadNearbyEvents() {
        String uid = ServiceLocator.uid();
        if (uid == null || uid.trim().isEmpty()) {
            pendingNearbyGridEvents.clear();
            updateHomeLocationStatusIcon(false);
            nearbyLoaded = true;
            resolveHomeEmptyState();
            return;
        }

        User.UserAddressMode selectedMode = getSavedNearbyMode();

        if (selectedMode == User.UserAddressMode.CURRENT) {
            userStorage.getCurrentUserAddress(
                    uid,
                    currentAddress -> {
                        if (isResolvedAddress(currentAddress)) {
                            loadNearbyEventsWithMode(
                                    uid,
                                    User.UserAddressMode.CURRENT,
                                    false,
                                    activeNearbyRadiusKm
                            );
                        } else {
                            pendingNearbyGridEvents.clear();
                            updateHomeLocationStatusIcon(false);
                            nearbyLoaded = true;
                            resolveHomeEmptyState();
                        }
                    },
                    e -> {
                        Log.e("HomeFragment", "Failed to load current user address", e);
                        pendingNearbyGridEvents.clear();
                        updateHomeLocationStatusIcon(false);
                        nearbyLoaded = true;
                        resolveHomeEmptyState();
                    }
            );
        } else {
            userStorage.getDefaultUserAddress(
                    uid,
                    defaultAddress -> {
                        if (isResolvedAddress(defaultAddress)) {
                            loadNearbyEventsWithMode(
                                    uid,
                                    User.UserAddressMode.DEFAULT,
                                    false,
                                    activeNearbyRadiusKm
                            );
                        } else {
                            pendingNearbyGridEvents.clear();
                            updateHomeLocationStatusIcon(false);
                            nearbyLoaded = true;
                            resolveHomeEmptyState();
                        }
                    },
                    e -> {
                        Log.e("HomeFragment", "Failed to load default user address", e);
                        pendingNearbyGridEvents.clear();
                        updateHomeLocationStatusIcon(false);
                        nearbyLoaded = true;
                        resolveHomeEmptyState();
                    }
            );
        }
    }

    /**
     * asynchronously get all events within the active radius using either the current or default location mode
     */
    private void loadNearbyEventsWithMode(
            String uid,
            User.UserAddressMode mode,
            boolean allowFallbackToDefault,
            int radiusKm
    ) {
        eventStorage.listUpcomingEventsNearUser(
                uid,
                mode,
                radiusKm,
                4,
                fetchedEvents -> {
                    if ((fetchedEvents == null || fetchedEvents.isEmpty()) && allowFallbackToDefault
                            && mode == User.UserAddressMode.CURRENT) {
                        loadNearbyEventsWithMode(uid, User.UserAddressMode.DEFAULT, false, radiusKm);
                        return;
                    }

                    pendingNearbyGridEvents.clear();
                    updateHomeLocationStatusIcon(true);

                    if (fetchedEvents != null) {
                        for (Event event : fetchedEvents) {
                            if (!event.isPrivateEvent()) {
                                pendingNearbyGridEvents.add(eventToDisplayEvent(event));
                            }
                        }
                    }

                    nearbyLoaded = true;
                    resolveHomeEmptyState();
                },
                e -> {
                    Log.e("HomeFragment", "Failed to load nearby upcoming events for mode " + mode, e);

                    if (allowFallbackToDefault && mode == User.UserAddressMode.CURRENT) {
                        loadNearbyEventsWithMode(uid, User.UserAddressMode.DEFAULT, false, radiusKm);
                        return;
                    }

                    pendingNearbyGridEvents.clear();
                    updateHomeLocationStatusIcon(true);
                    nearbyLoaded = true;
                    resolveHomeEmptyState();
                }
        );
    }

    /**
     * load events with mode helper
     *      persists the user address mode state from location activity into HomeFragment
     */
    private User.UserAddressMode getSavedNearbyMode() {
        if (getContext() == null) {
            return User.UserAddressMode.DEFAULT;
        }

        String savedMode = requireContext()
                .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .getString(KEY_LOCATION_MODE, User.UserAddressMode.DEFAULT.name());

        try {
            return User.UserAddressMode.valueOf(savedMode);
        } catch (Exception ignored) {
            return User.UserAddressMode.DEFAULT;
        }
    }

    /**
     * Returns true when the user has a saved location that can be used for nearby event queries
     */
    private boolean isResolvedAddress(@Nullable UserAddress address) {
        return address != null
                && address.getLatitude() != null
                && address.getLongitude() != null
                && address.getLocation() != null
                && !address.getLocation().trim().isEmpty();
    }


    /**
     * Load the pending notifications for the current user
     *      displayed inside a swipeable banner at the top of HomeFragment
     *      noti.s come from getPendingNotificationsForUser() from NotificationLogStorage
     */
    private void loadNotifications() {
        String uid = ServiceLocator.uid();
        if (uid == null || uid.trim().isEmpty()) {
            updateNotificationBannerVisibility();
            return;
        }

        if (notificationLogStorage == null) {
            Log.e("HomeFragment", "NotificationLogStorage is null");
            updateNotificationBannerVisibility();
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
                    notifications.clear();
                    updateNotificationBannerVisibility();
                }
        );
    }

    /**
     * Helper for setting the notification banner to show (has pending noti.s) or hide (no pending)
     */
    private void updateNotificationBannerVisibility() {
        if (invitationCard != null) invitationCard.setVisibility(VISIBLE);

        if (notifications.isEmpty()) {
            if (notificationsPager != null) notificationsPager.setVisibility(GONE);
            if (notificationsEmptyState != null) notificationsEmptyState.setVisibility(VISIBLE);
            return;
        }

        if (notificationsEmptyState != null) notificationsEmptyState.setVisibility(GONE);
        if (notificationsPager != null) {
            notificationsPager.setVisibility(VISIBLE);
            notificationPagerAdapter.notifyDataSetChanged();
            notificationsPager.setCurrentItem(0, false);
        }
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

    /**
     * Show the dialog for a user accepting or declining an invite to be enrolled for an event
     */
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

        eventStorage.acceptInviteToBeCoorganizer(
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

        eventStorage.declineInviteToBeCoorganizer(
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

    /**
     * Runs on the user clicking accept in the lottery invite dialog
     *          removes their entry from the invited subcollection and adds them to the enrolled subcollection
     *          sets notification as ACCEPTED
     */
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

    /**
     * Runs on the user clicking decline in the lottery invite dialog
     *      removes their entry from the invited subcollection without enrolling them
     *      sets notification as DECLINED
     */
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

        interface OnDismissClickListener {
            void onDismissClicked(int position);
        }

        interface OnNotificationClickListener {
            void onNotificationClicked(int position);
        }

        private final List<NotificationLog> notifications;
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
                    : title.trim();

            String message = (messageText == null || messageText.trim().isEmpty())
                    ? "You have a new notification."
                    : messageText.trim();

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

        /**
         * ViewHolder for a single notification banner card
         */
        static final class NotificationViewHolder extends RecyclerView.ViewHolder {
            View root;
            TextView header;
            TextView message;
            MaterialButton closeButton;
            LinearLayout dotsInline;

            /**
             * Binds views for a single notification card within the pager.
             */
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

            if (container.getContext() == null || notifications.size() <= 1) {
                container.setVisibility(GONE);
                return;
            }

            container.setVisibility(VISIBLE);

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
            TypedValue typedValue = new TypedValue();
            view.getContext().getTheme().resolveAttribute(
                    com.google.android.material.R.attr.colorOnTertiaryContainer,
                    typedValue,
                    true
            );
            return typedValue.data;
        }
    }

    /**
     * Checks the current user's TOS acceptance state in Firestore.
     * If the user has not accepted, launch the full TermsOfServiceActivity.
     */
    private void checkAndLaunchTOSIfNeeded() {
        if (!isAdded() || getActivity() == null) return;
        if (isCheckingTOS || isTOSActivityOpen) return;

        String uid = ServiceLocator.uid();
        if (uid == null || uid.trim().isEmpty()) return;

        isCheckingTOS = true;

        userStorage.getHasAcceptedTOS(
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
                    Log.e("HomeFragment", "Failed to check Terms of Service acceptance", e);
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
    private DisplayGridEvent eventToDisplayEvent(Event event) {
        return new DisplayGridEvent(
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
        if (waitlistCap != null && waitlistCap == UNLIMITED_WAITLIST_SENTINEL) {
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
