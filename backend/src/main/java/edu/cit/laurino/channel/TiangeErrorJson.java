package edu.cit.laurino.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
class TiangeErrorJson {
    public String error;
    public String message;
}
