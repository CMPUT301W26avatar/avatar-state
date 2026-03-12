package com.example.lotteryapp.services.storage;

import static com.example.lotteryapp.models.Entrant.EntrantStatus.DECLINED;
import static com.example.lotteryapp.models.Entrant.EntrantStatus.ENROLLED;
import static com.example.lotteryapp.models.Entrant.EntrantStatus.INVITED;
import static com.example.lotteryapp.models.Entrant.EntrantStatus.WAITLISTED;

import com.example.lotteryapp.models.Entrant;
import com.example.lotteryapp.models.Event;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the event pool and lottery lifecycle.
 * Firestore path: events/{eventId}/entries/{entrantId}
 *
 * US 02.06.03 - organizer views all entrants
 * US 02.01.01 - organizer sets registration period
 */

public class EventPoolStorage {

    private final FirebaseFirestore db;

    public EventPoolStorage(FirebaseFirestore db) {
        this.db = db;
    }

    // helpers: return a single document for a certain entrant (entrantId) in every subcollection of an event (eventId)
    private DocumentReference waitlistedDoc(String eventId, String entrantId) {
        return db.collection("events")
                .document(eventId)
                .collection("waitlisted")
                .document(entrantId);
    }
    private DocumentReference invitedDoc(String eventId, String entrantId) {
        return db.collection("events")
                .document(eventId)
                .collection("invited")
                .document(entrantId);
    }
    private DocumentReference enrolledDoc(String eventId, String entrantId) {
        return db.collection("events")
                .document(eventId)
                .collection("enrolled")
                .document(entrantId);
    }
    private DocumentReference declinedDoc(String eventId, String entrantId) {
        return db.collection("events")
                .document(eventId)
                .collection("declined")
                .document(entrantId);
    }

    // helper: return a dict/map of entrant data to upsert into the database
    private Map<String, Object> mapEntrantData(String eventId, String entrantId, String status) {
        Map<String, Object> data = new HashMap<>();
        data.put("entrantId", entrantId);
        data.put("eventId", eventId);
        data.put("status", status);
        data.put("joinedAt", FieldValue.serverTimestamp());
        return data;
    }

