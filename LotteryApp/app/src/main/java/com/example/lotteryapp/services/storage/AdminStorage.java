package com.example.lotteryapp.services.storage;

import com.example.lotteryapp.models.User;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/** Database storage and retrieval layer with respect to Admins
 * Admins have they own firebase collection "admin" with subcollections "current" and "requested"
 * Call via ServiceLocator
 */
public class AdminStorage {
    private final FirebaseFirestore db;

    public AdminStorage(FirebaseFirestore db) {
        this.db = db;
    }

    /** boolean helper: "is current user organizer?"
     * Asynchronous call: needs onSuccess and onFailure listeners
     * - takes in user id, and returns a bool for if that user is the Organizer
     */

    public void isAdmin(
            String uuid,
            OnSuccessListener<Boolean> onSuccess,
            OnFailureListener onFailure
    ) {
        db.collection("admin")
                .document("current")
                .collection("users")
                .document(uuid)
                .get()
                .addOnSuccessListener(doc -> onSuccess.onSuccess(doc.exists()))
                .addOnFailureListener(onFailure);
    }

    /** boolean helper: "is there an admin at all?"
     * Asynchronous call: needs onSuccess and onFailure listeners
     * - takes in user id, and returns a bool for if that user is the Organizer
     */

    public void isThereAnAdmin(
            OnSuccessListener<Boolean> onSuccess,
            OnFailureListener onFailure
    ) {
        db.collection("admin")
                .document("current")
                .collection("users")
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> onSuccess.onSuccess(!snapshot.isEmpty()))
                .addOnFailureListener(onFailure);
    }

    /** A user can request to be an Admin if they would like to be.
     * Asynchronous call: needs onSuccess and onFailure listeners
     * - takes in user id, and adds the user to the requested subcollection
     */
    public void requestNewAdmin(
            String uuid,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("uuid", uuid);

        db.collection("admin")
                .document("requests")
                .collection("requested")
                .document(uuid)
                .set(data)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /** Add a new admin to the current>users subcollection.
     * Asynchronous call: needs onSuccess and onFailure listeners
     * - takes in user id, and adds the user to the current>users subcollection
     * - a user who selects "apply to be an admin", whilst there is no admin currently in the current subcollection, becomes the new Admin.
     * - else if there is a current Admin, than the current Admin(s) decide which admins are set
     */
    public void setNewAdmin(
            String uuid,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("uuid", uuid);

        db.collection("admin")
                .document("current")
                .collection("users")
                .document(uuid)
                .set(data)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /** Remove an active admin from the current subcollection.
     * Asynchronous call: needs onSuccess and onFailure listeners
     * - takes in user id, and removes the user from the current>users subcollection
     * - only accessed by current admins (step down from position)
     */
    public void removeCurrentAdmin(
            String uid,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        if (uid == null || uid.trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("uid cannot be null or empty"));
            return;
        }

        db.collection("admin")
                .document("current")
                .collection("users")
                .document(uid)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /** Remove a requested admin from the requested subcollection.
     * Asynchronous call: needs onSuccess and onFailure listeners
     * - takes in user id, and removes the user from the requested subcollection
     * - only accessed by current admins (decline promotion request)
     */
    public void removeRequestedAdmin(
            String uid,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        if (uid == null || uid.trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("uid cannot be null or empty"));
            return;
        }

        db.collection("admin")
                .document("requests")
                .collection("requested")
                .document(uid)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /** Get all requested admins from the requested subcollection.
     * Asynchronous call: needs onSuccess and onFailure listeners
     * - returns all user id's from the requested subcollection inside of OnSuccess
     * - populates requested admin item list inside of PromoteNewAdminAcitivity
     */
    public void getRequestedAdmins(
            OnSuccessListener<List<String>> onSuccess,
            OnFailureListener onFailure
    ) {
        db.collection("admin")
                .document("requests")
                .collection("requested")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<String> users = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : snapshot) {
                        String uuid = doc.getString("uuid");

                        if (uuid != null && !uuid.trim().isEmpty()) {
                            users.add(uuid);
                        }
                    }

                    onSuccess.onSuccess(users);
                })
                .addOnFailureListener(onFailure);
    }

    /** Get all current admins from the current subcollection.
     * Asynchronous call: needs onSuccess and onFailure listeners
     * - returns all user id's from the current subcollection inside of OnSuccess
     */
    public void getCurrentAdmins(
            OnSuccessListener<List<String>> onSuccess,
            OnFailureListener onFailure
    ) {
        db.collection("admin")
                .document("current")
                .collection("users")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> adminIds = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        adminIds.add(doc.getId());
                    }
                    onSuccess.onSuccess(adminIds);
                })
                .addOnFailureListener(onFailure);
    }

    /** Remove a requested user from requests subcollection and add them to current subcollection as a "Current Admin".
     * Asynchronous call: needs onSuccess and onFailure listeners
     */
    public void promoteToAdmin(
            String uuid,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("uuid", uuid);

        db.collection("admin")
            .document("current")
            .collection("users")
            .document(uuid)
            .set(data)
            .addOnSuccessListener(unused ->
                db.collection("admin")
                    .document("requests")
                    .collection("requested")
                    .document(uuid)
                    .delete()
                    .addOnSuccessListener(onSuccess)
                    .addOnFailureListener(onFailure)
            )
            .addOnFailureListener(onFailure);
    }
}
