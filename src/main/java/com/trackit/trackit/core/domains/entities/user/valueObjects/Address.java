package com.trackit.trackit.core.domains.entities.user.valueObjects;

public class Address {
    public String country;
    public String city;
    public String state;
    public String streetAddress;
    public String zipCode;

    public Address() {}

    public Address(String country, String city, String state, String streetAddress, String zipCode) {
        this.country = country;
        this.city = city;
        this.state = state;
        this.streetAddress = streetAddress;
        this.zipCode = zipCode;
    }
}
