package pl.commercelink.inventory.deliveries;

import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import pl.commercelink.stores.StoreActivity;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupplierPurchaseEventListener {

    private final SupplierPurchaseService supplierPurchaseService;
    private final StoreActivity storeActivity;

    @SqsListener(
            value = "supplier-purchase-queue.fifo",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(SupplierPurchaseEventRequest payload,
            @Header(SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT) String receiveCount) {
        if (!storeActivity.isActive(payload.getStoreId())) {
            log.error("Supplier purchase of delivery {} for order {} dropped: store {} is inactive",
                    payload.getDeliveryId(), payload.getOrderId(), payload.getStoreId());
            return;
        }
        supplierPurchaseService.processPending(payload.getStoreId(), payload.getDeliveryId(),
                payload.getOrderId(), Integer.parseInt(receiveCount));
    }
}
