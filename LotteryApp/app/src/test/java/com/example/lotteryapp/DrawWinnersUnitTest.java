package com.example.lotteryapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;

import com.example.lotteryapp.models.Entrant;
import com.example.lotteryapp.services.storage.EventPoolStorage;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34)
public class DrawWinnersUnitTest {

    private FirebaseFirestore db;
    private EventPoolStorage storage;

    private CollectionReference eventsCollection;
    private DocumentReference eventDoc;
    private CollectionReference waitlistedCollection;
    private CollectionReference invitedCollection;
    private Query waitlistedQuery;
    private QuerySnapshot querySnapshot;
    private WriteBatch batch;

    /**
     * Maps invited document reference -> entrantId so tests can recover winners.
     */
    private Map<DocumentReference, String> invitedRefToEntrantId;

    @Before
    public void setUp() {
        initializeFixture();
    }

    private void initializeFixture() {
        db = mock(FirebaseFirestore.class);
        storage = new EventPoolStorage(db);

        eventsCollection = mock(CollectionReference.class);
        eventDoc = mock(DocumentReference.class);
        waitlistedCollection = mock(CollectionReference.class);
        invitedCollection = mock(CollectionReference.class);
        waitlistedQuery = mock(Query.class);
        querySnapshot = mock(QuerySnapshot.class);
        batch = mock(WriteBatch.class);

        invitedRefToEntrantId = new HashMap<>();

        when(db.collection("events")).thenReturn(eventsCollection);
        when(eventsCollection.document(anyString())).thenReturn(eventDoc);

        when(eventDoc.collection("waitlisted")).thenReturn(waitlistedCollection);
        when(eventDoc.collection("invited")).thenReturn(invitedCollection);

        when(waitlistedCollection.whereEqualTo(
                "status",
                Entrant.EntrantStatus.WAITLISTED.name()
        )).thenReturn(waitlistedQuery);

        when(db.batch()).thenReturn(batch);
        when(batch.commit()).thenReturn(Tasks.forResult(null));
    }

    @Test
    public void drawWinners_largeWaitlist_onlyInvitesEventCapacityEntrants() {
        String eventId = "event-1";
        int waitlistSize = 10_000;
        int eventCapacity = 25;

        List<QueryDocumentSnapshot> docs = buildWaitlistedDocs(waitlistSize);
        stubQueryResult(docs);

        AtomicInteger successCount = new AtomicInteger(-1);
        AtomicInteger failureCount = new AtomicInteger(0);

        storage.drawWinners(
                eventId,
                eventCapacity,
                successCount::set,
                e -> failureCount.incrementAndGet()
        );

        flushAsyncCallbacks();

        assertEquals(0, failureCount.get());
        assertEquals(eventCapacity, successCount.get());

        verify(batch, times(eventCapacity)).set(any(DocumentReference.class), any());
        verify(batch, times(eventCapacity)).delete(any(DocumentReference.class));

        int losers = waitlistSize - eventCapacity;
        verify(batch, times(losers)).update(any(DocumentReference.class), eq("status"), eq(Entrant.EntrantStatus.NOT_INVITED.name()));
        verify(batch, times(losers)).update(any(DocumentReference.class), eq("updatedAt"), any(FieldValue.class));

        verify(batch, times(1)).update(eq(eventDoc), eq("invitationCount"), any(FieldValue.class));
        verify(batch, times(1)).commit();
    }

    @Test
    public void onlySelectUpToEventCapacityPerDraw() {
        String eventId = "event-2";
        int waitlistSize = 1000;
        int eventCapacity = 2;

        List<QueryDocumentSnapshot> docs = buildWaitlistedDocs(waitlistSize);
        stubQueryResult(docs);

        AtomicInteger successCount = new AtomicInteger(-1);
        AtomicInteger failureCount = new AtomicInteger(0);

        storage.drawWinners(
                eventId,
                eventCapacity,
                successCount::set,
                e -> failureCount.incrementAndGet()
        );

        flushAsyncCallbacks();

        assertEquals(0, failureCount.get());
        assertEquals(eventCapacity, successCount.get());

        verify(batch, times(eventCapacity)).set(any(DocumentReference.class), any());
        verify(batch, times(eventCapacity)).delete(any(DocumentReference.class));

        int losers = waitlistSize - eventCapacity;
        verify(batch, times(losers)).update(any(DocumentReference.class), eq("status"), eq(Entrant.EntrantStatus.NOT_INVITED.name()));
        verify(batch, times(losers)).update(any(DocumentReference.class), eq("updatedAt"), any(FieldValue.class));

        verify(batch, times(1)).update(eq(eventDoc), eq("invitationCount"), any(FieldValue.class));
        verify(batch, times(1)).commit();
    }

