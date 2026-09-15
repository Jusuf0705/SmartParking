package com.example.smartparking.ui.auth;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.smartparking.data.FirebaseUtils;
import com.example.smartparking.data.WelcomeBonus;
import com.example.smartparking.databinding.ActivityRegisterBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() |
                            WindowInsetsCompat.Type.displayCutout());
            v.setPadding(v.getPaddingLeft(), insets.top, v.getPaddingRight(), insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        auth = FirebaseAuth.getInstance();

        addCapitalizeWatcher(binding.etFirstName);
        addCapitalizeWatcher(binding.etLastName);

        binding.btnRegister.setOnClickListener(v -> attemptRegister());

        // "Already have an account?" — just closes back to LoginActivity
        binding.btnGoToLogin.setOnClickListener(v -> finish());
    }

    // -- Capitalize the first letter of each word --

    private void addCapitalizeWatcher(android.widget.EditText et) {
        et.addTextChangedListener(new TextWatcher() {
            boolean editing = false;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (editing) return;
                editing = true;
                String text = s.toString();
                StringBuilder result = new StringBuilder();
                boolean capNext = true;
                for (char c : text.toCharArray()) {
                    if (Character.isWhitespace(c)) { capNext = true; result.append(c); }
                    else if (capNext) { result.append(Character.toUpperCase(c)); capNext = false; }
                    else result.append(c);
                }
                String fixed = result.toString();
                if (!fixed.equals(text)) {
                    int sel = et.getSelectionEnd();
                    s.replace(0, s.length(), fixed);
                    et.setSelection(Math.min(sel, fixed.length()));
                }
                editing = false;
            }
        });
    }

    // -- Register --

    private void attemptRegister() {
        final String first   = safeText(binding.etFirstName);
        final String last    = safeText(binding.etLastName);
        final String email   = safeText(binding.etEmail).toLowerCase(Locale.ROOT);
        final String pass    = safeText(binding.etPassword);
        final String confirm = safeText(binding.etConfirmPassword);

        if (TextUtils.isEmpty(first)) {
            binding.etFirstName.setError("Unesite ime"); binding.etFirstName.requestFocus(); return;
        }
        if (TextUtils.isEmpty(last)) {
            binding.etLastName.setError("Unesite prezime"); binding.etLastName.requestFocus(); return;
        }
        if (!isValidEmail(email)) {
            binding.etEmail.setError("Unesite ispravan email"); binding.etEmail.requestFocus(); return;
        }
        if (pass.length() < 6) {
            binding.etPassword.setError("Minimum 6 karaktera"); binding.etPassword.requestFocus(); return;
        }
        if (!pass.equals(confirm)) {
            binding.etConfirmPassword.setError("Lozinke se ne podudaraju");
            binding.etConfirmPassword.requestFocus(); return;
        }

        setLoading(true);

        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(res -> {
                    final FirebaseUser user = res.getUser();
                    if (user == null) { setLoading(false); toast("Neočekivana greška."); return; }

                    final String uid = user.getUid();
                    final long   now = System.currentTimeMillis();

                    Map<String, Object> userData = new HashMap<>();
                    userData.put("firstName", first);
                    userData.put("lastName",  last);
                    userData.put("email",     email);
                    userData.put("createdAt", now);

                    // Written per node — security rules don't allow batch writes at root
                    FirebaseUtils.user(uid).updateChildren(userData)
                            .addOnSuccessListener(x1 -> {

                                FirebaseUtils.role(uid).setValue("user")
                                        .addOnSuccessListener(x2 -> {

                                            // Welcome bonus — 10 KM
                                            FirebaseUtils.balance(uid).setValue(WelcomeBonus.AMOUNT)
                                                    .addOnSuccessListener(x3 -> {
                                                        markBonusGranted(uid);
                                                        finishRegistration();
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        // Balance write failed, but the account exists —
                                                        // UserPayFragment's fallback will retry the bonus
                                                        finishRegistration();
                                                    });
                                        })
                                        .addOnFailureListener(e -> {
                                            setLoading(false);
                                            user.delete();
                                            toast("Greška postavljanja role: " + e.getMessage());
                                        });
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                user.delete();
                                toast("Greška snimanja profila: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("email address is already in use"))
                        msg = "Email adresa je već registrovana.";
                    else if (msg != null && msg.contains("badly formatted"))
                        msg = "Format email adrese nije ispravan.";
                    else if (msg != null && msg.contains("network"))
                        msg = "Greška mreže. Provjeri internet vezu.";
                    toast(msg != null ? msg : "Greška registracije.");
                });
    }

    private void finishRegistration() {
        setLoading(false);
        toast("Registracija uspješna! Prijavite se.");
        finish();
    }

    /**
     * Marks the welcome bonus as granted locally, but not yet shown to the
     * user — the "Welcome!" panel appears the first time UserPayFragment
     * opens. Must use the same SharedPreferences keys as {@link WelcomeBonus}.
     */
    private void markBonusGranted(String uid) {
        SharedPreferences p = getSharedPreferences("sp_welcome_bonus", MODE_PRIVATE);
        p.edit()
                .putBoolean("granted_"  + uid, true)
                .putBoolean("ui_shown_" + uid, false)
                .apply();
    }

    // -- Helpers --

    private String safeText(android.widget.EditText et) {
        return (et == null || et.getText() == null) ? "" : et.getText().toString().trim();
    }

    private boolean isValidEmail(String email) {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void setLoading(boolean loading) {
        binding.btnRegister.setEnabled(!loading);
        binding.btnRegister.setText(loading ? "Kreiranje..." : "Kreiraj nalog");
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