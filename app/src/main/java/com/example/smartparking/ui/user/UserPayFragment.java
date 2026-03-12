package com.example.smartparking.ui.user;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class UserPayFragment extends Fragment {

    private static final String TAG = "UserPayFragment";
    private static final Pattern INVALID_KEY_CHARS = Pattern.compile("[.$#\\[\\]/]");

    private static final long ONE_HOUR_MS = 60L * 60L * 1000L;
    private static final long ONE_DAY_MS  = 24L * 60L * 60L * 1000L;

    private static final long TICK_MS  = 1000L;
    private static final long DRIFT_MS = 8000L;

    private static final double MIN_REFUND_KM = 0.05;
    private static final int MAX_TAKEOVER_RETRY = 1;

    // ---- prefs ----
    private static final String PREFS_NAME = "sp_user_pay";
    private static final String K_UID = "uid";
    private static final String K_LOT_ID = "lot_id";
    private static final String K_LOT_POS = "lot_pos";
    private static final String K_SPOT = "spot";
    private static final String K_PLATE = "plate";
    private static final String K_HAS_ACTIVE = "has_active";

    // UI
    private TextView tvBalance, tvStatus;
    private View cardStatus;

    // ✅ Parking dropdown (umjesto Spinner-a)
    private TextInputLayout tilParking;
    private MaterialAutoCompleteTextView etParking;

    private EditText etSpot;

    private TextInputLayout tilPlate;
    private AutoCompleteTextView etPlate;

    private Button btnPay1h, btnPay2h, btnPay3h, btnPayDay;
    private Button btnRefund;

    // lots
    private final List<LotItem> lots = new ArrayList<>();
    private ArrayAdapter<String> lotsAdapter;

    // vehicles dropdown
    private final List<String> displaySuggestions = new ArrayList<>();
    private final Map<String, String> displayToPlate = new HashMap<>();
    private ArrayAdapter<String> plateAdapter;

    // status ticker
    private Runnable statusTick;

    // active session info
    private String currentSessionId = null;
    private String currentLotId = null;
    private String currentLotName = null;
    private String currentSpaceKey = null;
    private String currentPlate = null;
    private long currentStartTime = 0L;
    private long currentEndTime = 0L;
    private double currentAmount = 0.0;

    private boolean isPaying = false;
    private boolean isRefunding = false;

    private boolean restoredOnce = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_user_pay, parent, false);

        tvBalance  = v.findViewById(R.id.tvBalance);
        tvStatus   = v.findViewById(R.id.tvStatus);
        cardStatus = v.findViewById(R.id.cardStatus);

        // ✅ parking dropdown
        tilParking = v.findViewById(R.id.tilParking);
        etParking  = v.findViewById(R.id.etParking);

        etSpot = v.findViewById(R.id.etSpot);

        tilPlate = v.findViewById(R.id.tilPlate);
        etPlate  = v.findViewById(R.id.etPlate);

        btnPay1h  = v.findViewById(R.id.btnPay1h);
        btnPay2h  = v.findViewById(R.id.btnPay2h);
        btnPay3h  = v.findViewById(R.id.btnPay3h);
        btnPayDay = v.findViewById(R.id.btnPayDay);

        try { btnRefund = v.findViewById(R.id.btnRefund); } catch (Exception ignored) { btnRefund = null; }

        if (cardStatus != null) cardStatus.setVisibility(View.GONE);
        if (tvStatus != null) tvStatus.setText("");
        if (btnRefund != null) btnRefund.setVisibility(View.GONE);

        // ✅ adapter za parkinge
        if (etParking == null) throw new IllegalStateException("etParking mora biti MaterialAutoCompleteTextView sa id=@+id/etParking");

        lotsAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, new ArrayList<>());
        etParking.setAdapter(lotsAdapter);
        etParking.setThreshold(0);

        etParking.setOnClickListener(vw -> showParkingDropdownIfAny());
        etParking.setOnFocusChangeListener((vw, hasFocus) -> { if (hasFocus) showParkingDropdownIfAny(); });

        etParking.setOnItemClickListener((parent1, view, position, id) -> {
            saveFormState();
            refreshActiveStatusForCurrentPlate();
        });

        // Plate dropdown
        if (etPlate == null) throw new IllegalStateException("etPlate mora biti AutoCompleteTextView sa id=@+id/etPlate");

        etPlate.setFilters(new InputFilter[]{ new InputFilter.AllCaps(), new InputFilter.LengthFilter(16) });

        plateAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1,
                displaySuggestions);

        etPlate.setAdapter(plateAdapter);
        etPlate.setThreshold(0);

// ⬇️ OVDJE DODAJ OVO ⬇️
        etPlate.setOnItemClickListener((adapterView, itemView, position, id) -> {


            String display = (String) adapterView.getItemAtPosition(position);

            String plateOnly = displayToPlate.get(display);

            if (!TextUtils.isEmpty(plateOnly)) {
                etPlate.setText(plateOnly);
                etPlate.setSelection(plateOnly.length());
            }

            saveFormState();
            refreshActiveStatusForCurrentPlate();
        });



        // ✅ OTVORI DROPDOWN uvijek kad klikne u polje
        etPlate.setOnClickListener(vw -> showPlateDropdownIfAny());

// ✅ OTVORI DROPDOWN kad dobije fokus (npr. tab/next)
        etPlate.setOnFocusChangeListener((vw, hasFocus) -> {
            if (hasFocus) showPlateDropdownIfAny();
        });

