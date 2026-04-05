package com.example.lotteryapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;

import com.example.lotteryapp.models.Entrant;
import com.example.lotteryapp.models.NotificationLog;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventPoolStorage;
import com.example.lotteryapp.models.UserEventHistory;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.UserStorage;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.example.lotteryapp.services.storage.NotificationLogStorage;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit tests for drawWinners functionality in EventPoolStorage.
 *
 * These tests verify:
 * 1. Correct number of winners are selected based on event capacity
 * 2. Waitlisted entrants are properly transitioned to invited or not-invited
 * 3. Selected entrants are removed from the waitlist collection
 * 4. Lottery selection behaves randomly (not biased toward ordering)
 * 5. Multiple draw rounds behave correctly (e.g., re-sampling scenarios)
 *
 * Robolectric + Mockito are used to simulate Firestore interactions and async behavior.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34)
public class DrawWinnersUnitTest {

    private FirebaseFirestore db;
    private EventPoolStorage mockEventPoolStorage;
    private UserStorage mockUserStorage;

    private NotificationLogStorage mockNotificationLogStorage;

    private CollectionReference eventsCollection;
    private DocumentReference eventDoc;
    private CollectionReference waitlistedCollection;
    private CollectionReference invitedCollection;
    private Query waitlistedQuery;
    private QuerySnapshot querySnapshot;
    private WriteBatch batch;

    // maps invited document refs back to entrant IDs so tests can recover winners
    private Map<DocumentReference, String> invitedRefToEntrantId;
    private DocumentSnapshot eventSnapshot;

    @Before
    public void setUp() {
        initializeFixture();
    }

    /**
     * Builds a fresh mocked Firestore fixture for each test/run.
     *
     * This allows each test to simulate the EventPoolStorage draw flow
     * without relying on a real database.
     */
    private void initializeFixture() {
        ServiceLocator.reset();

        db = mock(FirebaseFirestore.class);

        mockNotificationLogStorage = mock(NotificationLogStorage.class);
        ServiceLocator.setNotificationLogStorageForTests(mockNotificationLogStorage);

        mockUserStorage = mock(UserStorage.class);
        ServiceLocator.setUserStorageForTests(mockUserStorage);

        mockEventPoolStorage = new EventPoolStorage(db);

        doAnswer(invocation -> {
            OnSuccessListener<Void> success = invocation.getArgument(4);
            success.onSuccess(null);
            return null;
        }).when(mockUserStorage).addUserEventHistoryEntry(
                anyString(),
                anyString(),
                any(UserEventHistory.HistoryStatus.class),
                any(),
                any(),
                any()
        );

        // mock Firestore collections and references
        eventsCollection = mock(CollectionReference.class);
        eventDoc = mock(DocumentReference.class);
        waitlistedCollection = mock(CollectionReference.class);
        invitedCollection = mock(CollectionReference.class);
        waitlistedQuery = mock(Query.class);
        querySnapshot = mock(QuerySnapshot.class);
        batch = mock(WriteBatch.class);
        eventSnapshot = mock(DocumentSnapshot.class);

        invitedRefToEntrantId = new HashMap<>();

        when(db.collection("events")).thenReturn(eventsCollection);
        when(eventsCollection.document(anyString())).thenReturn(eventDoc);

        when(eventDoc.collection("waitlisted")).thenReturn(waitlistedCollection);
        when(eventDoc.collection("invited")).thenReturn(invitedCollection);

        when(waitlistedCollection.whereEqualTo(
                eq("status"),
                eq(Entrant.EntrantStatus.WAITLISTED.name())
        )).thenReturn(waitlistedQuery);

        when(waitlistedCollection.whereIn(
                eq("status"),
                eq(Arrays.asList(
                        Entrant.EntrantStatus.WAITLISTED.name(),
                        Entrant.EntrantStatus.NOT_INVITED.name()
                ))
        )).thenReturn(waitlistedQuery);

        when(eventDoc.get()).thenReturn(Tasks.forResult(eventSnapshot));
        when(waitlistedQuery.get()).thenReturn(Tasks.forResult(querySnapshot));

        when(eventSnapshot.exists()).thenReturn(true);
        when(eventSnapshot.getLong("invitationCount")).thenReturn(0L);
        when(eventSnapshot.getLong("waitlistCount")).thenReturn(1000L);
        when(eventSnapshot.getLong("enrolledCount")).thenReturn(0L);
        when(eventSnapshot.getString("title")).thenReturn("Sample Event");
        when(eventSnapshot.getString("organizerId")).thenReturn("organizer-1");
        when(eventSnapshot.getBoolean("hasDrawnLottery")).thenReturn(false);
        when(eventSnapshot.getLong("waitlistCapacity")).thenReturn(1000L);
        when(eventSnapshot.getLong("eventCapacity")).thenReturn(1000L);

        when(db.batch()).thenReturn(batch);
        when(batch.commit()).thenReturn(Tasks.forResult(null));
    }

