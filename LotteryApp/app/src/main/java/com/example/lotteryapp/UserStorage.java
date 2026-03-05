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

public class UserStorage {
    private final FirebaseFirestore db;

    public UserStorage(FirebaseFirestore db) {
        this.db = db;
    }

    private DocumentReference userDoc(String uuid) {
        return db.collection("users").document(uuid);
    }

    public void setNewUser(User user) {
        final String uuid = user.getUUID();
        final DocumentReference ref = db.collection("users").document(uuid);

        // Transaction so createdAt is only set once.
        db.runTransaction(new Transaction.Function<Void>() {
            @Override
            public Void apply(@NonNull Transaction transaction) throws FirebaseFirestoreException {
                DocumentSnapshot snapshot = transaction.get(ref);
                if (!snapshot.exists()) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("deviceID", uuid);
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

    // Firebase connector: user profile getter with failure listener to alert bad user requests
    public void getUserProfile(final String uuid,
            final OnSuccessListener<User> ok,
            OnFailureListener fail) {
        userDoc(uuid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    User user = new User(uuid);
                    if (snapshot.exists()) {
                        user.setName(snapshot.getString("name"));
                        user.setEmail(snapshot.getString("email"));
                        user.setPhoneNumber(snapshot.getString("phoneNumber"));
                    }
                    ok.onSuccess(user);
                })
                .addOnFailureListener(fail);
    }

    // Firebase connector: sets the updated values for the user in Firebase
    public void updateUserProfile(
            String uuid,
            String name,
            String email,
            String phoneNumber,
            OnSuccessListener<Void> ok,
            OnFailureListener fail
    ) {
        Map<String, Object> update = new HashMap<>();

        if (name != null) update.put("name", name);
        if (email != null) update.put("email", email);
        if (phoneNumber != null) update.put("phoneNumber", phoneNumber);

        update.put("updatedAt", FieldValue.serverTimestamp());
        update.put("deviceID", uuid);

        userDoc(uuid)
                .set(update, SetOptions.merge())
                .addOnSuccessListener(ok)
                .addOnFailureListener(fail);
    }

    public void updateName(String uuid, String name, OnSuccessListener<Void> ok, OnFailureListener fail) {
        updateUserProfile(uuid, name, null, null, ok, fail);
    }

    public void updateEmail(String uuid, String email, OnSuccessListener<Void> ok, OnFailureListener fail) {
        updateUserProfile(uuid, null, email, null, ok, fail);
    }

    public void updatePhoneNumber(String uuid, String phone, OnSuccessListener<Void> ok, OnFailureListener fail) {
        updateUserProfile(uuid, null, null, phone, ok, fail);
    }
}