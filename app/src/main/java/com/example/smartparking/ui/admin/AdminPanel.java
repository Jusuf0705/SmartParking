package com.example.smartparking.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartparking.ui.auth.LoginActivity;
import com.example.smartparking.R;
import com.google.firebase.auth.FirebaseAuth;

public class AdminPanel extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_panel);

        Button btnUsers   = findViewById(R.id.btnUsers);
        Button btnParking = findViewById(R.id.btnParking);
        Button btnFines   = findViewById(R.id.btnFines);
        Button btnSignOut = findViewById(R.id.btnSignOut);

        // ✅ Upravljanje korisnicima i ulogama
        btnUsers.setOnClickListener(v ->
                startActivity(new Intent(this, AdminManageAccountsActivity.class)));

        // ✅ Upravljanje parkingom
        btnParking.setOnClickListener(v ->
                startActivity(new Intent(this, ManageParkingActivity.class)));

        // ✅ Pregled kazni
        btnFines.setOnClickListener(v ->
                startActivity(new Intent(this, ManageFinesActivity.class)));

        // ✅ Odjava i povratak na LoginActivity
        btnSignOut.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();

            // Kreiraj intent za login
            Intent intent = new Intent(AdminPanel.this, LoginActivity.class);
            // Očisti prethodne aktivnosti tako da korisnik ne može “Back” da se vrati
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

            // Zatvori trenutni ekran
            finish();
        });
    }
}