    @org.junit.After
    public void tearDown() {
        ServiceLocator.reset();
    }

    // verify that each draw is capped strictly at eventCapacity
    @Test
    public void onlySelectUpToEventCapacityPerDraw() {
        String eventId = "event-2";
        int waitlistSize = 1000;
        int eventCapacity = 2;

        // After drawing 2 winners, waitlistCount becomes 998
        // With waitlistCapacity also 998 and hasDrawnLottery false,
        //  resolveEventStatusAfterChange() should produce REG_FULL
        when(eventSnapshot.getLong("waitlistCount")).thenReturn((long) waitlistSize);
        when(eventSnapshot.getLong("waitlistCapacity")).thenReturn((long) (waitlistSize - eventCapacity));
        when(eventSnapshot.getBoolean("hasDrawnLottery")).thenReturn(false);

        // create a larger pool than can be selected in a single draw
        List<QueryDocumentSnapshot> docs = buildWaitlistedDocs(waitlistSize);
        stubQueryResult(docs);

        AtomicInteger successCount = new AtomicInteger(-1);
        AtomicInteger failureCount = new AtomicInteger(0);

        // run draw
        mockEventPoolStorage.drawWinners(
                eventId,
                eventCapacity,
                successCount::set,
                e -> failureCount.incrementAndGet()
        );

        flushAsyncCallbacks();

        // only eventCapacity users should be invited
        assertEquals(0, failureCount.get());
        assertEquals(eventCapacity, successCount.get());

        // invited users are added to invited and removed from waitlisted
        verify(batch, times(eventCapacity)).set(any(DocumentReference.class), any());
        verify(batch, times(eventCapacity)).delete(any(DocumentReference.class));

        // everyone else should be marked as not invited
        int losers = waitlistSize - eventCapacity;
        verify(batch, times(losers)).update(
                any(DocumentReference.class),
                eq("status"),
                eq(Entrant.EntrantStatus.NOT_INVITED.name())
        );
        verify(batch, times(losers)).update(
                any(DocumentReference.class),
                eq("updatedAt"),
                any(FieldValue.class)
        );

        verify(batch).update(eq(eventDoc), argThat(map -> {
            Object invitationCount = map.get("invitationCount");
            Object waitlistCount = map.get("waitlistCount");
            Object status = map.get("status");

            return invitationCount instanceof Number
                    && ((Number) invitationCount).intValue() == eventCapacity
                    && waitlistCount instanceof Number
                    && ((Number) waitlistCount).intValue() == (waitlistSize - eventCapacity)
                    && "REG_FULL".equals(status);
        }));

        verify(batch, times(1)).commit();
    }

    // verify that if the waitlist is smaller than event capacity, everyone is invited
    @Test
    public void invitesAllWaitlistedWhen_waitlistCapLessThanEventCap() {
        String eventId = "event-3";
        int waitlistSize = 4;
        int eventCapacity = 10;

        // waitlist size is smaller than the number of available spots
        List<QueryDocumentSnapshot> docs = buildWaitlistedDocs(waitlistSize);
        stubQueryResult(docs);

        AtomicInteger successCount = new AtomicInteger(-1);
        AtomicInteger failureCount = new AtomicInteger(0);

        // run draw
        mockEventPoolStorage.drawWinners(
                eventId,
                eventCapacity,
                successCount::set,
                e -> failureCount.incrementAndGet()
        );

        flushAsyncCallbacks();

        // all waitlisted entrants should be selected
        assertEquals(0, failureCount.get());
        assertEquals(waitlistSize, successCount.get());

        // all users should be invited and removed from waitlisted
        verify(batch, times(waitlistSize)).set(any(DocumentReference.class), any());
        verify(batch, times(waitlistSize)).delete(any(DocumentReference.class));

        // no one should be marked as not invited because there are no leftovers
        verify(batch, times(0)).update(
                any(DocumentReference.class),
                eq("status"),
                eq(Entrant.EntrantStatus.NOT_INVITED.name())
        );
        verify(batch, times(0)).update(
                any(DocumentReference.class),
                eq("updatedAt"),
                any(FieldValue.class)
        );

        verify(batch).update(eq(eventDoc), argThat(map -> {
            return map.containsKey("invitationCount")
                    && map.containsKey("waitlistCount")
                    && map.containsKey("status");
        }));
        verify(batch, times(1)).commit();
    }

