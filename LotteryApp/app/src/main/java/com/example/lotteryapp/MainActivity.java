package com.example.lotteryapp;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {
    // Init Firestore database
    FirebaseService db = new FirebaseService();
    UserStorage ustore = new UserStorage(db.getDb());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                // TODO: Show Home Fragment
                return true;
            } else if (itemId == R.id.nav_search) {
                // TODO: Show Search Fragment
                return true;
            } else if (itemId == R.id.nav_joined) {
                // TODO: Show Joined Fragment
                return true;
            } else if (itemId == R.id.nav_manage) {
                // TODO: Show Manage Fragment
                return true;
            } else if (itemId == R.id.nav_profile) {
                // TODO: Show Profile Fragment
                return true;
            }
            return false;
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0); // Keep bottom padding 0 for nav bar
            return insets;
        });

        appSignIn(); // on open
    }

    private void appSignIn() {
        final FirebaseAuth auth = db.getAuth();

        // returning user
        if (auth.getCurrentUser() != null) {
            String uuid = auth.getCurrentUser().getUid();
            ustore.setNewUser(new User(uuid));
            return;
        }

        // new user
        auth.signInAnonymously().addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful() && auth.getCurrentUser() != null) {
                    String uid = auth.getCurrentUser().getUid();
                    ustore.setNewUser(new User(uid));
                }
            }
        });
    }
}