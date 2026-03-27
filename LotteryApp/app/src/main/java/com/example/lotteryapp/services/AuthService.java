package com.example.lotteryapp.services;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.example.lotteryapp.services.storage.UserStorage;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * User authentication service
 *      uses device Id to key the user
 */
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

    /**
     * Authenticate a user anonymously by device Id using FirebaseAuth
     */
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

    /**
     * Signs in to user's previous session if a session exists.
     *      This will only sign in if the previous session was w/ a credential sign in
     *          and NOT w/ an anonymous sign in.
     *      returns false if no previous session was found.
     */
    public boolean signInPrevSession() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return false; // Do nothing, there is no prev. session
        }

        if (user.isAnonymous()) {
            return false; // Do nothing for anon user.
        }

        // Sign in user w/ their previous session (Cred. sign in)
        final String UUID = auth.getCurrentUser().getUid();
        userStore.setNewUser(UUID);
        return true;
    }

    /**
     * Set secondary sign-in credentials for the user
     */
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

    /**
     * Anonymous device id sign in via FirebaseAuth
     */
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

    /**
     * Sign the user out via FirebaseAuth
     */
    public void userSignOut() {
        FirebaseUser currUser = auth.getCurrentUser();
        if (currUser == null) {
            return; // Do nothing, the user was never signed in
        }

        if (currUser.isAnonymous()) {
            Log.e("LogOut", "Signed Out Anonymously");
        } else {
            Log.e("LogOut", "Signed Out Creds");
            auth.signOut();
        }
    };
}
