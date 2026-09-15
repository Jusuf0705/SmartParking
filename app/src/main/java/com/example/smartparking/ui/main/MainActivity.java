package com.example.smartparking.ui.main;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smartparking.data.FirebaseUtils;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FIREBASE_TEST";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            // Explicit init — not strictly required, but helps when diagnosing connection issues
            FirebaseApp.initializeApp(this);
        } catch (Exception ignore) {}

        // (optional) To enable local disk cache, call
        // FirebaseDatabase.getInstance(...).setPersistenceEnabled(true) exactly
        // once, before any getReference() call.

        DatabaseReference ref = FirebaseUtils.root().child("_test/hello");

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

        // READ (one-time)
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