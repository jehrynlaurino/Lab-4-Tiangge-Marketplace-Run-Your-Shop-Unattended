package edu.cit.laurino.channel;

import edu.cit.laurino.inventory.InventoryItem;
import edu.cit.laurino.inventory.InventoryService;
import edu.cit.laurino.shop.OrderItemRequest;
import edu.cit.laurino.shop.OrderRequest;
import edu.cit.laurino.shop.OrderResponse;
import edu.cit.laurino.shop.OrderService;
import edu.cit.laurino.shop.OrderStatus;
import edu.cit.laurino.supplier.SupplierGateway;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ChannelOrderWriter {
    private static final Logger log = LoggerFactory.getLogger(ChannelOrderWriter.class);

    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;
    private final ChannelOrderRepository channelOrderRepository;

    ChannelOrderWriter(
            OrderService orderService,
            InventoryService inventoryService,
            SupplierGateway supplierGateway,
            ChannelOrderRepository channelOrderRepository) {
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
        this.channelOrderRepository = channelOrderRepository;
    }

    @Transactional
    Optional<ChannelOrderEntity> decideAndPersistPlaced(String tiangeOrderId, List<OrderItemRequest> items) {
        Optional<ChannelOrderEntity> existing = channelOrderRepository.findByTiangeOrderId(tiangeOrderId);
        if (existing.isPresent()) {
            return existing;
        }
        if (items.isEmpty()) {
            log.warn("Tiangge order {} had no line items, skipping", tiangeOrderId);
            return Optional.empty();
        }

        OrderResponse response = orderService.placeOrder(new OrderRequest(items));

        ChannelDecision decision;
        if (response.status() == OrderStatus.CONFIRMED) {
            decision = ChannelDecision.ACCEPTED;
        } else if (canBackorder(items)) {
            decision = ChannelDecision.BACKORDERED;
        } else {
            decision = ChannelDecision.REJECTED;
        }

        ChannelOrderEntity channelOrder = new ChannelOrderEntity(
                tiangeOrderId, response.orderId(), decision, PendingAction.DECISION);
        items.forEach(item -> channelOrder.addItem(item.productId(), item.quantity()));
        return Optional.of(channelOrderRepository.save(channelOrder));
    }

    @Transactional
    ChannelOrderEntity markCancelled(ChannelOrderEntity channelOrder) {
        if (channelOrder.getDecision() == ChannelDecision.ACCEPTED && channelOrder.getShopOrderId() != null) {
            orderService.cancelOrder(channelOrder.getShopOrderId());
        }
        channelOrder.markCancelled();
        return channelOrderRepository.save(channelOrder);
    }

    @Transactional
    Optional<ChannelOrderEntity> attemptBackorderResolution(String tiangeOrderId) {
        Optional<ChannelOrderEntity> maybeOrder = channelOrderRepository.findForUpdate(tiangeOrderId);
        if (maybeOrder.isEmpty() || maybeOrder.get().getDecision() != ChannelDecision.BACKORDERED) {
            return Optional.empty();
        }
        ChannelOrderEntity channelOrder = maybeOrder.get();

        List<OrderItemRequest> items = channelOrder.getItems().stream()
                .map(item -> new OrderItemRequest(item.getProductId(), item.getQuantity()))
                .toList();

        OrderResponse response = orderService.placeOrder(new OrderRequest(items));

        if (response.status() == OrderStatus.CONFIRMED) {
            channelOrder.resolveAccepted(response.orderId());
            return Optional.of(channelOrderRepository.save(channelOrder));
        }
        if (!canBackorder(items)) {
            channelOrder.resolveCancelled();
            return Optional.of(channelOrderRepository.save(channelOrder));
        }
        return Optional.empty();
    }

    private boolean canBackorder(List<OrderItemRequest> items) {
        for (OrderItemRequest item : items) {
            Optional<InventoryItem> inventoryItem = inventoryService.getItem(item.productId());
            boolean isShort = inventoryItem.isEmpty() || inventoryItem.get().stock() < item.quantity();
            if (isShort && !supplierGateway.hasOpenPurchaseOrder(item.productId())) {
                return false;
            }
        }
        return true;
    }
}
