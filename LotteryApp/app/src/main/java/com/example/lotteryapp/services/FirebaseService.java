package com.example.lotteryapp.services;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.FirebaseAuth;


/** Firebase Wrapper: centralized db access class
 * - Initialize db and pass onto "Storage" classes
 * - Prevent calling FirebaseFirestore.getInstance() inside of every Service method that requires it
 *
 */

public final class FirebaseService {
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    /// return either an instance of FirebaseFirestore for db operations, or FirebaseAuth for authentication
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
