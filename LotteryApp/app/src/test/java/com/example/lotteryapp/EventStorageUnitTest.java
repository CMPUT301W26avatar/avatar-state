package com.example.lotteryapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.models.EventAddress;
import com.example.lotteryapp.models.UserAddress;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.UserIdProvider;
import com.example.lotteryapp.services.storage.EventStorage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for EventStorage filter helpers.
 * Chatgpt was used to determine the lat/long coordinates for test cases
 */
public class EventStorageUnitTest {
    private EventStorage eventStorage;

    @Before
    public void setUp() {
        ServiceLocator.reset();
        eventStorage = new EventStorage(null);

        ServiceLocator.setUserIdProviderForTests(new UserIdProvider() {
            @Override
            public String getUid() {
                return "test-uid";
            }
        });

        ServiceLocator.setEventStorageForTests(eventStorage);
    }

    @After
    public void tearDown() {
        ServiceLocator.reset();
    }

    /**
     * verify calculateDistance behaves correctly for key scenarios
     */
    @Test
    public void testHaversineInCalculateAddress() {
        // Edmonton coordinates
        double lat1 = 53.5461;
        double lon1 = -113.4938;

        // Same point
        double sameDistance = eventStorage.calculateDistance(lat1, lon1, lat1, lon1);
        assertEquals(0.0, sameDistance, 0.001);

        // ~0.5 km away
        double latNearby = 53.5500;
        double lonNearby = -113.4900;

        double nearbyDistance = eventStorage.calculateDistance(lat1, lon1, latNearby, lonNearby);
        assertTrue(nearbyDistance > 0);
        assertTrue(nearbyDistance < 2); // should be small

        // Calgary
        double latFar = 51.0447;
        double lonFar = -114.0719;

        double farDistance = eventStorage.calculateDistance(lat1, lon1, latFar, lonFar);
        assertTrue(farDistance > 250);
        assertTrue(farDistance < 350);

        // check symmetry -> distance(A,B) == distance(B,A)
        double reverseDistance = eventStorage.calculateDistance(latFar, lonFar, lat1, lon1);
        assertEquals(farDistance, reverseDistance, 0.0001);
    }


    /**
     * verify an Event within the haversine distance is included in the filter, and an Event outside is disincluded
     */
    @Test
    public void filterEventsByRadiusNormal() {
        UserAddress userAddress = new UserAddress(
                "user1",
                "Edmonton",
                53.5461,
                -113.4938
        );

        Event insideEvent = new Event("1", 1, 1);
        insideEvent.setAddress(new EventAddress("Near", "a", 53.5500, -113.4900));

        Event outsideEvent = new Event("2", 1, 1);
        outsideEvent.setAddress(new EventAddress("Far", "b", 51.0447, -114.0719));

        List<Event> result = eventStorage.filterEventsWithinRadius(
                userAddress,
                Arrays.asList(insideEvent, outsideEvent),
                5
        );

        assertEquals(1, result.size());
        assertTrue(result.contains(insideEvent));
    }

    /**
     * verify that the filter skips events with null location
     */
    @Test
    public void filterEventsByRadiusHandlesNull() {
        UserAddress userAddress = new UserAddress(
                "user1",
                "Edmonton",
                53.5461,
                -113.4938
        );

        Event nullAddressEvent = new Event("", 0, 0);
        nullAddressEvent.setAddress(null);

        List<Event> result = eventStorage.filterEventsWithinRadius(
                userAddress,
                Arrays.asList(nullAddressEvent),
                5
        );

        assertEquals(0, result.size());
    }

    /**
     * verify an Event exactly at the
     */
    @Test
    public void filterEventsWithinRadius_includesEventExactlyAtBoundary() {
        UserAddress userAddress = new UserAddress(
                "user1",
                "Origin",
                53.5461,
                -113.4938
        );

        // Create multiple boundary events (slightly different directions)
        Event boundaryEvent1 = new Event("1", 0, 0);
        boundaryEvent1.setAddress(new EventAddress("Boundary1", "a", 53.5910, -113.4938)); // north

        Event boundaryEvent2 = new Event("2", 0, 0);
        boundaryEvent2.setAddress(new EventAddress("Boundary2", "b", 53.5012, -113.4938)); // south

        Event boundaryEvent3 = new Event("3", 0, 0);
        boundaryEvent3.setAddress(new EventAddress("Boundary3", "c", 53.5905, -113.4938)); // north, again

        double distance = eventStorage.calculateDistance(
                userAddress.getLatitude(),
                userAddress.getLongitude(),
                // only need one Event to determine the boundary radius
                boundaryEvent1.getAddress().getLatitude(),
                boundaryEvent1.getAddress().getLongitude()
        );

        int radius = (int) Math.ceil(distance);

        List<Event> result = eventStorage.filterEventsWithinRadius(
                userAddress,
                Arrays.asList(boundaryEvent1, boundaryEvent2, boundaryEvent3),
                radius
        );

        // All boundary events should be included
        assertEquals(3, result.size());
        assertTrue(result.contains(boundaryEvent1));
        assertTrue(result.contains(boundaryEvent2));
        assertTrue(result.contains(boundaryEvent3));
    }
}