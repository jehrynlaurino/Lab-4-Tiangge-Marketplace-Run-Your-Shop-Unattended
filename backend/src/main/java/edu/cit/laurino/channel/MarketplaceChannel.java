package edu.cit.laurino.channel;

/**
 * Public entry point for the channel module. Nothing else in this app needs
 * to call into channel to do its job (it only calls out to Order, Inventory
 * and Supplier), but this exists for introspection/debugging - e.g. an
 * admin endpoint could report the live instance ID and cursor position.
 */
public interface MarketplaceChannel {
    MarketplaceStatus currentStatus();
}
