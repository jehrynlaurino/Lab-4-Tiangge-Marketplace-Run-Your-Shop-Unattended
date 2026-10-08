package edu.cit.laurino.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
class ListingJson {
    public String sellerSku;
    public String title;
    public String supplierSku;

    ListingJson() {
    }

    ListingJson(String sellerSku, String title, String supplierSku) {
        this.sellerSku = sellerSku;
        this.title = title;
        this.supplierSku = supplierSku;
    }
}
