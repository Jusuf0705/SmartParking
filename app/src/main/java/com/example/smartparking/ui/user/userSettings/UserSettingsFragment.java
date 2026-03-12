package com.example.smartparking.ui.user.userSettings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smartparking.R;
import com.example.smartparking.ui.auth.LoginActivity;
import com.example.smartparking.ui.user.UserProfileFragment;
import com.google.firebase.auth.FirebaseAuth;

public class UserSettingsFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup parent,
                             @Nullable Bundle b) {

        View v = inf.inflate(R.layout.fragment_user_settings, parent, false);

        Button btnProfile  = v.findViewById(R.id.btnProfile);
        Button btnTopUp    = v.findViewById(R.id.btnTopUp);
        Button btnVehicles = v.findViewById(R.id.btnVehicles);
        Button btnLogOut   = v.findViewById(R.id.btnLogOut);

        // ✅ Profil (fragment)
        btnProfile.setOnClickListener(view ->
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragmentContainer, new UserProfileFragment())
                        .addToBackStack(null)
                        .commit()
        );

        // ✅ Dokupi kredit (activity)
        btnTopUp.setOnClickListener(view -> {
            Intent i = new Intent(requireContext(), TopUpActivity.class);
            startActivity(i);
        });

        // ✅ Upravljanje vozilima (activity)
        btnVehicles.setOnClickListener(view -> {
            Intent i = new Intent(requireContext(), VehiclesActivity.class);
            startActivity(i);
        });

        // ✅ Logout (na login + clear back stack)
        btnLogOut.setOnClickListener(view -> {
            FirebaseAuth.getInstance().signOut();

            Intent i = new Intent(requireContext(), LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);

            // dodatno osiguranje da se trenutna aktivnost zatvori
            requireActivity().finish();
        });

        return v;
    }
}
