package edu.cit.laurino.supplier;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private on purpose, same rule as the other modules' impls. Converts
 * our units into LegacySupply's cases (rounding up), and always persists the
 * order as PENDING before attempting the call - so even if the JVM crashes
 * mid-call, PendingReorderScheduler will pick it back up with the exact same
 * requestId/buyerRef next time it runs.
 */
@Service
class SupplierGatewayImpl implements SupplierGateway {
    private static final Logger log = LoggerFactory.getLogger(SupplierGatewayImpl.class);

    private final LegacySupplyProperties properties;
    private final LegacySupplyClient legacySupplyClient;
    private final SupplierOrderRepository supplierOrderRepository;

    SupplierGatewayImpl(
            LegacySupplyProperties properties,
            LegacySupplyClient legacySupplyClient,
            SupplierOrderRepository supplierOrderRepository) {
        this.properties = properties;
        this.legacySupplyClient = legacySupplyClient;
        this.supplierOrderRepository = supplierOrderRepository;
    }

    @Override
    @Transactional
    public ReorderResult reorder(String productId, int unitsNeeded) {
        LegacySupplyProperties.ProductMapping mapping = properties.mappingFor(productId);
        if (mapping == null) {
            log.warn("No LegacySupply mapping configured for product {}", productId);
            return new ReorderResult(false, "No supplier mapping configured for " + productId, null, null);
        }

        int cases = Math.max(1, (int) Math.ceil(unitsNeeded / (double) mapping.getPackSize()));
        cases = Math.min(cases, 99);
        int units = cases * mapping.getPackSize();

        String requestId = UUID.randomUUID().toString();
        String buyerRef = "RO-" + requestId;

        SupplierOrderEntity order = new SupplierOrderEntity(productId, buyerRef, requestId, cases, units);
        order = supplierOrderRepository.save(order);

        SubmitOutcome outcome = legacySupplyClient.submitPurchaseOrder(mapping.getSku(), cases, buyerRef, requestId);

        if (outcome.success()) {
            order.markSubmitted(outcome.poNumber(), outcome.statusCode());
            supplierOrderRepository.save(order);
            return new ReorderResult(true, "Purchase order submitted to LegacySupply.", order.getId(), outcome.poNumber());
        }

        if (!outcome.retryable()) {
            order.markFailed(outcome.failureReason());
            supplierOrderRepository.save(order);
            return new ReorderResult(false, "LegacySupply rejected the order: " + outcome.failureReason(), order.getId(), null);
        }

        return new ReorderResult(false, "LegacySupply unavailable, queued for retry: " + outcome.failureReason(), order.getId(), null);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> supplierSkuFor(String productId) {
        LegacySupplyProperties.ProductMapping mapping = properties.mappingFor(productId);
        return mapping == null ? Optional.empty() : Optional.ofNullable(mapping.getSku());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasOpenPurchaseOrder(String productId) {
        return supplierOrderRepository.existsByProductIdAndStatusIn(productId, SupplierOrderStatus.openStatuses());
    }
}
