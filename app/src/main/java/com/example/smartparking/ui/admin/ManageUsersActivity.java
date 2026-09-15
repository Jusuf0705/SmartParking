package com.example.smartparking.ui.admin;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManageUsersActivity extends AppCompatActivity {

    private RecyclerView rv;
    private UsersAdapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_manage_list);
        ((TextView) findViewById(R.id.tvTitle)).setText("Korisnici i uloge");

        // Hide "Add" — accounts are created through registration, not here
        Button fab = findViewById(R.id.fabAdd);
        fab.setVisibility(View.GONE);

        rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UsersAdapter();
        rv.setAdapter(adapter);

        FirebaseUtils.usersRef().addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                List<UserItem> list = new ArrayList<>();
                for (DataSnapshot u : ds.getChildren()) {
                    UserItem it = new UserItem();
                    it.uid = u.getKey();
                    it.email = u.child("email").getValue(String.class);
                    it.firstName = u.child("firstName").getValue(String.class);
                    it.lastName = u.child("lastName").getValue(String.class);
                    it.plate = u.child("plate").getValue(String.class);
                    list.add(it);
                }
                adapter.submit(list);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                Toast.makeText(ManageUsersActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    static class UserItem { String uid, email, firstName, lastName, plate; }

    class UsersAdapter extends RecyclerView.Adapter<UsersAdapter.VH> {
        List<UserItem> data = new ArrayList<>();
        void submit(List<UserItem> d) { data = d; notifyDataSetChanged(); }

        class VH extends RecyclerView.ViewHolder {
            TextView t1, t2;
            Button btnEdit, btnDelete, btnRole;
            VH(View v) {
                super(v);
                t1 = v.findViewById(R.id.rowTitle);
                t2 = v.findViewById(R.id.rowSubtitle);
                btnEdit = v.findViewById(R.id.btnEdit);
                btnDelete = v.findViewById(R.id.btnDelete);
                btnRole = v.findViewById(R.id.btnRole);
                btnRole.setText("Uloga");
            }
        }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.row_three_actions, p, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            UserItem it = data.get(pos);
            h.t1.setText(it.firstName + " " + it.lastName);
            String sub = (it.email == null ? "" : it.email) + (TextUtils.isEmpty(it.plate) ? "" : "  •  " + it.plate);
            h.t2.setText(sub);

            h.btnEdit.setOnClickListener(v -> showEditDialog(it));
            h.btnDelete.setOnClickListener(v -> confirmDelete(it));
            h.btnRole.setOnClickListener(v -> showRoleDialog(it));
        }
        @Override public int getItemCount() { return data.size(); }
    }

    private void showEditDialog(UserItem it) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_user, null);
        EditText etFN = view.findViewById(R.id.etFirstName);
        EditText etLN = view.findViewById(R.id.etLastName);
        EditText etPl = view.findViewById(R.id.etPlate);
        etFN.setText(it.firstName); etLN.setText(it.lastName); etPl.setText(it.plate);

        new AlertDialog.Builder(this)
                .setTitle("Uredi korisnika")
                .setView(view)
                .setPositiveButton("Spasi", (d, w) -> {
                    Map<String, Object> upd = new HashMap<>();
                    upd.put("firstName", etFN.getText().toString().trim());
                    upd.put("lastName", etLN.getText().toString().trim());
                    String pl = etPl.getText().toString().trim();
                    upd.put("plate", pl.isEmpty() ? null : pl);
                    FirebaseUtils.user(it.uid).updateChildren(upd);
                })
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void showRoleDialog(UserItem it) {
        String[] roles = {"user", "kontrola"};
        new AlertDialog.Builder(this)
                .setTitle("Promijeni ulogu")
                .setItems(roles, (d, idx) -> FirebaseUtils.role(it.uid).setValue(roles[idx]))
                .show();
    }

    private void confirmDelete(UserItem it) {
        new AlertDialog.Builder(this)
                .setTitle("Brisanje")
                .setMessage("Obrisati korisnika " + it.email + " iz baze (users/roles/balances)?")
                .setPositiveButton("Obriši", (d, w) -> {
                    FirebaseUtils.user(it.uid).removeValue();
                    FirebaseUtils.role(it.uid).removeValue();
                    FirebaseUtils.balance(it.uid).removeValue();
                    // Note: this does not delete the Firebase Auth account itself
                    // (would need the Admin SDK / a Cloud Function for that)
                })
                .setNegativeButton("Otkaži", null)
                .show();
    }
}