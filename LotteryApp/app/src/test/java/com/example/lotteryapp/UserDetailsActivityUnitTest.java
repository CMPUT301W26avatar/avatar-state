package com.example.lotteryapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.widget.EditText;

import androidx.test.core.app.ApplicationProvider;

import com.example.lotteryapp.activities.LoginActivity;
import com.example.lotteryapp.activities.UserDetailsActivity;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.UserIdProvider;
import com.example.lotteryapp.services.storage.AdminStorage;
import com.example.lotteryapp.services.storage.UserStorage;
import com.google.android.gms.auth.api.signin.internal.Storage;
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

/**
 * Unit tests for UserDetailsActivity.
 *
 * Tests include:
 * - user authentication validation
 * - save profile validation
 * - profile update logic
 * - profile deletion behavior
 *
 * Robolectric for simulating Android UI components.
 */

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 34)
public class UserDetailsActivityUnitTest {
    private UserStorage mockUserStorage;

    // mock the services needed for the unitTest
    @Before
    public void setUp() {
        //  reset previous mocks if they have not been reset
        ServiceLocator.reset();

        mockUserStorage = mock(UserStorage.class);

        ServiceLocator.setUserIdProviderForTests(new UserIdProvider() {
            @Override
            public String getUid() {
                return "test-uid";
            }
        });

        ServiceLocator.setUserStorageForTests(mockUserStorage);
    }

    // reset mocks after each test
    @After
    public void tearDown() {
        ServiceLocator.reset();
    }


    // verify the activity closes if no user Id is available
    @Test
    public void activityDoesNotLaunchWithoutUid() {
        ServiceLocator.setUserIdProviderForTests(new UserIdProvider() {
            @Override
            public String getUid() {
                return null;
            }
        });

        // mock activity to test
        UserDetailsActivity activity = Robolectric.buildActivity(UserDetailsActivity.class)
                .create()
                .start()
                .resume()
                .get();

        assertTrue(activity.isFinishing());
        assertEquals("User not signed in", ShadowToast.getTextOfLatestToast());
    }

    // verify that saving a profile with a blank name is rejected
    @Test
    public void cannotUpdateProfileWithBlankName() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), UserDetailsActivity.class);
        intent.putExtra(UserDetailsActivity.EXTRA_USER_ID, "1234");

        // mock activity to test
        UserDetailsActivity activity = Robolectric.buildActivity(UserDetailsActivity.class, intent)
                .create()
                .start()
                .resume()
                .get();

        // set values in activity
        ((EditText) activity.findViewById(R.id.et_name)).setText("   ");
        ((EditText) activity.findViewById(R.id.et_email)).setText("blambda@rambda.com");
        ((EditText) activity.findViewById(R.id.et_phone)).setText("7800000000");
        ((EditText) activity.findViewById(R.id.et_location)).setText("Schmegmonton");

        activity.findViewById(R.id.btn_save_profile).performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(mockUserStorage, never()).updateUserProfile(
                anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()
        );

        // assert update fails when there is not a name
        assertEquals("Name is required", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void saveProfileUpdatesWithCorrectValues() {
        doAnswer(invocation -> {
            OnSuccessListener<Void> success = invocation.getArgument(5);
            success.onSuccess(null);
            return null;
        }).when(mockUserStorage).updateUserProfile(
                anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()
        );

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), UserDetailsActivity.class);
        intent.putExtra(UserDetailsActivity.EXTRA_USER_ID, "1234");
        intent.putExtra(UserDetailsActivity.EXTRA_ADMIN_MODE, false);

        // mock activity to test
        UserDetailsActivity activity = Robolectric.buildActivity(UserDetailsActivity.class, intent)
                .create()
                .start()
                .resume()
                .get();

        // set values in activity
        ((EditText) activity.findViewById(R.id.et_name)).setText("  Wambda  ");
        ((EditText) activity.findViewById(R.id.et_email)).setText("  blambda@wambda.com ");
        ((EditText) activity.findViewById(R.id.et_phone)).setText(" 3481234567 ");
        ((EditText) activity.findViewById(R.id.et_location)).setText(" Kitsilano ");

        // user clicks save profile
        activity.findViewById(R.id.btn_save_profile).performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks(); // run UI task queue

        // assert inserted values are as to be expected
        verify(mockUserStorage).updateUserProfile(
                eq("1234"),
                eq("Wambda"),
                eq("blambda@wambda.com"),
                eq("3481234567"),
                eq("Kitsilano"),
                any(),
                any()
        );

        // assert activity ends after successful update
        assertTrue(activity.isFinishing());
        assertEquals("Profile updated!", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void successfulProfileDeleteLaunchesLoginActivity() {
        doAnswer(invocation -> {
            OnSuccessListener<Void> success = invocation.getArgument(1);
            success.onSuccess(null);
            return null;
        }).when(mockUserStorage).cascadeUserDelete(anyString(), any(), any());

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), UserDetailsActivity.class);
        intent.putExtra(UserDetailsActivity.EXTRA_USER_ID, "1234");

        // mock activity to test
        UserDetailsActivity activity = Robolectric.buildActivity(UserDetailsActivity.class, intent)
                .create()
                .start()
                .resume()
                .get();

        // user clicks delete profile
        activity.findViewById(R.id.btn_userdel_profile).performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks(); // run UI task queue

        // cascadeDelete inside of mocked storage
        verify(mockUserStorage).cascadeUserDelete(eq("test-uid"), any(), any());

        // assert LoginActivity starts, and UserProfileActivity finishes on profile delete
        Intent started = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(LoginActivity.class.getName(), started.getComponent().getClassName());
        assertTrue(activity.isFinishing());
        assertEquals("Profile deleted", ShadowToast.getTextOfLatestToast());
    }
}