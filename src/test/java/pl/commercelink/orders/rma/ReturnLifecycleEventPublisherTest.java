package pl.commercelink.orders.rma;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.orders.MarketplaceReturnAction;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReturnLifecycleEventPublisherTest {

    @Mock private SqsTemplate sqsTemplate;

    @InjectMocks
    private ReturnLifecycleEventPublisher publisher;

    private final ReturnLifecycleEvent event = new ReturnLifecycleEvent("store-1", "order-1", "ext-1", "Allegro",
            ReturnLifecycleEventType.ReturnAccepted,
            new MarketplaceReturnAction("rma-1", "r-1",
                    List.of(new MarketplaceReturnAction.Item("SKU-1", 1)), false, "cmd-1", null));

    @Test
    void decisionsGoToTheReturnQueueNotTheOrderQueue() {
        // given
        ReflectionTestUtils.setField(publisher, "env", "prod");

        // when
        publisher.publish(event);

        // then
        verify(sqsTemplate).send(eq("marketplace-return-lifecycle-queue"), eq(event));
    }

    @Test
    void nothingIsPublishedOutsideProduction() {
        // given: marketplace queues are production infrastructure and a refund moves real money
        ReflectionTestUtils.setField(publisher, "env", "localdev");

        // when
        publisher.publish(event);

        // then
        verifyNoInteractions(sqsTemplate);
    }
}
