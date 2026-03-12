package com.example.smartparking.data;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Centralizovane reference za Firebase Realtime Database.
 * Koristi strogo tipizirane putanje – bez čitanja/slušanja na "/" kako bi
 * pravila sigurnosti (rules) bila zadovoljena za običnog korisnika.
 */
public final class FirebaseUtils {

    // ====== KONFIGURACIJA ======
    // Ako mijenjaš region/projekat, ažuriraj URL ispod:
    private static final String DB_URL =
            "https://smartparking-744d8-default-rtdb.europe-west1.firebasedatabase.app";

    // Ako želiš offline cache, pozovi setPersistenceEnabled(true) JEDNOM
    // (npr. u Application.onCreate). Ovdje to svjesno ostavljamo isključeno.
    private static FirebaseDatabase INSTANCE;

    private FirebaseUtils() {}

    // Lazy init baze (siguran za više niti)
    private static synchronized FirebaseDatabase db() {
        if (INSTANCE == null) {
            INSTANCE = FirebaseDatabase.getInstance(DB_URL);
            // INSTANCE.setPersistenceEnabled(true);  // premjesti u Application ako želiš offline
        }
        return INSTANCE;
    }

    /** Root ref (ne koristiti za "listen at /", već samo za compose putanja i updateChildren) */
    public static DatabaseReference root() {
        return db().getReference();
    }

    // ====== .info helpers (ne traže auth i ne “diraju” rules) ======

    /** Server time offset u milisekundama (može biti +/− u odnosu na lokalno vrijeme). */
    public static DatabaseReference infoServerTimeOffset() {
        return db().getReference(".info/serverTimeOffset");
    }

    /** Status konekcije (true/false). */
    public static DatabaseReference infoConnected() {
        return db().getReference(".info/connected");
    }

    // ====== APP CONFIG (opcionalno) ======

    public static DatabaseReference appConfigRef() { return root().child("appConfig"); }
    public static DatabaseReference adminUidRef()  { return appConfigRef().child("adminUid"); }

    // ====== ROLE / USERS / BALANCES ======
    // Usklađeno sa tvojim rules iz poruke.

    /** /roles */
    public static DatabaseReference rolesRef() { return root().child("roles"); }
    public static DatabaseReference role(String uid) { return rolesRef().child(uid); }

    /** /users */
    public static DatabaseReference usersRef() { return root().child("users"); }
    public static DatabaseReference user(String uid) { return usersRef().child(uid); }
    public static DatabaseReference userVehicles(String uid) { return user(uid).child("vehicles"); }

    /** /balances */
    public static DatabaseReference balancesRef() { return root().child("balances"); }
    public static DatabaseReference balance(String uid) { return balancesRef().child(uid); }

    // Opcionalno: mapa “aktivnih” tablica po gradu, ako želiš “bravu” na tablice
    public static DatabaseReference activePlatesRef() { return root().child("activePlates"); }

    // ====== PARKING LOTS ======
    // Struktura pretpostavlja:
    // /parkingLots/{lotId}/name, address, geo/{lat,lng}, pricing/{perHour,perDay}, totalSpaces, spaces/{spaceId}/{status,reservedBy,until}

    public static DatabaseReference parkingLotsRef() { return root().child("parkingLots"); }
    public static DatabaseReference parkingLot(String parkingId) { return parkingLotsRef().child(parkingId); }
    public static DatabaseReference parkingPricing(String parkingId) { return parkingLot(parkingId).child("pricing"); }
    public static DatabaseReference parkingSpaces(String parkingId) { return parkingLot(parkingId).child("spaces"); }
    public static DatabaseReference parkingSpace(String parkingId, String spaceId) { return parkingSpaces(parkingId).child(spaceId); }

    // ====== PARKING SESSIONS ======
    // /parkingSessions/{sessionId}: { userId, parkingLotId, space, startTime, endTime, amount, type, plate }

    public static DatabaseReference sessionsRef() { return root().child("parkingSessions"); }
    public static DatabaseReference session(String sessionId) { return sessionsRef().child(sessionId); }

    // ====== FINES ======
    // /fines/{fineId}: { issuedBy, amount, plate, status, ... }

    public static DatabaseReference finesRef() { return root().child("fines"); }
    public static DatabaseReference fine(String fineId) { return finesRef().child(fineId); }

    // ====== UTIL: safe child compose (ako zatreba) ======
    // Možeš koristiti ako sklapaš dinamičke ključeve: sprječava . $ # [ ] /
    // Ostavljeno kao referenca; trenutno nije pozivano direktno iz fragmenata.
    public static String sanitizeKey(String raw) {
        if (raw == null) return "";
        return raw.replace(".", "")
                .replace("$", "")
                .replace("#", "")
                .replace("[", "")
                .replace("]", "")
                .replace("/", "");
    }
}
