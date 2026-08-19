package com.example.smartparking.ui.user.userSettings;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.example.smartparking.ui.auth.LoginActivity;
import com.example.smartparking.ui.user.UserProfileFragment;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;

public class UserSettingsFragment extends Fragment {

    private TextView tvInitials, tvName, tvEmail;
    private TextView tvBalance, tvTopUpSubtitle, tvVehiclesSubtitle;

    @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup parent,
                             @Nullable Bundle b) {

        View v = inf.inflate(R.layout.fragment_user_settings, parent, false);

        tvInitials = v.findViewById(R.id.tvProfileInitials);
        tvName     = v.findViewById(R.id.tvProfileName);
        tvEmail    = v.findViewById(R.id.tvProfileEmail);

        tvBalance          = v.findViewById(R.id.tvProfileBalance);
        tvTopUpSubtitle    = v.findViewById(R.id.tvTopUpSubtitle);
        tvVehiclesSubtitle = v.findViewById(R.id.tvVehiclesSubtitle);

        View btnProfile   = v.findViewById(R.id.btnProfile);
        View btnTopUp     = v.findViewById(R.id.btnTopUp);
        View btnVehicles  = v.findViewById(R.id.btnVehicles);
        View menuTerms    = v.findViewById(R.id.menuTerms);
        MaterialButton btnLogOut = v.findViewById(R.id.btnLogOut);

        loadUserProfile();
        loadBalance();
        loadVehicleCount();

        View.OnClickListener openProfile = view ->
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragmentContainer, new UserProfileFragment())
                        .addToBackStack(null)
                        .commit();

        if (btnProfile != null) btnProfile.setOnClickListener(openProfile);

        if (btnTopUp != null) btnTopUp.setOnClickListener(view -> {
            Intent i = new Intent(requireContext(), TopUpActivity.class);
            startActivity(i);
        });

        if (btnVehicles != null) btnVehicles.setOnClickListener(view -> {
            Intent i = new Intent(requireContext(), VehiclesActivity.class);
            startActivity(i);
        });

        // Placeholder — zamijeni sa stvarnim ekranom/URL-om za uslove korištenja kad bude spreman
        if (menuTerms != null) menuTerms.setOnClickListener(view -> {
            if (!isAdded()) return;
            new AlertDialog.Builder(requireContext())
                    .setTitle("Uvjeti korištenja")
                    .setMessage("Uvjeti korištenja aplikacije Smart Parking će uskoro biti dostupni ovdje.")
                    .setPositiveButton("Zatvori", null)
                    .show();
        });

        if (btnLogOut != null) btnLogOut.setOnClickListener(view -> {
            FirebaseAuth.getInstance().signOut();
            Intent i = new Intent(requireContext(), LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            requireActivity().finish();
        });

        return v;
    }

    private void loadUserProfile() {
        FirebaseUser fu = FirebaseAuth.getInstance().getCurrentUser();
        if (fu == null) return;

        // Email odmah
        String email = fu.getEmail();
        if (tvEmail != null && !TextUtils.isEmpty(email))
            tvEmail.setText(email);

        // Ime + prezime iz /users/{uid}
        FirebaseUtils.root().child("users").child(fu.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ds) {
                        if (!isAdded()) return;

                        String firstName = ds.child("firstName").getValue(String.class);
                        String lastName  = ds.child("lastName").getValue(String.class);

                        // Fallback na displayName
                        if (TextUtils.isEmpty(firstName) && !TextUtils.isEmpty(fu.getDisplayName())) {
                            String[] parts = fu.getDisplayName().trim().split(" ", 2);
                            firstName = parts[0];
                            lastName  = parts.length > 1 ? parts[1] : "";
                        }

                        String first    = TextUtils.isEmpty(firstName) ? "" : firstName.trim();
                        String last     = TextUtils.isEmpty(lastName)  ? "" : lastName.trim();
                        String fullName = (first + " " + last).trim();

                        if (tvName != null && !TextUtils.isEmpty(fullName))
                            tvName.setText(fullName);

                        // Inicijali
                        String initials = "";
                        if (!TextUtils.isEmpty(first)) initials += first.substring(0, 1).toUpperCase();
                        if (!TextUtils.isEmpty(last))  initials += last.substring(0, 1).toUpperCase();
                        if (TextUtils.isEmpty(initials) && !TextUtils.isEmpty(email))
                            initials = email.substring(0, 1).toUpperCase();

                        if (tvInitials != null) tvInitials.setText(initials);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    // ── Balans — prikazuje se i u hero kartici i kao podnaslov "Dokupi kredit" ──
    private void loadBalance() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        FirebaseUtils.balance(uid).addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot s) {
                if (!isAdded()) return;
                Double bal = s.getValue(Double.class);
                String formatted = String.format(Locale.getDefault(), "%.2f KM", bal == null ? 0.0 : bal);
                if (tvBalance != null) tvBalance.setText(formatted);
                if (tvTopUpSubtitle != null) tvTopUpSubtitle.setText("Stanje: " + formatted);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    // ── Broj registrovanih vozila — podnaslov "Upravljanje vozilima" ──
    private void loadVehicleCount() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        FirebaseUtils.userVehicles(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                if (!isAdded() || tvVehiclesSubtitle == null) return;
                long count = ds.getChildrenCount();
                tvVehiclesSubtitle.setText(count + " " + vehicleWord(count) + " registrovano");
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    // Jednostavna BHS pluralizacija: 1 vozilo, 2-4 vozila, 5+ vozila
    private static String vehicleWord(long count) {
        long mod10 = count % 10;
        long mod100 = count % 100;
        if (count == 1) return "vozilo";
        if (mod10 >= 2 && mod10 <= 4 && !(mod100 >= 12 && mod100 <= 14)) return "vozila";
        return "vozila";
    }
}