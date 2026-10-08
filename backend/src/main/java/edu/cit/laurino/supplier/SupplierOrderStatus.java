package edu.cit.laurino.supplier;

import java.util.List;

/**
 * Our own domain status - never LegacySupply's numeric StatusCode.
 */
public enum SupplierOrderStatus {
    PENDING,
    SUBMITTED,
    PICKING,
    SHIPPED,
    DELIVERED,
    FAILED;

    /**
     * Statuses that mean "already ordered from the supplier and still on its
     * way" - used by Lab 4's backorder eligibility check.
     */
    static List<SupplierOrderStatus> openStatuses() {
        return List.of(PENDING, SUBMITTED, PICKING, SHIPPED);
    }
}
