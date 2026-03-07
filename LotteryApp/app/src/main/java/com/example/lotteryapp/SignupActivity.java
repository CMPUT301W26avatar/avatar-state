package com.example.lotteryapp;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

public class SignupActivity extends AppCompatActivity {

    FirebaseService db = new FirebaseService();
    UserStorage ustore = new UserStorage(db.getDb());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        MaterialButton btnBackToLogin = findViewById(R.id.btn_back_to_login);
        btnBackToLogin.setOnClickListener(v -> finish());

        findViewById(R.id.btn_signup).setOnClickListener(v -> {
            // Logic for signing up
            finish();
        });
    }
    private void appSignIn() {
        final FirebaseAuth auth = db.getAuth();

        // returning user
        if (auth.getCurrentUser() != null) {
            String uuid = auth.getCurrentUser().getUid();
            ustore.setNewUser(new User(uuid));
            return;
        }

        // new user
        auth.signInAnonymously().addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful() && auth.getCurrentUser() != null) {
                    String uid = auth.getCurrentUser().getUid();
                    ustore.setNewUser(new User(uid));
                }
            }
        });
    }
}