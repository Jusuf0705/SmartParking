package com.example.smartparking.data;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;

public final class SecondaryAuth {

    private static FirebaseAuth secondary;

    private SecondaryAuth() {}

    /** Inicijalizuj sekundarni FirebaseApp jednom i vrati FirebaseAuth koji NE dira glavnu sesiju. */
    public static synchronized FirebaseAuth get(Context ctx) {
        if (secondary != null) return secondary;

        // Iskoristi iste opcije kao primarni app
        FirebaseApp primary = FirebaseApp.getInstance();
        FirebaseOptions opts = primary.getOptions();

        // Ako već postoji app s tim imenom, samo ga preuzmi.
        FirebaseApp app;
        try {
            app = FirebaseApp.getInstance("secondary");
        } catch (IllegalStateException notFound) {
            app = FirebaseApp.initializeApp(ctx, opts, "secondary");
        }
        secondary = FirebaseAuth.getInstance(app);
        return secondary;
    }
}
