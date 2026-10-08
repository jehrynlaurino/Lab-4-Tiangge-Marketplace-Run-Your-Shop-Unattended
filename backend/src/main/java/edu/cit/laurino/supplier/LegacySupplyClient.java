package edu.cit.laurino.supplier;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import edu.cit.laurino.InstanceContext;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Low-level HTTP/XML boundary with LegacySupply. Retries up to 3 attempts per
 * call with backoff (Part D), refreshes the session once on 401, and treats
 * 429/503/timeouts as retryable versus other 4xx as terminal - a bad SKU or
 * bad quantity will never succeed no matter how many times it's retried.
 * Every call carries X-Client-Instance (Lab 4 requirement).
 */
@Component
class LegacySupplyClient {
    private static final Logger log = LoggerFactory.getLogger(LegacySupplyClient.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final XmlMapper XML = new XmlMapper();

    private final RestClient restClient;
    private final LegacySupplySessionManager sessionManager;
    private final InstanceContext instanceContext;

    LegacySupplyClient(
            RestClient legacySupplyRestClient,
            LegacySupplySessionManager sessionManager,
            InstanceContext instanceContext) {
        this.restClient = legacySupplyRestClient;
        this.sessionManager = sessionManager;
        this.instanceContext = instanceContext;
    }

    SubmitOutcome submitPurchaseOrder(String supplierSku, int qty, String buyerRef, String requestId) {
        PurchaseOrderXml body = new PurchaseOrderXml(supplierSku, qty, buyerRef);
        boolean sessionRetried = false;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                PurchaseOrderAckXml ack = restClient.post()
                        .uri("/purchase-orders")
                        .contentType(MediaType.APPLICATION_XML)
                        .header("X-LS-Session", sessionManager.currentSession())
                        .header("X-Request-Id", requestId)
                        .header("X-Client-Instance", instanceContext.instanceId())
                        .body(body)
                        .retrieve()
                        .body(PurchaseOrderAckXml.class);

                if (ack == null) {
                    return SubmitOutcome.retryableFailure("LegacySupply returned an empty response.");
                }
                return SubmitOutcome.success(ack.poNumber, ack.statusCode);
            } catch (RestClientResponseException ex) {
                int httpStatus = ex.getStatusCode().value();
                LSErrorXml error = parseError(ex);
                String code = error != null ? error.code : "UNKNOWN";
                log.warn("LegacySupply order submit failed (attempt {}/{}): HTTP {} {}",
                        attempt, MAX_ATTEMPTS, httpStatus, code);

                if (httpStatus == 401 && !sessionRetried) {
                    sessionManager.invalidate();
                    sessionRetried = true;
                    continue;
                }
                if (isRetryable(httpStatus)) {
                    sleepBackoff(attempt);
                    continue;
                }
                return SubmitOutcome.terminalFailure(
                        code + ": " + (error != null ? error.message : ex.getMessage()));
            } catch (Exception ex) {
                log.warn("LegacySupply order submit failed (attempt {}/{}): {}", attempt, MAX_ATTEMPTS, ex.toString());
                sleepBackoff(attempt);
            }
        }
        return SubmitOutcome.retryableFailure("LegacySupply did not respond after " + MAX_ATTEMPTS + " attempts.");
    }

    Optional<Integer> fetchStatusCode(String poNumber) {
        try {
            PurchaseOrderStatusXml status = restClient.get()
                    .uri("/purchase-orders/{poNumber}", poNumber)
                    .header("X-LS-Session", sessionManager.currentSession())
                    .header("X-Client-Instance", instanceContext.instanceId())
                    .retrieve()
                    .body(PurchaseOrderStatusXml.class);
            return status != null ? Optional.of(status.statusCode) : Optional.empty();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) {
                sessionManager.invalidate();
            }
            log.warn("LegacySupply status check failed for {}: HTTP {}", poNumber, ex.getStatusCode().value());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("LegacySupply status check failed for {}: {}", poNumber, ex.toString());
            return Optional.empty();
        }
    }

    private boolean isRetryable(int httpStatus) {
        return httpStatus == 429 || httpStatus == 503;
    }

    private LSErrorXml parseError(RestClientResponseException ex) {
        try {
            String responseBody = ex.getResponseBodyAsString();
            if (responseBody == null || responseBody.isBlank()) {
                return null;
            }
            return XML.readValue(responseBody, LSErrorXml.class);
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
