package edu.cit.laurino.channel;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class FeedPollerScheduler {
    private static final Logger log = LoggerFactory.getLogger(FeedPollerScheduler.class);
    private static final int PAGE_LIMIT = 50;

    private final TiangeClient tiangeClient;
    private final TiangeOrderProcessor orderProcessor;
    private final TiangeReportDispatcher reportDispatcher;
    private final ChannelFeedCursorRepository cursorRepository;

    FeedPollerScheduler(
            TiangeClient tiangeClient,
            TiangeOrderProcessor orderProcessor,
            TiangeReportDispatcher reportDispatcher,
            ChannelFeedCursorRepository cursorRepository) {
        this.tiangeClient = tiangeClient;
        this.orderProcessor = orderProcessor;
        this.reportDispatcher = reportDispatcher;
        this.cursorRepository = cursorRepository;
    }

    @Scheduled(
            initialDelayString = "${app.tiangge.feed-poll-initial-delay-ms:3000}",
            fixedDelayString = "${app.tiangge.feed-poll-interval-ms:5000}")
    void pollFeed() {
        ChannelFeedCursorEntity cursor = cursorRepository.findById((short) 1).orElseThrow(
                () -> new IllegalStateException("channel_feed_cursor row is missing - re-run schema.sql"));

        FeedResponseJson page = tiangeClient.fetchFeed(cursor.getLastSeq(), PAGE_LIMIT);
        if (page == null || page.events == null || page.events.isEmpty()) {
            return;
        }

        long highestHandledSeq = cursor.getLastSeq();
        for (FeedEventJson event : page.events) {
            if (!dispatch(event)) {
                break;
            }
            highestHandledSeq = event.seq;
        }

        if (highestHandledSeq > cursor.getLastSeq()) {
            cursor.advanceTo(highestHandledSeq);
            cursorRepository.save(cursor);
        }
    }

    private boolean dispatch(FeedEventJson event) {
        try {
            Optional<ChannelOrderEntity> needsReport;
            if ("ORDER_PLACED".equals(event.type)) {
                needsReport = orderProcessor.handleOrderPlaced(event);
            } else if ("ORDER_CANCELLED".equals(event.type)) {
                needsReport = orderProcessor.handleOrderCancelled(event);
            } else {
                log.warn("Unknown Tiangge feed event type '{}' for order {}, skipping", event.type, event.orderId);
                return true;
            }
            needsReport.ifPresent(reportDispatcher::reportAsync);
            return true;
        } catch (Exception ex) {
            log.error("Failed to process Tiangge event seq={} order={}: {}", event.seq, event.orderId, ex.toString());
            return false;
        }
    }
}
