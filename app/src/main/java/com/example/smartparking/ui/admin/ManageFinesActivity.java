package com.example.smartparking.ui.admin;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ManageFinesActivity extends AppCompatActivity {

    // Filter mode
    private enum FilterMode { ALL, UNPAID, PAID }
    private FilterMode currentFilter = FilterMode.ALL;

    // Header
    private MaterialButton btnBack;
    private TextView tvCountFines, tvCountUnpaid, tvCollectedAmount, tvListHeader;

    // Filter
    private TextInputEditText etQuery;
    private AppCompatButton tabAll, tabUnpaid, tabPaid;

    // Lista
    private RecyclerView rv;
    private FinesAdapter adapter;
    private DatabaseReference finesRef;

    private static final String STATUS_PAID = "paid";

    // Master lista svih kazni (filter u memoriji)
    private final List<FineItem> master = new ArrayList<>();

    private final ValueEventListener finesListener = new ValueEventListener() {
        @Override public void onDataChange(@NonNull DataSnapshot ds) {
            master.clear();
            master.addAll(snapshotToList(ds));
            updateHeroStats();
            applyFilter();
        }
        @Override public void onCancelled(@NonNull DatabaseError e) { toast(e.getMessage()); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_manage_fines);

        // Bind
        btnBack           = findViewById(R.id.btnBack);
        tvCountFines      = findViewById(R.id.tvCountFines);
        tvCountUnpaid     = findViewById(R.id.tvCountUnpaid);
        tvCollectedAmount = findViewById(R.id.tvCollectedAmount);
        tvListHeader      = findViewById(R.id.tvListHeader);
        etQuery           = findViewById(R.id.etQuery);
        tabAll            = findViewById(R.id.tabAllFines);
        tabUnpaid         = findViewById(R.id.tabUnpaidFines);
        tabPaid           = findViewById(R.id.tabPaidFines);
        rv                = findViewById(R.id.recycler);

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // Tabs
        tabAll.setOnClickListener(v    -> selectTab(FilterMode.ALL));
        tabUnpaid.setOnClickListener(v -> selectTab(FilterMode.UNPAID));
        tabPaid.setOnClickListener(v   -> selectTab(FilterMode.PAID));

        // Search
        etQuery.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { applyFilter(); }
        });

        // RecyclerView
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FinesAdapter();
        rv.setAdapter(adapter);

        finesRef = FirebaseUtils.finesRef();

        selectTab(FilterMode.ALL);
    }

    @Override
    protected void onStart() {
        super.onStart();
        finesRef.addValueEventListener(finesListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        finesRef.removeEventListener(finesListener);
    }

    // ═══════════════════════════════════════════════════════
    // TABS
    // ═══════════════════════════════════════════════════════

    private void selectTab(FilterMode mode) {
        currentFilter = mode;

        AppCompatButton[] tabs = {tabAll, tabUnpaid, tabPaid};
        FilterMode[] modes = {FilterMode.ALL, FilterMode.UNPAID, FilterMode.PAID};

        for (int i = 0; i < tabs.length; i++) {
            boolean active = (modes[i] == mode);
            tabs[i].setBackgroundResource(active
                    ? R.drawable.bg_tab_active
                    : android.R.color.transparent);
            tabs[i].setTextColor(ContextCompat.getColor(this,
                    active ? R.color.white : R.color.muted_foreground));
        }

        applyFilter();
    }

    // ═══════════════════════════════════════════════════════
    // HERO STATS
    // ═══════════════════════════════════════════════════════

    private void updateHeroStats() {
        int total = master.size();
        int unpaidCount = 0;
        double collected = 0;

        for (FineItem it : master) {
            boolean isPaid = STATUS_PAID.equalsIgnoreCase(it.status);
            if (isPaid) collected += it.amount;
            else        unpaidCount++;
        }

        if (tvCountFines      != null) tvCountFines.setText(String.valueOf(total));
        if (tvCountUnpaid     != null) tvCountUnpaid.setText(String.valueOf(unpaidCount));
        if (tvCollectedAmount != null) {
            if (collected == Math.floor(collected)) {
                tvCollectedAmount.setText(String.valueOf((long) collected));
            } else {
                tvCollectedAmount.setText(String.format(Locale.getDefault(), "%.0f", collected));
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // FILTER
    // ═══════════════════════════════════════════════════════

    private void applyFilter() {
        String q = "";
        if (etQuery.getText() != null) {
            q = etQuery.getText().toString().trim().toUpperCase(Locale.ROOT);
        }

        List<FineItem> out = new ArrayList<>();
        for (FineItem it : master) {
            // Status filter
            String st = it.status == null ? "" : it.status.toLowerCase(Locale.ROOT);
            boolean isPaid = STATUS_PAID.equals(st);

            boolean statusOk;
            switch (currentFilter) {
                case PAID:   statusOk = isPaid; break;
                case UNPAID: statusOk = !isPaid; break;
                default:     statusOk = true;
            }
            if (!statusOk) continue;

            // Query po tablicama
            String plate = it.plate == null ? "" : it.plate.toUpperCase(Locale.ROOT);
            if (!TextUtils.isEmpty(q) && !plate.contains(q)) continue;

            out.add(it);
        }

        adapter.submit(out);

        if (tvListHeader != null) {
            int n = out.size();
            String suffix;
            if (n == 1)                          suffix = " KAZNA";
            else if (n >= 2 && n <= 4)           suffix = " KAZNE";
            else                                 suffix = " KAZNI";
            tvListHeader.setText(n + suffix);
        }
    }

    private List<FineItem> snapshotToList(DataSnapshot ds) {
        List<FineItem> list = new ArrayList<>();
        for (DataSnapshot f : ds.getChildren()) {
            FineItem it = new FineItem();
            it.id           = f.getKey();
            it.plate        = f.child("plate").getValue(String.class);
            Double amount   = f.child("amount").getValue(Double.class);
            it.amount       = amount == null ? 0.0 : amount;
            it.status       = f.child("status").getValue(String.class);
            it.reason       = f.child("reason").getValue(String.class);
            it.parkingLotId = f.child("parkingLotId").getValue(String.class);
            list.add(it);
        }
        return list;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    // ═══════════════════════════════════════════════════════
    // ACTIONS BOTTOM SHEET (klik na row)
    // ═══════════════════════════════════════════════════════

    private void showFineActionsDialog(FineItem it) {
        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this)
                .inflate(R.layout.dialog_fine_actions, null, false);
        dlg.setContentView(v);

        View     vAvatarBg    = v.findViewById(R.id.vFineActionsAvatarBg);
        TextView tvAvatarIcon = v.findViewById(R.id.tvFineActionsAvatarIcon);
        TextView tvPlate      = v.findViewById(R.id.tvFineActionsPlate);
        TextView tvInfo       = v.findViewById(R.id.tvFineActionsInfo);

        boolean isPaid = STATUS_PAID.equalsIgnoreCase(it.status);

        tvPlate.setText(it.plate == null ? "—" : it.plate);

        String info = String.format(Locale.getDefault(), "%.2f KM", it.amount);
        if (!TextUtils.isEmpty(it.parkingLotId)) {
            info += "  ·  " + it.parkingLotId;
        }
        tvInfo.setText(info);

        // Avatar boja prema statusu
        if (isPaid) {
            vAvatarBg.setBackgroundResource(R.drawable.bg_fine_avatar_green);
            tvAvatarIcon.setText("✓");
            tvAvatarIcon.setTextColor(ContextCompat.getColor(this, R.color.green_700));
        } else {
            vAvatarBg.setBackgroundResource(R.drawable.bg_fine_avatar_red);
            tvAvatarIcon.setText("⚡");
            tvAvatarIcon.setTextColor(ContextCompat.getColor(this, R.color.destructive));
        }

        View rowMarkPaid = v.findViewById(R.id.rowMarkPaid);
        TextView tvMarkPaidTitle    = v.findViewById(R.id.tvMarkPaidTitle);
        TextView tvMarkPaidSubtitle = v.findViewById(R.id.tvMarkPaidSubtitle);
        AppCompatButton btnCancel   = v.findViewById(R.id.btnFineActionsCancel);

        if (isPaid) {
            // Već plaćena — disable akcija
            tvMarkPaidTitle.setText("Već je plaćena");
            tvMarkPaidSubtitle.setText("Kazna je prethodno naplaćena");
            rowMarkPaid.setAlpha(0.5f);
            rowMarkPaid.setEnabled(false);
        } else {
            rowMarkPaid.setOnClickListener(x -> {
                dlg.dismiss();
                showConfirmMarkPaidDialog(it);
            });
        }

        btnCancel.setOnClickListener(x -> dlg.dismiss());

        dlg.show();
    }

    // ═══════════════════════════════════════════════════════
    // CONFIRM MARK PAID BOTTOM SHEET
    // ═══════════════════════════════════════════════════════

    private void showConfirmMarkPaidDialog(FineItem it) {
        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this)
                .inflate(R.layout.dialog_confirm_mark_paid, null, false);
        dlg.setContentView(v);

        TextView tvAmount  = v.findViewById(R.id.tvConfirmPaidAmount);
        TextView tvPlate   = v.findViewById(R.id.tvConfirmPaidPlate);
        TextView tvParking = v.findViewById(R.id.tvConfirmPaidParking);

        tvAmount.setText(String.format(Locale.getDefault(), "%.2f KM", it.amount));
        tvPlate.setText(it.plate == null ? "—" : it.plate);
        tvParking.setText(TextUtils.isEmpty(it.parkingLotId) ? "—" : it.parkingLotId);

        AppCompatButton btnConfirm = v.findViewById(R.id.btnConfirmMarkPaid);
        AppCompatButton btnCancel  = v.findViewById(R.id.btnCancelMarkPaid);

        btnConfirm.setOnClickListener(x -> {
            dlg.dismiss();
            markFineAsPaid(it);
        });
        btnCancel.setOnClickListener(x -> dlg.dismiss());

        dlg.show();
    }

    private void markFineAsPaid(FineItem it) {
        DatabaseReference statusRef = FirebaseUtils.fine(it.id).child("status");
        statusRef.runTransaction(new Transaction.Handler() {
            @NonNull @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                // Abort if already paid — prevents double marking
                Object cur = currentData.getValue();
                String curStr = cur == null ? "" : String.valueOf(cur);
                if (STATUS_PAID.equalsIgnoreCase(curStr)) {
                    return Transaction.abort();
                }
                currentData.setValue(STATUS_PAID);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (error != null) {
                    toast("Greška: " + error.getMessage());
                    return;
                }
                // !committed means the transaction was aborted (already paid)
                if (!committed) {
                    toast("Već je označeno kao plaćeno.");
                } else {
                    toast("Kazna označena kao plaćena ✓");
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════
    // MODEL + ADAPTER
    // ═══════════════════════════════════════════════════════

    static class FineItem {
        String id, plate, status, reason, parkingLotId;
        double amount;
    }

    class FinesAdapter extends RecyclerView.Adapter<FinesAdapter.VH> {
        List<FineItem> data = new ArrayList<>();
        void submit(List<FineItem> d) { data = d; notifyDataSetChanged(); }

        class VH extends RecyclerView.ViewHolder {
            View vFineAvatarBg;
            TextView tvFineAvatarIcon, rowTitle, rowSubtitle, tvFineAmount, tvStatusBadge;

            VH(View v) {
                super(v);
                vFineAvatarBg    = v.findViewById(R.id.vFineAvatarBg);
                tvFineAvatarIcon = v.findViewById(R.id.tvFineAvatarIcon);
                rowTitle         = v.findViewById(R.id.rowTitle);
                rowSubtitle      = v.findViewById(R.id.rowSubtitle);
                tvFineAmount     = v.findViewById(R.id.tvFineAmount);
                tvStatusBadge    = v.findViewById(R.id.tvStatusBadge);
            }
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            View v = LayoutInflater.from(p.getContext())
                    .inflate(R.layout.row_fine_admin, p, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            FineItem it = data.get(pos);

            // Plate
            h.rowTitle.setText(TextUtils.isEmpty(it.plate) ? "—" : it.plate);

            // Subtitle: parking + reason
            StringBuilder sub = new StringBuilder();
            if (!TextUtils.isEmpty(it.parkingLotId)) sub.append(it.parkingLotId);
            if (!TextUtils.isEmpty(it.reason)) {
                if (sub.length() > 0) sub.append("  ·  ");
                sub.append(it.reason);
            }
            if (sub.length() == 0) sub.append("Kazna");
            h.rowSubtitle.setText(sub.toString());

            // Iznos
            h.tvFineAmount.setText(String.format(Locale.getDefault(), "%.2f KM", it.amount));

            // Boja avatara + status badge prema statusu
            boolean isPaid = STATUS_PAID.equalsIgnoreCase(it.status);
            if (isPaid) {
                h.vFineAvatarBg.setBackgroundResource(R.drawable.bg_fine_avatar_green);
                h.tvFineAvatarIcon.setText("✓");
                h.tvFineAvatarIcon.setTextColor(ContextCompat.getColor(
                        ManageFinesActivity.this, R.color.green_700));

                h.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_pill_paid);
                h.tvStatusBadge.setTextColor(ContextCompat.getColor(
                        ManageFinesActivity.this, R.color.green_700));
                h.tvStatusBadge.setText("PLAĆENO");
            } else {
                h.vFineAvatarBg.setBackgroundResource(R.drawable.bg_fine_avatar_red);
                h.tvFineAvatarIcon.setText("⚡");
                h.tvFineAvatarIcon.setTextColor(ContextCompat.getColor(
                        ManageFinesActivity.this, R.color.destructive));

                h.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_pill_unpaid);
                h.tvStatusBadge.setTextColor(ContextCompat.getColor(
                        ManageFinesActivity.this, R.color.destructive));
                h.tvStatusBadge.setText("NEPLAĆENO");
            }

            // Klik na cijeli row → actions sheet
            h.itemView.setOnClickListener(v -> showFineActionsDialog(it));
        }

        @Override public int getItemCount() { return data.size(); }
    }
}
