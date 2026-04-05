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

        MaterialButton btnBackToLogin = findViewById(R.id.btn_back_to_login);
        btnBackToLogin.setOnClickListener(v -> finish());

        findViewById(R.id.btn_signup).setOnClickListener(v -> {
            String name = (etName != null) ? etName.getText().toString() : "";
            String email = (etEmail != null) ? etEmail.getText().toString() : "";
            String password = (etPassword != null) ? etPassword.getText().toString() : "";


            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(getBaseContext(),
                        "One of the fields are empty, please fill it in.", Toast.LENGTH_LONG).show();
            }

            Log.d("SignUp", name);
            Log.d("SignUp", email);
            Log.d("SignUp", password);

            auth.userSignUp(name, email, password, getBaseContext());
            auth.userSignInCred(email, password, getBaseContext());

            startActivity(new Intent(SignupActivity.this, MainActivity.class));
            finish();
        });
    }

}