package com.example.lotteryapp.activities;

import android.content.Intent;
import android.os.Bundle;
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
            auth.userSignIn(); // on open
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        });

        btnGuestLogin.setOnClickListener(v -> {
            auth.userSignIn(); // on open
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        });

        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignupActivity.class));
        });
    }
}