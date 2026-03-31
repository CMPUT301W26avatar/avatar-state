package com.example.lotteryapp.services.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.lotteryapp.models.User;
import com.example.lotteryapp.models.UserAddress;
import com.example.lotteryapp.models.UserEventHistory;
import com.example.lotteryapp.services.UserNameService;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Database storage and retrieval layer with respect to Users
 * Users have their own firebase collection "users"
 * Address data is stored under:
 *  - /users/{uuid}/geo/default
 *  - /users/{uuid}/geo/current
 * Call via ServiceLocator
 */
public class UserStorage {
    private final FirebaseFirestore db;

    public UserStorage(FirebaseFirestore db) {
        this.db = db;
    }

    /** firebase retrieval
     * returns a User document by the parameter user Id
     * Synchronous
     */
    public DocumentReference userDoc(String uuid) {
        return db.collection("users").document(uuid);
    }

    /** user default address subdocument
     * /users/{uuid}/geo/default
     */
    public DocumentReference userDefaultAddressDoc(String uuid) {
        return userDoc(uuid)
                .collection("geo")
                .document("default");
    }

    /** user current address subdocument
     * /users/{uuid}/geo/current
     */
    public DocumentReference userCurrentAddressDoc(String uuid) {
        return userDoc(uuid)
                .collection("geo")
                .document("current");
    }

    /**
     * user event history subdocument
     * /users/{uuid}/event_history/{eventId}
     *      - one entry in the subcollection is for one event
     */
    public DocumentReference userEventHistoryDoc(String uuid, String eventId) {
        return userDoc(uuid)
                .collection("event_history")
                .document(eventId);
    }