    @Test
    public void invitesAllWaitlistedWhen_waitlistCapLessThanEventCap() {
        String eventId = "event-3";
        int waitlistSize = 4;
        int eventCapacity = 10;

        List<QueryDocumentSnapshot> docs = buildWaitlistedDocs(waitlistSize);
        stubQueryResult(docs);

        AtomicInteger successCount = new AtomicInteger(-1);
        AtomicInteger failureCount = new AtomicInteger(0);

        storage.drawWinners(
                eventId,
                eventCapacity,
                successCount::set,
                e -> failureCount.incrementAndGet()
        );

        flushAsyncCallbacks();

        assertEquals(0, failureCount.get());
        assertEquals(waitlistSize, successCount.get());

        verify(batch, times(waitlistSize)).set(any(DocumentReference.class), any());
        verify(batch, times(waitlistSize)).delete(any(DocumentReference.class));

        verify(batch, times(0)).update(any(DocumentReference.class), eq("status"), eq(Entrant.EntrantStatus.NOT_INVITED.name()));
        verify(batch, times(0)).update(any(DocumentReference.class), eq("updatedAt"), any(FieldValue.class));

        verify(batch, times(1)).update(eq(eventDoc), eq("invitationCount"), any(FieldValue.class));
        verify(batch, times(1)).commit();
    }

    @Test
    public void lotteryForEmptyWaitlistDoesNothing() {
        String eventId = "event-4";

        stubQueryResult(new ArrayList<>());

        AtomicInteger successCount = new AtomicInteger(-1);
        AtomicInteger failureCount = new AtomicInteger(0);

        storage.drawWinners(
                eventId,
                5,
                successCount::set,
                e -> failureCount.incrementAndGet()
        );

        flushAsyncCallbacks();

        assertEquals(0, failureCount.get());
        assertEquals(0, successCount.get());

        verify(batch, times(0)).set(any(DocumentReference.class), any());
        verify(batch, times(0)).delete(any(DocumentReference.class));
        verify(batch, times(0)).update(any(DocumentReference.class), anyString(), any());
        verify(batch, times(0)).commit();
    }

    @Test
    public void sequentialDrawsProduceDistinctSelections() {
        int waitlistSize = 30;
        int eventCapacity = 5;
        int runs = 25;

        Set<Set<String>> distinctWinnerSets = new HashSet<>();

        for (int i = 0; i < runs; i++) {
            initializeFixture();
            stubQueryResult(buildWaitlistedDocs(waitlistSize));

            AtomicInteger successCount = new AtomicInteger(-1);
            AtomicInteger failureCount = new AtomicInteger(0);

            storage.drawWinners(
                    "event-random-" + i,
                    eventCapacity,
                    successCount::set,
                    e -> failureCount.incrementAndGet()
            );

            flushAsyncCallbacks();

            assertEquals(0, failureCount.get());
            assertEquals(eventCapacity, successCount.get());

            Set<String> winners = captureWinnerIds();
            assertEquals(eventCapacity, winners.size());

            distinctWinnerSets.add(winners);
        }

        assertTrue(
                "Expected multiple different winner sets across repeated runs",
                distinctWinnerSets.size() > 1
        );
    }

    @Test
    public void sequentialDrawsDoNotTargetFrontOfCollection() {
        int waitlistSize = 100;
        int eventCapacity = 5;
        int runs = 30;

        boolean selectedSomeoneOutsideFirstFive = false;

        for (int i = 0; i < runs; i++) {
            initializeFixture();
            stubQueryResult(buildWaitlistedDocs(waitlistSize));

            AtomicInteger successCount = new AtomicInteger(-1);
            AtomicInteger failureCount = new AtomicInteger(0);

            storage.drawWinners(
                    "event-outside-front-" + i,
                    eventCapacity,
                    successCount::set,
                    e -> failureCount.incrementAndGet()
            );

            flushAsyncCallbacks();

            assertEquals(0, failureCount.get());
            assertEquals(eventCapacity, successCount.get());

            Set<String> winners = captureWinnerIds();
            for (String winner : winners) {
                int index = parseEntrantIndex(winner);
                if (index >= eventCapacity) {
                    selectedSomeoneOutsideFirstFive = true;
                    break;
                }
            }

            if (selectedSomeoneOutsideFirstFive) {
                break;
            }
        }

        assertTrue(
                "Expected at least one run to select an entrant outside the first eventCapacity positions",
                selectedSomeoneOutsideFirstFive
        );
    }

