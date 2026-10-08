package edu.cit.laurino.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.laurino.InstanceContext;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
class TiangeClient {
    private static final Logger log = LoggerFactory.getLogger(TiangeClient.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient restClient;
    private final TiangeProperties properties;
    private final InstanceContext instanceContext;

    TiangeClient(RestClient tiangeRestClient, TiangeProperties properties, InstanceContext instanceContext) {
        this.restClient = tiangeRestClient;
        this.properties = properties;
        this.instanceContext = instanceContext;
    }

    void sendHeartbeat(String startedAtIso, long uptimeSeconds) {
        try {
            restClient.post()
                    .uri("/instances/heartbeat")
                    .header("X-Client-Id", properties.getClientId())
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("X-Client-Instance", instanceContext.instanceId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new HeartbeatRequestJson(properties.getAppName(), startedAtIso, uptimeSeconds))
                    .retrieve()
                    .body(HeartbeatResponseJson.class);
        } catch (Exception ex) {
            log.warn("Tiangge heartbeat failed: {}", ex.toString());
        }
    }

    boolean publishListings(List<ListingJson> listings) {
        try {
            restClient.put()
                    .uri("/listings")
                    .header("X-Client-Id", properties.getClientId())
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("X-Client-Instance", instanceContext.instanceId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(listings)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception ex) {
            log.warn("Tiangge listings publish failed: {}", ex.toString());
            return false;
        }
    }

    boolean publishStock(List<StockEntryJson> entries) {
        try {
            restClient.put()
                    .uri("/stock")
                    .header("X-Client-Id", properties.getClientId())
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("X-Client-Instance", instanceContext.instanceId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(entries)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception ex) {
            log.warn("Tiangge stock publish failed: {}", ex.toString());
            return false;
        }
    }

    FeedResponseJson fetchFeed(long after, int limit) {
        try {
            return restClient.get()
                    .uri("/feed?after={after}&limit={limit}", after, limit)
                    .header("X-Client-Id", properties.getClientId())
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("X-Client-Instance", instanceContext.instanceId())
                    .retrieve()
                    .body(FeedResponseJson.class);
        } catch (Exception ex) {
            log.warn("Tiangge feed fetch failed: {}", ex.toString());
            return null;
        }
    }

    TiangeCallOutcome decide(String tiangeOrderId, String decision, String shopOrderId, String reason) {
        return postWithRetry(
                "/orders/{orderId}/decision",
                tiangeOrderId,
                new DecisionRequestJson(decision, shopOrderId, reason),
                Set.of("decision_conflict"));
    }

    TiangeCallOutcome resolveBackorder(String tiangeOrderId, String status) {
        return postWithRetry(
                "/orders/{orderId}/resolution",
                tiangeOrderId,
                new ResolutionRequestJson(status),
                Set.of("not_backordered"));
    }

    TiangeCallOutcome confirmCancellation(String tiangeOrderId, boolean restocked) {
        return postWithRetry(
                "/orders/{orderId}/cancellation",
                tiangeOrderId,
                new CancellationConfirmJson(restocked),
                Set.of("not_cancelled"));
    }

    private TiangeCallOutcome postWithRetry(
            String uriTemplate, String tiangeOrderId, Object body, Set<String> alreadyHandledCodes) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                restClient.post()
                        .uri(uriTemplate, tiangeOrderId)
                        .header("X-Client-Id", properties.getClientId())
                        .header("Authorization", "Bearer " + properties.getApiKey())
                        .header("X-Client-Instance", instanceContext.instanceId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                return TiangeCallOutcome.SUCCESS;
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                TiangeErrorJson error = parseError(ex);
                String code = error != null ? error.error : null;

                if (code != null && alreadyHandledCodes.contains(code)) {
                    log.info("Tiangge order {} already handled ({}): {}", tiangeOrderId, code, error.message);
                    return TiangeCallOutcome.ALREADY_HANDLED;
                }
                if (status == 404 && "order_not_found".equals(code)) {
                    log.warn("Tiangge no longer knows order {} (order_not_found) - giving up on it "
                            + "rather than retrying forever (likely a Tiangge-side record reset).", tiangeOrderId);
                    return TiangeCallOutcome.NOT_FOUND;
                }
                if (status == 503 || status >= 500) {
                    log.warn("Tiangge call for order {} failed (attempt {}/{}): HTTP {} {}",
                            tiangeOrderId, attempt, MAX_ATTEMPTS, status, code);
                    sleepBackoff(attempt);
                    continue;
                }
                log.warn("Tiangge call for order {} failed permanently: HTTP {} {}", tiangeOrderId, status, code);
                return TiangeCallOutcome.FAILED;
            } catch (Exception ex) {
                log.warn("Tiangge call for order {} failed (attempt {}/{}): {}",
                        tiangeOrderId, attempt, MAX_ATTEMPTS, ex.toString());
                sleepBackoff(attempt);
            }
        }
        return TiangeCallOutcome.FAILED;
    }

    private TiangeErrorJson parseError(RestClientResponseException ex) {
        try {
            String responseBody = ex.getResponseBodyAsString();
            if (responseBody == null || responseBody.isBlank()) {
                return null;
            }
            return JSON.readValue(responseBody, TiangeErrorJson.class);
        } catch (Exception parseFailure) {
            return null;
        }
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(300L * attempt);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
