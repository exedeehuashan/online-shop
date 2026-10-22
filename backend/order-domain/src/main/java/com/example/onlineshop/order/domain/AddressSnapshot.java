package com.example.onlineshop.order.domain;

public record AddressSnapshot(String recipient, String line1, String line2, String city, String region,
                              String postalCode, String countryCode, String phone) {
  public AddressSnapshot {
    recipient = Validation.required(recipient, 200, "recipient");
    line1 = Validation.required(line1, 200, "address line 1");
    line2 = Validation.optional(line2, 200, "address line 2");
    city = Validation.required(city, 100, "city");
    region = Validation.optional(region, 100, "region");
    postalCode = Validation.required(postalCode, 32, "postal code");
    countryCode = Validation.required(countryCode, 2, "country code").toUpperCase();
    if (countryCode.length() != 2) throw Validation.invalid("country code must contain two letters");
    phone = Validation.optional(phone, 32, "phone");
  }
}
