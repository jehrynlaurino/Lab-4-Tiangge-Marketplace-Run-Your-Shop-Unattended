package edu.cit.laurino.channel;

import edu.cit.laurino.inventory.InventoryService;
import edu.cit.laurino.shop.OrderItemRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class TiangeOrderProcessor {
    private static final Logger log = LoggerFactory.getLogger(TiangeOrderProcessor.class);

    private final ChannelOrderWriter writer;
    private final ChannelOrderRepository channelOrderRepository;
    private final TiangeClient tiangeClient;
    private final InventoryService inventoryService;

    TiangeOrderProcessor(
            ChannelOrderWriter writer,
            ChannelOrderRepository channelOrderRepository,
            TiangeClient tiangeClient,
            InventoryService inventoryService) {
        this.writer = writer;
        this.channelOrderRepository = channelOrderRepository;
        this.tiangeClient = tiangeClient;
        this.inventoryService = inventoryService;
    }

    Optional<ChannelOrderEntity> handleOrderPlaced(FeedEventJson event) {
        List<OrderItemRequest> items = event.lines == null
                ? List.of()
                : event.lines.stream().map(line -> new OrderItemRequest(line.sellerSku, line.qty)).toList();

        Optional<ChannelOrderEntity> maybeOrder = writer.decideAndPersistPlaced(event.orderId, items);
        return maybeOrder.filter(channelOrder -> !channelOrder.isSynced());
    }

    Optional<ChannelOrderEntity> handleOrderCancelled(FeedEventJson event) {
        Optional<ChannelOrderEntity> maybeOrder = channelOrderRepository.findByTiangeOrderId(event.orderId);
        if (maybeOrder.isEmpty()) {
            log.warn("Cancellation for unknown Tiangge order {}", event.orderId);
            return Optional.empty();
        }

        ChannelOrderEntity channelOrder = maybeOrder.get();
        if (channelOrder.getDecision() == ChannelDecision.CANCELLED) {
            return channelOrder.isSynced() ? Optional.empty() : Optional.of(channelOrder);
        }

        return Optional.of(writer.markCancelled(channelOrder));
    }

    List<ChannelOrderEntity> onProductDelivered(String productId) {
        List<ChannelOrderEntity> backordered = channelOrderRepository.findByDecision(ChannelDecision.BACKORDERED);
        List<ChannelOrderEntity> resolved = new ArrayList<>();
        for (ChannelOrderEntity channelOrder : backordered) {
            boolean involvesProduct = channelOrder.getItems().stream()
                    .anyMatch(item -> item.getProductId().equals(productId));
            if (!involvesProduct) {
                continue;
            }
            writer.attemptBackorderResolution(channelOrder.getTiangeOrderId()).ifPresent(resolved::add);
        }
        return resolved;
    }

    void resumePending(ChannelOrderEntity channelOrder) {
        if (channelOrder.getPendingAction() == null) {
            return;
        }
        switch (channelOrder.getPendingAction()) {
            case DECISION -> reportDecision(channelOrder);
            case CANCELLATION -> reportCancellation(channelOrder);
            case RESOLUTION -> reportResolution(channelOrder);
        }
    }

    private void reportDecision(ChannelOrderEntity channelOrder) {
        String shopOrderId = "SO-" + channelOrder.getShopOrderId();
        TiangeCallOutcome outcome = tiangeClient.decide(
                channelOrder.getTiangeOrderId(), channelOrder.getDecision().name(), shopOrderId, null);
        finishIfHandled(channelOrder, outcome);
    }

    private void reportCancellation(ChannelOrderEntity channelOrder) {
        TiangeCallOutcome outcome = tiangeClient.confirmCancellation(channelOrder.getTiangeOrderId(), true);
        finishIfHandled(channelOrder, outcome);
    }

    private void reportResolution(ChannelOrderEntity channelOrder) {
        String status = channelOrder.getDecision() == ChannelDecision.ACCEPTED ? "ACCEPTED" : "CANCELLED";
        TiangeCallOutcome outcome = tiangeClient.resolveBackorder(channelOrder.getTiangeOrderId(), status);
        finishIfHandled(channelOrder, outcome);
    }

    private void finishIfHandled(ChannelOrderEntity channelOrder, TiangeCallOutcome outcome) {
        if (outcome == TiangeCallOutcome.SUCCESS || outcome == TiangeCallOutcome.ALREADY_HANDLED) {
            channelOrder.markSynced();
            channelOrderRepository.save(channelOrder);
            pushCurrentStockWithRetry(channelOrder);
            return;
        }
        if (outcome == TiangeCallOutcome.NOT_FOUND) {
            channelOrder.markSynced();
            channelOrderRepository.save(channelOrder);
        }
    }

    private void pushCurrentStockWithRetry(ChannelOrderEntity channelOrder) {
        List<StockEntryJson> entries = channelOrder.getItems().stream()
                .map(item -> item.getProductId())
                .distinct()
                .map(inventoryService::getItem)
                .flatMap(Optional::stream)
                .map(item -> new StockEntryJson(item.productId(), item.stock()))
                .toList();
        if (entries.isEmpty()) {
            return;
        }
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (tiangeClient.publishStock(entries)) {
                return;
            }
            try {
                Thread.sleep(200L * attempt);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("Gave up on the immediate post-decision stock push for {} - "
                + "StockSyncListener will still cover it as a backstop.", channelOrder.getTiangeOrderId());
    }
}
