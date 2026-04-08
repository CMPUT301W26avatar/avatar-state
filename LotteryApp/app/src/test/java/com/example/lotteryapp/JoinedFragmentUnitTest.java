
package com.example.lotteryapp;

import static android.view.View.VISIBLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.lotteryapp.fragments.JoinedFragment;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.TicketService;
import com.example.lotteryapp.services.storage.EventPoolStorage;
import com.example.lotteryapp.services.storage.EventStorage;
import com.example.lotteryapp.services.storage.NotificationLogStorage;
import com.google.android.gms.tasks.OnSuccessListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for JoinedFragment.
 *
 * These tests verify:
 * - enrolled events populate the main pager from event id lookups
 * - switching extra event categories reloads the secondary pager data
 * - empty enrolled results show the expected empty state
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class JoinedFragmentUnitTest {

    private EventStorage mockEventStorage;
    private EventPoolStorage mockEventPoolStorage;
    private NotificationLogStorage mockNotificationLogStorage;
    private TicketService mockTicketService;

    @Before
    public void setUp() {
        // reset service state before each fragment test
        ServiceLocator.reset();

        mockEventStorage = mock(EventStorage.class);
        mockEventPoolStorage = mock(EventPoolStorage.class);
        mockNotificationLogStorage = mock(NotificationLogStorage.class);
        mockTicketService = mock(TicketService.class);

        // install mocks before fragment construction because JoinedFragment resolves them in onCreateView
        ServiceLocator.setEventStorageForTests(mockEventStorage);
        ServiceLocator.setEventPoolStorageForTests(mockEventPoolStorage);
        ServiceLocator.setNotificationLogStorageForTests(mockNotificationLogStorage);
        ServiceLocator.setTicketServiceForTests(mockTicketService);
        ServiceLocator.setUserIdProviderForTests(() -> "user-1");
    }

    @After
    public void tearDown() {
        // clear service state after each test
        ServiceLocator.reset();
    }

    @Test
    public void loadsEnrolledEventsIntoJoinedPager() throws Exception {
        // return two enrolled event ids for the current user
        doAnswer(invocation -> {
            OnSuccessListener<List<String>> success = invocation.getArgument(1);
            success.onSuccess(Arrays.asList("event-1", "event-2"));
            return null;
        }).when(mockEventPoolStorage).getEnrolledEventIdsForUser(eq("user-1"), any(), any());

        // resolve each event id into a full event object
        doAnswer(invocation -> {
            String eventId = invocation.getArgument(0);
            OnSuccessListener<Event> success = invocation.getArgument(1);

            Event event = new Event("organizer-" + eventId, 10, 20);
            event.setEventId(eventId);
            event.setTitle("Title " + eventId);
            event.setEventDateMs(System.currentTimeMillis() + ("event-1".equals(eventId) ? 60_000L : 120_000L));
            success.onSuccess(event);
            return null;
        }).when(mockEventStorage).getEvent(any(), any(), any());

        // attach fragment and let its async loading complete
        FragmentActivity activity = startFragment(new JoinedFragment());
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // assert that the joined pager is visible after events are resolved
        ViewPager2 pager = activity.findViewById(R.id.joined_events_pager);
        assertNotNull(pager);
        assertEquals(VISIBLE, pager.getVisibility());

        // assert that the pager adapter contains the resolved joined events
        assertNotNull(pager.getAdapter());
        assertEquals(2, pager.getAdapter().getItemCount());

        // assert that the empty state is hidden once enrolled events are available
        TextView empty = activity.findViewById(R.id.tv_joined_empty);
        assertNotNull(empty);
        assertTrue(empty.getVisibility() != VISIBLE || !"No enrolled events yet.".contentEquals(empty.getText()));
    }

    @Test
    public void switchingExtraFilterToSelectedLoadsSelectedEvents() throws Exception {
        // keep the primary enrolled section stable while testing the extra filter flow
        doAnswer(invocation -> {
            OnSuccessListener<List<String>> success = invocation.getArgument(1);
            success.onSuccess(Collections.emptyList());
            return null;
        }).when(mockEventPoolStorage).getEnrolledEventIdsForUser(eq("user-1"), any(), any());

        // return one invited event id when the selected filter is requested
        doAnswer(invocation -> {
            OnSuccessListener<List<String>> success = invocation.getArgument(1);
            success.onSuccess(Collections.singletonList("selected-1"));
            return null;
        }).when(mockEventPoolStorage).getInvitedEventIdsForUser(eq("user-1"), any(), any());

        // resolve the selected event id into a full event object
        doAnswer(invocation -> {
            String eventId = invocation.getArgument(0);
            OnSuccessListener<Event> success = invocation.getArgument(1);

            Event event = new Event("organizer-1", 10, 20);
            event.setEventId(eventId);
            event.setTitle("Selected Event");
            event.setEventDateMs(System.currentTimeMillis() + 60_000L);
            success.onSuccess(event);
            return null;
        }).when(mockEventStorage).getEvent(eq("selected-1"), any(), any());

        // attach fragment and let the initial joined load finish
        JoinedFragment fragment = new JoinedFragment();
        FragmentActivity activity = startFragment(fragment);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // invoke the private extra filter loader with the SELECTED enum value
        invokePrivateLoadExtraEvents(fragment, "SELECTED");
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // assert that the extra section title switches to the selected label
        TextView title = activity.findViewById(R.id.tv_extra_section_title);
        assertNotNull(title);
        assertEquals("Selected Events", title.getText().toString());

        // assert that the extra pager becomes visible and contains the selected event
        ViewPager2 extraPager = activity.findViewById(R.id.extra_events_pager);
        assertNotNull(extraPager);
        assertEquals(VISIBLE, extraPager.getVisibility());
        assertNotNull(extraPager.getAdapter());
        assertEquals(1, extraPager.getAdapter().getItemCount());
    }

    @Test
    public void emptyJoinedResultsShowEmptyState() {
        // return no enrolled events for the current user
        doAnswer(invocation -> {
            OnSuccessListener<List<String>> success = invocation.getArgument(1);
            success.onSuccess(Collections.emptyList());
            return null;
        }).when(mockEventPoolStorage).getEnrolledEventIdsForUser(eq("user-1"), any(), any());

        // attach fragment and finish callbacks
        FragmentActivity activity = startFragment(new JoinedFragment());
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // assert that the joined empty state text is shown when no events are returned
        TextView empty = activity.findViewById(R.id.tv_joined_empty);
        assertNotNull(empty);
        assertEquals(VISIBLE, empty.getVisibility());
        assertTrue(empty.getText().toString().toLowerCase().contains("no enrolled events"));
    }

    private FragmentActivity startFragment(Fragment fragment) {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class)
                .create()
                .start()
                .resume()
                .visible()
                .get();

        activity.getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, fragment)
                .commitNow();

        return activity;
    }

    private void invokePrivateLoadExtraEvents(JoinedFragment fragment, String enumName) throws Exception {
        Class<?> enumType = Class.forName("com.example.lotteryapp.fragments.JoinedFragment$ExtraEventFilter");
        Object enumValue = Enum.valueOf((Class<Enum>) enumType.asSubclass(Enum.class), enumName);

        Method loadExtraEvents = fragment.getClass().getDeclaredMethod("loadExtraEvents", enumType);
        loadExtraEvents.setAccessible(true);
        loadExtraEvents.invoke(fragment, enumValue);
    }
}
