package edu.cit.laurino.channel;

/**
 * Our own bookkeeping of a Tiangge order's outcome - mirrors but is distinct
 * from Tiangge's own "decision"/"status" vocabulary.
 */
enum ChannelDecision {
    PENDING,
    ACCEPTED,
    REJECTED,
    BACKORDERED,
    CANCELLED
}
