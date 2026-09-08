package pl.commercelink.inventory.supplier;

import io.awspring.cloud.sqs.operations.SqsSendOptions;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.scheduling.EventBridgeSchedules;

import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreSupplierFeedSchedulerTest {

    private static final String QUEUE_ARN = "arn:aws:sqs:eu-central-1:1:supplier-feed-import-queue";

    @Mock
    private SqsTemplate sqsTemplate;

    @Mock
    private EventBridgeSchedules schedules;

    @InjectMocks
    private StoreSupplierFeedScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "feedImportQueueArn", QUEUE_ARN);
    }

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

    @Test
    void createsScheduleWithTheSuppliedCron() {
        // when
        scheduler.createSchedule("store-1", "Ingram Micro", "0/30 9-17 * * ? *");

        // then
        verify(schedules).put(
                eq("supplier-feed-store-1-ingram-micro"),
                eq("cron(0/30 9-17 * * ? *)"),
                eq(QUEUE_ARN),
                eq("{\"supplierName\":\"Ingram Micro\",\"storeId\":\"store-1\"}"));
    }

    @Test
    void fallsBackToRandomNightlyCronWhenNoScheduleGiven() {
        // when
        scheduler.createSchedule("store-1", "Acme", null);

        // then
        ArgumentCaptor<String> expression = ArgumentCaptor.forClass(String.class);
        verify(schedules).put(eq("supplier-feed-store-1-acme"), expression.capture(), eq(QUEUE_ARN), anyString());
        assertThat(expression.getValue()).matches("cron\\(\\d{1,2} (23|0|1|2|3|4) \\* \\* \\? \\*\\)");
    }

    @Test
    void updatesScheduleThroughTheSamePutCall() {
        // when
        scheduler.updateSchedule("store-1", "Acme", " 0 5 * * ? * ");

        // then
        verify(schedules).put(eq("supplier-feed-store-1-acme"), eq("cron(0 5 * * ? *)"), eq(QUEUE_ARN), anyString());
    }

    @Test
    void deletesScheduleByName() {
        // when
        scheduler.deleteSchedule("store-1", "Acme");

        // then
        verify(schedules).delete("supplier-feed-store-1-acme");
    }
}
