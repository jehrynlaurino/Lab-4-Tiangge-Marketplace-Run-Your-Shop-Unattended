package edu.cit.laurino.inventory;

/**
 * Published every time a product's available stock actually changes, whether
 * from a reservation or a restock - regardless of what caused it (a Tiangge
 * order, a React UI order, a cancellation, or a supplier delivery). The
 * channel module listens for this to keep Tiangge's stock figures in sync;
 * it must never be replaced with a direct call into the channel package.
 */
public record InventoryStockChanged(String productId, int available) {
}
