package com.example.lotteryapp.activities;

import android.content.Intent;
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

/** Handles promoting of new Admins and resignation of existing admins
 *      requested admins are shown as a AdminListFragment
 *      thus, promotion dialog logic lives in AdminListFragment
 * resignation logic lives here
 */

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
                    Toast.makeText(this, "Could not determine current user", Toast.LENGTH_LONG).show();
                    return;
                }

                AdminStorage astore = ServiceLocator.getAdminStorage();

                // count the current number of admins
                astore.getCurrentAdmins(adminIds -> {
                    int otherAdminCount = 0;
                    for (String adminId : adminIds) {
                        if (adminId != null && !adminId.equals(currentUid)) {
                            otherAdminCount++;
                        }
                    }

                    // enforce at least one Admin for the app
                    if (otherAdminCount == 0) {
                        new AlertDialog.Builder(this)
                            .setTitle("Cannot Step Down")
                            .setMessage("You are the last remaining admin. Please promote another admin before stepping down.")
                            .setPositiveButton("OK", null)
                            .show();
                        return;
                    }

                    // current user is not the only admin, than they are allowed to step down
                    new AlertDialog.Builder(this)
                        .setTitle("Step Down as Admin")
                        .setMessage("Are you sure you want to step down as admin?")
                        .setPositiveButton("Step Down", (dialog, which) -> {
                            astore.removeCurrentAdmin(currentUid, unused -> {
                                Toast.makeText(this, "You have stepped down as admin", Toast.LENGTH_LONG).show();

                                Intent intent = new Intent(this, MainActivity.class);
                                intent.putExtra("open_fragment", "profile");
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                finish();
                            }, e -> {
                                Toast.makeText(this, "Failed to step down as admin", Toast.LENGTH_LONG).show();
                                e.printStackTrace();
                            }
                            );
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                // getCurrentAdmins failed to retrieve current admins from Firebase
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
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.admin_settings_container, AdminListFragment.newInstance(AdminListFragment.TYPE_REQUESTED_ADMINS))
                .commit();
        }
    }
}