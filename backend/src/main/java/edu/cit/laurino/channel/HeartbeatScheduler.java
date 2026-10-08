package edu.cit.laurino.channel;

import edu.cit.laurino.InstanceContext;
import java.time.Duration;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the instance looking "online" to Tiangge (which treats a 90-second
 * silence as offline). First automatic run is delayed past startup so it
 * never races MarketplaceStartup's own synchronous first heartbeat.
 */
@Component
class HeartbeatScheduler {
    private final TiangeClient tiangeClient;
    private final InstanceContext instanceContext;

    HeartbeatScheduler(TiangeClient tiangeClient, InstanceContext instanceContext) {
        this.tiangeClient = tiangeClient;
        this.instanceContext = instanceContext;
    }

    @Scheduled(
            initialDelayString = "${app.tiangge.heartbeat-interval-ms:30000}",
            fixedDelayString = "${app.tiangge.heartbeat-interval-ms:30000}")
    void sendHeartbeat() {
        long uptimeSeconds = Duration.between(instanceContext.startedAt(), Instant.now()).getSeconds();
        tiangeClient.sendHeartbeat(instanceContext.startedAt().toString(), uptimeSeconds);
    }
}
