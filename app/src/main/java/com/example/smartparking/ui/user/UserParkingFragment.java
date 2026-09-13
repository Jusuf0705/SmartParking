package com.example.smartparking.ui.user;

import android.Manifest;
import android.content.Context;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartparking.R;
import com.example.smartparking.data.FirebaseUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UserParkingFragment extends Fragment {

    private RecyclerView rv;
    private LotsAdapter adapter;

    // Hero kartica
    private TextView tvTotalFree, tvTotalSpots;

    // Svi filter chipovi
    private TextView chipAll, chipFree, chipFull;
    private TextView chipZone1, chipZone2, chipZone3;
    private TextView chipDistAsc, chipDistDesc;
    private TextView chipPriceAsc, chipPriceDesc;

    private final List<LotRow> allLots = new ArrayList<>();
    private final Map<String, ZoneItem> zonesMap = new HashMap<>();
    private final Map<String, ValueEventListener> spaceListeners = new HashMap<>();

    private Location lastKnown;
    private static final int REQ_LOC = 1010;

    // Objedinjeni filter mode koji obuhvata: sve/status filter + sortiranje + zona filter
    private enum FilterMode {
        ALL,
        FREE_ONLY, FULL_ONLY,
        ZONE_1, ZONE_2, ZONE_3,
        DIST_ASC, DIST_DESC,
        PRICE_ASC, PRICE_DESC
    }
    private FilterMode currentFilter = FilterMode.ALL;

    @Override
    public View onCreateView(@NonNull LayoutInflater inf, ViewGroup parent, Bundle b) {
        View v = inf.inflate(R.layout.fragment_user_parking, parent, false);

        rv = v.findViewById(R.id.rvParkingList);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new LotsAdapter();
        rv.setAdapter(adapter);

        tvTotalFree  = v.findViewById(R.id.tvTotalFree);
        tvTotalSpots = v.findViewById(R.id.tvTotalSpots);

        chipAll        = v.findViewById(R.id.btnZoneAll);
        chipFree       = v.findViewById(R.id.btnFilterFree);
        chipFull       = v.findViewById(R.id.btnFilterFull);
        chipZone1      = v.findViewById(R.id.btnZone1);
        chipZone2      = v.findViewById(R.id.btnZone2);
        chipZone3      = v.findViewById(R.id.btnZone3);
        chipDistAsc    = v.findViewById(R.id.btnFilterDistAsc);
        chipDistDesc   = v.findViewById(R.id.btnFilterDistDesc);
        chipPriceAsc   = v.findViewById(R.id.btnFilterPriceAsc);
        chipPriceDesc  = v.findViewById(R.id.btnFilterPriceDesc);

        setChipClick(chipAll,       FilterMode.ALL);
        setChipClick(chipFree,      FilterMode.FREE_ONLY);
        setChipClick(chipFull,      FilterMode.FULL_ONLY);
        setChipClick(chipZone1,     FilterMode.ZONE_1);
        setChipClick(chipZone2,     FilterMode.ZONE_2);
        setChipClick(chipZone3,     FilterMode.ZONE_3);
        setChipClick(chipDistAsc,   FilterMode.DIST_ASC);
        setChipClick(chipDistDesc,  FilterMode.DIST_DESC);
        setChipClick(chipPriceAsc,  FilterMode.PRICE_ASC);
        setChipClick(chipPriceDesc, FilterMode.PRICE_DESC);

        requestLocationOnce();
        loadZonesThenParkings();
        return v;
    }

    private void setChipClick(TextView chip, FilterMode mode) {
        if (chip == null) return;
        chip.setOnClickListener(v -> selectFilter(mode));
    }

    private void selectFilter(FilterMode mode) {
        // Distanca chipovi traže lokaciju
        if ((mode == FilterMode.DIST_ASC || mode == FilterMode.DIST_DESC) && lastKnown == null) {
            if (isAdded() && ActivityCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOC);
            } else {
                requestLocationOnce();
                recomputeDistances();
            }
        }

        currentFilter = mode;
        updateChipStyles();
        applyFilter();
    }

    private void updateChipStyles() {
        setChipStyle(chipAll,        currentFilter == FilterMode.ALL);
        setChipStyle(chipFree,       currentFilter == FilterMode.FREE_ONLY);
        setChipStyle(chipFull,       currentFilter == FilterMode.FULL_ONLY);
        setChipStyle(chipZone1,      currentFilter == FilterMode.ZONE_1);
        setChipStyle(chipZone2,      currentFilter == FilterMode.ZONE_2);
        setChipStyle(chipZone3,      currentFilter == FilterMode.ZONE_3);
        setChipStyle(chipDistAsc,    currentFilter == FilterMode.DIST_ASC);
        setChipStyle(chipDistDesc,   currentFilter == FilterMode.DIST_DESC);
        setChipStyle(chipPriceAsc,   currentFilter == FilterMode.PRICE_ASC);
        setChipStyle(chipPriceDesc,  currentFilter == FilterMode.PRICE_DESC);
    }

    private void setChipStyle(TextView chip, boolean active) {
        if (chip == null || !isAdded()) return;
        chip.setBackgroundResource(active
                ? R.drawable.bg_filter_chip_active
                : R.drawable.bg_filter_chip_inactive);
        chip.setTextColor(ContextCompat.getColor(requireContext(),
                active ? R.color.blue_600 : R.color.white));
    }

    private void updateHeaderStats() {
        int totalFree = 0, totalSpaces = 0;
        for (LotRow r : allLots) {
            totalFree   += r.free;
            totalSpaces += r.total;
        }
        if (tvTotalFree  != null) tvTotalFree.setText(String.valueOf(totalFree));
        if (tvTotalSpots != null) tvTotalSpots.setText("/ " + totalSpaces);
    }

    private void requestLocationOnce() {
        try {
            if (!isAdded()) return;
            if (ActivityCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
            LocationManager lm = (LocationManager) requireContext()
                    .getSystemService(Context.LOCATION_SERVICE);
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
            applyFilter();
        }
    }

    private void loadZonesThenParkings() {
        FirebaseUtils.zonesRef().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                zonesMap.clear();
                for (DataSnapshot z : ds.getChildren()) {
                    String id   = z.getKey();
                    String name = z.child("name").getValue(String.class);
                    Double ph   = z.child("perHour").getValue(Double.class);
                    Double pd   = z.child("perDay").getValue(Double.class);
                    if (id == null) continue;
                    ZoneItem zi = new ZoneItem();
                    zi.id      = id;
                    zi.name    = name == null ? id : name;
                    zi.perHour = ph == null ? 0.0 : ph;
                    zi.perDay  = pd == null ? 0.0 : pd;
                    zonesMap.put(id, zi);
                }
                loadParkings();
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                if (getContext() != null)
                    Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadParkings() {
        FirebaseUtils.parkingRef().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                allLots.clear();
                for (DataSnapshot p : ds.getChildren()) {
                    String id      = p.getKey();
                    String name    = p.child("name").getValue(String.class);
                    String address = p.child("address").getValue(String.class);
                    String zoneId  = p.child("zoneId").getValue(String.class);
                    double lat     = toDouble(p.child("geo").child("lat").getValue());
                    double lng     = toDouble(p.child("geo").child("lng").getValue());
                    Integer totalV = p.child("totalSpaces").getValue(Integer.class);
                    int total      = totalV == null ? 0 : totalV;

                    ZoneItem zone   = zonesMap.get(zoneId);
                    double perHour  = zone != null ? zone.perHour : 0.0;
                    double perDay   = zone != null ? zone.perDay  : 0.0;
                    String zoneName = zone != null ? zone.name    : "—";

                    int free = 0;
                    for (DataSnapshot s : p.child("spaces").getChildren()) {
                        String st = s.child("status").getValue(String.class);
                        if ("slobodno".equalsIgnoreCase(st)) free++;
                    }

                    LotRow row     = new LotRow();
                    row.id         = id;
                    row.name       = safe(name);
                    row.address    = safe(address);
                    row.zoneId     = zoneId == null ? "" : zoneId;
                    row.zoneName   = zoneName;
                    row.perHour    = perHour;
                    row.perDay     = perDay;
                    row.lat        = lat;
                    row.lng        = lng;
                    row.total      = total;
                    row.free       = free;
                    row.distanceKm = (lastKnown != null && !(lat == 0 && lng == 0))
                            ? distKm(lastKnown.getLatitude(), lastKnown.getLongitude(), lat, lng) : -1;
                    allLots.add(row);

                    attachSpaceListener(row);
                }
                updateHeaderStats();
                updateChipStyles();
                applyFilter();
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                if (getContext() != null)
                    Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void attachSpaceListener(LotRow row) {
        if (row.id == null) return;
        ValueEventListener listener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                int free = 0, total = 0;
                for (DataSnapshot s : ds.getChildren()) {
                    String st = s.child("status").getValue(String.class);
                    total++;
                    if ("slobodno".equalsIgnoreCase(st)) free++;
                }
                row.free = free;
                if (total > 0) row.total = total;

                if (isAdded())
                    requireActivity().runOnUiThread(() -> {
                        adapter.notifyDataSetChanged();
                        updateHeaderStats();
                    });
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        spaceListeners.put(row.id, listener);
        FirebaseUtils.parkingLot(row.id).child("spaces").addValueEventListener(listener);
    }

    private void recomputeDistances() {
        if (lastKnown == null) return;
        double uLat = lastKnown.getLatitude(), uLng = lastKnown.getLongitude();
        for (LotRow row : allLots)
            row.distanceKm = (row.lat == 0 && row.lng == 0) ? -1
                    : distKm(uLat, uLng, row.lat, row.lng);
    }

    // Objedinjeni filter po chipu
    private void applyFilter() {
        List<LotRow> out = new ArrayList<>(allLots);

        switch (currentFilter) {
            case ALL:
                out.sort(Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT)));
                break;

            case FREE_ONLY:
                out.removeIf(r -> r.free <= 0);
                out.sort(Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT)));
                break;

            case FULL_ONLY:
                out.removeIf(r -> r.free > 0);
                out.sort(Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT)));
                break;

            case ZONE_1:
                out.removeIf(r -> !"Zona 1".equalsIgnoreCase(r.zoneName));
                out.sort(Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT)));
                break;

            case ZONE_2:
                out.removeIf(r -> !"Zona 2".equalsIgnoreCase(r.zoneName));
                out.sort(Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT)));
                break;

            case ZONE_3:
                out.removeIf(r -> !"Zona 3".equalsIgnoreCase(r.zoneName));
                out.sort(Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT)));
                break;

            case DIST_ASC:
                if (lastKnown == null) {
                    out.sort(Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT)));
                } else {
                    out.sort(Comparator.comparingDouble(
                            a -> a.distanceKm < 0 ? Double.MAX_VALUE : a.distanceKm));
                }
                break;

            case DIST_DESC:
                if (lastKnown == null) {
                    out.sort((a, b) -> b.name.compareToIgnoreCase(a.name));
                } else {
                    out.sort((a, b) -> {
                        if (a.distanceKm < 0 && b.distanceKm < 0) return 0;
                        if (a.distanceKm < 0) return 1;
                        if (b.distanceKm < 0) return -1;
                        return Double.compare(b.distanceKm, a.distanceKm);
                    });
                }
                break;

            case PRICE_ASC:
                out.sort(Comparator.comparingDouble(a -> a.perHour));
                break;

            case PRICE_DESC:
                out.sort((a, b) -> Double.compare(b.perHour, a.perHour));
                break;
        }
        adapter.submit(out);
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static double distKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon/2) * Math.sin(dLon/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0, 1-a)));
    }

    private static double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Double)  return (Double) v;
        if (v instanceof Long)    return ((Long) v).doubleValue();
        if (v instanceof Integer) return ((Integer) v).doubleValue();
        if (v instanceof Float)   return ((Float) v).doubleValue();
        if (v instanceof String)  { try { return Double.parseDouble((String) v); } catch (Exception ignored) {} }
        return 0;
    }

    /**
     * "Prirodno" poređenje stringova (npr. "2" < "10"), umjesto čisto
     * leksikografskog (gdje bi "10" ispalo prije "2"). Podržava i oznake
     * mjesta poput "A1", "A2", "A10".
     */
    private static int naturalCompare(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int startI = i, startJ = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                String numA = a.substring(startI, i);
                String numB = b.substring(startJ, j);
                long na = Long.parseLong(numA);
                long nb = Long.parseLong(numB);
                int cmp = Long.compare(na, nb);
                if (cmp != 0) return cmp;
                cmp = Integer.compare(numA.length(), numB.length());
                if (cmp != 0) return cmp;
            } else {
                int cmp = Character.toLowerCase(ca) - Character.toLowerCase(cb);
                if (cmp != 0) return cmp;
                i++; j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }

    static class LotRow {
        String id, name, address, zoneId, zoneName;
        double perHour, perDay, lat, lng, distanceKm;
        int total, free;
    }

    static class ZoneItem {
        String id, name;
        double perHour, perDay;
    }

    static class SpaceItem {
        String id;
        boolean free;
        SpaceItem(String id, boolean free) { this.id = id; this.free = free; }
    }

    class LotsAdapter extends RecyclerView.Adapter<LotsAdapter.VH> {

        List<LotRow> data = new ArrayList<>();

        void submit(List<LotRow> d) { data = d; notifyDataSetChanged(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvSubtitle, tvZone, tvStatus, tvSpotCount, tvDistance;
            ProgressBar progressAvailability;
            View btnDistance;
            Button btnDetails;

            VH(View v) {
                super(v);
                tvTitle               = v.findViewById(R.id.tvParkingName);
                tvSubtitle            = v.findViewById(R.id.tvParkingAddress);
                tvZone                = v.findViewById(R.id.tvZone);
                tvStatus              = v.findViewById(R.id.tvStatus);
                tvSpotCount           = v.findViewById(R.id.tvSpotCount);
                progressAvailability  = v.findViewById(R.id.progressAvailability);
                btnDistance           = v.findViewById(R.id.btnDistance);
                tvDistance            = v.findViewById(R.id.tvDistance);
                btnDetails            = v.findViewById(R.id.btnDetails);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            return new VH(LayoutInflater.from(p.getContext())
                    .inflate(R.layout.row_parking_user, p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            LotRow r = data.get(pos);
            Context ctx = h.itemView.getContext();

            h.tvTitle.setText(r.name);
            h.tvSubtitle.setText(TextUtils.isEmpty(r.address) ? "—" : r.address);
            h.tvZone.setText(TextUtils.isEmpty(r.zoneName) ? "—" : r.zoneName);

            int percentFree = r.total > 0 ? Math.round(r.free * 100f / r.total) : 0;
            h.tvSpotCount.setText(r.free + "/" + r.total + " mjesta");
            h.progressAvailability.setMax(100);
            h.progressAvailability.setProgress(percentFree);

            if (r.free <= 0) {
                h.tvStatus.setText("Puno");
                h.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.red_500));
                h.tvStatus.setBackgroundResource(R.drawable.bg_status_full);
                h.progressAvailability.setProgressDrawable(
                        ContextCompat.getDrawable(ctx, R.drawable.progress_full));
                h.btnDetails.setText("Nema mjesta");
                h.btnDetails.setEnabled(false);
                h.btnDetails.setAlpha(0.5f);
            } else if (percentFree <= 25) {
                h.tvStatus.setText("Ograničeno");
                h.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.amber_700));
                h.tvStatus.setBackgroundResource(R.drawable.bg_status_limited);
                h.progressAvailability.setProgressDrawable(
                        ContextCompat.getDrawable(ctx, R.drawable.progress_limited));
                h.btnDetails.setText("Detalji");
                h.btnDetails.setEnabled(true);
                h.btnDetails.setAlpha(1f);
            } else {
                h.tvStatus.setText("Slobodno");
                h.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.green_700));
                h.tvStatus.setBackgroundResource(R.drawable.bg_status_available);
                h.progressAvailability.setProgressDrawable(
                        ContextCompat.getDrawable(ctx, R.drawable.progress_available));
                h.btnDetails.setText("Detalji");
                h.btnDetails.setEnabled(true);
                h.btnDetails.setAlpha(1f);
            }

            if (r.distanceKm >= 0) {
                String distText;
                if (r.distanceKm < 1.0) {
                    int m = (int) Math.round(r.distanceKm * 1000);
                    distText = m + " m";
                } else {
                    distText = String.format(Locale.getDefault(), "%.1f km", r.distanceKm);
                }
                h.tvDistance.setText(distText);
            } else {
                h.tvDistance.setText("— km");
            }

            h.btnDistance.setOnClickListener(v -> {
                if (r.lat == 0 && r.lng == 0) {
                    Toast.makeText(v.getContext(), "Koordinate nisu validne.", Toast.LENGTH_SHORT).show();
                    return;
                }
                String label = Uri.encode((r.name + " - " + r.address).trim());
                Uri geoUri = Uri.parse("geo:" + r.lat + "," + r.lng
                        + "?q=" + r.lat + "," + r.lng + "(" + label + ")");
                Intent mi = new Intent(Intent.ACTION_VIEW, geoUri);
                mi.setPackage("com.google.android.apps.maps");
                try { v.getContext().startActivity(mi); return; } catch (Exception ignored) {}
                try { v.getContext().startActivity(new Intent(Intent.ACTION_VIEW, geoUri)); return; }
                catch (Exception ignored) {}
                Uri webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query="
                        + r.lat + "," + r.lng);
                try {
                    v.getContext().startActivity(
                            Intent.createChooser(new Intent(Intent.ACTION_VIEW, webUri), "Otvori mape"));
                } catch (Exception e) {
                    Toast.makeText(v.getContext(), "Ne mogu otvoriti mape.", Toast.LENGTH_SHORT).show();
                }
            });

            h.btnDetails.setOnClickListener(v -> showFreeSpacesDialog(r));
        }

        @Override
        public int getItemCount() { return data.size(); }
    }

    // ---------------------------------------------------------------
    // Bottom sheet dijalog sa stanjem mjesta (zamjena za AlertDialog).
    // Sadržaj dolazi iz res/layout/dialog_free_spaces.xml.
    // ---------------------------------------------------------------

    class SpaceStatusAdapter extends RecyclerView.Adapter<SpaceStatusAdapter.VH> {
        private final List<SpaceItem> items;
        SpaceStatusAdapter(List<SpaceItem> items) { this.items = items; }

        class VH extends RecyclerView.ViewHolder {
            TextView tvLabel;
            VH(View v) {
                super(v);
                tvLabel = v.findViewById(R.id.tvSpaceLabel);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            return new VH(LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_space_status, p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            SpaceItem it = items.get(pos);
            Context ctx = h.itemView.getContext();

            h.tvLabel.setText(it.id);
            h.tvLabel.setTextColor(ContextCompat.getColor(ctx,
                    it.free ? R.color.green_700 : R.color.red_500));
        }

        @Override
        public int getItemCount() { return items.size(); }
    }

    private void showFreeSpacesDialog(LotRow lot) {
        if (!isAdded() || getContext() == null) return;
        FirebaseUtils.parkingLot(lot.id).child("spaces")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot ds) {
                        List<SpaceItem> items = new ArrayList<>();
                        for (DataSnapshot s : ds.getChildren()) {
                            String spaceId = s.getKey();
                            String st = s.child("status").getValue(String.class);
                            if (spaceId == null) continue;
                            items.add(new SpaceItem(spaceId, "slobodno".equalsIgnoreCase(st)));
                        }
                        items.sort((a, b) -> naturalCompare(a.id, b.id));
                        if (isAdded()) showSpacesBottomSheet(lot, items);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        if (getContext() != null)
                            Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showSpacesBottomSheet(LotRow lot, List<SpaceItem> items) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheet = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_free_spaces, null);
        dialog.setContentView(sheet);

        TextView tvTitle = sheet.findViewById(R.id.tvSheetTitle);
        TextView tvEmpty = sheet.findViewById(R.id.tvSheetEmpty);
        RecyclerView rvSpaces = sheet.findViewById(R.id.rvSpaces);

        tvTitle.setText(lot.name);

        if (items.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvSpaces.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvSpaces.setVisibility(View.VISIBLE);
            rvSpaces.setLayoutManager(new GridLayoutManager(requireContext(), 5));
            rvSpaces.setAdapter(new SpaceStatusAdapter(items));
        }

        dialog.show();
    }
}