package com.example.smartparking.data;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public final class FirebaseUtils {

    private static final String DB_URL =
            "https://smartparking-744d8-default-rtdb.europe-west1.firebasedatabase.app";

    private static FirebaseDatabase INSTANCE;
    private FirebaseUtils() {}

    private static synchronized FirebaseDatabase db() {
        if (INSTANCE == null) INSTANCE = FirebaseDatabase.getInstance(DB_URL);
        return INSTANCE;
    }

    public static DatabaseReference root() { return db().getReference(); }

    // ── .info ──────────────────────────────────────────────
    public static DatabaseReference infoServerTimeOffset() {
        return db().getReference(".info/serverTimeOffset");
    }
    public static DatabaseReference infoConnected() {
        return db().getReference(".info/connected");
    }

    // ── APP CONFIG ─────────────────────────────────────────
    public static DatabaseReference appConfigRef() { return root().child("appConfig"); }
    public static DatabaseReference adminUidRef()  { return appConfigRef().child("adminUid"); }

    // ── ROLES / USERS / BALANCES ───────────────────────────
    public static DatabaseReference rolesRef()               { return root().child("roles"); }
    public static DatabaseReference role(String uid)         { return rolesRef().child(uid); }

    public static DatabaseReference usersRef()               { return root().child("users"); }
    public static DatabaseReference user(String uid)         { return usersRef().child(uid); }
    public static DatabaseReference userVehicles(String uid) { return user(uid).child("vehicles"); }

    public static DatabaseReference balancesRef()            { return root().child("balances"); }
    public static DatabaseReference balance(String uid)      { return balancesRef().child(uid); }

    public static DatabaseReference activePlatesRef()        { return root().child("activePlates"); }

    // ── ZONES (/zones) ─────────────────────────────────────
    // Struktura: /zones/{zoneId}/name, perHour, perDay
    public static DatabaseReference zonesRef()                    { return root().child("zones"); }
    public static DatabaseReference zone(String zoneId)           { return zonesRef().child(zoneId); }
    public static DatabaseReference zonePricing(String zoneId)    { return zone(zoneId); } // perHour/perDay su direktno na zoni

    // ── PARKING (/parking) ─────────────────────────────────
    // Struktura: /parking/{parkingId}/name, address, zoneId, geo, totalSpaces, spaces
    public static DatabaseReference parkingRef()                              { return root().child("parking"); }
    public static DatabaseReference parkingLot(String parkingId)              { return parkingRef().child(parkingId); }
    public static DatabaseReference parkingZoneSpaces(String parkingId)       { return parkingLot(parkingId).child("spaces"); }
    public static DatabaseReference parkingZoneSpace(String parkingId, String spaceId) {
        return parkingZoneSpaces(parkingId).child(spaceId);
    }

    // ── PARKING SESSIONS ───────────────────────────────────
    public static DatabaseReference sessionsRef()               { return root().child("parkingSessions"); }
    public static DatabaseReference session(String sessionId)   { return sessionsRef().child(sessionId); }

    // ── FINES ──────────────────────────────────────────────
    public static DatabaseReference finesRef()                  { return root().child("fines"); }
    public static DatabaseReference fine(String fineId)         { return finesRef().child(fineId); }

    // ── BALANCE TOPUPS ─────────────────────────────────────
    public static DatabaseReference balanceTopupsRef()          { return root().child("balanceTopups"); }
    public static DatabaseReference balanceTopup(String id)     { return balanceTopupsRef().child(id); }

    // ── MISUSE REPORTS ─────────────────────────────────────
    public static DatabaseReference misuseReportsRef()          { return root().child("misuseReports"); }

    // ── DEPRECATED (kompatibilnost) ────────────────────────
    /** @deprecated Koristi parkingRef() */
    @Deprecated
    public static DatabaseReference parkingLotsRef()            { return parkingRef(); }
    /** @deprecated Koristi parkingLot(id) */
    @Deprecated
    public static DatabaseReference parkingZone(String id)      { return parkingLot(id); }
    /** @deprecated Koristi parkingZoneSpaces(id) */
    @Deprecated
    public static DatabaseReference parkingSpaces(String id)    { return parkingZoneSpaces(id); }
    /** @deprecated Koristi parkingZoneSpace(id, spaceId) */
    @Deprecated
    public static DatabaseReference parkingSpace(String id, String spaceId) {
        return parkingZoneSpace(id, spaceId);
    }

    // ── UTIL ───────────────────────────────────────────────
    public static String sanitizeKey(String raw) {
        if (raw == null) return "";
        return raw.replace(".", "").replace("$", "")
                .replace("#", "").replace("[", "")
                .replace("]", "").replace("/", "");
    }
}