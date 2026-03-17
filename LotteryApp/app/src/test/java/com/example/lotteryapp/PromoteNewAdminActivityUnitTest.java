package com.example.lotteryapp;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import android.widget.ImageButton;

import androidx.fragment.app.Fragment;

import com.example.lotteryapp.activities.PromoteNewAdminActivity;
import com.example.lotteryapp.fragments.AdminListFragment;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.AdminStorage;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.button.MaterialButton;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for PromoteNewAdminActivity.
 *
 * These tests simulate UI interaction and verify behavior using
 * Robolectric + Mockito mocks.
 *
 * Important note:
 * This activity uses an AppCompat AlertDialog for confirmation.
 * Rather than directly interacting with the dialog internals,
 * these tests verify the surrounding behavior in a stable way.
 */
/**
 * Unit tests for PromoteNewAdminActivity.
 *
 * Tests include:
 * - user authentication validation
 * - at-least one admin enforcement
 * - promote launches
 * - profile deletion behavior
 *
 * Robolectric for simulating Android UI components.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 34)
public class PromoteNewAdminActivityUnitTest {

    private AdminStorage mockAdminStorage;

    /**
     * Runs before every test.
     *
     * We reset the ServiceLocator and inject mocked dependencies
     * so the tests do not depend on Firebase or real backend logic.
     */
    @Before
    public void setUp() {
        ServiceLocator.reset();

        // Create mocked AdminStorage used by the activity
        mockAdminStorage = mock(AdminStorage.class);
        ServiceLocator.setAdminStorageForTests(mockAdminStorage);

        // Provide a valid UID by default
        ServiceLocator.setUserIdProviderForTests(() -> "test-admin-uid");
    }

    /**
     * Runs after each test to restore ServiceLocator state
     */
    @After
    public void tearDown() {
        ServiceLocator.reset();
    }

    /**
     * Verify close button finishes the activity
     */
    @Test
    public void closeButtonFinishesActivity() {
        PromoteNewAdminActivity activity = Robolectric.buildActivity(PromoteNewAdminActivity.class)
                .create()
                .start()
                .resume()
                .get();

        ImageButton btnClose = activity.findViewById(R.id.btn_close_promote_admin);
        assertNotNull(btnClose);

        btnClose.performClick();

        assertTrue(activity.isFinishing());
    }

    /**
     * Verify the AdminListFragment is attached on first launch.
     */
    @Test
    public void requestedAdminsFragmentIsAttached() {
        PromoteNewAdminActivity activity = Robolectric.buildActivity(PromoteNewAdminActivity.class)
                .create()
                .start()
                .resume()
                .get();

        Fragment fragment = activity.getSupportFragmentManager()
                .findFragmentById(R.id.admin_settings_container);

        assertNotNull(fragment);
        assertTrue(fragment instanceof AdminListFragment);
    }

    /**
     * Verify the UID cannot be determined, stepping down should be blocked (AdminStorage never called).
     */
    @Test
    public void stepDownUidMissingFailureMsg() {
        // Override UID provider so it returns an invalid UID
        ServiceLocator.setUserIdProviderForTests(() -> " ");

        PromoteNewAdminActivity activity = Robolectric.buildActivity(PromoteNewAdminActivity.class)
                .create()
                .start()
                .resume()
                .get();

        MaterialButton btnStepDown = activity.findViewById(R.id.btn_admin_step_down);
        assertNotNull(btnStepDown);

        btnStepDown.performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertEquals("Could not determine current user",
                ShadowToast.getTextOfLatestToast());

        verify(mockAdminStorage, never()).getCurrentAdmins(any(), any());
        verify(mockAdminStorage, never()).removeCurrentAdmin(anyString(), any(), any());
    }

    /**
     * Verify the last remaining admin should NOT be allowed to step down.
     *
     * Steps:
     * 1. Mock the admin list so it only contains the current admin.
     * 2. Press step-down.
     * 3. Verify current admins were checked.
     * 4. Verify removal is never attempted.
     * 5. Verify the activity remains open.
     */
    @Test
    public void lastAdminCannotStepDown() {
        doAnswer(invocation -> {
            OnSuccessListener<List<String>> success = invocation.getArgument(0);
            success.onSuccess(Collections.singletonList("test-admin-uid"));
            return null;
        }).when(mockAdminStorage).getCurrentAdmins(any(), any());

        PromoteNewAdminActivity activity = Robolectric.buildActivity(PromoteNewAdminActivity.class)
                .create()
                .start()
                .resume()
                .get();

        MaterialButton btnStepDown = activity.findViewById(R.id.btn_admin_step_down);
        assertNotNull(btnStepDown);

        btnStepDown.performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(mockAdminStorage).getCurrentAdmins(any(), any());
        verify(mockAdminStorage, never()).removeCurrentAdmin(anyString(), any(), any());

        // User should remain on this screen
        assertFalse(activity.isFinishing());
    }

    /**
     * Test: When other admins exist, clicking step-down should at least
     * check the admin list first and should not remove the admin until
     * the user confirms through the dialog.
     */
    @Test
    public void stepDownChecksAdminList() {
        doAnswer(invocation -> {
            OnSuccessListener<List<String>> success = invocation.getArgument(0);
            success.onSuccess(Arrays.asList("test-admin-uid", "other-admin"));
            return null;
        }).when(mockAdminStorage).getCurrentAdmins(any(), any());

        PromoteNewAdminActivity activity = Robolectric.buildActivity(PromoteNewAdminActivity.class)
                .create()
                .start()
                .resume()
                .get();

        MaterialButton btnStepDown = activity.findViewById(R.id.btn_admin_step_down);
        assertNotNull(btnStepDown);

        btnStepDown.performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(mockAdminStorage).getCurrentAdmins(any(), any());

        // Removal should not happen until the confirmation dialog is accepted
        verify(mockAdminStorage, never()).removeCurrentAdmin(anyString(), any(), any());

        // The activity should still be open at this point
        assertFalse(activity.isFinishing());
    }

    /**
     * Verify failure while retrieving current admins sends toast pop-up and closes step down attempt
     */
    @Test
    public void getCurrentAdminsFailureMsg() {
        doAnswer(invocation -> {
            OnFailureListener failure = invocation.getArgument(1);
            failure.onFailure(new Exception("lookup failed"));
            return null;
        }).when(mockAdminStorage).getCurrentAdmins(any(), any());

        PromoteNewAdminActivity activity = Robolectric.buildActivity(PromoteNewAdminActivity.class)
                .create()
                .start()
                .resume()
                .get();

        MaterialButton btnStepDown = activity.findViewById(R.id.btn_admin_step_down);
        assertNotNull(btnStepDown);

        btnStepDown.performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertEquals("Failed to check current admins",
                ShadowToast.getTextOfLatestToast());

        verify(mockAdminStorage).getCurrentAdmins(any(), any());
        verify(mockAdminStorage, never()).removeCurrentAdmin(anyString(), any(), any());
    }
}