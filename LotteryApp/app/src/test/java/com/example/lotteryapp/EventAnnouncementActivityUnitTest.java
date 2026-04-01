package com.example.lotteryapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.test.core.app.ApplicationProvider;

import com.example.lotteryapp.activities.EventAnnouncementActivity;
import com.example.lotteryapp.activities.EventDetailsActivity;
import com.example.lotteryapp.models.Entrant;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.models.NotificationLog;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventPoolStorage;
import com.example.lotteryapp.services.storage.EventStorage;
import com.example.lotteryapp.services.storage.NotificationLogStorage;
import com.google.android.gms.tasks.OnSuccessListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for EventAnnouncementActivity.
 *
 * Tests include:
 * - launch validation for missing event id / missing uid
 * - template preview behavior
 * - input validation before sending
 * - empty waitlist behavior
 * - successful announcement delivery behavior
 *
 * Robolectric is used to simulate the Android activity lifecycle and UI.
 * Mockito is used to stub storage-layer callbacks and verify interactions.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 34)
public class EventAnnouncementActivityUnitTest {

    private EventStorage mockEventStorage;
    private EventPoolStorage mockEventPoolStorage;
    private NotificationLogStorage mockNotificationLogStorage;

    // setup mocked dependencies before each test
    @Before
    public void setUp() {
        // reset shared service state before wiring fresh mocks
        ServiceLocator.reset();

        mockEventStorage = mock(EventStorage.class);
        mockEventPoolStorage = mock(EventPoolStorage.class);
        mockNotificationLogStorage = mock(NotificationLogStorage.class);

        // provide a default signed-in user for activity tests
        ServiceLocator.setUserIdProviderForTests(() -> "test-uid");

        // register mocked storage objects in the service locator
        ServiceLocator.setEventStorageForTests(mockEventStorage);
        ServiceLocator.setEventPoolStorageForTests(mockEventPoolStorage);
        ServiceLocator.setNotificationLogStorageForTests(mockNotificationLogStorage);
    }

    // reset service locator after each test run
    @After
    public void tearDown() {
        ServiceLocator.reset();
    }

    // helper to build an intent containing the current event id extra
    private Intent buildIntentWithEventId(String eventId) {
        Intent intent = new Intent(
                ApplicationProvider.getApplicationContext(),
                EventAnnouncementActivity.class
        );
        intent.putExtra(EventDetailsActivity.EXTRA_EVENT_ID, eventId);
        return intent;
    }

    // helper to stub a successful event load with a simple titled event
    private void stubSuccessfulEventLoad(String title) {
        doAnswer(invocation -> {
            OnSuccessListener<Event> onSuccess = invocation.getArgument(1);

            // construct the event using the required constructor arguments
            Event event = new Event("o-123", 9, 10);

            // set the title separately since it is not part of the constructor
            event.setTitle(title);

            onSuccess.onSuccess(event);
            return null;
        }).when(mockEventStorage).getEvent(anyString(), any(), any());
    }

    // verify the activity closes when no event id is provided
    @Test
    public void activityDoesNotLaunchWithoutEventId() {
        Intent intent = new Intent(
                ApplicationProvider.getApplicationContext(),
                EventAnnouncementActivity.class
        );

        EventAnnouncementActivity activity = Robolectric
                .buildActivity(EventAnnouncementActivity.class, intent)
                .create()
                .start()
                .resume()
                .get();

        assertTrue(activity.isFinishing());
        assertEquals("Missing event ID", ShadowToast.getTextOfLatestToast());
    }

    // verify the activity closes when there is no signed-in user
    @Test
    public void activityDoesNotLaunchWithoutUserId() {
        ServiceLocator.setUserIdProviderForTests(() -> null);

        Intent intent = buildIntentWithEventId("event-123");

        EventAnnouncementActivity activity = Robolectric
                .buildActivity(EventAnnouncementActivity.class, intent)
                .create()
                .start()
                .resume()
                .get();

        assertTrue(activity.isFinishing());
        assertEquals("Not signed in", ShadowToast.getTextOfLatestToast());
    }

