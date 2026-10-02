package pl.commercelink.inventory.deliveries;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SupplierPurchaseCompletionEventPublisher {

    static final String DELAY_PROPERTY = "${supplier.purchase.completion.delay-seconds:60}";
    private static final String QUEUE_NAME = "supplier-purchase-completion-queue";

    private final SqsTemplate sqsTemplate;

    @Value(DELAY_PROPERTY)
    private int delaySeconds;

    public void publish(SupplierPurchaseCompletionEventRequest request) {
        sqsTemplate.send(to -> to
                .queue(QUEUE_NAME)
                .payload(request)
                .delaySeconds(delaySeconds)
        );
    }
}
