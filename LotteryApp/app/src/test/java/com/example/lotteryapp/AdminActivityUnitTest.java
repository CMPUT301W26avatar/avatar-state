package com.example.lotteryapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.view.View;
import android.widget.ImageButton;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lotteryapp.activities.AdminActivity;
import com.example.lotteryapp.activities.PromoteNewAdminActivity;
import com.example.lotteryapp.fragments.AdminListFragment;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.models.User;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventStorage;
import com.example.lotteryapp.services.storage.UserStorage;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for AdminActivity.
 *
 * These tests verify:
 * 1. Close button finishes the activity
 * 2. Promote button launches PromoteNewAdminActivity
 * 3. Tab layout is properly populated
 *
 * Robolectric allows us to inspect activity state and launched intents.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 34)
public class AdminActivityUnitTest {

    private UserStorage mockUserStorage;
    private EventStorage mockEventStorage;

    @Before
    public void setUp() {
        ServiceLocator.reset();

        mockUserStorage = mock(UserStorage.class);
        mockEventStorage = mock(EventStorage.class);

        ServiceLocator.setUserStorageForTests(mockUserStorage);
        ServiceLocator.setEventStorageForTests(mockEventStorage);
    }

    @After
    public void tearDown() {
        ServiceLocator.reset();
    }


    // verify the close button exits the activity
    @Test
    public void closeButtonFinishesActivity() {
        AdminActivity activity = Robolectric.buildActivity(AdminActivity.class)
                .create()
                .start()
                .resume()
                .get();

        ImageButton btnClose = activity.findViewById(R.id.btn_close_admin);
        assertNotNull(btnClose);

        // click close
        btnClose.performClick();

        // assert Activity finishes
        assertTrue(activity.isFinishing());
    }

    // verify clicking promote opens PromoteNewAdminActivity
    @Test
    public void promoteButtonLaunchesActivity() {
        AdminActivity activity = Robolectric.buildActivity(AdminActivity.class)
                .create()
                .start()
                .resume()
                .get();

        MaterialButton btnPromote = activity.findViewById(R.id.btn_admin_promote);
        assertNotNull(btnPromote);

        // click promote
        btnPromote.performClick();

        // assert PromoteNewAdminActivity launches
        Intent started = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(PromoteNewAdminActivity.class.getName(), started.getComponent().getClassName());
    }

    // verify the activity sets up the 3 expected tabs
    @Test
    public void activityDisplaysExpectedTabs() {
        AdminActivity activity = Robolectric.buildActivity(AdminActivity.class)
                .create()
                .start()
                .resume()
                .get();

        TabLayout tabLayout = activity.findViewById(R.id.tab_layout);
        assertNotNull(tabLayout);

        // assert the tab layout is properly populated with Events, Profiles and Images
        assertEquals(3, tabLayout.getTabCount());
        assertEquals("Events", String.valueOf(tabLayout.getTabAt(0).getText()));
        assertEquals("Profiles", String.valueOf(tabLayout.getTabAt(1).getText()));
        assertEquals("Images", String.valueOf(tabLayout.getTabAt(2).getText()));
    }

    // verify that the profile tab populates the recycler view with user profiles
    @Test
    public void profilesTabIsPopulated() {
        User user1 = mock(User.class);
        User user2 = mock(User.class);
        List<User> users = Arrays.asList(user1, user2);

        doAnswer(invocation -> {
            OnSuccessListener<List<User>> success = invocation.getArgument(0);
            success.onSuccess(users);
            return null;
        }).when(mockUserStorage).getAllUsers(any(), any());

        AdminListFragment fragment = attachFragment(AdminListFragment.TYPE_PROFILES);

        // assert our two test users are populated in the recycler view
        RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recycler_view);
        assertNotNull(recyclerView);
        assertNotNull(recyclerView.getAdapter());
        assertEquals(2, recyclerView.getAdapter().getItemCount());

        // assert UI elements from previous screen are gone
        View progressBar = fragment.requireView().findViewById(R.id.progress_bar);
        View emptyView = fragment.requireView().findViewById(R.id.tv_empty);

        assertEquals(View.GONE, progressBar.getVisibility());
        assertEquals(View.GONE, emptyView.getVisibility());
    }

    // verify that the events tab populates the recycler view with test events
    @Test
    public void eventsTabIsPopulated() {
        Event event1 = mock(Event.class);
        Event event2 = mock(Event.class);
        List<Event> events = Arrays.asList(event1, event2);

        doAnswer(invocation -> {
            OnSuccessListener<List<Event>> success = invocation.getArgument(0);
            success.onSuccess(events);
            return null;
        }).when(mockEventStorage).getAllEvents(any(), any());

        AdminListFragment fragment = attachFragment(AdminListFragment.TYPE_EVENTS);

        // assert our two test events are populated in the recycler view
        RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recycler_view);
        assertNotNull(recyclerView);
        assertNotNull(recyclerView.getAdapter());
        assertEquals(2, recyclerView.getAdapter().getItemCount());

        // assert UI elements from previous screen are gone
        View progressBar = fragment.requireView().findViewById(R.id.progress_bar);
        View emptyView = fragment.requireView().findViewById(R.id.tv_empty);

        assertEquals(View.GONE, progressBar.getVisibility());
        assertEquals(View.GONE, emptyView.getVisibility());
    }

    // helper: mock an activity the fragment belongs to and attach the test fragment to it
    private AdminListFragment attachFragment(String type) {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class)
                .create()
                .start()
                .resume()
                .get();

        AdminListFragment fragment = AdminListFragment.newInstance(type);
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertNotNull(fragment.getView());
        return fragment;
    }
}
