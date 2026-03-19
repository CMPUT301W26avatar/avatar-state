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

/** Database storage and retrieval layer with respect to Admins
 * Admins have they own firebase collection "admin" with subcollections "current" and "requested"
 * Call via ServiceLocator
 */

public class EventStorage {

    private final FirebaseFirestore db;

    public EventStorage(FirebaseFirestore db) {
        this.db = db;
    }

    /** firebase retrieval
     * returns an Event document by the parameter eventId
     * Synchronous
     */
    private DocumentReference eventDoc(String eventId) {
        return db.collection("events").document(eventId);
    }

    /** firebase storage
     * Takes in an Event as a parameter and creates/upserts it into the database
     * Transaction ensures the createdAt value is only set once.
     * Synchronous
     */
    public void upsertEvent(
            Event event,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
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
                }).addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /** firebase storage
     * Maps the attributes of an event into a Map to pass into the database (ref.update)
     * Synchronous
     */
    private Map<String, Object> eventToMap(Event event) {
        Map<String, Object> data = new HashMap<>();
        data.put("eventId", event.getEventId());
        data.put("organizerId", event.getOrganizerId());
        data.put("title", event.getTitle());
        data.put("status", event.getStatus().name());
        data.put("hasDrawnLottery", event.hasDrawnLottery());
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


    /** firebase retrieval
     * Returns a single Event from the database as a Document inside of OnSuccess
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns Document pertaining to Event, asynchronously
     */
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

    /** firebase retrieval helper
     * Returns a single Event from the parameter doc (database DocumentSnapshot)
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns Event outlined in the DocumentSnapshot, asynchronously
     */
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
            try {
                //"OPEN" -> "REG_OPEN" to prevent crashes
                if ("OPEN".equals(rawStatus)) {
                    event.setStatus(Event.EventStatus.REG_OPEN);
                } else {
                    event.setStatus(Event.EventStatus.valueOf(rawStatus));
                }
            } catch (IllegalArgumentException e) {
                event.setStatus(Event.EventStatus.REG_UPCOMING);
            }
        }

        Boolean hasDrawnLottery = doc.getBoolean("hasDrawnLottery");
        event.setHasDrawnLottery(hasDrawnLottery != null && hasDrawnLottery);

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

    /** firebase modify
     * Deletes a single document in firebase keyed by the parameter eventId
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns nothing, asynchronously or synchronously
     */
    public void deleteEvent(String eventId) {
        eventDoc(eventId).delete();
    }

    /** firebase boolean retrieval
     * Returns true or false for if the current user is the organizer for any event
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns bool, asynchronously
     */
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


    /** firebase retrieval
     * Returns all events by the organizerId given to the method as a parameter
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns list of Events, asynchronously
     */
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

    /** firebase retrieval
     * Returns all events in the database
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns list of Events, asynchronously
     */
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

    /** firebase modify
     * Deletes all events in the database pertaining to the organizerId given as a parameter
     * for use inside of cascadeDeleteUserProfile
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns nothing, asynchronously or synchronously
     */
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

    /** firebase retrieval
     * Returns all REG_OPEN events in the database
     *      REG_OPEN: registration window open
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns list of Events, asynchronously
     */
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

    /** firebase retrieval
     * Returns all REG_CLOSED events in the database
     *      REG_CLOSED: registration end date passed
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns list of Events, asynchronously
     */
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

    /** firebase retrieval
     * Returns all REG_FULL events in the database
     *      REG_FULL: registration window open, but waitlist is full
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns list of Events, asynchronously
     */
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

    /** firebase retrieval
     * Returns all REG_UPCOMING events in the database
     *      REG_UPCOMING: registration start date upcoming
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns list of Events, asynchronously
     */
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