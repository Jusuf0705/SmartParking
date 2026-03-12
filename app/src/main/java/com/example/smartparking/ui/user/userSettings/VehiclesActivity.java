package com.example.smartparking.ui.user.userSettings;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class VehiclesActivity extends AppCompatActivity {

    public static final String EXTRA_VEHICLE_ID = "extra_vehicle_id";

    private MaterialButton btnBack;
    private Button btnAdd;
    private RecyclerView rv;

    private String uid;
    private DatabaseReference vehiclesRef;
    private ValueEventListener vehiclesListener;

    private final VehiclesAdapter adapter = new VehiclesAdapter();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicles);

        // Back dugme iz headera
        btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        btnAdd = findViewById(R.id.btnAddVehicle);
        rv = findViewById(R.id.rvVehicles);

        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            toast("Niste prijavljeni.");
            finish();
            return;
        }

        vehiclesRef = FirebaseUtils.user(uid).child("vehicles");

        btnAdd.setOnClickListener(v ->
                startActivity(new Intent(this, AddVehicleActivity.class))
        );

        listenVehicles();
    }

    private void listenVehicles() {
        vehiclesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot ds) {
                List<VehicleRow> list = new ArrayList<>();

                for (DataSnapshot it : ds.getChildren()) {
                    VehicleRow r = new VehicleRow();
                    r.id = it.getKey();
                    r.brand = it.child("brand").getValue(String.class);
                    r.type = it.child("type").getValue(String.class);
                    r.nickname = it.child("nickname").getValue(String.class);
                    r.plate = it.child("plate").getValue(String.class);

                    Long created = it.child("createdAt").getValue(Long.class);
                    r.createdAt = created == null ? 0L : created;

                    list.add(r);
                }

                list.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
                adapter.submit(list);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError e) {
                toast("Greška: " + e.getMessage());
            }
        };

        vehiclesRef.addValueEventListener(vehiclesListener);
    }

    private void confirmDeleteVehicle(VehicleRow r) {
        if (r == null || TextUtils.isEmpty(r.id)) return;

        String plate = TextUtils.isEmpty(r.plate) ? "?" : r.plate;
        String nick = TextUtils.isEmpty(r.nickname) ? "" : (" (" + r.nickname + ")");

        new AlertDialog.Builder(this)
                .setTitle("Potvrda brisanja")
                .setMessage("Da li ste sigurni da želite obrisati vozilo" + nick + "?\nReg: " + plate)
                .setPositiveButton("DA", (d, w) -> deleteVehicle(r.id))
                .setNegativeButton("NE", null)
                .show();
    }

    private void deleteVehicle(String vehicleId) {
        if (TextUtils.isEmpty(vehicleId)) return;

        vehiclesRef.child(vehicleId).removeValue()
                .addOnSuccessListener(x -> toast("Vozilo obrisano."))
                .addOnFailureListener(e -> toast("Greška: " + e.getMessage()));
    }

    private void openEditVehicle(String vehicleId) {
        if (TextUtils.isEmpty(vehicleId)) return;

        Intent i = new Intent(this, AddVehicleActivity.class);
        i.putExtra(EXTRA_VEHICLE_ID, vehicleId);
        startActivity(i);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (vehiclesRef != null && vehiclesListener != null) {
            vehiclesRef.removeEventListener(vehiclesListener);
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    static class VehicleRow {
        String id, brand, type, nickname, plate;
        long createdAt;
    }

    class VehiclesAdapter extends RecyclerView.Adapter<VehiclesAdapter.VH> {

        private List<VehicleRow> data = new ArrayList<>();

        void submit(List<VehicleRow> d) {
            data = (d == null) ? new ArrayList<>() : d;
            notifyDataSetChanged();
        }

        class VH extends RecyclerView.ViewHolder {
            android.widget.TextView tvTitle, tvSub;
            Button btnDelete, btnEdit;

            VH(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvSub = itemView.findViewById(R.id.tvSub);
                btnDelete = itemView.findViewById(R.id.btnDelete);
                btnEdit = itemView.findViewById(R.id.btnEdit);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.row_vehicle_item, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            VehicleRow r = data.get(pos);

            String title = (TextUtils.isEmpty(r.nickname) ? "Vozilo" : r.nickname)
                    + " • " + (TextUtils.isEmpty(r.brand) ? "?" : r.brand)
                    + " • " + (TextUtils.isEmpty(r.type) ? "?" : r.type);

            h.tvTitle.setText(title);
            h.tvSub.setText("Reg: " + (TextUtils.isEmpty(r.plate) ? "?" : r.plate));

            h.btnDelete.setOnClickListener(v -> confirmDeleteVehicle(r));
            h.btnEdit.setOnClickListener(v -> openEditVehicle(r.id));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }
}
