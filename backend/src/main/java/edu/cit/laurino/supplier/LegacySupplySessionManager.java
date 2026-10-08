package edu.cit.laurino.supplier;

import edu.cit.laurino.InstanceContext;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Sessions are handled entirely inside this adapter: sign in lazily on first
 * use, and invalidate() lets the client force a fresh sign-in the moment
 * LegacySupply stops accepting the current token (401). No manual token
 * pasting anywhere. Every call carries X-Client-Instance so LegacySupply can
 * tell which running copy of the app made it (Lab 4 requirement).
 */
@Component
class LegacySupplySessionManager {
    private final RestClient restClient;
    private final LegacySupplyProperties properties;
    private final InstanceContext instanceContext;
    private volatile String sessionToken;
    private final Object lock = new Object();

    LegacySupplySessionManager(
            RestClient legacySupplyRestClient,
            LegacySupplyProperties properties,
            InstanceContext instanceContext) {
        this.restClient = legacySupplyRestClient;
        this.properties = properties;
        this.instanceContext = instanceContext;
    }

    String currentSession() {
        String token = sessionToken;
        if (token != null) {
            return token;
        }
        synchronized (lock) {
            if (sessionToken == null) {
                sessionToken = authenticate();
            }
            return sessionToken;
        }
    }

    void invalidate() {
        synchronized (lock) {
            sessionToken = null;
        }
    }

    private String authenticate() {
        AuthRequestXml request = new AuthRequestXml(properties.getClientId(), properties.getApiKey());
        AuthResponseXml response = restClient.post()
                .uri("/auth/token")
                .contentType(MediaType.APPLICATION_XML)
                .header("X-Client-Instance", instanceContext.instanceId())
                .body(request)
                .retrieve()
                .body(AuthResponseXml.class);

        if (response == null || response.sessionToken == null) {
            throw new IllegalStateException("LegacySupply did not return a session token.");
        }
        return response.sessionToken;
    }
}
