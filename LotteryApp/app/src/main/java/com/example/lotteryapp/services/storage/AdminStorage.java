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
            .get()
            .addOnSuccessListener(doc -> {
                String adminUuid = doc.getString("uuid");
                boolean isAdmin = adminUuid != null && adminUuid.equals(uuid);
                onSuccess.onSuccess(isAdmin);
            })
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

    public void setNewAdmin(String uuid,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("uuid", uuid);

        db.collection("admin")
            .document("current")
            .set(data)
            .addOnSuccessListener(onSuccess)
            .addOnFailureListener(onFailure);
    }

    public void getAdmin(
            OnSuccessListener<String> onSuccess,
            OnFailureListener onFailure
    ) {
        db.collection("admin")
            .document("current")
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (!documentSnapshot.exists()) {
                    onFailure.onFailure(
                            new IllegalStateException("Admin document does not exist")
                    );
                    return;
                }

                String uuid = documentSnapshot.getString("uuid");
                if (uuid == null) {
                    onFailure.onFailure(
                            new IllegalStateException("Admin uuid missing")
                    );
                    return;
                }
                onSuccess.onSuccess(uuid);
            })
            .addOnFailureListener(onFailure);
    }

    public void getRequestedAdminIds(
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

    public void promoteToAdmin(
            String uuid,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("uuid", uuid);

        db.collection("admin")
            .document("current")
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