    // verify that previewing a template populates the title and message fields
    @Test
    public void previewTemplateButtonPopulatesAnnouncementFields() {
        stubSuccessfulEventLoad("Campus Mixer");

        Intent intent = buildIntentWithEventId("event-123");

        EventAnnouncementActivity activity = Robolectric
                .buildActivity(EventAnnouncementActivity.class, intent)
                .create()
                .start()
                .resume()
                .get();

        // choose the first template in the spinner: "Event starts in 1 day"
        Spinner spinner = activity.findViewById(R.id.spinner_announcement_template);
        spinner.setSelection(0);

        // preview the selected template into the input fields
        activity.findViewById(R.id.btn_preview_template).performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        EditText title = activity.findViewById(R.id.et_announcement_title);
        EditText message = activity.findViewById(R.id.et_announcement_message);

        assertEquals("Event starts tomorrow", title.getText().toString());
        assertTrue(message.getText().toString().contains("Campus Mixer"));
        assertTrue(message.getText().toString().contains("1 day"));
    }

    // verify that sending is rejected when the title is blank
    @Test
    public void cannotSendAnnouncementWithBlankTitle() {
        stubSuccessfulEventLoad("Campus Mixer");

        Intent intent = buildIntentWithEventId("event-123");

        EventAnnouncementActivity activity = Robolectric
                .buildActivity(EventAnnouncementActivity.class, intent)
                .create()
                .start()
                .resume()
                .get();

        // leave title blank but provide a valid message
        ((EditText) activity.findViewById(R.id.et_announcement_title)).setText("   ");
        ((EditText) activity.findViewById(R.id.et_announcement_message))
                .setText("Please watch for updates.");

        activity.findViewById(R.id.btn_send_announcement).performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(mockEventPoolStorage, never()).getWaitlistedEntrants(anyString(), any(), any());
        verify(mockNotificationLogStorage, never()).logAnnouncementToUsers(
                anyString(), anyString(), anyList(), anyString(), anyString(), any(), any(), any()
        );

        assertEquals(
                "Title required",
                ((EditText) activity.findViewById(R.id.et_announcement_title)).getError()
        );
    }

    // verify that sending is rejected when the message is blank
    @Test
    public void cannotSendAnnouncementWithBlankMessage() {
        stubSuccessfulEventLoad("Campus Mixer");

        Intent intent = buildIntentWithEventId("event-123");

        EventAnnouncementActivity activity = Robolectric
                .buildActivity(EventAnnouncementActivity.class, intent)
                .create()
                .start()
                .resume()
                .get();

        // provide a valid title but leave the message blank
        ((EditText) activity.findViewById(R.id.et_announcement_title)).setText("Organizer update");
        ((EditText) activity.findViewById(R.id.et_announcement_message)).setText("   ");

        activity.findViewById(R.id.btn_send_announcement).performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(mockEventPoolStorage, never()).getWaitlistedEntrants(anyString(), any(), any());
        verify(mockNotificationLogStorage, never()).logAnnouncementToUsers(
                anyString(), anyString(), anyList(), anyString(), anyString(), any(), any(), any()
        );

        assertEquals(
                "Message required",
                ((EditText) activity.findViewById(R.id.et_announcement_message)).getError()
        );
    }

