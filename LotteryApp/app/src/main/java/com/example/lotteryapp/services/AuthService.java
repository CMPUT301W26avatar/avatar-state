package com.example.lotteryapp.services;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.lotteryapp.services.storage.UserStorage;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class AuthService {
    private final FirebaseFirestore db;
    private final UserStorage userStore;
    private final FirebaseAuth auth;

    public AuthService(FirebaseService fbs) {
        db = fbs.getDb();
        auth = fbs.getAuth();
        userStore = new UserStorage(db);
        setupListeners();
    }

    private void setupListeners() {
        // TODO: Setup an auth.addAuthStateListener()
        //       here if we want to keep user's logged in
        //       for x minutes only then log them out after
        //       that duration?
        // TODO: Can also just remove this
    };

    public void userSignUp(String name, String email, String password, Context context) {
         auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task ->  {
             if (task.isSuccessful() && auth.getCurrentUser() != null) {
                 final String UUID = auth.getCurrentUser().getUid();
                 userStore.setNewUser(UUID, name, email);
                 Log.d("SignUp", "Successfully created user!");
             } else {
                 Toast.makeText(context, "Failed to Sign Up. Try Again.", Toast.LENGTH_SHORT).show();
                 Log.w("SignUp", task.getException());
             }
         });
    }

    public void userSignInCred(String email, String password, Context context) {
        if (!email.contains("@")) {
            Toast.makeText(context, "Please use a valid email", Toast.LENGTH_SHORT).show();
            return;
        }

        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful() && auth.getCurrentUser() != null) {
                final String UUID = auth.getCurrentUser().getUid();
                userStore.setNewUser(UUID);
            } else {
               Log.e( "Login", "Sign in w/ Email & Password failed.");
               Log.w("Login", task.getException());
            }
        });
    }

    public void userSignInAnon(Context context) {
        auth.signInAnonymously().addOnCompleteListener(task -> {
            if (task.isSuccessful() && auth.getCurrentUser() != null) {
                final String UUID = auth.getCurrentUser().getUid();
                userStore.setNewUser(UUID);
            } else {
                Toast.makeText(context, "Failed to Sign In Anonymously. Try Again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public boolean userSignOut() {
        FirebaseUser currUser = auth.getCurrentUser();
        if (currUser == null) {
            // TODO: Error handling here
            Log.e("LogOut", "getCurrentUser() is null, failed to log out");
            return false;
        }

        if (currUser.isAnonymous()) {
            // TODO: Maybe custom anonymous sign out here?
            Log.e("LogOut", "Signed Out Anonymously");
        } else {
            auth.signOut();
        };

        return true;
    };
}
