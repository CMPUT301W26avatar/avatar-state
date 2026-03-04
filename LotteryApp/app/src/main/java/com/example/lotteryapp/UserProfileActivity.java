package com.example.lotteryapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class UserProfileActivity extends AppCompatActivity {

    private FirebaseService db;
    private UserStorage userStorage;

    private EditText nameField, emailField, phoneField;
    private Button saveButton;

    private String uuid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        db = new FirebaseService();
        userStorage = new UserStorage(db.getDb());

        uuid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        nameField = findViewById(R.id.editName);
        emailField = findViewById(R.id.editEmail);
        phoneField = findViewById(R.id.editPhone);
        saveButton = findViewById(R.id.btnSaveProfile);

        loadUserProfile();

        saveButton.setOnClickListener(v -> saveProfile());
        Button backButton = findViewById(R.id.btnBack);
        backButton.setOnClickListener(v -> finish());
    }

    private void loadUserProfile() {
        userStorage.getUserProfile(uuid, user -> {
            nameField.setText(user.getName());
            emailField.setText(user.getEmail());
            phoneField.setText(user.getPhoneNumber());
        }, e -> Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show());
    }

    private void saveProfile() {
        String name = nameField.getText().toString().trim();
        String email = emailField.getText().toString().trim();
        String phone = phoneField.getText().toString().trim();

        userStorage.updateUserProfile(uuid, name, email, phone,
                unused -> Toast.makeText(this, "Profile Updated", Toast.LENGTH_SHORT).show(),
                e -> Toast.makeText(this, "Update Failed", Toast.LENGTH_SHORT).show());
    }
}
