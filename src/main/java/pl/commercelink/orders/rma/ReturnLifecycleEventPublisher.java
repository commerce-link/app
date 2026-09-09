package pl.commercelink.orders.rma;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReturnLifecycleEventPublisher {

    private static final String QUEUE = "marketplace-return-lifecycle-queue";

    @Value("${application.env}")
    private String env;

    private final SqsTemplate sqsTemplate;

    /**
     * Transport only. Whether this RMA and order are a marketplace return at all is decided by the caller,
     * which already holds both - passing them here would be two parameters serving nothing but a guard.
     */
    public void publish(ReturnLifecycleEvent event) {
        if (!env.equals("prod")) {
            return;
        }
        sqsTemplate.send(QUEUE, event);
    }
}
