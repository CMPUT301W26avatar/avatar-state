package com.example.lotteryapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.lotteryapp.services.AuthService;
import com.example.lotteryapp.R;
import com.example.lotteryapp.services.ServiceLocator;
import com.google.android.material.button.MaterialButton;

public class LoginActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        AuthService auth = ServiceLocator.getAuthService();

        MaterialButton btnLogin = findViewById(R.id.btn_login);
        MaterialButton btnGuestLogin = findViewById(R.id.btn_guest_login);
        TextView tvRegister = findViewById(R.id.tv_register);

        btnLogin.setOnClickListener(v -> {
            EditText etEmail = findViewById(R.id.et_email);
            EditText etPassword = findViewById(R.id.et_password);

            final String email = etEmail.getText().toString();
            final String password = etPassword.getText().toString();

            auth.userSignInCred(email, password); // on open
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        });

        btnGuestLogin.setOnClickListener(v -> {
            auth.userSignInAnon(this.getBaseContext()); // on open
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        });

        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignupActivity.class));
        });
    }
}