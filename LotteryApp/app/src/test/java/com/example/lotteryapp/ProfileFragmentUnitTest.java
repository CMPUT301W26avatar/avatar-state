package com.example.lotteryapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import android.content.Intent;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.example.lotteryapp.activities.UserDetailsActivity;
import com.example.lotteryapp.fragments.ProfileFragment;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.UserIdProvider;
import com.example.lotteryapp.services.storage.AdminStorage;
import com.google.android.gms.tasks.OnSuccessListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;


/**
 * Unit tests for ProfileFragment.
 *
 * These tests verify:
 * 1. Admin UI elements appear when the user is an admin
 * 2. Clicking the User Details menu launches UserDetailsActivity
 *
 * Robolectric for simulating Android UI components.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 34)
public class ProfileFragmentUnitTest {
    private AdminStorage mockAdminStorage;

    // mock the services needed for the unitTest
    @Before
    public void setUp() {
        //  reset previous mocks if they have not been reset
        ServiceLocator.reset();

        mockAdminStorage = mock(AdminStorage.class);

        ServiceLocator.setUserIdProviderForTests(new UserIdProvider() {
            @Override
            public String getUid() {
                return "test-uid";
            }
        });

        ServiceLocator.setAdminStorageForTests(mockAdminStorage);
    }

    // reset mocks after each test
    @After
    public void tearDown() {
        ServiceLocator.reset();
    }

    // test admin elements only appear when the user is an admin
    @Test
    public void adminsCanSeeAdminBrowse() {
        // mock AdminStorage returns true for isAdmin
        doAnswer(invocation -> {
            OnSuccessListener<Boolean> success = invocation.getArgument(1);
            success.onSuccess(true);
            return null;
        }).when(mockAdminStorage).isAdmin(eq("test-uid"), any(), any());

        // create an activity to host the fragment
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class)
                .create()
                .start()
                .resume()
                .get();

        // attach fragment
        ProfileFragment fragment = new ProfileFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks(); // run UI task queue

        View root = fragment.getView();
        assertNotNull(root);

        // get admin UI components
        View adminBrowse = root.findViewById(R.id.item_admin_browse);
        View adminCard = root.findViewById(R.id.card_admin);
        View adminLabel = root.findViewById(R.id.tv_admin_label);

        // verify visibility
        assertNotNull(adminBrowse);
        assertEquals(View.VISIBLE, adminBrowse.getVisibility());
        assertEquals(View.VISIBLE, adminCard.getVisibility());
        assertEquals(View.VISIBLE, adminLabel.getVisibility());
    }

    @Test
    public void clickDetailsLaunchesUserDetailsActivity() {
        // mock AdminStorage returns false for isAdmin
        doAnswer(invocation -> {
            OnSuccessListener<Boolean> success = invocation.getArgument(1);
            success.onSuccess(false);
            return null;
        }).when(mockAdminStorage).isAdmin(eq("test-uid"), any(), any());

        // create an activity to host the fragment
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class)
                .create()
                .start()
                .resume()
                .get();

        // attach fragment
        ProfileFragment fragment = new ProfileFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks(); // run UI task queue

        View root = fragment.getView();
        assertNotNull(root);

        // user clicks the Details menu item
        View detailsItem = root.findViewById(R.id.item_details);
        assertNotNull(detailsItem);

        detailsItem.performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        // verify that UserDetailsActivity was launched
        Intent started = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(UserDetailsActivity.class.getName(), started.getComponent().getClassName());
    }
}