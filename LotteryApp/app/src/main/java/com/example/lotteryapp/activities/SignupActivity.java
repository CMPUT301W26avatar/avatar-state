package com.example.lotteryapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.lotteryapp.R;
import com.example.lotteryapp.services.AuthService;
import com.example.lotteryapp.services.ServiceLocator;
import com.google.android.material.button.MaterialButton;

/**
 * Obtain user inputs for user registration
 * manages buttons for signing up and going back to login screen
 * starts new main activity after successfully signing up
 */
public class SignupActivity extends AppCompatActivity {

    /**
     * Initialize UI components (set listeners for all buttons)
     *  get all necessary database classes from service locator
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        AuthService auth = ServiceLocator.getAuthService();
        setupListeners(auth);
    }

    /**
     * Set up all of the textViews and buttons for the Sign Up Activity UI components
     */
    private void setupListeners(AuthService auth) {
        EditText etName = findViewById(R.id.et_name);
        EditText etEmail = findViewById(R.id.et_email);
        EditText etPassword = findViewById(R.id.et_password);
        EditText etConfirmPassword = findViewById(R.id.et_confirm_password);
        android.widget.CheckBox cbAcceptTOS = findViewById(R.id.cb_terms);

        MaterialButton btnBackToLogin = findViewById(R.id.btn_back_to_login);
        btnBackToLogin.setOnClickListener(v -> finish());

        findViewById(R.id.btn_signup).setOnClickListener(v -> {
            String name = (etName != null) ? etName.getText().toString().trim() : "";
            String email = (etEmail != null) ? etEmail.getText().toString().trim() : "";
            String password = (etPassword != null) ? etPassword.getText().toString() : "";
            String confirmPassword = (etConfirmPassword != null)
                    ? etConfirmPassword.getText().toString()
                    : "";

            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(
                        SignupActivity.this,
                        "All fields are required.",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            if (!password.equals(confirmPassword)) {
                Toast.makeText(
                        SignupActivity.this,
                        "Password and confirm password must match.",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            if (cbAcceptTOS == null || !cbAcceptTOS.isChecked()) {
                Toast.makeText(
                        SignupActivity.this,
                        "You must accept the Terms of Service to sign up.",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            // Do NOT navigate here.
            // Only navigate after auth succeeds.
            auth.userSignUp(
                    name,
                    email,
                    password,
                    getBaseContext(),
                    () -> {
                        startActivity(new Intent(SignupActivity.this, MainActivity.class));
                        finish();
                    },
                    errorMessage -> Toast.makeText(
                            SignupActivity.this,
                            errorMessage == null ? "Sign up failed." : errorMessage,
                            Toast.LENGTH_LONG
                    ).show()
            );
        });
    }
}