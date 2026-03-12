package com.example.smartparking.ui.user;

import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UserHistoryFragment extends Fragment {

    private View cardEmpty;
    private RecyclerView rv;
    private HistoryAdapter adapter;

    private final List<HistRow> sessionsList = new ArrayList<>();
    private final List<HistRow> topupsList = new ArrayList<>();

    private final Map<String, String> parkingNames = new HashMap<>();

    // 30 dana u ms (30 * 24h * 60m * 60s * 1000ms)
    private static final long THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_user_history, parent, false);

        rv = v.findViewById(R.id.rvHistory);
        cardEmpty = v.findViewById(R.id.cardEmpty);

        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new HistoryAdapter();
        rv.setAdapter(adapter);

        loadParkingNames(); // prvo učitaj imena parkinga
        loadHistory();      // pa historiju

        return v;
    }

    // ✅ Učitavanje naziva parkinga
    private void loadParkingNames() {
        FirebaseUtils.root().child("parkingLots")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        parkingNames.clear();
                        for (DataSnapshot lot : ds.getChildren()) {
                            String id = lot.getKey();
                            String name = lot.child("name").getValue(String.class);
                            if (id != null && name != null) {
                                parkingNames.put(id, name);
                            }
                        }
                        updateMerged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void loadHistory() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        // 1) PARKING SESIJE
        FirebaseUtils.sessionsRef()
                .orderByChild("userId")
                .equalTo(uid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        sessionsList.clear();

                        for (DataSnapshot it : ds.getChildren()) {
                            Long st = it.child("startTime").getValue(Long.class);
                            Long en = it.child("endTime").getValue(Long.class);

                            long start = (st == null ? 0L : st);
                            long end   = (en == null ? 0L : en);

                            HistRow r = new HistRow();
                            r.kind = HistKind.PARKING;

                            r.parkingLotId = it.child("parkingLotId").getValue(String.class);
                            r.space        = it.child("space").getValue(String.class);
                            r.amount       = getD(it.child("amount").getValue());
                            r.type         = it.child("type").getValue(String.class);

                            r.start = start;
                            r.end   = end;

                            // ✅ REFUND PODACI (NOVO)
                            r.status = it.child("status").getValue(String.class);
                            Double ra = it.child("refundedAmount").getValue(Double.class);
                            Long rat  = it.child("refundedAt").getValue(Long.class);
                            r.refundedAmount = (ra == null ? 0.0 : ra);
                            r.refundedAt = (rat == null ? 0L : rat);

                            // ✅ Sort time: ako je refundirano, zadnji event je refundAt
                            long baseTime = (end > 0 ? end : start);
                            r.time = (r.refundedAt > 0 ? r.refundedAt : baseTime);

                            sessionsList.add(r);
                        }
                        updateMerged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {
                        toast(e.getMessage());
                    }
                });

        // 2) TOPUP LOG
        FirebaseUtils.root().child("balanceTopups")
                .orderByChild("userId")
                .equalTo(uid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        topupsList.clear();

                        for (DataSnapshot it : ds.getChildren()) {
                            Double amt = it.child("amount").getValue(Double.class);
                            Long t = it.child("createdAt").getValue(Long.class);

                            if (amt == null || t == null) continue;

                            HistRow r = new HistRow();
                            r.kind = HistKind.TOPUP;
                            r.amount = amt;
                            r.time = t;

                            topupsList.add(r);
                        }
                        updateMerged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {
                        toast(e.getMessage());
                    }
                });
    }

    private void updateMerged() {
        long now = System.currentTimeMillis();
        List<HistRow> merged = new ArrayList<>();

        for (HistRow r : sessionsList)
            if ((now - r.time) <= THIRTY_DAYS_MS) merged.add(r);

        for (HistRow r : topupsList)
            if ((now - r.time) <= THIRTY_DAYS_MS) merged.add(r);

        merged.sort((a, b) -> Long.compare(b.time, a.time));

        adapter.submit(merged);
        if (cardEmpty != null)
            cardEmpty.setVisibility(merged.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static double getD(Object o) {
        try { return o == null ? 0 : Double.parseDouble(String.valueOf(o)); }
        catch (Exception e) { return 0; }
    }

    private void toast(String msg) {
        if (getContext() != null && !TextUtils.isEmpty(msg))
            Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
    }

    enum HistKind { PARKING, TOPUP }

    static class HistRow {
        HistKind kind;
        double amount;
        long time;

        String parkingLotId, space, type;
        long start, end;

        // ✅ REFUND (NOVO)
        String status;
        double refundedAmount;
        long refundedAt;
    }

    class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

        List<HistRow> data = new ArrayList<>();
        void submit(List<HistRow> d){ data = d; notifyDataSetChanged(); }

        class VH extends RecyclerView.ViewHolder {
            TextView t1, t2;
            VH(View v){
                super(v);
                t1 = v.findViewById(R.id.tvTitle);
                t2 = v.findViewById(R.id.tvSub);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.row_history_item, p, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int i) {
            HistRow r = data.get(i);

            // TOPUP
            if (r.kind == HistKind.TOPUP) {
                h.t1.setText("Uplata • " + String.format(Locale.getDefault(),"%.2f KM", r.amount));
                h.t2.setText(DateFormat.format("dd.MM.yyyy HH:mm", r.time));
                return;
            }

            // PARKING
            String lotName = parkingNames.get(r.parkingLotId);
            if (lotName == null) lotName = (r.parkingLotId == null ? "—" : r.parkingLotId);

            boolean isRefunded = r.status != null && r.status.trim().equalsIgnoreCase("REFUNDED");
            String baseType = (r.type != null && r.type.equals("day")) ? "Dnevna karta" : "Satnica";

            String title;
            if (isRefunded) {
                title = "Refund • +" + String.format(Locale.getDefault(), "%.2f KM", r.refundedAmount);
            } else {
                title = baseType + " • " + String.format(Locale.getDefault(), "%.2f KM", r.amount);
            }

            String when = DateFormat.format("dd.MM.yyyy HH:mm", r.start)
                    + " — "
                    + DateFormat.format("dd.MM.yyyy HH:mm", r.end);



            String sub = "Parking: " + lotName
                    + " • Mjesto: " + (r.space == null ? "?" : r.space)
                    + "\n" + when;

            h.t1.setText(title);
            h.t2.setText(sub);
        }

        @Override public int getItemCount(){ return data.size(); }
    }
}
