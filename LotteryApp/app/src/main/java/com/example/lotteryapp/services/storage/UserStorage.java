package com.example.lotteryapp.services.storage;
import androidx.annotation.NonNull;

import com.example.lotteryapp.models.User;
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
                    data.put("isAdmin", false); // Default to false
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

    public void deleteUser(String uuid, OnSuccessListener<Void> ok, OnFailureListener fail) {
        userDoc(uuid).delete().addOnSuccessListener(ok).addOnFailureListener(fail);
    }

    public void updateUserProfile(String uuid, String name, String email, String phoneNumber, String location,
                                  OnSuccessListener<Void> ok, OnFailureListener fail) {
        updateUserProfile(uuid, name, email, phoneNumber, location, null, ok, fail);
    }

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

    public void deleteProfilePic(String uuid, OnSuccessListener<Void> ok, OnFailureListener fail) {
        Map<String, Object> update = new HashMap<>();
        update.put("profilePicUrl", FieldValue.delete());
        update.put("updatedAt", FieldValue.serverTimestamp());
        userDoc(uuid).update(update).addOnSuccessListener(ok).addOnFailureListener(fail);
    }
}
