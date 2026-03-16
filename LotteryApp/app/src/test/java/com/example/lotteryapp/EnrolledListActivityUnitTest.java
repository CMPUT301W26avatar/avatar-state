package com.example.lotteryapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import android.content.Intent;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.example.lotteryapp.activities.EnrolledListActivity;
import com.example.lotteryapp.models.Entrant;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventPoolStorage;
import com.google.android.gms.tasks.OnSuccessListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for EnrolledListActivity.
 *
 * Tests include:
 * - activity finishes if no event ID is provided
 * - empty view shown when no entrants are enrolled
 * - recycler view shown when entrants are enrolled
 *
 * Robolectric for simulating Android UI components.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 34)
public class EnrolledListActivityUnitTest {

    private EventPoolStorage mockEventPoolStorage;

    @Before
    public void setUp() {
        ServiceLocator.reset();
        mockEventPoolStorage = mock(EventPoolStorage.class);
        ServiceLocator.setEventPoolStorageForTests(mockEventPoolStorage);
    }

    @After
    public void tearDown() {
        ServiceLocator.reset();
    }

    // activity should finish immediately if no eventId is passed
    @Test
    public void activityFinishesWithoutEventId() {
        EnrolledListActivity activity = Robolectric.buildActivity(EnrolledListActivity.class)
                .create()
                .start()
                .resume()
                .get();

        assertTrue(activity.isFinishing());
        assertEquals("Missing eventID", ShadowToast.getTextOfLatestToast());
    }

    // empty view should be visible when enrolled list is empty
    @Test
    public void emptyViewShownWhenNoEntrantsEnrolled() {
        doAnswer(invocation -> {
            OnSuccessListener<List<Entrant>> success = invocation.getArgument(1);
            success.onSuccess(new ArrayList<>());
            return null;
        }).when(mockEventPoolStorage).getEnrolledEntrants(eq("event-123"), any(), any());

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), EnrolledListActivity.class);
        intent.putExtra(EnrolledListActivity.EXTRA_EVENT_ID, "event-123");

        EnrolledListActivity activity = Robolectric.buildActivity(EnrolledListActivity.class, intent)
                .create()
                .start()
                .resume()
                .get();

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertEquals(View.VISIBLE, activity.findViewById(R.id.tv_empty).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.rv_enrolled).getVisibility());
    }

    // recycler view should be visible and populated when entrants are enrolled
    @Test
    public void recyclerViewShownWhenEntrantsEnrolled() {
        List<Entrant> fakeEntrants = new ArrayList<>();
        fakeEntrants.add(new Entrant("entrant-1", "event-123", Entrant.EntrantStatus.ENROLLED));
        fakeEntrants.add(new Entrant("entrant-2", "event-123", Entrant.EntrantStatus.ENROLLED));

        doAnswer(invocation -> {
            OnSuccessListener<List<Entrant>> success = invocation.getArgument(1);
            success.onSuccess(fakeEntrants);
            return null;
        }).when(mockEventPoolStorage).getEnrolledEntrants(eq("event-123"), any(), any());

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), EnrolledListActivity.class);
        intent.putExtra(EnrolledListActivity.EXTRA_EVENT_ID, "event-123");

        EnrolledListActivity activity = Robolectric.buildActivity(EnrolledListActivity.class, intent)
                .create()
                .start()
                .resume()
                .get();

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertEquals(View.GONE, activity.findViewById(R.id.tv_empty).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.rv_enrolled).getVisibility());

        RecyclerView rv = activity.findViewById(R.id.rv_enrolled);
        assertEquals(2, rv.getAdapter().getItemCount());
    }
}