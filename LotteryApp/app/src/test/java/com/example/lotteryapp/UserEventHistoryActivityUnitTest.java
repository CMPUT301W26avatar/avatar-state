package com.example.lotteryapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import android.content.Intent;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.lotteryapp.activities.EventDetailsActivity;
import com.example.lotteryapp.activities.UserEventHistoryActivity;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.models.UserEventHistory;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventStorage;
import com.example.lotteryapp.services.storage.UserStorage;
import com.google.android.gms.tasks.OnSuccessListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for UserEventHistoryActivity.
 *
 * These tests verify:
 * - history rows render from UserStorage + EventStorage results
 * - details button opens EventDetailsActivity with the correct event ID
 * - empty history shows an empty state instead of crashing
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UserEventHistoryActivityUnitTest {

    private UserStorage mockUserStorage;
    private EventStorage mockEventStorage;

    @Before
    public void setUp() {
        ServiceLocator.reset();

        mockUserStorage = mock(UserStorage.class);
        mockEventStorage = mock(EventStorage.class);

        ServiceLocator.setUserStorageForTests(mockUserStorage);
        ServiceLocator.setEventStorageForTests(mockEventStorage);
        ServiceLocator.setUserIdProviderForTests(() -> "user-1");
    }

    @After
    public void tearDown() {
        ServiceLocator.reset();
    }

    /**
     * Verifies that the activity renders event history rows from storage results.
     */
    @Test
    public void loadsHistoryRowsIntoContainer() {
        long now = System.currentTimeMillis();

        UserEventHistory history1 = new UserEventHistory();
        history1.setUserId("user-1");
        history1.setEventId("event-1");
        history1.setStatus("INVITED");
        history1.setUpdatedAtMs(now);

        UserEventHistory history2 = new UserEventHistory();
        history2.setUserId("user-1");
        history2.setEventId("event-2");
        history2.setStatus("DECLINED");
        history2.setUpdatedAtMs(now - 60_000);

        List<UserEventHistory> historyList = Arrays.asList(history1, history2);

        Event event1 = new Event("organizer-1", 10, 20);
        event1.setEventId("event-1");
        event1.setTitle("Hackathon");

        Event event2 = new Event("organizer-2", 5, 10);
        event2.setEventId("event-2");
        event2.setTitle("Career Fair");

        doAnswer(invocation -> {
            OnSuccessListener<List<UserEventHistory>> success = invocation.getArgument(1);
            success.onSuccess(historyList);
            return null;
        }).when(mockUserStorage).getUserEventHistory(
                eq("user-1"),
                any(),
                any()
        );

        doAnswer(invocation -> {
            String eventId = invocation.getArgument(0);
            OnSuccessListener<Event> success = invocation.getArgument(1);

            if ("event-1".equals(eventId)) {
                success.onSuccess(event1);
            } else if ("event-2".equals(eventId)) {
                success.onSuccess(event2);
            }

            return null;
        }).when(mockEventStorage).getEvent(any(), any(), any());

        ActivityController<UserEventHistoryActivity> controller =
                Robolectric.buildActivity(UserEventHistoryActivity.class).create().start().resume().visible();

        UserEventHistoryActivity activity = controller.get();
        LinearLayout container = activity.findViewById(R.id.layout_history_items);

        assertNotNull(container);
        assertEquals(2, container.getChildCount());

        TextView firstTitle = container.getChildAt(0).findViewById(R.id.tv_event_title);
        TextView secondTitle = container.getChildAt(1).findViewById(R.id.tv_event_title);

        assertEquals("Hackathon", firstTitle.getText().toString());
        assertEquals("Career Fair", secondTitle.getText().toString());
    }

    /**
     * Verifies that tapping the details button opens EventDetailsActivity
     * with the selected event ID.
     */
    @Test
    public void clickingDetailsLaunchesEventDetailsActivity() {
        long now = System.currentTimeMillis();

        UserEventHistory history = new UserEventHistory();
        history.setUserId("user-1");
        history.setEventId("event-123");
        history.setStatus("WAITLISTED");
        history.setUpdatedAtMs(now);

        Event event = new Event("organizer-1", 10, 20);
        event.setEventId("event-123");
        event.setTitle("Lottery Night");

        doAnswer(invocation -> {
            OnSuccessListener<List<UserEventHistory>> success = invocation.getArgument(1);
            success.onSuccess(Collections.singletonList(history));
            return null;
        }).when(mockUserStorage).getUserEventHistory(
                eq("user-1"),
                any(),
                any()
        );

        doAnswer(invocation -> {
            OnSuccessListener<Event> success = invocation.getArgument(1);
            success.onSuccess(event);
            return null;
        }).when(mockEventStorage).getEvent(eq("event-123"), any(), any());

        ActivityController<UserEventHistoryActivity> controller =
                Robolectric.buildActivity(UserEventHistoryActivity.class).create().start().resume().visible();

        UserEventHistoryActivity activity = controller.get();
        LinearLayout container = activity.findViewById(R.id.layout_history_items);

        assertEquals(1, container.getChildCount());

        container.getChildAt(0)
                .findViewById(R.id.btn_event_details)
                .performClick();

        ShadowActivity shadowActivity = org.robolectric.Shadows.shadowOf(activity);
        Intent nextIntent = shadowActivity.getNextStartedActivity();

        assertNotNull(nextIntent);
        assertEquals(
                EventDetailsActivity.class.getName(),
                nextIntent.getComponent().getClassName()
        );
        assertEquals(
                "event-123",
                nextIntent.getStringExtra(EventDetailsActivity.EXTRA_EVENT_ID)
        );
    }

    /**
     * Verifies that an empty history list shows an empty state instead of crashing.
     */
    @Test
    public void emptyHistoryShowsEmptyState() {
        doAnswer(invocation -> {
            OnSuccessListener<List<UserEventHistory>> success = invocation.getArgument(1);
            success.onSuccess(Collections.emptyList());
            return null;
        }).when(mockUserStorage).getUserEventHistory(
                eq("user-1"),
                any(),
                any()
        );

        ActivityController<UserEventHistoryActivity> controller =
                Robolectric.buildActivity(UserEventHistoryActivity.class).create().start().resume().visible();

        UserEventHistoryActivity activity = controller.get();
        LinearLayout container = activity.findViewById(R.id.layout_history_items);

        assertNotNull(container);
        assertEquals(1, container.getChildCount());

        TextView emptyView = (TextView) container.getChildAt(0);
        assertTrue(emptyView.getText().toString().toLowerCase().contains("no event history"));
    }
}