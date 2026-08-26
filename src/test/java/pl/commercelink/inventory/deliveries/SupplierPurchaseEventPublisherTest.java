package pl.commercelink.inventory.deliveries;

import io.awspring.cloud.sqs.operations.SqsSendOptions;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SupplierPurchaseEventPublisherTest {

    @Mock
    private SqsTemplate sqsTemplate;

    @InjectMocks
    private SupplierPurchaseEventPublisher publisher;

    @Test
    void publishSendsMessageWithGroupAndDeduplicationId() {
        // given
        SupplierPurchaseEventRequest request =
                new SupplierPurchaseEventRequest("store-1", "delivery-1", "Acme", "ref-1");

        // when
        publisher.publish(request);

        // then
        ArgumentCaptor<Consumer<SqsSendOptions<SupplierPurchaseEventRequest>>> captor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(sqsTemplate).send(captor.capture());

        SqsSendOptions<SupplierPurchaseEventRequest> options = mock(SqsSendOptions.class, RETURNS_SELF);
        captor.getValue().accept(options);

        verify(options).queue("supplier-purchase-queue.fifo");
        verify(options).payload(request);
        verify(options).messageGroupId("store-1:Acme");
        verify(options).messageDeduplicationId("ref-1");
    }

    @Test
    void publishWithExplicitDeduplicationIdUsesIt() {
        // given
        SupplierPurchaseEventRequest request =
                new SupplierPurchaseEventRequest("store-1", "delivery-1", "Acme", "ref-1", null, 3);

        // when
        publisher.publish(request, "ref-1:3");

        // then
        ArgumentCaptor<Consumer<SqsSendOptions<SupplierPurchaseEventRequest>>> captor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(sqsTemplate).send(captor.capture());

        SqsSendOptions<SupplierPurchaseEventRequest> options = mock(SqsSendOptions.class, RETURNS_SELF);
        captor.getValue().accept(options);

        verify(options).messageDeduplicationId("ref-1:3");
    }
}