// ✅ IKONA (strelica) otvara listu uvijek
        if (tilPlate != null) {
            tilPlate.setEndIconOnClickListener(vw -> {
                if (!etPlate.hasFocus()) etPlate.requestFocus();
                showPlateDropdownIfAny();
            });
        }


        etPlate.addTextChangedListener(new SimpleTextWatcher(() -> {
            saveFormState();
            refreshActiveStatusForCurrentPlate();

            if (!isAdded()) return;
            String txt = etPlate.getText() == null ? "" : etPlate.getText().toString().trim();
            if (txt.isEmpty()) {
                etPlate.post(() -> {
                    if (!isAdded()) return;
                    if (etPlate.hasFocus()) showPlateDropdownIfAny();
                });
            }
        }));

        etSpot.addTextChangedListener(new SimpleTextWatcher(() -> {
            saveFormState();
            refreshActiveStatusForCurrentPlate();
        }));

        btnPay1h.setOnClickListener(vw -> { if (validateInputsBasic()) showConfirmDialog("1h"); });
        btnPay2h.setOnClickListener(vw -> { if (validateInputsBasic()) showConfirmDialog("2h"); });
        btnPay3h.setOnClickListener(vw -> { if (validateInputsBasic()) showConfirmDialog("3h"); });
        btnPayDay.setOnClickListener(vw -> { if (validateInputsBasic()) showConfirmDialog("day"); });

        if (btnRefund != null) btnRefund.setOnClickListener(vw -> showRefundDialog());

        ensureCorrectUserSessionAndClearIfSwitched(true);

        loadBalance();
        loadParkingLots();
        loadUserVehiclesForDropdown();

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        ensureCorrectUserSessionAndClearIfSwitched(false);

        tryAutoShowMyActiveSession();
        restoreFormStateIfNeeded();
        refreshActiveStatusForCurrentPlate();
    }

    // =========================================================
    // Dropdown helpers
    // =========================================================
    private void showPlateDropdownIfAny() {
        if (!isAdded()) return;
        if (plateAdapter == null) return;
        if (plateAdapter.getCount() <= 0) return;

        etPlate.post(() -> {
            if (!isAdded()) return;
            etPlate.showDropDown(); // ne provjeravaj isPopupShowing - samo pokaži
        });
    }

    private void showParkingDropdownIfAny() {
        if (!isAdded()) return;
        if (lotsAdapter == null) return;
        if (lotsAdapter.getCount() <= 0) return;

        etParking.post(() -> {
            if (!isAdded()) return;
            if (!etParking.isPopupShowing()) etParking.showDropDown();
        });
    }

    // =========================================================
    // Lot selection helpers
    // =========================================================
    private int getSelectedLotIndex() {
        if (lots.isEmpty()) return -1;

        String txt = etParking.getText() == null ? "" : etParking.getText().toString().trim();
        if (!TextUtils.isEmpty(txt)) {
            for (int i = 0; i < lots.size(); i++) {
                String name = lots.get(i).name == null ? "" : lots.get(i).name.trim();
                if (txt.equals(name)) return i;
            }
        }

        // fallback: saved pos
        SharedPreferences p = prefs();
        if (p != null) {
            int savedPos = p.getInt(K_LOT_POS, -1);
            if (savedPos >= 0 && savedPos < lots.size()) return savedPos;

            String savedLotId = p.getString(K_LOT_ID, "");
            if (!TextUtils.isEmpty(savedLotId)) {
                for (int i = 0; i < lots.size(); i++) {
                    if (savedLotId.equals(lots.get(i).id)) return i;
                }
            }
        }

        return 0; // default na prvi
    }

    private void setSelectedLotIndex(int idx) {
        if (idx < 0 || idx >= lots.size()) return;
        if (etParking == null) return;
        String name = lots.get(idx).name == null ? "" : lots.get(idx).name;
        // false -> ne triggeruje filter/replace čudno
        etParking.setText(name, false);
    }

    // =========================================================
    // PREFS
    // =========================================================
    @Nullable
    private SharedPreferences prefs() {
        Context ctx = getContext();
        if (ctx == null) return null;
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void ensureCorrectUserSessionAndClearIfSwitched(boolean clearUiNow) {
        SharedPreferences p = prefs();
        if (p == null) return;

        String currentUid = FirebaseAuth.getInstance().getUid();
        String savedUid = p.getString(K_UID, "");

        if (!TextUtils.isEmpty(savedUid) && !TextUtils.equals(savedUid, currentUid)) {
            clearSavedFormState();
            restoredOnce = false;
            stopStatusTicker(false);

            if (clearUiNow && isAdded()) {
                if (etSpot != null) etSpot.setText("");
                if (etPlate != null) etPlate.setText("");
                if (etParking != null) etParking.setText("", false);
            }
        }

        if (!TextUtils.isEmpty(currentUid)) {
            p.edit().putString(K_UID, currentUid).apply();
        }
    }

    private void saveFormState() {
        SharedPreferences p = prefs();
        if (p == null) return;

        String uid = FirebaseAuth.getInstance().getUid();
        int pos = getSelectedLotIndex();
        String lotId = (pos >= 0 && pos < lots.size()) ? lots.get(pos).id : "";
        String spot = etSpot != null && etSpot.getText() != null ? etSpot.getText().toString().trim() : "";
        String plate = resolvePlateFromInput();

        SharedPreferences.Editor ed = p.edit();
        if (!TextUtils.isEmpty(uid)) ed.putString(K_UID, uid);

        ed.putInt(K_LOT_POS, pos)
                .putString(K_LOT_ID, lotId == null ? "" : lotId)
                .putString(K_SPOT, spot)
                .putString(K_PLATE, plate)
                .apply();
    }

    private void restoreFormStateIfNeeded() {
        if (restoredOnce) return;

        SharedPreferences p = prefs();
        if (p == null) return;

        String uid = FirebaseAuth.getInstance().getUid();
        String savedUid = p.getString(K_UID, "");
        if (!TextUtils.equals(uid, savedUid)) {
            restoredOnce = true;
            return;
        }

        String spot = p.getString(K_SPOT, "");
        String plate = p.getString(K_PLATE, "");

        if (etSpot != null && TextUtils.isEmpty(etSpot.getText())) {
            if (!TextUtils.isEmpty(spot)) etSpot.setText(spot);
        }
        if (etPlate != null && TextUtils.isEmpty(etPlate.getText())) {
            if (!TextUtils.isEmpty(plate)) {
                etPlate.setText(plate);
                etPlate.setSelection(plate.length());
            }
        }

        restoredOnce = true;
    }

    private void applySavedLotSelectionAfterLotsLoaded() {
        SharedPreferences p = prefs();
        if (p == null) return;

        String uid = FirebaseAuth.getInstance().getUid();
        String savedUid = p.getString(K_UID, "");
        if (!TextUtils.equals(uid, savedUid)) return;

        int savedPos = p.getInt(K_LOT_POS, -1);
        String savedLotId = p.getString(K_LOT_ID, "");

        int target = -1;
        if (!TextUtils.isEmpty(savedLotId)) {
            for (int i = 0; i < lots.size(); i++) {
                if (savedLotId.equals(lots.get(i).id)) { target = i; break; }
            }
        }
        if (target < 0 && savedPos >= 0 && savedPos < lots.size()) target = savedPos;

        if (target >= 0) setSelectedLotIndex(target);
        else if (!lots.isEmpty()) setSelectedLotIndex(0);
    }

    private void clearSavedFormState() {
        SharedPreferences p = prefs();
        if (p == null) return;
        p.edit()
                .remove(K_LOT_POS)
                .remove(K_LOT_ID)
                .remove(K_SPOT)
                .remove(K_PLATE)
                .remove(K_HAS_ACTIVE)
                .apply();
    }

    // =========================================================
    // Vehicles dropdown
    // =========================================================
    private void loadUserVehiclesForDropdown() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FirebaseUtils.userVehicles(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot vehiclesSnap) {
                List<VehicleDisplay> temp = new ArrayList<>();

                for (DataSnapshot v : vehiclesSnap.getChildren()) {
                    String nick  = v.child("nickname").getValue(String.class);
                    String plate = v.child("plate").getValue(String.class);

                    String p = normalizePlate(plate);
                    if (TextUtils.isEmpty(p)) continue;

                    String n = nick == null ? "" : nick.trim();
                    String display = !TextUtils.isEmpty(n) ? (n + " - " + p) : p;

                    temp.add(new VehicleDisplay(display, p));
                }

                Collections.sort(temp, Comparator.comparing(a -> a.display.toLowerCase(Locale.ROOT)));

                displayToPlate.clear();
                displaySuggestions.clear();

                Set<String> used = new HashSet<>();
                for (VehicleDisplay it : temp) {
                    String d = it.display == null ? "" : it.display.trim();
                    String p = it.plate == null ? "" : it.plate.trim();
                    if (TextUtils.isEmpty(p)) continue;
                    if (TextUtils.isEmpty(d)) d = p;

                    if (used.contains(d)) d = d + " (" + p + ")";
                    used.add(d);

                    displayToPlate.put(d, p);
                    displaySuggestions.add(d);
                }

                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (plateAdapter != null) plateAdapter.notifyDataSetChanged();
                    String cur = etPlate.getText() == null ? "" : etPlate.getText().toString().trim();
                    if (cur.isEmpty() && etPlate.hasFocus()) showPlateDropdownIfAny();
                });
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "loadUserVehiclesForDropdown cancelled: " + error.getMessage());
            }
        });
    }

    // =========================================================
    // Balance / lots
    // =========================================================
    private void loadBalance() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FirebaseUtils.balance(uid).addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                Double bal = snapshot.getValue(Double.class);
                if (tvBalance != null) {
                    tvBalance.setText(String.format(Locale.getDefault(),
                            "Balans: %.2f KM", bal == null ? 0.0 : bal));
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void loadParkingLots() {
        FirebaseUtils.parkingLotsRef().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                lots.clear();
                List<String> names = new ArrayList<>();

                for (DataSnapshot p : ds.getChildren()) {
                    String id   = p.getKey();
                    String name = p.child("name").getValue(String.class);

                    Double ph = p.child("pricing").child("perHour").getValue(Double.class);
                    Double pd = p.child("pricing").child("perDay").getValue(Double.class);

                    if (id == null || name == null) continue;
                    if (hasInvalidKeyChar(id)) continue;

                    LotItem li = new LotItem();
                    li.id = id;
                    li.name = name;
                    li.perHour = ph == null ? 0.0 : ph;
                    li.perDay  = pd == null ? 0.0 : pd;

                    lots.add(li);
                    names.add(name);
                }

                lotsAdapter.clear();
                lotsAdapter.addAll(names);
                lotsAdapter.notifyDataSetChanged();

                applySavedLotSelectionAfterLotsLoaded();
                tryAutoShowMyActiveSession();
                restoreFormStateIfNeeded();
                refreshActiveStatusForCurrentPlate();
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                toast(error.getMessage());
            }
        });
    }

    // =========================================================
    // AUTO SHOW ACTIVE SESSION
    // =========================================================
    private void tryAutoShowMyActiveSession() {
        if (!isAdded()) return;
        if (lots.isEmpty()) return;
        loadMyActiveSessionAndBind();
    }

    private void loadMyActiveSessionAndBind() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        fetchServerNow(serverNow -> {
            Query q = FirebaseUtils.sessionsRef()
                    .orderByChild("userId")
                    .equalTo(uid)
                    .limitToLast(50);

            q.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot ds) {

                    String bestSessionId = null;
                    String bestLotId = null;
                    String bestSpace = null;
                    String bestPlate = null;
                    String bestLotName = null;

                    long bestStart = 0L;
                    long bestEnd = 0L;
                    double bestAmount = 0.0;

                    for (DataSnapshot s : ds.getChildren()) {
                        String sid = s.getKey();
                        if (sid == null) continue;

                        String status = safeStr(s.child("status").getValue(String.class)).toUpperCase(Locale.ROOT);
                        if (TextUtils.isEmpty(status)) status = "ACTIVE";
                        if (!"ACTIVE".equals(status)) continue;

                        Long startL = s.child("startTime").getValue(Long.class);
                        Long endL   = s.child("endTime").getValue(Long.class);
                        Double amtD = s.child("amount").getValue(Double.class);

                        long start = startL == null ? 0L : startL;
                        long end   = endL == null ? 0L : endL;
                        double amt = amtD == null ? 0.0 : amtD;

                        if (end <= (serverNow + DRIFT_MS)) continue;

                        String lotId = s.child("parkingLotId").getValue(String.class);
                        String space = s.child("space").getValue(String.class);
                        String plate = s.child("plate").getValue(String.class);

                        if (TextUtils.isEmpty(lotId) || TextUtils.isEmpty(space)) continue;

                        if (end > bestEnd) {
                            bestEnd = end;
                            bestStart = start;
                            bestAmount = amt;

                            bestSessionId = sid;
                            bestLotId = lotId;
                            bestSpace = space;
                            bestPlate = normalizePlate(plate);

                            bestLotName = null;
                            for (LotItem li : lots) {
                                if (lotId.equals(li.id)) { bestLotName = li.name; break; }
                            }
                        }
                    }

                    if (TextUtils.isEmpty(bestSessionId)) return;
                    if (!isAdded()) return;

                    int target = -1;
                    for (int i = 0; i < lots.size(); i++) {
                        if (bestLotId.equals(lots.get(i).id)) { target = i; break; }
                    }
                    if (target >= 0) setSelectedLotIndex(target);

                    if (etSpot != null) etSpot.setText(bestSpace);
                    if (etPlate != null && !TextUtils.isEmpty(bestPlate)) {
                        etPlate.setText(bestPlate);
                        etPlate.setSelection(bestPlate.length());
                    }

                    currentSessionId = bestSessionId;
                    currentLotId = bestLotId;
                    currentLotName = TextUtils.isEmpty(bestLotName) ? "—" : bestLotName;
                    currentSpaceKey = bestSpace;
                    currentPlate = bestPlate;

                    currentStartTime = bestStart;
                    currentEndTime = bestEnd;
                    currentAmount = bestAmount;

                    markHasActive(true);
                    saveFormState();
                    startStatusTicker();
                }

                @Override public void onCancelled(@NonNull DatabaseError error) {
                    Log.w(TAG, "loadMyActiveSessionAndBind cancelled: " + error.getMessage());
                }
            });
        });
    }

    // =========================================================
    // Server time
    // =========================================================
    private void fetchServerNow(@NonNull Consumer<Long> onOk) {
        FirebaseUtils.infoServerTimeOffset().get()
                .addOnSuccessListener(snap -> {
                    Long offset = snap.exists() ? snap.getValue(Long.class) : null;
                    long now = System.currentTimeMillis() + (offset == null ? 0L : offset);
                    onOk.accept(now);
                })
                .addOnFailureListener(e -> onOk.accept(System.currentTimeMillis()));
    }

    // =========================================================
    // Confirm dialog
    // =========================================================
    private void showConfirmDialog(@NonNull String type) {
        if (!isAdded()) return;

        int idx = getSelectedLotIndex();
        LotItem lot = (idx >= 0 && idx < lots.size()) ? lots.get(idx) : null;

        String parkingName = (lot != null && !TextUtils.isEmpty(lot.name)) ? lot.name : "—";
        String spotStr = etSpot.getText().toString().trim();
        String plate = resolvePlateFromInput();

        double amount = 0.0;
        if (lot != null) amount = calcAmountByType(type, lot);

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm_payment, null, false);

        ((TextView) dialogView.findViewById(R.id.tvParkingValue)).setText(parkingName);
        ((TextView) dialogView.findViewById(R.id.tvSpotValue)).setText(spotStr);
        ((TextView) dialogView.findViewById(R.id.tvPlateValue)).setText(plate);
        ((TextView) dialogView.findViewById(R.id.tvTypeValue)).setText(typeLabel(type));
        ((TextView) dialogView.findViewById(R.id.tvAmountValue))
                .setText(String.format(Locale.getDefault(), "%.2f KM", amount));

        androidx.appcompat.app.AlertDialog dlg = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        Button btnCancel  = dialogView.findViewById(R.id.btnCancel);
        Button btnConfirm = dialogView.findViewById(R.id.btnConfirm);

        btnCancel.setOnClickListener(v -> dlg.dismiss());
        btnConfirm.setOnClickListener(v -> { dlg.dismiss(); pay(type); });

        dlg.show();
    }

    // =========================================================
    // PAY FLOW
    // =========================================================
    private void pay(@NonNull String type) {
        if (isPaying) return;
        isPaying = true;
        setPayUiEnabled(false);
        setRefundUiEnabled(false);

        int idx = getSelectedLotIndex();
        if (idx < 0 || idx >= lots.size()) { failAndUnlock("Odaberite parking."); return; }
        final LotItem lot = lots.get(idx);

        final String spotStr = etSpot.getText().toString().trim();
        final int spot;
        try { spot = Integer.parseInt(spotStr); }
        catch (Exception e) { failAndUnlock("Neispravan broj mjesta."); return; }
        if (spot <= 0) { failAndUnlock("Broj mjesta mora biti veći od 0."); return; }

        final String spaceKey = String.valueOf(spot);
        if (hasInvalidKeyChar(spaceKey)) { failAndUnlock("Neispravan ključ mjesta."); return; }

        final String plate = resolvePlateFromInput();
        if (TextUtils.isEmpty(plate)) { failAndUnlock("Unesite registarsku oznaku."); return; }

        final FirebaseUser fu = FirebaseAuth.getInstance().getCurrentUser();
        if (fu == null) { failAndUnlock("Niste prijavljeni."); return; }
        final String uid = fu.getUid();

        final double amount = calcAmountByType(type, lot);
        if (amount <= 0) { failAndUnlock("Cijena nije podešena."); return; }

        final DatabaseReference spaceRef = FirebaseUtils.parkingSpace(lot.id, spaceKey);

        fetchServerNow(serverNow -> {
            spaceRef.get()
                    .addOnSuccessListener(spaceSnap -> {

                        String status = getLower(spaceSnap.child("status").getValue(String.class));
                        String reservedBy = safeStr(spaceSnap.child("reservedBy").getValue(String.class));

                        Long untilL = spaceSnap.child("until").getValue(Long.class);
                        long until = untilL == null ? 0L : untilL;

                        String plateOnSpot = normalizePlate(spaceSnap.child("plate").getValue(String.class));

                        boolean occupied = "zauzeto".equals(status);
                        boolean activeOther = occupied && until > (serverNow + DRIFT_MS) && !uid.equals(reservedBy);

                        if (activeOther) {
                            showTakeoverDialogs(type, lot, uid, spaceKey, plate, plateOnSpot);
                            isPaying = false;
                            setPayUiEnabled(true);
                            setRefundUiEnabled(true);
                            return;
                        }

                        proceedNormalReserveAndPay(type, lot, uid, spaceKey, plate, amount, serverNow);

                    })
                    .addOnFailureListener(e -> failAndUnlock(mapFirebaseError(e)));
        });
    }

    private void showTakeoverDialogs(@NonNull String type,
                                     @NonNull LotItem lot,
                                     @NonNull String uid,
                                     @NonNull String spaceKey,
                                     @NonNull String userInputPlate,
                                     @Nullable String plateFromDb) {
        if (!isAdded()) return;

        final String plateDb = TextUtils.isEmpty(plateFromDb) ? "—" : plateFromDb;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Provjera parking mjesta")
                .setMessage("Da li je na ovom mjestu još uvijek parkirano vozilo:\n\n" + plateDb + " ?")
                .setCancelable(true)
                .setPositiveButton("DA", (d, w) -> {
                    d.dismiss();
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Uredu")
                            .setMessage("Uredu, unesite ponovo podatke.")
                            .setPositiveButton("OK", (d2, w2) -> d2.dismiss())
                            .show();
                })
                .setNegativeButton("NE", (d, w) -> {
                    d.dismiss();
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Upozorenje")
                            .setMessage("Ukoliko zloupotrijebite sistem, snosit ćete krivičnu i materijalnu odgovornost.\n\n" +
                                    "Da li ste sigurni da vozilo:\n" + plateDb + "\n\nnije više na trenutnom mjestu?")
                            .setCancelable(true)
                            .setPositiveButton("DA", (d2, w2) -> {
                                d2.dismiss();
                                forceTakeoverAndPay(type, lot, uid, spaceKey, userInputPlate);
                            })
                            .setNegativeButton("NE", (d2, w2) -> d2.dismiss())
                            .show();
                })
                .show();
    }

    // ----------------- NORMAL RESERVE + PAY -----------------
    private void proceedNormalReserveAndPay(@NonNull String type,
                                            @NonNull LotItem lot,
                                            @NonNull String uid,
                                            @NonNull String spaceKey,
                                            @NonNull String plate,
                                            double amount,
                                            long serverNow) {

        final long duration = durationByType(type);
        final DatabaseReference spaceRef = FirebaseUtils.parkingSpace(lot.id, spaceKey);

        final String sessionId = FirebaseUtils.sessionsRef().push().getKey();
        if (sessionId == null) { failAndUnlock("Greška: sessionId"); return; }

        spaceRef.runTransaction(new Transaction.Handler() {
            @NonNull @Override
            public Transaction.Result doTransaction(@NonNull MutableData cur) {

                String status = getLower(cur.child("status").getValue(String.class));
                String reservedBy = safeStr(cur.child("reservedBy").getValue(String.class));

                Long untilL = cur.child("until").getValue(Long.class);
                long until = untilL == null ? 0L : untilL;

                boolean occupied = "zauzeto".equals(status);
                boolean expired = occupied && untilL != null && until <= (serverNow + DRIFT_MS);
                boolean noStatus = TextUtils.isEmpty(status);

                if (!occupied || expired || noStatus) {
                    cur.child("status").setValue("zauzeto");
                    cur.child("reservedBy").setValue(uid);
                    cur.child("until").setValue(serverNow + duration);
                    cur.child("plate").setValue(plate);
                    cur.child("sessionId").setValue(sessionId);

                    cur.child("takeover").setValue(null);
                    cur.child("takeoverAt").setValue(null);

                    cur.child("updatedAt").setValue(serverNow);
                    return Transaction.success(cur);
                }

                if (uid.equals(reservedBy)) {
                    long base = Math.max(serverNow, until);
                    cur.child("status").setValue("zauzeto");
                    cur.child("reservedBy").setValue(uid);
                    cur.child("until").setValue(base + duration);
                    cur.child("plate").setValue(plate);
                    cur.child("sessionId").setValue(sessionId);

                    cur.child("takeover").setValue(null);
                    cur.child("takeoverAt").setValue(null);

                    cur.child("updatedAt").setValue(serverNow);
                    return Transaction.success(cur);
                }

                return Transaction.abort();
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot after) {
                if (error != null) { failAndUnlock("Greška rezervacije: " + error.getMessage()); return; }
                if (!committed || after == null) { failAndUnlock("Mjesto je trenutno zauzeto."); return; }

                Long endL = after.child("until").getValue(Long.class);
                long endTime = (endL == null ? serverNow + duration : endL);

                chargeAndWriteSession(uid, lot.id, spaceKey, serverNow, endTime, amount, type, plate, sessionId,
                        () -> {
                            markHasActive(true);
                            saveFormState();
                            refreshActiveStatusForCurrentPlate();
                            doneAndUnlock();
                        },
                        msg -> rollbackSpaceIfMyLock(spaceRef, uid, sessionId, () -> failAndUnlock(msg)));
            }
        });
    }

    // ----------------- TAKEOVER + PAY -----------------
    private void forceTakeoverAndPay(@NonNull String type,
                                     @NonNull LotItem lot,
                                     @NonNull String uid,
                                     @NonNull String spaceKey,
                                     @NonNull String plate) {

        if (isPaying) return;
        isPaying = true;
        setPayUiEnabled(false);
        setRefundUiEnabled(false);

        final long duration = durationByType(type);
        final double amount = calcAmountByType(type, lot);
        final DatabaseReference spaceRef = FirebaseUtils.parkingSpace(lot.id, spaceKey);

        final String newSessionId = FirebaseUtils.sessionsRef().push().getKey();
        if (newSessionId == null) { failAndUnlock("Greška: sessionId"); return; }

        final int[] retriesLeft = { MAX_TAKEOVER_RETRY };
        final Runnable[] runner = new Runnable[1];

        runner[0] = () -> fetchServerNow(serverNow -> {

            spaceRef.get().addOnSuccessListener(snap -> {

                String status = getLower(snap.child("status").getValue(String.class));
                String reservedBy = safeStr(snap.child("reservedBy").getValue(String.class));
                Long untilL = snap.child("until").getValue(Long.class);
                long until = untilL == null ? 0L : untilL;

                boolean occupied = "zauzeto".equals(status);
                boolean activeOther = occupied
                        && until > (serverNow + DRIFT_MS)
                        && !TextUtils.isEmpty(reservedBy)
                        && !uid.equals(reservedBy);

                if (!activeOther) {
                    proceedNormalReserveAndPay(type, lot, uid, spaceKey, plate, amount, serverNow);
                    return;
                }

                spaceRef.runTransaction(new Transaction.Handler() {
                    @NonNull @Override
                    public Transaction.Result doTransaction(@NonNull MutableData cur) {
                        String st = getLower(cur.child("status").getValue(String.class));
                        String oldReservedBy = safeStr(cur.child("reservedBy").getValue(String.class));

                        Long untilTxL = cur.child("until").getValue(Long.class);
                        long untilTx = untilTxL == null ? 0L : untilTxL;

                        boolean occ = "zauzeto".equals(st);
                        boolean active = occ && untilTx > (serverNow + DRIFT_MS);

                        if (!(occ && active)) return Transaction.abort();
                        if (TextUtils.isEmpty(oldReservedBy) || uid.equals(oldReservedBy)) return Transaction.abort();

                        cur.child("takeover").setValue(true);
                        cur.child("takeoverAt").setValue(serverNow);

                        cur.child("status").setValue("zauzeto");
                        cur.child("reservedBy").setValue(uid);
                        cur.child("until").setValue(serverNow + duration);
                        cur.child("plate").setValue(plate);
                        cur.child("sessionId").setValue(newSessionId);
                        cur.child("updatedAt").setValue(serverNow);

                        return Transaction.success(cur);
                    }

                    @Override
                    public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot after) {
                        if (error != null) { failAndUnlock("Greška takeover-a: " + error.getMessage()); return; }

                        if (!committed) {
                            if (retriesLeft[0] > 0) {
                                retriesLeft[0]--;
                                if (tvStatus != null) tvStatus.postDelayed(runner[0], 150);
                                else runner[0].run();
                                return;
                            }
                            failAndUnlock("Takeover nije moguć (mjesto se promijenilo).");
                            return;
                        }

                        long endTime = serverNow + duration;

                        chargeAndWriteSession(uid, lot.id, spaceKey, serverNow, endTime, amount, type, plate, newSessionId,
                                () -> {
                                    markHasActive(true);
                                    saveFormState();
                                    refreshActiveStatusForCurrentPlate();
                                    doneAndUnlock();
                                },
                                msg -> rollbackSpaceIfMyLock(spaceRef, uid, newSessionId, () -> failAndUnlock(msg)));
                    }
                });

            }).addOnFailureListener(e -> failAndUnlock(mapFirebaseError(e)));

        });

        runner[0].run();
    }

    // ----------------- Write session + charge -----------------
    private void chargeAndWriteSession(@NonNull String uid,
                                       @NonNull String lotId,
                                       @NonNull String spaceKey,
                                       long startTime,
                                       long endTime,
                                       double amount,
                                       @NonNull String type,
                                       @NonNull String plate,
                                       @NonNull String sessionId,
                                       @NonNull Runnable onSuccess,
                                       @NonNull Consumer<String> onFail) {

        FirebaseUtils.balance(uid).runTransaction(new Transaction.Handler() {
            @NonNull @Override
            public Transaction.Result doTransaction(@NonNull MutableData cur) {
                Double bal = cur.getValue(Double.class);
                if (bal == null) bal = 0.0;
                if (bal < amount) return Transaction.abort();
                cur.setValue(bal - amount);
                return Transaction.success(cur);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot dataSnapshot) {
                if (error != null) { onFail.accept("Greška naplate: " + error.getMessage()); return; }
                if (!committed) { onFail.accept("Nedovoljan balans."); return; }

                Map<String, Object> sess = new HashMap<>();
                sess.put("userId", uid);
                sess.put("parkingLotId", lotId);
                sess.put("space", spaceKey);
                sess.put("startTime", startTime);
                sess.put("endTime", endTime);
                sess.put("amount", amount);
                sess.put("type", type);
                sess.put("plate", plate);
                sess.put("status", "ACTIVE");

                FirebaseUtils.session(sessionId).setValue(sess)
                        .addOnSuccessListener(v -> onSuccess.run())
                        .addOnFailureListener(e -> onFail.accept(mapFirebaseError(e)));
            }
        });
    }

    // ----------------- Rollback space only if my lock -----------------
    private void rollbackSpaceIfMyLock(@NonNull DatabaseReference spaceRef,
                                       @NonNull String uid,
                                       @NonNull String sessionId,
                                       @NonNull Runnable after) {

        spaceRef.runTransaction(new Transaction.Handler() {
            @NonNull @Override
            public Transaction.Result doTransaction(@NonNull MutableData cur) {
                String rb = safeStr(cur.child("reservedBy").getValue(String.class));
                String sid = safeStr(cur.child("sessionId").getValue(String.class));

                if (uid.equals(rb) && sessionId.equals(sid)) {
                    cur.child("status").setValue("slobodno");
                    cur.child("reservedBy").setValue(null);
                    cur.child("until").setValue(null);
                    cur.child("plate").setValue(null);
                    cur.child("sessionId").setValue(null);

                    cur.child("takeover").setValue(null);
                    cur.child("takeoverAt").setValue(null);

                    cur.child("updatedAt").setValue(System.currentTimeMillis());
                }
                return Transaction.success(cur);
            }

            @Override public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                after.run();
            }
        });
    }

    // =========================================================
    // STATUS (tajmer)
    // =========================================================
    private void refreshActiveStatusForCurrentPlate() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) { stopStatusTicker(true); return; }

        int idx = getSelectedLotIndex();
        if (idx < 0 || idx >= lots.size()) { stopStatusTicker(false); return; }

        String spotStr = etSpot.getText().toString().trim();
        if (TextUtils.isEmpty(spotStr)) { stopStatusTicker(false); return; }

        int spot;
        try { spot = Integer.parseInt(spotStr); }
        catch (Exception e) { stopStatusTicker(false); return; }

        final String spaceKey = String.valueOf(spot);
        final LotItem lot = lots.get(idx);
        final DatabaseReference spaceRef = FirebaseUtils.parkingSpace(lot.id, spaceKey);

        fetchServerNow(serverNow -> {
            spaceRef.get().addOnSuccessListener(snap -> {
                if (!snap.exists()) { stopStatusTicker(false); return; }

                String status = getLower(snap.child("status").getValue(String.class));
                String reservedBy = safeStr(snap.child("reservedBy").getValue(String.class));

                Long untilL = snap.child("until").getValue(Long.class);
                long until = untilL == null ? 0L : untilL;

                boolean mineActive = "zauzeto".equals(status)
                        && uid.equals(reservedBy)
                        && until > (serverNow + DRIFT_MS);

                if (!mineActive) {
                    boolean expiredMine =
                            "zauzeto".equals(status)
                                    && uid.equals(reservedBy)
                                    && until > 0
                                    && until <= (serverNow + DRIFT_MS);

                    stopStatusTicker(expiredMine);
                    return;
                }

                String sessionId = snap.child("sessionId").getValue(String.class);
                String plateOnSpot = snap.child("plate").getValue(String.class);

                currentSessionId = sessionId;
                currentLotId = lot.id;
                currentLotName = lot.name;
                currentSpaceKey = spaceKey;
                currentPlate = plateOnSpot;
                currentEndTime = until;

                markHasActive(true);
                saveFormState();

                if (!TextUtils.isEmpty(sessionId)) {
                    FirebaseUtils.session(sessionId).get().addOnSuccessListener(ses -> {
                        Long st = ses.child("startTime").getValue(Long.class);
                        Double amt = ses.child("amount").getValue(Double.class);
                        currentStartTime = st == null ? 0L : st;
                        currentAmount = amt == null ? 0.0 : amt;
                        startStatusTicker();
                    }).addOnFailureListener(e -> {
                        currentStartTime = 0L;
                        currentAmount = 0.0;
                        startStatusTicker();
                    });
                } else {
                    currentStartTime = 0L;
                    currentAmount = 0.0;
                    startStatusTicker();
                }

            }).addOnFailureListener(e -> stopStatusTicker(false));
        });
    }

    private void startStatusTicker() {
        if (tvStatus == null) return;
        if (!isAdded()) return;

        if (cardStatus != null) cardStatus.setVisibility(View.VISIBLE);
        updateRefundButtonVisibility();

        if (statusTick == null) {
            statusTick = new Runnable() {
                @Override public void run() {
                    if (!isAdded() || tvStatus == null) return;

                    long now = System.currentTimeMillis();
                    if (currentEndTime > now) {
                        long remain = currentEndTime - now;
                        long min = remain / 60000;
                        long sec = (remain / 1000) % 60;

                        String lot = TextUtils.isEmpty(currentLotName) ? "—" : currentLotName;
                        String space = TextUtils.isEmpty(currentSpaceKey) ? "—" : currentSpaceKey;

                        tvStatus.setText(String.format(Locale.getDefault(),
                                "Uplata aktivna • Parking: %s • Mjesto: %s • preostalo: %dm %02ds",
                                lot, space, min, sec));

                        saveFormState();
                        updateRefundButtonVisibility();

                        tvStatus.removeCallbacks(this);
                        tvStatus.postDelayed(this, TICK_MS);
                    } else {
                        stopStatusTicker(true);
                    }
                }
            };
        }

        tvStatus.removeCallbacks(statusTick);
        tvStatus.post(statusTick);
    }

    private void updateRefundButtonVisibility() {
        if (btnRefund == null) return;
        boolean has = !TextUtils.isEmpty(currentSessionId);
        boolean active = currentEndTime > System.currentTimeMillis();
        btnRefund.setVisibility((has && active) ? View.VISIBLE : View.GONE);
    }

    private void stopStatusTicker(boolean clearFormIfExpired) {
        if (tvStatus != null && statusTick != null) {
            tvStatus.removeCallbacks(statusTick);
        }

        currentSessionId = null;
        currentLotId = null;
        currentLotName = null;
        currentSpaceKey = null;
        currentPlate = null;

        currentStartTime = 0L;
        currentEndTime = 0L;
        currentAmount = 0.0;

        if (tvStatus != null) tvStatus.setText("");
        if (cardStatus != null) cardStatus.setVisibility(View.GONE);
        if (btnRefund != null) btnRefund.setVisibility(View.GONE);

        markHasActive(false);

        if (clearFormIfExpired) {
            clearSavedFormState();
        }
    }

    private void markHasActive(boolean v) {
        SharedPreferences p = prefs();
        if (p == null) return;
        p.edit().putBoolean(K_HAS_ACTIVE, v).apply();
    }

    // =========================================================
    // REFUND
    // =========================================================
    private void showRefundDialog() {
        if (!isAdded()) return;

        if (TextUtils.isEmpty(currentSessionId) || currentEndTime <= System.currentTimeMillis()) {
            toast("Nema aktivne uplate za refund.");
            return;
        }

        long now = System.currentTimeMillis();
        long totalMs = Math.max(1L, (currentEndTime - currentStartTime));
        long remainingMs = Math.max(0L, (currentEndTime - now));
        double preview = round2(currentAmount * (remainingMs / (double) totalMs));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Refund parkinga")
                .setMessage(String.format(Locale.getDefault(),
                        "Želite prekinuti parking i vratiti preostalo na balans?\n\nProcijenjeni refund: %.2f KM",
                        preview))
                .setNegativeButton("Odustani", (d, w) -> d.dismiss())
                .setPositiveButton("Refund", (d, w) -> refundCurrentSession())
                .show();
    }

    private void refundCurrentSession() {
        if (isRefunding) return;
        isRefunding = true;
        setRefundUiEnabled(false);

        FirebaseUser fu = FirebaseAuth.getInstance().getCurrentUser();
        if (fu == null) { finishRefundFail("Niste prijavljeni."); return; }
        String uid = fu.getUid();

        final String sessionId = currentSessionId;
        if (TextUtils.isEmpty(sessionId)) { finishRefundFail("Session nije pronađen."); return; }

        fetchServerNow(serverNow -> {
            DatabaseReference sesRef = FirebaseUtils.session(sessionId);

            sesRef.get().addOnSuccessListener(snap -> {
                if (!snap.exists()) { finishRefundFail("Session ne postoji."); return; }

                String userId = snap.child("userId").getValue(String.class);
                if (!uid.equals(userId)) { finishRefundFail("Nemate pravo na ovaj refund."); return; }

                String st = safeStr(snap.child("status").getValue(String.class)).toUpperCase(Locale.ROOT);
                if (TextUtils.isEmpty(st)) st = "ACTIVE";
                if (!"ACTIVE".equals(st)) { finishRefundFail("Refund nije moguć."); return; }

                Long startL = snap.child("startTime").getValue(Long.class);
                Long endL = snap.child("endTime").getValue(Long.class);
                Double amountD = snap.child("amount").getValue(Double.class);

                String lotId = snap.child("parkingLotId").getValue(String.class);
                String space = snap.child("space").getValue(String.class);

                long start = startL == null ? 0L : startL;
                long end = endL == null ? 0L : endL;
                double amount = amountD == null ? 0.0 : amountD;

                if (end <= serverNow) { finishRefundFail("Parking je istekao. Refund nije moguć."); return; }

                long totalMs = Math.max(1L, (end - start));
                long remainingMs = Math.max(0L, (end - serverNow));
                double refund = round2(amount * (remainingMs / (double) totalMs));
                if (refund < MIN_REFUND_KM) { finishRefundFail("Preostali iznos je premali za refund."); return; }

                sesRef.runTransaction(new Transaction.Handler() {
                    @NonNull @Override
                    public Transaction.Result doTransaction(@NonNull MutableData cur) {
                        String s = safeStr((String) cur.child("status").getValue()).toUpperCase(Locale.ROOT);
                        if (TextUtils.isEmpty(s)) s = "ACTIVE";
                        if (!"ACTIVE".equals(s)) return Transaction.abort();

                        cur.child("status").setValue("REFUNDED");
                        cur.child("refundedAt").setValue(serverNow);
                        cur.child("refundedAmount").setValue(refund);

                        cur.child("endTime").setValue(serverNow);
                        cur.child("endedAt").setValue(serverNow);
                        return Transaction.success(cur);
                    }

                    @Override
                    public void onComplete(@Nullable DatabaseError e1, boolean committed, @Nullable DataSnapshot dsAfter) {
                        if (e1 != null) { finishRefundFail("Greška refund-a: " + e1.getMessage()); return; }
                        if (!committed) { finishRefundFail("Refund nije moguć."); return; }

                        FirebaseUtils.balance(uid).runTransaction(new Transaction.Handler() {
                            @NonNull @Override
                            public Transaction.Result doTransaction(@NonNull MutableData curBal) {
                                Double b = curBal.getValue(Double.class);
                                if (b == null) b = 0.0;
                                curBal.setValue(b + refund);
                                return Transaction.success(curBal);
                            }

                            @Override
                            public void onComplete(@Nullable DatabaseError e2, boolean committed2, @Nullable DataSnapshot d2) {

                                if (!TextUtils.isEmpty(lotId) && !TextUtils.isEmpty(space)) {
                                    DatabaseReference spaceRef = FirebaseUtils.parkingSpace(lotId, space);
                                    rollbackSpaceIfMyLock(spaceRef, uid, sessionId, () -> {});
                                }

                                toast(String.format(Locale.getDefault(), "Refund uspješan: +%.2f KM", refund));
                                isRefunding = false;
                                setRefundUiEnabled(true);

                                stopStatusTicker(true);
                                refreshActiveStatusForCurrentPlate();
                            }
                        });
                    }
                });

            }).addOnFailureListener(e -> finishRefundFail(mapFirebaseError(e)));
        });
    }

    private void finishRefundFail(String msg) {
        toast(msg);
        isRefunding = false;
        setRefundUiEnabled(true);
        refreshActiveStatusForCurrentPlate();
    }

    // =========================================================
    // UI helpers
    // =========================================================
    private void setPayUiEnabled(boolean enabled) {
        if (btnPay1h != null) btnPay1h.setEnabled(enabled);
        if (btnPay2h != null) btnPay2h.setEnabled(enabled);
        if (btnPay3h != null) btnPay3h.setEnabled(enabled);
        if (btnPayDay != null) btnPayDay.setEnabled(enabled);
    }

    private void setRefundUiEnabled(boolean enabled) {
        if (btnRefund != null) btnRefund.setEnabled(enabled);
    }

    private void failAndUnlock(String msg) {
        toast(msg);
        isPaying = false;
        setPayUiEnabled(true);
        setRefundUiEnabled(true);
    }

    private void doneAndUnlock() {
        isPaying = false;
        setPayUiEnabled(true);
        setRefundUiEnabled(true);
    }

    // =========================================================
    // Validation / calc
    // =========================================================
    private boolean validateInputsBasic() {
        int idx = getSelectedLotIndex();
        if (idx < 0 || idx >= lots.size()) { toast("Odaberite parking."); return false; }

        String spotStr = etSpot.getText().toString().trim();
        if (TextUtils.isEmpty(spotStr)) { toast("Unesite broj mjesta."); return false; }
        int s;
        try { s = Integer.parseInt(spotStr); }
        catch (Exception e) { toast("Neispravan broj mjesta."); return false; }
        if (s <= 0) { toast("Broj mjesta mora biti veći od 0."); return false; }

        String plate = resolvePlateFromInput();
        if (TextUtils.isEmpty(plate)) { toast("Unesite registarsku oznaku."); return false; }
        if (!plate.matches("^[A-Z0-9 -]{4,12}$")) { toast("Neispravan format tablica."); return false; }

        return true;
    }

    private String typeLabel(@NonNull String type){
        switch (type) {
            case "1h": return "1 sat";
            case "2h": return "2 sata";
            case "3h": return "3 sata";
            default: return "Dnevna karta";
        }
    }

    private long durationByType(@NonNull String type){
        switch (type) {
            case "1h": return 1L * ONE_HOUR_MS;
            case "2h": return 2L * ONE_HOUR_MS;
            case "3h": return 3L * ONE_HOUR_MS;
            default: return ONE_DAY_MS;
        }
    }

    private double calcAmountByType(@NonNull String type, @NonNull LotItem lot){
        switch (type) {
            case "1h": return lot.perHour;
            case "2h": return lot.perHour * 2.0;
            case "3h": return lot.perHour * 3.0;
            default:   return (lot.perDay > 0 ? lot.perDay : (lot.perHour * 8.0));
        }
    }

    // =========================================================
    // Helpers
    // =========================================================
    private boolean hasInvalidKeyChar(String key) {
        return TextUtils.isEmpty(key) || INVALID_KEY_CHARS.matcher(key).find();
    }

    @NonNull
    private String resolvePlateFromInput() {
        String raw = etPlate == null ? "" : etPlate.getText().toString().trim();
        if (TextUtils.isEmpty(raw)) return "";
        return raw.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    @NonNull
    private String normalizePlate(String plate) {
        if (TextUtils.isEmpty(plate)) return "";
        return plate.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private void toast(String s) {
        if (getContext() != null) Toast.makeText(getContext(), s, Toast.LENGTH_LONG).show();
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String safeStr(String s) { return s == null ? "" : s.trim(); }
    private static String getLower(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }

    private String mapFirebaseError(@NonNull Exception e) {
        String msg = e.getMessage() == null ? "Greška." : e.getMessage();
        String low = msg.toLowerCase(Locale.ROOT);
        if (low.contains("permission denied") || low.contains("permission_denied")) {
            return "Permission denied (Firebase rules). Provjeri rules za spaces/balances/parkingSessions.";
        }
        return "Greška: " + msg;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (tvStatus != null && statusTick != null) tvStatus.removeCallbacks(statusTick);
    }

    @Override
    public void onDestroyView() {
        stopStatusTicker(false);
        super.onDestroyView();
    }

    // =========================================================
    // Models
    // =========================================================
    static class LotItem {
        String id, name;
        double perHour = 0.0, perDay = 0.0;
        @NonNull @Override public String toString() { return name != null ? name : id; }
    }

    static class VehicleDisplay {
        String display;
        String plate;
        VehicleDisplay(String display, String plate) {
            this.display = display;
            this.plate = plate;
        }
    }

    static class SimpleTextWatcher implements android.text.TextWatcher {
        private final Runnable after;
        SimpleTextWatcher(Runnable after){ this.after = after; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(android.text.Editable s) { if (after != null) after.run(); }
    }
}
