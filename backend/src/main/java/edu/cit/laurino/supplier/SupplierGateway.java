package edu.cit.laurino.supplier;

import java.util.Optional;

/**
 * Public entry point for the supplier module. Order and Inventory may depend
 * on this interface and the domain types it returns, but never on anything
 * else in this package - no XML classes, no LegacySupply item numbers, pack
 * sizes, or status codes. The channel module also depends on this interface
 * (never on supplier internals) to learn a product's supplier SKU for
 * listings, and to check whether a reorder is already in flight before
 * backordering a marketplace order.
 */
public interface SupplierGateway {
    ReorderResult reorder(String productId, int unitsNeeded);

    /**
     * The LegacySupply SupplierSku a product restocks from, if configured.
     */
    Optional<String> supplierSkuFor(String productId);

    /**
     * True if a purchase order for this product is already PENDING, SUBMITTED,
     * PICKING, or SHIPPED - i.e. already "on its way" per Lab 3's definition,
     * not yet DELIVERED or FAILED.
     */
    boolean hasOpenPurchaseOrder(String productId);
}
