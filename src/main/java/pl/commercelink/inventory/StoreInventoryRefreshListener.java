package pl.commercelink.inventory;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class StoreInventoryRefreshListener {

    private final StoreInventoryProvider storeInventoryProvider;

    // Not caught: the message comes back after the visibility timeout and, after its receives are used up, lands in
    // the DLQ. The previous snapshot stays in use meanwhile, so the ERROR logged here is the operator's only signal.
    @SqsListener(
            value = StoreInventoryRefreshPublisher.QUEUE_NAME,
            maxConcurrentMessages = "2",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20",
            // the rebuild of a large store takes longer than a queue's default 30 seconds
            messageVisibilitySeconds = "120"
    )
    void handleMessage(StoreInventoryRefreshRequest request) {
        try {
            storeInventoryProvider.refresh(request.getStoreId(), request.getRequestedAtMillis());
        } catch (RuntimeException e) {
            log.error("Inventory snapshot of store {} was not rebuilt", request.getStoreId(), e);
            throw e;
        }
    }
}
