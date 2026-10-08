package edu.cit.laurino;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * One instance ID is generated when the JVM starts and reused for every call
 * this process makes to Tiangge AND LegacySupply, so both systems can tell
 * which running copy made a given request. Shared at the root package so the
 * channel and supplier modules both depend on it without depending on each
 * other.
 */
@Component
public class InstanceContext {
    private final String instanceId = UUID.randomUUID().toString();
    private final Instant startedAt = Instant.now();

    public String instanceId() {
        return instanceId;
    }

    public Instant startedAt() {
        return startedAt;
    }
}
