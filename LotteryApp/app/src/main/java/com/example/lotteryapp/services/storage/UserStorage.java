package com.example.lotteryapp.services.storage;
import androidx.annotation.NonNull;

import com.example.lotteryapp.models.User;
import com.example.lotteryapp.services.UserNameService;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Database storage and retrieval layer with respect to Users
 * Users have they own firebase collection "users"
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
    private DocumentReference userDoc(String uuid) {
        return db.collection("users").document(uuid);
    }

    /** firebase storage
     * Takes in an user id as a parameter and creates/upserts a User into the database
     * Transaction ensures the createdAt value is only set once.
     * Synchronous
     */
    public void setNewUser(String UUID) {
        final DocumentReference ref = db.collection("users").document(UUID);
        // Transaction so createdAt is only set once.
        db.runTransaction(new Transaction.Function<Void>() {
            @Override
            public Void apply(@NonNull Transaction transaction) throws FirebaseFirestoreException {
                DocumentSnapshot snapshot = transaction.get(ref);
                if (!snapshot.exists()) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("deviceID", UUID);
                    data.put("isAdmin", false); // Default to false
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
     * Takes in an user id, name and email as a parameter and creates/upserts a User into the database
     * Transaction ensures the createdAt value is only set once.
     * Synchronous
     */
    public void setNewUser(String UUID, String name, String email) {
        final DocumentReference ref = db.collection("users").document(UUID);
        // Transaction so createdAt is only set once.
        db.runTransaction(new Transaction.Function<Void>() {
            @Override
            public Void apply(@NonNull Transaction transaction) throws FirebaseFirestoreException {
                DocumentSnapshot snapshot = transaction.get(ref);
                if (!snapshot.exists()) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("deviceID", UUID);
                    data.put("isAdmin", false); // Default to false
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
     * Takes in an user id as a parameter and creates/upserts a User into the database
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns Document pertaining to User, asynchronously
     */
    public void getUserProfile(final String uuid,
            final OnSuccessListener<User> ok, OnFailureListener fail) {
        userDoc(uuid)
                .get()
                .addOnSuccessListener(new OnSuccessListener<com.google.firebase.firestore.DocumentSnapshot>() {
                    @Override
                    public void onSuccess(com.google.firebase.firestore.DocumentSnapshot snapshot) {
                        if (snapshot.exists()) {
                            // moved to documentToUser()
                            ok.onSuccess(documentToUser(snapshot));
                        }
                        else {
                            ok.onSuccess(new User(uuid));
                        }
                    }
                }).addOnFailureListener(fail);
    }

    /** firebase retrieval helper
     * Returns a single User from the parameter doc (database DocumentSnapshot)
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns User outlined in the DocumentSnapshot, asynchronously
     */
    private User documentToUser(DocumentSnapshot snapshot) {
        User user = new User(snapshot.getId());
        user.setName(snapshot.getString("name"));
        user.setEmail(snapshot.getString("email"));
        user.setPhoneNumber(snapshot.getString("phoneNumber"));
        user.setLocation(snapshot.getString("location"));
        user.setProfilePicUrl(snapshot.getString("profilePicUrl"));
        Boolean admin = snapshot.getBoolean("isAdmin");
        user.setAdmin(admin != null && admin);

        return user;
    }

    /** firebase retrieval
     * Returns all users in the database
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns list of Users, asynchronously
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
     * - returns nothing, asynchronously or synchronously
     */

    public void deleteUser(String uuid, OnSuccessListener<Void> ok, OnFailureListener fail) {
        userDoc(uuid).delete().addOnSuccessListener(ok).addOnFailureListener(fail);
    }

    /** firebase modify
     * Updates the fields of a user entry in Users firebase collection
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns nothing, asynchronously or synchronously
     */
    public void upsertUserProfile(String uuid, String name, String email, String phoneNumber, String location,
                                  OnSuccessListener<Void> ok, OnFailureListener fail) {
        updateUserProfile(uuid, name, email, phoneNumber, location, null, ok, fail);
    }

    /** firebase modify
     * Updates the fields of a user entry in Users firebase collection (with image!)
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns nothing, asynchronously or synchronously
     */

    public void updateUserProfile(String uuid, String name, String email, String phoneNumber, String location, String profilePicUrl,
                                  OnSuccessListener<Void> ok, OnFailureListener fail) {
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
        if (location != null) {
            update.put("location", location);
        }
        if (profilePicUrl != null || name == null) { // name==null is a hack to allow clearing profilePicUrl if we pass it as null explicitly
             update.put("profilePicUrl", profilePicUrl);
        }
        update.put("updatedAt", FieldValue.serverTimestamp());
        update.put("deviceID", uuid);

        userDoc(uuid)
                .set(update, SetOptions.merge())
                .addOnSuccessListener(ok)
                .addOnFailureListener(fail);
    }

    /** firebase modify
     * Updates the fields of a user entry in Users firebase collection (image removed!)
     * Asynchronous: requires OnSuccess and OnFailure listeners
     * - returns nothing, asynchronously or synchronously
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
     * - returns nothing, asynchronously or synchronously
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
     * - returns nothing, asynchronously or synchronously
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
                        unused -> eventPoolStorage.delAllUserEntries(uuid,
                                unused2 -> delUserProfile(uuid, onSuccess, onFailure),
                                onFailure
                        ),
                        onFailure
                );
            } else {
                eventPoolStorage.delAllUserEntries(uuid,
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
