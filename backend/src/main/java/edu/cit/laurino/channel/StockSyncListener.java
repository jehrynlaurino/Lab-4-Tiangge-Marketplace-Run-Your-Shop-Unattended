package edu.cit.laurino.channel;

import edu.cit.laurino.inventory.InventoryService;
import edu.cit.laurino.inventory.InventoryStockChanged;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class StockSyncListener {
    private static final Logger log = LoggerFactory.getLogger(StockSyncListener.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long DEFENSIVE_DELAY_MS = 5000;

    private final TiangeClient tiangeClient;
    private final TiangeProperties properties;
    private final InventoryService inventoryService;

    StockSyncListener(TiangeClient tiangeClient, TiangeProperties properties, InventoryService inventoryService) {
        this.tiangeClient = tiangeClient;
        this.properties = properties;
        this.inventoryService = inventoryService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onStockChanged(InventoryStockChanged event) {
        Set<String> listed = Set.copyOf(properties.getListedProducts());
        if (!listed.contains(event.productId())) {
            return;
        }

        sleepQuietly(DEFENSIVE_DELAY_MS);

        Optional<Integer> currentStock = inventoryService.getItem(event.productId()).map(item -> item.stock());
        if (currentStock.isEmpty()) {
            return;
        }

        List<StockEntryJson> payload = List.of(new StockEntryJson(event.productId(), currentStock.get()));
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (tiangeClient.publishStock(payload)) {
                return;
            }
            sleepQuietly(300L * attempt);
        }
        log.warn("Gave up publishing stock for {} to Tiangge after {} attempts", event.productId(), MAX_ATTEMPTS);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
