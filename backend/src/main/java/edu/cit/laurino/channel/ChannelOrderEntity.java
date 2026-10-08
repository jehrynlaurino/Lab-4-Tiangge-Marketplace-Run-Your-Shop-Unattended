package edu.cit.laurino.channel;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Keyed by Tiangge's own orderId - NOT by eventId, since the manual warns a
 * redelivered event can arrive with a brand new seq/eventId. This row is
 * what actually guarantees "each Tiangge order becomes exactly one order in
 * your system."
 */
@Entity
@Table(name = "channel_orders")
class ChannelOrderEntity {
    @Id
    @Column(name = "tiangge_order_id", length = 40)
    private String tiangeOrderId;

    @Column(name = "shop_order_id")
    private Long shopOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChannelDecision decision;

    @Enumerated(EnumType.STRING)
    @Column(name = "pending_action", length = 20)
    private PendingAction pendingAction;

    @Column(nullable = false)
    private boolean synced;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "channelOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ChannelOrderItemEntity> items = new ArrayList<>();

    protected ChannelOrderEntity() {
        // Required by JPA.
    }

    ChannelOrderEntity(String tiangeOrderId, Long shopOrderId, ChannelDecision decision, PendingAction pendingAction) {
        this.tiangeOrderId = tiangeOrderId;
        this.shopOrderId = shopOrderId;
        this.decision = decision;
        this.pendingAction = pendingAction;
        this.synced = false;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    void addItem(String productId, int quantity) {
        items.add(new ChannelOrderItemEntity(this, productId, quantity));
    }

    void markSynced() {
        this.synced = true;
        this.pendingAction = null;
    }

    void markCancelled() {
        this.decision = ChannelDecision.CANCELLED;
        this.pendingAction = PendingAction.CANCELLATION;
        this.synced = false;
    }

    void resolveAccepted(Long newShopOrderId) {
        this.decision = ChannelDecision.ACCEPTED;
        this.shopOrderId = newShopOrderId;
        this.pendingAction = PendingAction.RESOLUTION;
        this.synced = false;
    }

    void resolveCancelled() {
        this.decision = ChannelDecision.CANCELLED;
        this.pendingAction = PendingAction.RESOLUTION;
        this.synced = false;
    }

    String getTiangeOrderId() {
        return tiangeOrderId;
    }

    Long getShopOrderId() {
        return shopOrderId;
    }

    ChannelDecision getDecision() {
        return decision;
    }

    PendingAction getPendingAction() {
        return pendingAction;
    }

    boolean isSynced() {
        return synced;
    }

    List<ChannelOrderItemEntity> getItems() {
        return items;
    }
}
