package com.example.smartparking.models;

public class ParkingLot {
    public String name;
    public String address;
    public double lat;
    public double lng;
    public double perHour;
    public double perDay;

    /** Required by Firebase for deserialization. */
    public ParkingLot() {
    }

    public ParkingLot(String name, String address, double lat, double lng, double perHour, double perDay) {
        this.name = name;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
        this.perHour = perHour;
        this.perDay = perDay;
    }

    @Override
    public String toString() {
        return "ParkingLot{name='" + name + "', address='" + address + "', perHour=" + perHour + ", perDay=" + perDay + "}";
    }
}