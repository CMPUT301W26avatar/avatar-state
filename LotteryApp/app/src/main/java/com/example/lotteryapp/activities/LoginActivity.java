package com.example.lotteryapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.lotteryapp.services.AuthService;
import com.example.lotteryapp.R;
import com.example.lotteryapp.services.ServiceLocator;
import com.google.android.material.button.MaterialButton;

public class LoginActivity extends AppCompatActivity {

    private void credLoginListener(AuthService auth) {
        MaterialButton btnLogin = findViewById(R.id.btn_login);
        btnLogin.setOnClickListener(v -> {
            EditText etEmail = findViewById(R.id.et_email);
            EditText etPassword = findViewById(R.id.et_password);

            final String email = etEmail.getText().toString();
            final String password = etPassword.getText().toString();

            auth.userSignInCred(email, password, getBaseContext()); // on open
            gotoMain();
        });
    }

    private void anonLoginListener(AuthService auth) {
        MaterialButton btnGuestLogin = findViewById(R.id.btn_guest_login);
        btnGuestLogin.setOnClickListener(v -> {
            auth.userSignInAnon(getBaseContext()); // on open
            gotoMain();
        });
    }

    private void setupListeners(AuthService auth) {
        TextView tvRegister = findViewById(R.id.tv_register);

        credLoginListener(auth);
        anonLoginListener(auth);

        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignupActivity.class));
        });
    }

    private void gotoMain() {
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        AuthService auth = ServiceLocator.getAuthService();
        setupListeners(auth);

    }
}