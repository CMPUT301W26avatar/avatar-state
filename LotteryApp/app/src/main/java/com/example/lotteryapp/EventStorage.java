package com.example.lotteryapp;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
    Connection between activities and database (FirebaseService).
        Call via ServiceLocator
        Handles read/writes into the Firebase database for Event items.
 */
public class EventStorage {

    private final FirebaseFirestore db;

    public EventStorage(FirebaseFirestore db) {
        this.db = db;
    }

    private DocumentReference eventDoc(String eventId) {
        return db.collection("events").document(eventId);
    }

    // create/update Events
    //      Transaction ensures createdAt is only set once
    public void upsertEvent(Event event) {
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

    // upsertsEvent: helper - maps document fields into a dict.
    private Map<String, Object> eventToMap(Event event) {
        Map<String, Object> data = new HashMap<>();
        data.put("eventId", event.getEventId());
        data.put("organizerId", event.getOrganizerId());
        data.put("title", event.getTitle());
        data.put("status", event.getStatus() != null ? event.getStatus().name() : Event.EventStatus.OPEN.name());
        data.put("location", event.getLocation());
        data.put("eventCapacity", event.getEventCapacity());
        data.put("waitlistCapacity", event.getWaitlistCapacity());
        data.put("posterUrl", event.getPosterUrl());
        data.put("description", event.getDescription());
        data.put("eventDateMs", event.getEventDateMs());
        data.put("regStartMs", event.getRegStartMs());
        data.put("regEndMs", event.getRegEndMs());
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());
        return data;
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

    // getEvent: helper - converts a Firebase document into an Event object
    private Event documentToEvent(DocumentSnapshot doc) {
        String organizerId = doc.getString("organizerId");
        String rawStatus = doc.getString("status");

        Event.EventStatus status = Event.EventStatus.OPEN;
        if (rawStatus != null) {
            try { status = Event.EventStatus.valueOf(rawStatus); }
            catch (IllegalArgumentException ignored) {}
        }

        int cap = doc.getLong("eventCapacity").intValue();

        Event event = new Event(organizerId, status, cap);

        event.eventId = doc.getId();
        event.setEventCapacity(cap);
        event.setTitle(doc.getString("title"));
        event.setPosterUrl(doc.getString("posterUrl"));
        event.setLocation(doc.getString("location"));

        Long waitlistCap = doc.getLong("waitlistCapacity");
        event.setWaitlistCapacity(waitlistCap == null ? null : waitlistCap.intValue());

        event.setDescription(doc.getString("description"));

        Long eventDateMs = doc.getLong("eventDateMs");
        if (eventDateMs != null) {
            event.setEventDateMs(eventDateMs);
        }

        Long regStartMs = doc.getLong("regStartMs");
        if (regStartMs != null) {
            event.setRegStartMs(regStartMs);
        }

        Long regEndMs = doc.getLong("regEndMs");
        if (regEndMs != null) {
            event.setRegEndMs(regEndMs);
        }

        return event;
    }

    // delete event from Firebase

    public void deleteEvent(String eventId) {
        eventDoc(eventId).delete();
    }

    // return all events from Firebase hosted by organizerId
    public void getEventsByOrganizer(
            String organizerId,
            OnSuccessListener<List<Event>> onSuccess,
            OnFailureListener onFailure
    ) {
        db.collection("events")
                .whereEqualTo("organizerId", organizerId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Event> events = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        events.add(documentToEvent(doc));
                    }
                    onSuccess.onSuccess(events);
                })
                .addOnFailureListener(onFailure);
    }


}