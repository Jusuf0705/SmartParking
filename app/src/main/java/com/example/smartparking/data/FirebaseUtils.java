package com.example.smartparking.data;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/** Central registry of Firebase Realtime Database references. */
public final class FirebaseUtils {

    private static final String DB_URL =
            "https://smartparking-744d8-default-rtdb.europe-west1.firebasedatabase.app";

    private static FirebaseDatabase instance;

    private FirebaseUtils() {}

    private static synchronized FirebaseDatabase db() {
        if (instance == null) instance = FirebaseDatabase.getInstance(DB_URL);
        return instance;
    }

    public static DatabaseReference root() { return db().getReference(); }

    // Connection / server time
    public static DatabaseReference infoServerTimeOffset() {
        return db().getReference(".info/serverTimeOffset");
    }
    public static DatabaseReference infoConnected() {
        return db().getReference(".info/connected");
    }

    // App config
    public static DatabaseReference appConfigRef() { return root().child("appConfig"); }
    public static DatabaseReference adminUidRef()  { return appConfigRef().child("adminUid"); }

    // Users
    public static DatabaseReference rolesRef()               { return root().child("roles"); }
    public static DatabaseReference role(String uid)         { return rolesRef().child(uid); }

    public static DatabaseReference usersRef()               { return root().child("users"); }
    public static DatabaseReference user(String uid)         { return usersRef().child(uid); }
    public static DatabaseReference userVehicles(String uid) { return user(uid).child("vehicles"); }

    public static DatabaseReference balancesRef()            { return root().child("balances"); }
    public static DatabaseReference balance(String uid)      { return balancesRef().child(uid); }

    public static DatabaseReference activePlatesRef()        { return root().child("activePlates"); }

    // Zones: /zones/{zoneId}/name, perHour, perDay
    public static DatabaseReference zonesRef()                 { return root().child("zones"); }
    public static DatabaseReference zone(String zoneId)        { return zonesRef().child(zoneId); }
    public static DatabaseReference zonePricing(String zoneId) { return zone(zoneId); } // pricing fields on the zone itself

    // Parking lots: /parking/{parkingId}/name, address, zoneId, geo, totalSpaces, spaces
    public static DatabaseReference parkingRef()                        { return root().child("parking"); }
    public static DatabaseReference parkingLot(String parkingId)        { return parkingRef().child(parkingId); }
    public static DatabaseReference parkingZoneSpaces(String parkingId) { return parkingLot(parkingId).child("spaces"); }
    public static DatabaseReference parkingZoneSpace(String parkingId, String spaceId) {
        return parkingZoneSpaces(parkingId).child(spaceId);
    }

    // Sessions / fines / topups / reports
    public static DatabaseReference sessionsRef()              { return root().child("parkingSessions"); }
    public static DatabaseReference session(String sessionId)  { return sessionsRef().child(sessionId); }

    public static DatabaseReference finesRef()          { return root().child("fines"); }
    public static DatabaseReference fine(String fineId) { return finesRef().child(fineId); }

    public static DatabaseReference balanceTopupsRef()      { return root().child("balanceTopups"); }
    public static DatabaseReference balanceTopup(String id) { return balanceTopupsRef().child(id); }

    public static DatabaseReference misuseReportsRef() { return root().child("misuseReports"); }

    /** @deprecated use {@link #parkingRef()} */
    @Deprecated
    public static DatabaseReference parkingLotsRef() { return parkingRef(); }

    /** @deprecated use {@link #parkingLot(String)} */
    @Deprecated
    public static DatabaseReference parkingZone(String id) { return parkingLot(id); }

    /** @deprecated use {@link #parkingZoneSpaces(String)} */
    @Deprecated
    public static DatabaseReference parkingSpaces(String id) { return parkingZoneSpaces(id); }

    /** @deprecated use {@link #parkingZoneSpace(String, String)} */
    @Deprecated
    public static DatabaseReference parkingSpace(String id, String spaceId) {
        return parkingZoneSpace(id, spaceId);
    }

    /** Removes characters Firebase disallows in a key. */
    public static String sanitizeKey(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[.$#\\[\\]/]", "");
    }
}