package edu.cit.laurino.channel;

/**
 * Which Tiangge call is still owed for a not-yet-synced channel order. Lets
 * UnsyncedOrderReconciler resume the correct call after a crash or a
 * transient Tiangge failure, without re-touching Order or Inventory.
 */
enum PendingAction {
    DECISION,
    CANCELLATION,
    RESOLUTION
}