    /** firebase storage
     * Takes in a user id as a parameter and creates/upserts a User into the database
     * Transaction ensures the createdAt value is only set once.
     * Synchronous
     */
    public void setNewUser(String UUID) {
        final DocumentReference ref = db.collection("users").document(UUID);
        db.runTransaction(new Transaction.Function<Void>() {
            @Override
            public Void apply(@NonNull Transaction transaction) throws FirebaseFirestoreException {
                DocumentSnapshot snapshot = transaction.get(ref);
                if (!snapshot.exists()) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("deviceID", UUID);
                    data.put("isAdmin", false);
                    data.put("name", UserNameService.generateUserName());
                    data.put("createdAt", FieldValue.serverTimestamp());
                    data.put("updatedAt", FieldValue.serverTimestamp());
                    transaction.set(ref, data);
                } else {
                    transaction.update(ref, "updatedAt", FieldValue.serverTimestamp());
                }
                return null;
            }
        });
    }

    /** firebase storage
     * Takes in a user id, name and email as parameters and creates/upserts a User into the database
     * Transaction ensures the createdAt value is only set once.
     * Synchronous
     */
    public void setNewUser(String UUID, String name, String email) {
        final DocumentReference ref = db.collection("users").document(UUID);
        db.runTransaction(new Transaction.Function<Void>() {
            @Override
            public Void apply(@NonNull Transaction transaction) throws FirebaseFirestoreException {
                DocumentSnapshot snapshot = transaction.get(ref);
                if (!snapshot.exists()) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("deviceID", UUID);
                    data.put("isAdmin", false);
                    data.put("name", name);
                    data.put("email", email);
                    data.put("createdAt", FieldValue.serverTimestamp());
                    data.put("updatedAt", FieldValue.serverTimestamp());
                    transaction.set(ref, data);
                } else {
                    transaction.update(ref, "updatedAt", FieldValue.serverTimestamp());
                }
                return null;
            }
        });
    }

    /** firebase retrieval
     * Returns the parent user profile document only.
     * Address data is loaded separately from geo/default or geo/current.
     * Asynchronous: requires OnSuccess and OnFailure listeners
     */
    public void getUserProfile(
            final String uuid,
            final OnSuccessListener<User> ok,
            OnFailureListener fail
    ) {
        userDoc(uuid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        ok.onSuccess(documentToUser(snapshot));
                    } else {
                        ok.onSuccess(new User(uuid));
                    }
                })
                .addOnFailureListener(fail);
    }

    /** firebase retrieval
     * Returns the user subcollection document event_history/eventId
     * Address data is loaded separately from geo/default or geo/current.
     * Asynchronous: requires OnSuccess and OnFailure listeners
     */
    public void getUserEventHistory(
            String uuid,
            OnSuccessListener<List<UserEventHistory>> ok,
            OnFailureListener fail
    ) {
        userDoc(uuid)
                .collection("event_history")
                .orderBy("updatedAtMs", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<UserEventHistory> historyList = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        UserEventHistory history = doc.toObject(UserEventHistory.class);
                        if (history != null) {
                            historyList.add(history);
                        }
                    }
                    ok.onSuccess(historyList);
                })
                .addOnFailureListener(fail);
    }


    /** firebase retrieval
     * Returns the user's default address from /users/{uuid}/geo/default
     * Asynchronous: requires OnSuccess and OnFailure listeners
     */
    public void getDefaultUserAddress(
            String uuid,
            OnSuccessListener<UserAddress> ok,
            OnFailureListener fail
    ) {
        userDefaultAddressDoc(uuid)
                .get()
                .addOnSuccessListener(snapshot -> ok.onSuccess(documentToUserAddress(snapshot, uuid)))
                .addOnFailureListener(fail);
    }

    /** firebase retrieval
     * Returns the user's current address from /users/{uuid}/geo/current
     * Asynchronous: requires OnSuccess and OnFailure listeners
     */
    public void getCurrentUserAddress(
            String uuid,
            OnSuccessListener<UserAddress> ok,
            OnFailureListener fail
    ) {
        userCurrentAddressDoc(uuid)
                .get()
                .addOnSuccessListener(snapshot -> ok.onSuccess(documentToUserAddress(snapshot, uuid)))
                .addOnFailureListener(fail);
    }

    /**
     * Returns the preferred user address based on the user's address mode.
     * CURRENT -> /geo/current
     * DEFAULT -> /geo/default
     */
    public void getPreferredUserAddress(
            String uuid,
            User.UserAddressMode addressMode,
            OnSuccessListener<UserAddress> ok,
            OnFailureListener fail
    ) {
        if (addressMode == User.UserAddressMode.CURRENT) {
            getCurrentUserAddress(uuid, ok, fail);
        } else {
            getDefaultUserAddress(uuid, ok, fail);
        }
    }

    /** firebase retrieval helper
     * Returns a single User from the parameter doc (database DocumentSnapshot)
     * Only profile metadata is loaded here.
     */
    private User documentToUser(DocumentSnapshot snapshot) {
        User user = new User(snapshot.getId());
        user.setName(snapshot.getString("name"));
        user.setEmail(snapshot.getString("email"));
        user.setPhoneNumber(snapshot.getString("phoneNumber"));
        user.setProfilePicUrl(snapshot.getString("profilePicUrl"));

        Boolean admin = snapshot.getBoolean("isAdmin");
        user.setAdmin(admin != null && admin);

        String rawAddressMode = snapshot.getString("addressMode");
        if (rawAddressMode != null) {
            try {
                user.setAddressMode(User.UserAddressMode.valueOf(rawAddressMode));
            } catch (IllegalArgumentException ignored) {
                user.setAddressMode(User.UserAddressMode.DEFAULT);
            }
        }

        return user;
    }

    /** firebase retrieval helper
     * Returns a single UserAddress from the parameter doc.
     * Returns null if the address document does not exist.
     */
    @Nullable
    private UserAddress documentToUserAddress(DocumentSnapshot snapshot, String uuid) {
        if (!snapshot.exists()) {
            return null;
        }

        String location = snapshot.getString("location");
        Double latitude = snapshot.getDouble("latitude");
        Double longitude = snapshot.getDouble("longitude");
        return new UserAddress(uuid, location, latitude, longitude);
    }

    /** firebase retrieval
     * Returns all users in the database
     * Asynchronous: requires OnSuccess and OnFailure listeners
     */
    public void getAllUsers(OnSuccessListener<List<User>> ok, OnFailureListener fail) {
        db.collection("users").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<User> users = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        users.add(documentToUser(doc));
                    }
                    ok.onSuccess(users);
                })
                .addOnFailureListener(fail);
    }

    /** firebase modify
     * Deletes a single document in firebase keyed by the parameter uuid
     * Asynchronous: requires OnSuccess and OnFailure listeners
     */
    public void deleteUser(String uuid, OnSuccessListener<Void> ok, OnFailureListener fail) {
        userDoc(uuid).delete().addOnSuccessListener(ok).addOnFailureListener(fail);
    }

    /** firebase modify
     * Updates the fields of a user entry in Users firebase collection
     * Asynchronous: requires OnSuccess and OnFailure listeners
     */
    public void upsertUserProfile(
            String uuid,
            String name,
            String email,
            String phoneNumber,
            String location,
            OnSuccessListener<Void> ok,
            OnFailureListener fail
    ) {
        updateUserProfile(uuid, name, email, phoneNumber, location, ok, fail);
    }

    /** firebase modify
     * Updates the fields of a user entry in Users firebase collection
     * Address fields are NOT stored on the parent user document.
     * The location parameter is intentionally ignored to preserve the geo subdocument design.
     * Asynchronous: requires OnSuccess and OnFailure listeners
     */
    public void updateUserProfile(
            String uuid,
            String name,
            String email,
            String phoneNumber,
            String location,
            OnSuccessListener<Void> ok,
            OnFailureListener fail
    ) {
        Map<String, Object> update = new HashMap<>();

        if (name != null) {
            update.put("name", name);
        }
        if (email != null) {
            update.put("email", email);
        }
        if (phoneNumber != null) {
            update.put("phoneNumber", phoneNumber);
        }

        update.put("updatedAt", FieldValue.serverTimestamp());
        update.put("deviceID", uuid);

        userDoc(uuid)
                .set(update, SetOptions.merge())
                .addOnSuccessListener(ok)
                .addOnFailureListener(fail);
    }

    /**
     * Writes a single user event history entry under:
     * /users/{uuid}/event_history/{eventId}
     */
    public void addUserEventHistoryEntry(
            String uuid,
            String eventId,
            UserEventHistory.HistoryStatus status,
            Long updatedAtMs,
            OnSuccessListener<Void> ok,
            OnFailureListener fail
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("eventId", eventId);
        data.put("userId", uuid);
        data.put("status", status.name());
        data.put("updatedAtMs", updatedAtMs != null ? updatedAtMs : System.currentTimeMillis());

        userEventHistoryDoc(uuid, eventId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(ok)
                .addOnFailureListener(fail);
    }


    /** firebase modify
     * Sets or deletes the user's default address subdocument
     * Stored at /users/{uuid}/geo/default
     */
    public void setDefaultUserAddress(
            String uuid,
            @Nullable UserAddress address,
            OnSuccessListener<Void> ok,
            OnFailureListener fail
    ) {
        upsertUserAddressDoc(userDefaultAddressDoc(uuid), uuid, address, ok, fail);
    }

    /** firebase modify
     * Sets or deletes the user's current address subdocument
     * Stored at /users/{uuid}/geo/current
     */
    public void setCurrentUserAddress(
            String uuid,
            @Nullable UserAddress address,
            OnSuccessListener<Void> ok,
            OnFailureListener fail
    ) {
        upsertUserAddressDoc(userCurrentAddressDoc(uuid), uuid, address, ok, fail);
    }

    /** firebase modify helper
     * Writes the address into the given geo subdocument.
     * Asynchronous: requires OnSuccess and OnFailure listeners
     */
    private void upsertUserAddressDoc(
            DocumentReference ref,
            String uuid,
            @Nullable UserAddress address,
            OnSuccessListener<Void> ok,
            OnFailureListener fail
    ) {
        if (address == null) {
            ref.delete()
                    .addOnSuccessListener(ok)
                    .addOnFailureListener(fail);
            return;
        }

        Map<String, Object> update = new HashMap<>();
        update.put("uid", uuid);
        update.put("location", address.getLocation());
        update.put("latitude", address.getLatitude());
        update.put("longitude", address.getLongitude());
        update.put("updatedAt", FieldValue.serverTimestamp());

        ref.set(update, SetOptions.merge())
                .addOnSuccessListener(ok)
                .addOnFailureListener(fail);
    }





    /** firebase modify
     * Updates the fields of a user entry in Users firebase collection (image removed!)
     * Asynchronous: requires OnSuccess and OnFailure listeners
     */
    public void deleteProfilePic(String uuid, OnSuccessListener<Void> ok, OnFailureListener fail) {
        Map<String, Object> update = new HashMap<>();
        update.put("profilePicUrl", FieldValue.delete());
        update.put("updatedAt", FieldValue.serverTimestamp());
        userDoc(uuid).update(update).addOnSuccessListener(ok).addOnFailureListener(fail);
    }

    /** firebase modify
     * Deletes a User entry from the users collection
     * Used in isolation -> reset profile, or
     * Used in conjunction with delAllUserEntries inside of cascadeDeleteProfile
     * Asynchronous: requires OnSuccess and OnFailure listeners
     */
    public void delUserProfile(
            String uuid,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        db.collection("users")
                .document(uuid)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /** firebase modify
     * Deletes a User entry from the users collection
     * Removes all instances of the User as an Entrant in all events
     * If organizer, deletes all events associated with the user/organizer
     * Asynchronous: requires OnSuccess and OnFailure listeners
     */
    public void cascadeUserDelete(
            String uuid,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        EventPoolStorage eventPoolStorage = new EventPoolStorage(db);
        EventStorage eventStorage = new EventStorage(db);

        eventStorage.isUserEventOrganizer(uuid, organizer -> {
            if (organizer) {
                eventStorage.delAllOrganizerEvents(
                        uuid,
                        unused -> eventPoolStorage.delAllUserEntries(
                                uuid,
                                unused2 -> delUserProfile(uuid, onSuccess, onFailure),
                                onFailure
                        ),
                        onFailure
                );
            } else {
                eventPoolStorage.delAllUserEntries(
                        uuid,
                        unused -> delUserProfile(uuid, onSuccess, onFailure),
                        onFailure
                );
            }
        }, onFailure);
    }

    public void updateFcmToken(String uuid, String token) {
        userDoc(uuid).update("fcmToken", token);
    }
}