    // verify that sending with an empty waitlist shows the proper toast and does not write notifications
    @Test
    public void sendAnnouncementDoesNothingWhenWaitlistIsEmpty() {
        stubSuccessfulEventLoad("Campus Mixer");

        doAnswer(invocation -> {
            OnSuccessListener<List<Entrant>> onSuccess = invocation.getArgument(1);
            onSuccess.onSuccess(new ArrayList<>());
            return null;
        }).when(mockEventPoolStorage).getWaitlistedEntrants(anyString(), any(), any());

        Intent intent = buildIntentWithEventId("event-123");

        EventAnnouncementActivity activity = Robolectric
                .buildActivity(EventAnnouncementActivity.class, intent)
                .create()
                .start()
                .resume()
                .get();

        // fill in valid announcement fields
        ((EditText) activity.findViewById(R.id.et_announcement_title)).setText("Organizer update");
        ((EditText) activity.findViewById(R.id.et_announcement_message))
                .setText("Please watch for updates.");

        activity.findViewById(R.id.btn_send_announcement).performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(mockEventPoolStorage).getWaitlistedEntrants(eq("event-123"), any(), any());
        verify(mockNotificationLogStorage, never()).logAnnouncementToUsers(
                anyString(), anyString(), anyList(), anyString(), anyString(), any(), any(), any()
        );

        assertEquals("No waitlisted users to notify", ShadowToast.getTextOfLatestToast());
    }

    // verify that sending a valid announcement writes one batch announcement request for all waitlisted users
    @Test
    public void announcementIsSentToWaitlistedEntrants() {
        stubSuccessfulEventLoad("Campus Mixer");

        doAnswer(invocation -> {
            OnSuccessListener<List<Entrant>> onSuccess = invocation.getArgument(1);

            List<Entrant> entrants = new ArrayList<>();

            // mock waitlisted entrant one
            Entrant entrantOne = mock(Entrant.class);
            doAnswer(a -> "user-1").when(entrantOne).getEntrantId();
            entrants.add(entrantOne);

            // mock waitlisted entrant two
            Entrant entrantTwo = mock(Entrant.class);
            doAnswer(a -> "user-2").when(entrantTwo).getEntrantId();
            entrants.add(entrantTwo);

            onSuccess.onSuccess(entrants);
            return null;
        }).when(mockEventPoolStorage).getWaitlistedEntrants(anyString(), any(), any());

        doAnswer(invocation -> {
            OnSuccessListener<String> onSuccess = invocation.getArgument(6);
            onSuccess.onSuccess("2");
            return null;
        }).when(mockNotificationLogStorage).logAnnouncementToUsers(
                anyString(), anyString(), anyList(), anyString(), anyString(), any(), any(), any()
        );

        Intent intent = buildIntentWithEventId("event-123");

        EventAnnouncementActivity activity = Robolectric
                .buildActivity(EventAnnouncementActivity.class, intent)
                .create()
                .start()
                .resume()
                .get();

        // enter a valid custom announcement
        ((EditText) activity.findViewById(R.id.et_announcement_title)).setText("Organizer update");
        ((EditText) activity.findViewById(R.id.et_announcement_message))
                .setText("Please arrive 15 minutes early.");

        activity.findViewById(R.id.btn_send_announcement).performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // capture the user id list passed to the batch announcement logger
        ArgumentCaptor<List<String>> userIdsCaptor = ArgumentCaptor.forClass(List.class);

        verify(mockNotificationLogStorage).logAnnouncementToUsers(
                eq("event-123"),
                eq("test-uid"),
                userIdsCaptor.capture(),
                eq("Organizer update"),
                eq("Please arrive 15 minutes early."),
                eq(NotificationLog.NotificationType.EVENT_ANNOUNCEMENT),
                any(),
                any()
        );

        List<String> capturedUserIds = userIdsCaptor.getValue();
        assertEquals(2, capturedUserIds.size());
        assertTrue(capturedUserIds.contains("user-1"));
        assertTrue(capturedUserIds.contains("user-2"));

        assertEquals("Announcement sent to 2 waitlisted users", ShadowToast.getTextOfLatestToast());
        assertTrue(activity.isFinishing());
    }

    // verify that tapping cancel immediately closes the activity
    @Test
    public void cancelButtonFinishesActivity() {
        stubSuccessfulEventLoad("Campus Mixer");

        Intent intent = buildIntentWithEventId("event-123");

        EventAnnouncementActivity activity = Robolectric
                .buildActivity(EventAnnouncementActivity.class, intent)
                .create()
                .start()
                .resume()
                .get();

        activity.findViewById(R.id.btn_cancel_announcement).performClick();

        assertTrue(activity.isFinishing());
    }
}