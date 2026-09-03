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

import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyInt;

@ExtendWith(MockitoExtension.class)
class DropshipTrackingEventPublisherTest {

    @Mock
    private SqsTemplate sqsTemplate;
    @InjectMocks
    private DropshipTrackingEventPublisher publisher;

    @Test
    void publishSendsToTrackingQueueWithoutDelay() {
        // given
        DropshipTrackingEventRequest request = new DropshipTrackingEventRequest("store-1", "delivery-1", "ACME-DS-1");

        // when
        publisher.publish(request);

        // then
        ArgumentCaptor<Consumer<SqsSendOptions<DropshipTrackingEventRequest>>> captor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(sqsTemplate).send(captor.capture());
        SqsSendOptions<DropshipTrackingEventRequest> options = mock(SqsSendOptions.class, RETURNS_SELF);
        captor.getValue().accept(options);
        verify(options).queue("supplier-order-tracking-queue");
        verify(options).payload(request);
        verify(options, never()).delaySeconds(anyInt());
    }
}
