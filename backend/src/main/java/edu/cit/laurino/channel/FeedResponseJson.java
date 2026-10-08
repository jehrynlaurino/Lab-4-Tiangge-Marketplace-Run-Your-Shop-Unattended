package edu.cit.laurino.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class FeedResponseJson {
    public List<FeedEventJson> events;
    public long nextCursor;
}