    // verify that an empty waitlist produces no writes and reports zero winners
    @Test
    public void lotteryForEmptyWaitlistDoesNothing() {
        String eventId = "event-4";

        // return an empty query result
        stubQueryResult(new ArrayList<>());

        AtomicInteger successCount = new AtomicInteger(-1);
        AtomicInteger failureCount = new AtomicInteger(0);

        // run draw against an empty waitlist
        mockEventPoolStorage.drawWinners(
                eventId,
                5,
                successCount::set,
                e -> failureCount.incrementAndGet()
        );

        flushAsyncCallbacks();

        // no errors and zero selected winners
        assertEquals(0, failureCount.get());
        assertEquals(0, successCount.get());

        // no batch writes should occur when no entrants exist
        verify(batch, times(0)).set(any(DocumentReference.class), any());
        verify(batch, times(0)).delete(any(DocumentReference.class));
        verify(batch, times(0)).update(any(DocumentReference.class), anyString(), any());
        verify(batch, times(0)).commit();
    }

    // verify randomness: multiple runs should produce different winner sets
    @Test
    public void sequentialDrawsProduceDistinctSelections() {
        int waitlistSize = 30;
        int eventCapacity = 5;
        int runs = 25;

        // store the unique winner sets observed across many independent draws
        Set<Set<String>> distinctWinnerSets = new HashSet<>();

        for (int i = 0; i < runs; i++) {
            // rebuild the fixture each run so previous verifications/state do not leak forward
            initializeFixture();
            stubQueryResult(buildWaitlistedDocs(waitlistSize));

            AtomicInteger successCount = new AtomicInteger(-1);
            AtomicInteger failureCount = new AtomicInteger(0);

            // run the draw for this round
            mockEventPoolStorage.drawWinners(
                    "event-random-" + i,
                    eventCapacity,
                    successCount::set,
                    e -> failureCount.incrementAndGet()
            );

            flushAsyncCallbacks();

            // each draw should succeed and return exactly eventCapacity winners
            assertEquals(0, failureCount.get());
            assertEquals(eventCapacity, successCount.get());

            // recover the actual selected entrant IDs from captured invited writes
            Set<String> winners = captureWinnerIds();
            assertEquals(eventCapacity, winners.size());

            // add the observed winner set to see whether different runs vary
            distinctWinnerSets.add(winners);
        }

        // repeated randomized draws should not always return the exact same winner set
        assertTrue(
                "Expected multiple different winner sets across repeated runs",
                distinctWinnerSets.size() > 1
        );
    }

    // verify randomness: multiple runs should not pick only from the front of the collection
    @Test
    public void sequentialDrawsDoNotTargetFrontOfCollection() {
        int waitlistSize = 100;
        int eventCapacity = 5;
        int runs = 30;

        // if randomness is working, at least one run should include someone beyond the first 5 entrants
        boolean selectedSomeoneOutsideFirstFive = false;

        for (int i = 0; i < runs; i++) {
            initializeFixture();
            stubQueryResult(buildWaitlistedDocs(waitlistSize));

            AtomicInteger successCount = new AtomicInteger(-1);
            AtomicInteger failureCount = new AtomicInteger(0);

            // run draw for this iteration
            mockEventPoolStorage.drawWinners(
                    "event-outside-front-" + i,
                    eventCapacity,
                    successCount::set,
                    e -> failureCount.incrementAndGet()
            );

            flushAsyncCallbacks();

            // ensure the draw completed successfully
            assertEquals(0, failureCount.get());
            assertEquals(eventCapacity, successCount.get());

            // inspect selected winners and see whether any were chosen from outside the first eventCapacity
            Set<String> winners = captureWinnerIds();
            for (String winner : winners) {
                int index = parseEntrantIndex(winner);
                if (index >= eventCapacity) {
                    selectedSomeoneOutsideFirstFive = true;
                    break;
                }
            }

            // once we have evidence of selection outside the front slice, we can stop early
            if (selectedSomeoneOutsideFirstFive) {
                break;
            }
        }

        assertTrue(
                "Expected at least one run to select an entrant outside the first eventCapacity positions",
                selectedSomeoneOutsideFirstFive
        );
    }

