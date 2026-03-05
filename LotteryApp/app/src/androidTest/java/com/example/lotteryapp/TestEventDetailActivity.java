package com.example.lotteryapp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.gms.tasks.OnSuccessListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.sql.Timestamp;
import java.util.Calendar;

@RunWith(AndroidJUnit4.class)
public class TestEventDetailActivity {

    private static final String TEST_UID = "uid-test-123";

    private EventStorage eventStorage;
    private EventPoolStorage eventPoolStorage;

    @Before
    public void setUp() {
        eventStorage = mock(EventStorage.class);
        eventPoolStorage = mock(EventPoolStorage.class);

        ServiceLocator.setEventStorageForTests(eventStorage);
        ServiceLocator.setEventPoolStorageForTests(eventPoolStorage);
        ServiceLocator.setUserIdProviderForTests(() -> TEST_UID);
    }

    @After
    public void tearDown() {
        ServiceLocator.reset();
    }

    private static Intent launchIntent(String eventId, String organizerId) {
        Context ctx = ApplicationProvider.getApplicationContext();
        Intent i = new Intent(ctx, EventDetailActivity.class);
        i.putExtra(EventDetailActivity.EXTRA_EVENT_ID, eventId);
        i.putExtra(EventDetailActivity.EXTRA_ORGANIZER_ID, organizerId);
        return i;
    }

    private static Timestamp ts(int year, int month1to12, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month1to12 - 1, day, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return new Timestamp(cal.getTimeInMillis());
    }

    private static Event openEventWithRegWindowNow(String organizerId, int capacity) {
        Event e = new Event(organizerId, Event.EventStatus.OPEN, capacity);
        e.setTitle("Sample Event");

        // regStart <= now <= regEnd
        Timestamp now = new Timestamp(System.currentTimeMillis());
        e.setRegStart(new Timestamp(now.getTime() - 24L * 60 * 60 * 1000));
        e.setRegEnd(new Timestamp(now.getTime() + 24L * 60 * 60 * 1000));

        // optional set draw time
        e.setDrawTime(new Timestamp(now.getTime() + 2L * 24 * 60 * 60 * 1000));
        return e;
    }

    @Test
    public void testActivityOpens() {
        String eventId = "event-1";

        Event event = openEventWithRegWindowNow("org-1", 50);
        event.eventId = eventId;

        // EventDetailActivity loads via eventStorage.getEvent(...)
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            OnSuccessListener<Event> ok = (OnSuccessListener<Event>) inv.getArgument(1);
            ok.onSuccess(event);
            return null;
        }).when(eventStorage).getEvent(eq(eventId), any(), any());

        // enrollment status check
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            OnSuccessListener<String> ok = (OnSuccessListener<String>) inv.getArgument(2);
            ok.onSuccess(null); // not enrolled
            return null;
        }).when(eventPoolStorage).getEntrantStatus(eq(eventId), eq(TEST_UID), any(), any());

        try (ActivityScenario<EventDetailActivity> scenario =
                     ActivityScenario.launch(launchIntent(eventId, "org-1"))) {

            onView(withId(R.id.tvCapacity)).check(matches(withText(containsString("50"))));
            onView(withId(R.id.btnBack)).check(matches(isDisplayed()));

            onView(withId(R.id.btnEnroll)).check(matches(isDisplayed()));
            onView(withId(R.id.btnEnroll)).check(matches(withText("Enroll")));
        }
    }

    @Test
    public void testEnrollButtonAddsEntry() {
        String eventId = "event-1";

        Event event = openEventWithRegWindowNow("org-1", 50);
        event.eventId = eventId;

        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            OnSuccessListener<Event> ok = (OnSuccessListener<Event>) inv.getArgument(1);
            ok.onSuccess(event);
            return null;
        }).when(eventStorage).getEvent(eq(eventId), any(), any());

        // Initially not enrolled
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            OnSuccessListener<String> ok = (OnSuccessListener<String>) inv.getArgument(2);
            ok.onSuccess(null);
            return null;
        }).when(eventPoolStorage).getEntrantStatus(eq(eventId), eq(TEST_UID), any(), any());

        // Enroll succeeds immediately
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            OnSuccessListener<Void> ok = (OnSuccessListener<Void>) inv.getArgument(2);
            ok.onSuccess(null);
            return null;
        }).when(eventPoolStorage).enrollInEvent(eq(eventId), any(), any(), any());

        try (ActivityScenario<EventDetailActivity> scenario =
                     ActivityScenario.launch(launchIntent(eventId, "org-1"))) {

            onView(withId(R.id.btnEnroll)).perform(click());

            verify(eventPoolStorage, timeout(1500))
                    .enrollInEvent(eq(eventId), any(Entrant.class), any(), any());

            onView(withId(R.id.btnEnroll)).check(matches(withText("Unenroll")));
        }
    }

    @Test
    public void testUnenrollButtonDeletesEntry() {
        String eventId = "event-1";

        Event event = openEventWithRegWindowNow("org-1", 50);
        event.eventId = eventId;

        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            OnSuccessListener<Event> ok = (OnSuccessListener<Event>) inv.getArgument(1);
            ok.onSuccess(event);
            return null;
        }).when(eventStorage).getEvent(eq(eventId), any(), any());

        // Initially enrolled
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            OnSuccessListener<String> ok = (OnSuccessListener<String>) inv.getArgument(2);
            ok.onSuccess(Entrant.EntrantStatus.ENROLLED.name());
            return null;
        }).when(eventPoolStorage).getEntrantStatus(eq(eventId), eq(TEST_UID), any(), any());

        // Delete succeeds immediately (EventDetailActivity calls deleteEntry)
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            OnSuccessListener<Void> ok = (OnSuccessListener<Void>) inv.getArgument(2);
            ok.onSuccess(null);
            return null;
        }).when(eventPoolStorage).deleteEntry(eq(eventId), eq(TEST_UID), any(), any());

        try (ActivityScenario<EventDetailActivity> scenario =
                     ActivityScenario.launch(launchIntent(eventId, "org-1"))) {

            // Should start as Unenroll
            onView(withId(R.id.btnEnroll)).check(matches(withText("Unenroll")));

            onView(withId(R.id.btnEnroll)).perform(click());

            verify(eventPoolStorage, timeout(1500))
                    .deleteEntry(eq(eventId), eq(TEST_UID), any(), any());

            // Should toggle back
            onView(withId(R.id.btnEnroll)).check(matches(withText("Enroll")));
        }
    }

    @Test
    public void testBackButtonExits() {
        String eventId = "event-1";

        Event event = openEventWithRegWindowNow("org-1", 50);
        event.eventId = eventId;

        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            OnSuccessListener<Event> ok = (OnSuccessListener<Event>) inv.getArgument(1);
            ok.onSuccess(event);
            return null;
        }).when(eventStorage).getEvent(eq(eventId), any(), any());

        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            OnSuccessListener<String> ok = (OnSuccessListener<String>) inv.getArgument(2);
            ok.onSuccess(null);
            return null;
        }).when(eventPoolStorage).getEntrantStatus(eq(eventId), eq(TEST_UID), any(), any());

        try (ActivityScenario<EventDetailActivity> scenario =
                     ActivityScenario.launch(launchIntent(eventId, "org-1"))) {

            onView(withId(R.id.btnBack)).perform(click());
            scenario.onActivity(a -> assertTrue(a.isFinishing() || a.isDestroyed()));
        }
    }
}