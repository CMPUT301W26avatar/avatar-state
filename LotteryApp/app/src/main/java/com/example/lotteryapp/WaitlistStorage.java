package com.example.lotteryapp;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages waitlist membership for an event.
 * Firestore path: events/{eventId}/entries/{entrantId}  where status == WAITLISTED
 *
 * US 01.01.01 - entrant joins waitlist
 * US 01.01.02 - entrant leaves waitlist
 * US 02.02.01 - organizer views waitlist
 */
public class WaitlistStorage {

    private final FirebaseFirestore db;

    public WaitlistStorage(FirebaseFirestore db) {
        this.db = db;
    }

    static DocumentReference entryDoc(FirebaseFirestore db, String eventId, String entrantId) {
        return db.collection("events")
                .document(eventId)
                .collection("entries")
                .document(entrantId);
    }

    /** US 01.01.01 */
    public void joinWaitlist(
            String eventId,
            Entrant entrant,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("entrantId", entrant.getEntrantId());
        data.put("eventId", eventId);
        data.put("status", Entrant.EntrantStatus.WAITLISTED.name());
        data.put("joinedAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());

        entryDoc(db, eventId, entrant.getEntrantId())
                .set(data)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }


    /** US 01.01.02 */
    public void leaveWaitlist(
            String eventId,
            Entrant entrant,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        entryDoc(db, eventId, entrant.getEntrantId())
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }


    /** US 02.02.01 — all WAITLISTED entrants, ordered by join time */
    public void listWaitlistedEntrants(
            String eventId,
            OnSuccessListener<List<Entrant>> onSuccess,
            OnFailureListener onFailure
    ) {
        db.collection("events")
                .document(eventId)
                .collection("entries")
                .whereEqualTo("status", Entrant.EntrantStatus.WAITLISTED.name())
                .orderBy("joinedAt", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    List<Entrant> out = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : qs) {
                        out.add(documentToEntrant(doc, eventId));
                    }
                    onSuccess.onSuccess(out);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Returns events that have a waitlist enabled and are within their
     * registration window. Capacity check is done client-side.
     */
    public void listOpenWaitlistEvents(
            OnSuccessListener<List<Event>> onSuccess,
            OnFailureListener onFailure
    ) {
        Timestamp now = Timestamp.now();
        db.collection("events")
                .whereEqualTo("status", Event.EventStatus.OPEN.name())
                .whereNotEqualTo("waitlistCapacity", null)
                .whereLessThanOrEqualTo("regStart", now)
                .whereGreaterThanOrEqualTo("regEnd", now)
                .get()
                .addOnSuccessListener(qs -> {
                    List<Event> out = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : qs) {
                        out.add(documentToEvent(doc));
                    }
                    onSuccess.onSuccess(out);
                })
                .addOnFailureListener(onFailure);
    }

    // ─── Shared document mappers ─────────────────────────────────────────────

    static Entrant documentToEntrant(QueryDocumentSnapshot doc, String eventId) {
        String entrantId = doc.getId();
        String rawStatus = doc.getString("status");
        Entrant.EntrantStatus status = Entrant.EntrantStatus.WAITLISTED;
        if (rawStatus != null) {
            try { status = Entrant.EntrantStatus.valueOf(rawStatus); }
            catch (IllegalArgumentException ignored) {}
        }
        return new Entrant(entrantId, eventId, status);
    }

    static Event documentToEvent(QueryDocumentSnapshot doc) {
        String organizerId = doc.getString("organizerId");
        Long cap = doc.getLong("capacity");

        String rawStatus = doc.getString("status");
        Event.EventStatus status = Event.EventStatus.OPEN;
        if (rawStatus != null) {
            try { status = Event.EventStatus.valueOf(rawStatus); }
            catch (IllegalArgumentException ignored) {}
        }

        Event e = new Event(organizerId, status, cap != null ? cap.intValue() : 1);

        // preserve Firestore document id as the eventId
        e.eventId = doc.getId();
        e.setTitle(doc.getString("title"));
        e.setPosterUrl(doc.getString("posterUrl"));
        e.setRegStartMs(doc.getLong("regStartMs"));
        e.setRegEndMs(doc.getLong("regEndMs"));

        Long wlCap = doc.getLong("waitlistCapacity");
        e.setWaitlistCapacity(wlCap != null ? wlCap.intValue() : null);

        return e;
    }
}