package com.example.smartparking.ui.admin;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ManageParkingActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private LotsAdapter adapter;
    private MaterialButton fabAdd;

    private TextView tvCountParkings, tvCountTotalSpaces, tvCountFreeSpaces, tvListHeader;

    private final List<ZoneOption> zoneOptions = new ArrayList<>();
    private final Map<String, Integer> freeSpacesByLot = new HashMap<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_manage_parking);

        MaterialButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        fabAdd             = findViewById(R.id.fabAdd);
        recycler           = findViewById(R.id.recycler);
        tvCountParkings    = findViewById(R.id.tvCountParkings);
        tvCountTotalSpaces = findViewById(R.id.tvCountTotalSpaces);
        tvCountFreeSpaces  = findViewById(R.id.tvCountFreeSpaces);
        tvListHeader       = findViewById(R.id.tvListHeader);

        fabAdd.setOnClickListener(v -> {
            if (zoneOptions.isEmpty()) {
                toast("Prvo dodaj zone u \"Upravljanje zonama\"");
                return;
            }
            showAddEditDialog(null, null);
        });

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LotsAdapter();
        recycler.setAdapter(adapter);

        FirebaseUtils.zonesRef().addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                zoneOptions.clear();
                for (DataSnapshot z : ds.getChildren()) {
                    String id   = z.getKey();
                    String name = z.child("name").getValue(String.class);
                    Double ph   = z.child("perHour").getValue(Double.class);
                    Double pd   = z.child("perDay").getValue(Double.class);
                    if (id == null || name == null) continue;
                    ZoneOption zo = new ZoneOption();
                    zo.id      = id;
                    zo.name    = name;
                    zo.perHour = ph == null ? 0.0 : ph;
                    zo.perDay  = pd == null ? 0.0 : pd;
                    zoneOptions.add(zo);
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });

        FirebaseUtils.root().child("parkingSpaces").addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                freeSpacesByLot.clear();
                for (DataSnapshot lot : ds.getChildren()) {
                    String lotId = lot.getKey();
                    if (lotId == null) continue;
                    int free = 0;
                    for (DataSnapshot space : lot.getChildren()) {
                        String status = space.child("status").getValue(String.class);
                        if (status == null) status = space.getValue(String.class);
                        if ("slobodno".equalsIgnoreCase(status)) free++;
                    }
                    freeSpacesByLot.put(lotId, free);
                }
                adapter.notifyDataSetChanged();
                updateHeroStats();
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });

        FirebaseUtils.parkingRef().addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                List<LotItem> list = new ArrayList<>();
                for (DataSnapshot p : ds.getChildren()) {
                    LotItem it     = new LotItem();
                    it.id          = p.getKey();
                    it.name        = nvl(p.child("name").getValue(String.class));
                    it.address     = nvl(p.child("address").getValue(String.class));
                    it.zoneId      = nvl(p.child("zoneId").getValue(String.class));
                    Long tsL       = p.child("totalSpaces").getValue(Long.class);
                    it.totalSpaces = tsL == null ? 0 : tsL.intValue();
                    list.add(it);
                }
                adapter.submit(list);
                updateHeroStats();
            }
            @Override public void onCancelled(@NonNull DatabaseError e) { toast(e.getMessage()); }
        });
    }

    // -- Header stats --

    private void updateHeroStats() {
        int parkings = adapter.data.size();
        int total = 0, free = 0;
        for (LotItem it : adapter.data) {
            total += it.totalSpaces;
            Integer f = freeSpacesByLot.get(it.id);
            free += (f == null) ? it.totalSpaces : f;
        }

        if (tvCountParkings    != null) tvCountParkings.setText(String.valueOf(parkings));
        if (tvCountTotalSpaces != null) tvCountTotalSpaces.setText(String.valueOf(total));
        if (tvCountFreeSpaces  != null) tvCountFreeSpaces.setText(String.valueOf(free));

        if (tvListHeader != null) {
            String suffix = (parkings == 1) ? " PARKING" : " PARKINGA";
            tvListHeader.setText(parkings + suffix);
        }
    }

    // -- Helpers --

    private static String nvl(String s)     { return s == null ? "" : s; }
    private void toast(String s)            { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private static String safe(EditText et) { return et.getText() == null ? "" : et.getText().toString().trim(); }
    private static String safeAct(AutoCompleteTextView et) { return et.getText() == null ? "" : et.getText().toString().trim(); }
    private static double parseD(String s)  { try { return Double.parseDouble(s.replace(",", ".")); } catch (Exception e) { return 0; } }
    private static int    parseI(String s)  { try { return Integer.parseInt(s);   } catch (Exception e) { return 0; } }

    private String zoneNameById(String zoneId) {
        for (ZoneOption zo : zoneOptions) {
            if (zo.id.equals(zoneId)) return zo.name;
        }
        return TextUtils.isEmpty(zoneId) ? "—" : zoneId;
    }

    /** Label shown in the zone dropdown: "Name (id)". */
    private static String zoneDisplayLabel(ZoneOption zo) {
        return zo.name + " (" + zo.id + ")";
    }

    private String zoneDisplayById(String zoneId) {
        for (ZoneOption zo : zoneOptions) {
            if (zo.id.equals(zoneId)) return zoneDisplayLabel(zo);
        }
        return TextUtils.isEmpty(zoneId) ? "(bez zone)" : zoneId;
    }

    private ZoneOption zoneByDisplay(String display) {
        for (ZoneOption zo : zoneOptions) {
            if (zoneDisplayLabel(zo).equals(display)) return zo;
            if (zo.id.equals(display)) return zo;
        }
        return null;
    }

    // -- Models --

    static class LotItem {
        String id, name, address, zoneId;
        int totalSpaces;
    }

    static class ZoneOption {
        String id, name;
        double perHour, perDay;
    }

    // -- Adapter --

    class LotsAdapter extends RecyclerView.Adapter<LotsAdapter.VH> {
        List<LotItem> data = new ArrayList<>();
        void submit(List<LotItem> d) { data = d; notifyDataSetChanged(); }

        class VH extends RecyclerView.ViewHolder {
            TextView rowTitle, rowSubtitle, tvZonePill, tvSpacesPill, tvFraction;
            View progressFill, progressTrack;

            VH(@NonNull View v) {
                super(v);
                rowTitle      = v.findViewById(R.id.rowTitle);
                rowSubtitle   = v.findViewById(R.id.rowSubtitle);
                tvZonePill    = v.findViewById(R.id.tvZonePill);
                tvSpacesPill  = v.findViewById(R.id.tvSpacesPill);
                progressFill  = v.findViewById(R.id.progressFill);
                progressTrack = v.findViewById(R.id.progressTrack);
                tvFraction    = v.findViewById(R.id.tvFraction);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.row_parking_admin, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            LotItem it = data.get(pos);

            h.rowTitle.setText(TextUtils.isEmpty(it.name) ? "(bez naziva)" : it.name);
            h.rowSubtitle.setText(TextUtils.isEmpty(it.address) ? "—" : it.address);

            h.tvZonePill.setText(zoneNameById(it.zoneId));
            h.tvSpacesPill.setText(it.totalSpaces + " mj.");

            Integer freeObj = freeSpacesByLot.get(it.id);
            int free       = (freeObj == null) ? it.totalSpaces : freeObj;
            int occupied   = it.totalSpaces - free;
            if (occupied < 0) occupied = 0;

            h.tvFraction.setText(occupied + "/" + it.totalSpaces);

            final float ratio;
            if (it.totalSpaces > 0) {
                ratio = (float) occupied / (float) it.totalSpaces;
            } else {
                ratio = 0f;
            }

            int fillRes;
            if (ratio >= 0.9f) {
                fillRes = R.drawable.bg_progress_fill_red;
            } else if (ratio >= 0.5f) {
                fillRes = R.drawable.bg_progress_fill_blue;
            } else {
                fillRes = R.drawable.bg_progress_fill_green;
            }
            h.progressFill.setBackgroundResource(fillRes);

            setProgressWidth(h.progressTrack, h.progressFill, ratio);

            h.itemView.setOnClickListener(v -> showParkingActionsDialog(it));
        }

        @Override public int getItemCount() { return data.size(); }
    }

    private void setProgressWidth(View track, View fill, float ratio) {
        Runnable apply = () -> {
            int trackW = track.getWidth();
            if (trackW <= 0) return;
            int fillW = (int) (trackW * Math.max(0f, Math.min(1f, ratio)));
            ViewGroup.LayoutParams lp = fill.getLayoutParams();
            lp.width = fillW;
            fill.setLayoutParams(lp);
        };
        if (track.getWidth() > 0) {
            apply.run();
        } else {
            track.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override public void onGlobalLayout() {
                            track.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            apply.run();
                        }
                    });
        }
    }

    // -- Actions bottom sheet --

    private void showParkingActionsDialog(LotItem it) {
        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this)
                .inflate(R.layout.dialog_parking_actions, null, false);
        dlg.setContentView(v);

        TextView tvName    = v.findViewById(R.id.tvParkingActionsName);
        TextView tvAddress = v.findViewById(R.id.tvParkingActionsAddress);
        tvName.setText(TextUtils.isEmpty(it.name) ? "(bez naziva)" : it.name);
        tvAddress.setText(TextUtils.isEmpty(it.address) ? "—" : it.address);

        View rowEdit   = v.findViewById(R.id.rowEditParking);
        View rowDelete = v.findViewById(R.id.rowDeleteParking);
        AppCompatButton btnCancel = v.findViewById(R.id.btnParkingActionsCancel);

        rowEdit.setOnClickListener(x -> {
            dlg.dismiss();
            showAddEditDialog(it.id, it);
        });
        rowDelete.setOnClickListener(x -> {
            dlg.dismiss();
            showDeleteConfirmDialog(it);
        });
        btnCancel.setOnClickListener(x -> dlg.dismiss());

        dlg.show();
    }

    private void showDeleteConfirmDialog(LotItem it) {
        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this)
                .inflate(R.layout.dialog_confirm_delete_parking, null, false);
        dlg.setContentView(v);

        TextView tvName    = v.findViewById(R.id.tvDeleteParkingName);
        TextView tvAddress = v.findViewById(R.id.tvDeleteParkingAddress);
        tvName.setText(TextUtils.isEmpty(it.name) ? "(bez naziva)" : it.name);
        tvAddress.setText(TextUtils.isEmpty(it.address) ? "—" : it.address);

        AppCompatButton btnConfirm = v.findViewById(R.id.btnConfirmDeleteParking);
        AppCompatButton btnCancel  = v.findViewById(R.id.btnCancelDeleteParking);

        btnConfirm.setOnClickListener(x -> {
            dlg.dismiss();
            FirebaseUtils.parkingLot(it.id).removeValue()
                    .addOnSuccessListener(r -> toast("Parking obrisan."))
                    .addOnFailureListener(e -> toast("Greška: " + e.getMessage()));
        });
        btnCancel.setOnClickListener(x -> dlg.dismiss());

        dlg.show();
    }

    // -- Add / edit bottom sheet (no manual id entry) --

    private void showAddEditDialog(String parkingId, LotItem current) {
        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_add_edit_parking, null, false);
        dlg.setContentView(view);

        TextView tvTitle = view.findViewById(R.id.tvParkingDialogTitle);
        if (tvTitle != null) tvTitle.setText(parkingId == null ? "Dodaj parking" : "Uredi parking");

        EditText etName        = view.findViewById(R.id.etName);
        EditText etAddress     = view.findViewById(R.id.etAddress);
        EditText etTotalSpaces = view.findViewById(R.id.etTotalSpaces);
        EditText etLat         = view.findViewById(R.id.etLat);
        EditText etLng         = view.findViewById(R.id.etLng);
        AutoCompleteTextView etZone = view.findViewById(R.id.etZoneDropdown);

        AppCompatButton btnSave   = view.findViewById(R.id.btnParkingSave);
        AppCompatButton btnCancel = view.findViewById(R.id.btnParkingCancel);

        List<String> zoneDisplayNames = new ArrayList<>();
        for (ZoneOption zo : zoneOptions) {
            zoneDisplayNames.add(zoneDisplayLabel(zo));
        }
        ArrayAdapter<String> zoneAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, zoneDisplayNames);
        etZone.setAdapter(zoneAdapter);
        etZone.setThreshold(0);
        etZone.setOnClickListener(v -> etZone.showDropDown());
        etZone.setOnFocusChangeListener((v, f) -> { if (f) etZone.showDropDown(); });

        // Prefill fields when editing an existing lot
        if (current != null) {
            etName.setText(current.name);
            etAddress.setText(current.address);
            if (current.totalSpaces > 0)
                etTotalSpaces.setText(String.valueOf(current.totalSpaces));

            String currentZoneDisplay = zoneDisplayById(current.zoneId);
            etZone.setText(currentZoneDisplay, false);

            FirebaseUtils.parkingLot(parkingId).child("geo")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override public void onDataChange(@NonNull DataSnapshot ds) {
                            Double lat = ds.child("lat").getValue(Double.class);
                            Double lng = ds.child("lng").getValue(Double.class);
                            if (lat != null) etLat.setText(String.valueOf(lat));
                            if (lng != null) etLng.setText(String.valueOf(lng));
                        }
                        @Override public void onCancelled(@NonNull DatabaseError e) {}
                    });
        }

        btnSave.setOnClickListener(x -> {

            // Edit keeps the existing id; add generates a new push id
            final String finalParkingId;
            if (parkingId != null) {
                finalParkingId = parkingId;
            } else {
                finalParkingId = FirebaseUtils.parkingRef().push().getKey();
                if (TextUtils.isEmpty(finalParkingId)) {
                    toast("Greška generisanja ID-a.");
                    return;
                }
            }

            String name = safe(etName);
            if (TextUtils.isEmpty(name)) { toast("Naziv je obavezan"); return; }

            String zoneDisplay = safeAct(etZone);
            ZoneOption selectedZone = zoneByDisplay(zoneDisplay);
            if (selectedZone == null) { toast("Odaberi zonu"); return; }

            String address = safe(etAddress);
            double lat     = parseD(safe(etLat));
            double lng     = parseD(safe(etLng));
            int    total   = parseI(safe(etTotalSpaces));
            if (total < 0) total = 0;

            DatabaseReference ref = FirebaseUtils.parkingLot(finalParkingId);

            Map<String, Object> data = new HashMap<>();
            data.put("name",        name);
            data.put("address",     address);
            data.put("zoneId",      selectedZone.id);
            data.put("totalSpaces", total);

            Map<String, Object> geo = new HashMap<>();
            geo.put("lat", lat);
            geo.put("lng", lng);
            data.put("geo", geo);

            int finalTotal = total;
            ref.updateChildren(data).addOnSuccessListener(v1 -> {
                toast("Parking " + (parkingId == null ? "dodan" : "ažuriran") + " ✓");
                generateOrTrimSpaces(finalParkingId, finalTotal);
                dlg.dismiss();
            }).addOnFailureListener(e -> toast("Greška: " + e.getMessage()));
        });

        btnCancel.setOnClickListener(x -> dlg.dismiss());

        dlg.show();
    }

    // -- Keep the space nodes in sync with totalSpaces --

    private void generateOrTrimSpaces(@NonNull String parkingId, int total) {
        DatabaseReference spacesRef = FirebaseUtils.parkingZoneSpaces(parkingId);
        spacesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                // Read existing space numbers
                java.util.Set<Integer> existing = new java.util.HashSet<>();
                for (DataSnapshot s : ds.getChildren()) {
                    try { existing.add(Integer.parseInt(s.getKey())); }
                    catch (Exception ignored) {}
                }

                // Add missing spaces (1..total)
                Map<String, Object> adds = new HashMap<>();
                for (int i = 1; i <= total; i++) {
                    if (!existing.contains(i)) {
                        Map<String, Object> one = new HashMap<>();
                        one.put("status", "slobodno");
                        adds.put(String.valueOf(i), one);
                    }
                }
                if (!adds.isEmpty()) spacesRef.updateChildren(adds);

                // Remove spaces above the new capacity
                for (Integer exist : existing) {
                    if (exist > total) spacesRef.child(String.valueOf(exist)).removeValue();
                }
                toast("Sinhronizovano mjesta: 1.." + total);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) { toast("Greška: " + e.getMessage()); }
        });
    }
}