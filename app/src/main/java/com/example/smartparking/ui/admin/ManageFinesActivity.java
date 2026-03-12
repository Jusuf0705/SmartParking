package com.example.smartparking.ui.admin;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.google.android.material.button.MaterialButtonToggleGroup;
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

    private TextView etQuery; // ostavljam kao TextInputEditText u layoutu; ovdje je dovoljan TextView tip
    private RecyclerView rv;
    private FinesAdapter adapter;
    private DatabaseReference finesRef;

    private MaterialButtonToggleGroup toggleStatus;

    private static final String STATUS_PAID = "paid";
    private static final String STATUS_UNPAID = "unpaid"; // ako ih eksplicitno snimaš

    private final List<FineItem> master = new ArrayList<>();
    // 0 = ALL, 1 = UNPAID, 2 = PAID
    private int statusFilter = 0;

    private final ValueEventListener finesListener = new ValueEventListener() {
        @Override public void onDataChange(@NonNull DataSnapshot ds) {
            master.clear();
            master.addAll(snapshotToList(ds));
            applyFilter();
        }
        @Override public void onCancelled(@NonNull DatabaseError e) { toast(e.getMessage()); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_manage_fines);

        etQuery       = findViewById(R.id.etQuery);
        toggleStatus  = findViewById(R.id.toggleStatus);
        rv            = findViewById(R.id.recycler);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FinesAdapter();
        rv.setAdapter(adapter);

        finesRef = FirebaseUtils.finesRef();

        // Auto-pretraga (on-type)
        if (etQuery instanceof android.widget.EditText) {
            ((android.widget.EditText) etQuery).addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) { applyFilter(); }
            });
        }

        // Toggle status filter
        toggleStatus.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnFilterAll)         statusFilter = 0;
            else if (checkedId == R.id.btnFilterUnpaid) statusFilter = 1;
            else if (checkedId == R.id.btnFilterPaid)   statusFilter = 2;
            applyFilter();
        });

        toggleStatus.check(R.id.btnFilterAll);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Realtime sync – čim se nešto promijeni u /fines, UI se osvježi
        finesRef.addValueEventListener(finesListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        finesRef.removeEventListener(finesListener);
    }

    /** Kombinuje text query + status filter nad 'master' i šalje u adapter. */
    private void applyFilter() {
        String q = "";
        if (etQuery instanceof android.widget.EditText) {
            CharSequence t = ((android.widget.EditText) etQuery).getText();
            q = t == null ? "" : t.toString().trim().toUpperCase(Locale.ROOT);
        }

        List<FineItem> out = new ArrayList<>();
        for (FineItem it : master) {
            // 1) Status filter
            boolean statusOk;
            String st = it.status == null ? "" : it.status.toLowerCase(Locale.ROOT);
            if (statusFilter == 2) { // PAID
                statusOk = STATUS_PAID.equals(st);
            } else if (statusFilter == 1) { // UNPAID
                // ako ne snimaš eksplicitno "unpaid", sve što NIJE "paid" tretiramo kao neplaćeno
                statusOk = !STATUS_PAID.equals(st);
            } else {
                statusOk = true; // ALL
            }
            if (!statusOk) continue;

            // 2) Query po registraciji
            String plate = it.plate == null ? "" : it.plate.toUpperCase(Locale.ROOT);
            boolean textOk = TextUtils.isEmpty(q) || plate.contains(q);
            if (textOk) out.add(it);
        }
        adapter.submit(out);
    }

    private List<FineItem> snapshotToList(DataSnapshot ds) {
        List<FineItem> list = new ArrayList<>();
        for (DataSnapshot f : ds.getChildren()) {
            FineItem it = new FineItem();
            it.id = f.getKey();
            it.plate = f.child("plate").getValue(String.class);
            Double amount = f.child("amount").getValue(Double.class);
            it.amount = amount == null ? 0.0 : amount;
            it.status = f.child("status").getValue(String.class);
            it.reason = f.child("reason").getValue(String.class);
            it.parkingLotId = f.child("parkingLotId").getValue(String.class);
            list.add(it);
        }
        return list;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    // ===== Adapter =====
    static class FineItem {
        String id, plate, status, reason, parkingLotId;
        double amount;
    }

    class FinesAdapter extends RecyclerView.Adapter<FinesAdapter.VH> {
        List<FineItem> data = new ArrayList<>();
        void submit(List<FineItem> d) { data = d; notifyDataSetChanged(); }

        class VH extends RecyclerView.ViewHolder {
            TextView t1, t2;
            Button btnPay, btnDelete, btnRole;
            VH(View v) {
                super(v);
                t1 = v.findViewById(R.id.rowTitle);
                t2 = v.findViewById(R.id.rowSubtitle);
                btnPay = v.findViewById(R.id.btnEdit);
                btnDelete = v.findViewById(R.id.btnDelete);
                btnRole = v.findViewById(R.id.btnRole);

                btnPay.setText("Označi kao plaćeno");
                btnDelete.setVisibility(View.GONE);
                btnRole.setVisibility(View.GONE);
            }
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            View v = LayoutInflater.from(p.getContext())
                    .inflate(R.layout.row_three_actions, p, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            FineItem it = data.get(pos);

            String title = (it.plate == null ? "" : it.plate)
                    + " • " + String.format(Locale.ROOT, "%.2f BAM", it.amount);
            String subtitle = (it.parkingLotId == null ? "" : it.parkingLotId + " • ")
                    + (it.status == null ? "" : it.status)
                    + (TextUtils.isEmpty(it.reason) ? "" : " • " + it.reason);

            h.t1.setText(title);
            h.t2.setText(subtitle);

            // Ako je plaćena, sakrij dugme
            boolean isPaid = STATUS_PAID.equalsIgnoreCase(it.status);
            h.btnPay.setVisibility(isPaid ? View.GONE : View.VISIBLE);
            h.btnPay.setEnabled(!isPaid);

            h.btnPay.setOnClickListener(v -> {
                // Optimistic UI: odmah onemogući da se ne može spamati
                h.btnPay.setEnabled(false);

                // Transakcija: set status = "paid" samo ako već nije "paid"
                DatabaseReference statusRef = FirebaseUtils.fine(it.id).child("status");
                statusRef.runTransaction(new Transaction.Handler() {
                    @NonNull @Override
                    public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                        Object cur = currentData.getValue();
                        String curStr = cur == null ? "" : String.valueOf(cur);
                        if (STATUS_PAID.equalsIgnoreCase(curStr)) {
                            // Već plaćeno -> ne mijenjaj ništa
                            return Transaction.abort();
                        }
                        currentData.setValue(STATUS_PAID);
                        return Transaction.success(currentData);
                    }

                    @Override
                    public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                        if (error != null) {
                            toast("Greška: " + error.getMessage());
                            // vrati dugme ako nije uspjelo
                            h.btnPay.setEnabled(true);
                            return;
                        }
                        if (!committed) {
                            // Neko je već označio kao plaćeno
                            toast("Već je označeno kao plaćeno.");
                        } else {
                            toast("Kazna označena kao plaćena.");
                        }

                        // Lokalno ažuriraj model da UI odmah reaguje
                        it.status = STATUS_PAID;
                        notifyItemChanged(h.getBindingAdapterPosition());

                        // Dodatno: sakrij dugme odmah
                        h.btnPay.setVisibility(View.GONE);
                    }
                });
            });
        }

        @Override public int getItemCount() { return data.size(); }
    }
}
