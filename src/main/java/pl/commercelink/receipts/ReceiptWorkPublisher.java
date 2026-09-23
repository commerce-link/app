package pl.commercelink.receipts;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Wakes attempts through the FIFO queue, one group per store. The deduplication id carries the attempt's scheduled
 * moment, so the sweep re-publishing a still-queued attempt within five minutes adds nothing.
 */
@Component
public class ReceiptWorkPublisher {

    public static final String QUEUE_NAME = "order-receipt-queue.fifo";

    private final SqsTemplate sqsTemplate;
    private final Clock clock;

    public ReceiptWorkPublisher(SqsTemplate sqsTemplate) {
        this(sqsTemplate, Clock.systemUTC());
    }

    ReceiptWorkPublisher(SqsTemplate sqsTemplate, Clock clock) {
        this.sqsTemplate = sqsTemplate;
        this.clock = clock;
    }

    public void publishDue(ReceiptAttempt attempt) {
        Instant at = attempt.getNextCheckAt() != null ? attempt.getNextCheckAt() : clock.instant();
        send(attempt.getStoreId(), attempt.getReceiptKey(), attempt.getReceiptKey() + ":" + at.getEpochSecond());
    }

    public void publishNow(String storeId, String receiptKey) {
        send(storeId, receiptKey, receiptKey + ":now:" + clock.instant().getEpochSecond());
    }

    private void send(String storeId, String receiptKey, String deduplicationId) {
        sqsTemplate.send(to -> to
                .queue(QUEUE_NAME)
                .payload(new ReceiptWorkRequest(storeId, receiptKey))
                .messageGroupId(storeId)
                .messageDeduplicationId(deduplicationId));
    }
}