    // verify randomness: repeated draws should reach entrants from front, middle, and back regions
    @Test
    public void sequentialDrawsHaveSelectionsAcrossTheWholeCollection() {
        int waitlistSize = 1000;
        int eventCapacity = 10;
        int runs = 40;

        // track whether selections ever hit each major region of the waitlist
        boolean sawFrontRegionWinner = false;   // 0-99
        boolean sawMiddleRegionWinner = false;  // 450-549
        boolean sawBackRegionWinner = false;    // 900-999

        for (int i = 0; i < runs; i++) {
            initializeFixture();
            stubQueryResult(buildWaitlistedDocs(waitlistSize));

            AtomicInteger successCount = new AtomicInteger(-1);
            AtomicInteger failureCount = new AtomicInteger(0);

            // run another randomized draw
            mockEventPoolStorage.drawWinners(
                    "event-regions-" + i,
                    eventCapacity,
                    successCount::set,
                    e -> failureCount.incrementAndGet()
            );

            flushAsyncCallbacks();

            // every run should still return the proper number of winners
            assertEquals(0, failureCount.get());
            assertEquals(eventCapacity, successCount.get());

            // inspect where the chosen winners came from within the synthetic waitlist
            Set<String> winners = captureWinnerIds();
            for (String winner : winners) {
                int index = parseEntrantIndex(winner);

                if (index >= 0 && index < 100) {
                    sawFrontRegionWinner = true;
                }
                if (index >= 450 && index < 550) {
                    sawMiddleRegionWinner = true;
                }
                if (index >= 900 && index < 1000) {
                    sawBackRegionWinner = true;
                }
            }

            // stop once all regions have been observed at least once
            if (sawFrontRegionWinner && sawMiddleRegionWinner && sawBackRegionWinner) {
                break;
            }
        }

        assertTrue("Expected at least one winner from the front region", sawFrontRegionWinner);
        assertTrue("Expected at least one winner from the middle region", sawMiddleRegionWinner);
        assertTrue("Expected at least one winner from the back region", sawBackRegionWinner);
    }

    // verify randomness: selections are not biased to when the entrant joined the waitlist
    @Test
    public void lotteryDoesNotTargetEntrantsByJoinedTime() {
        int waitlistSize = 100;
        int eventCapacity = 5;

        initializeFixture();
        stubQueryResult(buildWaitlistedDocs(waitlistSize));

        AtomicInteger successCount = new AtomicInteger(-1);
        AtomicInteger failureCount = new AtomicInteger(0);

        // run a single draw
        mockEventPoolStorage.drawWinners(
                "event-not-front-only",
                eventCapacity,
                successCount::set,
                e -> failureCount.incrementAndGet()
        );

        flushAsyncCallbacks();

        // ensure the draw succeeded
        assertEquals(0, failureCount.get());
        assertEquals(eventCapacity, successCount.get());

        // recover chosen winners
        Set<String> winners = captureWinnerIds();

        // build the deterministic "front of list" expectation that would occur if no shuffle/randomization happened
        Set<String> firstCapacityEntrants = new HashSet<>();
        for (int i = 0; i < eventCapacity; i++) {
            firstCapacityEntrants.add("entrant-" + i);
        }

        // draw should not simply mirror insertion/join order
        assertFalse(
                "A shuffled draw should not simply return the first eventCapacity entrants",
                winners.equals(firstCapacityEntrants)
        );
    }

