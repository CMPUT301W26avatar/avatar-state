package com.example.lotteryapp;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class UserDetailsActivity extends AppCompatActivity {

    private EditText etName;
    private EditText etPhone;
    private EditText etEmail;
    private EditText etLocation;
    private MaterialButton btnSave;

    private UserStorage ustore;
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_details);

        // use ServiceLocator to get user id and User Storage instance
        uid = ServiceLocator.uid();
        ustore = ServiceLocator.userStorage();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        etName = findViewById(R.id.et_name);
        etPhone = findViewById(R.id.et_phone);
        etEmail = findViewById(R.id.et_email);
        etLocation = findViewById(R.id.et_location);
        btnSave = findViewById(R.id.btn_save_profile);

        toolbar.setNavigationOnClickListener(v -> finish());

        if (uid == null || uid.trim().isEmpty()) {
            Toast.makeText(this, "User not signed in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadProfile();

        btnSave.setOnClickListener(v -> saveProfile());
    }

    private void loadProfile() {
        ustore.getUserProfile(
                uid,
                user -> {
                    etName.setText(user.getName() != null ? user.getName() : "");
                    etPhone.setText(user.getPhoneNumber() != null ? user.getPhoneNumber() : "");
                    etEmail.setText(user.getEmail() != null ? user.getEmail() : "");
                    etLocation.setText(user.getLocation() != null ? user.getLocation() : "");
                },
                e -> {
                    android.util.Log.e("UserDetailsActivity",
                            "Failed to load user profile for uid=" + uid, e);

                    Toast.makeText(this,
                            "Failed to load profile",
                            Toast.LENGTH_SHORT).show();
                }
        );
    }

    private void populateFields(@NonNull DocumentSnapshot doc) {
        if (!doc.exists()) {
            return;
        }

        String name = doc.getString("name");
        String phone = doc.getString("phone");
        String email = doc.getString("email");
        String location = doc.getString("location");

        etName.setText(name != null ? name : "");
        etEmail.setText(email != null ? name : "");
        etPhone.setText(phone != null ? phone : "");
        etLocation.setText(location != null ? location : "");
    }

    private void saveProfile() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String location = etLocation.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show();
            return;
        }

        ustore.updateUserProfile(
                uid,
                name,
                email,
                phone,
                location,
                unused -> {
                    Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show();
                    finish();
                },
                e -> {
                    android.util.Log.e("UserDetailsActivity",
                            "Failed to save user profile for uid=" + uid, e);

                    Toast.makeText(this,
                            "Failed to save profile",
                            Toast.LENGTH_SHORT).show();
                }
        );
    }
}