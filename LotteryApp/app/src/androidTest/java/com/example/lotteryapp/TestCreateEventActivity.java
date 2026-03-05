package com.example.lotteryapp;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.content.Intent;
import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.contrib.PickerActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.sql.Timestamp;
import java.util.Calendar;

@RunWith(AndroidJUnit4.class)
public class TestCreateEventActivity {

    private static final int IDX_TITLE = 0;
    private static final int IDX_CAPACITY = 1;
    private static final int IDX_WAITLIST_CAP = 2;
    private static final int IDX_WAITLIST_ON = 3;
    private static final int IDX_REG_START = 4;
    private static final int IDX_REG_END = 5;
    private static final int IDX_DRAW_TIME = 6;

    private EventStorage storage;

    @Before
    public void setUp() {
        storage = mock(EventStorage.class);
        ServiceLocator.setEventStorageForTests(storage);
    }

    @After
    public void tearDown() {
        ServiceLocator.reset();
    }

    private static Intent launchIntentWithOrganizer(String organizerId) {
        Context ctx = ApplicationProvider.getApplicationContext();
        Intent i = new Intent(ctx, CreateEventsActivity.class);
        i.putExtra("organizerId", organizerId);
        return i;
    }

    private static void clickRow(int index) {
        onData(anything())
                .inAdapterView(withId(R.id.listFields))
                .atPosition(index)
                .perform(click());
    }

    private static void fillTextDialog(String value) {
        onView(isAssignableFrom(EditText.class))
                .perform(replaceText(value), closeSoftKeyboard());
        onView(withText("OK")).perform(click());
    }

    private static void setDateInDatePicker(int year, int month, int day) {
        onView(isAssignableFrom(android.widget.DatePicker.class))
                .perform(PickerActions.setDate(year, month, day));
        onView(withText("OK")).perform(click());
    }

    private static Timestamp tsAtMidnight(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, day, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return new Timestamp(cal.getTimeInMillis());
    }

    @Test
    public void testActivityOpens() {
        try (ActivityScenario<CreateEventsActivity> scenario =
                     ActivityScenario.launch(launchIntentWithOrganizer("org-123"))) {

            onView(withId(R.id.listFields)).check(matches(isDisplayed()));
            onView(withId(R.id.btnSaveEvent)).check(matches(isDisplayed()));
            onView(withId(R.id.btnBack)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void testBackButtonExits() {
        try (ActivityScenario<CreateEventsActivity> scenario =
                     ActivityScenario.launch(launchIntentWithOrganizer("org-123"))) {

            onView(withId(R.id.btnBack)).perform(click());
            scenario.onActivity(a -> assertTrue(a.isFinishing() || a.isDestroyed()));
        }
    }

    @Test
    public void testFieldsShowValuesWhenSet() {
        try (ActivityScenario<CreateEventsActivity> scenario =
                     ActivityScenario.launch(launchIntentWithOrganizer("org-123"))) {

            clickRow(IDX_TITLE);
            fillTextDialog("My Event");
            onData(anything()).inAdapterView(withId(R.id.listFields)).atPosition(IDX_TITLE)
                    .onChildView(withId(android.R.id.text2))
                    .check(matches(withText("My Event")));

            clickRow(IDX_CAPACITY);
            fillTextDialog("25");
            onData(anything()).inAdapterView(withId(R.id.listFields)).atPosition(IDX_CAPACITY)
                    .onChildView(withId(android.R.id.text2))
                    .check(matches(withText("25")));

            clickRow(IDX_WAITLIST_ON);
            onData(anything()).inAdapterView(withId(R.id.listFields)).atPosition(IDX_WAITLIST_ON)
                    .onChildView(withId(android.R.id.text2))
                    .check(matches(withText("Yes")));
            clickRow(IDX_WAITLIST_ON);
            onData(anything()).inAdapterView(withId(R.id.listFields)).atPosition(IDX_WAITLIST_ON)
                    .onChildView(withId(android.R.id.text2))
                    .check(matches(withText("No")));

            clickRow(IDX_REG_START);
            setDateInDatePicker(2026, 3, 10);
            clickRow(IDX_REG_END);
            setDateInDatePicker(2026, 3, 20);

            onData(anything()).inAdapterView(withId(R.id.listFields)).atPosition(IDX_REG_START)
                    .onChildView(withId(android.R.id.text2))
                    .check(matches(not(withText(containsString("Tap")))));
            onData(anything()).inAdapterView(withId(R.id.listFields)).atPosition(IDX_REG_END)
                    .onChildView(withId(android.R.id.text2))
                    .check(matches(not(withText(containsString("Tap")))));
        }
    }

    @Test
    public void testEnforcementOfRequiredFields() {
        try (ActivityScenario<CreateEventsActivity> scenario =
                     ActivityScenario.launch(launchIntentWithOrganizer("org-123"))) {

            onView(withId(R.id.btnSaveEvent)).perform(click());
            verify(storage, never()).createEvent(any());
        }
    }

    @Test
    public void testCreateValidEvent() {
        try (ActivityScenario<CreateEventsActivity> scenario =
                     ActivityScenario.launch(launchIntentWithOrganizer("org-123"))) {

            clickRow(IDX_TITLE);
            fillTextDialog("Spring Lottery");

            clickRow(IDX_CAPACITY);
            fillTextDialog("50");

            clickRow(IDX_REG_START);
            setDateInDatePicker(2026, 3, 10);

            clickRow(IDX_REG_END);
            setDateInDatePicker(2026, 3, 20);

            clickRow(IDX_DRAW_TIME);
            setDateInDatePicker(2026, 3, 25);

            clickRow(IDX_WAITLIST_ON); // Yes
            clickRow(IDX_WAITLIST_CAP);
            fillTextDialog("120");

            onView(withId(R.id.btnSaveEvent)).perform(click());

            ArgumentCaptor<Event> cap = ArgumentCaptor.forClass(Event.class);
            verify(storage, timeout(1500)).createEvent(cap.capture());

            Event e = cap.getValue();
            assertNotNull(e);

            assertEquals("org-123", e.getOrganizerId());
            assertEquals("Spring Lottery", e.getTitle());
            assertEquals(50, e.getCapacity());

            assertEquals(tsAtMidnight(2026, 3, 10).getTime(), e.getRegStart().getTime());
            assertEquals(tsAtMidnight(2026, 3, 20).getTime(), e.getRegEnd().getTime());
            assertEquals(tsAtMidnight(2026, 3, 25).getTime(), e.getDrawTime().getTime());

            assertEquals(120, (int) e.getWaitlistCapacity());
        }
    }

    @Test
    public void testEnforceRegStartBeforeRegEnd() {
        try (ActivityScenario<CreateEventsActivity> scenario =
                     ActivityScenario.launch(launchIntentWithOrganizer("org-123"))) {

            clickRow(IDX_TITLE);
            fillTextDialog("Bad Dates Event");

            clickRow(IDX_CAPACITY);
            fillTextDialog("10");

            clickRow(IDX_REG_START);
            setDateInDatePicker(2026, 3, 20);

            clickRow(IDX_REG_END);
            setDateInDatePicker(2026, 3, 10);

            onView(withId(R.id.btnSaveEvent)).perform(click());

            verify(storage, never()).createEvent(any());
        }
    }
}