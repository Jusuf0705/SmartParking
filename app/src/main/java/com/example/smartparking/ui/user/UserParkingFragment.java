package com.example.smartparking.ui.user;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class UserParkingFragment extends Fragment {

    // UI
    private RecyclerView rv;
    private LotsAdapter adapter;
    private MaterialButton btnFilter;

    // Podaci
    private final List<LotRow> allLots = new ArrayList<>();
    private DatabaseReference lotsRef;

    // Lokacija
    private Location lastKnown;
    private static final int REQ_LOC = 1010;

    // Režimi
    private enum Mode {
        PRICE_DESC, PRICE_ASC, FREE_ONLY, FULL_ONLY, DIST_ASC, DIST_DESC
    }
    private Mode currentMode = Mode.PRICE_ASC;

    // Opcije menija
    private static final String[] OPTIONS = new String[]{
            "Cijena najviša",
            "Cijena najniža",
            "Slobodno",
            "Zauzeto",
            "Udaljenost - Najbliža",
            "Udaljenost - Najdalja"
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inf, ViewGroup parent, Bundle b) {
        View v = inf.inflate(R.layout.fragment_user_parking, parent, false);

        // Recycler
        rv = v.findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new LotsAdapter();
        rv.setAdapter(adapter);

        // Filter dugme -> odmah padajući meni
        btnFilter = v.findViewById(R.id.btnFilter);
        if (btnFilter != null) {
            btnFilter.setOnClickListener(view -> showFilterMenu(view));
        }

        // Firebase
        lotsRef = FirebaseUtils.parkingLotsRef();

        // Tiho pokušaj dohvatiti lokaciju (ako već ima dozvolu)
        requestLocationOnce();

        // Učitaj
        loadLots();

        return v;
    }

    /** Klik na dugme -> odmah prikaži padajući meni sa opcijama */
    private void showFilterMenu(View anchor) {
        if (!isAdded()) return;

        PopupMenu pm = new PopupMenu(requireContext(), anchor);

        for (int i = 0; i < OPTIONS.length; i++) {
            pm.getMenu().add(0, i, i, OPTIONS[i]);
        }

        pm.setOnMenuItemClickListener(item -> {
            int position = item.getItemId();

            switch (position) {
                case 0:
                    currentMode = Mode.PRICE_DESC;
                    applyMode();
                    return true;
                case 1:
                    currentMode = Mode.PRICE_ASC;
                    applyMode();
                    return true;
                case 2:
                    currentMode = Mode.FREE_ONLY;
                    applyMode();
                    return true;
                case 3:
                    currentMode = Mode.FULL_ONLY;
                    applyMode();
                    return true;
                case 4:
                    currentMode = Mode.DIST_ASC;
                    ensureLocationThenApply();
                    return true;
                case 5:
                    currentMode = Mode.DIST_DESC;
                    ensureLocationThenApply();
                    return true;
            }
            return false;
        });

        pm.show();
    }

    private void ensureLocationThenApply() {
        if (!isAdded()) return;

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOC);
            // fallback sortiranje bez lokacije
            applyMode();
            return;
        }

        requestLocationOnce();
        recomputeDistances();
        applyMode();
    }

    private void requestLocationOnce() {
        try {
            if (!isAdded()) return;

            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            LocationManager lm = (LocationManager) requireContext().getSystemService(android.content.Context.LOCATION_SERVICE);
            if (lm == null) return;

            String provider = lm.getBestProvider(new Criteria(), true);
            lastKnown = provider == null ? null : lm.getLastKnownLocation(provider);
        } catch (Exception ignored) {}
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_LOC && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            requestLocationOnce();
            recomputeDistances();
            applyMode();
        }
    }

    private void loadLots() {
        if (lotsRef == null) return;

        lotsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                allLots.clear();
                long now = System.currentTimeMillis();

                for (DataSnapshot p : ds.getChildren()) {
                    String id = p.getKey();
                    String name = p.child("name").getValue(String.class);
                    String address = p.child("address").getValue(String.class);
                    Double ph = p.child("pricing").child("perHour").getValue(Double.class);
                    Double pd = p.child("pricing").child("perDay").getValue(Double.class);

                    Object latObj = p.child("geo").child("lat").getValue();
                    Object lngObj = p.child("geo").child("lng").getValue();
                    double lat = toDouble(latObj);
                    double lng = toDouble(lngObj);

                    Integer totalVal = p.child("totalSpaces").getValue(Integer.class);
                    int total = totalVal == null ? 0 : totalVal;

                    int free = 0;
                    DataSnapshot spaces = p.child("spaces");
                    for (DataSnapshot s : spaces.getChildren()) {
                        String st  = s.child("status").getValue(String.class);
                        Long until = s.child("until").getValue(Long.class);
                        boolean expired = (until != null && until <= now);
                        if ("slobodno".equalsIgnoreCase(st) || expired) free++;
                    }

                    LotRow row = new LotRow();
                    row.id = id;
                    row.name = safe(name);
                    row.address = safe(address);
                    row.perHour = ph == null ? 0 : ph;
                    row.perDay = pd == null ? 0 : pd;
                    row.lat = lat;
                    row.lng = lng;
                    row.total = total;
                    row.free = free;

                    if (lastKnown != null && !(row.lat == 0 && row.lng == 0)) {
                        row.distanceKm = distKm(lastKnown.getLatitude(), lastKnown.getLongitude(), row.lat, row.lng);
                    } else {
                        row.distanceKm = -1;
                    }

                    allLots.add(row);
                }

                applyMode();
            }

            @Override public void onCancelled(@NonNull DatabaseError e) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /** Ponovo izračunaj udaljenosti kada se dobije lokacija. */
    private void recomputeDistances() {
        if (lastKnown == null) return;

        double uLat = lastKnown.getLatitude();
        double uLng = lastKnown.getLongitude();

        for (LotRow row : allLots) {
            if (row.lat == 0 && row.lng == 0) {
                row.distanceKm = -1;
            } else {
                row.distanceKm = distKm(uLat, uLng, row.lat, row.lng);
            }
        }
    }

    /** Primijeni trenutno odabrani režim na 'allLots' i prikaži rezultat. */
    private void applyMode() {
        List<LotRow> out = new ArrayList<>(allLots);

        switch (currentMode) {
            case PRICE_DESC:
                out.sort((a, b) -> Double.compare(b.perHour, a.perHour));
                break;

            case PRICE_ASC:
                out.sort(Comparator.comparingDouble(a -> a.perHour));
                break;

            case FREE_ONLY:
                out.removeIf(r -> r.free <= 0);
                break;

            case FULL_ONLY:
                out.removeIf(r -> r.free > 0);
                break;

            case DIST_ASC:
                if (lastKnown == null) {
                    out.sort(Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT)));
                } else {
                    // -1 ide na kraj
                    out.sort(Comparator.comparingDouble(a -> a.distanceKm < 0 ? Double.MAX_VALUE : a.distanceKm));
                }
                break;

            case DIST_DESC:
                if (lastKnown == null) {
                    out.sort((a, b) -> b.name.compareToIgnoreCase(a.name));
                } else {
                    // -1 ide na kraj, veća udaljenost prva
                    out.sort((a, b) -> {
                        boolean aInv = a.distanceKm < 0;
                        boolean bInv = b.distanceKm < 0;
                        if (aInv && bInv) return 0;
                        if (aInv) return 1;   // a na kraj
                        if (bInv) return -1;  // b na kraj
                        return Double.compare(b.distanceKm, a.distanceKm);
                    });
                }
                break;
        }

        adapter.submit(out);
    }

    // ===== utili =====

    private static String safe(String s){ return s == null ? "" : s; }

    private static double distKm(double lat1,double lon1,double lat2,double lon2){
        double R=6371.0;
        double dLat=Math.toRadians(lat2-lat1);
        double dLon=Math.toRadians(lon2-lon1);
        double a=Math.sin(dLat/2)*Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon/2)*Math.sin(dLon/2);
        double c=2*Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0, 1 - a)));
        return R*c;
    }

    private static double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Double) return (Double) v;
        if (v instanceof Long) return ((Long) v).doubleValue();
        if (v instanceof Integer) return ((Integer) v).doubleValue();
        if (v instanceof Float) return ((Float) v).doubleValue();
        if (v instanceof String) {
            try { return Double.parseDouble((String) v); } catch (Exception ignored) { return 0; }
        }
        return 0;
    }

    // ===== model & adapter =====

    static class LotRow {
        String id, name, address;
        double perHour, perDay, lat, lng;
        int total, free;
        double distanceKm;
    }

    class LotsAdapter extends RecyclerView.Adapter<LotsAdapter.VH> {
        List<LotRow> data = new ArrayList<>();
        void submit(List<LotRow> d){ data = d; notifyDataSetChanged(); }

        class VH extends RecyclerView.ViewHolder {
            TextView t1,t2,t3;
            Button btnNav;
            Button btnDetails;

            VH(View v){
                super(v);
                t1=v.findViewById(R.id.rowTitle);
                t2=v.findViewById(R.id.rowSubtitle);
                t3=v.findViewById(R.id.rowExtra);
                btnNav=v.findViewById(R.id.btnNav);
                btnDetails=v.findViewById(R.id.btnDetails);
            }
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            View v=LayoutInflater.from(p.getContext()).inflate(R.layout.row_parking_user, p, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            LotRow r = data.get(pos);

            h.t1.setText(r.name);

            StringBuilder info = new StringBuilder();
            info.append(TextUtils.isEmpty(r.address) ? "(bez adrese)" : r.address).append("\n\n");
            info.append("Cijena 1h - ").append(r.perHour).append(" KM").append("\n");
            info.append("Cijena 24h - ").append(r.perDay).append(" KM").append("\n\n");

            if (r.distanceKm >= 0) {
                info.append("Udaljenost: ~")
                        .append(String.format(Locale.getDefault(), "%.1f", r.distanceKm))
                        .append(" km");
            } else {
                info.append("Udaljenost: —");
            }

            h.t2.setText(info.toString());
            h.t3.setText("Slobodna mjesta: " + r.free + "/" + r.total);

            h.btnNav.setOnClickListener(v -> {
                if (!isValidCoord(r.lat, r.lng)) {
                    Toast.makeText(v.getContext(), "Koordinate parkinga nisu validne.", Toast.LENGTH_SHORT).show();
                    return;
                }

                String label = Uri.encode((r.name + " - " + r.address).trim());
                Uri geoUri = Uri.parse("geo:" + r.lat + "," + r.lng + "?q=" + r.lat + "," + r.lng + "(" + label + ")");
                Intent geoIntent = new Intent(Intent.ACTION_VIEW, geoUri);

                Intent mapsAppIntent = new Intent(Intent.ACTION_VIEW, geoUri);
                mapsAppIntent.setPackage("com.google.android.apps.maps");

                try {
                    v.getContext().startActivity(mapsAppIntent);
                    return;
                } catch (Exception ignored) {}

                try {
                    v.getContext().startActivity(geoIntent);
                    return;
                } catch (Exception ignored) {}

                Uri webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + r.lat + "," + r.lng);
                Intent webIntent = new Intent(Intent.ACTION_VIEW, webUri);

                try {
                    v.getContext().startActivity(Intent.createChooser(webIntent, "Otvori mape"));
                } catch (Exception e) {
                    Toast.makeText(v.getContext(), "Ne mogu otvoriti mape na ovom uređaju.", Toast.LENGTH_SHORT).show();
                }
            });

            if (h.btnDetails != null) {
                h.btnDetails.setOnClickListener(v -> showFreeSpacesDialog(r));
            }
        }

        private boolean isValidCoord(double lat, double lng) {
            if (lat == 0.0 && lng == 0.0) return false;
            return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
        }

        @Override public int getItemCount(){ return data.size(); }
    }

    /** Otvara dialog i prikazuje koja su slobodna mjesta za izabrani parking. */
    private void showFreeSpacesDialog(LotRow lot) {
        if (!isAdded() || getContext() == null) return;
        if (lotsRef == null) return;

        long now = System.currentTimeMillis();

        lotsRef.child(lot.id).child("spaces")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot ds) {
                        List<String> freeList = new ArrayList<>();

                        for (DataSnapshot s : ds.getChildren()) {
                            String spaceId = s.getKey();
                            String st = s.child("status").getValue(String.class);
                            Long until = s.child("until").getValue(Long.class);

                            boolean expired = (until != null && until <= now);
                            boolean isFree = "slobodno".equalsIgnoreCase(st) || expired;

                            if (isFree && spaceId != null) freeList.add(spaceId);
                        }

                        String title = "Slobodna mjesta - " + lot.name;

                        if (freeList.isEmpty()) {
                            new AlertDialog.Builder(requireContext())
                                    .setTitle(title)
                                    .setMessage("Trenutno nema slobodnih mjesta.")
                                    .setPositiveButton("OK", null)
                                    .show();
                            return;
                        }

                        String[] items = freeList.toArray(new String[0]);

                        new AlertDialog.Builder(requireContext())
                                .setTitle(title)
                                .setItems(items, null)
                                .setPositiveButton("Zatvori", null)
                                .show();
                    }

                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }
}
