package edu.cit.laurino.channel;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the app.tiangge.* configuration block. Credentials are the SAME
 * client ID / API key used for LegacySupply (per the Tiangge manual),
 * read from env vars via application.yml - never hardcoded.
 */
@Component
@ConfigurationProperties(prefix = "app.tiangge")
class TiangeProperties {
    private String baseUrl;
    private String clientId;
    private String apiKey;
    private String appName = "laurino-shop";
    private List<String> listedProducts = List.of();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public List<String> getListedProducts() {
        return listedProducts;
    }

    public void setListedProducts(List<String> listedProducts) {
        this.listedProducts = listedProducts;
    }
}
