package com.logitrust.domain;

/**
 * The six platform actors defined in the SRS (section 3). ADMIN is never
 * self-registrable via the public API — it is provisioned out of band.
 */
public enum Role {
    MANUFACTURER,
    DISTRIBUTOR,
    COURIER,
    RETAILER,
    CUSTOMER,
    ADMIN
}