    @Test
    public void sequentialDrawsHaveSelectionsAcrossTheWholeCollection() {
        int waitlistSize = 1000;
        int eventCapacity = 10;
        int runs = 40;

        boolean sawFrontRegionWinner = false;   // 0-99
        boolean sawMiddleRegionWinner = false;  // 450-549
        boolean sawBackRegionWinner = false;    // 900-999

        for (int i = 0; i < runs; i++) {
            initializeFixture();
            stubQueryResult(buildWaitlistedDocs(waitlistSize));

            AtomicInteger successCount = new AtomicInteger(-1);
            AtomicInteger failureCount = new AtomicInteger(0);

            storage.drawWinners(
                    "event-regions-" + i,
                    eventCapacity,
                    successCount::set,
                    e -> failureCount.incrementAndGet()
            );

            flushAsyncCallbacks();

            assertEquals(0, failureCount.get());
            assertEquals(eventCapacity, successCount.get());

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

            if (sawFrontRegionWinner && sawMiddleRegionWinner && sawBackRegionWinner) {
                break;
            }
        }

        assertTrue("Expected at least one winner from the front region", sawFrontRegionWinner);
        assertTrue("Expected at least one winner from the middle region", sawMiddleRegionWinner);
        assertTrue("Expected at least one winner from the back region", sawBackRegionWinner);
    }

    @Test
    public void lotteryDoesNotTargetEntrantsByJoinedTime() {
        int waitlistSize = 100;
        int eventCapacity = 5;

        initializeFixture();
        stubQueryResult(buildWaitlistedDocs(waitlistSize));

        AtomicInteger successCount = new AtomicInteger(-1);
        AtomicInteger failureCount = new AtomicInteger(0);

        storage.drawWinners(
                "event-not-front-only",
                eventCapacity,
                successCount::set,
                e -> failureCount.incrementAndGet()
        );

        flushAsyncCallbacks();

        assertEquals(0, failureCount.get());
        assertEquals(eventCapacity, successCount.get());

        Set<String> winners = captureWinnerIds();

        Set<String> firstCapacityEntrants = new HashSet<>();
        for (int i = 0; i < eventCapacity; i++) {
            firstCapacityEntrants.add("entrant-" + i);
        }

        assertFalse(
                "A shuffled draw should not simply return the first eventCapacity entrants",
                winners.equals(firstCapacityEntrants)
        );
    }

    @Test
    public void selectedEntrantsRemovedFromWaitlistCollection() {
        String eventId = "event-removed";
        int waitlistSize = 20;
        int eventCapacity = 5;

        initializeFixture();
        List<QueryDocumentSnapshot> docs = buildWaitlistedDocs(waitlistSize);
        stubQueryResult(docs);

        AtomicInteger successCount = new AtomicInteger(-1);
        AtomicInteger failureCount = new AtomicInteger(0);

        storage.drawWinners(
                eventId,
                eventCapacity,
                successCount::set,
                e -> failureCount.incrementAndGet()
        );

        flushAsyncCallbacks();

        assertEquals(0, failureCount.get());
        assertEquals(eventCapacity, successCount.get());

        ArgumentCaptor<DocumentReference> invitedRefCaptor =
                ArgumentCaptor.forClass(DocumentReference.class);
        ArgumentCaptor<DocumentReference> deletedRefCaptor =
                ArgumentCaptor.forClass(DocumentReference.class);

        verify(batch, times(eventCapacity)).set(invitedRefCaptor.capture(), any());
        verify(batch, times(eventCapacity)).delete(deletedRefCaptor.capture());

        Set<String> invitedEntrantIds = new HashSet<>();
        for (DocumentReference invitedRef : invitedRefCaptor.getAllValues()) {
            String entrantId = invitedRefToEntrantId.get(invitedRef);
            assertTrue("Invited ref should map to an entrant ID", entrantId != null);
            invitedEntrantIds.add(entrantId);
        }

        Set<DocumentReference> expectedDeletedRefs = new HashSet<>();
        for (QueryDocumentSnapshot doc : docs) {
            String entrantId = doc.getString("entrantId");
            if (invitedEntrantIds.contains(entrantId)) {
                expectedDeletedRefs.add(doc.getReference());
            }
        }

        Set<DocumentReference> actualDeletedRefs = new HashSet<>(deletedRefCaptor.getAllValues());

        assertEquals(
                "Exactly the selected entrants should be removed from waitlisted",
                expectedDeletedRefs,
                actualDeletedRefs
        );
    }

