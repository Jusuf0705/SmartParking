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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UserHistoryFragment extends Fragment {

    // THIS_MONTH = ovaj mjesec, PREV_MONTH = prethodni mjesec, YEAR = ova godina
    private enum Period { THIS_MONTH, PREV_MONTH, YEAR }

    private View cardEmpty;
    private RecyclerView rv;
    private HistoryAdapter adapter;

    // Stats hero card
    private TextView tvStatLabel, tvStatAmount, tvStatSessions, tvStatHours, tvStatAvg;

    // Period switcher
    private TextView btnPeriodWeek, btnPeriodMonth, btnPeriodYear;

    private Period currentPeriod = Period.THIS_MONTH;

    private final List<HistRow> sessionsList = new ArrayList<>();
    private final List<HistRow> topupsList   = new ArrayList<>();
    private final Map<String, String> parkingNames = new HashMap<>();
    private final Map<String, String> zoneNames    = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_user_history, parent, false);

        rv        = v.findViewById(R.id.rvHistory);
        cardEmpty = v.findViewById(R.id.cardEmpty);

        tvStatLabel    = v.findViewById(R.id.tvStatLabel);
        tvStatAmount   = v.findViewById(R.id.tvStatAmount);
        tvStatSessions = v.findViewById(R.id.tvStatSessions);
        tvStatHours    = v.findViewById(R.id.tvStatHours);
        tvStatAvg      = v.findViewById(R.id.tvStatAvg);

        btnPeriodWeek  = v.findViewById(R.id.btnPeriodWeek);   // sada "Ovaj mjesec"
        btnPeriodMonth = v.findViewById(R.id.btnPeriodMonth);  // "Preth. mjesec"
        btnPeriodYear  = v.findViewById(R.id.btnPeriodYear);   // "Ova godina"

        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new HistoryAdapter();
        rv.setAdapter(adapter);

        btnPeriodWeek.setOnClickListener(v1 -> selectPeriod(Period.THIS_MONTH));
        btnPeriodMonth.setOnClickListener(v1 -> selectPeriod(Period.PREV_MONTH));
        btnPeriodYear.setOnClickListener(v1 -> selectPeriod(Period.YEAR));

        updatePeriodButtons();

        loadParkingNames();
        loadZoneNames();
        loadHistory();

        return v;
    }

    private void selectPeriod(Period p) {
        if (currentPeriod == p) return;
        currentPeriod = p;
        updatePeriodButtons();
        updateMerged();
    }

    private void updatePeriodButtons() {
        setPeriodButtonState(btnPeriodWeek,  currentPeriod == Period.THIS_MONTH);
        setPeriodButtonState(btnPeriodMonth, currentPeriod == Period.PREV_MONTH);
        setPeriodButtonState(btnPeriodYear,  currentPeriod == Period.YEAR);
    }

    private void setPeriodButtonState(TextView btn, boolean active) {
        if (btn == null || getContext() == null) return;
        if (active) {
            btn.setBackgroundResource(R.drawable.bg_period_active);
            btn.setTextColor(ContextCompat.getColor(getContext(), R.color.blue_600));
        } else {
            btn.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            btn.setTextColor(ContextCompat.getColor(getContext(), R.color.muted_foreground));
        }
    }

    // ── Raspon datuma za odabrani period ──────────────────
    private long[] getPeriodRange(Period p) {
        Calendar cal = Calendar.getInstance();

        switch (p) {
            case THIS_MONTH: {
                // Od 1. ovog mjeseca 00:00 do sada
                long now = System.currentTimeMillis();
                cal.set(Calendar.DAY_OF_MONTH, 1);
                setStartOfDay(cal);
                return new long[]{cal.getTimeInMillis(), now};
            }
            case PREV_MONTH: {
                // Cijeli prethodni mjesec: 1. 00:00 → zadnji dan 23:59:59
                cal.add(Calendar.MONTH, -1);
                cal.set(Calendar.DAY_OF_MONTH, 1);
                setStartOfDay(cal);
                long start = cal.getTimeInMillis();

                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
                setEndOfDay(cal);
                long end = cal.getTimeInMillis();
                return new long[]{start, end};
            }
            case YEAR: {
                // Od 1. januara ove godine 00:00 do sada
                long now = System.currentTimeMillis();
                cal.set(Calendar.MONTH, Calendar.JANUARY);
                cal.set(Calendar.DAY_OF_MONTH, 1);
                setStartOfDay(cal);
                return new long[]{cal.getTimeInMillis(), now};
            }
        }
        return new long[]{0L, System.currentTimeMillis()};
    }

    private void setStartOfDay(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    private void setEndOfDay(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        c.set(Calendar.MILLISECOND, 999);
    }

    private String periodLabel(Period p) {
        switch (p) {
            case THIS_MONTH: return "Potrošeno · ovaj mjesec";
            case PREV_MONTH: return "Potrošeno · prethodni mjesec";
            case YEAR:       return "Potrošeno · ova godina";
        }
        return "Potrošeno";
    }

    // ── Učitavanje naziva parkinga ────────────────────────
    private void loadParkingNames() {
        FirebaseUtils.root().child("parkingLots")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        parkingNames.clear();
                        for (DataSnapshot lot : ds.getChildren()) {
                            String id   = lot.getKey();
                            String name = lot.child("name").getValue(String.class);
                            if (id != null && name != null) parkingNames.put(id, name);
                        }
                        updateMerged();
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    // ── Učitavanje naziva zona ───────────────────────────
    private void loadZoneNames() {
        FirebaseUtils.zonesRef()
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        zoneNames.clear();
                        for (DataSnapshot z : ds.getChildren()) {
                            String id   = z.getKey();
                            String name = z.child("name").getValue(String.class);
                            if (id != null && name != null) zoneNames.put(id, name);
                        }
                        updateMerged();
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    private void loadHistory() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        // Parking sessions — counted as spending
        FirebaseUtils.sessionsRef()
                .orderByChild("userId")
                .equalTo(uid)
                .addValueEventListener(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot ds) {
                        sessionsList.clear();
                        for (DataSnapshot it : ds.getChildren()) {
                            HistRow r      = new HistRow();
                            r.kind         = HistKind.PARKING;
                            r.parkingLotId = it.child("parkingLotId").getValue(String.class);
                            r.zoneId       = it.child("zoneId").getValue(String.class);
                            r.plate        = it.child("plate").getValue(String.class);
                            r.amount       = getD(it.child("amount").getValue());
                            r.type         = it.child("type").getValue(String.class);
                            r.status       = it.child("status").getValue(String.class);
                            Long st = it.child("startTime").getValue(Long.class);
                            Long en = it.child("endTime").getValue(Long.class);
                            r.start = (st == null ? 0L : st);
                            r.end   = (en == null ? 0L : en);
                            r.time  = (r.start > 0 ? r.start : r.end);
                            sessionsList.add(r);
                        }
                        updateMerged();
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) { toast(e.getMessage()); }
                });

        // Top-ups — shown in the list, but NOT counted as spending
        FirebaseUtils.root().child("balanceTopups")
                .orderByChild("userId")
                .equalTo(uid)
                .addValueEventListener(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot ds) {
                        topupsList.clear();
                        for (DataSnapshot it : ds.getChildren()) {
                            Double amt = it.child("amount").getValue(Double.class);
                            Long   t   = it.child("createdAt").getValue(Long.class);
                            if (amt == null || t == null) continue;
                            HistRow r = new HistRow();
                            r.kind   = HistKind.TOPUP;
                            r.amount = amt;
                            r.time   = t;
                            topupsList.add(r);
                        }
                        updateMerged();
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) { toast(e.getMessage()); }
                });
    }

    private void updateMerged() {
        long[] range = getPeriodRange(currentPeriod);
        long start = range[0], end = range[1];

        // Parking sesije u periodu
        List<HistRow> filteredSessions = new ArrayList<>();
        for (HistRow r : sessionsList)
            if (r.time >= start && r.time <= end) filteredSessions.add(r);

        // Uplate u periodu (samo za prikaz u listi)
        List<HistRow> filteredTopups = new ArrayList<>();
        for (HistRow r : topupsList)
            if (r.time >= start && r.time <= end) filteredTopups.add(r);

        // ── Statistika: SAMO parking sesije se broje kao "Potrošeno" ──
        double totalSpent   = 0;
        double totalHours   = 0;
        int    sessionCount = filteredSessions.size();

        for (HistRow r : filteredSessions) {
            totalSpent += r.amount;   // bez refund-a
            if (r.start > 0) {
                long effectiveEnd = r.end;
                // Za aktivnu sesiju (kraj u budućnosti) broji do sada
                long nowMs = System.currentTimeMillis();
                if (effectiveEnd <= 0 || effectiveEnd > nowMs) effectiveEnd = nowMs;
                if (effectiveEnd > r.start) {
                    totalHours += (effectiveEnd - r.start) / 3600000.0;
                }
            }
        }

        double avg = sessionCount > 0 ? totalSpent / sessionCount : 0;

        if (tvStatLabel != null)    tvStatLabel.setText(periodLabel(currentPeriod));
        if (tvStatAmount != null)   tvStatAmount.setText(String.format(Locale.getDefault(), "%.2f", totalSpent));
        if (tvStatSessions != null) tvStatSessions.setText(String.valueOf(sessionCount));
        if (tvStatHours != null)    tvStatHours.setText(String.format(Locale.getDefault(), "%.1f", totalHours));
        if (tvStatAvg != null)      tvStatAvg.setText(String.format(Locale.getDefault(), "%.2f KM", avg));

        // ── Lista: sesije + uplate (uplate se vide, ali ne ulaze u trošak) ──
        List<HistRow> merged = new ArrayList<>();
        merged.addAll(filteredSessions);
        merged.addAll(filteredTopups);
        merged.sort((a, bRow) -> Long.compare(bRow.time, a.time));

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
        double   amount;
        long     time;
        String   parkingLotId, zoneId, space, plate, type, status;
        long     start, end;
    }

    // ─────────────────────────────────────────────────────
    class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

        List<HistRow> data = new ArrayList<>();

        void submit(List<HistRow> d) { data = d; notifyDataSetChanged(); }

        class VH extends RecyclerView.ViewHolder {
            MaterialCardView card;
            android.widget.ImageView ivCarIcon;
            TextView tvType, tvZoneBadge, tvAmount, tvDate, tvSub, tvStatus;

            VH(View v) {
                super(v);
                card        = v.findViewById(R.id.cardHistory);
                ivCarIcon   = v.findViewById(R.id.ivCarIcon);
                tvType      = v.findViewById(R.id.tvType);
                tvZoneBadge = v.findViewById(R.id.tvZoneBadge);
                tvAmount    = v.findViewById(R.id.tvAmount);
                tvDate      = v.findViewById(R.id.tvDate);
                tvSub       = v.findViewById(R.id.tvSub);
                tvStatus    = v.findViewById(R.id.tvStatus);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            View v = LayoutInflater.from(p.getContext())
                    .inflate(R.layout.row_history_item, p, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            HistRow r = data.get(pos);

            // ── TOPUP (uplata kredita) ──────────────────
            if (r.kind == HistKind.TOPUP) {
                if (h.ivCarIcon != null) h.ivCarIcon.setImageResource(R.drawable.ic_credit_card);
                h.tvType.setText("Uplata kredita");
                h.tvZoneBadge.setVisibility(View.GONE);
                h.tvAmount.setText(String.format(Locale.getDefault(), "+%.2f KM", r.amount));
                h.tvDate.setText(DateFormat.format("dd.MM.yyyy  HH:mm", r.time));
                h.tvSub.setText("—");
                h.tvStatus.setVisibility(View.GONE);
                resetCardBorder(h.card);
                return;
            }

            // ── PARKING SESIJA ───────────────────────────
            if (h.ivCarIcon != null) h.ivCarIcon.setImageResource(R.drawable.ic_car);

            long now = System.currentTimeMillis();
            boolean isActive = r.status != null
                    && r.status.trim().equalsIgnoreCase("ACTIVE")
                    && r.end > now;

            String zoneName = zoneNames.get(r.zoneId);
            if (zoneName == null)
                zoneName = (r.zoneId == null ? "—" : r.zoneId);

            String typeLabel = (r.type != null && r.type.equals("day"))
                    ? "Dnevna karta" : "Satnica";

            h.tvType.setText(typeLabel);
            h.tvZoneBadge.setVisibility(View.VISIBLE);
            h.tvZoneBadge.setText(zoneName);
            h.tvAmount.setText(String.format(Locale.getDefault(), "%.2f KM", r.amount));

            String dateStr = "";
            if (r.start > 0 && r.end > 0) {
                dateStr = DateFormat.format("dd.MM.yyyy  HH:mm", r.start)
                        + " – "
                        + DateFormat.format("HH:mm", r.end);
            } else if (r.start > 0) {
                dateStr = DateFormat.format("dd.MM.yyyy  HH:mm", r.start).toString();
            }
            h.tvDate.setText(dateStr);

            String plate = (r.plate != null && !r.plate.isEmpty())
                    ? r.plate.toUpperCase(Locale.ROOT)
                    : (r.space != null ? r.space : "—");
            h.tvSub.setText(plate);

            h.tvStatus.setVisibility(View.VISIBLE);
            if (isActive) {
                h.tvStatus.setText("Aktivno");
                h.tvStatus.setBackgroundResource(R.drawable.bg_status_pill_active);
                h.tvStatus.setTextColor(0xFF16A34A);
                if (h.card != null) {
                    float density = h.itemView.getResources().getDisplayMetrics().density;
                    h.card.setStrokeWidth((int) (2 * density));
                    h.card.setStrokeColor(0xFF22C55E);
                    h.card.setCardElevation(4 * density);
                }
            } else {
                h.tvStatus.setText("Završeno");
                h.tvStatus.setBackgroundResource(R.drawable.bg_status_pill_done);
                h.tvStatus.setTextColor(0xFF94A3B8);
                resetCardBorder(h.card);
            }
        }

        private void resetCardBorder(MaterialCardView card) {
            if (card == null) return;
            float density = card.getResources().getDisplayMetrics().density;
            card.setStrokeWidth((int) (1 * density));
            card.setStrokeColor(0x142563EB);
            card.setCardElevation(2 * density);
        }

        @Override
        public int getItemCount() { return data.size(); }
    }
}