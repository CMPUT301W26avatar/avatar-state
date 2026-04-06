package com.example.lotteryapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.lotteryapp.R;
import com.example.lotteryapp.services.AuthService;
import com.example.lotteryapp.services.ServiceLocator;
import com.google.android.material.button.MaterialButton;

/**
 * Manages login functionality
 * sets up three buttons for logging in, logging in as a guest or registration.
 *
 */
public class LoginActivity extends AppCompatActivity {

    /**
     * Initialize the UI components for the Login Activity, sets all listeners and authenticates the User
     *  launches main for authenticated users
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        AuthService auth = ServiceLocator.getAuthService();
        setupListeners(auth);
        if (auth.signInPrevSession()) {
            gotoMain();
        }

    }

    /**
     * Set up login button and obtains user input for login authentication
     * @param auth
     */
    private void credLoginListener(AuthService auth) {
        MaterialButton btnLogin = findViewById(R.id.btn_login);
        btnLogin.setOnClickListener(v -> {
            EditText etEmail = findViewById(R.id.et_email);
            EditText etPassword = findViewById(R.id.et_password);

            final String email = etEmail.getText().toString().trim();
            final String password = etPassword.getText().toString();

            if (email.isEmpty() || password.isEmpty()) {
                android.widget.Toast.makeText(
                        LoginActivity.this,
                        "Email and password cannot be empty.",
                        android.widget.Toast.LENGTH_LONG
                ).show();
                return;
            }

            auth.userSignInCred(
                    email,
                    password,
                    getBaseContext(),
                    this::gotoMain,
                    errorMessage -> android.widget.Toast.makeText(
                            LoginActivity.this,
                            errorMessage == null ? "Login failed." : errorMessage,
                            android.widget.Toast.LENGTH_LONG
                    ).show()
            );
        });
    }

    /**
     * Set up guest login button
     * @param auth
     */
    private void anonLoginListener(AuthService auth) {
        MaterialButton btnGuestLogin = findViewById(R.id.btn_guest_login);
        btnGuestLogin.setOnClickListener(v -> {
            auth.userSignInAnon(getBaseContext()); // on open
            gotoMain();
        });
    }

    /**
     * Manages registation button, starts new sign up activity
     * @param auth
     */
    private void setupListeners(AuthService auth) {
        TextView tvRegister = findViewById(R.id.tv_register);

        credLoginListener(auth);
        anonLoginListener(auth);

        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignupActivity.class));
        });
    }

    /**
     * Once successfully authenticated, this function starts main activity
     */
    private void gotoMain() {
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finish();
    }
}