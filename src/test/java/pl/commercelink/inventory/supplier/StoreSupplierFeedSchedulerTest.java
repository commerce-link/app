package pl.commercelink.inventory.supplier;

import io.awspring.cloud.sqs.operations.SqsSendOptions;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreSupplierFeedSchedulerTest {

    @Mock
    private SqsTemplate sqsTemplate;

    @InjectMocks
    private StoreSupplierFeedScheduler scheduler;

    @Test
    @SuppressWarnings("unchecked")
    void schedulesConfigurationRetryWithDelayAndAttempt() {
        // given
        SqsSendOptions<Object> options = mock(SqsSendOptions.class, RETURNS_SELF);
        when(sqsTemplate.send(any(Consumer.class))).thenAnswer(invocation -> {
            Consumer<SqsSendOptions<Object>> to = invocation.getArgument(0);
            to.accept(options);
            return null;
        });

        // when
        scheduler.scheduleConfigurationRetry("store-1", "Wortmann", 3);

        // then
        verify(options).queue("supplier-feed-import-queue");
        verify(options).delaySeconds(10);
        verify(options).payload(Map.of("supplierName", "Wortmann", "storeId", "store-1", "attempt", "3"));
    }
}
