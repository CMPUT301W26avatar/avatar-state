package com.example.lotteryapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import com.example.lotteryapp.services.storage.UserStorage;
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
 * Updated to support UserStorage mock for US 02.06.05.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 34)
public class EnrolledListActivityUnitTest {

    private EventPoolStorage mockEventPoolStorage;
    private UserStorage mockUserStorage;

    @Before
    public void setUp() {
        ServiceLocator.reset();
        mockEventPoolStorage = mock(EventPoolStorage.class);
        mockUserStorage = mock(UserStorage.class);
        ServiceLocator.setEventPoolStorageForTests(mockEventPoolStorage);
        ServiceLocator.setUserStorageForTests(mockUserStorage);
    }

    @After
    public void tearDown() {
        ServiceLocator.reset();
    }

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
        // Export button should be disabled if list is empty
        assertFalse(activity.findViewById(R.id.btn_export_csv).isEnabled());
    }

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
        assertTrue(activity.findViewById(R.id.btn_export_csv).isEnabled());

        RecyclerView rv = activity.findViewById(R.id.rv_enrolled);
        assertEquals(2, rv.getAdapter().getItemCount());
    }
}
