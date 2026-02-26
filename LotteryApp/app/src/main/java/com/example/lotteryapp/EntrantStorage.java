package com.example.lotteryapp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

import android.util.Log;
import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;


/* Storage layer for Entrant objects (storage is store+retrieve)
    - Firebase access (call FirebaseService to get FirebaseFirestore)
    - decision: differentiate storage into classes for each major data model
    -           - prevents the creation of a storage "god-object"
    -           - can still have a firebase / db "wrapper" type class that calls on each of the storage classes
*/

public class EntrantStorage {
    private final FirebaseFirestore db;
    public EntrantStorage(FirebaseFirestore db) {
        this.db = db;
    }

    /* Checks collection for entrants by this device id
        If not found, adds a new entrant and a timestamp of when they were entered
        If found, overwrites.
     */
    public void setNewEntrant(Entrant entrant) {
        String uuid = entrant.getUUID();

        DocumentReference ref = db.collection("entrants").document(uuid);

        Map<String, Object> data = new HashMap<>();
        data.put("deviceID", uuid);
        data.put("createdAt", FieldValue.serverTimestamp());

        // no merge because we want to keep createdAt the same throughout
        // Note: right now createdAt is updated everytime
        ref.set(data);
    }
}
