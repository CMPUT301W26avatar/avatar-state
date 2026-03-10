package com.example.lotteryapp.activities;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.lotteryapp.R;
import com.example.lotteryapp.fragments.AdminListFragment;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.AdminStorage;
import com.google.android.material.button.MaterialButton;

public class PromoteNewAdminActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_promote_new_admin);

        ImageButton btnClose = findViewById(R.id.btn_close_promote_admin);
        MaterialButton btnStepDown = findViewById(R.id.btn_admin_step_down);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> finish());
        }

        if (btnStepDown != null) {
            btnStepDown.setOnClickListener(v -> {
                String currentUid = ServiceLocator.uid();
                if (currentUid == null || currentUid.trim().isEmpty()) {
                    Toast.makeText(
                            this,
                            "Could not determine current user",
                            Toast.LENGTH_LONG
                    ).show();
                    return;
                }

                AdminStorage astore = ServiceLocator.getAdminStorage();

                astore.getCurrentAdmins(adminIds -> {
                    int otherAdminCount = 0;
                    for (String adminId : adminIds) {
                        if (adminId != null && !adminId.equals(currentUid)) {
                            otherAdminCount++;
                        }
                    }

                    if (otherAdminCount == 0) {
                        new AlertDialog.Builder(this)
                                .setTitle("Cannot Step Down")
                                .setMessage("You are the last remaining admin. Please promote another admin before stepping down.")
                                .setPositiveButton("OK", null)
                                .show();
                        return;
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("Step Down as Admin")
                            .setMessage("Are you sure you want to step down as admin?")
                            .setPositiveButton("Step Down", (dialog, which) -> {
                                astore.removeCurrentAdmin(
                                        currentUid,
                                        unused -> {
                                            Toast.makeText(
                                                    this,
                                                    "You have stepped down as admin",
                                                    Toast.LENGTH_LONG
                                            ).show();

                                            finish();
                                        },
                                        e -> {
                                            Toast.makeText(
                                                    this,
                                                    "Failed to step down as admin",
                                                    Toast.LENGTH_LONG
                                            ).show();
                                            e.printStackTrace();
                                        }
                                );
                            })
                            .setNegativeButton("Cancel", null)
                            .show();

                }, e -> {
                    Toast.makeText(
                            this,
                            "Failed to check current admins",
                            Toast.LENGTH_LONG
                    ).show();
                    e.printStackTrace();
                });
            });
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(
                            R.id.admin_settings_container,
                            AdminListFragment.newInstance(AdminListFragment.TYPE_REQUESTED_ADMINS)
                    )
                    .commit();
        }
    }
}