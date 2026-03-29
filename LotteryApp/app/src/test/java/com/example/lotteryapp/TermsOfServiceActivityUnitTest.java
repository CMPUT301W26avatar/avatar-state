package com.example.lotteryapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Intent;

import com.example.lotteryapp.activities.LoginActivity;
import com.example.lotteryapp.activities.TermsOfServiceActivity;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.UserStorage;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

/**
 * Unit tests for TermsOfServiceActivity
 *
 * These tests verify:
 *      The Terms of Service body is displayed on launch
 *      Accept button saves hasAcceptedTOS and finishes the activity on success
 *      Accept button re-enables buttons and shows an error toast on storage failure
 *      Accept button shows an error when the current user id is unavailable
 *      Decline button returns the user to LoginActivity
 *
 * Roboelectric for accessing android components like activities, intents, etc.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 34)
public class TermsOfServiceActivityUnitTest {

    private UserStorage mockUserStorage;

    @Before
    public void setUp() {
        // clean up ServiceLocator before each test
        ServiceLocator.reset();

        // mock firebase storage classes
        mockUserStorage = mock(UserStorage.class);
        ServiceLocator.setUserStorageForTests(mockUserStorage);

        // stable fake user id for tests that require an authenticated user.
        ServiceLocator.setUserIdProviderForTests(() -> "test-user-123");
    }

    @After
    public void tearDown() {
        // clean up ServiceLocator after each test
        ServiceLocator.reset();
    }

    /**
     * Verify the Terms of Service body is shown when the activity launches
     */
    @Test
    public void activityDisplaysTermsOfServiceText() {
        // Launch the activity through Robolectric
        TermsOfServiceActivity activity = Robolectric.buildActivity(TermsOfServiceActivity.class)
                .create()
                .start()
                .resume()
                .get();

        // find the body text view and confirm it exists.
        MaterialTextView tvTermsBody = activity.findViewById(R.id.tv_terms_body);
        assertNotNull(tvTermsBody);

        // confirm the rendered text contains expected TOS headings/content.
        String bodyText = String.valueOf(tvTermsBody.getText());
        assertTrue(bodyText.contains("Terms of Service"));
        assertTrue(bodyText.contains("Community Conduct and Content Policy"));
        assertTrue(bodyText.contains("Gambling and Raffle Notice"));
    }

    /**
     * Verify clicking Accept writes hasAcceptedTOS=true and closes the activity on success
     */
    @Test
    public void acceptButtonSavesAcceptanceAndFinishesActivity() {
        // stub UserStorage to immediately invoke the success callback.
        doAnswer(invocation -> {
            OnSuccessListener<Void> success = invocation.getArgument(2);
            success.onSuccess(null);
            return null;
        }).when(mockUserStorage).setHasAcceptedTOS(eq("test-user-123"), eq(true), any(), any());

        // launch the activity
        TermsOfServiceActivity activity = Robolectric.buildActivity(TermsOfServiceActivity.class)
                .create()
                .start()
                .resume()
                .get();

        // find the Accept button and confirm it exists
        MaterialButton btnAccept = activity.findViewById(R.id.btn_accept_tos);
        assertNotNull(btnAccept);

        // click Accept to trigger the save
        btnAccept.performClick();

        // Verify the storage layer was asked to store TOS acceptance
        verify(mockUserStorage).setHasAcceptedTOS(eq("test-user-123"), eq(true), any(), any());

        // Confirm the activity reports RESULT_OK and is finishing
        assertEquals(TermsOfServiceActivity.RESULT_OK, Shadows.shadowOf(activity).getResultCode());
        assertTrue(activity.isFinishing());
    }

    /**
     * Verify clicking Accept re-enables buttons and shows an error toast when saving fails
     */
    @Test
    public void acceptButtonResetsWhenSaveFails() {
        // stub UserStorage to immediately invoke the failure callback.
        doAnswer(invocation -> {
            OnFailureListener failure = invocation.getArgument(3);
            failure.onFailure(new Exception("save failed"));
            return null;
        }).when(mockUserStorage).setHasAcceptedTOS(eq("test-user-123"), eq(true), any(), any());

        // launch the activity
        TermsOfServiceActivity activity = Robolectric.buildActivity(TermsOfServiceActivity.class)
                .create()
                .start()
                .resume()
                .get();

        // find both action buttons and confirm they exist
        MaterialButton btnAccept = activity.findViewById(R.id.btn_accept_tos);
        MaterialButton btnDecline = activity.findViewById(R.id.btn_decline_tos);
        assertNotNull(btnAccept);
        assertNotNull(btnDecline);

        // click Accept to trigger the failing save
        btnAccept.performClick();

        // the activity should restore both buttons so the user can retry or decline
        assertTrue(btnAccept.isEnabled());
        assertTrue(btnDecline.isEnabled());

        // confirm the correct failure toast was displayed
        assertEquals("Failed to save Terms of Service acceptance.", ShadowToast.getTextOfLatestToast());
    }

    /**
     * Verify clicking Accept with no current user id shows an error and does not call storage
     */
    @Test
    public void acceptButtonShowsErrorWhenUidMissing() {
        // clear the fake uid to simulate a missing authenticated user.
        ServiceLocator.setUserIdProviderForTests(() -> null);

        // launch the activity
        TermsOfServiceActivity activity = Robolectric.buildActivity(TermsOfServiceActivity.class)
                .create()
                .start()
                .resume()
                .get();

        // find the Accept button and confirm it exists
        MaterialButton btnAccept = activity.findViewById(R.id.btn_accept_tos);
        assertNotNull(btnAccept);

        // click Accept without a current uid
        btnAccept.performClick();

        // the activity should remain open because acceptance could not be saved
        assertFalse(activity.isFinishing());

        // confirm the user sees the expected error
        assertEquals("Unable to verify user account.", ShadowToast.getTextOfLatestToast());
    }

    /**
     * Verify clicking Decline sends the user back to LoginActivity and clears navigation history
     */
    @Test
    public void declineButtonLaunchesLoginActivity() {
        // launch the activity
        TermsOfServiceActivity activity = Robolectric.buildActivity(TermsOfServiceActivity.class)
                .create()
                .start()
                .resume()
                .get();

        // find the Decline button and confirm it exists
        MaterialButton btnDecline = activity.findViewById(R.id.btn_decline_tos);
        assertNotNull(btnDecline);

        // click Decline to leave the TOS flow
        btnDecline.performClick();

        // inspect the next started activity intent
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertNotNull(started.getComponent());

        // confirm the user is redirected to LoginActivity
        assertEquals(LoginActivity.class.getName(), started.getComponent().getClassName());

        // confirm the back stack clearing flags were applied
        int flags = started.getFlags();
        assertTrue((flags & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertTrue((flags & Intent.FLAG_ACTIVITY_CLEAR_TASK) != 0);
    }
}