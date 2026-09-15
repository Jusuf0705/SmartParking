package com.example.smartparking.ui.user;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.smartparking.R;
import com.example.smartparking.ui.auth.LoginActivity;
import com.example.smartparking.ui.user.userSettings.UserSettingsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class UserPanel extends AppCompatActivity {

    private BottomNavigationView nav;
    private boolean loggingOut = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_panel);
        setTitle("Smart Parking");

        nav = findViewById(R.id.bottomNav);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (loggingOut) return;

                FragmentManager fm = getSupportFragmentManager();
                if (fm.getBackStackEntryCount() > 0) {
                    fm.popBackStack();
                } else {
                    finish();
                }
            }
        });

        nav.setOnItemSelectedListener(item -> {
            if (loggingOut) return false;

            int id = item.getItemId();

            if (id == R.id.tab_parking) {
                openRoot(new UserParkingFragment());
                return true;
            } else if (id == R.id.tab_pay) {
                openRoot(new UserPayFragment());
                return true;
            } else if (id == R.id.tab_history) {
                openRoot(new UserHistoryFragment());
                return true;
            } else if (id == R.id.tab_settings) {
                openRoot(new UserSettingsFragment());
                return true;
            }

            return false;
        });

        if (savedInstanceState == null) {
            nav.setSelectedItemId(R.id.tab_parking);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null && !loggingOut) {
            goToLoginClearingTask();
        }
    }

    private void openRoot(@NonNull Fragment fragment) {
        FragmentManager fm = getSupportFragmentManager();
        clearBackStackImmediate(fm);

        fm.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    public void doLogout() {
        if (loggingOut) return;
        loggingOut = true;

        if (nav != null) nav.setEnabled(false);
        FirebaseAuth.getInstance().signOut();

        clearBackStackImmediate(getSupportFragmentManager());

        goToLoginClearingTask();
    }

    /** Drops any back-stack fragments immediately, ignoring the case where the state is already gone. */
    private void clearBackStackImmediate(FragmentManager fm) {
        try {
            fm.executePendingTransactions();
            while (fm.getBackStackEntryCount() > 0) {
                fm.popBackStackImmediate();
            }
        } catch (IllegalStateException ignored) {}
    }

    private void goToLoginClearingTask() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }
}