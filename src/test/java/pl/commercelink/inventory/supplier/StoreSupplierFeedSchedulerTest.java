package pl.commercelink.inventory.supplier;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.inventory.supplier.SqsFeedLoaderEventListener.FeedLoaderEventPayload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class StoreSupplierFeedSchedulerTest {

    @Mock
    private SqsTemplate sqsTemplate;

    @InjectMocks
    private StoreSupplierFeedScheduler scheduler;

    @Test
    void immediateImportSendsTypedPayloadTheListenerCanConsume() {
        // given
        ReflectionTestUtils.setField(scheduler, "env", "prod");

        // when
        scheduler.triggerImmediateImport("store-1", "Elko");

        // then
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(sqsTemplate).send(eq("supplier-feed-import-queue"), captor.capture());
        FeedLoaderEventPayload payload = (FeedLoaderEventPayload) captor.getValue();
        assertEquals("Elko", payload.getSupplierName());
        assertEquals("store-1", payload.getStoreId());
    }

    @Test
    void immediateImportIsSkippedOffProd() {
        // given
        ReflectionTestUtils.setField(scheduler, "env", "localhost");

        // when
        scheduler.triggerImmediateImport("store-1", "Elko");

        // then
        verifyNoInteractions(sqsTemplate);
    }
}
