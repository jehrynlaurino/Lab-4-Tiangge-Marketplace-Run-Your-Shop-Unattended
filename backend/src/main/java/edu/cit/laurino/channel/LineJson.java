package edu.cit.laurino.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
class LineJson {
    public String sellerSku;
    public int qty;
}
