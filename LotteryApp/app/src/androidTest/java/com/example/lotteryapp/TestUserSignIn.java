package com.example.lotteryapp;

import static org.junit.Assert.assertNotNull;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.firebase.auth.FirebaseAuth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class TestUserSignIn {

    private String uuid;

    @Before
    public void ensureSignedIn() throws Exception {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            uuid = auth.getCurrentUser().getUid();
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
        uuid = auth.getCurrentUser().getUid();
    }

    @Test
    public void uuidIsNotNull() {
        assertNotNull("uuid should be set by @Before sign-in", uuid);
    }
}