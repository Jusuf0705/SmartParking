package com.example.smartparking.ui.main;

import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.*;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FIREBASE_TEST";
    private static final String DB_URL = "https://smartparking-744d8-default-rtdb.europe-west1.firebasedatabase.app";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            // Eksplicitna inicijalizacija (nije uvijek potrebna, ali pomaže u dijagnostici)
            FirebaseApp.initializeApp(this);
        } catch (Exception ignore) {}

        FirebaseDatabase db = FirebaseDatabase.getInstance(DB_URL);

        // (opciono) Uključi lokalni disk cache – ne smetа testu:
        // db.setPersistenceEnabled(true); // POZOR: poziva se SAMO jednom u app-u prije prvog getReference()

        DatabaseReference ref = db.getReference("_test/hello");

        // WRITE
        ref.setValue("Pozdrav iz Android aplikacije 👋")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "✅ WRITE OK");
                    } else {
                        Exception e = task.getException();
                        Log.e(TAG, "❌ WRITE ERR: " + (e != null ? e.getMessage() : "unknown"));
                    }
                });

        // READ (jednokratni)
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                String val = snap.getValue(String.class);
                Log.d(TAG, "📥 READ OK: " + val);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "❌ READ ERR: " + error.getMessage());
            }
        });
    }
}
