package com.example.lotteryapp.services;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.lotteryapp.models.User;
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
    private FirebaseAuth auth;

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
    };

    public void userSignUp() {

    }

    public boolean userSignInCred(String email, String password) {
        // returning user
        if (auth.getCurrentUser() != null) {
            Log.d("SignIn", "Hello, Returning User!");
            String uuid = auth.getCurrentUser().getUid();
            userStore.setNewUser(new User(uuid));
        }

        // temp
        return auth.getCurrentUser() != null;
    }

    public boolean userSignInAnon(Context context) {
        auth.signInAnonymously().addOnCompleteListener(task -> {
            if (task.isSuccessful() && auth.getCurrentUser() != null) {
                final String UID = auth.getCurrentUser().getUid();
                userStore.setNewUser(new User(UID));
                Log.d("SignIn", "Hello, New User!");
            } else {
                Toast.makeText(context, "Failed to Sign In Anonymously. Try Again.", Toast.LENGTH_SHORT).show();
            }
        });

        return auth.getCurrentUser() != null;
    }

    public void userSignOut() {
        // TODO: Do something else here that's not auth.signOut().
        //       It will, when used with anon login, create
        //       a new unique device ID when logging back in
    };
}
