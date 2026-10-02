package pl.commercelink.inventory.deliveries;

import io.awspring.cloud.sqs.operations.SqsSendOptions;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SupplierPurchaseCompletionEventPublisherTest {

    @Mock
    private SqsTemplate sqsTemplate;

    @InjectMocks
    private SupplierPurchaseCompletionEventPublisher publisher;

    @Test
    void publishSendsDelayedMessageToTheCompletionQueue() {
        // given
        ReflectionTestUtils.setField(publisher, "delaySeconds", 60);
        SupplierPurchaseCompletionEventRequest request =
                new SupplierPurchaseCompletionEventRequest("store-1", "delivery-1", "ref-1", "order-1");

        // when
        publisher.publish(request);

        // then
        ArgumentCaptor<Consumer<SqsSendOptions<SupplierPurchaseCompletionEventRequest>>> captor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(sqsTemplate).send(captor.capture());

        SqsSendOptions<SupplierPurchaseCompletionEventRequest> options = mock(SqsSendOptions.class, RETURNS_SELF);
        captor.getValue().accept(options);

        verify(options).queue("supplier-purchase-completion-queue");
        verify(options).payload(request);
        verify(options).delaySeconds(60);
    }

    @Test
    void firstCheckWaitsSixtySecondsUnlessConfigured() {
        // given
        MockEnvironment defaults = new MockEnvironment();
        MockEnvironment configured = new MockEnvironment()
                .withProperty("supplier.purchase.completion.delay-seconds", "5");

        // when
        String defaultDelay = defaults.resolveRequiredPlaceholders(SupplierPurchaseCompletionEventPublisher.DELAY_PROPERTY);
        String configuredDelay =
                configured.resolveRequiredPlaceholders(SupplierPurchaseCompletionEventPublisher.DELAY_PROPERTY);

        // then
        assertEquals("60", defaultDelay);
        assertEquals("5", configuredDelay);
    }
}