    @Test
    public void drawWinners_simulateDeclineRounds_eventuallyExhaustsSmallWaitlist() {
        List<String> remainingEntrants = buildEntrantIds(6);
        int[] requestedInvitesPerRound = {3, 2, 3, 1, 1};

        Set<String> allInvitedAcrossRounds = new HashSet<>();

        for (int round = 0; round < requestedInvitesPerRound.length && !remainingEntrants.isEmpty(); round++) {
            int requestedInvites = requestedInvitesPerRound[round];

            initializeFixture();
            stubQueryResult(buildWaitlistedDocsFromEntrantIds(remainingEntrants));

            AtomicInteger successCount = new AtomicInteger(-1);
            AtomicInteger failureCount = new AtomicInteger(0);

            storage.drawWinners(
                    "event-exhaust-" + round,
                    requestedInvites,
                    successCount::set,
                    e -> failureCount.incrementAndGet()
            );

            flushAsyncCallbacks();

            assertEquals(0, failureCount.get());

            int expectedThisRound = Math.min(requestedInvites, remainingEntrants.size());
            assertEquals(expectedThisRound, successCount.get());

            Set<String> winnersThisRound = captureWinnerIds();
            assertEquals(expectedThisRound, winnersThisRound.size());

            for (String winner : winnersThisRound) {
                assertFalse(allInvitedAcrossRounds.contains(winner));
            }

            allInvitedAcrossRounds.addAll(winnersThisRound);
            remainingEntrants.removeIf(winnersThisRound::contains);
        }

        assertTrue("All entrants should have been sampled and removed", remainingEntrants.isEmpty());
        assertEquals(6, allInvitedAcrossRounds.size());
    }

    private List<String> buildEntrantIds(int count) {
        List<String> entrantIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entrantIds.add("entrant-" + i);
        }
        return entrantIds;
    }

    private List<QueryDocumentSnapshot> buildWaitlistedDocsFromEntrantIds(List<String> entrantIds) {
        List<QueryDocumentSnapshot> docs = new ArrayList<>();

        for (int i = 0; i < entrantIds.size(); i++) {
            String entrantId = entrantIds.get(i);

            QueryDocumentSnapshot doc = mock(QueryDocumentSnapshot.class);
            DocumentReference waitlistedDocRef = mock(DocumentReference.class);
            DocumentReference invitedDocRef = mock(DocumentReference.class);

            when(doc.getString("entrantId")).thenReturn(entrantId);
            when(doc.get("joinedAt")).thenReturn("joinedAt-" + entrantId);
            when(doc.getReference()).thenReturn(waitlistedDocRef);

            when(invitedCollection.document(entrantId)).thenReturn(invitedDocRef);
            invitedRefToEntrantId.put(invitedDocRef, entrantId);

            docs.add(doc);
        }

        return docs;
    }
    private void stubQueryResult(List<QueryDocumentSnapshot> docs) {
        when(waitlistedQuery.get()).thenReturn(Tasks.forResult(querySnapshot));
        when(querySnapshot.iterator()).thenAnswer(invocation -> {
            Iterator<QueryDocumentSnapshot> iterator = docs.iterator();
            return iterator;
        });
    }

    private List<QueryDocumentSnapshot> buildWaitlistedDocs(int count) {
        List<QueryDocumentSnapshot> docs = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String entrantId = "entrant-" + i;

            QueryDocumentSnapshot doc = mock(QueryDocumentSnapshot.class);
            DocumentReference waitlistedDocRef = mock(DocumentReference.class);
            DocumentReference invitedDocRef = mock(DocumentReference.class);

            when(doc.getString("entrantId")).thenReturn(entrantId);
            when(doc.get("joinedAt")).thenReturn("joinedAt-" + i);
            when(doc.getReference()).thenReturn(waitlistedDocRef);

            when(invitedCollection.document(entrantId)).thenReturn(invitedDocRef);
            invitedRefToEntrantId.put(invitedDocRef, entrantId);

            docs.add(doc);
        }

        return docs;
    }

    private Set<String> captureWinnerIds() {
        ArgumentCaptor<DocumentReference> invitedRefCaptor =
                ArgumentCaptor.forClass(DocumentReference.class);

        verify(batch, atLeastOnce()).set(invitedRefCaptor.capture(), any());

        Set<String> winnerIds = new HashSet<>();
        for (DocumentReference invitedRef : invitedRefCaptor.getAllValues()) {
            String entrantId = invitedRefToEntrantId.get(invitedRef);
            if (entrantId != null) {
                winnerIds.add(entrantId);
            }
        }

        return winnerIds;
    }

    private int parseEntrantIndex(String entrantId) {
        return Integer.parseInt(entrantId.substring("entrant-".length()));
    }

    private void flushAsyncCallbacks() {
        shadowOf(Looper.getMainLooper()).idle();
        shadowOf(Looper.getMainLooper()).idle();
    }
}