package com.example.lotteryapp.services.storage;

import static com.example.lotteryapp.models.Entrant.EntrantStatus.DECLINED;
import static com.example.lotteryapp.models.Entrant.EntrantStatus.ENROLLED;
import static com.example.lotteryapp.models.Entrant.EntrantStatus.INVITED;
import static com.example.lotteryapp.models.Entrant.EntrantStatus.WAITLISTED;

import android.app.Service;

import com.example.lotteryapp.models.Entrant;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.models.UserEventHistory;
import android.widget.Toast;

import com.example.lotteryapp.models.Entrant;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.models.EventAddress;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.firebase.firestore.WriteBatch;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import com.example.lotteryapp.models.NotificationLog;
import com.example.lotteryapp.services.ServiceLocator;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Transaction;
import com.example.lotteryapp.services.storage.NotificationLogStorage;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Database storage and retrieval layer with respect to Event subcollections of Entrants
 * Events have their own subcollections for waitlisted, invited, enrolled and declined Entrants
 * Call via ServiceLocator
 */

public class EventPoolStorage {

    private final FirebaseFirestore db;

    public EventPoolStorage(FirebaseFirestore db) {
        this.db = db;
    }

    private NotificationLogStorage notificationLogStorage = ServiceLocator.getNotificationLogStorage();

    /** firebase retrieval helper
     * - returns a document of the waitlisted subcollection for a certain unique entrantId
     */
    private DocumentReference waitlistedDoc(String eventId, String entrantId) {
        return db.collection("events")
                .document(eventId)
                .collection("waitlisted")
                .document(entrantId);
    }

    /** firebase retrieval helper
     * - returns a document of the invited subcollection for a certain unique entrantId
     */
    private DocumentReference invitedDoc(String eventId, String entrantId) {
        return db.collection("events")
                .document(eventId)
                .collection("invited")
                .document(entrantId);
    }

    /** firebase retrieval helper
     * - returns a document of the enrolled subcollection for a certain unique entrantId
     */
    private DocumentReference enrolledDoc(String eventId, String entrantId) {
        return db.collection("events")
                .document(eventId)
                .collection("enrolled")
                .document(entrantId);
    }

    /** firebase retrieval helper
     * - returns a document of the waitlisted subcollection for a certain unique entrantId
     */
    private DocumentReference declinedDoc(String eventId, String entrantId) {
        return db.collection("events")
                .document(eventId)
                .collection("declined")
                .document(entrantId);
    }

    private DocumentReference cancelledDoc(String eventId, String entrantId) {
        return db.collection("events")
                .document(eventId)
                .collection("cancelled")
                .document(entrantId);
    }

    /** firebase storage helper
     * - takes database fields to store as parameters, and returns a Map with those fields
     */
    private Map<String, Object> mapEntrantData(String eventId, String entrantId, String status) {
        Map<String, Object> data = new HashMap<>();
        data.put("entrantId", entrantId);
        data.put("eventId", eventId);
        data.put("status", status);
        data.put("joinedAt", FieldValue.serverTimestamp());
        return data;
    }

