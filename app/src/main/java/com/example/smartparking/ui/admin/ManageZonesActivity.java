package com.example.smartparking.ui.admin;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ManageZonesActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private ZonesAdapter adapter;
    private MaterialButton fabAdd;

    private TextView tvCountZones, tvAvgPrice, tvListHeader;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_manage_zones);

        MaterialButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        fabAdd       = findViewById(R.id.fabAdd);
        recycler     = findViewById(R.id.recycler);
        tvCountZones = findViewById(R.id.tvCountZones);
        tvAvgPrice   = findViewById(R.id.tvAvgPrice);
        tvListHeader = findViewById(R.id.tvListHeader);

        fabAdd.setOnClickListener(v -> showAddEditDialog(null, null));

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ZonesAdapter();
        recycler.setAdapter(adapter);

        FirebaseUtils.zonesRef().addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                List<ZoneItem> list = new ArrayList<>();
                for (DataSnapshot z : ds.getChildren()) {
                    ZoneItem it = new ZoneItem();
                    it.id       = z.getKey();
                    it.name     = nvl(z.child("name").getValue(String.class));
                    Double ph   = z.child("perHour").getValue(Double.class);
                    Double pd   = z.child("perDay").getValue(Double.class);
                    it.perHour  = ph == null ? 0.0 : ph;
                    it.perDay   = pd == null ? 0.0 : pd;
                    list.add(it);
                }
                adapter.submit(list);
                updateHeroStats(list);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) { toast(e.getMessage()); }
        });
    }

    // -- Header stats --

    private void updateHeroStats(List<ZoneItem> list) {
        int count = list.size();
        double sum = 0;
        for (ZoneItem it : list) sum += it.perHour;
        double avg = count > 0 ? sum / count : 0.0;

        if (tvCountZones != null) tvCountZones.setText(String.valueOf(count));
        if (tvAvgPrice   != null) tvAvgPrice.setText(fmt2(avg));

        if (tvListHeader != null) {
            tvListHeader.setText(count + " ZONA");
        }
    }

    // -- Helpers --

    private static String nvl(String s)     { return s == null ? "" : s; }
    private void toast(String s)            { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private static String safe(EditText et) { return et.getText() == null ? "" : et.getText().toString().trim(); }
    private static double parseD(String s)  { try { return Double.parseDouble(s.replace(",", ".")); } catch (Exception e) { return 0; } }
    private static String fmt2(double v)    { return String.format(Locale.getDefault(), "%.2f", v); }

    // -- Model --

    static class ZoneItem {
        String id, name;
        double perHour, perDay;
    }

    // -- Adapter --

    class ZonesAdapter extends RecyclerView.Adapter<ZonesAdapter.VH> {
        List<ZoneItem> data = new ArrayList<>();
        void submit(List<ZoneItem> d) { data = d; notifyDataSetChanged(); }

        class VH extends RecyclerView.ViewHolder {
            TextView rowTitle, rowSubtitle, tvZoneAvatar, tvPricePerHour, tvPricePerDay;
            VH(@NonNull View v) {
                super(v);
                rowTitle       = v.findViewById(R.id.rowTitle);
                rowSubtitle    = v.findViewById(R.id.rowSubtitle);
                tvZoneAvatar   = v.findViewById(R.id.tvZoneAvatar);
                tvPricePerHour = v.findViewById(R.id.tvPricePerHour);
                tvPricePerDay  = v.findViewById(R.id.tvPricePerDay);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.row_zone_admin, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            ZoneItem it = data.get(pos);

            h.rowTitle.setText(TextUtils.isEmpty(it.name) ? "(bez naziva)" : it.name);
            h.rowSubtitle.setText("ID: " + (TextUtils.isEmpty(it.id) ? "—" : it.id));

            String initial = TextUtils.isEmpty(it.name)
                    ? "Z"
                    : String.valueOf(it.name.charAt(0)).toUpperCase(Locale.ROOT);
            h.tvZoneAvatar.setText(initial);

            h.tvPricePerHour.setText(String.format(Locale.getDefault(), "1h · %.2f KM", it.perHour));
            h.tvPricePerDay.setText(String.format(Locale.getDefault(), "Dnevna · %.2f KM", it.perDay));

            h.itemView.setOnClickListener(v -> showZoneActionsDialog(it));
        }

        @Override public int getItemCount() { return data.size(); }
    }

    // -- Actions bottom sheet --

    private void showZoneActionsDialog(ZoneItem it) {
        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this)
                .inflate(R.layout.dialog_zone_actions, null, false);
        dlg.setContentView(v);

        TextView tvName = v.findViewById(R.id.tvZoneActionsName);
        TextView tvId   = v.findViewById(R.id.tvZoneActionsId);
        tvName.setText(TextUtils.isEmpty(it.name) ? "(bez naziva)" : it.name);
        tvId.setText("ID: " + (TextUtils.isEmpty(it.id) ? "—" : it.id));

        View rowEdit   = v.findViewById(R.id.rowEditZone);
        View rowDelete = v.findViewById(R.id.rowDeleteZone);
        AppCompatButton btnCancel = v.findViewById(R.id.btnZoneActionsCancel);

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

    // -- Delete confirm bottom sheet --

    private void showDeleteConfirmDialog(ZoneItem it) {
        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this)
                .inflate(R.layout.dialog_confirm_delete_zone, null, false);
        dlg.setContentView(v);

        TextView tvName   = v.findViewById(R.id.tvDeleteZoneName);
        TextView tvPrices = v.findViewById(R.id.tvDeleteZonePrices);
        tvName.setText(TextUtils.isEmpty(it.name) ? "(bez naziva)" : it.name);
        tvPrices.setText(String.format(Locale.getDefault(),
                "%.2f KM/h  ·  %.2f KM/dan", it.perHour, it.perDay));

        AppCompatButton btnConfirm = v.findViewById(R.id.btnConfirmDeleteZone);
        AppCompatButton btnCancel  = v.findViewById(R.id.btnCancelDeleteZone);

        btnConfirm.setOnClickListener(x -> {
            dlg.dismiss();
            FirebaseUtils.zone(it.id).removeValue()
                    .addOnSuccessListener(r -> toast("Zona obrisana."))
                    .addOnFailureListener(e -> toast("Greška: " + e.getMessage()));
        });
        btnCancel.setOnClickListener(x -> dlg.dismiss());

        dlg.show();
    }

    // -- Add / edit bottom sheet --

    private void showAddEditDialog(String zoneId, ZoneItem current) {
        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_add_edit_zone, null, false);
        dlg.setContentView(view);

        TextView tvTitle = view.findViewById(R.id.tvZoneDialogTitle);
        if (tvTitle != null) tvTitle.setText(zoneId == null ? "Dodaj zonu" : "Uredi zonu");

        EditText etName    = view.findViewById(R.id.etZoneName);
        EditText etPerHour = view.findViewById(R.id.etPerHour);
        EditText etPerDay  = view.findViewById(R.id.etPerDay);

        AppCompatButton btnSave   = view.findViewById(R.id.btnZoneSave);
        AppCompatButton btnCancel = view.findViewById(R.id.btnZoneCancel);

        // Prefill from the object we already have, then refresh from the DB
        // once it responds (covers the case where it's since changed)
        if (current != null) {
            etName.setText(current.name);
            etPerHour.setText(fmt2(current.perHour));
            etPerDay.setText(fmt2(current.perDay));
        }
        if (zoneId != null) {
            FirebaseUtils.zone(zoneId).get().addOnSuccessListener(snap -> {
                if (!snap.exists()) return;
                String nm  = snap.child("name").getValue(String.class);
                Double ph  = snap.child("perHour").getValue(Double.class);
                Double pd  = snap.child("perDay").getValue(Double.class);
                etName.setText(nm == null ? "" : nm);
                etPerHour.setText(fmt2(ph == null ? 0.0 : ph));
                etPerDay.setText(fmt2(pd == null ? 0.0 : pd));
            });
        }

        btnSave.setOnClickListener(x -> {
            String name = safe(etName);
            if (TextUtils.isEmpty(name)) { toast("Naziv je obavezan"); return; }

            // Keep the existing id when editing, generate a new push id when adding
            final String finalId;
            if (zoneId != null) {
                finalId = zoneId;
            } else {
                finalId = FirebaseUtils.zonesRef().push().getKey();
                if (TextUtils.isEmpty(finalId)) {
                    toast("Greška generisanja ID-a.");
                    return;
                }
            }

            double ph = parseD(safe(etPerHour));
            double pd = parseD(safe(etPerDay));

            Map<String, Object> data = new HashMap<>();
            data.put("name",    name);
            data.put("perHour", ph);
            data.put("perDay",  pd);

            FirebaseUtils.zone(finalId).updateChildren(data)
                    .addOnSuccessListener(v1 -> {
                        toast("\"" + name + "\" " + (zoneId == null ? "dodana" : "ažurirana") + " ✓");
                        dlg.dismiss();
                    })
                    .addOnFailureListener(e -> toast("Greška: " + e.getMessage()));
        });

        btnCancel.setOnClickListener(x -> dlg.dismiss());

        dlg.show();
    }
}