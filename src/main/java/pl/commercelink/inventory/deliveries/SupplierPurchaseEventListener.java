package pl.commercelink.inventory.deliveries;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SupplierPurchaseEventListener {

    private final SupplierPurchaseService supplierPurchaseService;

    @SqsListener(
            value = "supplier-purchase-queue.fifo",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(SupplierPurchaseEventRequest payload) {
        supplierPurchaseService.processPending(payload.getStoreId(), payload.getDeliveryId());
    }
}
