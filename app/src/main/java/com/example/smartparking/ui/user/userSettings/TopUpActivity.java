package com.example.smartparking.ui.user.userSettings;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.example.smartparking.databinding.ActivityTopUpBinding;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TopUpActivity extends AppCompatActivity {

    private ActivityTopUpBinding binding;
    private String uid;

    // Currently saved card, or null if none
    private CardData currentCard = null;

    // Cached balance, used in the confirm dialog
    private double cachedBalance = 0.0;

    private static final double MIN = 1.0;
    private static final double MAX = 5000.0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTopUpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser fu = FirebaseAuth.getInstance().getCurrentUser();
        if (fu == null) {
            toast("Niste prijavljeni.");
            finish();
            return;
        }
        uid = fu.getUid();

        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnAmount10.setOnClickListener(v -> setAmount(10));
        binding.btnAmount20.setOnClickListener(v -> setAmount(20));
        binding.btnAmount50.setOnClickListener(v -> setAmount(50));

        binding.btnTopUp.setOnClickListener(v -> onConfirm());

        // Tapping the hero card opens the add form or the options sheet
        binding.heroCardContainer.setOnClickListener(v -> {
            if (currentCard == null) showAddCardSheet();
            else                     showCardOptionsSheet();
        });

        // Live balance
        FirebaseUtils.balance(uid).addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                Double d = snapshot.getValue(Double.class);
                double bal = d == null ? 0.0 : d;
                cachedBalance = bal;
                binding.tvBalance.setText(String.format(Locale.getDefault(), "%.2f KM", bal));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        });

        // Listen for card changes (add/update/delete)
        FirebaseUtils.user(uid).child("paymentCard").addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                if (!ds.exists()) {
                    currentCard = null;
                } else {
                    CardData c = new CardData();
                    c.number = ds.child("number").getValue(String.class);
                    c.holder = ds.child("holder").getValue(String.class);
                    c.expiry = ds.child("expiry").getValue(String.class);
                    c.cvc    = ds.child("cvc").getValue(String.class);
                    currentCard = c;
                }
                refreshHeroCard();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    // -- Hero card UI --

    private void refreshHeroCard() {
        boolean has = currentCard != null && !TextUtils.isEmpty(currentCard.number);
        binding.cardEmpty.setVisibility(has ? View.GONE : View.VISIBLE);
        binding.cardFilled.setVisibility(has ? View.VISIBLE : View.GONE);
        binding.btnTopUp.setEnabled(has);
        binding.btnTopUp.setAlpha(has ? 1f : 0.5f);

        if (has) {
            binding.tvCardNumber.setText(maskCardNumber(currentCard.number));
            binding.tvCardHolder.setText(
                    TextUtils.isEmpty(currentCard.holder) ? "" : currentCard.holder.toUpperCase(Locale.ROOT));
            binding.tvCardExpiry.setText(
                    TextUtils.isEmpty(currentCard.expiry) ? "MM/GG" : currentCard.expiry);
        }
    }

    // Full masked number: •••• •••• •••• 4242
    private static String maskCardNumber(String number) {
        if (TextUtils.isEmpty(number)) return "••••    ••••    ••••    ••••";
        String digits = number.replaceAll("\\D+", "");
        String last4  = digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits;
        while (last4.length() < 4) last4 = "•" + last4;
        return "••••    ••••    ••••    " + last4;
    }

    // Short form used in dialogs: •••• 4242
    private static String maskedLast4(@Nullable String number) {
        String digits = number == null ? "" : number.replaceAll("\\D+", "");
        String last4  = digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits;
        return "•••• " + last4;
    }

    // -- Bottom sheet: card form (add or edit) --

    private void showAddCardSheet()  { showCardFormSheet(null); }

    private void showEditCardSheet() { showCardFormSheet(currentCard); }

    private void showCardFormSheet(@Nullable CardData existing) {
        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_card_form, null, false);
        dlg.setContentView(v);

        TextView title      = v.findViewById(R.id.tvFormTitle);
        EditText etNumber   = v.findViewById(R.id.etCardNumber);
        EditText etHolder   = v.findViewById(R.id.etCardHolder);
        EditText etExpiry   = v.findViewById(R.id.etCardExpiry);
        EditText etCvc      = v.findViewById(R.id.etCardCvc);
        Button btnSave      = v.findViewById(R.id.btnSaveCard);
        Button btnCancel    = v.findViewById(R.id.btnCancelForm);

        // Auto-format: a space every 4 digits in the number, "/" in the expiry date
        etNumber.addTextChangedListener(new SimpleWatcher(etNumber) {
            @Override void onChange(String raw) {
                String digits = raw.replaceAll("\\D+", "");
                if (digits.length() > 16) digits = digits.substring(0, 16);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < digits.length(); i++) {
                    if (i > 0 && i % 4 == 0) sb.append(' ');
                    sb.append(digits.charAt(i));
                }
                setSilently(sb.toString());
            }
        });
        etExpiry.addTextChangedListener(new SimpleWatcher(etExpiry) {
            @Override void onChange(String raw) {
                String digits = raw.replaceAll("\\D+", "");
                if (digits.length() > 4) digits = digits.substring(0, 4);
                String out = digits.length() > 2
                        ? digits.substring(0, 2) + "/" + digits.substring(2)
                        : digits;
                setSilently(out);
            }
        });

        if (existing != null) {
            title.setText("Uredi karticu");
            btnSave.setText("Sačuvaj izmjene");
            if (!TextUtils.isEmpty(existing.number)) etNumber.setText(existing.number);
            if (!TextUtils.isEmpty(existing.holder)) etHolder.setText(existing.holder);
            if (!TextUtils.isEmpty(existing.expiry)) etExpiry.setText(existing.expiry);
            if (!TextUtils.isEmpty(existing.cvc))    etCvc.setText(existing.cvc);
        } else {
            title.setText("Dodaj karticu");
            btnSave.setText("Sačuvaj karticu");
        }

        btnSave.setOnClickListener(x -> {
            String number = etNumber.getText().toString().replaceAll("\\s+", "");
            String holder = etHolder.getText().toString().trim();
            String expiry = etExpiry.getText().toString().trim();
            String cvc    = etCvc.getText().toString().trim();

            if (number.length() < 13 || number.length() > 19) { toast("Broj kartice nije ispravan."); return; }
            if (TextUtils.isEmpty(holder))                    { toast("Unesite ime vlasnika."); return; }
            if (!expiry.matches("\\d{2}/\\d{2}"))             { toast("Datum isteka mora biti u formatu MM/GG."); return; }
            if (cvc.length() < 3 || cvc.length() > 4)         { toast("CVC nije ispravan."); return; }

            saveCard(number, holder, expiry, cvc);
            dlg.dismiss();
        });
        btnCancel.setOnClickListener(x -> dlg.dismiss());

        dlg.show();
    }

    private void saveCard(String number, String holder, String expiry, String cvc) {
        Map<String, Object> data = new HashMap<>();
        data.put("number",    number);
        data.put("holder",    holder);
        data.put("expiry",    expiry);
        data.put("cvc",       cvc);
        data.put("createdAt", System.currentTimeMillis());

        FirebaseUtils.user(uid).child("paymentCard").setValue(data)
                .addOnSuccessListener(x -> toast("Kartica sačuvana."))
                .addOnFailureListener(e -> toast("Greška: " + e.getMessage()));
    }

    // -- Bottom sheet: options for the existing card --

    private void showCardOptionsSheet() {
        if (currentCard == null) return;

        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_card_options, null, false);
        dlg.setContentView(v);

        TextView tvNumber = v.findViewById(R.id.tvOptionsCardNumber);
        tvNumber.setText(maskedLast4(currentCard.number));

        v.findViewById(R.id.btnEditCard).setOnClickListener(x -> {
            dlg.dismiss();
            showEditCardSheet();
        });
        v.findViewById(R.id.btnDeleteCard).setOnClickListener(x -> {
            dlg.dismiss();
            showDeleteCardConfirmDialog();
        });
        v.findViewById(R.id.btnCancelOptions).setOnClickListener(x -> dlg.dismiss());

        dlg.show();
    }

    // -- Bottom sheet: confirm card deletion --

    private void showDeleteCardConfirmDialog() {
        if (currentCard == null) return;

        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this)
                .inflate(R.layout.dialog_confirm_delete_card, null, false);
        dlg.setContentView(v);

        TextView tvNumber = v.findViewById(R.id.tvDeleteCardNumber);
        tvNumber.setText(maskedLast4(currentCard.number));

        AppCompatButton btnConfirm = v.findViewById(R.id.btnConfirmDeleteCard);
        AppCompatButton btnCancel  = v.findViewById(R.id.btnCancelDeleteCard);

        btnConfirm.setOnClickListener(x -> {
            dlg.dismiss();
            deleteCard();
        });
        btnCancel.setOnClickListener(x -> dlg.dismiss());

        dlg.show();
    }

    private void deleteCard() {
        FirebaseUtils.user(uid).child("paymentCard").removeValue()
                .addOnSuccessListener(x -> toast("Kartica obrisana."))
                .addOnFailureListener(e -> toast("Greška: " + e.getMessage()));
    }

    // -- Top-up --

    private void setAmount(double v) {
        binding.etAmount.setText(String.format(Locale.getDefault(), "%.2f", v));
        binding.etAmount.setSelection(binding.etAmount.getText().length());
    }

    private void onConfirm() {
        if (currentCard == null) {
            toast("Prvo dodajte karticu.");
            showAddCardSheet();
            return;
        }

        String s = binding.etAmount.getText() == null ? "" : binding.etAmount.getText().toString().trim();
        s = s.replace(',', '.');

        double amount;
        try { amount = Double.parseDouble(s); }
        catch (Exception ex) { toast("Unesite ispravan iznos."); return; }

        if (amount < MIN) { toast("Minimalni iznos je " + fmt(MIN) + " KM."); return; }
        if (amount > MAX) { toast("Maksimalni iznos je " + fmt(MAX) + " KM."); return; }

        // Show the confirm dialog instead of topping up directly
        showConfirmTopupDialog(amount);
    }

    /** Confirm bottom sheet: "Top up by X KM?" Yes / No */
    private void showConfirmTopupDialog(double amount) {
        BottomSheetDialog dlg = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this)
                .inflate(R.layout.dialog_confirm_topup, null, false);
        dlg.setContentView(v);

        TextView tvAmount     = v.findViewById(R.id.tvConfirmTopupAmount);
        TextView tvCard       = v.findViewById(R.id.tvConfirmTopupCard);
        TextView tvBalance    = v.findViewById(R.id.tvConfirmTopupBalance);
        TextView tvNewBalance = v.findViewById(R.id.tvConfirmTopupNewBalance);

        AppCompatButton btnYes = v.findViewById(R.id.btnConfirmTopupYes);
        AppCompatButton btnNo  = v.findViewById(R.id.btnConfirmTopupNo);

        tvAmount.setText(String.format(Locale.getDefault(), "%.2f KM", amount));
        tvBalance.setText(String.format(Locale.getDefault(), "%.2f KM", cachedBalance));
        tvNewBalance.setText(String.format(Locale.getDefault(), "%.2f KM", cachedBalance + amount));

        tvCard.setText(currentCard != null && !TextUtils.isEmpty(currentCard.number)
                ? maskedLast4(currentCard.number)
                : "•••• ••••");

        btnYes.setOnClickListener(x -> {
            dlg.dismiss();
            binding.btnTopUp.setEnabled(false);
            performTopUp(amount);
        });
        btnNo.setOnClickListener(x -> dlg.dismiss());

        dlg.show();
    }

    private void performTopUp(double amount) {
        FirebaseUtils.balance(uid).runTransaction(new Transaction.Handler() {
            @NonNull @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                // Atomically increase the balance
                Double cur = currentData.getValue(Double.class);
                double newBal = (cur == null ? 0.0 : cur) + amount;
                currentData.setValue(newBal);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot snap) {
                if (error != null || !committed) {
                    if (binding != null) binding.btnTopUp.setEnabled(true);
                    toast("Neuspješna dopuna: " + (error != null ? error.getMessage() : "?"));
                    return;
                }

                // Log the top-up in /balanceTopups
                String key = FirebaseUtils.root().child("balanceTopups").push().getKey();
                if (key != null) {
                    Map<String, Object> log = new HashMap<>();
                    log.put("userId", uid);
                    log.put("amount", amount);
                    log.put("createdAt", System.currentTimeMillis());

                    FirebaseUtils.root().child("balanceTopups").child(key)
                            .setValue(log)
                            .addOnCompleteListener(x -> onTopUpFinished(amount));
                } else {
                    onTopUpFinished(amount);
                }
            }
        });
    }

    private void onTopUpFinished(double amount) {
        if (binding != null) binding.btnTopUp.setEnabled(currentCard != null);
        toast("Uspješno dopunjeno " + fmt(amount) + " KM.");
        finish();
    }

    private static String fmt(double v) {
        return String.format(Locale.getDefault(), "%.2f", v);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    // -- Helpers --

    static class CardData {
        String number, holder, expiry, cvc;
    }

    // Watcher that avoids re-entrant calls while auto-formatting
    private static abstract class SimpleWatcher implements TextWatcher {
        private final EditText target;
        private boolean editing = false;

        SimpleWatcher(EditText target) { this.target = target; }

        abstract void onChange(String raw);

        void setSilently(String formatted) {
            editing = true;
            int cursor = formatted.length();
            target.setText(formatted);
            target.setSelection(Math.min(cursor, formatted.length()));
            editing = false;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) {
            if (editing) return;
            onChange(s.toString());
        }
    }
}