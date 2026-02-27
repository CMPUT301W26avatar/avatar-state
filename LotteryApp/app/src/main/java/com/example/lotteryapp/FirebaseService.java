package com.example.lotteryapp;

import android.content.Context;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.FirebaseAuth;

/* Firebase wrapper
    - Initialize db and pass onto "Storage" classes
 */
public final class FirebaseService {
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public FirebaseService() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public FirebaseAuth getAuth() {
        return auth;
    }
    public FirebaseFirestore getDb() {
        return db;
    }
}
