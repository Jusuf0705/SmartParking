package com.example.smartparking.ui.admin;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.example.smartparking.ui.auth.LoginActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ControlPanelActivity extends AppCompatActivity {

    private TextInputEditText etPlate;
    private AppCompatButton   btnSearch, btnIssueFine;
    private MaterialButton    btnExit;

    private MaterialCardView  cardResult;
    private TextView          tvStatusLabel, tvStatusBadge;
    private TextView          tvZoneValue, tvPlateValue, tvUntilValue, tvUntilLabel, tvParkingValue;

    private TextView tvControllerName, tvCheckedCount, tvFineCount;

    private DatabaseReference zonesRef, finesRef;

    private String foundLotId, foundSpaceId, foundPlate, foundZoneName;
    private boolean notFoundInSpaces = false;
    private boolean lastHadActivePayment = false;

    private int checkedToday = 0;

    private static final int COLOR_GREEN = 0xFF22C55E;
    private static final int COLOR_RED   = 0xFFEF4444;
    private static final double FINE_AMOUNT = 20.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kontrola_panel);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        zonesRef = FirebaseUtils.root().child("zones");
        finesRef = FirebaseUtils.finesRef();

        etPlate        = findViewById(R.id.etPlate);
        btnSearch      = findViewById(R.id.btnSearch);
        btnIssueFine   = findViewById(R.id.btnIssueFine);
        btnExit        = findViewById(R.id.btnExit);
        cardResult     = findViewById(R.id.cardResult);
        tvStatusLabel  = findViewById(R.id.tvStatusLabel);
        tvStatusBadge  = findViewById(R.id.tvStatusBadge);
        tvZoneValue    = findViewById(R.id.tvZoneValue);
        tvPlateValue   = findViewById(R.id.tvPlateValue);
        tvUntilValue   = findViewById(R.id.tvUntilValue);
        tvUntilLabel   = findViewById(R.id.tvUntilLabel);
        tvParkingValue = findViewById(R.id.tvParkingValue);

        tvControllerName = findViewById(R.id.tvControllerName);
        tvCheckedCount   = findViewById(R.id.tvCheckedCount);
        tvFineCount      = findViewById(R.id.tvFineCount);

        cardResult.setVisibility(View.GONE);
        btnIssueFine.setVisibility(View.GONE);

        etPlate.setFilters(new InputFilter[]{
                (source, start, end, dest, dstart, dend) -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i = start; i < end; i++) {
                        char c = source.charAt(i);
                        if (Character.isLetterOrDigit(c) || c == '-')
                            sb.append(Character.toUpperCase(c));
                    }
                    return sb.toString();
                }
        });

        btnSearch.setOnClickListener(v -> search());
        btnIssueFine.setOnClickListener(v -> confirmIssueFine());
        btnExit.setOnClickListener(v -> logout());

        etPlate.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
        });

        loadControllerName();
        loadFineCountToday();
        updateCheckedCount();
    }

    private void loadControllerName() {
        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) return;

        FirebaseUtils.user(me.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                String first = ds.child("firstName").getValue(String.class);
                String last  = ds.child("lastName").getValue(String.class);
                String full  = ((first == null ? "" : first.trim()) + " "
                        + (last == null ? "" : last.trim())).trim();
                if (TextUtils.isEmpty(full) && me.getDisplayName() != null) full = me.getDisplayName();
                if (TextUtils.isEmpty(full) && me.getEmail() != null) full = me.getEmail();
                if (tvControllerName != null && !TextUtils.isEmpty(full))
                    tvControllerName.setText(full);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    private void loadFineCountToday() {
        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null || tvFineCount == null) return;
        final String uid = me.getUid();
        final long dayStart = startOfToday();

        finesRef.orderByChild("issuedBy").equalTo(uid)
                .addValueEventListener(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot ds) {
                        int count = 0;
                        for (DataSnapshot f : ds.getChildren()) {
                            Long at = f.child("issuedAt").getValue(Long.class);
                            if (at != null && at >= dayStart) count++;
                        }
                        if (tvFineCount != null) tvFineCount.setText(String.valueOf(count));
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    private void updateCheckedCount() {
        if (tvCheckedCount != null) tvCheckedCount.setText(String.valueOf(checkedToday));
    }

    private long startOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    // Search: fetch all sessions, normalize and compare manually
    private void search() {
        final String plateNorm = normalizePlateStd(etPlate.getText() != null
                ? etPlate.getText().toString() : "");

        if (TextUtils.isEmpty(plateNorm)) {
            Toast.makeText(this, "Unesite registarsku oznaku.", Toast.LENGTH_SHORT).show();
            return;
        }

        cardResult.setVisibility(View.GONE);
        btnIssueFine.setVisibility(View.GONE);
        btnSearch.setText("Pretražujem...");
        btnSearch.setEnabled(false);

        foundLotId = null; foundSpaceId = null;
        foundPlate = plateNorm; foundZoneName = null;
        notFoundInSpaces = false;
        lastHadActivePayment = false;

        // Fetch all sessions (also handles older records without plateNormalized field)
        FirebaseUtils.sessionsRef().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot all) {
                btnSearch.setText("Traži");
                btnSearch.setEnabled(true);
                checkedToday++; updateCheckedCount();

                // Find the most recent session for this plate
                DataSnapshot bestSnap = null;
                long bestEnd = 0;

                for (DataSnapshot s : all.getChildren()) {
                    String p = s.child("plate").getValue(String.class);
                    if (p == null) continue;
                    if (!normalizePlateStd(p).equals(plateNorm)) continue;

                    Long endL = s.child("endTime").getValue(Long.class);
                    long e = endL == null ? 0 : endL;
                    if (e > bestEnd) { bestEnd = e; bestSnap = s; }
                }

                if (bestSnap == null) {
                    notFoundInSpaces = true;
                    foundLotId = "unknown"; foundSpaceId = "unknown";
                    showResult(null, null, plateNorm, null, false);
                    return;
                }
                processBest(bestSnap, plateNorm);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                btnSearch.setText("Traži");
                btnSearch.setEnabled(true);
                Toast.makeText(ControlPanelActivity.this,
                        "Greška: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void processBest(DataSnapshot bestSnap, String plateNorm) {
        long now = System.currentTimeMillis();
        Long endL = bestSnap.child("endTime").getValue(Long.class);
        final long finalEndTime = endL == null ? 0 : endL;
        String status = bestSnap.child("status").getValue(String.class);

        // Aktivno = kraj u budućnosti I status ACTIVE
        final boolean active = (finalEndTime > now && "ACTIVE".equalsIgnoreCase(status));
        final String finalZoneId = bestSnap.child("zoneId").getValue(String.class);

        foundLotId   = "unknown";
        foundSpaceId = "unknown";
        foundPlate   = plateNorm;
        notFoundInSpaces = false;

        loadZoneNameAndShow(finalZoneId, null, plateNorm, finalEndTime, active);
    }

    private void loadZoneNameAndShow(String zoneId, String lotName,
                                     String plate, Long until, boolean active) {
        if (TextUtils.isEmpty(zoneId)) {
            showResult(null, lotName, plate, until, active);
            return;
        }
        zonesRef.child(zoneId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                String zoneName = ds.child("name").getValue(String.class);
                foundZoneName = TextUtils.isEmpty(zoneName) ? zoneId : zoneName;
                showResult(foundZoneName, lotName, plate, until, active);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                showResult(null, lotName, plate, until, active);
            }
        });
    }

    private void showResult(String zoneName, String lotName, String plate,
                            Long until, boolean active) {

        lastHadActivePayment = active;
        cardResult.setVisibility(View.VISIBLE);

        tvPlateValue.setText(plate);
        tvZoneValue.setText(!TextUtils.isEmpty(zoneName) ? zoneName : "—");

        tvParkingValue.setVisibility(View.VISIBLE);
        tvParkingValue.setText(!TextUtils.isEmpty(lotName) ? lotName : "Evidentirano putem aplikacije");

        if (active) {
            tvStatusLabel.setText("Uplaćeno");
            tvStatusBadge.setText("✓ AKTIVNO");
            tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(COLOR_GREEN));
            cardResult.setStrokeColor(COLOR_GREEN);

            if (until != null && until > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.getDefault());
                tvUntilValue.setText(sdf.format(new Date(until)));
                tvUntilLabel.setVisibility(View.VISIBLE);
                tvUntilValue.setVisibility(View.VISIBLE);
            }
            btnIssueFine.setVisibility(View.GONE);
        } else {
            tvStatusLabel.setText("Nije uplaćeno");
            tvStatusBadge.setText("✗ ISTEKLO");
            tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(COLOR_RED));
            cardResult.setStrokeColor(COLOR_RED);

            tvUntilLabel.setVisibility(View.GONE);
            tvUntilValue.setVisibility(View.GONE);
            btnIssueFine.setVisibility(View.VISIBLE);
        }
    }

    private void confirmIssueFine() {
        if (TextUtils.isEmpty(foundPlate)) {
            Toast.makeText(this, "Nema podataka za kaznu.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (lastHadActivePayment) {
            Toast.makeText(this, "Vozilo ima aktivnu uplatu — kazna nije moguća.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_fine, null, false);
        dlg.setContentView(v);

        TextView tvAmount = v.findViewById(R.id.tvFineAmount);
        tvAmount.setText(String.format(Locale.getDefault(), "%.2f KM", FINE_AMOUNT));

        TextView tvPlate = v.findViewById(R.id.tvFinePlate);
        tvPlate.setText(foundPlate);

        TextView tvTime = v.findViewById(R.id.tvFineTime);
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.getDefault());
        tvTime.setText(sdf.format(new Date()));

        TextView tvQuestion = v.findViewById(R.id.tvFineQuestion);
        if (tvQuestion != null) {
            tvQuestion.setText("Da li ste sigurni da za vozilo " + foundPlate
                    + " ne postoji aktivna uplata?");
        }

        AppCompatButton btnConfirm = v.findViewById(R.id.btnConfirmFine);
        AppCompatButton btnCancel  = v.findViewById(R.id.btnCancelFine);

        btnConfirm.setOnClickListener(x -> { dlg.dismiss(); issueFine(); });
        btnCancel.setOnClickListener(x -> dlg.dismiss());

        dlg.show();
    }

    private void issueFine() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) { Toast.makeText(this, "Niste prijavljeni.", Toast.LENGTH_SHORT).show(); return; }

        // Build fine record — server timestamp ensures accurate issue time
        Map<String, Object> fine = new HashMap<>();
        fine.put("issuedBy",     uid);
        fine.put("plate",        foundPlate);
        fine.put("issuedAt",     ServerValue.TIMESTAMP);
        fine.put("status",       "neplaceno");
        fine.put("amount",       FINE_AMOUNT);
        fine.put("parkingLotId", !TextUtils.isEmpty(foundLotId)   ? foundLotId   : "unknown");
        fine.put("space",        !TextUtils.isEmpty(foundSpaceId) ? foundSpaceId : "unknown");
        fine.put("match",        notFoundInSpaces ? "not_found" : "found");

        // Push generates a unique fine ID
        finesRef.push().setValue(fine)
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, "Kazna uspješno izdata.", Toast.LENGTH_LONG).show();
                    btnIssueFine.setVisibility(View.GONE);
                    tvStatusLabel.setText("Kazna izdata");
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private static String normalizePlateStd(String s) {
        if (s == null) return "";
        String up = s.toUpperCase(Locale.ROOT).replace("Đ", "D");
        up = Normalizer.normalize(up, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return up.replaceAll("[^A-Z0-9]", "");
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}