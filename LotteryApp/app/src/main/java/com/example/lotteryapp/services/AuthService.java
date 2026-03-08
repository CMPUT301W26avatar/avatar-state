package com.example.lotteryapp.services;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.lotteryapp.models.User;
import com.example.lotteryapp.services.storage.UserStorage;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

public class AuthService {
    private final FirebaseService db;
    private final UserStorage userStore;

    public AuthService(FirebaseService fbs) {
        db = fbs;
        userStore = new UserStorage(db.getDb());
    }

    public void userSignIn() {
        final FirebaseAuth auth = db.getAuth();

        // returning user
        if (auth.getCurrentUser() != null) {
            Log.d("SignIn", "Hello, Returning User!");
            String uuid = auth.getCurrentUser().getUid();
            userStore.setNewUser(new User(uuid));
            return;
        }

        // new user
        auth.signInAnonymously().addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful() && auth.getCurrentUser() != null) {
                    String uid = auth.getCurrentUser().getUid();
                    userStore.setNewUser(new User(uid));
                    Log.d("SignIn", "Hello, New User!");
                }
            }
        });
    };
    public void userSignOut() {
        final FirebaseAuth auth = db.getAuth();
        auth.signOut();
    };
}
