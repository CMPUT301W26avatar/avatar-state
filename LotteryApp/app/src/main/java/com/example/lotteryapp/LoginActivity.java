package com.example.lotteryapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {
    FirebaseService db = new FirebaseService();
    UserStorage ustore = new UserStorage(db.getDb());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        MaterialButton btnLogin = findViewById(R.id.btn_login);
        MaterialButton btnGuestLogin = findViewById(R.id.btn_guest_login);
        TextView tvRegister = findViewById(R.id.tv_register);

        btnLogin.setOnClickListener(v -> {
            appSignIn(); // on open
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        });

        btnGuestLogin.setOnClickListener(v -> {
            appSignIn(); // on open
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        });

        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignupActivity.class));
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