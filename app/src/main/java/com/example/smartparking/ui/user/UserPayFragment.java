package com.example.smartparking.ui.user;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class UserPayFragment extends Fragment {

    private static final long ONE_HOUR_MS = 60L * 60L * 1000L;
    private static final long ONE_DAY_MS  = 24L * 60L * 60L * 1000L;
    private static final long TICK_MS     = 1000L;
    private static final long DRIFT_MS    = 8000L;

    private static final long WELCOME_DIALOG_MS = 5000L;

    private static final String PREFS_NAME = "sp_user_pay";
    private static final String K_UID         = "uid";
    private static final String K_LOT_ID      = "lot_id";
    private static final String K_LOT_POS     = "lot_pos";
    private static final String K_VEHICLE_POS = "vehicle_pos";

    private static final int TYPE_VEHICLE = 0;
    private static final int TYPE_ADD     = 1;

    private TextView tvBalance, tvBalanceDecimal;

    private ViewPager2 vpVehicles;
    private VehicleSliderAdapter vehicleAdapter;
    private LinearLayout llVehicleDots;
    private final List<VehicleDisplay> vehicles = new ArrayList<>();
    private int currentVehicleIndex = 0;

    private LinearLayout llActiveSessions, llSessionDots;
    private ViewPager2 vpActiveSessions;
    private SessionSliderAdapter sessionAdapter;
    private TextView tvActiveCount;
    private final List<ActiveSession> sessionList = new ArrayList<>();
    private int currentSessionIndex = 0;

    private LinearLayout llZoneContainer;
    private final List<AppCompatButton> zoneButtons = new ArrayList<>();
    private int selectedZoneChip = 0;

    private AppCompatButton btnDuration1, btnDuration2;
    private AppCompatButton btnPay, btnDailyPass;
    private String selectedDuration = "1h";

    private final List<ZoneItem> zones = new ArrayList<>();

    private static class ActiveSession {
        String plate, zoneName, sessionId;
        long startTime, endTime;
        double amount;
    }
    private final Map<String, ActiveSession> activeSessionsMap = new HashMap<>();

    private Runnable statusTick;
    private boolean isPaying     = false;
    private boolean restoredOnce = false;

    private double cachedBalance = 0.0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup parent,
                             @Nullable Bundle b) {

        View v = inf.inflate(R.layout.fragment_user_pay, parent, false);

        tvBalance        = v.findViewById(R.id.tvBalance);
        tvBalanceDecimal = v.findViewById(R.id.tvBalanceDecimal);

        llActiveSessions = v.findViewById(R.id.llActiveSessions);
        llSessionDots    = v.findViewById(R.id.llSessionDots);
        vpActiveSessions = v.findViewById(R.id.vpActiveSessions);
        tvActiveCount    = v.findViewById(R.id.tvActiveCount);

        sessionAdapter = new SessionSliderAdapter();
        vpActiveSessions.setAdapter(sessionAdapter);
        vpActiveSessions.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                currentSessionIndex = position;
                updateSessionDots();
            }
        });

        vpVehicles    = v.findViewById(R.id.vpVehicles);
        llVehicleDots = v.findViewById(R.id.llVehicleDots);

        vehicleAdapter = new VehicleSliderAdapter();
        vpVehicles.setAdapter(vehicleAdapter);
        vpVehicles.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                currentVehicleIndex = position;
                updateVehicleDots();
                saveFormState();
            }
        });

        llZoneContainer = v.findViewById(R.id.llZoneContainer);

        btnDuration1 = v.findViewById(R.id.btnDuration1);
        btnDuration2 = v.findViewById(R.id.btnDuration2);
        btnPay       = v.findViewById(R.id.btnPay);
        btnDailyPass = v.findViewById(R.id.btnDailyPass);

        if (btnDuration1 != null) btnDuration1.setOnClickListener(vw -> selectDuration("1h", 0));
        if (btnDuration2 != null) btnDuration2.setOnClickListener(vw -> selectDuration("2h", 1));
        if (btnDailyPass != null) btnDailyPass.setOnClickListener(vw -> selectDuration("day", 2));
        updateDurationChipStyles(0);

        if (btnPay != null) btnPay.setOnClickListener(vw -> {
            if (validateInputs()) showConfirmDialog(selectedDuration);
        });

        AppCompatButton btnOpenTopup = v.findViewById(R.id.btnOpenTopup);
        if (btnOpenTopup != null) btnOpenTopup.setOnClickListener(vw -> openAddCardScreen());

        loadBalanceAndCheckWelcomeBonus();
        loadZones();
        loadUserVehicles();

        return v;
    }

    // -- Welcome bonus --

    private void loadBalanceAndCheckWelcomeBonus() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        Context ctx = getContext();
        if (ctx == null) { attachBalanceListener(uid); return; }

        SharedPreferences bonusPrefs = ctx.getApplicationContext()
                .getSharedPreferences("sp_welcome_bonus", Context.MODE_PRIVATE);

        boolean granted = bonusPrefs.getBoolean("granted_"  + uid, false);
        boolean uiShown = bonusPrefs.getBoolean("ui_shown_" + uid, false);

        attachBalanceListener(uid);

        if (granted && !uiShown) {
            showWelcomeBonusDialog();
            bonusPrefs.edit().putBoolean("ui_shown_" + uid, true).apply();
        } else if (!granted) {
            com.example.smartparking.data.WelcomeBonus.grantIfNew(ctx, uid, wasGranted -> {
                if (!isAdded()) return;
                if (wasGranted) {
                    showWelcomeBonusDialog();
                    ctx.getApplicationContext()
                            .getSharedPreferences("sp_welcome_bonus", Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("ui_shown_" + uid, true)
                            .apply();
                }
            });
        }
    }

    private void attachBalanceListener(String uid) {
        FirebaseUtils.balance(uid).addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot s) {
                Double bal = s.getValue(Double.class);
                double val = bal == null ? 0.0 : bal;
                cachedBalance = val;
                long whole = (long) val;
                int cents = (int) Math.round((val - whole) * 100);
                if (cents >= 100) { whole += 1; cents = 0; }
                if (tvBalance != null) tvBalance.setText(String.valueOf(whole));
                if (tvBalanceDecimal != null)
                    tvBalanceDecimal.setText(String.format(Locale.getDefault(), ",%02d KM", cents));
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    private void showWelcomeBonusDialog() {
        if (!isAdded()) return;

        final android.app.Dialog dlg = new android.app.Dialog(requireContext());
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        View v = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_welcome_bonus, null, false);
        dlg.setContentView(v);
        dlg.setCancelable(true);

        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dlg.getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            dlg.getWindow().setGravity(android.view.Gravity.CENTER);
        }

        ProgressBar pb = v.findViewById(R.id.pbWelcomeAutoDismiss);
        AppCompatButton btnClose = v.findViewById(R.id.btnWelcomeClose);
        btnClose.setOnClickListener(x -> dlg.dismiss());

        if (pb != null) {
            android.animation.ObjectAnimator anim =
                    android.animation.ObjectAnimator.ofInt(pb, "progress", 100, 0);
            anim.setDuration(WELCOME_DIALOG_MS);
            anim.setInterpolator(new LinearInterpolator());
            anim.start();
        }

        Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(() -> { if (dlg.isShowing()) dlg.dismiss(); }, WELCOME_DIALOG_MS);

        dlg.show();
    }

    // -- Active sessions: ViewPager slider + dots --

    private void renderActiveSessions() {
        if (llActiveSessions == null) return;

        if (activeSessionsMap.isEmpty()) {
            llActiveSessions.setVisibility(View.GONE);
            sessionList.clear();
            sessionAdapter.notifyDataSetChanged();
            return;
        }

        llActiveSessions.setVisibility(View.VISIBLE);

        List<ActiveSession> sorted = new ArrayList<>(activeSessionsMap.values());
        Collections.sort(sorted, (a, bb) -> Long.compare(a.endTime, bb.endTime));

        sessionList.clear();
        sessionList.addAll(sorted);
        sessionAdapter.notifyDataSetChanged();

        if (currentSessionIndex >= sessionList.size()) currentSessionIndex = 0;
        vpActiveSessions.setCurrentItem(currentSessionIndex, false);

        if (tvActiveCount != null)
            tvActiveCount.setText(sessionList.size() > 1 ? sessionList.size() + " aktivnih" : "");

        updateSessionDots();
    }

    private void updateSessionDots() {
        buildDots(llSessionDots, sessionList.size(), currentSessionIndex);
    }

    private void updateAllActiveSessionsUI() {
        if (vpActiveSessions == null) return;

        long now = System.currentTimeMillis();
        boolean anyExpired = false;

        RecyclerView rv = (RecyclerView) vpActiveSessions.getChildAt(0);
        if (rv != null) {
            for (int i = 0; i < sessionList.size(); i++) {
                ActiveSession s = sessionList.get(i);
                RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(i);
                if (vh == null) continue;
                TextView tvCountdown = vh.itemView.findViewById(R.id.tvSessionCountdown);
                ProgressBar pb       = vh.itemView.findViewById(R.id.progressSessionTimer);
                if (tvCountdown == null || pb == null) continue;

                long remain = s.endTime - now;
                long total  = s.endTime - s.startTime;
                if (remain > 0 && total > 0) {
                    tvCountdown.setText(formatRemain(remain));
                    int percent = (int) Math.max(0, Math.min(100, (remain * 100L) / total));
                    pb.setProgress(percent);
                } else {
                    anyExpired = true;
                }
            }
        }

        if (anyExpired) {
            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, ActiveSession> e : activeSessionsMap.entrySet())
                if (e.getValue().endTime <= now) toRemove.add(e.getKey());
            for (String k : toRemove) activeSessionsMap.remove(k);
            renderActiveSessions();
        }
    }

    // -- Vehicle dots --

    private void updateVehicleDots() {
        buildDots(llVehicleDots, vehicleAdapter.getItemCount(), currentVehicleIndex);
    }

    /** Builds the small "N of M" page-indicator dots into a container, sizing the active one larger. */
    private void buildDots(LinearLayout container, int total, int activeIndex) {
        if (container == null) return;
        container.removeAllViews();
        if (total <= 1) return;

        int activeColor   = 0xFF2563EB;
        int inactiveColor = 0xFFD0D4E8;

        for (int i = 0; i < total; i++) {
            View dot = new View(requireContext());
            boolean isActive = (i == activeIndex);
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int dotW = (int) android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_DIP, isActive ? 20 : 8, dm);
            int dotH = (int) android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_DIP, 8, dm);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dotW, dotH);
            lp.setMargins(4, 0, 4, 0);
            dot.setLayoutParams(lp);
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            gd.setCornerRadius(20f);
            gd.setColor(isActive ? activeColor : inactiveColor);
            dot.setBackground(gd);
            container.addView(dot);
        }
    }

    // -- Zone chips, built dynamically in a horizontal slider --

    private void buildZoneButtons() {
        if (llZoneContainer == null) return;
        llZoneContainer.removeAllViews();
        zoneButtons.clear();

        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int wPx = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 110, dm);
        int hPx = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 52, dm);
        int mPx = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 10, dm);

        for (int i = 0; i < zones.size(); i++) {
            final int idx = i;
            AppCompatButton btn = new AppCompatButton(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(wPx, hPx);
            if (i < zones.size() - 1) lp.setMarginEnd(mPx);
            btn.setLayoutParams(lp);
            btn.setText(zones.get(i).name);
            btn.setAllCaps(false);
            btn.setTextSize(13f);
            btn.setTypeface(btn.getTypeface(), android.graphics.Typeface.BOLD);
            btn.setBackgroundResource(R.drawable.bg_btn_inactive_white);
            btn.setBackgroundTintList(null);
            btn.setStateListAnimator(null);
            btn.setOnClickListener(vw -> selectZoneChip(idx));
            zoneButtons.add(btn);
            llZoneContainer.addView(btn);
        }
        updateZoneChipUI();
    }

    private void selectZoneChip(int index) {
        if (index < 0 || index >= zones.size()) return;
        selectedZoneChip = index;
        updateZoneChipUI();
        saveFormState();
    }

    private void updateZoneChipUI() {
        for (int i = 0; i < zoneButtons.size(); i++) {
            AppCompatButton btn = zoneButtons.get(i);
            boolean active = (i == selectedZoneChip);
            btn.setBackgroundResource(active
                    ? R.drawable.bg_btn_active_primary
                    : R.drawable.bg_btn_inactive_white);
            btn.setBackgroundTintList(null);
            if (isAdded()) btn.setTextColor(ContextCompat.getColor(requireContext(),
                    active ? R.color.white : R.color.foreground));
        }
        updateDurationAndPayLabels();
    }

    // -- Duration --

    private void selectDuration(String type, int index) {
        selectedDuration = type;
        updateDurationChipStyles(index);
        updateDurationAndPayLabels();
    }

    private void updateDurationChipStyles(int activeIndex) {
        AppCompatButton[] chips = {btnDuration1, btnDuration2, btnDailyPass};
        for (int i = 0; i < chips.length; i++) {
            if (chips[i] == null) continue;
            boolean active = (i == activeIndex);
            chips[i].setBackgroundResource(active
                    ? R.drawable.bg_btn_active_primary
                    : R.drawable.bg_btn_inactive_white);
            chips[i].setBackgroundTintList(null);
            if (isAdded()) chips[i].setTextColor(ContextCompat.getColor(requireContext(),
                    active ? R.color.white : R.color.foreground));
        }
    }

    private void updateDurationAndPayLabels() {
        int idx = getSelectedZoneIndex();
        ZoneItem zone = (idx >= 0 && idx < zones.size()) ? zones.get(idx) : null;
        double perHour = zone != null ? zone.perHour : 0.0;
        double perDay  = zone != null && zone.perDay > 0 ? zone.perDay : perHour * 8.0;

        if (btnDuration1 != null) btnDuration1.setText("1h\n" + fmtAmount(perHour) + " KM");
        if (btnDuration2 != null) btnDuration2.setText("2h\n" + fmtAmount(perHour * 2) + " KM");
        if (btnDailyPass != null) btnDailyPass.setText("24h\n" + fmtAmount(perDay) + " KM");

        double payAmount = zone != null ? calcAmount(selectedDuration, zone) : 0.0;
        if (btnPay != null) btnPay.setText("Plati " + fmtAmount(payAmount) + " KM");
    }

    private String fmtAmount(double v) { return String.format(Locale.getDefault(), "%.2f", v); }

    private int getSelectedZoneIndex() {
        if (!zones.isEmpty() && selectedZoneChip >= 0 && selectedZoneChip < zones.size())
            return selectedZoneChip;
        return 0;
    }

    private void setSelectedZoneIndex(int idx) {
        if (idx < 0 || idx >= zones.size()) return;
        selectedZoneChip = idx;
        updateZoneChipUI();
    }

    private void applySavedZone() {
        SharedPreferences p = prefs();
        if (p == null) return;
        String savedId = p.getString(K_LOT_ID, "");
        int target = -1;
        if (!TextUtils.isEmpty(savedId))
            for (int i = 0; i < zones.size(); i++)
                if (savedId.equals(zones.get(i).id)) { target = i; break; }
        if (target < 0) {
            int savedPos = p.getInt(K_LOT_POS, -1);
            if (savedPos >= 0 && savedPos < zones.size()) target = savedPos;
        }
        if (target >= 0) setSelectedZoneIndex(target);
        else if (!zones.isEmpty()) setSelectedZoneIndex(0);
    }

    @Nullable
    private SharedPreferences prefs() {
        Context ctx = getContext();
        return ctx == null ? null : ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void saveFormState() {
        SharedPreferences p = prefs();
        if (p == null) return;
        int idx       = getSelectedZoneIndex();
        String zoneId = (idx >= 0 && idx < zones.size()) ? zones.get(idx).id : "";
        p.edit()
                .putString(K_UID,      FirebaseAuth.getInstance().getUid())
                .putString(K_LOT_ID,   zoneId)
                .putInt(K_LOT_POS,     idx)
                .putInt(K_VEHICLE_POS, currentVehicleIndex)
                .apply();
    }

    private void restoreFormState() {
        if (restoredOnce) return;
        SharedPreferences p = prefs();
        if (p == null) { restoredOnce = true; return; }
        String uid = FirebaseAuth.getInstance().getUid();
        if (!TextUtils.equals(uid, p.getString(K_UID, ""))) { restoredOnce = true; return; }
        int savedVehiclePos = p.getInt(K_VEHICLE_POS, -1);
        if (savedVehiclePos >= 0 && savedVehiclePos < vehicleAdapter.getItemCount())
            currentVehicleIndex = savedVehiclePos;
        restoredOnce = true;
    }

    // -- Firebase load --

    private void loadZones() {
        FirebaseUtils.zonesRef().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                zones.clear();
                for (DataSnapshot z : ds.getChildren()) {
                    String id   = z.getKey();
                    String name = z.child("name").getValue(String.class);
                    Double ph   = z.child("perHour").getValue(Double.class);
                    Double pd   = z.child("perDay").getValue(Double.class);
                    if (id == null || name == null) continue;
                    ZoneItem zi = new ZoneItem();
                    zi.id = id; zi.name = name;
                    zi.perHour = ph == null ? 0.0 : ph;
                    zi.perDay  = pd == null ? 0.0 : pd;
                    zones.add(zi);
                }
                buildZoneButtons();
                applySavedZone();
                restoreFormState();
                loadAllActiveSessions();
            }
            @Override public void onCancelled(@NonNull DatabaseError e) { toast(e.getMessage()); }
        });
    }

    private void loadUserVehicles() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        FirebaseUtils.userVehicles(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                List<VehicleDisplay> temp = new ArrayList<>();
                for (DataSnapshot veh : ds.getChildren()) {
                    String nick  = veh.child("nickname").getValue(String.class);
                    String plate = veh.child("plate").getValue(String.class);
                    if (TextUtils.isEmpty(plate)) continue;
                    String pl = plate.toUpperCase(Locale.ROOT).trim();
                    String n  = TextUtils.isEmpty(nick) ? "" : nick.trim();
                    temp.add(new VehicleDisplay(TextUtils.isEmpty(n) ? pl : n, pl));
                }
                Collections.sort(temp, Comparator.comparing(a -> a.display.toLowerCase(Locale.ROOT)));

                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    vehicles.clear();
                    vehicles.addAll(temp);
                    if (currentVehicleIndex >= vehicleAdapter.getItemCount()) currentVehicleIndex = 0;
                    vehicleAdapter.notifyDataSetChanged();
                    vpVehicles.setCurrentItem(currentVehicleIndex, false);
                    updateVehicleDots();
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    private void loadAllActiveSessions() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        fetchServerNow(serverNow -> {
            Query q = FirebaseUtils.sessionsRef()
                    .orderByChild("userId").equalTo(uid).limitToLast(50);
            q.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot ds) {
                    activeSessionsMap.clear();

                    for (DataSnapshot s : ds.getChildren()) {
                        String status = safeStr(s.child("status").getValue(String.class)).toUpperCase(Locale.ROOT);
                        if (TextUtils.isEmpty(status)) status = "ACTIVE";
                        if (!"ACTIVE".equals(status)) continue;

                        Long endL   = s.child("endTime").getValue(Long.class);
                        Long startL = s.child("startTime").getValue(Long.class);
                        Double amt  = s.child("amount").getValue(Double.class);
                        String zId  = s.child("zoneId").getValue(String.class);
                        String pl   = s.child("plate").getValue(String.class);

                        if (pl == null || endL == null) continue;
                        long end = endL;
                        if (end <= serverNow + DRIFT_MS) continue;

                        String plateKey = pl.toUpperCase(Locale.ROOT).trim();
                        ActiveSession existing = activeSessionsMap.get(plateKey);
                        if (existing != null && existing.endTime >= end) continue;

                        ActiveSession as = new ActiveSession();
                        as.sessionId = s.getKey();
                        as.plate     = plateKey;
                        as.startTime = startL == null ? 0L : startL;
                        as.endTime   = end;
                        as.amount    = amt == null ? 0.0 : amt;
                        as.zoneName  = "—";
                        for (ZoneItem zi : zones)
                            if (zi.id != null && zi.id.equals(zId)) { as.zoneName = zi.name; break; }

                        activeSessionsMap.put(plateKey, as);
                    }

                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        renderActiveSessions();
                        startTicker();
                    });
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
        });
    }

    @Override public void onResume() {
        super.onResume();
        restoreFormState();
        loadAllActiveSessions();
    }

    @Override public void onPause() {
        super.onPause();
        stopTicker();
    }

    @Override public void onDestroyView() {
        stopTicker();
        super.onDestroyView();
    }

    // -- Confirm pay / extend --

    private void showConfirmDialog(@NonNull String type) {
        if (!isAdded()) return;

        int idx       = getSelectedZoneIndex();
        ZoneItem zone = (idx >= 0 && idx < zones.size()) ? zones.get(idx) : null;
        String  plate = resolvePlate();
        double amount = zone != null ? calcAmount(type, zone) : 0.0;

        if (zone != null) {
            String plateKey = plate.toUpperCase(Locale.ROOT).trim();
            ActiveSession existing = activeSessionsMap.get(plateKey);
            if (existing != null) {
                showExtendDialog(existing, zone, type, amount);
                return;
            }
        }

        String vehicleLabel = plate;
        int vehIdx = currentVehicleIndex;
        if (vehIdx >= 0 && vehIdx < vehicles.size()) {
            String disp = vehicles.get(vehIdx).display;
            if (!TextUtils.isEmpty(disp)) vehicleLabel = disp + " · " + plate;
        }

        BottomSheetDialog dlg = new BottomSheetDialog(requireContext());
        View v = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_confirm_payment, null, false);
        dlg.setContentView(v);

        TextView tvTitle       = v.findViewById(R.id.tvConfirmTitle);
        TextView tvSubtitle    = v.findViewById(R.id.tvConfirmSubtitle);
        TextView tvAmount      = v.findViewById(R.id.tvConfirmAmount);
        TextView tvBreakdown   = v.findViewById(R.id.tvConfirmAmountBreakdown);
        TextView tvVehicle     = v.findViewById(R.id.tvConfirmVehicle);
        TextView tvZone        = v.findViewById(R.id.tvConfirmZone);
        TextView tvType        = v.findViewById(R.id.tvConfirmType);
        TextView tvSavings     = v.findViewById(R.id.tvSavings);
        TextView tvBalanceInfo = v.findViewById(R.id.tvBalanceInfo);
        View     savingsBox    = v.findViewById(R.id.savingsBox);
        View     insufficient  = v.findViewById(R.id.insufficientBox);
        android.widget.Button btnConfirm = v.findViewById(R.id.btnConfirm);
        android.widget.Button btnCancel  = v.findViewById(R.id.btnCancel);

        boolean isDayPass = "day".equals(type);
        tvTitle.setText(isDayPass ? "Dnevna karta" : "Potvrda plaćanja");
        tvSubtitle.setText(isDayPass ? "Neograničeno parkiranje 24h" : "Provjerite podatke");

        tvAmount.setText(String.format(Locale.getDefault(), "%.2f KM", amount));
        if (zone != null) {
            if (isDayPass) {
                tvBreakdown.setText(String.format(Locale.getDefault(),
                        "%.2f KM · dnevno neograničeno", amount));
            } else {
                int hours = type.equals("1h") ? 1 : 2;
                tvBreakdown.setText(String.format(Locale.getDefault(),
                        "%d %s × %.2f KM/sat", hours, hourWord(hours), zone.perHour));
            }
        }

        tvVehicle.setText(TextUtils.isEmpty(vehicleLabel) ? "—" : vehicleLabel);
        tvZone.setText(zone != null ? zone.name : "—");
        tvType.setText(typeLabel(type));

        if (isDayPass && zone != null && zone.perHour > 0) {
            double normal8h = zone.perHour * 8.0;
            double savings  = normal8h - amount;
            if (savings > 0) {
                savingsBox.setVisibility(View.VISIBLE);
                double percent = (savings / normal8h) * 100.0;
                tvSavings.setText(String.format(Locale.getDefault(),
                        "%.2f KM (%.0f%% jeftinije od 8h)", savings, percent));
            }
        }

        final double finalAmount = amount;
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            FirebaseUtils.balance(uid).get().addOnSuccessListener(snap -> {
                if (!isAdded()) return;
                Double b = snap.exists() ? snap.getValue(Double.class) : null;
                double balance = b == null ? 0.0 : b;
                cachedBalance = balance;
                tvBalanceInfo.setText(String.format(Locale.getDefault(), "%.2f KM", balance));
                boolean sufficient = balance >= finalAmount;
                insufficient.setVisibility(sufficient ? View.GONE : View.VISIBLE);
                btnConfirm.setEnabled(true);
                btnConfirm.setAlpha(1f);
                btnConfirm.setText(sufficient ? "Plati" : "Dopuni kredit");
            });
        }

        btnConfirm.setOnClickListener(x -> {
            dlg.dismiss();
            if (cachedBalance < finalAmount) showTopupDialog(finalAmount);
            else pay(type);
        });
        btnCancel.setOnClickListener(x -> dlg.dismiss());

        dlg.show();
    }

    private static String hourWord(int hours) {
        if (hours == 1) return "sat";
        if (hours >= 2 && hours <= 4) return "sata";
        return "sati";
    }

    // -- Top-up dialog --

    private void showTopupDialog(double needForAmount) {
        if (!isAdded()) return;
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) { openAddCardScreen(); return; }

        FirebaseUtils.user(uid).child("paymentCard").get().addOnSuccessListener(snap -> {
            if (!isAdded()) return;
            boolean hasCard = snap.exists() && snap.child("number").exists();
            if (!hasCard) { openAddCardScreen(); return; }
            String cardNumber = snap.child("number").getValue(String.class);
            showTopupAmountDialog(needForAmount, cardNumber);
        }).addOnFailureListener(e -> { if (isAdded()) openAddCardScreen(); });
    }

    private void showTopupAmountDialog(double needForAmount, @Nullable String cardNumber) {
        if (!isAdded()) return;

        BottomSheetDialog dlg = new BottomSheetDialog(requireContext());
        View v = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_topup_quick, null, false);
        dlg.setContentView(v);

        TextView tvSubtitle    = v.findViewById(R.id.tvTopupSubtitle);
        TextView tvBal         = v.findViewById(R.id.tvTopupBalance);
        TextView tvCardMasked  = v.findViewById(R.id.tvCardMasked);
        View llCardInfo        = v.findViewById(R.id.llCardInfo);
        View llNoCardWarning   = v.findViewById(R.id.llNoCardWarning);

        AppCompatButton btn5   = v.findViewById(R.id.btnTopup5);
        AppCompatButton btn10  = v.findViewById(R.id.btnTopup10);
        AppCompatButton btn20  = v.findViewById(R.id.btnTopup20);
        AppCompatButton btn50  = v.findViewById(R.id.btnTopup50);
        AppCompatButton btnPrimary = v.findViewById(R.id.btnTopupPrimary);
        AppCompatButton btnCancel  = v.findViewById(R.id.btnTopupCancel);

        tvBal.setText(String.format(Locale.getDefault(), "%.2f KM", cachedBalance));

        llCardInfo.setVisibility(View.VISIBLE);
        llNoCardWarning.setVisibility(View.GONE);
        if (cardNumber != null && cardNumber.length() >= 4)
            tvCardMasked.setText("•••• " + cardNumber.substring(cardNumber.length() - 4));
        else tvCardMasked.setText("•••• ••••");
        btnPrimary.setText("Dopuni sa kartice");

        double[] presets = {5, 10, 20, 50};
        AppCompatButton[] btns = {btn5, btn10, btn20, btn50};
        final double[] selected = {10.0};

        if (needForAmount > 0) {
            double missing = needForAmount - cachedBalance;
            if (missing < 0) missing = 0;
            selected[0] = 10.0;
            for (double p : presets) {
                if (p >= missing) { selected[0] = p; break; }
                if (p == presets[presets.length - 1]) selected[0] = p;
            }
            tvSubtitle.setText(String.format(Locale.getDefault(),
                    "Potrebno vam je %.2f KM više", missing));
        }

        Runnable syncPresets = () -> {
            for (int i = 0; i < presets.length; i++) {
                boolean active = presets[i] == selected[0];
                btns[i].setBackgroundResource(active
                        ? R.drawable.bg_btn_active_primary
                        : R.drawable.bg_btn_inactive_white);
                btns[i].setTextColor(ContextCompat.getColor(requireContext(),
                        active ? R.color.white : R.color.foreground));
            }
        };
        syncPresets.run();

        for (int i = 0; i < presets.length; i++) {
            final double val = presets[i];
            btns[i].setOnClickListener(x -> { selected[0] = val; syncPresets.run(); });
        }

        btnPrimary.setOnClickListener(x -> {
            dlg.dismiss();
            showConfirmTopupDialog(selected[0], cardNumber);
        });
        btnCancel.setOnClickListener(x -> dlg.dismiss());

        dlg.show();
    }

    private void showConfirmTopupDialog(double amount, @Nullable String cardNumber) {
        if (!isAdded()) return;

        BottomSheetDialog dlg = new BottomSheetDialog(requireContext());
        View v = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_confirm_topup, null, false);
        dlg.setContentView(v);

        TextView tvAmount     = v.findViewById(R.id.tvConfirmTopupAmount);
        TextView tvCard       = v.findViewById(R.id.tvConfirmTopupCard);
        TextView tvBalance    = v.findViewById(R.id.tvConfirmTopupBalance);
        TextView tvNewBalance = v.findViewById(R.id.tvConfirmTopupNewBalance);

        AppCompatButton btnYes = v.findViewById(R.id.btnConfirmTopupYes);
        AppCompatButton btnNo  = v.findViewById(R.id.btnConfirmTopupNo);

        tvAmount.setText(String.format(Locale.getDefault(), "%.2f KM", amount));
        tvBalance.setText(String.format(Locale.getDefault(), "%.2f KM", cachedBalance));
        tvNewBalance.setText(String.format(Locale.getDefault(), "%.2f KM", cachedBalance + amount));

        if (cardNumber != null && cardNumber.length() >= 4)
            tvCard.setText("•••• " + cardNumber.substring(cardNumber.length() - 4));
        else tvCard.setText("•••• ••••");

        btnYes.setOnClickListener(x -> { dlg.dismiss(); executeTopup(amount); });
        btnNo.setOnClickListener(x -> dlg.dismiss());

        dlg.show();
    }

    private void executeTopup(double amount) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) { toast("Niste prijavljeni."); return; }

        FirebaseUtils.balance(uid).runTransaction(new Transaction.Handler() {
            @NonNull @Override
            public Transaction.Result doTransaction(@NonNull MutableData cur) {
                Double bal = cur.getValue(Double.class);
                if (bal == null) bal = 0.0;
                cur.setValue(bal + amount);
                return Transaction.success(cur);
            }
            @Override public void onComplete(@Nullable DatabaseError error, boolean committed,
                                             @Nullable DataSnapshot snapshot) {
                if (error != null) { toast("Greška: " + error.getMessage()); return; }
                if (committed) toast(String.format(Locale.getDefault(),
                        "Uspješno dopunjeno %.2f KM ✓", amount));
            }
        });
    }

    private void openAddCardScreen() {
        if (!isAdded()) return;
        try {
            Intent i = new Intent(requireContext(),
                    com.example.smartparking.ui.user.userSettings.TopUpActivity.class);
            startActivity(i);
        } catch (Exception e) {
            toast("Ne mogu otvoriti TopUp ekran: " + e.getMessage());
        }
    }

    // -- Extend session: time is ADDED to the existing end time --

    private void showExtendDialog(@NonNull ActiveSession existing, @NonNull ZoneItem zone,
                                  @NonNull String type, double amount) {
        if (!isAdded()) return;

        BottomSheetDialog dlg = new BottomSheetDialog(requireContext());
        View v = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_confirm_extend_session, null, false);
        dlg.setContentView(v);

        TextView tvSubtitle   = v.findViewById(R.id.tvExtendSubtitle);
        TextView tvAmount     = v.findViewById(R.id.tvExtendAmount);
        TextView tvPlate      = v.findViewById(R.id.tvExtendPlate);
        TextView tvDuration   = v.findViewById(R.id.tvExtendDuration);
        TextView tvCurrentEnd = v.findViewById(R.id.tvExtendCurrentEnd);
        TextView tvNewEnd     = v.findViewById(R.id.tvExtendNewEnd);
        TextView tvBalance    = v.findViewById(R.id.tvExtendBalance);
        View insufficientBox  = v.findViewById(R.id.extendInsufficientBox);
        AppCompatButton btnConfirm = v.findViewById(R.id.btnConfirmExtend);
        AppCompatButton btnCancel  = v.findViewById(R.id.btnCancelExtend);

        long extendMs = durationMs(type);
        long newEnd   = existing.endTime + extendMs; // added onto the existing end time

        tvSubtitle.setText("Vrijeme će biti dodano postojećoj sesiji");
        tvAmount.setText(String.format(Locale.getDefault(), "%.2f KM", amount));
        tvPlate.setText(existing.plate);
        tvDuration.setText(typeLabel(type));

        java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault());
        tvCurrentEnd.setText(sdf.format(new java.util.Date(existing.endTime)));
        tvNewEnd.setText(sdf.format(new java.util.Date(newEnd)));

        final double finalAmount = amount;
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            FirebaseUtils.balance(uid).get().addOnSuccessListener(snap -> {
                if (!isAdded()) return;
                Double b = snap.exists() ? snap.getValue(Double.class) : null;
                double balance = b == null ? 0.0 : b;
                cachedBalance = balance;
                tvBalance.setText(String.format(Locale.getDefault(), "%.2f KM", balance));
                boolean sufficient = balance >= finalAmount;
                insufficientBox.setVisibility(sufficient ? View.GONE : View.VISIBLE);
                btnConfirm.setEnabled(true);
                btnConfirm.setAlpha(1f);
                btnConfirm.setText(sufficient ? "⚡ Produži sesiju" : "Dopuni kredit");
            });
        }

        btnConfirm.setOnClickListener(x -> {
            dlg.dismiss();
            if (cachedBalance < finalAmount) showTopupDialog(finalAmount);
            else executeExtend(existing, zone, type, finalAmount, extendMs);
        });
        btnCancel.setOnClickListener(x -> dlg.dismiss());

        dlg.show();
    }

    private void executeExtend(@NonNull ActiveSession existing, @NonNull ZoneItem zone,
                               @NonNull String type, double amount, long extendMs) {
        if (isPaying) return;
        isPaying = true;
        setPayEnabled(false);

        final FirebaseUser fu = FirebaseAuth.getInstance().getCurrentUser();
        if (fu == null) { failUnlock("Niste prijavljeni."); return; }
        final String uid = fu.getUid();
        final long newEnd = existing.endTime + extendMs; // accumulates past 24h if extended repeatedly

        FirebaseUtils.balance(uid).runTransaction(new Transaction.Handler() {
            @NonNull @Override
            public Transaction.Result doTransaction(@NonNull MutableData cur) {
                Double bal = cur.getValue(Double.class);
                if (bal == null) bal = 0.0;
                if (bal < amount) return Transaction.abort();
                cur.setValue(bal - amount);
                return Transaction.success(cur);
            }
            @Override public void onComplete(@Nullable DatabaseError error, boolean committed,
                                             @Nullable DataSnapshot snapshot) {
                if (error != null) { failUnlock("Greška naplate: " + error.getMessage()); return; }
                if (!committed)    { failUnlock("Nedovoljan balans."); return; }

                Map<String, Object> upd = new HashMap<>();
                upd.put("endTime", newEnd);
                upd.put("amount",  existing.amount + amount);

                FirebaseUtils.session(existing.sessionId).updateChildren(upd)
                        .addOnSuccessListener(v2 -> {
                            existing.endTime = newEnd;
                            existing.amount += amount;
                            activeSessionsMap.put(existing.plate, existing);
                            isPaying = false;
                            setPayEnabled(true);
                            toast("Sesija produžena ✓");
                            if (isAdded()) requireActivity().runOnUiThread(() -> {
                                renderActiveSessions();
                                startTicker();
                            });
                        })
                        .addOnFailureListener(e -> failUnlock("Greška produženja: " + e.getMessage()));
            }
        });
    }

    // -- Quick add vehicle --

    private void showQuickAddVehicleDialog() {
        if (!isAdded()) return;

        BottomSheetDialog dlg = new BottomSheetDialog(requireContext());
        View v = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_vehicle_quick, null, false);
        dlg.setContentView(v);

        EditText etPlate    = v.findViewById(R.id.etQuickPlate);
        EditText etNickname = v.findViewById(R.id.etQuickNickname);
        AppCompatButton btnSave   = v.findViewById(R.id.btnQuickSave);
        AppCompatButton btnCancel = v.findViewById(R.id.btnQuickCancel);

        btnSave.setOnClickListener(x -> {
            String plate = etPlate.getText() == null ? "" : etPlate.getText().toString().trim().toUpperCase(Locale.ROOT);
            String nick  = etNickname.getText() == null ? "" : etNickname.getText().toString().trim();

            if (TextUtils.isEmpty(plate)) { toast("Registarske oznake su obavezne."); return; }

            String uid = FirebaseAuth.getInstance().getUid();
            if (uid == null) { toast("Niste prijavljeni."); return; }

            for (VehicleDisplay vd : vehicles)
                if (vd.plate.equalsIgnoreCase(plate)) { toast("Vozilo sa ovim tablicama već postoji."); return; }

            String vehId = FirebaseUtils.userVehicles(uid).push().getKey();
            if (vehId == null) { toast("Greška generisanja ID-a."); return; }

            Map<String, Object> data = new HashMap<>();
            data.put("plate", plate);
            if (!TextUtils.isEmpty(nick)) data.put("nickname", nick);

            FirebaseUtils.userVehicles(uid).child(vehId).setValue(data)
                    .addOnSuccessListener(r -> {
                        toast("Vozilo sačuvano ✓");
                        dlg.dismiss();
                        currentVehicleIndex = 0;
                        loadUserVehicles();
                    })
                    .addOnFailureListener(e -> toast("Greška: " + e.getMessage()));
        });

        btnCancel.setOnClickListener(x -> dlg.dismiss());
        dlg.show();
    }

    // -- Pay --

    private void pay(@NonNull String type) {
        if (isPaying) return;
        isPaying = true;
        setPayEnabled(false);

        int idx = getSelectedZoneIndex();
        if (idx < 0 || idx >= zones.size()) { failUnlock("Odaberite zonu."); return; }
        final ZoneItem zone = zones.get(idx);

        final String plate = resolvePlate();
        if (TextUtils.isEmpty(plate)) { failUnlock("Odaberite ili dodajte vozilo."); return; }

        final FirebaseUser fu = FirebaseAuth.getInstance().getCurrentUser();
        if (fu == null) { failUnlock("Niste prijavljeni."); return; }
        final String uid = fu.getUid();

        final double amount = calcAmount(type, zone);
        if (amount <= 0) { failUnlock("Cijena nije podešena za ovu zonu."); return; }

        final String sessionId = FirebaseUtils.sessionsRef().push().getKey();
        if (sessionId == null) { failUnlock("Greška generisanja sesije."); return; }

        String plateKey = plate.toUpperCase(Locale.ROOT).trim();
        ActiveSession existing = activeSessionsMap.get(plateKey);
        if (existing != null) {
            isPaying = false;
            setPayEnabled(true);
            showExtendDialog(existing, zone, type, amount);
            return;
        }

        executePay(uid, zone, plate, amount, sessionId, type);
    }

    private void executePay(String uid, ZoneItem zone, String plate,
                            double amount, String sessionId, String type) {
        fetchServerNow(serverNow -> {
            final long startTime = serverNow;
            final long endTime   = serverNow + durationMs(type);

            final String plateNorm = normalizePlateStd(plate);

            // Atomic balance deduction — abort if insufficient funds
            FirebaseUtils.balance(uid).runTransaction(new Transaction.Handler() {
                @NonNull @Override
                public Transaction.Result doTransaction(@NonNull MutableData cur) {
                    Double bal = cur.getValue(Double.class);
                    if (bal == null) bal = 0.0;
                    if (bal < amount) return Transaction.abort();
                    cur.setValue(bal - amount);
                    return Transaction.success(cur);
                }
                @Override public void onComplete(@Nullable DatabaseError error, boolean committed,
                                                 @Nullable DataSnapshot snapshot) {
                    if (error != null) { failUnlock("Greška naplate: " + error.getMessage()); return; }

                    // Insufficient balance — offer top-up
                    if (!committed) {
                        isPaying = false;
                        setPayEnabled(true);
                        if (isAdded()) requireActivity().runOnUiThread(() -> showTopupDialog(amount));
                        return;
                    }

                    // Create parking session
                    Map<String, Object> sess = new HashMap<>();
                    sess.put("userId",          uid);
                    sess.put("zoneId",          zone.id);
                    sess.put("startTime",       startTime);
                    sess.put("endTime",         endTime);
                    sess.put("amount",          amount);
                    sess.put("type",            type);
                    sess.put("plate",           plateNorm);
                    sess.put("plateNormalized", plateNorm);
                    sess.put("status",          "ACTIVE");

                    FirebaseUtils.session(sessionId).setValue(sess)
                            .addOnSuccessListener(v2 -> {
                                String plateKey = plateNorm;
                                ActiveSession as = new ActiveSession();
                                as.sessionId = sessionId;
                                as.plate     = plateKey;
                                as.startTime = startTime;
                                as.endTime   = endTime;
                                as.amount    = amount;
                                as.zoneName  = zone.name;
                                activeSessionsMap.put(plateKey, as);

                                saveFormState();
                                isPaying = false;
                                setPayEnabled(true);
                                if (isAdded()) requireActivity().runOnUiThread(() -> {
                                    renderActiveSessions();
                                    startTicker();
                                });
                            })
                            .addOnFailureListener(e -> failUnlock("Greška upisa sesije."));
                }
            });
        });
    }

    // -- Ticker --

    private void startTicker() {
        if (!isAdded() || vpActiveSessions == null) return;
        stopTicker();
        statusTick = new Runnable() {
            @Override public void run() {
                if (!isAdded()) return;
                updateAllActiveSessionsUI();
                if (vpActiveSessions != null) {
                    vpActiveSessions.removeCallbacks(this);
                    vpActiveSessions.postDelayed(this, TICK_MS);
                }
            }
        };
        vpActiveSessions.post(statusTick);
    }

    private void stopTicker() {
        if (vpActiveSessions != null && statusTick != null)
            vpActiveSessions.removeCallbacks(statusTick);
        statusTick = null;
    }

    // -- Helpers --

    String formatRemain(long ms) {
        if (ms <= 0) return "";
        long h   = ms / 3600000;
        long min = (ms % 3600000) / 60000;
        long sec = (ms / 1000) % 60;
        return h > 0
                ? String.format(Locale.getDefault(), "%d:%02d:%02d", h, min, sec)
                : String.format(Locale.getDefault(), "%d:%02d", min, sec);
    }

    private boolean validateInputs() {
        int idx = getSelectedZoneIndex();
        if (idx < 0 || idx >= zones.size()) { toast("Odaberite zonu."); return false; }
        String plate = resolvePlate();
        if (TextUtils.isEmpty(plate)) {
            toast("Prvo dodajte vozilo.");
            showQuickAddVehicleDialog();
            return false;
        }
        return true;
    }

    private String resolvePlate() {
        if (vehicleAdapter == null) return "";
        int type = getItemViewTypeAt(currentVehicleIndex);
        if (type == TYPE_VEHICLE)
            if (currentVehicleIndex >= 0 && currentVehicleIndex < vehicles.size())
                return vehicles.get(currentVehicleIndex).plate.toUpperCase(Locale.ROOT).trim();
        return "";
    }

    private int getItemViewTypeAt(int pos) {
        if (pos == vehicles.size()) return TYPE_ADD;
        return TYPE_VEHICLE;
    }

    private double calcAmount(@NonNull String type, @NonNull ZoneItem zone) {
        switch (type) {
            case "1h": return zone.perHour;
            case "2h": return zone.perHour * 2.0;
            default:   return zone.perDay > 0 ? zone.perDay : zone.perHour * 8.0;
        }
    }

    private long durationMs(@NonNull String type) {
        switch (type) {
            case "1h": return ONE_HOUR_MS;
            case "2h": return 2 * ONE_HOUR_MS;
            default:   return ONE_DAY_MS;
        }
    }

    private String typeLabel(@NonNull String type) {
        switch (type) {
            case "1h": return "1 sat";
            case "2h": return "2 sata";
            default:   return "Dnevna karta";
        }
    }

    private void setPayEnabled(boolean en) {
        if (btnPay       != null) btnPay.setEnabled(en);
        if (btnDailyPass != null) btnDailyPass.setEnabled(en);
    }

    private void failUnlock(String msg) { toast(msg); isPaying = false; setPayEnabled(true); }

    private void fetchServerNow(@NonNull Consumer<Long> cb) {
        FirebaseUtils.infoServerTimeOffset().get()
                .addOnSuccessListener(s -> {
                    Long off = s.exists() ? s.getValue(Long.class) : null;
                    cb.accept(System.currentTimeMillis() + (off == null ? 0L : off));
                })
                .addOnFailureListener(e -> cb.accept(System.currentTimeMillis()));
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
    }

    private static String safeStr(String s) { return s == null ? "" : s.trim(); }

    private static String normalizePlateStd(String s) {
        if (s == null) return "";
        String up = s.toUpperCase(Locale.ROOT).replace("Đ", "D");
        up = Normalizer.normalize(up, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return up.replaceAll("[^A-Z0-9]", "");
    }

    static class ZoneItem { String id, name; double perHour, perDay; }

    static class VehicleDisplay {
        String display, plate;
        VehicleDisplay(String d, String p) { display = d; plate = p; }
    }

    // -- Adapter: active sessions (ViewPager) --
    class SessionSliderAdapter extends RecyclerView.Adapter<SessionSliderAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.row_active_session, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            ActiveSession s = sessionList.get(position);
            h.tvPlate.setText(s.plate);
            h.tvZone.setText((s.zoneName == null ? "—" : s.zoneName) + " · aktivno");

            long now = System.currentTimeMillis();
            long remain = s.endTime - now;
            long total  = s.endTime - s.startTime;
            if (remain > 0 && total > 0) {
                h.tvCountdown.setText(formatRemain(remain));
                int percent = (int) Math.max(0, Math.min(100, (remain * 100L) / total));
                h.pb.setProgress(percent);
            } else {
                h.tvCountdown.setText("0:00");
                h.pb.setProgress(0);
            }
        }
        @Override public int getItemCount() { return sessionList.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvPlate, tvZone, tvCountdown;
            ProgressBar pb;
            VH(View v) {
                super(v);
                tvPlate     = v.findViewById(R.id.tvSessionPlate);
                tvZone      = v.findViewById(R.id.tvSessionZone);
                tvCountdown = v.findViewById(R.id.tvSessionCountdown);
                pb          = v.findViewById(R.id.progressSessionTimer);
            }
        }
    }

    // -- Adapter: vehicles --
    class VehicleSliderAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemViewType(int position) { return getItemViewTypeAt(position); }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_ADD)
                return new AddVH(inf.inflate(R.layout.row_vehicle_add, parent, false));
            return new VehicleVH(inf.inflate(R.layout.row_vehicle_slider, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            int type = getItemViewType(position);
            if (type == TYPE_VEHICLE) {
                VehicleDisplay vd = vehicles.get(position);
                VehicleVH vh = (VehicleVH) h;
                vh.tvPlate.setText(vd.plate);
                vh.tvModel.setText(TextUtils.isEmpty(vd.display) ? vd.plate : vd.display);
            } else if (type == TYPE_ADD) {
                AddVH ah = (AddVH) h;
                ah.itemView.setOnClickListener(v -> showQuickAddVehicleDialog());
            }
        }

        @Override public int getItemCount() { return vehicles.size() + 1; }

        class VehicleVH extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvPlate, tvModel;
            VehicleVH(View v) {
                super(v);
                ivIcon  = v.findViewById(R.id.ivVehicleIcon);
                tvPlate = v.findViewById(R.id.tvVehiclePlate);
                tvModel = v.findViewById(R.id.tvVehicleModel);
            }
        }

        class AddVH extends RecyclerView.ViewHolder {
            AddVH(View v) { super(v); }
        }
    }
}