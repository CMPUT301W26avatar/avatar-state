
package com.example.lotteryapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import android.content.Intent;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.example.lotteryapp.activities.EventDetailsActivity;
import com.example.lotteryapp.fragments.ManageFragment;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventStorage;
import com.google.android.gms.tasks.OnSuccessListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowLooper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for ManageFragment.
 *
 * These tests verify:
 * - managed events populate the organizer event list
 * - event details navigation launches the expected activity and event id
 * - empty managed results render the empty organizer message
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ManageFragmentUnitTest {

    private EventStorage mockEventStorage;

    @Before
    public void setUp() {
        // reset shared services before each test
        ServiceLocator.reset();

        mockEventStorage = mock(EventStorage.class);

        // install the event storage mock before fragment construction
        ServiceLocator.setEventStorageForTests(mockEventStorage);
        ServiceLocator.setUserIdProviderForTests(() -> "organizer-1");
    }

    @After
    public void tearDown() {
        // clear shared service state after each test
        ServiceLocator.reset();
    }

    @Test
    public void loadsManagedEventsIntoContainer() {
        // create two managed events for the organizer list
        Event event1 = new Event("organizer-1", 10, 20);
        event1.setEventId("event-1");
        event1.setTitle("Organizer One");
        event1.setStatus(Event.EventStatus.REG_OPEN);

        Event event2 = new Event("organizer-1", 8, 12);
        event2.setEventId("event-2");
        event2.setTitle("Organizer Two");
        event2.setStatus(Event.EventStatus.REG_UPCOMING);

        // return the managed events immediately
        doAnswer(invocation -> {
            OnSuccessListener<List<Event>> success = invocation.getArgument(1);
            success.onSuccess(Arrays.asList(event1, event2));
            return null;
        }).when(mockEventStorage).getManagedEventsForUser(eq("organizer-1"), any(), any());

        // attach fragment and allow rendering to complete
        FragmentActivity activity = startFragment(new ManageFragment());
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // assert that both managed event rows were added to the container
        LinearLayout container = activity.findViewById(R.id.layout_upcoming_event_item);
        assertNotNull(container);
        assertEquals(2, container.getChildCount());

        // assert that the first rendered row contains the expected title text
        TextView firstTitle = container.getChildAt(0).findViewById(R.id.tv_event_title);
        assertEquals("Organizer One", firstTitle.getText().toString());
    }

    @Test
    public void clickingDetailsLaunchesEventDetailsActivity() {
        // create one managed event row with a valid event id
        Event event = new Event("organizer-1", 10, 20);
        event.setEventId("event-123");
        event.setTitle("Launch Me");
        event.setStatus(Event.EventStatus.REG_OPEN);

        // return the single managed event
        doAnswer(invocation -> {
            OnSuccessListener<List<Event>> success = invocation.getArgument(1);
            success.onSuccess(Collections.singletonList(event));
            return null;
        }).when(mockEventStorage).getManagedEventsForUser(eq("organizer-1"), any(), any());

        // attach fragment and let the list render
        FragmentActivity activity = startFragment(new ManageFragment());
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // click the details button on the first rendered managed event row
        LinearLayout container = activity.findViewById(R.id.layout_upcoming_event_item);
        container.getChildAt(0).findViewById(R.id.btn_event_details).performClick();

        // assert that EventDetailsActivity launches with the expected event id extra
        ShadowActivity shadowActivity = org.robolectric.Shadows.shadowOf(activity);
        Intent nextIntent = shadowActivity.getNextStartedActivity();
        assertNotNull(nextIntent);
        assertEquals(EventDetailsActivity.class.getName(), nextIntent.getComponent().getClassName());
        assertEquals("event-123", nextIntent.getStringExtra(EventDetailsActivity.EXTRA_EVENT_ID));
    }

    @Test
    public void emptyManagedEventsShowOrganizerEmptyMessage() {
        // return no managed events for the organizer
        doAnswer(invocation -> {
            OnSuccessListener<List<Event>> success = invocation.getArgument(1);
            success.onSuccess(Collections.emptyList());
            return null;
        }).when(mockEventStorage).getManagedEventsForUser(eq("organizer-1"), any(), any());

        // attach fragment and finish callbacks
        FragmentActivity activity = startFragment(new ManageFragment());
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // assert that the container shows a single empty-state text row
        LinearLayout container = activity.findViewById(R.id.layout_upcoming_event_item);
        assertNotNull(container);
        assertEquals(1, container.getChildCount());

        // assert that the rendered empty row contains the expected helper message
        TextView empty = (TextView) container.getChildAt(0);
        assertEquals("No Events Yet, Click + to Add an Event", empty.getText().toString());
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
}
