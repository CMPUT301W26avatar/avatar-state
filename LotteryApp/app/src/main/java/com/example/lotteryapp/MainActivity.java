package com.example.lotteryapp;

import android.os.Bundle;
import android.util.Log;

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
    EntrantStorage entrantStorage = new EntrantStorage(db.getDb());

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

        appSignIn(); // on open
    }

    private void appSignIn() {
        final FirebaseAuth auth = db.getAuth();

        // returning user
        if (auth.getCurrentUser() != null) {
            String uuid = auth.getCurrentUser().getUid();
            entrantStorage.setNewEntrant(new Entrant(uuid));
            return;
        }

        // new user
        auth.signInAnonymously().addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                String uid = auth.getCurrentUser().getUid();
                entrantStorage.setNewEntrant(new Entrant(uid));
            }
        });
    }
}