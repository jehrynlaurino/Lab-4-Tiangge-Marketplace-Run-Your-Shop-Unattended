package edu.cit.laurino.channel;

import edu.cit.laurino.InstanceContext;
import edu.cit.laurino.inventory.InventoryItem;
import edu.cit.laurino.inventory.InventoryService;
import edu.cit.laurino.supplier.SupplierGateway;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs once, synchronously, before the scheduled heartbeat/feed-poll jobs
 * get their first chance to fire: send the very first heartbeat (required by
 * the manual before any other call), then publish listings, then publish an
 * initial stock snapshot so buyers see real numbers immediately. This is
 * what makes the shop "go live" (Tasks 1 and 2).
 */
@Component
class MarketplaceStartup implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MarketplaceStartup.class);

    private final TiangeClient tiangeClient;
    private final TiangeProperties properties;
    private final InstanceContext instanceContext;
    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;

    MarketplaceStartup(
            TiangeClient tiangeClient,
            TiangeProperties properties,
            InstanceContext instanceContext,
            InventoryService inventoryService,
            SupplierGateway supplierGateway) {
        this.tiangeClient = tiangeClient;
        this.properties = properties;
        this.instanceContext = instanceContext;
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
    }

    @Override
    public void run(ApplicationArguments args) {
        tiangeClient.sendHeartbeat(instanceContext.startedAt().toString(), 0);

        List<ListingJson> listings = properties.getListedProducts().stream()
                .map(this::buildListing)
                .flatMap(Optional::stream)
                .toList();

        if (listings.isEmpty()) {
            log.warn("No Tiangge listings configured (app.tiangge.listed-products) - shop will not go live.");
            return;
        }

        boolean listingsOk = tiangeClient.publishListings(listings);
        if (!listingsOk) {
            log.warn("Initial Tiangge listings publish failed - will not retry automatically; restart to retry.");
            return;
        }

        List<StockEntryJson> stock = listings.stream()
                .map(listing -> inventoryService.getItem(listing.sellerSku)
                        .map(item -> new StockEntryJson(item.productId(), item.stock())))
                .flatMap(Optional::stream)
                .toList();
        tiangeClient.publishStock(stock);

        log.info("Tiangge shop live: {} listing(s) published, instance {}", listings.size(), instanceContext.instanceId());
    }

    private Optional<ListingJson> buildListing(String productId) {
        Optional<InventoryItem> item = inventoryService.getItem(productId);
        Optional<String> supplierSku = supplierGateway.supplierSkuFor(productId);

        if (item.isEmpty() || supplierSku.isEmpty()) {
            log.warn("Skipping Tiangge listing for {} - missing inventory item or supplier mapping", productId);
            return Optional.empty();
        }
        return Optional.of(new ListingJson(productId, item.get().name(), supplierSku.get()));
    }
}
