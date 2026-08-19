package com.example.smartparking.ui.user.userSettings;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;
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

    // Deterministička paleta boja — vozilo dobija konzistentnu boju po tablici
    private static final int[] VEHICLE_PALETTE = new int[] {
            0xFF3B82F6, 0xFFE74C3C, 0xFF10B981, 0xFFF59E0B,
            0xFF8B5CF6, 0xFF06B6D4, 0xFFEC4899, 0xFF6366F1
    };

    // Adapter view type-ovi
    private static final int TYPE_VEHICLE = 0;
    private static final int TYPE_ADD     = 1;

    private MaterialButton btnBack;
    private RecyclerView rv;
    private TextView tvVehicleCount;

    private String uid;
    private DatabaseReference vehiclesRef;
    private ValueEventListener vehiclesListener;

    private final VehiclesAdapter adapter = new VehiclesAdapter();

    private String defaultVehicleId = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicles);

        btnBack        = findViewById(R.id.btnBack);
        rv             = findViewById(R.id.rvVehicles);
        tvVehicleCount = findViewById(R.id.tvVehicleCount);

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            toast("Niste prijavljeni.");
            finish();
            return;
        }

        vehiclesRef = FirebaseUtils.user(uid).child("vehicles");
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

                // Najstarije vozilo dobija "OSNOVNO"
                defaultVehicleId = null;
                long oldest = Long.MAX_VALUE;
                for (VehicleRow r : list) {
                    if (r.createdAt > 0 && r.createdAt < oldest) {
                        oldest = r.createdAt;
                        defaultVehicleId = r.id;
                    }
                }

                // Najnovije prvo
                list.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
                adapter.submit(list);

                if (tvVehicleCount != null)
                    tvVehicleCount.setText(String.valueOf(list.size()));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError e) {
                toast("Greška: " + e.getMessage());
            }
        };

        vehiclesRef.addValueEventListener(vehiclesListener);
    }

    // ── Boja po vozilu ────────────────────────────────────

    private static int colorForPlate(String plate) {
        if (TextUtils.isEmpty(plate)) return VEHICLE_PALETTE[0];
        int hash = 0;
        for (int i = 0; i < plate.length(); i++) hash = 31 * hash + plate.charAt(i);
        int idx = Math.abs(hash) % VEHICLE_PALETTE.length;
        return VEHICLE_PALETTE[idx];
    }

    private static int tintedBg(int color) {
        return (color & 0x00FFFFFF) | 0x1F000000;
    }

    // ── Bottom sheet za brisanje ──────────────────────────

    private void showDeleteDialog(VehicleRow r) {
        if (r == null || TextUtils.isEmpty(r.id)) return;

        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_delete_vehicle, null, false);
        dlg.setContentView(v);

        TextView tvMsg = v.findViewById(R.id.tvDeleteMessage);
        if (tvMsg != null) {
            String plate = TextUtils.isEmpty(r.plate) ? "" : r.plate;
            String nick  = TextUtils.isEmpty(r.nickname) ? "" : r.nickname;
            if (!nick.isEmpty() && !plate.isEmpty())
                tvMsg.setText("Vozilo " + nick + " (" + plate + ")\nOva akcija se ne može poništiti.");
            else if (!nick.isEmpty())
                tvMsg.setText(nick + "\nOva akcija se ne može poništiti.");
            else if (!plate.isEmpty())
                tvMsg.setText(plate + "\nOva akcija se ne može poništiti.");
        }

        Button btnConfirm = v.findViewById(R.id.btnConfirmDelete);
        Button btnCancel  = v.findViewById(R.id.btnCancelDelete);

        btnConfirm.setOnClickListener(x -> {
            deleteVehicle(r.id);
            dlg.dismiss();
        });
        btnCancel.setOnClickListener(x -> dlg.dismiss());

        dlg.show();
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

    private void openAddVehicle() {
        startActivity(new Intent(this, AddVehicleActivity.class));
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

    // ═════════════════════════════════════════════════════════════
    // Adapter — podržava dva view type-a: vozilo + "Dodaj vozilo".
    // "Dodaj" red je uvijek POSLJEDNJI item u listi:
    //   - Ako lista vozila je prazna: prikazuje se sam (na vrhu)
    //   - Ako ima vozila: prikazuje se ispod poslednjeg vozila
    // ═════════════════════════════════════════════════════════════
    class VehiclesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private List<VehicleRow> data = new ArrayList<>();

        void submit(List<VehicleRow> d) {
            data = (d == null) ? new ArrayList<>() : d;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            // Zadnja pozicija = "Dodaj vozilo" kartica
            return position == data.size() ? TYPE_ADD : TYPE_VEHICLE;
        }

        @Override
        public int getItemCount() {
            // +1 za "Dodaj vozilo" red
            return data.size() + 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_ADD) {
                View v = inf.inflate(R.layout.row_add_vehicle, parent, false);
                return new AddVH(v);
            } else {
                View v = inf.inflate(R.layout.row_vehicle_item, parent, false);
                return new VehicleVH(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof AddVH) {
                holder.itemView.setOnClickListener(v -> openAddVehicle());
                return;
            }

            VehicleVH h = (VehicleVH) holder;
            VehicleRow r = data.get(position);

            // Nickname (ili fallback)
            String nickname = r.nickname;
            if (TextUtils.isEmpty(nickname)) {
                if (!TextUtils.isEmpty(r.brand) || !TextUtils.isEmpty(r.type))
                    nickname = ((r.brand == null ? "" : r.brand) + " "
                            + (r.type == null ? "" : r.type)).trim();
                else
                    nickname = "Vozilo";
            }
            h.tvVehicleNickname.setText(nickname);

            // Brand · type
            String brand = TextUtils.isEmpty(r.brand) ? "" : r.brand;
            String type  = TextUtils.isEmpty(r.type)  ? "" : r.type;
            String brandType;
            if (!brand.isEmpty() && !type.isEmpty()) brandType = brand + " · " + type;
            else if (!brand.isEmpty())               brandType = brand;
            else if (!type.isEmpty())                brandType = type;
            else                                      brandType = "";
            h.tvVehicleBrand.setText(brandType);
            h.tvVehicleBrand.setVisibility(brandType.isEmpty() ? View.GONE : View.VISIBLE);

            // Tablica
            h.tvVehiclePlate.setText(TextUtils.isEmpty(r.plate) ? "—" : r.plate);

            // OSNOVNO bedž
            boolean isDefault = defaultVehicleId != null && defaultVehicleId.equals(r.id);
            h.tvDefaultBadge.setVisibility(isDefault ? View.VISIBLE : View.GONE);

            // Boja ikonice + pozadina
            int color = colorForPlate(r.plate);

            if (h.ivVehicleColor != null) {
                h.ivVehicleColor.setImageResource(R.drawable.ic_car);
                h.ivVehicleColor.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            }
            if (h.vIconBg != null && h.vIconBg.getBackground() != null) {
                GradientDrawable bg = (GradientDrawable)
                        h.vIconBg.getBackground().getConstantState().newDrawable().mutate();
                bg.setColor(tintedBg(color));
                h.vIconBg.setBackground(bg);
            }

            h.btnEditVehicle.setOnClickListener(v -> openEditVehicle(r.id));
            h.btnDeleteVehicle.setOnClickListener(v -> showDeleteDialog(r));
        }

        // ── ViewHolder-i ────────────────────────────

        class VehicleVH extends RecyclerView.ViewHolder {
            View vIconBg;
            ImageView ivVehicleColor;
            TextView tvVehicleNickname, tvVehicleBrand, tvVehiclePlate, tvDefaultBadge;
            Button btnEditVehicle, btnDeleteVehicle;

            VehicleVH(@NonNull View itemView) {
                super(itemView);
                vIconBg           = itemView.findViewById(R.id.vIconBg);
                ivVehicleColor    = itemView.findViewById(R.id.ivVehicleColor);
                tvVehicleNickname = itemView.findViewById(R.id.tvVehicleNickname);
                tvVehicleBrand    = itemView.findViewById(R.id.tvVehicleBrand);
                tvVehiclePlate    = itemView.findViewById(R.id.tvVehiclePlate);
                tvDefaultBadge    = itemView.findViewById(R.id.tvDefaultBadge);
                btnEditVehicle    = itemView.findViewById(R.id.btnEditVehicle);
                btnDeleteVehicle  = itemView.findViewById(R.id.btnDeleteVehicle);
            }
        }

        class AddVH extends RecyclerView.ViewHolder {
            AddVH(@NonNull View itemView) {
                super(itemView);
            }
        }
    }
}