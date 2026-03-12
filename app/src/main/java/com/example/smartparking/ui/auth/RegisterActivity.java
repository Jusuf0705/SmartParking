package com.example.smartparking.ui.auth;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.smartparking.data.FirebaseUtils;
import com.example.smartparking.databinding.ActivityRegisterBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Edge-to-edge — isti princip kao LoginActivity
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // WindowInsets — prostor za status bar (gore) i navigation bar (dole)
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() |
                            WindowInsetsCompat.Type.displayCutout()
            );
            v.setPadding(
                    v.getPaddingLeft(),
                    insets.top,
                    v.getPaddingRight(),
                    insets.bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });

        auth = FirebaseAuth.getInstance();

        // Back dugme — povrat na LoginActivity
        binding.btnBack.setOnClickListener(v -> finish());

        // Registracija
        binding.btnRegister.setOnClickListener(v -> attemptRegister());
    }

    private void attemptRegister() {
        final String first = safeText(binding.etFirstName);
        final String last  = safeText(binding.etLastName);
        final String email = safeText(binding.etEmail).toLowerCase(Locale.ROOT).trim();
        final String pass  = safeText(binding.etPassword);

        if (TextUtils.isEmpty(first)) { toast("Unesite ime");    return; }
        if (TextUtils.isEmpty(last))  { toast("Unesite prezime"); return; }
        if (!isValidEmail(email))     { toast("Unesite ispravan email"); return; }
        if (TextUtils.isEmpty(pass) || pass.length() < 6) {
            toast("Lozinka mora imati najmanje 6 karaktera");
            return;
        }

        setLoading(true);

        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(res -> {
                    final FirebaseUser user = res.getUser();
                    if (user == null) {
                        setLoading(false);
                        toast("Neočekivana greška: korisnik je null");
                        return;
                    }

                    final String uid = user.getUid();
                    final long now = System.currentTimeMillis();
                    final DatabaseReference root = FirebaseUtils.root();

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("/users/" + uid + "/firstName", first);
                    updates.put("/users/" + uid + "/lastName",  last);
                    updates.put("/users/" + uid + "/email",     email);
                    updates.put("/users/" + uid + "/createdAt", now);
                    updates.put("/balances/" + uid, 100.0);
                    updates.put("/roles/" + uid, "user");

                    root.updateChildren(updates)
                            .addOnSuccessListener(x -> {
                                setLoading(false);
                                toast("Registracija uspješna. Prijavite se.");
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                toast("Greška pri snimanju profila: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    toast("Greška: " + e.getMessage());
                });
    }

    // ─── Helpers ──────────────────────────────────────────────

    private String safeText(android.widget.EditText et) {
        return (et == null || et.getText() == null) ? "" : et.getText().toString().trim();
    }

    private boolean isValidEmail(String email) {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void setLoading(boolean loading) {
        binding.btnRegister.setEnabled(!loading);
        binding.btnBack.setEnabled(!loading);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}