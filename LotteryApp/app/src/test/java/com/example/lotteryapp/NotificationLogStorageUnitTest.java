package com.example.lotteryapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.lotteryapp.models.NotificationLog;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.NotificationLogStorage;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Unit tests for NotificationLogStorage using ServiceLocator
 *
 * Tests include:
 * - logNotification calls onSuccess with a doc ID
 * - getAllNotificationLogs returns correct list of logs
 * - getAllNotificationLogs calls onFailure when storage throws
 * - getAllNotificationLogs returns empty list when there are no logs
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 34)
public class NotificationLogStorageUnitTest {

    private NotificationLogStorage mockStorage;

    /**
     * mock NotificationLogStorage and injects using ServiceLocator before each test
     */
    @Before
    public void setUp() {
        ServiceLocator.reset();
        mockStorage = mock(NotificationLogStorage.class);
        ServiceLocator.setNotificationLogStorageForTests(mockStorage);
    }

    /**
     * Clears ServiceLocator overrides after each test
     */
    @After
    public void tearDown() {
        ServiceLocator.reset();
    }

    /**
     * Verifies logNotification calls onSuccess with doc ID on success
     */
    @Test
    public void logNotificationCallsOnSuccessWithDocId() {
        doAnswer(invocation -> {
            OnSuccessListener<String> success = invocation.getArgument(5);
            success.onSuccess("fake-doc-id");
            return null;
        }).when(mockStorage).logNotification(any(), any(), any(), any(), any(), any(), any());

        AtomicReference<String> result = new AtomicReference<>();

        ServiceLocator.getNotificationLogStorage().logNotification(
                "event-1", "organizer-1", "Title", "Message", "invitation",
                result::set,
                e -> {}
        );

        assertNotNull(result.get());
        assertEquals("fake-doc-id", result.get());
    }

    /**
     * Verifies getAllNotificationLogs returns correct list when logs exist
     */
    @Test
    public void getAllLogsReturnsCorrectList() {
        NotificationLog log1 = new NotificationLog("e1", "o1", "Invited!", "Congrats", "invitation", null);
        NotificationLog log2 = new NotificationLog("e2", "o2", "Result", "Better luck", "lottery_result", null);
        List<NotificationLog> fakeLogs = Arrays.asList(log1, log2);

        doAnswer(invocation -> {
            OnSuccessListener<List<NotificationLog>> success = invocation.getArgument(0);
            success.onSuccess(fakeLogs);
            return null;
        }).when(mockStorage).getAllNotificationLogs(any(), any());

        AtomicReference<List<NotificationLog>> result = new AtomicReference<>();

        ServiceLocator.getNotificationLogStorage().getAllNotificationLogs(
                result::set,
                e -> {}
        );

        assertNotNull(result.get());
        assertEquals(2, result.get().size());
        assertEquals("Invited!", result.get().get(0).getTitle());
    }

    /**
     * Verifies getAllNotificationLogs returns empty list when no logs exist
     */
    @Test
    public void getAllLogsReturnsEmptyListWhenNoneExist() {
        doAnswer(invocation -> {
            OnSuccessListener<List<NotificationLog>> success = invocation.getArgument(0);
            success.onSuccess(Collections.emptyList());
            return null;
        }).when(mockStorage).getAllNotificationLogs(any(), any());

        AtomicReference<List<NotificationLog>> result = new AtomicReference<>();

        ServiceLocator.getNotificationLogStorage().getAllNotificationLogs(
                result::set,
                e -> {}
        );

        assertNotNull(result.get());
        assertEquals(0, result.get().size());
    }

    /**
     * Verifies getAllNotificationLogs calls onFailure when storage throws exception
     */
    @Test
    public void getAllLogsCallsOnFailureOnError() {
        doAnswer(invocation -> {
            OnFailureListener failure = invocation.getArgument(1);
            failure.onFailure(new Exception("Firestore error"));
            return null;
        }).when(mockStorage).getAllNotificationLogs(any(), any());

        AtomicBoolean failureCalled = new AtomicBoolean(false);

        ServiceLocator.getNotificationLogStorage().getAllNotificationLogs(
                logs -> {},
                e -> failureCalled.set(true)
        );

        assertEquals(true, failureCalled.get());
    }
}
