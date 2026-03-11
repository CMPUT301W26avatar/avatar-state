package com.example.lotteryapp.services.storage;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminStorage {
    private final FirebaseFirestore db;

    public AdminStorage(FirebaseFirestore db) {
        this.db = db;
    }

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

    public void getRequestedAdmins(
            OnSuccessListener<List<String>> onSuccess,
            OnFailureListener onFailure
    ) {
        db.collection("admin")
            .document("requests")
            .collection("requested")
            .get()
            .addOnSuccessListener(snapshot -> {
                List<String> ids = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snapshot) {
                    String uuid = doc.getString("uuid");
                    if (uuid != null && !uuid.trim().isEmpty()) {
                        ids.add(uuid);
                    }
                }
                onSuccess.onSuccess(ids);
            })
            .addOnFailureListener(onFailure);
    }

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
