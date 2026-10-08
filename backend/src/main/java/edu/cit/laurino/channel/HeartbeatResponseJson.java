package edu.cit.laurino.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
class HeartbeatResponseJson {
    public String serverTime;
    public int nextHeartbeatSeconds;
}
