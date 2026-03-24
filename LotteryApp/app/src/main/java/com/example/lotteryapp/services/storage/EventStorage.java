package com.example.lotteryapp.services.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.models.EventAddress;
import com.example.lotteryapp.models.EventJoinedMap;
import com.example.lotteryapp.models.User;
import com.example.lotteryapp.models.UserAddress;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Database storage and retrieval layer with respect to Events
 * Events have their own firebase collection "events"
 * Event addresses are stored under:
 *   /events/{eventId}/geo/address
 * Call via ServiceLocator
 */
public class EventStorage {

    private final FirebaseFirestore db;

    public EventStorage(FirebaseFirestore db) {
        this.db = db;
    }

    /** firebase retrieval
     * returns an Event document by the primary key eventId
     * Synchronous
     */
    private DocumentReference eventDoc(String eventId) {
        return db.collection("events").document(eventId);
    }

    /** firebase retrieval
     * returns an EventAddress document by the primary key eventId
     * Stored under /events/{eventId}/geo/{eventId}
     * Synchronous
     */
    private DocumentReference eventAddressDoc(String eventId) {
        return eventDoc(eventId)
                .collection("geo")
                .document("address");
    }

    /**
     * returns an EventAddress document by the composite key eventId+userId.
     * Stored under /events/{eventId}/joined_map/{uid}
     * Synchronous
     */
    private DocumentReference eventJoinedMapDoc(
            @NonNull String eventId,
            @NonNull String uid
    ) {
        return eventDoc(eventId)
                .collection("joined_map")
                .document(uid);
    }

    /**
     * returns a User document by the composite key eventId+userId from the coorganizer_invites subcollection
     * Stored under /events/{eventId}/coorganizer_invites/{uid}
     * Synchronous
     */
    private DocumentReference eventCoorganizerInviteDoc(
            @NonNull String eventId,
            @NonNull String uid
    ) {
        return eventDoc(eventId)
                .collection("coorganizer_invites")
                .document(uid);
    }

    /**
     * returns a User document in a subcollection from the uuid value
     * Synchronous
     */
    private DocumentReference userDoc(@NonNull String uid) {
        return db.collection("users").document(uid);
    }

    /**
     * Returns the comments subcollection for an event.
     * Stored under /events/{eventId}/comments
     */
    private CollectionReference eventCommentsCollection(@NonNull String eventId) {
        return eventDoc(eventId).collection("comments");
    }

    /**
     * Returns the reported comments subcollection for an event.
     * Stored under /events/{eventId}/reported_comments
     */
    private CollectionReference eventReportedCommentsCollection(@NonNull String eventId) {
        return eventDoc(eventId).collection("reported_comments");
    }

    /** firebase storage
     * Takes in an Event as a parameter and creates/upserts it into the database.
     * Transaction ensures the createdAt value is only set once.
     * Address is stored separately under /geo/address and does not affect query success.
     * Asynchronous
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

    /**
     * firebase modify
     * Sets or updates the EventAddress fields for an event
     * Optional: if address is null the address document is removed
     */
    public void setEventAddress(
            String eventId,
            @Nullable EventAddress address,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        if (address == null) {
            eventAddressDoc(eventId)
                    .delete()
                    .addOnSuccessListener(onSuccess)
                    .addOnFailureListener(onFailure);
            return;
        }

        Map<String, Object> data = eventAddressToMap(address);
        data.put("updatedAt", FieldValue.serverTimestamp());

        eventAddressDoc(eventId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Creates or updates the joined_map document for a user for an event.
     * The stored address is a snapshot of the user's chosen address at join time.
     */
    public void setEventJoinedMap(
            @NonNull String eventId,
            @NonNull EventJoinedMap joinedMap,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        UserAddress joinedFrom = joinedMap.getJoinedFrom();

        if (joinedFrom == null) {
            onFailure.onFailure(new IllegalArgumentException("joinedFrom address required"));
            return;
        }

        if (joinedFrom.getUid() == null || joinedFrom.getUid().trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("joinedFrom uid required"));
            return;
        }

        Map<String, Object> data = eventJoinedMapToMap(joinedMap);
        data.put("updatedAt", FieldValue.serverTimestamp());

        eventJoinedMapDoc(eventId, joinedFrom.getUid())
                .set(data, SetOptions.merge())
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Removes the joined_map document for a user for an event.
     */
    public void removeEventJoinedMap(
            @NonNull String eventId,
            @NonNull String uid,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        eventJoinedMapDoc(eventId, uid)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Returns all joined_map entries for an event.
     */
    public void getEventJoinedMapEntries(
            @NonNull String eventId,
            OnSuccessListener<List<EventJoinedMap>> onSuccess,
            OnFailureListener onFailure
    ) {
        eventDoc(eventId)
                .collection("joined_map")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<EventJoinedMap> entries = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        EventJoinedMap entry = documentToEventJoinedMap(doc, eventId);
                        if (entry != null) {
                            entries.add(entry);
                        }
                    }
                    onSuccess.onSuccess(entries);
                })
                .addOnFailureListener(onFailure);
    }

