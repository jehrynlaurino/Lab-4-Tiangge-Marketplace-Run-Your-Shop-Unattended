package edu.cit.laurino.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Single-row table (id is always 1) holding the last processed feed
 * sequence number. Read on every poll, updated after a batch of events is
 * safely handled, so a restarted app resumes instead of re-reading the
 * whole feed from the start.
 */
@Entity
@Table(name = "channel_feed_cursor")
class ChannelFeedCursorEntity {
    @Id
    private Short id;

    @Column(name = "last_seq", nullable = false)
    private long lastSeq;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChannelFeedCursorEntity() {
        // Required by JPA.
    }

    long getLastSeq() {
        return lastSeq;
    }

    void advanceTo(long newSeq) {
        this.lastSeq = newSeq;
        this.updatedAt = Instant.now();
    }
}