    // verify winners are removed from waitlist after selection
    @Test
    public void selectedEntrantsRemovedFromWaitlistCollection() {
        String eventId = "event-removed";
        int waitlistSize = 20;
        int eventCapacity = 5;

        initializeFixture();

        // keep the original docs so we can compare selected winners to deleted waitlist refs
        List<QueryDocumentSnapshot> docs = buildWaitlistedDocs(waitlistSize);
        stubQueryResult(docs);

        AtomicInteger successCount = new AtomicInteger(-1);
        AtomicInteger failureCount = new AtomicInteger(0);

        // run draw
        mockEventPoolStorage.drawWinners(
                eventId,
                eventCapacity,
                successCount::set,
                e -> failureCount.incrementAndGet()
        );

        flushAsyncCallbacks();

        // ensure draw completed with the expected number of winners
        assertEquals(0, failureCount.get());
        assertEquals(eventCapacity, successCount.get());

        // capture both invited writes and deleted waitlist refs
        ArgumentCaptor<DocumentReference> invitedRefCaptor =
                ArgumentCaptor.forClass(DocumentReference.class);
        ArgumentCaptor<DocumentReference> deletedRefCaptor =
                ArgumentCaptor.forClass(DocumentReference.class);

        verify(batch, times(eventCapacity)).set(invitedRefCaptor.capture(), any());
        verify(batch, times(eventCapacity)).delete(deletedRefCaptor.capture());

        // convert invited document refs back to entrant IDs
        Set<String> invitedEntrantIds = new HashSet<>();
        for (DocumentReference invitedRef : invitedRefCaptor.getAllValues()) {
            String entrantId = invitedRefToEntrantId.get(invitedRef);
            assertTrue("Invited ref should map to an entrant ID", entrantId != null);
            invitedEntrantIds.add(entrantId);
        }

        // compute which original waitlist document refs should have been deleted
        Set<DocumentReference> expectedDeletedRefs = new HashSet<>();
        for (QueryDocumentSnapshot doc : docs) {
            String entrantId = doc.getString("entrantId");
            if (invitedEntrantIds.contains(entrantId)) {
                expectedDeletedRefs.add(doc.getReference());
            }
        }

        // actual delete operations performed by the batch
        Set<DocumentReference> actualDeletedRefs = new HashSet<>(deletedRefCaptor.getAllValues());

        // the exact selected winners should be the exact documents deleted from waitlisted
        assertEquals(
                "Exactly the selected entrants should be removed from waitlisted",
                expectedDeletedRefs,
                actualDeletedRefs
        );
    }

    // simulate repeated draws where users decline and ensure full exhaustion of waitlist
    @Test
    public void multipleLotteryDrawsEventuallyChoosesAll() {
        // remaining entrants that still need to be sampled in later rounds
        List<String> remainingEntrants = buildEntrantIds(6);

        // variable invite sizes to simulate re-sampling rounds after earlier invite declines
        int[] requestedInvitesPerRound = {3, 2, 3, 1, 1};

        // track every entrant invited across all rounds to ensure no duplicates are redrawn
        Set<String> allInvitedAcrossRounds = new HashSet<>();

        for (int round = 0; round < requestedInvitesPerRound.length && !remainingEntrants.isEmpty(); round++) {
            int requestedInvites = requestedInvitesPerRound[round];

            // rebuild fixture using only entrants still remaining on the waitlist
            initializeFixture();
            stubQueryResult(buildWaitlistedDocsFromEntrantIds(remainingEntrants));

            AtomicInteger successCount = new AtomicInteger(-1);
            AtomicInteger failureCount = new AtomicInteger(0);

            // run draw for this decline/resample round
            mockEventPoolStorage.drawWinners(
                    "event-exhaust-" + round,
                    requestedInvites,
                    successCount::set,
                    e -> failureCount.incrementAndGet()
            );

            flushAsyncCallbacks();

            // draw should succeed
            assertEquals(0, failureCount.get());

            // actual invitations this round cannot exceed the number still remaining
            int expectedThisRound = Math.min(requestedInvites, remainingEntrants.size());
            assertEquals(expectedThisRound, successCount.get());

            // determine which entrants were chosen this round
            Set<String> winnersThisRound = captureWinnerIds();
            assertEquals(expectedThisRound, winnersThisRound.size());

            // nobody should be re-invited across rounds
            for (String winner : winnersThisRound) {
                assertFalse(allInvitedAcrossRounds.contains(winner));
            }

            // simulate all winners declining by removing them from the remaining pool
            allInvitedAcrossRounds.addAll(winnersThisRound);
            remainingEntrants.removeIf(winnersThisRound::contains);
        }

        // eventually the full small waitlist should be exhausted
        assertTrue("All entrants should have been sampled and removed", remainingEntrants.isEmpty());
        assertEquals(6, allInvitedAcrossRounds.size());
    }

