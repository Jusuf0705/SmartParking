package com.example.smartparking.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Helper za welcome bonus (10 KM za nove korisnike).
 *
 * KORISTI OVO NA 2 MJESTA:
 *
 * 1) NAKON USPJEŠNE REGISTRACIJE (najbolje mjesto):
 *    WelcomeBonus.grantIfNew(context, uid, granted -> {
 *        // granted == true znaci da je 10 KM dodano
 *    });
 *
 * 2) UserPayFragment (fallback, ako korisnik nije dobio bonus pri registraciji):
 *    WelcomeBonus.grantIfNew(context, uid, granted -> {
 *        if (granted) showWelcomeDialog();
 *    });
 *
 * Metoda je idempotentna — provjerava zastavu u SharedPreferences,
 * pa je bezbjedno pozvati je više puta.
 */
public final class WelcomeBonus {

    private WelcomeBonus() {}

    public static final double AMOUNT = 10.00;

    private static final String PREFS_NAME    = "sp_welcome_bonus";
    private static final String KEY_PREFIX    = "granted_";
    private static final String KEY_UI_SHOWN  = "ui_shown_"; // Da li je UI vec prikazan korisniku

    public interface Callback {
        /** @param granted true ako je bonus upravo dodan, false ako je vec bio dodijeljen ranije */
        void onResult(boolean granted);
    }

    /**
     * Dodjeljuje 10 KM ako korisnik jos nije dobio bonus.
     * Sigurno se moze pozvati vise puta.
     */
    public static void grantIfNew(@NonNull Context ctx,
                                  @Nullable String uid,
                                  @NonNull Callback cb) {
        if (uid == null || uid.isEmpty()) { cb.onResult(false); return; }

        SharedPreferences p = ctx.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Ako je vec dodijeljen prema local flagu, ne diramo bazu
        if (p.getBoolean(KEY_PREFIX + uid, false)) { cb.onResult(false); return; }

        // Provjeri postoji li balance u DB-u
        FirebaseUtils.balance(uid).get().addOnSuccessListener(snap -> {
            boolean balanceExists = snap.exists() && snap.getValue(Double.class) != null;

            if (balanceExists) {
                // Vec postoji — nije novi user, samo obiljezi kao granted da ne pokusavamo opet
                p.edit()
                        .putBoolean(KEY_PREFIX + uid, true)
                        .putBoolean(KEY_UI_SHOWN + uid, true)
                        .apply();
                cb.onResult(false);
                return;
            }

            // Prvi put — postavi 10 KM
            FirebaseUtils.balance(uid).setValue(AMOUNT)
                    .addOnSuccessListener(r -> {
                        p.edit().putBoolean(KEY_PREFIX + uid, true).apply();
                        cb.onResult(true);
                    })
                    .addOnFailureListener(e -> cb.onResult(false));
        }).addOnFailureListener(e -> cb.onResult(false));
    }

    /**
     * Provjerava treba li prikazati "Dobrodošli!" panel korisniku.
     * Vraca true samo jednom nakon što je bonus dodijeljen.
     */
    public static boolean shouldShowUi(@NonNull Context ctx, @Nullable String uid) {
        if (uid == null || uid.isEmpty()) return false;
        SharedPreferences p = ctx.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean granted   = p.getBoolean(KEY_PREFIX + uid, false);
        boolean uiShown   = p.getBoolean(KEY_UI_SHOWN + uid, false);
        return granted && !uiShown;
    }

    /**
     * Oznaci da je UI prikazan — sledeci put se ne prikazuje.
     */
    public static void markUiShown(@NonNull Context ctx, @Nullable String uid) {
        if (uid == null || uid.isEmpty()) return;
        ctx.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_UI_SHOWN + uid, true)
                .apply();
    }
}