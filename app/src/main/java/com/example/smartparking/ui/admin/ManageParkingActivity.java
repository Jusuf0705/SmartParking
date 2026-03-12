package com.example.smartparking.ui.admin;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManageParkingActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private LotsAdapter adapter;
    private DatabaseReference lotsRef;
    private Button fabAdd;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_manage_list);

        ((TextView) findViewById(R.id.tvTitle)).setText("Parking lokacije");

        fabAdd = findViewById(R.id.fabAdd);
        fabAdd.setVisibility(View.VISIBLE);
        fabAdd.setOnClickListener(v -> showAddEditDialog(null, null));

        recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LotsAdapter();
        recycler.setAdapter(adapter);

        lotsRef = FirebaseUtils.parkingLotsRef();
        lotsRef.addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                List<LotItem> list = new ArrayList<>();
                for (DataSnapshot p : ds.getChildren()) {
                    LotItem it = new LotItem();
                    it.id = p.getKey();
                    it.name = p.child("name").getValue(String.class);
                    it.address = nvl(p.child("address").getValue(String.class));
                    Double ph = p.child("pricing").child("perHour").getValue(Double.class);
                    Double pd = p.child("pricing").child("perDay").getValue(Double.class);
                    it.perHour = ph == null ? 0d : ph;
                    it.perDay  = pd == null ? 0d : pd;
                    Long tsL = p.child("totalSpaces").getValue(Long.class);
                    it.totalSpaces = tsL == null ? 0 : tsL.intValue();
                    list.add(it);
                }
                adapter.submit(list);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                Toast.makeText(ManageParkingActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private static String nvl(String s){ return s == null ? "" : s; }
    private void toast(String s){ Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private static String safe(EditText et){ return et.getText() == null ? "" : et.getText().toString().trim(); }
    private static double parseD(String s){ try { return Double.parseDouble(s); } catch (Exception e){ return 0; } }
    private static int parseI(String s){ try { return Integer.parseInt(s); } catch (Exception e){ return 0; } }

    // ===== Model za red =====
    static class LotItem {
        String id, name, address;
        double perHour, perDay;
        int totalSpaces;
    }

    // ===== Adapter =====
    class LotsAdapter extends RecyclerView.Adapter<LotsAdapter.VH> {
        List<LotItem> data = new ArrayList<>();
        void submit(List<LotItem> d){ data = d; notifyDataSetChanged(); }

        class VH extends RecyclerView.ViewHolder {
            TextView t1, t2;
            Button btnEdit, btnDelete;
            VH(@NonNull View v) {
                super(v);
                t1 = v.findViewById(R.id.rowTitle);
                t2 = v.findViewById(R.id.rowSubtitle);
                btnEdit = v.findViewById(R.id.btnEdit);
                btnDelete = v.findViewById(R.id.btnDelete);
                View extra = v.findViewById(R.id.btnRole);
                if (extra != null) extra.setVisibility(View.GONE);
            }
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_three_actions, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            LotItem it = data.get(pos);

            // Naziv parkinga
            h.t1.setText(nvl(it.name));

            // Sve informacije — svaka u svom redu
            StringBuilder info = new StringBuilder();
            info.append(TextUtils.isEmpty(it.address) ? "(bez adrese)" : it.address).append("\n");
            info.append("Cijena 1h - ").append(it.perHour).append(" KM").append("\n");
            info.append("Cijena 24h - ").append(it.perDay).append(" KM").append("\n");
            info.append("Mjesta: ").append(it.totalSpaces);

            // Postavi višeredni tekst
            h.t2.setText(info.toString());

            // Akcije
            h.btnEdit.setOnClickListener(v -> showAddEditDialog(it.id, it));
            h.btnDelete.setOnClickListener(v -> confirmDelete(it.id, it.name));
        }

        @Override public int getItemCount() { return data.size(); }
    }

    /** Dijalog za dodavanje / uređivanje — uključuje LAT/LNG i TOTAL mjesta */
    private void showAddEditDialog(String id, LotItem current) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_parking_simple, null);
        EditText etName        = view.findViewById(R.id.etName);
        EditText etAddress     = view.findViewById(R.id.etAddress);
        EditText etPerHour     = view.findViewById(R.id.etPerHour);
        EditText etPerDay      = view.findViewById(R.id.etPerDay);
        EditText etLat         = view.findViewById(R.id.etLat);
        EditText etLng         = view.findViewById(R.id.etLng);
        EditText etTotalSpaces = view.findViewById(R.id.etTotalSpaces);

        if (current != null) {
            etName.setText(current.name);
            etAddress.setText(current.address);
            etPerHour.setText(String.valueOf(current.perHour));
            etPerDay.setText(String.valueOf(current.perDay));
            if (current.totalSpaces > 0) etTotalSpaces.setText(String.valueOf(current.totalSpaces));
        }

        if (id != null) {
            FirebaseUtils.parkingLot(id).child("geo")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override public void onDataChange(@NonNull DataSnapshot ds) {
                            Double lat = ds.child("lat").getValue(Double.class);
                            Double lng = ds.child("lng").getValue(Double.class);
                            if (lat != null) etLat.setText(String.valueOf(lat));
                            if (lng != null) etLng.setText(String.valueOf(lng));
                        }
                        @Override public void onCancelled(@NonNull DatabaseError error) { /* ignore */ }
                    });
        }

        new AlertDialog.Builder(this)
                .setTitle(id == null ? "Dodaj parking" : "Uredi parking")
                .setView(view)
                .setPositiveButton("Spasi", (d, w) -> {
                    String name = safe(etName);
                    if (TextUtils.isEmpty(name)) { toast("Naziv je obavezan"); return; }

                    String address = safe(etAddress);
                    double ph  = parseD(safe(etPerHour));
                    double pd  = parseD(safe(etPerDay));
                    double lat = parseD(safe(etLat));
                    double lng = parseD(safe(etLng));
                    int total  = parseI(safe(etTotalSpaces));
                    if (total < 0) total = 0;

                    DatabaseReference ref = (id == null) ? lotsRef.push() : lotsRef.child(id);
                    String lotId = ref.getKey();

                    Map<String, Object> data = new HashMap<>();
                    data.put("name", name);
                    data.put("address", address);

                    Map<String,Object> pricing = new HashMap<>();
                    pricing.put("perHour", ph);
                    pricing.put("perDay", pd);
                    data.put("pricing", pricing);

                    Map<String,Object> geo = new HashMap<>();
                    geo.put("lat", lat);
                    geo.put("lng", lng);
                    data.put("geo", geo);

                    data.put("totalSpaces", total);

                    int finalTotal = total;
                    ref.updateChildren(data).addOnSuccessListener(v1 -> {
                        generateOrTrimSpaces(lotId, finalTotal);
                    });
                })
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void confirmDelete(String id, String name) {
        new AlertDialog.Builder(this)
                .setTitle("Brisanje")
                .setMessage("Obrisati parking: " + (name == null ? id : name) + "?")
                .setPositiveButton("Obriši", (d, w) -> FirebaseUtils.parkingLot(id).removeValue())
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void generateOrTrimSpaces(@NonNull String lotId, int total) {
        DatabaseReference spacesRef = FirebaseUtils.parkingLot(lotId).child("spaces");
        spacesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                java.util.Set<Integer> existing = new java.util.HashSet<>();
                for (DataSnapshot s : ds.getChildren()) {
                    try { existing.add(Integer.parseInt(s.getKey())); } catch (Exception ignored) {}
                }
                Map<String, Object> adds = new HashMap<>();
                for (int i = 1; i <= total; i++) {
                    if (!existing.contains(i)) {
                        Map<String,Object> one = new HashMap<>();
                        one.put("status", "slobodno");
                        adds.put(String.valueOf(i), one);
                    }
                }
                if (!adds.isEmpty()) spacesRef.updateChildren(adds);
                for (Integer exist : existing) {
                    if (exist > total) { spacesRef.child(String.valueOf(exist)).removeValue(); }
                }
                toast("Sinhronizovano mjesta: 1.." + total);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                toast("Greška pri sinhronizaciji mjesta: " + e.getMessage());
            }
        });
    }
}
