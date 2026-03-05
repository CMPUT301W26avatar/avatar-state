package com.example.lotteryapp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.gms.tasks.OnSuccessListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

@RunWith(AndroidJUnit4.class)
public class TestUserProfileActivity {

    private static final String TEST_UID = "uid-test-123";

    private UserStorage storage;

    @Before
    public void setUp() {
        storage = mock(UserStorage.class);

        // Make save succeed immediately
        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(4)).onSuccess(null);
            return null;
        }).when(storage).updateUserProfile(anyString(), anyString(), anyString(), anyString(), any(), any());

        ServiceLocator.setUserStorageForTests(storage);
        ServiceLocator.setUserIdProviderForTests(() -> TEST_UID);
    }

    @After
    public void tearDown() {
        ServiceLocator.reset();
    }

    @Test
    public void testActivityOpens() {
        try (ActivityScenario<UserProfileActivity> scenario =
                     ActivityScenario.launch(UserProfileActivity.class)) {

            onView(withId(R.id.editName)).check(matches(isDisplayed()));
            onView(withId(R.id.editEmail)).check(matches(isDisplayed()));
            onView(withId(R.id.editPhone)).check(matches(isDisplayed()));
            onView(withId(R.id.btnSaveProfile)).check(matches(isDisplayed()));
            onView(withId(R.id.btnBack)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void testBackButtonExits() {
        try (ActivityScenario<UserProfileActivity> scenario =
                     ActivityScenario.launch(UserProfileActivity.class)) {

            onView(withId(R.id.btnBack)).perform(click());
            scenario.onActivity(a -> assertTrue(a.isFinishing() || a.isDestroyed()));
        }
    }

    @Test
    public void testFieldsTakeInput() {
        try (ActivityScenario<UserProfileActivity> scenario =
                     ActivityScenario.launch(UserProfileActivity.class)) {

            onView(withId(R.id.editName)).perform(replaceText("Connor"), closeSoftKeyboard());
            onView(withId(R.id.editEmail)).perform(replaceText("c@example.com"), closeSoftKeyboard());
            onView(withId(R.id.editPhone)).perform(replaceText("7801234567"), closeSoftKeyboard());

            onView(withId(R.id.editName)).check(matches(withText("Connor")));
            onView(withId(R.id.editEmail)).check(matches(withText("c@example.com")));
            onView(withId(R.id.editPhone)).check(matches(withText("7801234567")));
        }
    }

    @Test
    public void testCorrectFieldsStored() {
        try (ActivityScenario<UserProfileActivity> scenario =
                     ActivityScenario.launch(UserProfileActivity.class)) {

            onView(withId(R.id.editName)).perform(replaceText("Connor"), closeSoftKeyboard());
            onView(withId(R.id.editEmail)).perform(replaceText("c@example.com"), closeSoftKeyboard());
            onView(withId(R.id.editPhone)).perform(replaceText("7801234567"), closeSoftKeyboard());

            onView(withId(R.id.btnSaveProfile)).perform(click());

            ArgumentCaptor<String> uuidCap = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> nameCap = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> emailCap = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> phoneCap = ArgumentCaptor.forClass(String.class);

            verify(storage, timeout(1500)).updateUserProfile(
                    uuidCap.capture(),
                    nameCap.capture(),
                    emailCap.capture(),
                    phoneCap.capture(),
                    any(),
                    any()
            );

            assertEquals(TEST_UID, uuidCap.getValue());
            assertEquals("Connor", nameCap.getValue());
            assertEquals("c@example.com", emailCap.getValue());
            assertEquals("7801234567", phoneCap.getValue());
        }
    }
}