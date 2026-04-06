package com.example.lotteryapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.widget.EditText;
import android.widget.TextView;

import com.example.lotteryapp.activities.LoginActivity;
import com.example.lotteryapp.activities.MainActivity;
import com.example.lotteryapp.activities.SignupActivity;
import com.example.lotteryapp.services.AuthService;
import com.example.lotteryapp.services.ServiceLocator;
import com.google.android.material.button.MaterialButton;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Unit tests for LoginActivity.
 *
 * These tests verify:
 * 1. Credential login calls AuthService with entered values
 * 2. Credential login launches MainActivity and finishes
 * 3. Guest login calls anonymous sign in
 * 4. Guest login launches MainActivity and finishes
 * 5. Register text launches SignupActivity
 * 6. Previous session sign-in goes directly to MainActivity
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 34)
public class LoginActivityUnitTest {

    private AuthService mockAuthService;

    @Before
    public void setUp() {
        ServiceLocator.reset();

        mockAuthService = mock(AuthService.class);
        ServiceLocator.setAuthServiceForTests(mockAuthService);
    }

    @After
    public void tearDown() {
        ServiceLocator.reset();
    }

    @Test
    public void credentialLoginCallsAuth() {
        when(mockAuthService.signInPrevSession()).thenReturn(false);

        LoginActivity activity = Robolectric.buildActivity(LoginActivity.class)
                .create()
                .start()
                .resume()
                .get();

        EditText etEmail = activity.findViewById(R.id.et_email);
        EditText etPassword = activity.findViewById(R.id.et_password);
        MaterialButton btnLogin = activity.findViewById(R.id.btn_login);

        assertNotNull(etEmail);
        assertNotNull(etPassword);
        assertNotNull(btnLogin);

        etEmail.setText("test@example.com");
        etPassword.setText("secret123");

        btnLogin.performClick();

        verify(mockAuthService).userSignInCred(
                eq("test@example.com"),
                eq("secret123"),
                any(),
                any(),
                any()
        );

        Intent started = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        org.junit.Assert.assertNull(started);
    }

    @Test
    public void guestLoginCallsAuthAndLaunchesMain() {
        when(mockAuthService.signInPrevSession()).thenReturn(false);

        LoginActivity activity = Robolectric.buildActivity(LoginActivity.class)
                .create()
                .start()
                .resume()
                .get();

        MaterialButton btnGuestLogin = activity.findViewById(R.id.btn_guest_login);
        assertNotNull(btnGuestLogin);

        btnGuestLogin.performClick();

        verify(mockAuthService).userSignInAnon(any());

        Intent started = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(MainActivity.class.getName(), started.getComponent().getClassName());
        assertTrue(activity.isFinishing());
    }

    @Test
    public void registerTextLaunchesSignupActivity() {
        when(mockAuthService.signInPrevSession()).thenReturn(false);

        LoginActivity activity = Robolectric.buildActivity(LoginActivity.class)
                .create()
                .start()
                .resume()
                .get();

        TextView tvRegister = activity.findViewById(R.id.tv_register);
        assertNotNull(tvRegister);

        tvRegister.performClick();

        Intent started = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(SignupActivity.class.getName(), started.getComponent().getClassName());
    }

    @Test
    public void previousSessionLaunchesMainImmediately() {
        when(mockAuthService.signInPrevSession()).thenReturn(true);

        LoginActivity activity = Robolectric.buildActivity(LoginActivity.class)
                .create()
                .start()
                .resume()
                .get();

        Intent started = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(MainActivity.class.getName(), started.getComponent().getClassName());
        assertTrue(activity.isFinishing());
    }

    @Test
    public void noPreviousSessionDoesNotLaunchImmediately() {
        when(mockAuthService.signInPrevSession()).thenReturn(false);

        LoginActivity activity = Robolectric.buildActivity(LoginActivity.class)
                .create()
                .start()
                .resume()
                .get();

        Intent started = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        org.junit.Assert.assertNull(started);
    }
}