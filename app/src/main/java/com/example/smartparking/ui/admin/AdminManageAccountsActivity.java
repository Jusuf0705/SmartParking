package com.example.smartparking.ui.admin;

import android.os.Bundle;
import android.text.TextUtils;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.example.smartparking.data.SecondaryAuth;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AdminManageAccountsActivity extends AppCompatActivity {

    // XML: spType je AutoCompleteTextView (dropdown), nije Spinner
    private AutoCompleteTextView spType;

    private Button btnAdd, btnRefresh;
    private RecyclerView rv;
    private AccountsAdapter adapter;

    private TextView tvEmpty;

    private DatabaseReference usersRef, rolesRef;

    private String currentType = "user"; // default

    private final String[] ROLE_OPTIONS = new String[]{"user", "kontrola"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_admin_manage_accounts);

        // Header back dugme (ako postoji u layoutu)
        MaterialButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        spType = findViewById(R.id.spType);
        btnAdd = findViewById(R.id.btnAdd);
        btnRefresh = findViewById(R.id.btnRefresh);
        rv = findViewById(R.id.recycler);

        // Empty text (overlay) - kao u tvom kodu
        tvEmpty = new TextView(this);
        tvEmpty.setText("Nema zapisa za odabrani tip.");
        tvEmpty.setPadding(24, 24, 24, 24);
        tvEmpty.setVisibility(View.GONE);
        ((ViewGroup) findViewById(android.R.id.content)).addView(tvEmpty);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AccountsAdapter();
        rv.setAdapter(adapter);

        usersRef = FirebaseUtils.usersRef();
        rolesRef = FirebaseUtils.rolesRef();

        // ===== Dropdown setup (AutoCompleteTextView) =====
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                ROLE_OPTIONS
        );
        spType.setAdapter(typeAdapter);

        // default vrijednost
        spType.setText(ROLE_OPTIONS[0], false);
        currentType = ROLE_OPTIONS[0];

        // kad korisnik izabere tip
        spType.setOnItemClickListener((parent, view, position, id) -> {
            String val = (String) parent.getItemAtPosition(position);
            currentType = normalizeRole(val);
            loadListForRole(currentType);
        });

        // inicijalni load
        loadListForRole(currentType);

        btnAdd.setOnClickListener(v -> showAddDialog());
        btnRefresh.setOnClickListener(v -> loadListForRole(currentType));
    }

    private String normalizeRole(String role) {
        if (role == null) return "user";
        String r = role.trim().toLowerCase(Locale.ROOT);
        if (!r.equals("user") && !r.equals("kontrola") && !r.equals("admin")) return "user";
        return r;
    }

    /** Učitaj sve naloge za izabrani tip (user/kontrola) */
    private void loadListForRole(@NonNull String roleValueRaw) {
        final String targetRole = normalizeRole(roleValueRaw);

        adapter.submit(Collections.emptyList());
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);

        rolesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot rolesSnap) {

                final Set<String> matchedUids = new HashSet<>();
                for (DataSnapshot r : rolesSnap.getChildren()) {
                    String uid = r.getKey();
                    String role = r.getValue(String.class);
                    if (uid == null || role == null) continue;

                    if (normalizeRole(role).equals(targetRole)) {
                        matchedUids.add(uid);
                    }
                }

                if (matchedUids.isEmpty()) {
                    adapter.submit(Collections.emptyList());
                    showEmpty("Nema zapisa za odabrani tip („" + targetRole + "”).");
                    return;
                }

                usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot usersSnap) {
                        List<AccountRow> list = new ArrayList<>();

                        for (DataSnapshot u : usersSnap.getChildren()) {
                            String uid = u.getKey();
                            if (uid == null || !matchedUids.contains(uid)) continue;

                            AccountRow row = new AccountRow();
                            row.uid = uid;
                            row.email = u.child("email").getValue(String.class);
                            row.firstName = u.child("firstName").getValue(String.class);
                            row.lastName = u.child("lastName").getValue(String.class);
                            list.add(row);
                        }

                        adapter.submit(list);

                        if (list.isEmpty()) {
                            showEmpty("Nema zapisa za odabrani tip („" + targetRole + "”).");
                        } else {
                            hideEmpty();
                        }

                        Toast.makeText(AdminManageAccountsActivity.this,
                                "Pronađeno: " + list.size() + " (" + targetRole + ")",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        toast(e.getMessage());
                        showEmpty("Greška: " + e.getMessage());
                    }
                });
            }

            @Override public void onCancelled(@NonNull DatabaseError e) {
                toast(e.getMessage());
                showEmpty("Greška: " + e.getMessage());
            }
        });
    }

    private void showEmpty(String text) {
        if (tvEmpty == null) return;
        tvEmpty.setText(text);
        tvEmpty.setVisibility(View.VISIBLE);
    }

    private void hideEmpty() {
        if (tvEmpty == null) return;
        tvEmpty.setVisibility(View.GONE);
    }

    // ===== Dodavanje, uređivanje, brisanje =====

    private void showAddDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_edit_account, null);

        TextView tvTitle = view.findViewById(R.id.tvRoleTitle);
        EditText etFN = view.findViewById(R.id.etFirstName);
        EditText etLN = view.findViewById(R.id.etLastName);
        EditText etEmail = view.findViewById(R.id.etEmail);
        EditText etPass = view.findViewById(R.id.etPassword);
        EditText etConfirm = view.findViewById(R.id.etConfirmPassword);

        tvTitle.setText(currentType.equals("user") ? "Novi USER" : "Novi KONTROLOR");

        if (etPass != null) {
            etPass.setVisibility(View.VISIBLE);
            etPass.setText("");
            etPass.setHint("Lozinka");
        }

        if (etConfirm != null) {
            etConfirm.setVisibility(View.VISIBLE);
            etConfirm.setText("");
        }

        new AlertDialog.Builder(this)
                .setTitle("Dodaj " + (currentType.equals("user") ? "usera" : "kontrolora"))
                .setView(view)
                // Napomena: PositiveButton automatski zatvara dialog,
                // ali zadržavam tvoju logiku kao i ranije.
                .setPositiveButton("Spasi", (d, w) -> {
                    String fn = etFN != null ? etFN.getText().toString().trim() : "";
                    String ln = etLN != null ? etLN.getText().toString().trim() : "";
                    String email = etEmail != null ? etEmail.getText().toString().trim() : "";
                    String pass = etPass != null ? etPass.getText().toString() : "";
                    String conf = etConfirm != null ? etConfirm.getText().toString() : "";

                    if (TextUtils.isEmpty(fn) || TextUtils.isEmpty(ln) || TextUtils.isEmpty(email) || TextUtils.isEmpty(pass)) {
                        toast("Ime, prezime, email i lozinka su obavezni.");
                        return;
                    }
                    if (pass.length() < 6) {
                        toast("Lozinka mora imati najmanje 6 znakova.");
                        return;
                    }
                    if (etConfirm != null && !TextUtils.isEmpty(conf) && !pass.equals(conf)) {
                        toast("Lozinke se ne podudaraju.");
                        return;
                    }

                    addAccount(fn, ln, email, pass);
                })
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void addAccount(String fn, String ln, String email, String pass) {
        FirebaseAuth sec = SecondaryAuth.get(this);

        sec.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(res -> {
                    if (res.getUser() == null) {
                        toast("Greška: korisnik nije kreiran.");
                        return;
                    }
                    String uid = res.getUser().getUid();

                    Map<String, Object> user = new HashMap<>();
                    user.put("email", email);
                    user.put("firstName", fn);
                    user.put("lastName", ln);

                    FirebaseUtils.user(uid).setValue(user);
                    FirebaseUtils.role(uid).setValue(currentType);
                    if (currentType.equals("user")) {
                        FirebaseUtils.balance(uid).setValue(0.0);
                    }

                    toast("Kreirano: " + email + " (" + currentType + ")");
                    loadListForRole(currentType);
                })
                .addOnFailureListener(e -> toast("Greška pri kreiranju naloga: " + e.getMessage()));
    }

    private void showEditDialog(AccountRow row) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_edit_account, null);

        TextView tvRole = view.findViewById(R.id.tvRoleTitle);
        EditText etFN = view.findViewById(R.id.etFirstName);
        EditText etLN = view.findViewById(R.id.etLastName);
        EditText etEmail = view.findViewById(R.id.etEmail);
        EditText etPass = view.findViewById(R.id.etPassword);
        EditText etConfirm = view.findViewById(R.id.etConfirmPassword);

        tvRole.setText(currentType.equals("user") ? "User" : "Kontrolor");

        if (etFN != null) etFN.setText(row.firstName);
        if (etLN != null) etLN.setText(row.lastName);

        if (etEmail != null) {
            etEmail.setText(row.email);
            etEmail.setEnabled(false);
        }

        if (etPass != null) {
            etPass.setVisibility(View.VISIBLE);
            etPass.setText("");
            etPass.setHint("Nova lozinka (opciono)");
        }

        if (etConfirm != null) {
            etConfirm.setVisibility(View.VISIBLE);
            etConfirm.setText("");
            etConfirm.setHint("Potvrdi novu lozinku");
        }

        new AlertDialog.Builder(this)
                .setTitle("Uredi " + (currentType.equals("user") ? "usera" : "kontrolora"))
                .setView(view)
                .setPositiveButton("Spasi", (d, w) -> {

                    Map<String, Object> upd = new HashMap<>();
                    upd.put("firstName", etFN != null ? etFN.getText().toString().trim() : "");
                    upd.put("lastName", etLN != null ? etLN.getText().toString().trim() : "");

                    FirebaseUtils.user(row.uid).updateChildren(upd)
                            .addOnSuccessListener(v1 -> {
                                toast("Spašeno.");
                                loadListForRole(currentType);
                            })
                            .addOnFailureListener(e -> toast("Greška: " + e.getMessage()));

                    String newPass = etPass != null ? etPass.getText().toString().trim() : "";
                    String conf = etConfirm != null ? etConfirm.getText().toString().trim() : "";

                    if (!TextUtils.isEmpty(newPass)) {
                        if (newPass.length() < 6) { toast("Nova lozinka mora imati najmanje 6 znakova."); return; }
                        if (etConfirm != null && !TextUtils.isEmpty(conf) && !newPass.equals(conf)) {
                            toast("Lozinke se ne podudaraju.");
                            return;
                        }

                        // Bez Admin SDK: šaljemo reset email
                        FirebaseAuth.getInstance().sendPasswordResetEmail(row.email)
                                .addOnSuccessListener(v2 -> toast("Poslan reset email na: " + row.email))
                                .addOnFailureListener(e -> toast("Ne mogu poslati reset email: " + e.getMessage()));
                    }
                })
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void confirmDelete(AccountRow row) {
        new AlertDialog.Builder(this)
                .setTitle("Brisanje")
                .setMessage("Obrisati " + (currentType.equals("user") ? "usera" : "kontrolora") + " " + row.email + " iz baze?")
                .setPositiveButton("Obriši", (d, w) -> {
                    FirebaseUtils.user(row.uid).removeValue();
                    FirebaseUtils.role(row.uid).removeValue();
                    FirebaseUtils.balance(row.uid).removeValue();
                    toast("Obrisan iz baze. (Auth nalog ostaje — treba Admin SDK/Cloud Function)");
                    loadListForRole(currentType);
                })
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    // ==== Model + Adapter ====

    static class AccountRow {
        String uid, email, firstName, lastName;
    }

    class AccountsAdapter extends RecyclerView.Adapter<AccountsAdapter.VH> {
        List<AccountRow> data = new ArrayList<>();

        void submit(List<AccountRow> d) {
            data = (d == null) ? new ArrayList<>() : d;
            notifyDataSetChanged();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView t1, t2;
            Button btnEdit, btnDelete;

            VH(@NonNull View v) {
                super(v);
                t1 = v.findViewById(R.id.rowTitle);
                t2 = v.findViewById(R.id.rowSubtitle);
                btnEdit = v.findViewById(R.id.btnEdit);
                btnDelete = v.findViewById(R.id.btnDelete);

                View btnRole = v.findViewById(R.id.btnRole);
                if (btnRole != null) btnRole.setVisibility(View.GONE);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_three_actions, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            AccountRow it = data.get(pos);

            String fn = it.firstName == null ? "" : it.firstName;
            String ln = it.lastName == null ? "" : it.lastName;
            String title = (fn + " " + ln).trim();
            h.t1.setText(title.isEmpty() ? "(Bez imena)" : title);

            h.t2.setText(it.email == null ? "" : it.email);

            h.btnEdit.setOnClickListener(v -> showEditDialog(it));
            h.btnDelete.setOnClickListener(v -> confirmDelete(it));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }
}
