package edu.cit.laurino.inventory;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InventoryServiceImpl implements InventoryService {
    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final int lowStockThreshold;

    InventoryServiceImpl(
            InventoryRepository inventoryRepository,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.inventory.low-stock-threshold:5}") int lowStockThreshold) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
        this.lowStockThreshold = lowStockThreshold;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryItem> getItem(String productId) {
        return inventoryRepository.findById(productId).map(InventoryEntity::toItem);
    }

    @Override
    @Transactional
    public ReservationResult reserve(String productId, int quantity) {
        if (quantity <= 0) {
            return ReservationResult.rejected("Quantity must be at least 1.", null);
        }

        Optional<InventoryEntity> item = inventoryRepository.findForUpdate(productId);
        if (item.isEmpty()) {
            return ReservationResult.rejected("Product " + productId + " was not found.", null);
        }

        InventoryEntity inventory = item.get();
        if (quantity > inventory.getStock()) {
            return ReservationResult.rejected(
                    "Only " + inventory.getStock() + " unit(s) of " + inventory.getName() + " remain.",
                    inventory.toItem());
        }

        inventory.decreaseStock(quantity);
        InventoryItem remainingInventory = inventory.toItem();

        eventPublisher.publishEvent(new InventoryStockChanged(remainingInventory.productId(), remainingInventory.stock()));

        if (remainingInventory.stock() < lowStockThreshold) {
            eventPublisher.publishEvent(new LowStockEvent(
                    remainingInventory.productId(),
                    remainingInventory.name(),
                    remainingInventory.stock(),
                    lowStockThreshold));
        }

        return new ReservationResult(true, "Inventory reserved.", remainingInventory);
    }

    @Override
    @Transactional
    public void restock(String productId, int quantity) {
        if (quantity <= 0) {
            return;
        }
        inventoryRepository.findForUpdate(productId).ifPresent(inventory -> {
            inventory.increaseStock(quantity);
            eventPublisher.publishEvent(new InventoryStockChanged(productId, inventory.getStock()));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItem> listItems() {
        return inventoryRepository.findAll().stream()
                .map(InventoryEntity::toItem)
                .toList();
    }
}