    /** firebase storage
     * Maps the attributes of an event into a Map to pass into the database
     * Synchronous
     */
    private Map<String, Object> eventToMap(Event event) {
        event.generateKeywords(); // Generate keywords before saving
        Map<String, Object> data = new HashMap<>();
        data.put("eventId", event.getEventId());
        data.put("organizerId", event.getOrganizerId());
        data.put("coOrganizerIds", event.getCoOrganizerIds());
        data.put("title", event.getTitle());
        data.put("status", event.getStatus().name());
        data.put("hasDrawnLottery", event.hasDrawnLottery());
        data.put("hasGeoConstraint", event.hasGeoConstraint());
        data.put("privateEvent", event.isPrivateEvent());
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
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());
        data.put("description", event.getDescription());
        data.put("keywords", event.getKeywords()); // Save keywords to Firestore
        return data;
    }

    /** firebase storage
     * Maps EventAddress into a Map to pass into the database
     * Synchronous
     */
    private Map<String, Object> eventAddressToMap(EventAddress eventAddress) {
        Map<String, Object> data = new HashMap<>();
        data.put("eventId", eventAddress.getEventId());
        data.put("location", eventAddress.getLocation());
        data.put("latitude", eventAddress.getLatitude());
        data.put("longitude", eventAddress.getLongitude());
        data.put("radiusKm", eventAddress.getRadiusKm());
        return data;
    }

    /**
     * Maps EventJoinedMap into a flat Firestore payload.
     * UserAddress fields are stored as a snapshot at join time.
     */
    private Map<String, Object> eventJoinedMapToMap(EventJoinedMap joinedMap) {
        Map<String, Object> data = new HashMap<>();
        UserAddress joinedFrom = joinedMap.getJoinedFrom();

        data.put("eventId", joinedMap.getEventId());
        data.put("uid", joinedFrom.getUid());
        data.put("location", joinedFrom.getLocation());
        data.put("latitude", joinedFrom.getLatitude());
        data.put("longitude", joinedFrom.getLongitude());

        if (joinedMap.getJoinedAtMs() != null) {
            data.put("joinedAtMs", joinedMap.getJoinedAtMs());
        } else {
            data.put("joinedAtMs", System.currentTimeMillis());
        }

        data.put("joinedAt", FieldValue.serverTimestamp());
        return data;
    }

    /** firebase retrieval
     * Returns a single Event from the database
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * Address hydration is best-effort and not cause the main event query to fail.
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

                    Event event = documentToEvent(snapshot);

                    eventAddressDoc(eventId)
                            .get()
                            .addOnSuccessListener(addressSnapshot -> {
                                if (addressSnapshot.exists()) {
                                    event.setAddress(documentToEventAddress(addressSnapshot, eventId));
                                }
                                onSuccess.onSuccess(event);
                            })
                            .addOnFailureListener(e -> onSuccess.onSuccess(event));
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Returns only the event address subdocument for the given event.
     * Returns null when the event has no geo/address document.
     */
    public void getEventAddress(
            @NonNull String eventId,
            OnSuccessListener<EventAddress> onSuccess,
            OnFailureListener onFailure
    ) {
        eventAddressDoc(eventId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        onSuccess.onSuccess(null);
                        return;
                    }
                    onSuccess.onSuccess(documentToEventAddress(snapshot, eventId));
                })
                .addOnFailureListener(onFailure);
    }

    /** firebase retrieval helper
     * Returns a single Event from the parameter doc (database DocumentSnapshot)
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

        List<String> coOrganizerIds = (List<String>) doc.get("coOrganizerIds");
        event.setCoOrganizerIds(coOrganizerIds);

        String rawStatus = doc.getString("status");
        if (rawStatus != null) {
            try {
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

        Boolean hasGeoConstraint = doc.getBoolean("hasGeoConstraint");
        event.setHasGeoConstraint(hasGeoConstraint != null && hasGeoConstraint);

        Boolean privateEvent = doc.getBoolean("privateEvent");
        event.setPrivateEvent(privateEvent != null && privateEvent);

        event.setTitle(doc.getString("title"));
        event.setPosterUrl(doc.getString("posterUrl"));

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

    /** firebase retrieval helper
     * Returns a single EventAddress from the address doc
     */
    private EventAddress documentToEventAddress(DocumentSnapshot doc, String eventId) {
        String location = doc.getString("location");
        Double latitude = doc.getDouble("latitude");
        Double longitude = doc.getDouble("longitude");

        EventAddress address = new EventAddress(eventId, location, latitude, longitude);

        Long radiusKm = doc.getLong("radiusKm");
        if (radiusKm != null) {
            address.setRadiusKm(radiusKm.intValue());
        }

        return address;
    }

    /**
     * Builds one EventJoinedMap from a joined_map document.
     */
    @Nullable
    private EventJoinedMap documentToEventJoinedMap(
            @NonNull DocumentSnapshot doc,
            @NonNull String eventId
    ) {
        String uid = doc.getString("uid");
        Double latitude = doc.getDouble("latitude");
        Double longitude = doc.getDouble("longitude");
        String location = doc.getString("location");
        Long joinedAtMs = doc.getLong("joinedAtMs");

        if (uid == null) {
            return null;
        }

        UserAddress joinedFrom = new UserAddress(uid, location, latitude, longitude);
        return new EventJoinedMap(eventId, joinedFrom, joinedAtMs);
    }

    /**firebase retrieval helper
     *  Best-effort address population for a list.
     *  Address read failures do not fail the overall event query.
     *      - read failures skipped by going Event by Event and performing a recursive call on
     *        index + 1 when failing
     */
    private void populateEventAddressesSafely(
            List<Event> events,
            int index,
            OnSuccessListener<List<Event>> onSuccess
    ) {
        if (index >= events.size()) {
            onSuccess.onSuccess(events);
            return;
        }

        Event event = events.get(index);
        eventAddressDoc(event.getEventId())
                .get()
                .addOnSuccessListener(addressSnapshot -> {
                    if (addressSnapshot.exists()) {
                        event.setAddress(documentToEventAddress(addressSnapshot, event.getEventId()));
                    }
                    populateEventAddressesSafely(events, index + 1, onSuccess);
                })
                .addOnFailureListener(e ->
                        populateEventAddressesSafely(events, index + 1, onSuccess)
                );
    }

    /** firebase retrieval helper
     * Runs a query, converts docs to events, then best-effort hydrates their address subdocuments.
     * Event queries should never fail because an address subdocument is missing or unreadable.
     */
    private void queryEventsWithAddresses(
            Query query,
            OnSuccessListener<List<Event>> onSuccess,
            OnFailureListener onFailure
    ) {
        query.get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Event> events = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        events.add(documentToEvent(doc));
                    }

                    if (events.isEmpty()) {
                        onSuccess.onSuccess(events);
                        return;
                    }

                    populateEventAddressesSafely(events, 0, onSuccess);
                })
                .addOnFailureListener(onFailure);
    }

    /** firebase modify
     * Deletes a single document in firebase keyed by the parameter eventId
     * Address subdocument delete is best-effort.
     */
    public void deleteEvent(String eventId) {
        eventAddressDoc(eventId).delete();
        eventDoc(eventId).delete();
    }


    public void inviteCoorganizer(
            @NonNull String eventId,
            @NonNull String organizerId,
            @NonNull String invitedUserId,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        if (eventId.trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("eventId required"));
            return;
        }

        if (organizerId.trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("organizerId required"));
            return;
        }

        if (invitedUserId.trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("invitedUserId required"));
            return;
        }

        if (organizerId.equals(invitedUserId)) {
            onFailure.onFailure(new IllegalArgumentException("Organizer cannot invite themselves"));
            return;
        }

        final DocumentReference eventRef = eventDoc(eventId);
        final DocumentReference inviteRef = eventCoorganizerInviteDoc(eventId, invitedUserId);
        final DocumentReference invitedUserRef = userDoc(invitedUserId);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
                    DocumentSnapshot eventSnapshot = transaction.get(eventRef);
                    if (!eventSnapshot.exists()) {
                        throw new FirebaseFirestoreException(
                                "Event not found",
                                FirebaseFirestoreException.Code.NOT_FOUND
                        );
                    }

                    String actualOrganizerId = eventSnapshot.getString("organizerId");
                    if (actualOrganizerId == null || !actualOrganizerId.equals(organizerId)) {
                        throw new FirebaseFirestoreException(
                                "Only the organizer can invite co-organizers",
                                FirebaseFirestoreException.Code.PERMISSION_DENIED
                        );
                    }

                    DocumentSnapshot invitedUserSnapshot = transaction.get(invitedUserRef);
                    if (!invitedUserSnapshot.exists()) {
                        throw new FirebaseFirestoreException(
                                "Invited user not found",
                                FirebaseFirestoreException.Code.NOT_FOUND
                        );
                    }

                    DocumentSnapshot inviteSnapshot = transaction.get(inviteRef);
                    if (inviteSnapshot.exists()) {
                        throw new FirebaseFirestoreException(
                                "Co-organizer invite already sent",
                                FirebaseFirestoreException.Code.ALREADY_EXISTS
                        );
                    }

                    Map<String, Object> inviteData = new HashMap<>();
                    inviteData.put("eventId", eventId);
                    inviteData.put("invitedUserId", invitedUserId);
                    inviteData.put("organizerId", organizerId);
                    inviteData.put("status", "PENDING");
                    inviteData.put("createdAt", FieldValue.serverTimestamp());
                    inviteData.put("updatedAt", FieldValue.serverTimestamp());

                    transaction.set(inviteRef, inviteData);

                    return null;
                }).addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public void acceptInviteToBeCoorganizer(
            @NonNull String eventId,
            @NonNull String invitedUserId,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        if (eventId.trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("eventId required"));
            return;
        }

        if (invitedUserId.trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("invitedUserId required"));
            return;
        }

        final DocumentReference eventRef = eventDoc(eventId);
        final DocumentReference inviteRef = eventCoorganizerInviteDoc(eventId, invitedUserId);
        final DocumentReference invitedUserRef = userDoc(invitedUserId);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
                    DocumentSnapshot eventSnapshot = transaction.get(eventRef);
                    if (!eventSnapshot.exists()) {
                        throw new FirebaseFirestoreException(
                                "Event not found",
                                FirebaseFirestoreException.Code.NOT_FOUND
                        );
                    }

                    DocumentSnapshot invitedUserSnapshot = transaction.get(invitedUserRef);
                    if (!invitedUserSnapshot.exists()) {
                        throw new FirebaseFirestoreException(
                                "Invited user not found",
                                FirebaseFirestoreException.Code.NOT_FOUND
                        );
                    }

                    DocumentSnapshot inviteSnapshot = transaction.get(inviteRef);
                    if (!inviteSnapshot.exists()) {
                        throw new FirebaseFirestoreException(
                                "Co-organizer invite not found",
                                FirebaseFirestoreException.Code.NOT_FOUND
                        );
                    }

                    String storedInvitedUserId = inviteSnapshot.getString("invitedUserId");
                    if (storedInvitedUserId != null && !invitedUserId.equals(storedInvitedUserId)) {
                        throw new FirebaseFirestoreException(
                                "Invite does not belong to this user",
                                FirebaseFirestoreException.Code.PERMISSION_DENIED
                        );
                    }

                    String organizerId = eventSnapshot.getString("organizerId");
                    if (organizerId != null && organizerId.equals(invitedUserId)) {
                        throw new FirebaseFirestoreException(
                                "Organizer cannot accept a co-organizer invite",
                                FirebaseFirestoreException.Code.INVALID_ARGUMENT
                        );
                    }

                    transaction.update(eventRef,
                            "coOrganizerIds", FieldValue.arrayUnion(invitedUserId),
                            "updatedAt", FieldValue.serverTimestamp()
                    );

                    transaction.delete(inviteRef);

                    return null;
                }).addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Adds a single comment to an event's comments subcollection.
     */
    public void addEventComment(
            @NonNull String eventId,
            @NonNull String uid,
            @NonNull String authorName,
            @NonNull String message,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        String trimmedMessage = message.trim();
        String trimmedAuthor = authorName.trim();

        if (eventId.trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("eventId required"));
            return;
        }

        if (uid.trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("uid required"));
            return;
        }

        if (trimmedMessage.isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("comment message required"));
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("eventId", eventId);
        data.put("uid", uid);
        data.put("authorName", trimmedAuthor.isEmpty() ? "Unknown User" : trimmedAuthor);
        data.put("message", trimmedMessage);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("createdAtMs", System.currentTimeMillis());

        eventCommentsCollection(eventId)
                .document()
                .set(data)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Returns all comments for an event ordered by newest first.
     */
    public void getEventComments(
            @NonNull String eventId,
            OnSuccessListener<List<Map<String, Object>>> onSuccess,
            OnFailureListener onFailure
    ) {
        eventCommentsCollection(eventId)
                .orderBy("createdAtMs", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Map<String, Object>> comments = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> comment = new HashMap<>();
                        comment.put("commentId", doc.getId());
                        comment.put("uid", doc.getString("uid"));
                        comment.put("authorName", doc.getString("authorName"));
                        comment.put("message", doc.getString("message"));
                        comment.put("createdAtMs", doc.getLong("createdAtMs"));
                        comments.add(comment);
                    }
                    onSuccess.onSuccess(comments);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Deletes a comment from an event.
     */
    public void deleteEventComment(
            @NonNull String eventId,
            @NonNull String commentId,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        eventCommentsCollection(eventId)
                .document(commentId)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Reports a comment.
     * Reports are stored in a subcollection "reported_comments" under the event document.
     */
    public void reportComment(
            @NonNull String eventId,
            @NonNull String commentId,
            @NonNull String reportedByUid,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        Map<String, Object> report = new HashMap<>();
        report.put("commentId", commentId);
        report.put("reportedByUid", reportedByUid);
        report.put("timestamp", FieldValue.serverTimestamp());

        eventReportedCommentsCollection(eventId)
                .document(commentId) // use commentId as doc ID to avoid duplicate reports per comment
                .set(report, SetOptions.merge())
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Returns all reported comments for an event.
     */
    public void getEventReportedComments(
            @NonNull String eventId,
            OnSuccessListener<List<Map<String, Object>>> onSuccess,
            OnFailureListener onFailure
    ) {
        eventReportedCommentsCollection(eventId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Map<String, Object>> reports = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Map<String, Object> report = new HashMap<>();
                        report.put("reportId", doc.getId());
                        report.put("commentId", doc.getString("commentId"));
                        report.put("reportedByUid", doc.getString("reportedByUid"));
                        // timestamp might be null if it hasn't synced with server yet
                        Object ts = doc.get("timestamp");
                        report.put("timestamp", ts);
                        reports.add(report);
                    }
                    onSuccess.onSuccess(reports);
                })
                .addOnFailureListener(onFailure);
    }
    public void declineInviteToBeCoorganizer(
            @NonNull String eventId,
            @NonNull String invitedUserId,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        if (eventId.trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("eventId required"));
            return;
        }

        if (invitedUserId.trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("invitedUserId required"));
            return;
        }

        eventCoorganizerInviteDoc(eventId, invitedUserId)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /** firebase boolean retrieval
     * Returns true or false for if the current user is the organizer for any event
     * Asynchronous: requires OnSuccess and OnFailure listeners
     */
    public void isUserEventOrganizer(
            String uuid,
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
     */
    public void getManagedEventsForUser(
            String userId,
            OnSuccessListener<List<Event>> onSuccess,
            OnFailureListener onFailure
    ) {
        db.collection("events")
                .whereEqualTo("organizerId", userId)
                .get()
                .addOnSuccessListener(organizerSnapshot -> {
                    db.collection("events")
                            .whereArrayContains("coOrganizerIds", userId)
                            .get()
                            .addOnSuccessListener(coOrganizerSnapshot -> {
                                Map<String, Event> merged = new HashMap<>();

                                for (QueryDocumentSnapshot doc : organizerSnapshot) {
                                    Event e = documentToEvent(doc);
                                    merged.put(e.getEventId(), e);
                                }

                                for (QueryDocumentSnapshot doc : coOrganizerSnapshot) {
                                    Event e = documentToEvent(doc);
                                    merged.put(e.getEventId(), e);
                                }

                                List<Event> events = new ArrayList<>(merged.values());
                                if (events.isEmpty()) {
                                    onSuccess.onSuccess(events);
                                    return;
                                }

                                populateEventAddressesSafely(events, 0, onSuccess);
                            })
                            .addOnFailureListener(onFailure);
                })
                .addOnFailureListener(onFailure);
    }

    /** firebase retrieval
     * Returns all events in the database
     * Asynchronous: requires OnSuccess and OnFailure listeners
     */
    public void getAllEvents(
            OnSuccessListener<List<Event>> onSuccess,
            OnFailureListener onFailure
    ) {
        Query query = db.collection("events").whereEqualTo("privateEvent", false);
        queryEventsWithAddresses(query, onSuccess, onFailure);
    }

    /** firebase modify
     * Deletes all events in the database pertaining to the organizerId given as a parameter
     * for use inside of cascadeDeleteUserProfile
     * Asynchronous: requires OnSuccess and OnFailure listeners
     */
    public void delAllOrganizerEvents(
            String organizerId,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        db.collection("events")
                .whereEqualTo("organizerId", organizerId)
                .get()
                .addOnSuccessListener(eventSnapshot -> {
                    if (eventSnapshot.isEmpty()) {
                        onSuccess.onSuccess(null);
                        return;
                    }

                    WriteBatch batch = db.batch();
                    for (QueryDocumentSnapshot eventDoc : eventSnapshot) {
                        batch.delete(eventDoc.getReference());
                    }
                    batch.commit().addOnSuccessListener(onSuccess).addOnFailureListener(onFailure);
                })
                .addOnFailureListener(onFailure);
    }

    /** firebase retrieval
     * Returns all REG_OPEN events in the database
     * REG_OPEN: registration window open
     * Asynchronous: requires OnSuccess and OnFailure listeners
     */
    public void listEventsRegOpen(
            Integer limit,
            OnSuccessListener<List<Event>> onSuccess,
            OnFailureListener onFailure
    ) {
        Query query = db.collection("events")
                .whereEqualTo("status", Event.EventStatus.REG_OPEN.name()).whereEqualTo("privateEvent", false);

        if (limit != null && limit > 0) {
            query = query.limit(limit);
        }

        queryEventsWithAddresses(query, onSuccess, onFailure);
    }

    /** firebase retrieval
     * Returns all REG_CLOSED events in the database
     * REG_CLOSED: registration end date passed
     * Asynchronous: requires OnSuccess and OnFailure listeners
     */
    public void listEventsRegClosed(
            Integer limit,
            OnSuccessListener<List<Event>> onSuccess,
            OnFailureListener onFailure
    ) {
        Query query = db.collection("events")
                .whereEqualTo("status", Event.EventStatus.REG_CLOSED.name()).whereEqualTo("privateEvent", false);

        if (limit != null && limit > 0) {
            query = query.limit(limit);
        }

        queryEventsWithAddresses(query, onSuccess, onFailure);
    }

    /** firebase retrieval
     * Returns all REG_FULL events in the database
     * REG_FULL: registration window open, but waitlist is full
     * Asynchronous: requires OnSuccess and OnFailure listeners
     */
    public void listEventsRegFull(
            Integer limit,
            OnSuccessListener<List<Event>> onSuccess,
            OnFailureListener onFailure
    ) {
        Query query = db.collection("events")
                .whereEqualTo("status", Event.EventStatus.REG_FULL.name()).whereEqualTo("privateEvent", false);

        if (limit != null && limit > 0) {
            query = query.limit(limit);
        }

        queryEventsWithAddresses(query, onSuccess, onFailure);
    }

    /** firebase retrieval
     * Returns all REG_UPCOMING events in the database
     * REG_UPCOMING: registration start date upcoming
     * Asynchronous: requires OnSuccess and OnFailure listeners
     */
    public void listEventsRegUpcoming(
            Integer limit,
            OnSuccessListener<List<Event>> onSuccess,
            OnFailureListener onFailure
    ) {
        Query query = db.collection("events")
                .whereEqualTo("status", Event.EventStatus.REG_UPCOMING.name()).whereEqualTo("privateEvent", false);

        if (limit != null && limit > 0) {
            query = query.limit(limit);
        }

        queryEventsWithAddresses(query, onSuccess, onFailure);
    }

    /**
     * function searchEvents will search using the keywords array
     * partial word matching.
     */
    public void searchEvents(
            String query,
            OnSuccessListener<List<Event>> onSuccess,
            OnFailureListener onFailure
    ) {
        if (query == null || query.trim().isEmpty()) {
            onSuccess.onSuccess(new ArrayList<>());
            return;
        }

        String lowerQuery = query.toLowerCase().trim();

        // Efficient case-insensitive search using array-contains
        Query searchQuery = db.collection("events")
                .whereArrayContains("keywords", lowerQuery);

        queryEventsWithAddresses(searchQuery, onSuccess, onFailure);
    }

    public void getEventsNearUser(
            @NonNull String uid,
            @NonNull User.UserAddressMode userAddressMode,
            int radiusKm,
            OnSuccessListener<List<Event>> onSuccess,
            OnFailureListener onFailure
    ) {
        if (uid.trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("uid required"));
            return;
        }

        if (radiusKm > 6371) { // primitive int resolves radiusKm > 0 to some 0xF....... which is > 6371 outside of two's complement
            onFailure.onFailure(new IllegalArgumentException("radiusKm must be greater than 0 and less than the radius of the Earth."));
            return;
        }

        userAddressDoc(uid, userAddressMode)
                .get()
                .addOnSuccessListener(userAddressSnapshot -> {
                    if (!userAddressSnapshot.exists()) {
                        onFailure.onFailure(new Exception(
                                "User address not found for mode: " + userAddressMode.name()
                        ));
                        return;
                    }

                    UserAddress userAddress = documentToUserAddress(userAddressSnapshot, uid);

                    if (userAddress.getLatitude() == null || userAddress.getLongitude() == null) {
                        onFailure.onFailure(new Exception(
                                "User address is missing latitude/longitude for mode: " + userAddressMode.name()
                        ));
                        return;
                    }

                    getAllEvents(events -> {
                        List<Event> nearbyEvents = filterEventsWithinRadius(
                                userAddress,
                                events,
                                radiusKm
                        );
                        onSuccess.onSuccess(nearbyEvents);
                    }, onFailure);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Filters a list of events to only those within the given radius of the user.
     */
    public List<Event> filterEventsWithinRadius(
            @NonNull UserAddress userAddress,
            @NonNull List<Event> events,
            int radiusKm
    ) {
        List<Event> nearbyEvents = new ArrayList<>();

        for (Event event : events) {
            EventAddress eventAddress = event.getAddress();
            if (eventAddress == null) {
                continue;
            }

            Double eventLat = eventAddress.getLatitude();
            Double eventLng = eventAddress.getLongitude();

            if (eventLat == null || eventLng == null) {
                continue;
            }

            double distanceKm = calculateDistance(
                    userAddress.getLatitude(),
                    userAddress.getLongitude(),
                    eventLat,
                    eventLng
            );

            if (distanceKm <= radiusKm) {
                nearbyEvents.add(event);
            }
        }

        return nearbyEvents;
    }


    /**
     * Returns true when the supplied event has a usable geo/radius restriction.
     */
    public boolean eventHasUsableGeoConstraint(@Nullable Event event) {
        if (event == null) {
            return false;
        }

        if (event.isGeoConstraintEnabled()) {
            return true;
        }

        EventAddress address = event.getAddress();
        return address != null
                && address.getLatitude() != null
                && address.getLongitude() != null
                && address.getRadiusKm() != null
                && address.getRadiusKm() > 0;
    }

    /**
     * Returns true when the user address falls inside the event's configured waitlist radius.
     */
    public boolean isWithinEventRadius(
            @Nullable UserAddress userAddress,
            @Nullable Event event
    ) {
        if (userAddress == null || event == null) {
            return false;
        }

        return isWithinEventRadius(userAddress, event.getAddress(), eventHasUsableGeoConstraint(event));
    }

    /**
     * Returns true when the user address falls inside the supplied event address radius.
     */
    public boolean isWithinEventRadius(
            @Nullable UserAddress userAddress,
            @Nullable EventAddress eventAddress,
            boolean hasGeoConstraint
    ) {
        if (!hasGeoConstraint) {
            return true;
        }

        if (userAddress == null || eventAddress == null) {
            return false;
        }

        if (userAddress.getLatitude() == null || userAddress.getLongitude() == null) {
            return false;
        }

        if (eventAddress.getLatitude() == null || eventAddress.getLongitude() == null) {
            return false;
        }

        Integer radiusKm = eventAddress.getRadiusKm();
        if (radiusKm == null || radiusKm <= 0) {
            return false;
        }

        double distanceKm = calculateDistance(
                userAddress.getLatitude(),
                userAddress.getLongitude(),
                eventAddress.getLatitude(),
                eventAddress.getLongitude()
        );

        return distanceKm <= radiusKm;
    }

    /**
     * firebase retrieval helper
     * Returns the user geo document reference for the selected address mode.
     */
    private DocumentReference userAddressDoc(
            @NonNull String uid,
            @NonNull User.UserAddressMode userAddressMode
    ) {
        String addressDocId = userAddressMode == User.UserAddressMode.CURRENT
                ? "current"
                : "default";

        return db.collection("users")
                .document(uid)
                .collection("geo")
                .document(addressDocId);
    }

    /**
     * firebase retrieval helper
     * Builds a UserAddress model from the selected user geo document.
     */
    private UserAddress documentToUserAddress(
            @NonNull DocumentSnapshot doc,
            @NonNull String uid
    ) {
        String location = doc.getString("location");
        Double latitude = doc.getDouble("latitude");
        Double longitude = doc.getDouble("longitude");

        return new UserAddress(uid, location, latitude, longitude);
    }

    /**
     * distance helper: accurate distance between two points (x,y) over a curve with radius earthRadiusKm
     */
    // 3. Calculate the Distance Using the Haversine Formula, Harpal Singh, Last Updated: 01/24/2026
    // https://www.baeldung.com/java-find-distance-between-points
    public double calculateDistance(double startLat, double startLong, double endLat, double endLong) {

        final double earthRadiusKm = 6371.0;

        double dLat = Math.toRadians((endLat - startLat));
        double dLong = Math.toRadians((endLong - startLong));

        startLat = Math.toRadians(startLat);
        endLat = Math.toRadians(endLat);

        double a = haversine(dLat) + Math.cos(startLat) * Math.cos(endLat) * haversine(dLong);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return earthRadiusKm * c;
    }
    public double haversine(double val) {
        return Math.pow(Math.sin(val / 2), 2);
    }

    /**
     ** One time migration tool to update all existing events with keywords.
     */
    public void syncAllEventKeywords(OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        getAllEvents(events -> {
            if (events.isEmpty()) {
                onSuccess.onSuccess(null);
                return;
            }

            WriteBatch batch = db.batch();
            for (Event event : events) {
                event.generateKeywords();
                DocumentReference ref = eventDoc(event.getEventId());
                batch.update(ref, "keywords", event.getKeywords());
            }

            batch.commit()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
        }, onFailure);
    }
}