    // add an entrant to the waitlisted subcollection
    public void waitlistForEvent(
            String eventId,
            Entrant entrant,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        DocumentReference eventRef = db.collection("events").document(eventId);
        DocumentReference entrantWaitlistedRef = waitlistedDoc(eventId, entrant.getEntrantId());
        DocumentReference entrantInvitedRef = invitedDoc(eventId, entrant.getEntrantId());
        DocumentReference entrantEnrolledRef = enrolledDoc(eventId, entrant.getEntrantId());

        db.runTransaction((Transaction.Function<Void>) transaction -> {
                    DocumentSnapshot eventSnap = transaction.get(eventRef);
                    DocumentSnapshot waitlistedSnap = transaction.get(entrantWaitlistedRef);
                    DocumentSnapshot invitedSnap = transaction.get(entrantInvitedRef);
                    DocumentSnapshot enrolledSnap = transaction.get(entrantEnrolledRef);

                    if (!eventSnap.exists()) {
                        throw new IllegalStateException("Event does not exist");
                    }

                    // enforce no double waitlisting
                    if (waitlistedSnap.exists() && (waitlistedSnap.getString("status").equals("WAITLISTED"))) {
                        throw new IllegalStateException("Entrant already waitlisted");
                    }

                    int waitlistCount = eventSnap.getLong("waitlistCount") != null ? eventSnap.getLong("waitlistCount").intValue() : 0;
                    int waitlistCapacity = eventSnap.getLong("waitlistCapacity") != null ? eventSnap.getLong("waitlistCapacity").intValue() : 0;

                    if (waitlistCount >= waitlistCapacity) {
                        throw new IllegalStateException("Waitlist is full");
                    }

                    Map<String, Object> data = mapEntrantData(
                            eventId,
                            entrant.getEntrantId(),
                            WAITLISTED.name()
                    );

                    transaction.set(entrantWaitlistedRef, data);

                    // after the user has been inserted into the waitlisted collection, update the event entry
                    int updatedWaitlistCount = waitlistCount + 1;

                    Map<String, Object> eventUpdates = new HashMap<>();
                    eventUpdates.put("waitlistCount", updatedWaitlistCount);
                    eventUpdates.put(
                            "status",
                            updatedWaitlistCount >= waitlistCapacity
                                    ? Event.EventStatus.REG_FULL.name()
                                    : Event.EventStatus.REG_OPEN.name()
                    );

                    transaction.update(eventRef, eventUpdates);

                    return null;
                }).addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // add an entrant to the invited subcollection, and remove them from the waitlisted subcollection
    public void inviteToEvent(
            String eventId,
            Entrant entrant,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        DocumentReference eventRef = db.collection("events").document(eventId);
        DocumentReference entrantWaitlistedRef = waitlistedDoc(eventId, entrant.getEntrantId());
        DocumentReference entrantInvitedRef = invitedDoc(eventId, entrant.getEntrantId());
        DocumentReference entrantEnrolledRef = enrolledDoc(eventId, entrant.getEntrantId());

        db.runTransaction((Transaction.Function<Void>) transaction -> {
                    DocumentSnapshot eventSnap = transaction.get(eventRef);
                    DocumentSnapshot waitlistedSnap = transaction.get(entrantWaitlistedRef);
                    DocumentSnapshot invitedSnap = transaction.get(entrantInvitedRef);
                    DocumentSnapshot enrolledSnap = transaction.get(entrantEnrolledRef);

                    if (!eventSnap.exists()) {
                        throw new IllegalStateException("Event does not exist");
                    }

                    // enforce no double invitation
                    if (invitedSnap.exists()) {
                        throw new IllegalStateException("Entrant already invited");
                    }

                    int invitationCount = eventSnap.getLong("invitationCount") != null ? eventSnap.getLong("invitationCount").intValue() : 0;
                    int waitlistCount = eventSnap.getLong("waitlistCount") != null ? eventSnap.getLong("waitlistCount").intValue() : 0;
                    int waitlistCap = eventSnap.getLong("waitlistCapacity") != null ? eventSnap.getLong("waitlistCapacity").intValue() : 0;

                    Map<String, Object> data = mapEntrantData(
                            eventId,
                            entrant.getEntrantId(),
                            INVITED.name()
                    );
                    // mapEntrantData sets a current time, overwrite this current time with the stored "joinedAt" time
                    data.put("joinedAt", waitlistedSnap.get("joinedAt"));

                    transaction.set(entrantInvitedRef, data); // add to invited subcollection
                    transaction.delete(entrantWaitlistedRef); // remove from waitlisted subcollection

                    // after the user has been inserted into the invited collection, update the event entry
                    int updatedWaitlistCount = Math.max(0, waitlistCount - 1);

                    Map<String, Object> eventUpdates = new HashMap<>();
                    eventUpdates.put("waitlistCount", updatedWaitlistCount);
                    eventUpdates.put("invitationCount", invitationCount + 1);
                    eventUpdates.put(
                            "status",
                            updatedWaitlistCount >= waitlistCap
                                    ? Event.EventStatus.REG_FULL.name()
                                    : Event.EventStatus.REG_OPEN.name()
                    );

                    transaction.update(eventRef, eventUpdates);
                    return null;
                }).addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // add an entrant to the enrolled subcollection, and remove them from the invited subcollection
    public void enrollInEvent(
            String eventId,
            Entrant entrant,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        DocumentReference eventRef = db.collection("events").document(eventId);
        DocumentReference invitedRef = invitedDoc(eventId, entrant.getEntrantId());
        DocumentReference enrolledRef = enrolledDoc(eventId, entrant.getEntrantId());

        db.runTransaction((Transaction.Function<Void>) transaction -> {
                    DocumentSnapshot eventSnap = transaction.get(eventRef);
                    DocumentSnapshot invitedSnap = transaction.get(invitedRef);
                    DocumentSnapshot enrolledSnap = transaction.get(enrolledRef);

                    if (!eventSnap.exists()) {
                        throw new IllegalStateException("Event does not exist");
                    }
                    if (!invitedSnap.exists()) {
                        throw new IllegalStateException("Entrant is not invited");
                    }
                    if (enrolledSnap.exists()) {
                        throw new IllegalStateException("Entrant already enrolled");
                    }

                    int enrolledCount = eventSnap.getLong("enrolledCount") != null
                            ? eventSnap.getLong("enrolledCount").intValue() : 0;
                    int invitationCount = eventSnap.getLong("invitationCount") != null
                            ? eventSnap.getLong("invitationCount").intValue() : 0;
                    int capacity = eventSnap.getLong("eventCapacity") != null
                            ? eventSnap.getLong("eventCapacity").intValue() : 0;

                    if (enrolledCount >= capacity) {
                        throw new IllegalStateException("Event is already full");
                    }

                    Map<String, Object> data = mapEntrantData(
                            eventId,
                            entrant.getEntrantId(),
                            ENROLLED.name()
                    );
                    data.put("joinedAt", invitedSnap.get("joinedAt"));

                    transaction.delete(invitedRef);
                    transaction.set(enrolledRef, data);

                    int updatedEnrolled = enrolledCount + 1;

                    Map<String, Object> eventUpdates = new HashMap<>();
                    eventUpdates.put("enrolledCount", updatedEnrolled);
                    eventUpdates.put("invitationCount", Math.max(0, invitationCount - 1));
                    eventUpdates.put(
                            "status",
                            updatedEnrolled >= capacity
                                    ? Event.EventStatus.EVENT_FULL.name()
                                    : Event.EventStatus.EVENT_OPEN.name()
                    );

                    transaction.update(eventRef, eventUpdates);
                    return null;
                }).addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // add an entrant to the declined subcollection, and remove them from the invited subcollection
    public void removeFromWaitlist(
            String eventId,
            String entrantId,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        DocumentReference eventRef = db.collection("events").document(eventId);
        DocumentReference waitlistedRef = waitlistedDoc(eventId, entrantId);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
                    DocumentSnapshot eventSnap = transaction.get(eventRef);
                    DocumentSnapshot waitlistedSnap = transaction.get(waitlistedRef);

                    if (!eventSnap.exists()) {
                        throw new IllegalStateException("Event does not exist");
                    }
                    if (!waitlistedSnap.exists()) {
                        throw new IllegalStateException("Entrant is not waitlisted");
                    }

                    int waitlistCount = eventSnap.getLong("waitlistCount") != null
                            ? eventSnap.getLong("waitlistCount").intValue() : 0;
                    int waitlistCapacity = eventSnap.getLong("waitlistCapacity") != null
                            ? eventSnap.getLong("waitlistCapacity").intValue() : 0;

                    transaction.delete(waitlistedRef);

                    int updatedWaitlistCount = Math.max(0, waitlistCount - 1);

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("waitlistCount", updatedWaitlistCount);
                    updates.put(
                            "status",
                            updatedWaitlistCount >= waitlistCapacity
                                    ? Event.EventStatus.REG_FULL.name()
                                    : Event.EventStatus.REG_OPEN.name()
                    );

                    transaction.update(eventRef, updates);
                    return null;
                }).addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }
    // delete the entrant from the enrolled subcollection
    public void unenroll(
            String eventId,
            String entrantId,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        DocumentReference eventRef = db.collection("events").document(eventId);
        DocumentReference enrolledRef = enrolledDoc(eventId, entrantId);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
                    DocumentSnapshot eventSnap = transaction.get(eventRef);
                    DocumentSnapshot enrolledSnap = transaction.get(enrolledRef);

                    if (!eventSnap.exists()) {
                        throw new IllegalStateException("Event does not exist");
                    }

                    if (!enrolledSnap.exists()) {
                        throw new IllegalStateException("Entrant is not enrolled");
                    }

                    int enrolledCount = eventSnap.getLong("enrolledCount") != null
                            ? eventSnap.getLong("enrolledCount").intValue() : 0;

                    transaction.delete(enrolledRef);

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("enrolledCount", Math.max(0, enrolledCount - 1));

                    transaction.update(eventRef, updates);
                    return null;
                }).addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }


    // DATABASE BASIC QUERIES
    // for querying whether an Entrant is WAITLISTED, INVITED, ...
    public void getEntrantStatus(
            String eventId,
            String entrantId,
            OnSuccessListener<Entrant.EntrantStatus> onSuccess,
            OnFailureListener onFailure
    ) {
        waitlistedDoc(eventId, entrantId).get()
                .addOnSuccessListener(waitlistedSnap -> {
                    if (waitlistedSnap.exists()) {
                        onSuccess.onSuccess(WAITLISTED);
                        return;
                    }

                    invitedDoc(eventId, entrantId).get()
                            .addOnSuccessListener(invitedSnap -> {
                                if (invitedSnap.exists()) {
                                    onSuccess.onSuccess(INVITED);
                                    return;
                                }

                                enrolledDoc(eventId, entrantId).get()
                                        .addOnSuccessListener(enrolledSnap -> {
                                            if (enrolledSnap.exists()) {
                                                onSuccess.onSuccess(ENROLLED);
                                                return;
                                            }

                                            declinedDoc(eventId, entrantId).get()
                                                    .addOnSuccessListener(declinedSnap -> {
                                                        if (declinedSnap.exists()) {
                                                            onSuccess.onSuccess(DECLINED);
                                                        } else {
                                                            onSuccess.onSuccess(null);
                                                        }
                                                    })
                                                    .addOnFailureListener(onFailure);
                                        })
                                        .addOnFailureListener(onFailure);
                            })
                            .addOnFailureListener(onFailure);
                })
                .addOnFailureListener(onFailure);
    }


    // DATABASE LIST QUERIES
    // return a list of entrants in the waitlisted subcollection asynchronously
    public void getWaitlistedEntrants(String eventId,
                                    OnSuccessListener<List<Entrant>> onSuccess,
                                    OnFailureListener onFailure) {
        db.collection("events")
                .document(eventId)
                .collection("waitlisted")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Entrant> entrants = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String entrantId = doc.getString("entrantId");
                        String eid = doc.getString("eventId");
                        if (entrantId != null && eid != null) {
                            entrants.add(new Entrant(
                                    entrantId,
                                    eid,
                                    WAITLISTED
                            ));
                        }
                    }
                    onSuccess.onSuccess(entrants);
                })
                .addOnFailureListener(onFailure);
    }

    // return a list of entrants in the invited subcollection asynchronously
    public void getInvitedEntrants(String eventId,
                                    OnSuccessListener<List<Entrant>> onSuccess,
                                    OnFailureListener onFailure) {
        db.collection("events")
                .document(eventId)
                .collection("invited")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Entrant> entrants = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String entrantId = doc.getString("entrantId");
                        String eid = doc.getString("eventId");
                        if (entrantId != null && eid != null) {
                            entrants.add(new Entrant(
                                    entrantId,
                                    eid,
                                    INVITED
                            ));
                        }
                    }
                    onSuccess.onSuccess(entrants);
                })
                .addOnFailureListener(onFailure);
    }

    // return a list of entrants in the declined subcollection asynchronously
    public void getDeclinedEntrants(String eventId,
                                    OnSuccessListener<List<Entrant>> onSuccess,
                                    OnFailureListener onFailure) {
        db.collection("events")
                .document(eventId)
                .collection("declined")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Entrant> entrants = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String entrantId = doc.getString("entrantId");
                        String eid = doc.getString("eventId");
                        if (entrantId != null && eid != null) {
                            entrants.add(new Entrant(
                                    entrantId,
                                    eid,
                                    DECLINED
                            ));
                        }
                    }
                    onSuccess.onSuccess(entrants);
                })
                .addOnFailureListener(onFailure);
    }

    // return a list of entrants in the enrolled subcollection asynchronously

    public void getEnrolledEntrants(String eventId,
                                    OnSuccessListener<List<Entrant>> onSuccess,
                                    OnFailureListener onFailure) {
        db.collection("events")
                .document(eventId)
                .collection("enrolled")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Entrant> entrants = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String entrantId = doc.getString("entrantId");
                        String eid = doc.getString("eventId");
                        if (entrantId != null && eid != null) {
                            entrants.add(new Entrant(
                                    entrantId,
                                    eid,
                                    ENROLLED
                            ));
                        }
                    }
                    onSuccess.onSuccess(entrants);
                })
                .addOnFailureListener(onFailure);
    }

    // DATABASE DELETE ENTRY (status unknown)
    // helper: holds different logic for deleting an entrant from each subcollection
    private void deleteEntryByStatus(
            String eventId,
            String entrantId,
            Entrant.EntrantStatus status,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        DocumentReference eventRef = db.collection("events").document(eventId);
        DocumentReference waitlistedRef = waitlistedDoc(eventId, entrantId);
        DocumentReference invitedRef = invitedDoc(eventId, entrantId);
        DocumentReference enrolledRef = enrolledDoc(eventId, entrantId);
        DocumentReference declinedRef = declinedDoc(eventId, entrantId);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot eventSnap = transaction.get(eventRef);

            if (!eventSnap.exists()) {
                throw new IllegalStateException("Event does not exist");
            }

            int waitlistCount = eventSnap.getLong("waitlistCount") != null
                    ? eventSnap.getLong("waitlistCount").intValue() : 0;
            int invitationCount = eventSnap.getLong("invitationCount") != null
                    ? eventSnap.getLong("invitationCount").intValue() : 0;
            int enrolledCount = eventSnap.getLong("enrolledCount") != null
                    ? eventSnap.getLong("enrolledCount").intValue() : 0;
            int waitlistCapacity = eventSnap.getLong("waitlistCapacity") != null
                    ? eventSnap.getLong("waitlistCapacity").intValue() : 0;

            Map<String, Object> updates = new HashMap<>();

            switch (status) {
                case WAITLISTED:
                    transaction.delete(waitlistedRef);
                    waitlistCount = Math.max(0, waitlistCount - 1);
                    updates.put("waitlistCount", waitlistCount);
                    updates.put("status",
                        waitlistCount >= waitlistCapacity
                                ? Event.EventStatus.REG_CLOSED.name()
                                : Event.EventStatus.REG_OPEN.name()
                    );
                    break;

                case INVITED:
                    transaction.delete(invitedRef);
                    invitationCount = Math.max(0, invitationCount - 1);
                    updates.put("invitationCount", invitationCount);
                    break;

                case ENROLLED:
                    transaction.delete(enrolledRef);
                    enrolledCount = Math.max(0, enrolledCount - 1);
                    updates.put("enrolledCount", enrolledCount);
                    break;

                case DECLINED:
                    transaction.delete(declinedRef);
                    break;
            }

            if (!updates.isEmpty()) {
                transaction.update(eventRef, updates);
            }

            return null;
        }).addOnSuccessListener(onSuccess)
        .addOnFailureListener(onFailure);
    }

    // deletes the selected Entrant from all subcollections, in all events
    public void delAllUserEntries(
            String entrantId,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        db.collection("events").get().addOnSuccessListener(eventSnapshot -> {
            if (eventSnapshot.isEmpty()) {
                onSuccess.onSuccess(null);
                return;
            }

            AtomicInteger remainingEvents = new AtomicInteger(eventSnapshot.size());
            boolean[] failed = {false};

            Runnable tryFinish = () -> {
                if (!failed[0] && remainingEvents.get() == 0) {
                    onSuccess.onSuccess(null);
                }
            };

            for (QueryDocumentSnapshot eventDoc : eventSnapshot) {
                if (failed[0]) {
                    return;
                }

                String eventId = eventDoc.getId();

                getEntrantStatus(eventId, entrantId, status -> {
                    if (failed[0]) {
                        return;
                    }

                    if (status == null) {
                        if (remainingEvents.decrementAndGet() == 0) {
                            tryFinish.run();
                        }
                        return;
                    }

                    deleteEntryByStatus(eventId, entrantId, status, unused -> {
                        if (failed[0]) {
                            return;
                        }

                        if (remainingEvents.decrementAndGet() == 0) {
                            tryFinish.run();
                        }

                    }, e -> {
                        if (!failed[0]) {
                            failed[0] = true;
                            onFailure.onFailure(e);
                        }

                    });
                    }, e -> {
                        if (!failed[0]) {
                            failed[0] = true;
                            onFailure.onFailure(e);
                        }

                    }
                );
            }
        }).addOnFailureListener(onFailure);
    }
}
