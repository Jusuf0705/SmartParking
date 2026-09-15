package com.example.smartparking.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.example.smartparking.ui.auth.LoginActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.Calendar;
import java.util.Locale;

public class AdminPanel extends AppCompatActivity {

    private TextView tvActiveSessions, tvRevenueToday;

    // Kept for listener cleanup in onDestroy
    private DatabaseReference sessionsRef;
    private ValueEventListener sessionsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_panel);

        // These rows are plain LinearLayouts in the XML, not Buttons
        View btnUsers   = findViewById(R.id.btnUsers);
        View btnParking = findViewById(R.id.btnParking);
        View btnZones   = findViewById(R.id.btnZones);
        View btnFines   = findViewById(R.id.btnFines);

        MaterialButton btnSignOut = findViewById(R.id.btnSignOut);

        tvActiveSessions = findViewById(R.id.tvActiveSessions);
        tvRevenueToday   = findViewById(R.id.tvRevenueToday);

        btnUsers.setOnClickListener(v ->
                startActivity(new Intent(this, AdminManageAccountsActivity.class)));

        btnParking.setOnClickListener(v ->
                startActivity(new Intent(this, ManageParkingActivity.class)));

        btnZones.setOnClickListener(v ->
                startActivity(new Intent(this, ManageZonesActivity.class)));

        btnFines.setOnClickListener(v ->
                startActivity(new Intent(this, ManageFinesActivity.class)));

        btnSignOut.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(AdminPanel.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        loadStats();
    }

    // -- Stats: active sessions + today's revenue --

    private void loadStats() {
        sessionsRef = FirebaseUtils.sessionsRef();

        sessionsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot ds) {
                long now = System.currentTimeMillis();
                long startOfDay = startOfDayMs();

                int activeCount = 0;
                double revenueToday = 0.0;

                for (DataSnapshot s : ds.getChildren()) {
                    // Active session: status ACTIVE and not yet ended
                    String status = s.child("status").getValue(String.class);
                    Long endTime = s.child("endTime").getValue(Long.class);

                    if ("ACTIVE".equalsIgnoreCase(status)
                            && endTime != null && endTime > now) {
                        activeCount++;
                    }

                    // Counts toward today's revenue if it started today
                    Long startTime = s.child("startTime").getValue(Long.class);
                    Double amount = s.child("amount").getValue(Double.class);

                    if (startTime != null && startTime >= startOfDay && amount != null) {
                        revenueToday += amount;
                    }
                }

                if (tvActiveSessions != null) {
                    tvActiveSessions.setText(String.valueOf(activeCount));
                }
                if (tvRevenueToday != null) {
                    // Whole numbers shown without decimals, otherwise 2 decimal places
                    if (revenueToday == Math.floor(revenueToday)) {
                        tvRevenueToday.setText(String.valueOf((long) revenueToday));
                    } else {
                        tvRevenueToday.setText(
                                String.format(Locale.getDefault(), "%.2f", revenueToday));
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Read failed — leave the current display as is
            }
        };

        sessionsRef.addValueEventListener(sessionsListener);
    }

    /** Start of today (00:00:00.000, local time), used to filter "today's revenue". */
    private long startOfDayMs() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sessionsRef != null && sessionsListener != null) {
            sessionsRef.removeEventListener(sessionsListener);
        }
    }
}