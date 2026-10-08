package edu.cit.laurino.channel;

import edu.cit.laurino.supplier.SupplierOrderDelivered;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class BackorderResolutionListener {
    private final TiangeOrderProcessor orderProcessor;
    private final TiangeReportDispatcher reportDispatcher;

    BackorderResolutionListener(TiangeOrderProcessor orderProcessor, TiangeReportDispatcher reportDispatcher) {
        this.orderProcessor = orderProcessor;
        this.reportDispatcher = reportDispatcher;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onSupplierOrderDelivered(SupplierOrderDelivered event) {
        orderProcessor.onProductDelivered(event.productId())
                .forEach(reportDispatcher::reportAsync);
    }
}
