package edu.cit.laurino.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "channel_order_items")
class ChannelOrderItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "channel_order_item_id")
    private Long channelOrderItemId;

    @ManyToOne
    @JoinColumn(name = "tiangge_order_id", nullable = false)
    private ChannelOrderEntity channelOrder;

    @Column(name = "product_id", nullable = false, length = 50)
    private String productId;

    @Column(nullable = false)
    private int quantity;

    protected ChannelOrderItemEntity() {
        // Required by JPA.
    }

    ChannelOrderItemEntity(ChannelOrderEntity channelOrder, String productId, int quantity) {
        this.channelOrder = channelOrder;
        this.productId = productId;
        this.quantity = quantity;
    }

    String getProductId() {
        return productId;
    }

    int getQuantity() {
        return quantity;
    }
}