    /** firebase storage
     * Stores a single entrant inside of the waitlisted subcollection
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns nothing, synchronously or asynchronously
     */
    public void waitlistForEvent(
            String eventId,
            Entrant entrant,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        DocumentReference eventRef = db.collection("events").document(eventId);
        DocumentReference entrantWaitlistedRef = waitlistedDoc(eventId, entrant.getEntrantId());

        db.runTransaction((Transaction.Function<Void>) transaction -> {
                    DocumentSnapshot eventSnap = transaction.get(eventRef);
                    DocumentSnapshot waitlistedSnap = transaction.get(entrantWaitlistedRef);

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


    /** firebase storage
     * Removes an entrant from the invited subcollection
     *  for organizers to cancel invites so they can re-sample the lottery if they invite a non-responsive user
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns nothing, synchronously or asynchronously
     */
    public void cancelInvitation(
            String eventId,
            String entrantId,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        DocumentReference eventRef = db.collection("events").document(eventId);
        DocumentReference invitedRef = invitedDoc(eventId, entrantId);
        DocumentReference cancelledRef = cancelledDoc(eventId, entrantId);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
                    DocumentSnapshot eventSnap = transaction.get(eventRef);
                    DocumentSnapshot invitedSnap = transaction.get(invitedRef);

                    if (!eventSnap.exists()) {
                        throw new IllegalStateException("Event does not exist");
                    }

                    if (!invitedSnap.exists()) {
                        throw new IllegalStateException("Entrant is not invited");
                    }

                    int invitationCount = eventSnap.getLong("invitationCount") != null
                            ? eventSnap.getLong("invitationCount").intValue() : 0;
                    int waitlistCount = eventSnap.getLong("waitlistCount") != null
                            ? eventSnap.getLong("waitlistCount").intValue() : 0;
                    int enrolledCount = eventSnap.getLong("enrolledCount") != null
                            ? eventSnap.getLong("enrolledCount").intValue() : 0;

                    int updatedInvitationCount = Math.max(0, invitationCount - 1);

                    Map<String, Object> data = mapEntrantData(
                            eventId,
                            entrantId,
                            Entrant.EntrantStatus.CANCELLED.name()
                    );
                    data.put("joinedAt", invitedSnap.get("joinedAt"));

                    transaction.delete(invitedRef);
                    transaction.set(cancelledRef, data);

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("invitationCount", updatedInvitationCount);
                    updates.put(
                            "status",
                            resolveEventStatusAfterChange(
                                    eventSnap,
                                    waitlistCount,
                                    updatedInvitationCount,
                                    enrolledCount
                            )
                    );

                    transaction.update(eventRef, updates);

                    return null;
                }).addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /** firebase storage
     * Stores a single entrant inside of the enrolled subcollection
     * Removes the aforementioned entrant from the invited subcollection
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns nothing, synchronously or asynchronously
     */
    public void enrollInEvent(
            String eventId,
            String entrantId,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        DocumentReference eventRef = db.collection("events").document(eventId);
        DocumentReference invitedRef = invitedDoc(eventId, entrantId);
        DocumentReference enrolledRef = enrolledDoc(eventId, entrantId);

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
                            entrantId,
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

    /** firebase storage
     * Removes a single entrant from the waitlisted subcollection
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns nothing, synchronously or asynchronously
     */
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

                    int invitationCount = eventSnap.getLong("invitationCount") != null
                            ? eventSnap.getLong("invitationCount").intValue() : 0;
                    int enrolledCount = eventSnap.getLong("enrolledCount") != null
                            ? eventSnap.getLong("enrolledCount").intValue() : 0;

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
                            resolveEventStatusAfterChange(
                                    eventSnap,
                                    updatedWaitlistCount,
                                    invitationCount,
                                    enrolledCount
                            )
                    );

                    transaction.update(eventRef, updates);
                    return null;
                }).addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Removes a single entrant from the invited subcollection and moves them to declined.
     * Asynchronous: requires OnSuccess and OnFailure listeners.
     */
    public void removeFromInvited(
            String eventId,
            String entrantId,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        DocumentReference eventRef = db.collection("events").document(eventId);
        DocumentReference invitedRef = invitedDoc(eventId, entrantId);
        DocumentReference declinedRef = declinedDoc(eventId, entrantId);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
                    DocumentSnapshot eventSnap = transaction.get(eventRef);
                    DocumentSnapshot invitedSnap = transaction.get(invitedRef);

                    if (!eventSnap.exists()) {
                        throw new IllegalStateException("Event does not exist");
                    }
                    if (!invitedSnap.exists()) {
                        throw new IllegalStateException("Entrant is not invited");
                    }

                    int invitationCount = eventSnap.getLong("invitationCount") != null
                            ? eventSnap.getLong("invitationCount").intValue() : 0;

                    Map<String, Object> declinedData = mapEntrantData(
                            eventId,
                            entrantId,
                            DECLINED.name()
                    );
                    declinedData.put("joinedAt", invitedSnap.get("joinedAt"));

                    transaction.delete(invitedRef);
                    transaction.set(declinedRef, declinedData);

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("invitationCount", Math.max(0, invitationCount - 1));

                    transaction.update(eventRef, updates);
                    return null;
                }).addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /** firebase storage
     * Removes a single entrant from the enrolled subcollection
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns nothing, synchronously or asynchronously
     */
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

    // BASIC DB QUERIES

    /** firebase retrieval
     * Returns the status of an Entrant inside of events (all subcollections)
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns the status of the Entrant onSuccess, asynchronously
     */
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

    /** firebase retrieval
     * Returns all Entrants inside of the waitlisted subcollection
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns a list of Entrants, asynchronously
     */
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

    /** firebase retrieval
     * Returns all Entrants inside of the invited subcollection
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns a list of Entrants, asynchronously
     */
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

    /** firebase retrieval
     * Returns all Entrants inside of the declined subcollection
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns a list of Entrants, asynchronously
     */
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

    /** firebase retrieval
     * Returns all Entrants inside of the enrolled subcollection
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns a list of Entrants, asynchronously
     */
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

    /** firebase retrieval
     * Returns all Entrants inside of the cancelled subcollection
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns a list of Entrants, asynchronously
     */
    public void getCancelledEntrants(String eventId,
                                     OnSuccessListener<List<Entrant>> onSuccess,
                                     OnFailureListener onFailure) {
        db.collection("events")
                .document(eventId)
                .collection("cancelled")
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
                                    Entrant.EntrantStatus.CANCELLED
                            ));
                        }
                    }
                    onSuccess.onSuccess(entrants);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Returns all events the given user is currently enrolled in.
     * Uses a collection group query over all /enrolled subcollections.
     * Asynchronous: requires OnSuccess and OnFailure listeners.
     */
    public void getEnrolledEventIdsForUser(
            String entrantId,
            OnSuccessListener<List<String>> onSuccess,
            OnFailureListener onFailure
    ) {
        db.collectionGroup("enrolled")
                .whereEqualTo("entrantId", entrantId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> eventIds = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String eventId = doc.getString("eventId");
                        if (eventId != null && !eventId.trim().isEmpty()) {
                            eventIds.add(eventId);
                        }
                    }
                    onSuccess.onSuccess(eventIds);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Returns all events the given user is currently invited to enroll in.
     * Uses a collection group query over all /enrolled subcollections.
     * Asynchronous: requires OnSuccess and OnFailure listeners.
     */
    public void getInvitedEventIdsForUser(
            String entrantId,
            OnSuccessListener<List<String>> onSuccess,
            OnFailureListener onFailure
    ) {
        db.collectionGroup("invited")
                .whereEqualTo("entrantId", entrantId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> eventIds = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String eventId = doc.getString("eventId");
                        if (eventId != null && !eventId.trim().isEmpty() && !eventIds.contains(eventId)) {
                            eventIds.add(eventId);
                        }
                    }
                    onSuccess.onSuccess(eventIds);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Returns all events the given user is currently waiting for an invite to enroll in.
     * Uses a collection group query over all /enrolled subcollections.
     * Asynchronous: requires OnSuccess and OnFailure listeners.
     */
    public void getWaitlistedEventIdsForUser(
            String entrantId,
            OnSuccessListener<List<String>> onSuccess,
            OnFailureListener onFailure
    ) {
        db.collectionGroup("waitlisted")
                .whereEqualTo("entrantId", entrantId)
                .whereEqualTo("status", "WAITLISTED")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> eventIds = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String eventId = doc.getString("eventId");
                        if (eventId != null && !eventId.trim().isEmpty() && !eventIds.contains(eventId)) {
                            eventIds.add(eventId);
                        }
                    }
                    onSuccess.onSuccess(eventIds);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Perform the lottery draw to select winners.
     * Picks up to capacity random entrants from WAITLISTED pool and marks them as INVITED.
     * The rest are marked as NOT_INVITED.
     */
    public void drawWinners(
            String eventId,
            int capacity,
            OnSuccessListener<Integer> onSuccess,
            OnFailureListener onFailure
    ) {
        db.collection("events")
                .document(eventId)
                .collection("waitlisted")
                .whereEqualTo("status", Entrant.EntrantStatus.WAITLISTED.name())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<QueryDocumentSnapshot> eligibleEntrants = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        eligibleEntrants.add(doc);
                    }

                    if (eligibleEntrants.isEmpty()) {
                        onSuccess.onSuccess(0);
                        return;
                    }

                    Collections.shuffle(eligibleEntrants);

                    int winnersCount = Math.min(capacity, eligibleEntrants.size());
                    DocumentReference eventRef = db.collection("events").document(eventId);

                    eventRef.get()
                            .addOnSuccessListener(eventSnap -> {
                                if (!eventSnap.exists()) {
                                    onFailure.onFailure(
                                            new IllegalStateException("Event does not exist")
                                    );
                                    return;
                                }

                                int invitationCount = eventSnap.getLong("invitationCount") != null
                                        ? eventSnap.getLong("invitationCount").intValue()
                                        : 0;
                                int waitlistCount = eventSnap.getLong("waitlistCount") != null
                                        ? eventSnap.getLong("waitlistCount").intValue()
                                        : 0;
                                int enrolledCount = eventSnap.getLong("enrolledCount") != null
                                        ? eventSnap.getLong("enrolledCount").intValue()
                                        : 0;

                                int updatedInvitationCount = invitationCount + winnersCount;
                                int updatedWaitlistCount = Math.max(0, waitlistCount - winnersCount);

                                WriteBatch batch = db.batch();
                                List<String> invitedEntrantIds = new ArrayList<>();
                                List<String> notSelectedEntrantIds = new ArrayList<>();

                                String eventTitle = eventSnap.getString("title") != null
                                        ? eventSnap.getString("title")
                                        : "No Title";
                                String organizerId = eventSnap.getString("organizerId");

                                String invitedTitle = String.format("Invitation to %s", eventTitle);
                                String invitedMessage = String.format(
                                        "Congratulations! You have been invited to sign up for %s. Please accept or decline the invitation promptly.",
                                        eventTitle
                                );

                                String notInvitedTitle = String.format("Not Invited to %s", eventTitle);
                                String notInvitedMessage = String.format(
                                        "Unfortunately, you have not been selected to sign up for %s. Feel free to check out other events or wait around for another chance to be selected!",
                                        eventTitle
                                );

                                for (int i = 0; i < eligibleEntrants.size(); i++) {
                                    QueryDocumentSnapshot doc = eligibleEntrants.get(i);
                                    String entrantId = doc.getString("entrantId");

                                    if (entrantId == null || entrantId.trim().isEmpty()) {
                                        continue;
                                    }

                                    if (i < winnersCount) {
                                        DocumentReference invitedRef = invitedDoc(eventId, entrantId);

                                        Map<String, Object> data = mapEntrantData(
                                                eventId,
                                                entrantId,
                                                Entrant.EntrantStatus.INVITED.name()
                                        );
                                        data.put("joinedAt", doc.get("joinedAt"));

                                        batch.set(invitedRef, data);
                                        batch.delete(doc.getReference());

                                        invitedEntrantIds.add(entrantId);

                                        notificationLogStorage.logNotification(
                                                eventId,
                                                organizerId,
                                                entrantId,
                                                invitedTitle,
                                                invitedMessage,
                                                NotificationLog.NotificationType.WON_LOTTERY,
                                                sentCount -> { },
                                                e -> { }
                                        );
                                    } else {
                                        batch.update(
                                                doc.getReference(),
                                                "status",
                                                Entrant.EntrantStatus.NOT_INVITED.name()
                                        );
                                        batch.update(
                                                doc.getReference(),
                                                "updatedAt",
                                                FieldValue.serverTimestamp()
                                        );

                                        notSelectedEntrantIds.add(entrantId);

                                        notificationLogStorage.logNotification(
                                                eventId,
                                                organizerId,
                                                entrantId,
                                                notInvitedTitle,
                                                notInvitedMessage,
                                                NotificationLog.NotificationType.LOST_LOTTERY,
                                                sentCount -> { },
                                                e -> { }
                                        );
                                    }
                                }

                                Map<String, Object> eventUpdates = new HashMap<>();
                                eventUpdates.put("invitationCount", updatedInvitationCount);
                                eventUpdates.put("waitlistCount", updatedWaitlistCount);
                                eventUpdates.put(
                                        "status",
                                        resolveEventStatusAfterChange(
                                                eventSnap,
                                                updatedWaitlistCount,
                                                updatedInvitationCount,
                                                enrolledCount
                                        )
                                );

                                batch.update(eventRef, eventUpdates);

                                batch.commit()
                                        .addOnSuccessListener(unused -> {
                                            UserStorage ustore = ServiceLocator.getUserStorage();

                                            int totalHistoryWrites =
                                                    invitedEntrantIds.size() + notSelectedEntrantIds.size();

                                            if (totalHistoryWrites == 0) {
                                                onSuccess.onSuccess(winnersCount);
                                                return;
                                            }

                                            AtomicInteger remaining = new AtomicInteger(totalHistoryWrites);

                                            OnSuccessListener<Void> historyWriteSuccess = unused2 -> {
                                                if (remaining.decrementAndGet() == 0) {
                                                    onSuccess.onSuccess(winnersCount);
                                                }
                                            };

                                            OnFailureListener historyWriteFailure = e -> {
                                                // Do not fail the draw after the batch has already committed.
                                                if (remaining.decrementAndGet() == 0) {
                                                    onSuccess.onSuccess(winnersCount);
                                                }
                                            };

                                            for (String invitedEntrantId : invitedEntrantIds) {
                                                ustore.addUserEventHistoryEntry(
                                                        invitedEntrantId,
                                                        eventId,
                                                        UserEventHistory.HistoryStatus.INVITED,
                                                        System.currentTimeMillis(),
                                                        historyWriteSuccess,
                                                        historyWriteFailure
                                                );
                                            }

                                            for (String notSelectedEntrantId : notSelectedEntrantIds) {
                                                ustore.addUserEventHistoryEntry(
                                                        notSelectedEntrantId,
                                                        eventId,
                                                        UserEventHistory.HistoryStatus.NOT_SELECTED,
                                                        System.currentTimeMillis(),
                                                        historyWriteSuccess,
                                                        historyWriteFailure
                                                );
                                            }
                                        })
                                        .addOnFailureListener(onFailure);
                            })
                            .addOnFailureListener(onFailure);
                })
                .addOnFailureListener(onFailure);
    }

    // DATABASE DELETE ENTRY (status unknown)
    /** firebase delete single
     * Deletes a singular Entrant from one of the event subcollections
     * - differentiated logic for each subcollection (status)
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns nothing, synchronously or asynchronously
     */
    public void deleteEntryByStatus(
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
                            updates.put(
                                    "status",
                                    resolveEventStatusAfterChange(
                                            eventSnap,
                                            waitlistCount,
                                            invitationCount,
                                            enrolledCount
                                    )
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

    /** firebase delete cascade
     * Deletes a singular Entrant from all of the event subcollections
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns nothing, synchronously or asynchronously
     */
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

    /**
     * Takes in the new waitlist, invitation and enrolled count, and figures out what the EventStatus should be given these values.
     *  returns the name of the EventStatus enumerator to add to the db.
     */
    private String resolveEventStatusAfterChange(
            DocumentSnapshot eventSnap,
            int updatedWaitlistCount,
            int updatedInvitationCount,
            int updatedEnrolledCount
    ) {
        Boolean drawn = eventSnap.getBoolean("hasDrawnLottery");
        boolean hasDrawnLottery = drawn != null && drawn;

        int waitlistCapacity = eventSnap.getLong("waitlistCapacity") != null
                ? eventSnap.getLong("waitlistCapacity").intValue() : 0;

        int eventCapacity = eventSnap.getLong("eventCapacity") != null
                ? eventSnap.getLong("eventCapacity").intValue() : 0;

        if (hasDrawnLottery) {
            if (updatedEnrolledCount >= eventCapacity) {
                return Event.EventStatus.EVENT_FULL.name();
            }
            if (updatedInvitationCount > 0 || updatedEnrolledCount > 0) {
                return Event.EventStatus.EVENT_OPEN.name();
            }
            return Event.EventStatus.REG_CLOSED.name();
        }

        return updatedWaitlistCount >= waitlistCapacity
                ? Event.EventStatus.REG_FULL.name()
                : Event.EventStatus.REG_OPEN.name();
    }

}


