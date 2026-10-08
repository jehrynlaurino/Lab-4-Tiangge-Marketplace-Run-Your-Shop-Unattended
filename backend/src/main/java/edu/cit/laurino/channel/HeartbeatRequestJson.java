package edu.cit.laurino.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
class HeartbeatRequestJson {
    public String appName;
    public String startedAt;
    public long uptimeSeconds;

    HeartbeatRequestJson() {
    }

    HeartbeatRequestJson(String appName, String startedAt, long uptimeSeconds) {
        this.appName = appName;
        this.startedAt = startedAt;
        this.uptimeSeconds = uptimeSeconds;
    }
}
