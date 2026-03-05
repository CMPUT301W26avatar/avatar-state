package com.example.lotteryapp;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;

public class EventStorage {

    private final FirebaseFirestore db;

    public EventStorage(FirebaseFirestore db) {
        this.db = db;
    }

    private DocumentReference eventDoc(String eventId) {
        return db.collection("events").document(eventId);
    }



    // Transaction ensures createdAt is only set once
    public void createEvent(Event event) {
        final DocumentReference ref = eventDoc(event.getEventId());
        db.runTransaction(new Transaction.Function<Void>() {
            @Override
            public Void apply(@NonNull Transaction transaction) throws FirebaseFirestoreException {
                DocumentSnapshot snapshot = transaction.get(ref);
                if (!snapshot.exists()) {
                    transaction.set(ref, eventToMap(event));
                } else {
                    transaction.update(ref, "updatedAt", FieldValue.serverTimestamp());
                }
                return null;
            }
        });
    }

    // read event from Firebase
    public void getEvent(
            String eventId,
            OnSuccessListener<Event> onSuccess,
            OnFailureListener onFailure
    ) {
        eventDoc(eventId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        onFailure.onFailure(new Exception("Event not found: " + eventId));
                        return;
                    }
                    onSuccess.onSuccess(documentToEvent(snapshot));
                })
                .addOnFailureListener(onFailure);
    }

    // updateEvent in Firebase

    public void updateEvent(
            String eventId,
            Event.EventStatus status,
            Integer capacity,
            Integer waitlistCapacity,
            String posterUrl
    ) {
        Map<String, Object> update = new HashMap<>();

        if (status != null) update.put("status", status.name());
        if (capacity != null) update.put("capacity", capacity);
        if (posterUrl != null) update.put("posterUrl", posterUrl);

        // null is valid — disables waitlist
        update.put("waitlistCapacity", waitlistCapacity);
        update.put("updatedAt", FieldValue.serverTimestamp());

        eventDoc(eventId).set(update, SetOptions.merge());
    }

    // delete event from Firebase

    public void deleteEvent(String eventId) {
        eventDoc(eventId).delete();
    }

    // helper - maps document fields into a dict.

    private Map<String, Object> eventToMap(Event event) {
        Map<String, Object> data = new HashMap<>();
        data.put("eventId", event.getEventId());
        data.put("organizerId", event.getOrganizerId());
        data.put("title", event.getTitle());
        data.put("status", event.getStatus() != null ? event.getStatus().name() : Event.EventStatus.OPEN.name());
        data.put("capacity", event.getCapacity());
        data.put("waitlistCapacity", event.getWaitlistCapacity());
        data.put("posterUrl", event.getPosterUrl());
        data.put("regStart", timestampToFirebase(event.getRegStart()));
        data.put("regEnd", timestampToFirebase(event.getRegEnd()));
        data.put("drawTime", timestampToFirebase(event.getDrawTime()));
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());
        return data;
    }

    // helper - converts a Firebase document into an Event object
    private Event documentToEvent(DocumentSnapshot doc) {
        String organizerId = doc.getString("organizerId");
        Long cap = doc.getLong("capacity");
        String rawStatus = doc.getString("status");

        Event.EventStatus status = Event.EventStatus.OPEN;
        if (rawStatus != null) {
            try { status = Event.EventStatus.valueOf(rawStatus); }
            catch (IllegalArgumentException ignored) {}
        }

        Event event = new Event(organizerId, status, cap != null ? cap.intValue() : 1);

        event.eventId = doc.getId();
        event.setTitle(doc.getString("title"));
        event.setPosterUrl(doc.getString("posterUrl"));
        event.setWaitlistCapacity(doc.getLong("waitlistCapacity") != null
                ? doc.getLong("waitlistCapacity").intValue() : null);
        event.setRegStart(doc.getTimestamp("regStart") != null
                ? new java.sql.Timestamp(doc.getTimestamp("regStart").toDate().getTime()) : null);
        event.setRegEnd(doc.getTimestamp("regEnd") != null
                ? new java.sql.Timestamp(doc.getTimestamp("regEnd").toDate().getTime()) : null);
        event.setDrawTime(doc.getTimestamp("drawTime") != null
                ? new java.sql.Timestamp(doc.getTimestamp("drawTime").toDate().getTime()) : null);

        return event;
    }

    // converts a timestamp into the proper type that Firebase expects
    private com.google.firebase.Timestamp timestampToFirebase(java.sql.Timestamp ts) {
        if (ts == null) return null;
        return new com.google.firebase.Timestamp(new java.util.Date(ts.getTime()));
    }
}