    /**
     * Verifies that drawWinners writes user event history updates for both
     * invited entrants and non-selected entrants after the batch commit succeeds.
     */
    @Test
    public void userEventHistoryIsUpdatedByDrawWinners() {
        String eventId = "event-history";
        int waitlistSize = 6;
        int eventCapacity = 2;

        List<QueryDocumentSnapshot> docs = buildWaitlistedDocs(waitlistSize);
        stubQueryResult(docs);

        AtomicInteger successCount = new AtomicInteger(-1);
        AtomicInteger failureCount = new AtomicInteger(0);

        mockEventPoolStorage.drawWinners(
                eventId,
                eventCapacity,
                successCount::set,
                e -> failureCount.incrementAndGet()
        );

        flushAsyncCallbacks();

        assertEquals(0, failureCount.get());
        assertEquals(eventCapacity, successCount.get());

        // winners selected into invited collection
        Set<String> invitedEntrants = captureWinnerIds();
        assertEquals(eventCapacity, invitedEntrants.size());

        // everyone else is treated as not selected / not invited for this round
        Set<String> allEntrants = new HashSet<>();
        for (int i = 0; i < waitlistSize; i++) {
            allEntrants.add("entrant-" + i);
        }

        Set<String> nonSelectedEntrants = new HashSet<>(allEntrants);
        nonSelectedEntrants.removeAll(invitedEntrants);

        // verify history entries for winners
        for (String invitedEntrantId : invitedEntrants) {
            verify(mockUserStorage).addUserEventHistoryEntry(
                    eq(invitedEntrantId),
                    eq(eventId),
                    eq(UserEventHistory.HistoryStatus.INVITED),
                    any(),
                    any(),
                    any()
            );
        }

        // verify history entries for non-selected entrants
        for (String nonSelectedEntrantId : nonSelectedEntrants) {
            verify(mockUserStorage).addUserEventHistoryEntry(
                    eq(nonSelectedEntrantId),
                    eq(eventId),
                    eq(UserEventHistory.HistoryStatus.NOT_SELECTED),
                    any(),
                    any(),
                    any()
            );
        }
    }

    /**
     * Verifies that drawWinners sends lottery-result notifications to both:
     * 1. Entrants who were selected and moved to invited
     * 2. Entrants who were not selected and were marked NOT_INVITED
     */
    @Test
    public void notificationsSentToSelectedAndNotSelected() {
        String eventId = "event-notifications";
        int waitlistSize = 5;
        int eventCapacity = 2;

        // build a synthetic waitlist
        List<QueryDocumentSnapshot> docs = buildWaitlistedDocs(waitlistSize);
        stubQueryResult(docs);

        AtomicInteger successCount = new AtomicInteger(-1);
        AtomicInteger failureCount = new AtomicInteger(0);

        // run draw
        mockEventPoolStorage.drawWinners(
                eventId,
                eventCapacity,
                successCount::set,
                e -> failureCount.incrementAndGet()
        );

        flushAsyncCallbacks();

        // ensure draw succeeded
        assertEquals(0, failureCount.get());
        assertEquals(eventCapacity, successCount.get());

        // capture notification arguments
        ArgumentCaptor<String> eventIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> organizerIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> entrantIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NotificationLog.NotificationType> typeCaptor =
                ArgumentCaptor.forClass(NotificationLog.NotificationType.class);

        verify(mockNotificationLogStorage, times(waitlistSize)).logNotification(
                eventIdCaptor.capture(),
                organizerIdCaptor.capture(),
                entrantIdCaptor.capture(),
                titleCaptor.capture(),
                messageCaptor.capture(),
                typeCaptor.capture(),
                any(),
                any()
        );

        int invitedNotificationCount = 0;
        int notInvitedNotificationCount = 0;

        for (int i = 0; i < titleCaptor.getAllValues().size(); i++) {
            assertEquals(eventId, eventIdCaptor.getAllValues().get(i));
            assertEquals("organizer-1", organizerIdCaptor.getAllValues().get(i));

            String title = titleCaptor.getAllValues().get(i);
            String message = messageCaptor.getAllValues().get(i);
            NotificationLog.NotificationType type = typeCaptor.getAllValues().get(i);

            if (title.startsWith("Invitation to ")) {
                invitedNotificationCount++;
                assertEquals(NotificationLog.NotificationType.WON_LOTTERY, type);
                assertTrue(message.contains("Congratulations!"));
            } else if (title.startsWith("Not Invited to ")) {
                notInvitedNotificationCount++;
                assertEquals(NotificationLog.NotificationType.LOST_LOTTERY, type);
                assertTrue(message.contains("Unfortunately"));
            } else {
                fail("Unexpected notification title: " + title);
            }
        }

        assertEquals(eventCapacity, invitedNotificationCount);
        assertEquals(waitlistSize - eventCapacity, notInvitedNotificationCount);
    }

