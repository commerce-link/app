package pl.commercelink.inventory;

import io.awspring.cloud.sqs.operations.SqsSendOptions;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreInventoryRefreshPublisherTest {

    @Mock
    private SqsTemplate sqsTemplate;
    @Mock
    private StoreInventoryCache cache;
    @Mock(answer = Answers.RETURNS_SELF)
    private SqsSendOptions<Object> options;
    @Captor
    private ArgumentCaptor<Consumer<SqsSendOptions<Object>>> send;

    @InjectMocks
    private StoreInventoryRefreshPublisher publisher;

    @Test
    void sendsADelayedRequestStampedWithTheCacheClock() {
        // given
        when(cache.clockMillis()).thenReturn(4_242L);

        // when
        publisher.request("store-1");

        // then
        verify(sqsTemplate).send(send.capture());
        send.getValue().accept(options);
        verify(options).queue("store-inventory-refresh-queue");
        verify(options).payload(new StoreInventoryRefreshRequest("store-1", 4_242L));
        verify(options).delaySeconds(60);
    }
}
