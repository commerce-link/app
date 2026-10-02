package pl.commercelink.receipts;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ReceiptWorkListener {

    private final ReceiptProcessor processor;

    @SqsListener(
            value = ReceiptWorkPublisher.QUEUE_NAME,
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    void handleMessage(ReceiptWorkRequest request) {
        processor.process(request.getStoreId(), request.getReceiptKey());
    }
}
