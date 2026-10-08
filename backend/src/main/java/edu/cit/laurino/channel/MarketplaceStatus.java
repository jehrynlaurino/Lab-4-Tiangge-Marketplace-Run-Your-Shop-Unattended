package edu.cit.laurino.channel;

import java.time.Instant;

public record MarketplaceStatus(String instanceId, Instant startedAt, long lastProcessedSeq) {
}
