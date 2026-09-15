package com.example.smartparking.ui.user.userSettings;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AddVehicleActivity extends AppCompatActivity {

    private TextInputEditText etBrand, etType, etNickname, etPlate;
    private MaterialButton btnSave;

    private String uid;
    private DatabaseReference vehiclesRef;

    // Non-null means we're editing this vehicle instead of adding a new one
    private String editVehicleId = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_vehicle);

        MaterialButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        etBrand    = findViewById(R.id.etBrand);
        etType     = findViewById(R.id.etType);
        etNickname = findViewById(R.id.etNickname);
        etPlate    = findViewById(R.id.etPlate);
        btnSave    = findViewById(R.id.btnSaveVehicle);

        uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            toast("Niste prijavljeni.");
            finish();
            return;
        }

        vehiclesRef = FirebaseUtils.userVehicles(uid);

        editVehicleId = getIntent().getStringExtra(VehiclesActivity.EXTRA_VEHICLE_ID);

        if (!TextUtils.isEmpty(editVehicleId)) {
            if (btnSave != null) btnSave.setText("Sačuvaj izmjene");
            loadVehicle(editVehicleId);
        }

        if (btnSave != null) btnSave.setOnClickListener(v -> save());
    }

    private void loadVehicle(String vehicleId) {
        vehiclesRef.child(vehicleId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot ds) {
                if (!ds.exists()) { toast("Vozilo ne postoji."); finish(); return; }
                if (etBrand    != null) etBrand.setText(ds.child("brand").getValue(String.class));
                if (etType     != null) etType.setText(ds.child("type").getValue(String.class));
                if (etNickname != null) etNickname.setText(ds.child("nickname").getValue(String.class));
                if (etPlate    != null) etPlate.setText(ds.child("plate").getValue(String.class));
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                toast("Greška: " + e.getMessage());
            }
        });
    }

    private void save() {
        String brand    = safe(etBrand);
        String type     = safe(etType);
        String nickname = safe(etNickname);
        String plate    = safe(etPlate).toUpperCase(Locale.ROOT);

        if (TextUtils.isEmpty(plate)) {
            if (etPlate != null) {
                etPlate.setError("Unesite registarsku oznaku");
                etPlate.requestFocus();
            }
            return;
        }

        Map<String, Object> map = new HashMap<>();
        map.put("brand",     brand);
        map.put("type",      type);
        map.put("nickname",  nickname);
        map.put("plate",     plate);
        map.put("updatedAt", System.currentTimeMillis());

        if (!TextUtils.isEmpty(editVehicleId)) {
            vehiclesRef.child(editVehicleId).updateChildren(map)
                    .addOnSuccessListener(x -> { toast("Vozilo izmijenjeno."); finish(); })
                    .addOnFailureListener(e -> toast("Greška: " + e.getMessage()));
        } else {
            map.put("createdAt", System.currentTimeMillis());
            vehiclesRef.push().setValue(map)
                    .addOnSuccessListener(x -> { toast("Vozilo dodano."); finish(); })
                    .addOnFailureListener(e -> toast("Greška: " + e.getMessage()));
        }
    }

    private String safe(TextInputEditText et) {
        return (et == null || et.getText() == null) ? "" : et.getText().toString().trim();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}