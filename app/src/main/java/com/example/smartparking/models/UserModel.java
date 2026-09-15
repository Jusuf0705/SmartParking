package com.example.smartparking.models;

public class UserModel {
    public String uid;
    public String email;
    public String firstName;
    public String lastName;
    public String plate;   // optional
    public long createdAt;

    /** Required by Firebase for deserialization. */
    public UserModel() {
    }

    public UserModel(String uid, String email, String firstName, String lastName, String plate, long createdAt) {
        this.uid = uid;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.plate = plate;
        this.createdAt = createdAt;
    }

    public String getFullName() {
        return (firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName);
    }
}