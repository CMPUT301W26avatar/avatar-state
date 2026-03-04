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
import com.google.firebase.auth.FirebaseAuth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class TestUserProfileActivity {

    private String uid;

    @Before
    public void ensureSignedIn() throws Exception {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            uid = auth.getCurrentUser().getUid();
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        auth.signInAnonymously().addOnCompleteListener(task -> latch.countDown());

        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out waiting for anonymous sign-in");
        }
        if (auth.getCurrentUser() == null) {
            throw new AssertionError("Anonymous sign-in failed (currentUser still null)");
        }
        uid = auth.getCurrentUser().getUid();
    }

    // helper function for setting private fields inside User
    private static void setPrivateField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field '" + fieldName + "'", e);
        }
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

            scenario.onActivity(activity ->
                    assertTrue(activity.isFinishing() || activity.isDestroyed())
            );
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
        UserStorage storage = mock(UserStorage.class);
        doAnswer(inv -> {
            ((OnSuccessListener<Void>) inv.getArgument(4)).onSuccess(null);
            return null;
        })
                .when(storage).updateUserProfile(anyString(), anyString(), anyString(), anyString(), any(), any());

        try (ActivityScenario<UserProfileActivity> scenario =
                     ActivityScenario.launch(UserProfileActivity.class)) {

            // Inject mock storage + stable uid into the Activity (no production changes)
            scenario.onActivity(activity -> {
                setPrivateField(activity, "userStorage", storage);
                setPrivateField(activity, "uuid", uid);
            });

            // Enter data
            onView(withId(R.id.editName)).perform(replaceText("Connor"), closeSoftKeyboard());
            onView(withId(R.id.editEmail)).perform(replaceText("c@example.com"), closeSoftKeyboard());
            onView(withId(R.id.editPhone)).perform(replaceText("7801234567"), closeSoftKeyboard());

            // Save
            onView(withId(R.id.btnSaveProfile)).perform(click());

            // Verify update called with correct args (this is the "firebase updated" proof)
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

            assertEquals(uid, uuidCap.getValue());
            assertEquals("Connor", nameCap.getValue());
            assertEquals("c@example.com", emailCap.getValue());
            assertEquals("7801234567", phoneCap.getValue());
        }
    }
}