package com.example.lotteryapp;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import android.app.Activity;
import android.app.Instrumentation;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class TestListEventsActivity {

    private EventPoolStorage eventPool;

    @Before
    public void setUp() {
        eventPool = mock(EventPoolStorage.class);
        ServiceLocator.setEventPoolStorageForTests(eventPool);
    }

    @After
    public void tearDown() {
        ServiceLocator.reset();
        try { Intents.release(); } catch (Throwable ignored) {}
    }

    @Test
    public void testActivityOpens_BackButtonExits() {
        try (ActivityScenario<ListEventsActivity> scenario =
                     ActivityScenario.launch(ListEventsActivity.class)) {

            onView(withId(R.id.listEvents)).check(matches(isDisplayed()));
            onView(withId(R.id.btnBack)).check(matches(isDisplayed()));

            onView(withId(R.id.btnBack)).perform(click());
            scenario.onActivity(a -> assertTrue(a.isFinishing() || a.isDestroyed()));
        }
    }

    @Test
    public void testLoadedEventHasTitleAndCapacity() {
        Event e1 = new Event("org-1", Event.EventStatus.OPEN, 50);
        e1.eventId = "event-1";
        e1.setTitle("Event One");

        Event e2 = new Event("org-2", Event.EventStatus.OPEN, 25);
        e2.eventId = "event-2";
        e2.setTitle("Event Two");

        List<Event> fetched = Arrays.asList(e1, e2);

        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            OnSuccessListener<List<Event>> ok = (OnSuccessListener<List<Event>>) inv.getArgument(0);
            ok.onSuccess(fetched);
            return null;
        }).when(eventPool).listOpenEvents(any(), any());

        // Arrange: countEntrants returns counts
        doAnswer(inv -> {
            String eventId = inv.getArgument(0);
            @SuppressWarnings("unchecked")
            OnSuccessListener<Integer> ok = (OnSuccessListener<Integer>) inv.getArgument(1);
            if ("event-1".equals(eventId)) ok.onSuccess(12);
            else if ("event-2".equals(eventId)) ok.onSuccess(3);
            else ok.onSuccess(0);
            return null;
        }).when(eventPool).countEntrants(anyString(), any(), any());

        try (ActivityScenario<ListEventsActivity> scenario =
                     ActivityScenario.launch(ListEventsActivity.class)) {

            onData(anything())
                    .inAdapterView(withId(R.id.listEvents))
                    .atPosition(0)
                    .onChildView(withId(android.R.id.text1))
                    .check(matches(withText("Event One")));

            onData(anything())
                    .inAdapterView(withId(R.id.listEvents))
                    .atPosition(1)
                    .onChildView(withId(android.R.id.text1))
                    .check(matches(withText("Event Two")));

            // Verify: countEntrants called for both events (ListEventsActivity loops events and calls countEntrants) :contentReference[oaicite:5]{index=5}
            verify(eventPool, timeout(1500)).countEntrants(eq("event-1"), any(), any());
            verify(eventPool, timeout(1500)).countEntrants(eq("event-2"), any(), any());
        }
    }

    @Test
    public void testClickEventOpensDetails() {
        Intents.init();

        Event e1 = new Event("org-1", Event.EventStatus.OPEN, 50);
        e1.eventId = "event-1";
        e1.setTitle("Event One");

        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            OnSuccessListener<List<Event>> ok = (OnSuccessListener<List<Event>>) inv.getArgument(0);
            ok.onSuccess(Arrays.asList(e1));
            return null;
        }).when(eventPool).listOpenEvents(any(), any());

        // counts not important for intent test
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            OnSuccessListener<Integer> ok = (OnSuccessListener<Integer>) inv.getArgument(1);
            ok.onSuccess(0);
            return null;
        }).when(eventPool).countEntrants(anyString(), any(), any());

        // Prevent actually launching the next activity (optional)
        intending(hasComponent(EventDetailActivity.class.getName()))
                .respondWith(new Instrumentation.ActivityResult(Activity.RESULT_OK, null));

        try (ActivityScenario<ListEventsActivity> scenario =
                     ActivityScenario.launch(ListEventsActivity.class)) {

            onData(anything())
                    .inAdapterView(withId(R.id.listEvents))
                    .atPosition(0)
                    .perform(click());

            // ListEventsActivity builds intent with EXTRA_EVENT_ID/EXTRA_ORGANIZER_ID :contentReference[oaicite:6]{index=6}
            intended(allOf(
                    hasComponent(EventDetailActivity.class.getName()),
                    hasExtra(ListEventsActivity.EXTRA_EVENT_ID, "event-1"),
                    hasExtra(ListEventsActivity.EXTRA_ORGANIZER_ID, "org-1")
            ));
        }
    }
}