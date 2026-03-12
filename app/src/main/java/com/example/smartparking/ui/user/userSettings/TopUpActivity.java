package com.example.smartparking.ui.user.userSettings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TopUpActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "smartparking_topup";
    private static final String KEY_REMEMBER = "remember_card";
    private static final String KEY_CARD = "card_number";
    private static final String KEY_EXP = "card_expiry";

    private EditText etCardNumber, etExpiry, etCvc, etAmount;
    private Button btnPay, btnBack;
    private MaterialCheckBox cbRememberCard;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_top_up);
        setTitle("Dokupi kredit");

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        etCardNumber = findViewById(R.id.etCardNumber);
        etExpiry     = findViewById(R.id.etExpiry);
        etCvc        = findViewById(R.id.etCvc);
        etAmount     = findViewById(R.id.etAmount);
        btnPay       = findViewById(R.id.btnPay);

        btnBack = findViewById(R.id.btnBack);
        cbRememberCard = findViewById(R.id.cbRememberCard);

        setupInputFormattingAndFocus();
        restoreRememberedCard();

        btnBack.setOnClickListener(v -> finish());
        btnPay.setOnClickListener(v -> doPay());

        if (cbRememberCard != null) {
            cbRememberCard.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean(KEY_REMEMBER, isChecked).apply();
                if (!isChecked) clearRememberedCard();
            });
        }
    }

    private void restoreRememberedCard() {
        boolean remember = prefs.getBoolean(KEY_REMEMBER, false);
        if (cbRememberCard != null) cbRememberCard.setChecked(remember);

        if (!remember) return;

        String savedCard = prefs.getString(KEY_CARD, "");
        String savedExp  = prefs.getString(KEY_EXP, "");

        if (!TextUtils.isEmpty(savedCard) && etCardNumber != null) {
            etCardNumber.setText(savedCard);
            etCardNumber.setSelection(etCardNumber.getText().length());
        }
        if (!TextUtils.isEmpty(savedExp) && etExpiry != null) {
            etExpiry.setText(savedExp);
            etExpiry.setSelection(etExpiry.getText().length());
        }
    }

    private void saveRememberedCardIfNeeded() {
        boolean remember = cbRememberCard != null && cbRememberCard.isChecked();
        prefs.edit().putBoolean(KEY_REMEMBER, remember).apply();

        if (!remember) {
            clearRememberedCard();
            return;
        }

        // čuvamo samo broj kartice + expiry (ne CVC)
        String card = safe(etCardNumber);
        String exp  = safe(etExpiry);

        prefs.edit()
                .putString(KEY_CARD, card)
                .putString(KEY_EXP, exp)
                .apply();
    }

    private void clearRememberedCard() {
        prefs.edit()
                .remove(KEY_CARD)
                .remove(KEY_EXP)
                .apply();
    }

    private void setupInputFormattingAndFocus() {
        etCardNumber.setKeyListener(DigitsKeyListener.getInstance("0123456789 "));
        etCardNumber.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(23) });
        etCardNumber.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        etCardNumber.addTextChangedListener(new CardNumberTextWatcher(etCardNumber, () -> {
            String raw = safe(etCardNumber).replace(" ", "");
            if (raw.length() >= 16) etExpiry.requestFocus();
        }));

        etExpiry.setKeyListener(DigitsKeyListener.getInstance("0123456789/"));
        etExpiry.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(5) });
        etExpiry.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        etExpiry.addTextChangedListener(new ExpiryTextWatcher(etExpiry, () -> {
            if (safe(etExpiry).length() == 5) etCvc.requestFocus();
        }));

        etCvc.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
        etCvc.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(4) });
        etCvc.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        etCvc.addTextChangedListener(simpleLengthWatcher(etCvc, 3, () -> etAmount.requestFocus()));

        etAmount.setKeyListener(DigitsKeyListener.getInstance("0123456789.,"));
        etAmount.setImeOptions(EditorInfo.IME_ACTION_DONE);
        etAmount.addTextChangedListener(new MoneyTextWatcher(etAmount));

        etAmount.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                doPay();
                return true;
            }
            return false;
        });
    }

    private TextWatcher simpleLengthWatcher(EditText et, int minLenToJump, Runnable jump) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (s != null && s.length() >= minLenToJump) jump.run();
            }
        };
    }

    private void doPay() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            toast("Niste prijavljeni.");
            finish();
            return;
        }

        String cardDigits = safe(etCardNumber).replace(" ", "");
        String exp        = safe(etExpiry);
        String cvc        = safe(etCvc);
        String amtS       = safe(etAmount).replace(",", ".");

        if (!isValidCardNumber(cardDigits)) { etCardNumber.requestFocus(); toast("Neispravan broj kartice."); return; }
        if (!isValidExpiry(exp))            { etExpiry.requestFocus(); toast("Neispravan datum (MM/YY)."); return; }
        if (!isValidCvc(cvc))               { etCvc.requestFocus(); toast("Neispravan CVC."); return; }

        double amount;
        try { amount = Double.parseDouble(amtS); }
        catch (Exception e) { etAmount.requestFocus(); toast("Unesite ispravan iznos."); return; }

        if (amount <= 0) { etAmount.requestFocus(); toast("Iznos mora biti veći od 0."); return; }
        if (amount > 5000) { toast("Prevelik iznos za simulaciju."); return; }

        btnPay.setEnabled(false);

        FirebaseUtils.balance(uid).runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData currentData) {
                Double cur = currentData.getValue(Double.class);
                double current = (cur == null ? 0.0 : cur);
                currentData.setValue(current + amount);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed,
                                   @Nullable com.google.firebase.database.DataSnapshot snapshot) {

                if (error != null) {
                    btnPay.setEnabled(true);
                    toast("Greška: " + error.getMessage());
                    return;
                }
                if (!committed) {
                    btnPay.setEnabled(true);
                    toast("Transakcija nije završena.");
                    return;
                }

                // ✅ sačuvaj karticu ako je čekirano
                saveRememberedCardIfNeeded();

                writeTopupLog(uid, amount);
            }
        });
    }

    private void writeTopupLog(String uid, double amount) {
        DatabaseReference topupsRef = FirebaseUtils.root().child("balanceTopups");
        String id = topupsRef.push().getKey();
        if (id == null) {
            btnPay.setEnabled(true);
            toast("Greška: nije moguće kreirati zapis uplate.");
            return;
        }

        Map<String, Object> log = new HashMap<>();
        log.put("userId", uid);
        log.put("amount", amount);
        log.put("createdAt", ServerValue.TIMESTAMP);

        topupsRef.child(id).setValue(log)
                .addOnSuccessListener(x -> {
                    btnPay.setEnabled(true);
                    toast(String.format(Locale.getDefault(), "Uspješno dopunjeno: %.2f KM", amount));
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnPay.setEnabled(true);
                    toast("Balans je dopunjen, ali log nije upisan: " + e.getMessage());
                    finish();
                });
    }

    private boolean isValidCardNumber(String s) {
        return !TextUtils.isEmpty(s) && s.matches("^\\d{13,19}$");
    }

    private boolean isValidExpiry(String s) {
        if (TextUtils.isEmpty(s)) return false;
        if (!s.matches("^\\d{2}/\\d{2}$")) return false;
        int mm = Integer.parseInt(s.substring(0, 2));
        return mm >= 1 && mm <= 12;
    }

    private boolean isValidCvc(String s) {
        return !TextUtils.isEmpty(s) && s.matches("^\\d{3,4}$");
    }

    private String safe(EditText et) {
        return et == null ? "" : String.valueOf(et.getText()).trim();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    // -------- TextWatchers --------

    interface DoneCallback { void run(); }

    static class CardNumberTextWatcher implements TextWatcher {
        private final EditText editText;
        private final DoneCallback cb;
        private boolean self;

        CardNumberTextWatcher(EditText et, DoneCallback cb) {
            this.editText = et;
            this.cb = cb;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override public void afterTextChanged(Editable s) {
            if (self) return;
            self = true;

            String raw = s.toString().replaceAll("\\s+", "").replaceAll("[^0-9]", "");
            if (raw.length() > 19) raw = raw.substring(0, 19);

            StringBuilder out = new StringBuilder();
            for (int i = 0; i < raw.length(); i++) {
                if (i > 0 && i % 4 == 0) out.append(' ');
                out.append(raw.charAt(i));
            }

            String formatted = out.toString();
            if (!formatted.equals(s.toString())) {
                editText.setText(formatted);
                editText.setSelection(formatted.length());
            }

            if (cb != null && raw.length() >= 16) cb.run();
            self = false;
        }
    }

    static class ExpiryTextWatcher implements TextWatcher {
        private final EditText editText;
        private final DoneCallback cb;
        private boolean self;

        ExpiryTextWatcher(EditText et, DoneCallback cb) {
            this.editText = et;
            this.cb = cb;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override public void afterTextChanged(Editable s) {
            if (self) return;
            self = true;

            String raw = s.toString().replaceAll("[^0-9]", "");
            if (raw.length() > 4) raw = raw.substring(0, 4);

            String formatted;
            if (raw.length() == 0) formatted = "";
            else if (raw.length() <= 2) {
                formatted = raw;
                if (raw.length() == 2) formatted = raw + "/";
            } else {
                formatted = raw.substring(0, 2) + "/" + raw.substring(2);
            }

            if (raw.length() >= 2) {
                int mm = Integer.parseInt(raw.substring(0, 2));
                String yy = raw.length() > 2 ? raw.substring(2) : "";
                if (mm == 0) mm = 1;
                if (mm > 12) mm = 12;
                formatted = String.format(Locale.ROOT, "%02d/", mm) + yy;
            }

            if (!formatted.equals(s.toString())) {
                editText.setText(formatted);
                editText.setSelection(formatted.length());
            }

            if (cb != null && formatted.length() == 5) cb.run();
            self = false;
        }
    }

    static class MoneyTextWatcher implements TextWatcher {
        private final EditText editText;
        private boolean self;

        MoneyTextWatcher(EditText et) { this.editText = et; }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override public void afterTextChanged(Editable s) {
            if (self) return;
            self = true;

            String txt = s.toString().replace(',', '.').replaceAll("[^0-9.]", "");
            int dot = txt.indexOf('.');
            if (dot != -1) {
                String before = txt.substring(0, dot + 1);
                String after = txt.substring(dot + 1).replace(".", "");
                txt = before + after;
                int decLen = txt.length() - (dot + 1);
                if (decLen > 2) txt = txt.substring(0, dot + 1 + 2);
            }

            while (txt.length() > 1 && txt.startsWith("0") && !txt.startsWith("0.")) {
                txt = txt.substring(1);
            }

            if (!txt.equals(s.toString())) {
                editText.setText(txt);
                editText.setSelection(txt.length());
            }

            self = false;
        }
    }
}
