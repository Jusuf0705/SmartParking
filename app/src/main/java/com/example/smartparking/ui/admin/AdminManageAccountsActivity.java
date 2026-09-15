package com.example.smartparking.ui.admin;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.example.smartparking.data.SecondaryAuth;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminManageAccountsActivity extends AppCompatActivity {

    private enum FilterMode { ALL, USER, KONTROLOR }

    private FilterMode currentFilter = FilterMode.ALL;
    private String searchQuery = "";

    private MaterialButton btnBack, btnAdd, btnSearch;
    private TextView tvCountUsers, tvCountKontrolori, tvListHeader;

    private AppCompatButton tabAll, tabUser, tabKontrolor;

    private View searchCard;
    private TextInputEditText etSearch;

    private RecyclerView rv;
    private AccountsAdapter adapter;

    private DatabaseReference usersRef, rolesRef;

    private final List<AccountRow> allAccounts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_admin_manage_accounts);

        btnBack           = findViewById(R.id.btnBack);
        btnAdd            = findViewById(R.id.btnAdd);
        btnSearch         = findViewById(R.id.btnSearch);
        tvCountUsers      = findViewById(R.id.tvCountUsers);
        tvCountKontrolori = findViewById(R.id.tvCountKontrolori);
        tvListHeader      = findViewById(R.id.tvListHeader);
        tabAll            = findViewById(R.id.tabAll);
        tabUser           = findViewById(R.id.tabUser);
        tabKontrolor      = findViewById(R.id.tabKontrolor);
        searchCard        = findViewById(R.id.searchCard);
        etSearch          = findViewById(R.id.etSearch);
        rv                = findViewById(R.id.recycler);

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        if (btnAdd  != null) btnAdd.setOnClickListener(v -> showPickRoleFirst());

        btnSearch.setOnClickListener(v -> toggleSearchCard());

        tabAll.setOnClickListener(v       -> selectTab(FilterMode.ALL));
        tabUser.setOnClickListener(v      -> selectTab(FilterMode.USER));
        tabKontrolor.setOnClickListener(v -> selectTab(FilterMode.KONTROLOR));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                searchQuery = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }
        });

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AccountsAdapter();
        rv.setAdapter(adapter);

        usersRef = FirebaseUtils.usersRef();
        rolesRef = FirebaseUtils.rolesRef();

        selectTab(FilterMode.ALL);
        loadAllAccounts();
    }

    private void toggleSearchCard() {
        if (searchCard == null) return;
        boolean visible = searchCard.getVisibility() == View.VISIBLE;
        searchCard.setVisibility(visible ? View.GONE : View.VISIBLE);
        if (!visible && etSearch != null) {
            etSearch.requestFocus();
        } else {
            if (etSearch != null) etSearch.setText("");
            searchQuery = "";
            applyFilter();
        }
    }

    // -- Tabs --

    private void selectTab(FilterMode mode) {
        currentFilter = mode;

        AppCompatButton[] tabs = {tabAll, tabUser, tabKontrolor};
        FilterMode[] modes = {FilterMode.ALL, FilterMode.USER, FilterMode.KONTROLOR};

        for (int i = 0; i < tabs.length; i++) {
            boolean active = (modes[i] == mode);
            tabs[i].setBackgroundResource(active
                    ? R.drawable.bg_btn_active_primary
                    : android.R.color.transparent);
            tabs[i].setTextColor(ContextCompat.getColor(this,
                    active ? R.color.white : R.color.muted_foreground));
        }

        applyFilter();
    }

    private void applyFilter() {
        List<AccountRow> filtered = new ArrayList<>();
        for (AccountRow r : allAccounts) {
            boolean roleMatch;
            switch (currentFilter) {
                case USER:      roleMatch = "user".equals(r.role); break;
                case KONTROLOR: roleMatch = "kontrolor".equals(r.role); break;
                default:        roleMatch = true;
            }
            if (!roleMatch) continue;

            if (!searchQuery.isEmpty()) {
                String haystack = ((r.firstName == null ? "" : r.firstName) + " "
                        + (r.lastName == null ? "" : r.lastName) + " "
                        + (r.email == null ? "" : r.email))
                        .toLowerCase(Locale.ROOT);
                if (!haystack.contains(searchQuery)) continue;
            }

            filtered.add(r);
        }
        adapter.submit(filtered);

        if (tvListHeader != null) {
            int n = filtered.size();
            String suffix = (n == 1) ? " NALOG" : " NALOGA";
            tvListHeader.setText(n + suffix);
        }
    }

    // -- Load accounts (admins are excluded from the list) --

    private void loadAllAccounts() {
        rolesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot rolesSnap) {

                final Map<String, String> uidToRole = new HashMap<>();
                for (DataSnapshot r : rolesSnap.getChildren()) {
                    String uid  = r.getKey();
                    String role = r.getValue(String.class);
                    if (uid == null) continue;
                    uidToRole.put(uid, normalizeRole(role));
                }

                usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot usersSnap) {
                        allAccounts.clear();

                        int countUsers = 0, countKontrolori = 0;

                        for (DataSnapshot u : usersSnap.getChildren()) {
                            String uid = u.getKey();
                            if (uid == null) continue;

                            String role = uidToRole.getOrDefault(uid, "user");
                            if ("admin".equals(role)) continue;

                            AccountRow row = new AccountRow();
                            row.uid       = uid;
                            row.email     = u.child("email").getValue(String.class);
                            row.firstName = u.child("firstName").getValue(String.class);
                            row.lastName  = u.child("lastName").getValue(String.class);
                            row.role      = role;

                            allAccounts.add(row);

                            if ("user".equals(role)) countUsers++;
                            else if ("kontrolor".equals(role)) countKontrolori++;
                        }

                        Collections.sort(allAccounts, (a, bb) -> {
                            String an = (a.firstName == null ? "" : a.firstName).toLowerCase(Locale.ROOT);
                            String bn = (bb.firstName == null ? "" : bb.firstName).toLowerCase(Locale.ROOT);
                            if (an.isEmpty() && !bn.isEmpty()) return 1;
                            if (!an.isEmpty() && bn.isEmpty()) return -1;
                            return an.compareTo(bn);
                        });

                        if (tvCountUsers      != null) tvCountUsers.setText(String.valueOf(countUsers));
                        if (tvCountKontrolori != null) tvCountKontrolori.setText(String.valueOf(countKontrolori));

                        applyFilter();
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) { toast(e.getMessage()); }
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError e) { toast(e.getMessage()); }
        });
    }

    /** DB -> UI: "kontrola" becomes "kontrolor" for display. */
    private String normalizeRole(String role) {
        if (role == null) return "user";
        String r = role.trim().toLowerCase(Locale.ROOT);
        if (r.equals("kontrola")) return "kontrolor";
        if (!r.equals("user") && !r.equals("kontrolor") && !r.equals("admin")) return "user";
        return r;
    }

    /** UI -> DB: "kontrolor" is stored as "kontrola". */
    private String roleForDb(String uiRole) {
        if (uiRole == null) return "user";
        String r = uiRole.trim().toLowerCase(Locale.ROOT);
        if (r.equals("kontrolor") || r.equals("kontrola")) return "kontrola";
        if (r.equals("admin")) return "admin";
        return "user";
    }

    /** Full name for display, trimmed, falling back to an empty string. */
    private static String fullName(AccountRow row) {
        return ((row.firstName == null ? "" : row.firstName) + " "
                + (row.lastName == null ? "" : row.lastName)).trim();
    }

    // -- Actions bottom sheet --

    private void showUserActionsDialog(AccountRow row) {
        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this)
                .inflate(R.layout.dialog_user_actions, null, false);
        dlg.setContentView(v);

        View     vAvatarBg     = v.findViewById(R.id.vDialogAvatarBg);
        TextView tvAvatar      = v.findViewById(R.id.tvDialogAvatar);
        TextView tvName        = v.findViewById(R.id.tvDialogName);
        TextView tvEmail       = v.findViewById(R.id.tvDialogEmail);
        TextView tvCurrentRole = v.findViewById(R.id.tvCurrentRole);

        String fullName = fullName(row);
        tvName.setText(TextUtils.isEmpty(fullName) ? "(Bez imena)" : fullName);
        tvEmail.setText(row.email == null ? "" : row.email);
        tvAvatar.setText(initialsFor(row.firstName, row.lastName, row.email));
        tvCurrentRole.setText("Trenutno: " + row.role);

        int avatarBgRes;
        int avatarTextColor;
        if ("kontrolor".equals(row.role)) {
            avatarBgRes     = R.drawable.bg_avatar_amber;
            avatarTextColor = ContextCompat.getColor(this, R.color.amber_700);
        } else {
            avatarBgRes     = R.drawable.bg_avatar_blue;
            avatarTextColor = ContextCompat.getColor(this, R.color.blue_600);
        }
        vAvatarBg.setBackgroundResource(avatarBgRes);
        tvAvatar.setTextColor(avatarTextColor);

        View rowEdit   = v.findViewById(R.id.rowEditProfile);
        View rowRole   = v.findViewById(R.id.rowChangeRole);
        View rowDelete = v.findViewById(R.id.rowDelete);
        AppCompatButton btnCancel = v.findViewById(R.id.btnCancel);

        rowEdit.setOnClickListener(x -> {
            dlg.dismiss();
            showEditDialog(row);
        });
        rowRole.setOnClickListener(x -> {
            dlg.dismiss();
            showToggleRoleDialog(row);
        });
        rowDelete.setOnClickListener(x -> {
            dlg.dismiss();
            showDeleteConfirmDialog(row);
        });
        btnCancel.setOnClickListener(x -> dlg.dismiss());

        dlg.show();
    }

    // -- Change role --

    private void showToggleRoleDialog(AccountRow row) {
        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this)
                .inflate(R.layout.dialog_pick_role, null, false);
        dlg.setContentView(v);

        TextView tvSubtitle = v.findViewById(R.id.tvPickRoleSubtitle);
        if (tvSubtitle != null) {
            String display = TextUtils.isEmpty(fullName(row)) ? (row.email == null ? "" : row.email) : fullName(row);
            tvSubtitle.setText("Promijeni ulogu za: " + display + "\nTrenutno: " + row.role);
        }

        View pickUser      = v.findViewById(R.id.pickRoleUser);
        View pickKontrolor = v.findViewById(R.id.pickRoleKontrolor);
        AppCompatButton btnCancel = v.findViewById(R.id.btnPickRoleCancel);

        pickUser.setOnClickListener(x -> {
            dlg.dismiss();
            changeRole(row, "user");
        });
        pickKontrolor.setOnClickListener(x -> {
            dlg.dismiss();
            changeRole(row, "kontrolor");
        });
        btnCancel.setOnClickListener(x -> dlg.dismiss());

        dlg.show();
    }

    private void changeRole(AccountRow row, String newRole) {
        if (newRole.equals(row.role)) {
            toast("Uloga nije promijenjena.");
            return;
        }
        // Stored as "kontrola" in the DB, shown as "kontrolor" in the UI
        FirebaseUtils.role(row.uid).setValue(roleForDb(newRole))
                .addOnSuccessListener(x -> {
                    toast("Uloga promijenjena u: " + newRole);
                    loadAllAccounts();
                })
                .addOnFailureListener(e -> toast("Greška: " + e.getMessage()));
    }

    // -- Add flow --

    private void showAddDialog(String role) {
        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_add_edit_user, null, false);
        dlg.setContentView(view);

        TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        if (tvTitle != null) {
            tvTitle.setText("kontrolor".equals(role) ? "Dodaj kontrolora" : "Dodaj korisnika");
        }

        EditText etFN      = view.findViewById(R.id.etFirstName);
        EditText etLN      = view.findViewById(R.id.etLastName);
        EditText etEmail   = view.findViewById(R.id.etEmail);
        EditText etPass    = view.findViewById(R.id.etPassword);
        EditText etConfirm = view.findViewById(R.id.etConfirmPassword);

        AppCompatButton btnSave   = view.findViewById(R.id.btnSave);
        AppCompatButton btnCancel = view.findViewById(R.id.btnCancel);

        btnSave.setText("Kreiraj nalog");

        btnSave.setOnClickListener(x -> {
            String fn    = etFN      != null ? etFN.getText().toString().trim()    : "";
            String ln    = etLN      != null ? etLN.getText().toString().trim()    : "";
            String email = etEmail   != null ? etEmail.getText().toString().trim() : "";
            String pass  = etPass    != null ? etPass.getText().toString()         : "";
            String conf  = etConfirm != null ? etConfirm.getText().toString()      : "";

            if (TextUtils.isEmpty(fn) || TextUtils.isEmpty(ln)
                    || TextUtils.isEmpty(email) || TextUtils.isEmpty(pass)) {
                toast("Ime, prezime, email i lozinka su obavezni.");
                return;
            }
            if (pass.length() < 6) {
                toast("Lozinka mora imati najmanje 6 znakova.");
                return;
            }
            if (!TextUtils.isEmpty(conf) && !pass.equals(conf)) {
                toast("Lozinke se ne podudaraju.");
                return;
            }

            dlg.dismiss();
            addAccount(fn, ln, email, pass, role); // role already chosen in the previous step
        });

        btnCancel.setOnClickListener(x -> dlg.dismiss());

        dlg.show();
    }

    private void showPickRoleFirst() {
        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this)
                .inflate(R.layout.dialog_pick_role, null, false);
        dlg.setContentView(v);

        TextView tvSubtitle = v.findViewById(R.id.tvPickRoleSubtitle);
        if (tvSubtitle != null) tvSubtitle.setText("Odaberite tip naloga koji dodajete:");

        View pickUser      = v.findViewById(R.id.pickRoleUser);
        View pickKontrolor = v.findViewById(R.id.pickRoleKontrolor);
        AppCompatButton btnCancel = v.findViewById(R.id.btnPickRoleCancel);

        pickUser.setOnClickListener(x -> {
            dlg.dismiss();
            showAddDialog("user");
        });
        pickKontrolor.setOnClickListener(x -> {
            dlg.dismiss();
            showAddDialog("kontrolor");
        });
        btnCancel.setOnClickListener(x -> dlg.dismiss());

        dlg.show();
    }

    private void addAccount(String fn, String ln, String email, String pass, String role) {
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
                    FirebaseUtils.role(uid).setValue(roleForDb(role));
                    if (role.equals("user")) {
                        FirebaseUtils.balance(uid).setValue(0.0);
                    }

                    // Sign out the secondary instance so it doesn't stay logged in as the new user
                    try { sec.signOut(); } catch (Exception ignored) {}

                    toast("Kreirano: " + email + " (" + role + ")");
                    loadAllAccounts();
                })
                .addOnFailureListener(e -> toast("Greška pri kreiranju naloga: " + e.getMessage()));
    }

    // -- Edit profile --

    private void showEditDialog(AccountRow row) {
        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_add_edit_user, null, false);
        dlg.setContentView(view);

        TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        if (tvTitle != null) tvTitle.setText("Uredi nalog");

        EditText etFN      = view.findViewById(R.id.etFirstName);
        EditText etLN      = view.findViewById(R.id.etLastName);
        EditText etEmail   = view.findViewById(R.id.etEmail);
        EditText etPass    = view.findViewById(R.id.etPassword);
        EditText etConfirm = view.findViewById(R.id.etConfirmPassword);

        if (etFN    != null) etFN.setText(row.firstName);
        if (etLN    != null) etLN.setText(row.lastName);
        if (etEmail != null) { etEmail.setText(row.email); etEmail.setEnabled(false); }
        if (etPass    != null) etPass.setHint("Nova lozinka (opciono)");
        if (etConfirm != null) etConfirm.setHint("Potvrdi novu lozinku");

        AppCompatButton btnSave   = view.findViewById(R.id.btnSave);
        AppCompatButton btnCancel = view.findViewById(R.id.btnCancel);

        btnSave.setText("Spasi");

        btnSave.setOnClickListener(x -> {
            Map<String, Object> upd = new HashMap<>();
            upd.put("firstName", etFN != null ? etFN.getText().toString().trim() : "");
            upd.put("lastName",  etLN != null ? etLN.getText().toString().trim() : "");

            FirebaseUtils.user(row.uid).updateChildren(upd)
                    .addOnSuccessListener(v1 -> {
                        toast("Spašeno.");
                        loadAllAccounts();
                    })
                    .addOnFailureListener(e -> toast("Greška: " + e.getMessage()));

            String newPass = etPass    != null ? etPass.getText().toString().trim()    : "";
            String conf    = etConfirm != null ? etConfirm.getText().toString().trim() : "";

            if (!TextUtils.isEmpty(newPass)) {
                if (newPass.length() < 6) {
                    toast("Nova lozinka mora imati najmanje 6 znakova.");
                    return;
                }
                if (!TextUtils.isEmpty(conf) && !newPass.equals(conf)) {
                    toast("Lozinke se ne podudaraju.");
                    return;
                }

                // No client-side API to set another user's password directly — send a reset email instead
                FirebaseAuth.getInstance().sendPasswordResetEmail(row.email)
                        .addOnSuccessListener(v2 -> toast("Poslan reset email na: " + row.email))
                        .addOnFailureListener(e -> toast("Ne mogu poslati reset email: " + e.getMessage()));
            }

            dlg.dismiss();
        });

        btnCancel.setOnClickListener(x -> dlg.dismiss());

        dlg.show();
    }

    // -- Delete --

    private void showDeleteConfirmDialog(AccountRow row) {
        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this)
                .inflate(R.layout.dialog_confirm_delete_user, null, false);
        dlg.setContentView(v);

        TextView tvName  = v.findViewById(R.id.tvDeleteUserName);
        TextView tvEmail = v.findViewById(R.id.tvDeleteUserEmail);

        String fullName = fullName(row);
        tvName.setText(TextUtils.isEmpty(fullName) ? "(Bez imena)" : fullName);
        tvEmail.setText(row.email == null ? "" : row.email);

        AppCompatButton btnConfirm = v.findViewById(R.id.btnConfirmDeleteUser);
        AppCompatButton btnCancel  = v.findViewById(R.id.btnCancelDeleteUser);

        btnConfirm.setOnClickListener(x -> {
            dlg.dismiss();
            deleteAccount(row);
        });
        btnCancel.setOnClickListener(x -> dlg.dismiss());

        dlg.show();
    }

    private void deleteAccount(AccountRow row) {
        FirebaseUtils.user(row.uid).removeValue();
        FirebaseUtils.role(row.uid).removeValue();
        FirebaseUtils.balance(row.uid).removeValue();
        toast("Nalog obrisan iz baze.");
        loadAllAccounts();
    }

    // -- Helpers --

    private static String initialsFor(String firstName, String lastName, String email) {
        String fn = firstName == null ? "" : firstName.trim();
        String ln = lastName == null ? "" : lastName.trim();

        if (!fn.isEmpty() && !ln.isEmpty()) {
            return (String.valueOf(fn.charAt(0)) + ln.charAt(0)).toUpperCase(Locale.ROOT);
        }
        if (!fn.isEmpty()) {
            return String.valueOf(fn.charAt(0)).toUpperCase(Locale.ROOT);
        }
        if (email != null && !email.isEmpty()) {
            return String.valueOf(email.charAt(0)).toUpperCase(Locale.ROOT);
        }
        return "?";
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    // -- Model + adapter --

    static class AccountRow {
        String uid, email, firstName, lastName, role;
    }

    class AccountsAdapter extends RecyclerView.Adapter<AccountsAdapter.VH> {
        List<AccountRow> data = new ArrayList<>();

        void submit(List<AccountRow> d) {
            data = (d == null) ? new ArrayList<>() : d;
            notifyDataSetChanged();
        }

        class VH extends RecyclerView.ViewHolder {
            View vAvatarBg;
            TextView tvAvatar, rowTitle, rowSubtitle, tvRoleBadge;

            VH(@NonNull View v) {
                super(v);
                vAvatarBg    = v.findViewById(R.id.vAvatarBg);
                tvAvatar     = v.findViewById(R.id.tvAvatar);
                rowTitle     = v.findViewById(R.id.rowTitle);
                rowSubtitle  = v.findViewById(R.id.rowSubtitle);
                tvRoleBadge  = v.findViewById(R.id.tvRoleBadge);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.row_admin_user, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            AccountRow it = data.get(pos);

            String fn = it.firstName == null ? "" : it.firstName;
            String ln = it.lastName == null ? "" : it.lastName;
            String title = (fn + " " + ln).trim();
            h.rowTitle.setText(title.isEmpty() ? "(Bez imena)" : title);
            h.rowSubtitle.setText(it.email == null ? "" : it.email);

            h.tvAvatar.setText(initialsFor(fn, ln, it.email));

            int avatarBgRes, avatarTextColor, badgeBgRes, badgeTextColor;
            String badgeText;

            if ("kontrolor".equals(it.role)) {
                avatarBgRes     = R.drawable.bg_avatar_amber;
                avatarTextColor = ContextCompat.getColor(AdminManageAccountsActivity.this, R.color.amber_700);
                badgeBgRes      = R.drawable.bg_role_kontrolor;
                badgeTextColor  = ContextCompat.getColor(AdminManageAccountsActivity.this, R.color.amber_700);
                badgeText       = "kontrolor";
            } else {
                avatarBgRes     = R.drawable.bg_avatar_blue;
                avatarTextColor = ContextCompat.getColor(AdminManageAccountsActivity.this, R.color.blue_600);
                badgeBgRes      = R.drawable.bg_price_pill_hour;
                badgeTextColor  = ContextCompat.getColor(AdminManageAccountsActivity.this, R.color.blue_600);
                badgeText       = "user";
            }

            h.vAvatarBg.setBackgroundResource(avatarBgRes);
            h.tvAvatar.setTextColor(avatarTextColor);
            h.tvRoleBadge.setBackgroundResource(badgeBgRes);
            h.tvRoleBadge.setTextColor(badgeTextColor);
            h.tvRoleBadge.setText(badgeText);

            h.itemView.setOnClickListener(v -> showUserActionsDialog(it));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }
}