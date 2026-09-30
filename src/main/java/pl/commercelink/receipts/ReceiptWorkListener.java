package pl.commercelink.receipts;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.stores.StoreActivity;

@Slf4j
@Component
@RequiredArgsConstructor
class ReceiptWorkListener {

    private final ReceiptProcessor processor;
    private final StoreActivity storeActivity;

    @SqsListener(
            value = ReceiptWorkPublisher.QUEUE_NAME,
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    void handleMessage(ReceiptWorkRequest request) {
        if (!storeActivity.isActive(request.getStoreId())) {
            log.warn("Receipt attempt {} skipped: store {} is inactive", request.getReceiptKey(), request.getStoreId());
            return;
        }
        processor.process(request.getStoreId(), request.getReceiptKey());
    }
}
