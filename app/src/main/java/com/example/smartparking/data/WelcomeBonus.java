package com.example.smartparking.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Grants the 10 KM welcome bonus to new users. Idempotent — safe to call
 * repeatedly (e.g. once on registration, once as a fallback in UserPayFragment).
 */
public final class WelcomeBonus {

    private WelcomeBonus() {}

    public static final double AMOUNT = 10.00;

    private static final String PREFS_NAME   = "sp_welcome_bonus";
    private static final String KEY_PREFIX   = "granted_";
    private static final String KEY_UI_SHOWN = "ui_shown_";

    public interface Callback {
        /** @param granted true if the bonus was just added, false if already granted */
        void onResult(boolean granted);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Grants the bonus if this user hasn't received it yet. */
    public static void grantIfNew(@NonNull Context ctx,
                                  @Nullable String uid,
                                  @NonNull Callback cb) {
        if (uid == null || uid.isEmpty()) { cb.onResult(false); return; }

        SharedPreferences p = prefs(ctx);

        // Already granted locally — skip the DB round-trip.
        if (p.getBoolean(KEY_PREFIX + uid, false)) { cb.onResult(false); return; }

        FirebaseUtils.balance(uid).get().addOnSuccessListener(snap -> {
            boolean balanceExists = snap.exists() && snap.getValue(Double.class) != null;

            if (balanceExists) {
                // Not a new user — mark as granted so we stop checking.
                p.edit()
                        .putBoolean(KEY_PREFIX + uid, true)
                        .putBoolean(KEY_UI_SHOWN + uid, true)
                        .apply();
                cb.onResult(false);
                return;
            }

            FirebaseUtils.balance(uid).setValue(AMOUNT)
                    .addOnSuccessListener(r -> {
                        p.edit().putBoolean(KEY_PREFIX + uid, true).apply();
                        cb.onResult(true);
                    })
                    .addOnFailureListener(e -> cb.onResult(false));
        }).addOnFailureListener(e -> cb.onResult(false));
    }

    /** True exactly once, right after the bonus was granted, so the welcome panel can be shown. */
    public static boolean shouldShowUi(@NonNull Context ctx, @Nullable String uid) {
        if (uid == null || uid.isEmpty()) return false;
        SharedPreferences p = prefs(ctx);
        return p.getBoolean(KEY_PREFIX + uid, false) && !p.getBoolean(KEY_UI_SHOWN + uid, false);
    }

    /** Marks the welcome panel as shown so it won't appear again. */
    public static void markUiShown(@NonNull Context ctx, @Nullable String uid) {
        if (uid == null || uid.isEmpty()) return;
        prefs(ctx).edit().putBoolean(KEY_UI_SHOWN + uid, true).apply();
    }
}