package com.example.lotteryapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
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
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.btnProfile).setOnClickListener(v ->
                startActivity(new Intent(this, UserProfileActivity.class)));
        findViewById(R.id.btnBrowseEvents).setOnClickListener(v ->
                startActivity(new Intent(this, ListEventsActivity.class)));
        findViewById(R.id.btnCreateEvent).setOnClickListener(v -> {
            String uid = db.getAuth().getCurrentUser() != null
                    ? db.getAuth().getCurrentUser().getUid() : null;
            if (uid == null) {
                Toast.makeText(this, "Not signed in yet", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, CreateEventsActivity.class);
            intent.putExtra("organizerId", uid);
            startActivity(intent);
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
        auth.signInAnonymously().addOnCompleteListener(new OnCompleteListener<>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                String uid = auth.getCurrentUser().getUid();
                ustore.setNewUser(new User(uid));
            }
        });
    }
}