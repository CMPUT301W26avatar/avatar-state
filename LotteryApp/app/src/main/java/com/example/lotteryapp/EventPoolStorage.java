package com.example.lotteryapp;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private static Event documentToEvent(QueryDocumentSnapshot doc) {

        String organizerId = doc.getString("organizerId");
        String statusStr = doc.getString("status");

        Event.EventStatus status = Event.EventStatus.valueOf(statusStr);

        Long capacityLong = doc.getLong("capacity");
        int capacity = capacityLong != null ? capacityLong.intValue() : 0;

        Event event = new Event(organizerId, status, capacity);
        event.eventId = doc.getId();

        event.setTitle(doc.getString("title"));

        Timestamp regStart = doc.getTimestamp("regStart");
        if (regStart != null) event.setRegStart((java.sql.Timestamp) regStart.toDate());

        Timestamp regEnd = doc.getTimestamp("regEnd");
        if (regEnd != null) event.setRegEnd((java.sql.Timestamp) regEnd.toDate());

        Timestamp drawTime = doc.getTimestamp("drawTime");
        if (drawTime != null) event.setDrawTime((java.sql.Timestamp) drawTime.toDate());

        Boolean waitlist = doc.getBoolean("waitlistEnabled");
        /* set waitlist here */

        Long waitlistCap = doc.getLong("waitlistCapacity");
        if (waitlistCap != null) {
            event.setWaitlistCapacity(waitlistCap.intValue());
        }

        return event;
    }

    // ─────────────────────────────────────────────────────────────
    // Enrollment
    // ─────────────────────────────────────────────────────────────

    public void enrollInEvent(
            String eventId,
            Entrant entrant,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {

        Map<String, Object> data = new HashMap<>();
        data.put("entrantId", entrant.getEntrantId());
        data.put("eventId", eventId);
        data.put("status", Entrant.EntrantStatus.ENROLLED.name());
        data.put("joinedAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());

        entryDoc(eventId, entrant.getEntrantId())
                .set(data)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // ─────────────────────────────────────────────────────────────
    // Count entrants
    // ─────────────────────────────────────────────────────────────

    public void countEntrants(
            String eventId,
            OnSuccessListener<Integer> onSuccess,
            OnFailureListener onFailure
    ) {
        db.collection("events")
                .document(eventId)
                .collection("entries")
                .get()
                .addOnSuccessListener(qs -> onSuccess.onSuccess(qs.size()))
                .addOnFailureListener(onFailure);
    }

    // ─────────────────────────────────────────────────────────────
    // Get entrant status
    // ─────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────
    // Delete entry (unenroll)
    // ─────────────────────────────────────────────────────────────

    public void deleteEntry(
            String eventId,
            String entrantId,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        entryDoc(eventId, entrantId)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // ─────────────────────────────────────────────────────────────
    // List open events
    // ─────────────────────────────────────────────────────────────

    public void listOpenEvents(
            OnSuccessListener<List<Event>> onSuccess,
            OnFailureListener onFailure
    ) {

        db.collection("events")
                .whereEqualTo("status", Event.EventStatus.OPEN.name())
                .get()
                .addOnSuccessListener(qs -> {

                    List<Event> events = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : qs) {
                        events.add(documentToEvent(doc));
                    }

                    onSuccess.onSuccess(events);
                })
                .addOnFailureListener(onFailure);
    }
}