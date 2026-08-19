package com.example.smartparking.ui.user;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class UserProfileFragment extends Fragment {

    private static final long SAVED_STATE_DURATION_MS = 2000L;

    private MaterialButton btnBack;
    private EditText etFN, etLN, etEmailReadonly;
    private Button btnSave;
    private TextView tvHeaderInitials, tvHeaderName, tvHeaderEmail;
    private String uid;

    private DatabaseReference userRef;
    private ValueEventListener userListener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_user_profile, parent, false);

        // ✅ Back dugme iz headera
        btnBack = v.findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(vw -> goBackToSettings());
        }

        etFN = v.findViewById(R.id.etFirstName);
        etLN = v.findViewById(R.id.etLastName);
        etEmailReadonly = v.findViewById(R.id.etEmail);
        btnSave = v.findViewById(R.id.btnSave);

        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) {
            toast("Niste prijavljeni.");
            return v;
        }

        uid = me.getUid();
        if (etEmailReadonly != null) etEmailReadonly.setText(me.getEmail());
        if (tvHeaderEmail != null) tvHeaderEmail.setText(me.getEmail());

        userRef = FirebaseUtils.user(uid);

        // Kreiraj profil ako ne postoji
        ensureUserProfile(uid, me.getEmail());

        // Listener za profil
        userListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot u) {
                String first = u.child("firstName").getValue(String.class);
                String last  = u.child("lastName").getValue(String.class);

                if (etFN != null) etFN.setText(first == null ? "" : first);
                if (etLN != null) etLN.setText(last == null ? "" : last);

                // ── Header: ime + inicijali ──
                String fullName = ((first == null ? "" : first.trim()) + " "
                        + (last == null ? "" : last.trim())).trim();
                if (tvHeaderName != null)
                    tvHeaderName.setText(TextUtils.isEmpty(fullName) ? "" : fullName);

                String initials = "";
                if (!TextUtils.isEmpty(first)) initials += first.trim().substring(0, 1).toUpperCase();
                if (!TextUtils.isEmpty(last))  initials += last.trim().substring(0, 1).toUpperCase();
                if (TextUtils.isEmpty(initials) && me.getEmail() != null && !me.getEmail().isEmpty())
                    initials = me.getEmail().substring(0, 1).toUpperCase();
                if (tvHeaderInitials != null) tvHeaderInitials.setText(initials);
            }

            @Override public void onCancelled(@NonNull DatabaseError e) {
                toast("Greška čitanja: " + e.getMessage());
            }
        };
        userRef.addValueEventListener(userListener);

        // ✅ Otvori panel za promjenu lozinke
        View btnChangePassword = v.findViewById(R.id.btnChangePassword);
        if (btnChangePassword != null) {
            btnChangePassword.setOnClickListener(vw -> showChangePasswordSheet());
        }

        btnSave.setOnClickListener(vw -> doSave());

        return v;
    }

    // ✅ Vraćanje na UserSettingsActivity
    private void goBackToSettings() {
        requireActivity().getOnBackPressedDispatcher().onBackPressed();
    }

    private void ensureUserProfile(@NonNull String uid, @Nullable String email) {
        FirebaseUtils.user(uid).get()
                .addOnSuccessListener(snap -> {
                    if (!snap.exists()) {
                        Map<String, Object> userDoc = new HashMap<>();
                        userDoc.put("firstName", "");
                        userDoc.put("lastName", "");
                        userDoc.put("email", email == null ? "" : email);
                        userDoc.put("createdAt", System.currentTimeMillis());
                        FirebaseUtils.user(uid).setValue(userDoc);
                    }
                })
                .addOnFailureListener(e -> toast("Čitanje profila: " + e.getMessage()));
    }

    private void doSave() {
        String first = safeTrim(etFN == null ? null : etFN.getText());
        String last  = safeTrim(etLN == null ? null : etLN.getText());

        // Both fields are required
        if (TextUtils.isEmpty(first)) { if (etFN != null) etFN.requestFocus(); toast("Unesite ime."); return; }
        if (TextUtils.isEmpty(last))  { if (etLN != null) etLN.requestFocus(); toast("Unesite prezime."); return; }

        // Update user profile in a single write
        Map<String, Object> upd = new HashMap<>();
        upd.put("/users/" + uid + "/firstName", first);
        upd.put("/users/" + uid + "/lastName", last);

        FirebaseUtils.root().updateChildren(upd)
                .addOnSuccessListener(x -> showSavedAnimation())
                .addOnFailureListener(e -> toast("Greška: " + e.getMessage()));
    }

    // ── Bottom sheet za promjenu lozinke (bez zasebne klase) ──
    private void showChangePasswordSheet() {
        if (getContext() == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheet = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_change_password, null, false);
        dialog.setContentView(sheet);

        TextInputEditText etNewPass = sheet.findViewById(R.id.etSheetNewPassword);
        TextInputEditText etConfirmPass = sheet.findViewById(R.id.etSheetConfirmPassword);
        Button btnSheetSave = sheet.findViewById(R.id.btnSheetSave);
        Button btnSheetCancel = sheet.findViewById(R.id.btnSheetCancel);

        if (btnSheetCancel != null) btnSheetCancel.setOnClickListener(x -> dialog.dismiss());

        if (btnSheetSave != null) {
            btnSheetSave.setOnClickListener(x -> {
                String newPass = text(etNewPass);
                String confirm = text(etConfirmPass);

                if (TextUtils.isEmpty(newPass)) {
                    if (etNewPass != null) etNewPass.requestFocus();
                    toast("Unesite novu lozinku."); return;
                }
                if (newPass.length() < 6) {
                    if (etNewPass != null) etNewPass.requestFocus();
                    toast("Lozinka mora imati najmanje 6 znakova."); return;
                }
                if (!newPass.equals(confirm)) {
                    if (etConfirmPass != null) etConfirmPass.requestFocus();
                    toast("Lozinke se ne podudaraju."); return;
                }

                FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
                if (me == null) { toast("Niste prijavljeni."); return; }

                btnSheetSave.setEnabled(false);
                me.updatePassword(newPass)
                        .addOnSuccessListener(unused -> {
                            toast("Lozinka je uspješno promijenjena.");
                            dialog.dismiss();
                        })
                        .addOnFailureListener(e -> {
                            btnSheetSave.setEnabled(true);
                            if (e instanceof FirebaseAuthRecentLoginRequiredException) {
                                toast("Iz sigurnosnih razloga se odjavite pa ponovo prijavite, zatim pokušajte opet.");
                            } else {
                                toast("Greška: " + e.getMessage());
                            }
                        });
            });
        }

        dialog.show();
    }

    // ── Privremena vizuelna potvrda na Sačuvaj dugmetu ──
    private void showSavedAnimation() {
        if (btnSave == null || !isAdded()) return;

        btnSave.setText("Sačuvano!");
        btnSave.setBackgroundResource(R.drawable.bg_btn_save_success);
        btnSave.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_check_circle, 0, 0, 0);

        handler.postDelayed(() -> {
            if (!isAdded() || btnSave == null) return;
            btnSave.setText("Sačuvaj izmjene");
            btnSave.setBackgroundResource(R.drawable.bg_btn_save_primary);
            btnSave.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_zap, 0, 0, 0);
        }, SAVED_STATE_DURATION_MS);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (userRef != null && userListener != null) userRef.removeEventListener(userListener);
        handler.removeCallbacksAndMessages(null);
    }

    private String text(@Nullable TextInputEditText et) {
        return et == null || et.getText() == null ? "" : et.getText().toString().trim();
    }

    private String safeTrim(@Nullable CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }

    private void toast(String s) {
        if (getContext() != null) Toast.makeText(getContext(), s, Toast.LENGTH_LONG).show();
    }
}