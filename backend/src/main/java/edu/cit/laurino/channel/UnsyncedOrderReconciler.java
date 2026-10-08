package edu.cit.laurino.channel;

import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Catches anything TiangeOrderProcessor couldn't immediately report to
 * Tiangge (a transient failure, or the app crashing between a local commit
 * and the Tiangge call). Nothing here ever touches Order or Inventory again
 * - only resumes the specific Tiangge call each row is still owed.
 */
@Component
class UnsyncedOrderReconciler {
    private final ChannelOrderRepository channelOrderRepository;
    private final TiangeOrderProcessor orderProcessor;

    UnsyncedOrderReconciler(ChannelOrderRepository channelOrderRepository, TiangeOrderProcessor orderProcessor) {
        this.channelOrderRepository = channelOrderRepository;
        this.orderProcessor = orderProcessor;
    }

    @Scheduled(fixedDelayString = "${app.tiangge.reconcile-interval-ms:20000}")
    void reconcile() {
        List<ChannelOrderEntity> unsynced = channelOrderRepository.findBySyncedFalse();
        for (ChannelOrderEntity channelOrder : unsynced) {
            orderProcessor.resumePending(channelOrder);
        }
    }
}
