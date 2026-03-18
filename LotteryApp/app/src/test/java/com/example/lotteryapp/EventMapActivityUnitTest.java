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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class EventMapActivityUnitTest {

    private EventStorage mockEventStorage;
    private UserStorage mockUserStorage;

    @Before
    public void setUp() {
        ServiceLocator.reset();

        mockEventStorage = mock(EventStorage.class);
        mockUserStorage = mock(UserStorage.class);

        ServiceLocator.setEventStorageForTests(mockEventStorage);
        ServiceLocator.setUserStorageForTests(mockUserStorage);
    }

    @After
    public void tearDown() {
        ServiceLocator.reset();
    }

    @Test
    public void closeButton_finishesActivity() {
        stubJoinedMapEntries(new ArrayList<>());
        Intent intent = buildIntent("event-1");

        ActivityController<EventMapActivity> controller =
                Robolectric.buildActivity(EventMapActivity.class, intent).setup();
        EventMapActivity activity = controller.get();

        activity.findViewById(R.id.btn_close_event_map).performClick();

        assertTrue(activity.isFinishing());
    }

    @Test
    public void rendersOnlyEntriesWithUsablePinnedAddresses() {
        EventJoinedMap valid1 = joinedMap("event-1", "u1", "Downtown", 53.5461, -113.4938);
        EventJoinedMap valid2 = joinedMap("event-1", "u2", "North", 53.56, -113.49);
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

        assertEquals(2, markers.size());
        assertEquals("Alice", markers.get(0).getTitle());
        assertEquals("Bob", markers.get(1).getTitle());
    }

    @Test
    public void usesUnknownEntrantWhenNameMissingOrLookupFails() {
        EventJoinedMap missingName = joinedMap("event-1", "u1", "Downtown", 53.5461, -113.4938);
        EventJoinedMap lookupFails = joinedMap("event-1", "u2", "South", 53.50, -113.50);
        EventJoinedMap blankUid = joinedMap("event-1", "", "West", 53.54, -113.48);

        stubJoinedMapEntries(Arrays.asList(missingName, lookupFails, blankUid));
        stubBlankUserName("u1");
        stubUserLookupFailure("u2");

        EventMapActivity activity = Robolectric.buildActivity(
                EventMapActivity.class,
                buildIntent("event-1")
        ).setup().get();

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        MapView mapView = activity.findViewById(R.id.event_map_view);
        List<Marker> markers = extractMarkers(mapView);

        assertEquals(3, markers.size());
        assertEquals("Unknown entrant", markers.get(0).getTitle());
        assertEquals("Unknown entrant", markers.get(1).getTitle());
        assertEquals("Unknown entrant", markers.get(2).getTitle());
    }

    @Test
    public void rendersClusteredPinsAndOneFarAwayPin() {
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

        assertEquals(4, markers.size());

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

    private Intent buildIntent(String eventId) {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), EventMapActivity.class);
        intent.putExtra(EventDetailsActivity.EXTRA_EVENT_ID, eventId);
        return intent;
    }

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

    private void stubBlankUserName(String uid) {
        doAnswer(invocation -> {
            OnSuccessListener<User> ok = invocation.getArgument(1);
            User user = new User(uid);
            user.setName("");
            ok.onSuccess(user);
            return null;
        }).when(mockUserStorage).getUserProfile(
                eq(uid),
                any(),
                any()
        );
    }

    private void stubUserLookupFailure(String uid) {
        doAnswer(invocation -> {
            OnFailureListener fail = invocation.getArgument(2);
            fail.onFailure(new Exception("lookup failed"));
            return null;
        }).when(mockUserStorage).getUserProfile(
                eq(uid),
                any(),
                any()
        );
    }

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