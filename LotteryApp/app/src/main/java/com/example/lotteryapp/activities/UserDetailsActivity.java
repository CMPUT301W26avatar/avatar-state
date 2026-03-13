package com.example.lotteryapp.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.lotteryapp.R;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.UserStorage;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;

public class UserDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "userId";
    public static final String EXTRA_ADMIN_MODE = "isAdminMode";

    private EditText etName;
    private EditText etPhone;
    private EditText etEmail;
    private EditText etLocation;
    private TextView tvDeviceId;
    private View tvDeviceIdLabel;
    private MaterialButton btnSave;
    private MaterialButton btnUserDel;
    private MaterialButton btnRemoveProfile;
    private MaterialButton btnDeleteProfilePic;
    private ImageView ivProfilePic;

    private UserStorage ustore;
    private String uid;
    private boolean isAdminMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_details);

        // use ServiceLocator to get user id and User Storage instance
        uid = getIntent().getStringExtra(EXTRA_USER_ID);
        if (uid == null) {
            uid = ServiceLocator.uid();
        }

        isAdminMode = getIntent().getBooleanExtra(EXTRA_ADMIN_MODE, false);
        ustore = ServiceLocator.getUserStorage();

        // moved to bindViews() to avoid null pointer exception
        bindViews();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        if (uid == null || uid.trim().isEmpty()) {
            Toast.makeText(this, "User not signed in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadProfile();

        btnSave.setOnClickListener(v -> saveProfile());
        btnUserDel.setOnClickListener(v -> userRemoveProfile());
        
        setupAdminActions();
    }

    private void bindViews() {
        etName = findViewById(R.id.et_name);
        etPhone = findViewById(R.id.et_phone);
        etEmail = findViewById(R.id.et_email);
        etLocation = findViewById(R.id.et_location);
        tvDeviceId = findViewById(R.id.tv_device_id);
        tvDeviceIdLabel = findViewById(R.id.tv_device_id_label);
        btnSave = findViewById(R.id.btn_save_profile);
        btnUserDel = findViewById(R.id.btn_userdel_profile);
        btnRemoveProfile = findViewById(R.id.btn_remove_profile);
        btnDeleteProfilePic = findViewById(R.id.btn_delete_profile_pic);
        ivProfilePic = findViewById(R.id.iv_profile_pic_large);
    }

    /**
     * Checks if user is admin
     *      shows button to access admin actions
     *      delete profile button
     *      delete image button
     *
     *      NOTE: no tested idk if this even works
     */
    private void setupAdminActions() {
        if (isAdminMode) {
            btnRemoveProfile.setVisibility(View.VISIBLE);
            tvDeviceId.setVisibility(View.VISIBLE);
            tvDeviceIdLabel.setVisibility(View.VISIBLE);
            
            btnRemoveProfile.setOnClickListener(v -> {
                ustore.deleteUser(uid, unused -> {
                    Toast.makeText(this, "Profile Removed", Toast.LENGTH_SHORT).show();
                    finish();
                }, e -> Toast.makeText(this, "Failed to remove profile", Toast.LENGTH_SHORT).show());
            });

            btnDeleteProfilePic.setOnClickListener(v -> {
                ustore.deleteProfilePic(uid, unused -> {
                    Toast.makeText(this, "Image Deleted", Toast.LENGTH_SHORT).show();
                    ivProfilePic.setImageResource(R.drawable.ic_profile);
                    btnDeleteProfilePic.setVisibility(View.GONE);
                }, e -> Toast.makeText(this, "Failed to delete image", Toast.LENGTH_SHORT).show());
            });
        }
    }

    private void loadProfile() {
        ustore.getUserProfile(
                uid,
                user -> {
                    etName.setText(user.getName() != null ? user.getName() : "");
                    etPhone.setText(user.getPhoneNumber() != null ? user.getPhoneNumber() : "");
                    etEmail.setText(user.getEmail() != null ? user.getEmail() : "");
                    etLocation.setText(user.getLocation() != null ? user.getLocation() : "");
                    tvDeviceId.setText(user.getUUID());

                    // Admin can see delete buttons
                    if (isAdminMode && user.getProfilePicUrl() != null && !user.getProfilePicUrl().isEmpty()) {
                        btnDeleteProfilePic.setVisibility(View.VISIBLE);
                    }
                    else { // no one else can!
                        btnDeleteProfilePic.setVisibility(View.GONE);
                    }
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

        ustore.upsertUserProfile(
                uid,
                name,
                email,
                phone,
                location,
                unused -> {
                    Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show();
                    if (!isAdminMode) {
                        finish();
                    }
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

    private void userRemoveProfile() {

        String uuid = ServiceLocator.uid(); // get current user id

        ServiceLocator.getUserStorage().cascadeUserDelete(
                uuid,
                unused -> {
                    Toast.makeText(this, "Profile deleted", Toast.LENGTH_SHORT).show();
                    
                    // launch LoginActivity
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    
                    // finish UserDetailsActivity
                    finish();
                },
                e -> {
                    android.util.Log.e("UserDetailsActivity",
                            "Failed to delete user profile for uid=" + uuid, e);
                    Toast.makeText(this,
                            "Failed to delete profile: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
        );
    }
}
