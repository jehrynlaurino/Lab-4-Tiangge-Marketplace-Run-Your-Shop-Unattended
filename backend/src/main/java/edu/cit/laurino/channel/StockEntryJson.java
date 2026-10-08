package edu.cit.laurino.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
class StockEntryJson {
    public String sellerSku;
    public int available;

    StockEntryJson() {
    }

    StockEntryJson(String sellerSku, int available) {
        this.sellerSku = sellerSku;
        this.available = available;
    }
}
