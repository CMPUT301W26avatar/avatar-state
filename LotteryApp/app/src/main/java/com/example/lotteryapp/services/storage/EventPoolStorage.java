package com.example.lotteryapp.services.storage;

import com.example.lotteryapp.models.Entrant;
import com.example.lotteryapp.models.Event;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;

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

    private DocumentReference entryDoc(String eventId, String entrantId) {
        return db.collection("events")
                .document(eventId)
                .collection("entries")
                .document(entrantId);
    }

    // for join events from events details page
    //      creates a new entrant in the database
    //      updates the Event the entrant is in as required
    //      uses a db Transaction to prevent enrollment race conditions between to Entrants
    public void enrollInEvent(
            String eventId,
            Entrant entrant,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        DocumentReference eventRef = db.collection("events").document(eventId);
        DocumentReference entrantRef = entryDoc(eventId, entrant.getEntrantId());

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot eventSnap = transaction.get(eventRef);
            DocumentSnapshot entrantSnap = transaction.get(entrantRef);

            if (!eventSnap.exists()) {
                throw new IllegalStateException("Event does not exist");
            }

            int enrolled = eventSnap.getLong("enrolledCount").intValue();
            int capacity = eventSnap.getLong("eventCapacity").intValue();

            // enforce no double enrollments
            if (entrantSnap.exists()) {
                throw new IllegalStateException("Entrant already enrolled");
            }

            // enforce no overenrollments
            if (enrolled >= capacity) {
                throw new IllegalStateException("Event is already full");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("entrantId", entrant.getEntrantId());
            data.put("eventId", eventId);
            data.put("status", Entrant.EntrantStatus.ENROLLED.name());
            data.put("joinedAt", FieldValue.serverTimestamp());
            data.put("updatedAt", FieldValue.serverTimestamp());

            transaction.set(entrantRef, data);

            // update Event details inside transaction
                    int enrolledUpdate = enrolled + 1;

                    Map<String, Object> eventUpdates = new HashMap<>();
                    eventUpdates.put("enrolledCount", enrolledUpdate);
                    eventUpdates.put("waitlistCount", eventSnap.getLong("waitlistCount") != null
                            ? eventSnap.getLong("waitlistCount").intValue()
                            : 0);
                    eventUpdates.put(
                            "status",
                            enrolledUpdate >= capacity
                                    ? Event.EventStatus.CLOSED.name()
                                    : Event.EventStatus.OPEN.name()
                    );
                    transaction.update(eventRef, eventUpdates);
            return null;
        }).addOnSuccessListener(onSuccess)
        .addOnFailureListener(onFailure);
    }

    // for querying whether an Entrant is WAITLISTED, INVITED, ...
    public void getEntrantStatus(
            String eventId,
            String entrantId,
            OnSuccessListener<String> onSuccess,
            OnFailureListener onFailure
    ) {
        entryDoc(eventId, entrantId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        onSuccess.onSuccess(null);
                        return;
                    }
                    onSuccess.onSuccess(doc.getString("status"));
                })
                .addOnFailureListener(onFailure);
    }

    // We already have enrolled Counts, and waitlist Counts stored in Firebase
    //  Use this for queries on statuses unqeual to ENROLLED and WAITLISTED
    public void countEntrantsByStatus(String eventId, String status,
                                      OnSuccessListener<Integer> onSuccess,
                                      OnFailureListener onFailure) {
        db.collection("eventPool")
                .document(eventId)
                .collection("entrants")
                .whereEqualTo("status", status)
                .get()
                .addOnSuccessListener(querySnapshot -> onSuccess.onSuccess(querySnapshot.size()))
                .addOnFailureListener(onFailure);
    }


    // unenroll , delete in event/entries doc only
    //      deletes an existing entrant from the event/entries doc
    //      updates the Event the entrant is in as required
    //      uses a db Transaction to prevent enrollment race conditions between to Entrants
    public void deleteEntry(
            String eventId,
            String entrantId,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        DocumentReference eventRef = db.collection("events").document(eventId);
        DocumentReference entrantRef = entryDoc(eventId, entrantId);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot eventSnap = transaction.get(eventRef);
            DocumentSnapshot entrantSnap = transaction.get(entrantRef);

            if (!eventSnap.exists()) {
                throw new IllegalStateException("Event does not exist");
            }

            if (!entrantSnap.exists()) {
                throw new IllegalStateException("Entry does not exist");
            }

            Long enrolledLong = eventSnap.getLong("enrolledCount");
            Long waitlistLong = eventSnap.getLong("waitlistCount");
            Long capacityLong = eventSnap.getLong("eventCapacity");

            int enrolledCount = enrolledLong != null ? enrolledLong.intValue() : 0;
            int waitlistCount = waitlistLong != null ? waitlistLong.intValue() : 0;
            int capacity = capacityLong != null ? capacityLong.intValue() : 0;

            String status = entrantSnap.getString("status");

            if (Entrant.EntrantStatus.ENROLLED.name().equals(status)) {
                enrolledCount = Math.max(0, enrolledCount - 1);
            } else if (Entrant.EntrantStatus.WAITLISTED.name().equals(status)) {
                waitlistCount = Math.max(0, waitlistCount - 1);
            }

            transaction.delete(entrantRef);

            Map<String, Object> updates = new HashMap<>();
            updates.put("enrolledCount", enrolledCount);
            updates.put("waitlistCount", waitlistCount);
            updates.put(
                    "status",
                    enrolledCount >= capacity
                            ? Event.EventStatus.CLOSED.name()
                            : Event.EventStatus.OPEN.name()
            );

            transaction.update(eventRef, updates);

            return null;
        }).addOnSuccessListener(onSuccess)
        .addOnFailureListener(onFailure);
    }
}
