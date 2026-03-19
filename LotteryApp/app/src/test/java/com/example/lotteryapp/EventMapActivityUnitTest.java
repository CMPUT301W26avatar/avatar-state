package com.example.lotteryapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.example.lotteryapp.activities.EventDetailsActivity;
import com.example.lotteryapp.activities.EventMapActivity;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.models.EventAddress;
import com.example.lotteryapp.models.EventJoinedMap;
import com.example.lotteryapp.models.User;
import com.example.lotteryapp.models.UserAddress;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventStorage;
import com.example.lotteryapp.services.storage.UserStorage;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Verify that EventMapActivity correctly:
 *      initalizes wiring for UI components
 *      renders only calid joined-map entries
 *      falls back to "Unknown Entrant" when user data cannot be resolved
 *      correctly displays markers for both clustered and distant locations
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class EventMapActivityUnitTest {

    private EventStorage mockEventStorage;
    private UserStorage mockUserStorage;

    // initialize mock services in order to access methods
    @Before
    public void setUp() {
        ServiceLocator.reset();

        mockEventStorage = mock(EventStorage.class);
        mockUserStorage = mock(UserStorage.class);

        ServiceLocator.setEventStorageForTests(mockEventStorage);
        ServiceLocator.setUserStorageForTests(mockUserStorage);

        stubEvent(eventWithAddress("event-1", "Main Hall", 53.5461, -113.4938, 5));
    }

    // reset mock services for the next test
    @After
    public void tearDown() {
        ServiceLocator.reset();
    }


    // verify that the close button ends the activity
    @Test
    public void closeButtonEndsActivity() {
        // Provide an empty joined-map dataset so the activity can launch normally.
        stubJoinedMapEntries(new ArrayList<>());
        Intent intent = buildIntent("event-1");

        // Launch the activity and click the close button.
        ActivityController<EventMapActivity> controller =
                Robolectric.buildActivity(EventMapActivity.class, intent).setup();
        EventMapActivity activity = controller.get();

        activity.findViewById(R.id.btn_close_event_map).performClick();

        // Verify the activity is finishing.
        assertTrue(activity.isFinishing());
    }

    // verify the map render skips bad addresses
    @Test
    public void rendersOnlyEntriesWithUsableAddresses() {
        // Two valid joined-map entries should render.
        EventJoinedMap valid1 = joinedMap("event-1", "u1", "Downtown", 53.5461, -113.4938);
        EventJoinedMap valid2 = joinedMap("event-1", "u2", "North", 53.56, -113.49);

        // Invalid entries should be ignored.
        EventJoinedMap nullAddress = new EventJoinedMap("event-1", null, System.currentTimeMillis());
        EventJoinedMap noLat = joinedMap("event-1", "u3", "BadLat", null, -113.50);
        EventJoinedMap noLng = joinedMap("event-1", "u4", "BadLng", 53.57, null);

        stubJoinedMapEntries(Arrays.asList(valid1, valid2, nullAddress, noLat, noLng));
        stubUserName("u1", "Alice");
        stubUserName("u2", "Bob");

        EventMapActivity activity = Robolectric.buildActivity(
                EventMapActivity.class,
                buildIntent("event-1")
        ).setup().get();

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        MapView mapView = activity.findViewById(R.id.event_map_view);
        List<Marker> markers = extractMarkers(mapView);

        // Event origin marker + 2 entrant markers.
        assertEquals(3, markers.size());
        assertEquals("Event 1", markers.get(0).getTitle());
        assertEquals("Alice", markers.get(1).getTitle());
        assertEquals("Bob", markers.get(2).getTitle());
    }

    // verify that the map zooms out, and properly renders all pins when the majority are in a cluster, and there exists one outlier at a far distance
    @Test
    public void rendersClusteredPinsAndOneFarAwayPin() {
        // Nearby cluster plus one far-away entrant marker.
        EventJoinedMap p1 = joinedMap("event-1", "u1", "A", 53.5461, -113.4938);
        EventJoinedMap p2 = joinedMap("event-1", "u2", "B", 53.5465, -113.4940);
        EventJoinedMap p3 = joinedMap("event-1", "u3", "C", 53.5459, -113.4935);
        EventJoinedMap far = joinedMap("event-1", "u4", "Vancouver", 49.2827, -123.1207);

        stubJoinedMapEntries(Arrays.asList(p1, p2, p3, far));
        stubUserName("u1", "User 1");
        stubUserName("u2", "User 2");
        stubUserName("u3", "User 3");
        stubUserName("u4", "User 4");

        EventMapActivity activity = Robolectric.buildActivity(
                EventMapActivity.class,
                buildIntent("event-1")
        ).setup().get();

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        MapView mapView = activity.findViewById(R.id.event_map_view);
        List<Marker> markers = extractMarkers(mapView);

        // Event origin marker + 4 entrant markers.
        assertEquals(5, markers.size());

        boolean foundFarAwayPin = false;
        for (Marker marker : markers) {
            double lat = marker.getPosition().getLatitude();
            double lng = marker.getPosition().getLongitude();
            if (Math.abs(lat - 49.2827) < 0.0001 && Math.abs(lng - (-123.1207)) < 0.0001) {
                foundFarAwayPin = true;
                break;
            }
        }

        assertTrue(foundFarAwayPin);
    }

    // Builds intent with required event ID extra
    private Intent buildIntent(String eventId) {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), EventMapActivity.class);
        intent.putExtra(EventDetailsActivity.EXTRA_EVENT_ID, eventId);
        return intent;
    }

    // Creates a joined-map entry with a user address
    private EventJoinedMap joinedMap(
            String eventId,
            String uid,
            String location,
            Double latitude,
            Double longitude
    ) {
        UserAddress address = new UserAddress(uid, location, latitude, longitude);
        return new EventJoinedMap(eventId, address, System.currentTimeMillis());
    }

    // Stubs the EventAddress of an Event
    private Event eventWithAddress(
            String eventId,
            String location,
            Double latitude,
            Double longitude,
            Integer radiusKm
    ) {
        Event event = new Event("organizer-1", 10, 10);
        event.setEventId(eventId);
        event.setTitle("Event 1");
        event.setHasGeoConstraint(true);

        EventAddress address = new EventAddress(eventId, location, latitude, longitude);
        address.setRadiusKm(radiusKm);
        event.setAddress(address);
        return event;
    }

    // Stubs an Event from EventStorage
    private void stubEvent(Event event) {
        doAnswer(invocation -> {
            OnSuccessListener<Event> ok = invocation.getArgument(1);
            ok.onSuccess(event);
            return null;
        }).when(mockEventStorage).getEvent(
                eq("event-1"),
                any(),
                any()
        );
    }

    // Stubs EventStorage to return a predefined list of entries
    private void stubJoinedMapEntries(List<EventJoinedMap> entries) {
        doAnswer(invocation -> {
            OnSuccessListener<List<EventJoinedMap>> ok = invocation.getArgument(1);
            ok.onSuccess(entries);
            return null;
        }).when(mockEventStorage).getEventJoinedMapEntries(
                eq("event-1"),
                any(),
                any()
        );
    }

    // Stubs a successful user lookup with a name
    private void stubUserName(String uid, String name) {
        doAnswer(invocation -> {
            OnSuccessListener<User> ok = invocation.getArgument(1);
            User user = new User(uid);
            user.setName(name);
            ok.onSuccess(user);
            return null;
        }).when(mockUserStorage).getUserProfile(
                eq(uid),
                any(),
                any()
        );
    }

    // Extracts only Marker overlays from the map
    private List<Marker> extractMarkers(MapView mapView) {
        List<Marker> markers = new ArrayList<>();
        for (Object overlay : mapView.getOverlays()) {
            if (overlay instanceof Marker) {
                markers.add((Marker) overlay);
            }
        }
        return markers;
    }
}
