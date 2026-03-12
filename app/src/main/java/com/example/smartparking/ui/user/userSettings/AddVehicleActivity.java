package com.example.smartparking.ui.user.userSettings;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class AddVehicleActivity extends AppCompatActivity {

    private com.google.android.material.textfield.TextInputEditText etBrand, etType, etNickname, etPlate;
    private Button btnSave;
    private Button btnCancel;
    private TextView tvTitle;

    private String uid;
    private DatabaseReference vehiclesRef;

    // ✅ ako nije null => edit mode
    private String editVehicleId = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_vehicle);

        tvTitle = findViewById(R.id.tvTitle);

        etBrand = findViewById(R.id.etBrand);
        etType = findViewById(R.id.etType);
        etNickname = findViewById(R.id.etNickname);
        etPlate = findViewById(R.id.etPlate);

        btnSave = findViewById(R.id.btnSaveVehicle);
        btnCancel = findViewById(R.id.btnCancel);

        uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            toast("Niste prijavljeni.");
            finish();
            return;
        }

        vehiclesRef = FirebaseUtils.user(uid).child("vehicles");

        // ✅ provjeri da li je edit
        editVehicleId = getIntent().getStringExtra(VehiclesActivity.EXTRA_VEHICLE_ID);

        if (!TextUtils.isEmpty(editVehicleId)) {
            setScreenTitle("Izmijeni vozilo");
            btnSave.setText("Sačuvaj izmjene");
            loadVehicle(editVehicleId);
        } else {
            setScreenTitle("Dodaj vozilo");
            btnSave.setText("Sačuvaj");
        }

        btnSave.setOnClickListener(v -> save());

        // ✅ CANCEL: vrati nazad na VehiclesActivity
        btnCancel.setOnClickListener(v -> finish());
    }

    private void setScreenTitle(String title) {
        setTitle(title); // za toolbar (ako postoji)
        if (tvTitle != null) tvTitle.setText(title); // za tvoj custom header
    }

    private void loadVehicle(String vehicleId) {
        vehiclesRef.child(vehicleId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot ds) {
                if (!ds.exists()) {
                    toast("Vozilo ne postoji.");
                    finish();
                    return;
                }

                if (etBrand != null) etBrand.setText(ds.child("brand").getValue(String.class));
                if (etType != null) etType.setText(ds.child("type").getValue(String.class));
                if (etNickname != null) etNickname.setText(ds.child("nickname").getValue(String.class));
                if (etPlate != null) etPlate.setText(ds.child("plate").getValue(String.class));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError e) {
                toast("Greška: " + e.getMessage());
            }
        });
    }

    private void save() {
        String brand = safe(etBrand);
        String type = safe(etType);
        String nickname = safe(etNickname);
        String plate = safe(etPlate);

        // Minimalna validacija
        if (TextUtils.isEmpty(plate)) {
            if (etPlate != null) {
                etPlate.setError("Unesite registarske oznake");
                etPlate.requestFocus();
            }
            return;
        }

        Map<String, Object> map = new HashMap<>();
        map.put("brand", brand);
        map.put("type", type);
        map.put("nickname", nickname);
        map.put("plate", plate);
        map.put("updatedAt", System.currentTimeMillis());

        if (!TextUtils.isEmpty(editVehicleId)) {
            // ✅ UPDATE postojeceg
            vehiclesRef.child(editVehicleId).updateChildren(map)
                    .addOnSuccessListener(x -> {
                        toast("Vozilo izmijenjeno.");
                        finish();
                    })
                    .addOnFailureListener(e -> toast("Greška: " + e.getMessage()));
        } else {
            // ✅ ADD novo
            map.put("createdAt", System.currentTimeMillis());

            vehiclesRef.push().setValue(map)
                    .addOnSuccessListener(x -> {
                        toast("Vozilo dodano.");
                        finish();
                    })
                    .addOnFailureListener(e -> toast("Greška: " + e.getMessage()));
        }
    }

    private String safe(com.google.android.material.textfield.TextInputEditText et) {
        return (et == null || et.getText() == null) ? "" : et.getText().toString().trim();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
