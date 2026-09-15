package com.example.smartparking.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.smartparking.data.FirebaseUtils;
import com.example.smartparking.databinding.ActivityLoginBinding;
import com.example.smartparking.ui.admin.AdminPanel;
import com.example.smartparking.ui.admin.ControlPanelActivity;
import com.example.smartparking.ui.user.UserPanel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;

import java.util.Locale;

public class LoginActivity extends AppCompatActivity {

    private static final boolean AUTO_REDIRECT_AFTER_COLD_START = false;

    private ActivityLoginBinding binding;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Edge-to-edge: content draws behind the status/nav bars, so we add
        // matching padding to the root view below
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Pad the root view so content clears the status bar (top) and nav bar (bottom)
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

        setupClickListeners();
    }

    private void setupClickListeners() {

        binding.btnLogin.setOnClickListener(v -> {
            hideKeyboard(v);

            String email = safeText(binding.etEmail).toLowerCase(Locale.ROOT).trim();
            String pass  = safeText(binding.etPassword);

            if (!isValidEmail(email)) {
                binding.etEmail.setError("Unesite ispravan email");
                binding.etEmail.requestFocus();
                return;
            }
            if (TextUtils.isEmpty(pass)) {
                binding.etPassword.setError("Unesite lozinku");
                binding.etPassword.requestFocus();
                return;
            }

            setLoading(true);

            auth.signInWithEmailAndPassword(email, pass)
                    .addOnSuccessListener(res -> {
                        FirebaseUser u = res.getUser();
                        if (u != null) {
                            ensureUserRoleAndRoute(u.getUid());
                        } else {
                            toast("Greška: korisnik je null");
                            setLoading(false);
                        }
                    })
                    .addOnFailureListener(e -> {
                        toast("Prijava nije uspjela: " + e.getMessage());
                        setLoading(false);
                    });
        });

        binding.btnGoRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class))
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (AUTO_REDIRECT_AFTER_COLD_START) {
            FirebaseUser u = auth.getCurrentUser();
            if (u != null) {
                setLoading(true);
                ensureUserRoleAndRoute(u.getUid());
            }
        }
    }

    /**
     * Writes "user" to /roles/{uid} if it doesn't exist yet,
     * then reads the role and routes to the matching screen.
     */
    private void ensureUserRoleAndRoute(@NonNull String uid) {
        FirebaseUtils.role(uid).get()
                .addOnSuccessListener((DataSnapshot snap) -> {
                    if (!snap.exists()) {
                        FirebaseUtils.role(uid).setValue("user")
                                .addOnSuccessListener(x -> routeByRoleString("user"))
                                .addOnFailureListener(err -> {
                                    toast("Greška prilikom dodjeljivanja uloge");
                                    routeByRoleString("user");
                                });
                    } else {
                        String role = snap.getValue(String.class);
                        if (role == null) role = "user";
                        routeByRoleString(role.trim().toLowerCase(Locale.ROOT));
                    }
                })
                .addOnFailureListener(err -> {
                    toast("Greška prilikom učitavanja uloge");
                    routeByRoleString("user");
                });
    }

    /** Routes to the right screen based on the role string. */
    private void routeByRoleString(@NonNull String role) {
        setLoading(false);

        Class<?> target;
        switch (role) {
            case "admin":
                target = AdminPanel.class;
                break;
            case "kontrola":
                target = ControlPanelActivity.class;
                break;
            case "user":
            default:
                target = UserPanel.class;
                break;
        }

        navigateAndFinish(target);
    }

    private void navigateAndFinish(Class<?> target) {
        Intent i = new Intent(this, target);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    // -- Helpers --

    private static boolean isValidEmail(String email) {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private static String safeText(android.widget.EditText et) {
        return et.getText() == null ? "" : et.getText().toString();
    }

    private void hideKeyboard(View v) {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && v != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    private void setLoading(boolean loading) {
        binding.btnLogin.setEnabled(!loading);
        binding.btnGoRegister.setEnabled(!loading);
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