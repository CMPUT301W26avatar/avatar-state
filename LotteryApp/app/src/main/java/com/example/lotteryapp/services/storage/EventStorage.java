package com.example.lotteryapp.services.storage;

import androidx.annotation.NonNull;

import com.example.lotteryapp.models.Event;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

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
                Map<String, Object> data = eventToMap(event);

                if (!snapshot.exists()) {
                    transaction.set(ref, data);
                } else {
                    data.remove("createdAt");
                    data.put("updatedAt", FieldValue.serverTimestamp());
                    transaction.update(ref, data);
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
        data.put("status", event.getStatus().name());
        data.put("eventCapacity", event.getEventCapacity());
        data.put("waitlistCapacity", event.getWaitlistCapacity());
        data.put("enrolledCount", event.getEnrolledCount());
        data.put("waitlistCount", event.getWaitlistCount());
        data.put("invitationCount", event.getInvitationCount());
        data.put("criteriaGuidelines", event.getCriteriaGuidelines());
        data.put("posterUrl", event.getPosterUrl());
        data.put("eventDateMs", event.getEventDateMs());
        data.put("regStartMs", event.getRegStartMs());
        data.put("regEndMs", event.getRegEndMs());
        data.put("location", event.getLocation());
        data.put("createdAt", FieldValue.serverTimestamp()); // currently no use case - remove for part 4 if still not used
        data.put("updatedAt", FieldValue.serverTimestamp()); // currently no use case - remove for part 4 if still not used
        data.put("description", event.getDescription());
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

        Long eventCapLong = doc.getLong("eventCapacity");
        int eventCap = eventCapLong != null ? eventCapLong.intValue() : 1;

        Long waitlistCapLong = doc.getLong("waitlistCapacity");
        int waitlistCap = waitlistCapLong != null ? waitlistCapLong.intValue() : 1;

        Event event = new Event(organizerId, eventCap, waitlistCap);

        event.setEventId(doc.getId());
        event.setEventCapacity(eventCap);
        event.setWaitlistCapacity(waitlistCap);

        String rawStatus = doc.getString("status");
        if (rawStatus != null) {
            event.setStatus(Event.EventStatus.valueOf(rawStatus));
        }

        event.setTitle(doc.getString("title"));
        event.setPosterUrl(doc.getString("posterUrl"));
        event.setLocation(doc.getString("location"));

        Long enrolledCount = doc.getLong("enrolledCount");
        event.setEnrolledCount(enrolledCount == null ? 0 : enrolledCount.intValue());

        Long waitlistCount = doc.getLong("waitlistCount");
        event.setWaitlistCount(waitlistCount == null ? 0 : waitlistCount.intValue());

        Long invitationCount = doc.getLong("invitationCount");
        event.setInvitationCount(invitationCount == null ? 0 : invitationCount.intValue());

        event.setCriteriaGuidelines(doc.getString("criteriaGuidelines"));
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
    public void isUserEventOrganizer(String uuid,
                                     OnSuccessListener<Boolean> onSuccess,
                                     OnFailureListener onFailure
    ) {
        db.collection("events")
                .whereEqualTo("organizerId", uuid)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> onSuccess.onSuccess(!querySnapshot.isEmpty()))
                .addOnFailureListener(onFailure);
    }


    // return all events from Firebase hosted by organizerId
    public void getEventsByOrganizer(String organizerId, OnSuccessListener<List<Event>> onSuccess, OnFailureListener onFailure) {
        db.collection("events").whereEqualTo("organizerId", organizerId).get()
                .addOnSuccessListener(querySnapshot -> {List<Event> events = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        events.add(documentToEvent(doc));
                    }
                    onSuccess.onSuccess(events);
                })
                .addOnFailureListener(onFailure);
    }

    public void getAllEvents(OnSuccessListener<List<Event>> onSuccess, OnFailureListener onFailure) {
        db.collection("events").get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Event> events = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        events.add(documentToEvent(doc));
                    }
                    onSuccess.onSuccess(events);
                })
                .addOnFailureListener(onFailure);
    }
    public void delAllOrganizerEvents(String organizerId,
                                      OnSuccessListener<Void> onSuccess,
                                      OnFailureListener onFailure
    ) {
        db.collection("events")
                .whereEqualTo("organizerId", organizerId)
                .get()
                .addOnSuccessListener(eventSnapshot -> {
                    if (eventSnapshot.isEmpty()) { // no hosted events found
                        onSuccess.onSuccess(null);
                        return;
                    }
                    // batch delete hosted event docs
                    WriteBatch batch = db.batch(); // batch delete hosted event docs
                    for (QueryDocumentSnapshot eventDoc : eventSnapshot) {
                        batch.delete(eventDoc.getReference());
                    }
                    batch.commit().addOnSuccessListener(onSuccess).addOnFailureListener(onFailure);
                })
                .addOnFailureListener(onFailure); // query failure
    }

    public void listEventsRegOpen(Integer limit, OnSuccessListener<List<Event>> onSuccess, OnFailureListener onFailure) {
        // define query
        Query query = db.collection("events")
                .whereEqualTo("status", Event.EventStatus.REG_OPEN.name());

        // optional limit to how many events to return
        if (limit != null && limit > 0) {
            query = query.limit(limit);
        }

        // run the query and return the results on success
        query.get().addOnSuccessListener(qs -> {
            List<Event> events = new ArrayList<>();
            for (QueryDocumentSnapshot doc : qs) {
                events.add(documentToEvent(doc));
            }
            onSuccess.onSuccess(events);
        }).addOnFailureListener(onFailure);
    }

    public void listEventsRegClosed(Integer limit, OnSuccessListener<List<Event>> onSuccess, OnFailureListener onFailure) {
        Query query = db.collection("events")
                .whereEqualTo("status", Event.EventStatus.REG_CLOSED.name());

        if (limit != null && limit > 0) {
            query = query.limit(limit);
        }

        query.get().addOnSuccessListener(qs -> {
            List<Event> events = new ArrayList<>();

            android.util.Log.d("EventStorage", "Closed query count = " + qs.size());

            for (QueryDocumentSnapshot doc : qs) {
                android.util.Log.d("EventStorage",
                        "doc=" + doc.getId() + ", status=" + doc.get("status"));
                events.add(documentToEvent(doc));
            }

            onSuccess.onSuccess(events);
        }).addOnFailureListener(onFailure);
    }

    public void listEventsRegFull(Integer limit, OnSuccessListener<List<Event>> onSuccess, OnFailureListener onFailure) {
        Query query = db.collection("events")
                .whereEqualTo("status", Event.EventStatus.REG_FULL.name());

        if (limit != null && limit > 0) {
            query = query.limit(limit);
        }

        query.get().addOnSuccessListener(qs -> {
            List<Event> events = new ArrayList<>();

            android.util.Log.d("EventStorage", "Closed query count = " + qs.size());

            for (QueryDocumentSnapshot doc : qs) {
                android.util.Log.d("EventStorage",
                        "doc=" + doc.getId() + ", status=" + doc.get("status"));
                events.add(documentToEvent(doc));
            }

            onSuccess.onSuccess(events);
        }).addOnFailureListener(onFailure);
    }

    public void listEventsRegUpcoming(Integer limit, OnSuccessListener<List<Event>> onSuccess, OnFailureListener onFailure) {
        Query query = db.collection("events")
                .whereEqualTo("status", Event.EventStatus.REG_UPCOMING.name());

        if (limit != null && limit > 0) {
            query = query.limit(limit);
        }

        query.get().addOnSuccessListener(qs -> {
            List<Event> events = new ArrayList<>();

            android.util.Log.d("EventStorage", "Closed query count = " + qs.size());

            for (QueryDocumentSnapshot doc : qs) {
                android.util.Log.d("EventStorage",
                        "doc=" + doc.getId() + ", status=" + doc.get("status"));
                events.add(documentToEvent(doc));
            }

            onSuccess.onSuccess(events);
        }).addOnFailureListener(onFailure);
    }


}