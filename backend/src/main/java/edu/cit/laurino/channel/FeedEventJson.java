package edu.cit.laurino.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * One flexible shape covering both ORDER_PLACED and ORDER_CANCELLED events -
 * fields that don't apply to a given type are simply left null by Jackson.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class FeedEventJson {
    public long seq;
    public String eventId;
    public String type;
    public String orderId;
    public String placedAt;
    public String decisionDeadline;
    public List<LineJson> lines;
    public BuyerJson buyer;
    public String cancelledAt;
    public String confirmDeadline;
}
