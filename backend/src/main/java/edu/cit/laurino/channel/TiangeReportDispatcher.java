package edu.cit.laurino.channel;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
class TiangeReportDispatcher {
    private final TiangeOrderProcessor orderProcessor;

    TiangeReportDispatcher(TiangeOrderProcessor orderProcessor) {
        this.orderProcessor = orderProcessor;
    }

    @Async
    void reportAsync(ChannelOrderEntity channelOrder) {
        orderProcessor.resumePending(channelOrder);
    }
}
