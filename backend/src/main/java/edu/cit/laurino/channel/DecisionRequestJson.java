package edu.cit.laurino.channel;

class DecisionRequestJson {
    public String decision;
    public String shopOrderId;
    public String reason;

    DecisionRequestJson(String decision, String shopOrderId, String reason) {
        this.decision = decision;
        this.shopOrderId = shopOrderId;
        this.reason = reason;
    }
}
