package pl.commercelink.inventory.supplier;

import io.awspring.cloud.sqs.operations.SqsSendOptions;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.scheduling.EventBridgeSchedules;

import java.util.Optional;
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

    private StoreSupplierFeedScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new StoreSupplierFeedScheduler(QUEUE_ARN, schedules, sqsTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void schedulesConfigurationRetryWithDelayAndAttemptAsListenerPayload() {
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
        ArgumentCaptor<SqsFeedLoaderEventListener.FeedLoaderEventPayload> payload = ArgumentCaptor.forClass(SqsFeedLoaderEventListener.FeedLoaderEventPayload.class);
        verify(options).payload(payload.capture());
        assertThat(payload.getValue().getSupplierName()).isEqualTo("Wortmann");
        assertThat(payload.getValue().getStoreId()).isEqualTo("store-1");
        assertThat(payload.getValue().getAttempt()).isEqualTo(3);
    }

    @Test
    void anIntervalChosenByTheShopStartsAtARandomMinuteWithinIt() {
        // when
        scheduler.schedule("store-1", "Acme", "0/30 * * * ? *");

        // then
        ArgumentCaptor<String> expression = ArgumentCaptor.forClass(String.class);
        verify(schedules).put(eq("supplier-feed-store-1-acme"), expression.capture(), eq(QUEUE_ARN), anyString());
        assertThat(expression.getValue()).matches("cron\\((\\d|[12]\\d)/30 \\* \\* \\* \\? \\*\\)");
    }

    @Test
    void createsScheduleWithTheSuppliedCron() {
        // when
        scheduler.schedule("store-1", "Ingram Micro", "0/30 9-17 * * ? *");

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
        scheduler.schedule("store-1", "Acme", null);

        // then
        ArgumentCaptor<String> expression = ArgumentCaptor.forClass(String.class);
        verify(schedules).put(eq("supplier-feed-store-1-acme"), expression.capture(), eq(QUEUE_ARN), anyString());
        assertThat(expression.getValue()).matches("cron\\(\\d{1,2} (23|0|1|2|3|4) \\* \\* \\? \\*\\)");
    }

    @Test
    void snapshotReadsTheCurrentExpressionByScheduleName() {
        // given
        when(schedules.expressionOf("supplier-feed-store-1-acme")).thenReturn(Optional.of("cron(37 2 * * ? *)"));

        // when / then
        assertThat(scheduler.snapshot("store-1", "Acme")).contains("cron(37 2 * * ? *)");
    }

    @Test
    void restorePutsBackTheExactExpression() {
        // when
        scheduler.restore("store-1", "Acme", Optional.of("cron(37 2 * * ? *)"));

        // then
        verify(schedules).put(eq("supplier-feed-store-1-acme"), eq("cron(37 2 * * ? *)"), eq(QUEUE_ARN),
                eq("{\"supplierName\":\"Acme\",\"storeId\":\"store-1\"}"));
    }

    @Test
    void restoreDeletesWhenThereWasNoSchedule() {
        // when
        scheduler.restore("store-1", "Acme", Optional.empty());

        // then
        verify(schedules).delete("supplier-feed-store-1-acme");
        verify(schedules, never()).put(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void triggersImmediateImportWithListenerPayloadOnlyWhenSchedulingIsEnabled() {
        // given
        when(schedules.isEnabled()).thenReturn(true);

        // when
        scheduler.triggerImmediateImport("store-1", "Acme");

        // then
        ArgumentCaptor<SqsFeedLoaderEventListener.FeedLoaderEventPayload> payload = ArgumentCaptor.forClass(SqsFeedLoaderEventListener.FeedLoaderEventPayload.class);
        verify(sqsTemplate).send(eq("supplier-feed-import-queue"), payload.capture());
        assertThat(payload.getValue().getSupplierName()).isEqualTo("Acme");
        assertThat(payload.getValue().getStoreId()).isEqualTo("store-1");
        assertThat(payload.getValue().getAttempt()).isZero();
    }

    @Test
    void skipsImmediateImportWhenSchedulingIsDisabled() {
        // given
        when(schedules.isEnabled()).thenReturn(false);

        // when
        scheduler.triggerImmediateImport("store-1", "Acme");

        // then
        verifyNoInteractions(sqsTemplate);
    }

    @Test
    void deletesScheduleByName() {
        // when
        scheduler.deleteSchedule("store-1", "Acme");

        // then
        verify(schedules).delete("supplier-feed-store-1-acme");
    }

    @Test
    void scheduleNameOfATokenedIdentityStaysWithinEventBridgeLimits() {
        // when
        scheduler.schedule("oh4d5y15it", "IngramMicro-k7f3a9c2", "0 6 * * ? *");

        // then
        ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        verify(schedules).put(name.capture(), anyString(), anyString(), anyString());
        assertThat(name.getValue()).isEqualTo("supplier-feed-oh4d5y15it-ingrammicro-k7f3a9c2")
                .hasSizeLessThanOrEqualTo(64)
                .matches("^[0-9a-zA-Z-_.]+$");
    }
}
