package com.example.smartparking.ui.user;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class UserProfileFragment extends Fragment {

    private MaterialButton btnBack;
    private EditText etFN, etLN, etEmailReadonly, etNewPass, etConfirmPass;
    private Button btnSave;
    private String uid;

    private DatabaseReference userRef;
    private ValueEventListener userListener;

    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_user_profile, parent, false);

        // ✅ Back dugme iz headera (iz tvog XML-a)
        btnBack = v.findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(vw -> goBackToSettings());
        }

        etFN = v.findViewById(R.id.etFirstName);
        etLN = v.findViewById(R.id.etLastName);
        etEmailReadonly = v.findViewById(R.id.etEmail);
        etNewPass = v.findViewById(R.id.etNewPassword);
        etConfirmPass = v.findViewById(R.id.etConfirmPassword);
        btnSave = v.findViewById(R.id.btnSave);

        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) {
            toast("Niste prijavljeni.");
            return v;
        }

        uid = me.getUid();
        if (etEmailReadonly != null) etEmailReadonly.setText(me.getEmail());

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
            }

            @Override public void onCancelled(@NonNull DatabaseError e) {
                toast("Greška čitanja: " + e.getMessage());
            }
        };
        userRef.addValueEventListener(userListener);

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
        String newPass  = safeTrim(etNewPass == null ? null : etNewPass.getText());
        String confirm  = safeTrim(etConfirmPass == null ? null : etConfirmPass.getText());

        if (TextUtils.isEmpty(first)) { if (etFN != null) etFN.requestFocus(); toast("Unesite ime."); return; }
        if (TextUtils.isEmpty(last))  { if (etLN != null) etLN.requestFocus(); toast("Unesite prezime."); return; }

        Map<String, Object> upd = new HashMap<>();
        upd.put("/users/" + uid + "/firstName", first);
        upd.put("/users/" + uid + "/lastName", last);

        FirebaseUtils.root().updateChildren(upd)
                .addOnSuccessListener(x -> {
                    if (!TextUtils.isEmpty(newPass) || !TextUtils.isEmpty(confirm)) {
                        changePassword(newPass, confirm);
                    } else {
                        toast("Sačuvano.");
                    }
                })
                .addOnFailureListener(e -> toast("Greška: " + e.getMessage()));
    }

    private void changePassword(String newPass, String confirm) {
        if (TextUtils.isEmpty(newPass)) { if (etNewPass != null) etNewPass.requestFocus(); toast("Unesite novu lozinku ili ostavite prazno."); return; }
        if (!newPass.equals(confirm))   { if (etConfirmPass != null) etConfirmPass.requestFocus(); toast("Lozinke se ne podudaraju."); return; }
        if (newPass.length() < 6)       { if (etNewPass != null) etNewPass.requestFocus(); toast("Lozinka mora imati najmanje 6 znakova."); return; }

        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) { toast("Niste prijavljeni."); return; }

        me.updatePassword(newPass)
                .addOnSuccessListener(v -> {
                    if (etNewPass != null) etNewPass.setText("");
                    if (etConfirmPass != null) etConfirmPass.setText("");
                    toast("Lozinka promijenjena.");
                })
                .addOnFailureListener(e -> toast("Lozinka nije promijenjena: " + e.getMessage()));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (userRef != null && userListener != null) userRef.removeEventListener(userListener);
    }

    private String safeTrim(@Nullable CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }

    private void toast(String s) {
        if (getContext() != null) Toast.makeText(getContext(), s, Toast.LENGTH_LONG).show();
    }
}
