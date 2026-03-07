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

import org.w3c.dom.Document;

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

    public void getUserProfile(final String uuid,
            final OnSuccessListener<User> ok, OnFailureListener fail) {
        userDoc(uuid)
                .get()
                .addOnSuccessListener(new OnSuccessListener<com.google.firebase.firestore.DocumentSnapshot>() {
                    @Override
                    public void onSuccess(com.google.firebase.firestore.DocumentSnapshot snapshot) {
                        User user = new User(uuid);
                        if (snapshot.exists()) {
                            user.setName(snapshot.getString("name"));
                            user.setEmail(snapshot.getString("email"));
                            user.setPhoneNumber(snapshot.getString("phoneNumber"));
                            user.setLocation(snapshot.getString("location"));
                        }
                        ok.onSuccess(user);
                    }
                }).addOnFailureListener(fail);
    }

    public void updateUserProfile(String uuid, String name, String email, String phoneNumber, String location,
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
        update.put("updatedAt", FieldValue.serverTimestamp());
        update.put("deviceID", uuid);

        userDoc(uuid)
                .set(update, SetOptions.merge())
                .addOnSuccessListener(ok)
                .addOnFailureListener(fail);
    }
}

