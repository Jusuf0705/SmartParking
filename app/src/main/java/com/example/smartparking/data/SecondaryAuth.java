package com.example.smartparking.data;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;

/** Provides a second FirebaseAuth instance so admin actions don't affect the current user's session. */
public final class SecondaryAuth {

    private static final String APP_NAME = "secondary";

    private static FirebaseAuth secondary;

    private SecondaryAuth() {}

    public static synchronized FirebaseAuth get(Context ctx) {
        if (secondary != null) return secondary;

        FirebaseOptions opts = FirebaseApp.getInstance().getOptions();

        FirebaseApp app;
        try {
            app = FirebaseApp.getInstance(APP_NAME);
        } catch (IllegalStateException notInitialized) {
            app = FirebaseApp.initializeApp(ctx.getApplicationContext(), opts, APP_NAME);
        }

        secondary = FirebaseAuth.getInstance(app);
        return secondary;
    }
}