package com.example.smartparking.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smartparking.R;
import com.example.smartparking.ui.auth.LoginActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ControlPanelActivity extends AppCompatActivity {

    private static final String TAG = "CTRL_PANEL";
    private static final String DB_URL =
            "https://smartparking-744d8-default-rtdb.europe-west1.firebasedatabase.app";

    // UI
    private AutoCompleteTextView actLotName;     // dropdown po NAZIVU
    private TextInputEditText etSpaceId, etPlate;
    private MaterialButton btnSearch, btnIssueFine, btnExit;
    private View cardResult;
    private TextView tvResult;

    // Firebase
    private DatabaseReference lotsRef, finesRef;

    // Dropdown data (naziv -> lotId)
    private final List<String> lotNames = new ArrayList<>();
    private final Map<String, String> nameToLotId = new HashMap<>();
    private ArrayAdapter<String> lotAdapter;

    // Nalaz
    private String foundLotId;
    private String foundSpaceId;
    private String foundPlate;
    private Long foundUntil;
    private boolean notFoundInSpaces = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kontrola_panel);
        setTitle("Kontrola panela");

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        FirebaseDatabase db;
        try { db = FirebaseDatabase.getInstance(DB_URL); }
        catch (Exception e) { db = FirebaseDatabase.getInstance(); }

        lotsRef = db.getReference("parkingLots");
        finesRef = db.getReference("fines");

        // bind UI
        actLotName = findViewById(R.id.actLotName);
        etSpaceId  = findViewById(R.id.etSpaceId);
        etPlate    = findViewById(R.id.etPlate);

        btnSearch    = findViewById(R.id.btnSearch);
        btnIssueFine = findViewById(R.id.btnIssueFine);
        btnExit      = findViewById(R.id.btnExit);

        cardResult = findViewById(R.id.cardResult);
        tvResult   = findViewById(R.id.tvResult);

        cardResult.setVisibility(View.GONE);
        btnIssueFine.setVisibility(View.GONE);

        // Filter tablica: slova/brojevi/crtica + uppercase
        etPlate.setFilters(new InputFilter[]{
                (source, start, end, dest, dstart, dend) -> {
                    for (int i = start; i < end; i++) {
                        char c = source.charAt(i);
                        if (!Character.isLetterOrDigit(c) && c != '-') return "";
                    }
                    return source.toString().toUpperCase(Locale.ROOT);
                }
        });

        // dropdown adapter
        lotAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, lotNames);
        actLotName.setAdapter(lotAdapter);

        actLotName.setOnClickListener(v -> actLotName.showDropDown());
        actLotName.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) actLotName.showDropDown(); });

        // učitaj parkinge (samo nazivi)
        loadParkingNames();

        btnSearch.setOnClickListener(v -> search());
        btnIssueFine.setOnClickListener(v -> confirmIssueFine());
        btnExit.setOnClickListener(v -> logout());
    }

    // ------------------------------------------------------------
    //  Load parking names (display=NAME, value=lotId)
    //  očekuje: parkingLots/{lotId}/name
    // ------------------------------------------------------------
    private void loadParkingNames() {
        lotsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                lotNames.clear();
                nameToLotId.clear();

                for (DataSnapshot lotSnap : snap.getChildren()) {
                    String lotId = lotSnap.getKey();
                    if (TextUtils.isEmpty(lotId)) continue;

                    String name = lotSnap.child("name").getValue(String.class);
                    if (TextUtils.isEmpty(name)) name = lotId; // fallback

                    // ako postoje dupli nazivi, dodaj (2), (3)...
                    String unique = name;
                    int c = 2;
                    while (nameToLotId.containsKey(unique)) {
                        unique = name + " (" + c + ")";
                        c++;
                    }

                    nameToLotId.put(unique, lotId);
                    lotNames.add(unique);
                }

                Collections.sort(lotNames);
                lotAdapter.notifyDataSetChanged();
            }

            @Override public void onCancelled(@NonNull DatabaseError e) {
                toast("Ne mogu učitati parkinge: " + e.getMessage());
                Log.e(TAG, "loadParkingNames: " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------
    //  Search: primarno po tablici + opcionalno napredno (parking + mjesto)
    // ------------------------------------------------------------
    private void search() {
        String plate = normalizePlate(safe(etPlate));     // primarno
        String spaceId = safe(etSpaceId);
        String lotName = safe(actLotName);
        String lotId = nameToLotId.get(lotName);          // naziv -> lotId

        clearResultUI();
        setSearching(true);

        foundLotId = null;
        foundSpaceId = null;
        foundUntil = null;
        foundPlate = plate;
        notFoundInSpaces = false;

        boolean hasPlate = !TextUtils.isEmpty(plate);
        boolean hasLot = !TextUtils.isEmpty(lotId);
        boolean hasSpace = !TextUtils.isEmpty(spaceId);

        // 1) Ako ima tablice -> primarno
        if (hasPlate) {
            if (hasLot && hasSpace) {
                lookupByLotAndSpace(lotId, spaceId, plate);
            } else if (hasLot) {
                lookupByPlateInLot(lotId, plate);
            } else {
                lookupByPlateAllLots(plate);
            }
            return;
        }

        // 2) Ako nema tablice -> napredno: lot + space
        if (hasLot && hasSpace) {
            lookupByLotAndSpace(lotId, spaceId, "");
            return;
        }

        setSearching(false);
        tvResult.setText("Unesi tablice (primarno) ili odaberi parking + broj mjesta.");
        cardResult.setVisibility(View.VISIBLE);
        btnIssueFine.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------
    //  lot + space (+ optional plate)
    // ------------------------------------------------------------
    private void lookupByLotAndSpace(String lotId, String spaceId, String plateOptional) {
        lotsRef.child(lotId).child("spaces").child(spaceId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot spaceSnap) {
                        setSearching(false);

                        foundLotId = lotId;
                        foundSpaceId = spaceId;

                        if (!spaceSnap.exists()) {
                            notFoundInSpaces = true;

                            if (!TextUtils.isEmpty(plateOptional)) {
                                foundPlate = plateOptional;
                                showNotFoundButFineAllowed(
                                        "🚗  " + plateOptional + "\n" +
                                                "📍  " + lotNameOf(lotId) + "\n" +
                                                "🅿️  Mjesto br. " + spaceId + "\n\n" +
                                                "❌  Nema aktivne uplate\n" +
                                                "⚠️  Mjesto nije u evidenciji"
                                );
                            } else {
                                tvResult.setText(
                                        "📍  " + lotNameOf(lotId) + "\n" +
                                                "🅿️  Mjesto br. " + spaceId + "\n\n" +
                                                "⚠️  Mjesto ne postoji u evidenciji"
                                );
                                cardResult.setVisibility(View.VISIBLE);
                                btnIssueFine.setVisibility(View.GONE);
                            }
                            return;
                        }

                        String snapPlate = readPlate(spaceSnap);
                        Long until = readUntil(spaceSnap);
                        boolean isActive = (until != null && until > System.currentTimeMillis());

                        foundUntil = until;

                        // mismatch (ako je uneseno plate i u bazi postoji)
                        if (!TextUtils.isEmpty(plateOptional) && !TextUtils.isEmpty(snapPlate)) {
                            if (!normalizePlate(snapPlate).equals(plateOptional)) {
                                foundPlate = plateOptional;
                                showSpaceMismatch(lotId, spaceId, snapPlate, plateOptional, until, isActive);
                                return;
                            }
                        }

                        if (TextUtils.isEmpty(plateOptional)) {
                            foundPlate = !TextUtils.isEmpty(snapPlate) ? normalizePlate(snapPlate) : "";
                        } else {
                            foundPlate = plateOptional;
                        }

                        showResult(lotId, spaceId,
                                TextUtils.isEmpty(foundPlate) ? "(nema tablica u evidenciji)" : foundPlate,
                                until, isActive);

                        // bez tablica = nema kazne
                        if (TextUtils.isEmpty(foundPlate) || foundPlate.startsWith("(")) {
                            btnIssueFine.setVisibility(View.GONE);
                        }
                    }

                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        setSearching(false);
                        toast("Greška: " + e.getMessage());
                    }
                });
    }

    // ------------------------------------------------------------
    //  plate in selected lot
    // ------------------------------------------------------------
    private void lookupByPlateInLot(String lotId, String plate) {
        lotsRef.child(lotId).child("spaces")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot spacesSnap) {
                        setSearching(false);

                        long now = System.currentTimeMillis();

                        for (DataSnapshot spaceSnap : spacesSnap.getChildren()) {
                            String spaceId = spaceSnap.getKey();
                            String spacePlate = readPlate(spaceSnap);
                            if (spacePlate == null) continue;

                            if (!normalizePlate(spacePlate).equals(plate)) continue;

                            Long until = readUntil(spaceSnap);
                            boolean isActive = (until != null && until > now);

                            foundLotId = lotId;
                            foundSpaceId = spaceId;
                            foundPlate = plate;
                            foundUntil = until;
                            notFoundInSpaces = false;

                            showResult(lotId, spaceId, plate, until, isActive);
                            return;
                        }

                        // nije pronađeno -> ipak kazna
                        notFoundInSpaces = true;
                        foundLotId = lotId;
                        foundSpaceId = "unknown";
                        foundPlate = plate;

                        showNotFoundButFineAllowed(
                                "Vozilo:       " + plate + "\n" +
                                        "Parking:      " + lotNameOf(lotId) + "\n" +
                                        "───────────────────────\n" +
                                        "❌ NEMA AKTIVNE UPLATE\n\n" +
                                        "Vozilo nije pronađeno u evidenciji tog parkinga.\n"
                        );
                    }

                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        setSearching(false);
                        toast("Greška: " + e.getMessage());
                    }
                });
    }

    // ------------------------------------------------------------
    //  plate all lots
    // ------------------------------------------------------------
    private void lookupByPlateAllLots(String plate) {
        lotsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot lotsSnap) {
                setSearching(false);

                long now = System.currentTimeMillis();

                for (DataSnapshot lotSnap : lotsSnap.getChildren()) {
                    String lotId = lotSnap.getKey();

                    for (DataSnapshot spaceSnap : lotSnap.child("spaces").getChildren()) {
                        String spaceId = spaceSnap.getKey();

                        String spacePlate = readPlate(spaceSnap);
                        if (spacePlate == null) continue;

                        if (!normalizePlate(spacePlate).equals(plate)) continue;

                        Long until = readUntil(spaceSnap);
                        boolean isActive = (until != null && until > now);

                        foundLotId = lotId;
                        foundSpaceId = spaceId;
                        foundPlate = plate;
                        foundUntil = until;
                        notFoundInSpaces = false;

                        showResult(lotId, spaceId, plate, until, isActive);
                        return;
                    }
                }

                // nije pronađeno -> ipak kazna
                notFoundInSpaces = true;
                foundLotId = "unknown";
                foundSpaceId = "unknown";
                foundPlate = plate;

                showNotFoundButFineAllowed(
                        "Vozilo:       " + plate + "\n" +
                                "─────────────────────────\n" +
                                "❌ NEMA AKTIVNE UPLATE\n\n" +
                                "Vozilo nije pronađeno u evidenciji parking mjesta.\n"
                );
            }

            @Override public void onCancelled(@NonNull DatabaseError e) {
                setSearching(false);
                toast("Greška: " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------
    //  UI
    // ------------------------------------------------------------
    private void showResult(String lotId, String spaceId, String plate, Long until, boolean isActive) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.getDefault());
        StringBuilder sb = new StringBuilder();

        sb.append("Vozilo:       ").append(plate).append("\n");
        sb.append("Parking:      ").append(lotNameOf(lotId)).append("\n");
        sb.append("Mjesto br.:   ").append(spaceId).append("\n");
        sb.append("─────────────────────────\n");

        if (isActive) {
            sb.append("✅ UPLATA AKTIVNA\n");
            if (until != null) sb.append("Vrijedi do:   ").append(sdf.format(new Date(until))).append("\n");
            btnIssueFine.setVisibility(View.GONE);
        } else {
            sb.append("❌ NEMA AKTIVNE UPLATE\n");
            btnIssueFine.setVisibility(View.VISIBLE);
        }

        btnIssueFine.setText("Izdaj kaznu");
        tvResult.setText(sb.toString());
        cardResult.setVisibility(View.VISIBLE);
    }

    private void showSpaceMismatch(String lotId, String spaceId, String plateInDb, String plateTyped,
                                   Long until, boolean isActive) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.getDefault());
        StringBuilder sb = new StringBuilder();

        sb.append("Vozilo (uneseno):      ").append(plateTyped).append("\n");
        sb.append("Vozilo (u evidenciji): ").append(normalizePlate(plateInDb)).append("\n");
        sb.append("Parking:      ").append(lotNameOf(lotId)).append("\n");
        sb.append("Mjesto br.:   ").append(spaceId).append("\n");
        sb.append("─────────────────────────\n");
        sb.append("⚠️ TABLICE SE NE POKLAPAJU\n\n");

        if (isActive) {
            sb.append("✅ UPLATA AKTIVNA\n");
            if (until != null) sb.append("Vrijedi do:   ").append(sdf.format(new Date(until))).append("\n");
            btnIssueFine.setVisibility(View.GONE);
        } else {
            sb.append("❌ NEMA AKTIVNE UPLATE\n");
            btnIssueFine.setVisibility(View.VISIBLE);
        }

        btnIssueFine.setText("Izdaj kaznu");
        tvResult.setText(sb.toString());
        cardResult.setVisibility(View.VISIBLE);
    }

    private void showNotFoundButFineAllowed(String msg) {
        tvResult.setText(msg);
        cardResult.setVisibility(View.VISIBLE);
        btnIssueFine.setText("Izdaj kaznu");
        btnIssueFine.setVisibility(View.VISIBLE);
    }

    private void clearResultUI() {
        cardResult.setVisibility(View.GONE);
        btnIssueFine.setVisibility(View.GONE);
        tvResult.setText("");
    }

    private void setSearching(boolean searching) {
        btnSearch.setEnabled(!searching);
        btnSearch.setText(searching ? "Pretražujem..." : "Pretraži");
    }

    // ------------------------------------------------------------
    //  Fine flow
    // ------------------------------------------------------------
    private void confirmIssueFine() {
        if (TextUtils.isEmpty(foundPlate) || foundPlate.startsWith("(")) {
            toast("Za kaznu moraju postojati tablice.");
            return;
        }

        String lotText = !TextUtils.isEmpty(foundLotId) ? lotNameOf(foundLotId) : "unknown";
        String spaceText = !TextUtils.isEmpty(foundSpaceId) ? foundSpaceId : "unknown";

        String msg = "Reg. oznaka:  " + foundPlate + "\n"
                + "Parking:      " + lotText + "\n"
                + "Mjesto:       " + spaceText + "\n\n"
                + "Potvrditi izdavanje kazne?";

        new AlertDialog.Builder(this)
                .setTitle("Izdaj kaznu")
                .setMessage(msg)
                .setPositiveButton("Izdaj kaznu", (d, w) -> issueFine())
                .setNegativeButton("Odustani", null)
                .show();
    }

    private void issueFine() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) { toast("Niste prijavljeni."); return; }

        Map<String, Object> fine = new HashMap<>();
        fine.put("issuedBy", uid);
        fine.put("plate", foundPlate);
        fine.put("issuedAt", ServerValue.TIMESTAMP);
        fine.put("status", "neplaceno");
        fine.put("amount", 20.0);

        fine.put("parkingLotId", !TextUtils.isEmpty(foundLotId) ? foundLotId : "unknown");
        fine.put("space", !TextUtils.isEmpty(foundSpaceId) ? foundSpaceId : "unknown");
        fine.put("match", notFoundInSpaces ? "not_found_in_spaces" : "found_in_spaces");

        finesRef.push().setValue(fine)
                .addOnSuccessListener(v -> {
                    toast("Kazna uspješno upisana.");
                    btnIssueFine.setVisibility(View.GONE);
                    tvResult.setText(tvResult.getText().toString() + "\n\n⚖ Kazna izdata.");
                })
                .addOnFailureListener(e -> toast("Greška: " + e.getMessage()));
    }

    // ------------------------------------------------------------
    //  Data helpers
    // ------------------------------------------------------------
    private Long readUntil(DataSnapshot spaceSnap) {
        Long until = spaceSnap.child("until").getValue(Long.class);
        if (until == null) until = spaceSnap.child("do").getValue(Long.class);
        if (until == null) until = spaceSnap.child("session").child("until").getValue(Long.class);
        if (until == null) until = spaceSnap.child("activeSession").child("until").getValue(Long.class);
        return until;
    }

    private String readPlate(DataSnapshot spaceSnap) {
        String plate = spaceSnap.child("plate").getValue(String.class);
        if (plate == null) plate = spaceSnap.child("tablice").getValue(String.class);
        if (plate == null) plate = spaceSnap.child("registracija").getValue(String.class);
        return plate;
    }

    private String normalizePlate(String s) {
        if (s == null) return "";
        return s.toUpperCase(Locale.ROOT).replaceAll("\\s+", "").trim();
    }

    private String safe(AutoCompleteTextView et) {
        return (et.getText() != null) ? et.getText().toString().trim() : "";
    }

    private String safe(TextInputEditText et) {
        return (et.getText() != null) ? et.getText().toString().trim() : "";
    }

    // lotId -> naziv (bez ulice)
    private String lotNameOf(String lotId) {
        if (TextUtils.isEmpty(lotId)) return "unknown";
        for (Map.Entry<String, String> e : nameToLotId.entrySet()) {
            if (lotId.equals(e.getValue())) return e.getKey();
        }
        return lotId;
    }

    // ------------------------------------------------------------
    private void logout() {
        FirebaseAuth.getInstance().signOut();
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