    // Builds entrant IDs for repeated-draw simulations.
    private List<String> buildEntrantIds(int count) {
        List<String> entrantIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entrantIds.add("entrant-" + i);
        }
        return entrantIds;
    }

    // Builds mocked waitlisted docs from a provided entrant ID list.
    private List<QueryDocumentSnapshot> buildWaitlistedDocsFromEntrantIds(List<String> entrantIds) {
        List<QueryDocumentSnapshot> docs = new ArrayList<>();

        for (int i = 0; i < entrantIds.size(); i++) {
            String entrantId = entrantIds.get(i);

            // mock each waitlisted document and its related references
            QueryDocumentSnapshot doc = mock(QueryDocumentSnapshot.class);
            DocumentReference waitlistedDocRef = mock(DocumentReference.class);
            DocumentReference invitedDocRef = mock(DocumentReference.class);

            // stub the fields accessed by drawWinners
            when(doc.getString("entrantId")).thenReturn(entrantId);
            when(doc.get("joinedAt")).thenReturn("joinedAt-" + entrantId);
            when(doc.getReference()).thenReturn(waitlistedDocRef);

            // map invited document ref back to entrant ID for verification later
            when(invitedCollection.document(entrantId)).thenReturn(invitedDocRef);
            invitedRefToEntrantId.put(invitedDocRef, entrantId);

            docs.add(doc);
        }

        return docs;
    }

    // stub Firestore query to return a predefined set of waitlisted documents
    private void stubQueryResult(List<QueryDocumentSnapshot> docs) {
        when(waitlistedQuery.get()).thenReturn(Tasks.forResult(querySnapshot));

        // return iterator over mocked documents
        when(querySnapshot.iterator()).thenAnswer(invocation -> {
            Iterator<QueryDocumentSnapshot> iterator = docs.iterator();
            return iterator;
        });
    }

    // build a synthetic waitlist of size `count`
    private List<QueryDocumentSnapshot> buildWaitlistedDocs(int count) {
        List<QueryDocumentSnapshot> docs = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String entrantId = "entrant-" + i;

            // mock Firestore document snapshot
            QueryDocumentSnapshot doc = mock(QueryDocumentSnapshot.class);
            DocumentReference waitlistedDocRef = mock(DocumentReference.class);
            DocumentReference invitedDocRef = mock(DocumentReference.class);

            // stub fields used by drawWinners
            when(doc.getString("entrantId")).thenReturn(entrantId);
            when(doc.get("joinedAt")).thenReturn("joinedAt-" + i);
            when(doc.getReference()).thenReturn(waitlistedDocRef);

            // map invited document -> entrantId for later verification
            when(invitedCollection.document(entrantId)).thenReturn(invitedDocRef);
            invitedRefToEntrantId.put(invitedDocRef, entrantId);

            docs.add(doc);
        }

        return docs;
    }

    // capture all entrants that were selected (written to invited collection)
    private Set<String> captureWinnerIds() {
        ArgumentCaptor<DocumentReference> invitedRefCaptor =
                ArgumentCaptor.forClass(DocumentReference.class);

        // capture all "set" operations (invites)
        verify(batch, atLeastOnce()).set(invitedRefCaptor.capture(), any());

        Set<String> winnerIds = new HashSet<>();

        for (DocumentReference invitedRef : invitedRefCaptor.getAllValues()) {
            String entrantId = invitedRefToEntrantId.get(invitedRef);

            // only include valid mappings
            if (entrantId != null) {
                winnerIds.add(entrantId);
            }
        }

        return winnerIds;
    }

    private int parseEntrantIndex(String entrantId) {
        return Integer.parseInt(entrantId.substring("entrant-".length()));
    }

    // flush Robolectric main looper to ensure async callbacks complete
    private void flushAsyncCallbacks() {
        shadowOf(Looper.getMainLooper()).idle();
        shadowOf(Looper.getMainLooper()).idle();
    }
}