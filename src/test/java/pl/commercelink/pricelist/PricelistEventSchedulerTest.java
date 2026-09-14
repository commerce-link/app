package pl.commercelink.pricelist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.scheduling.EventBridgeSchedules;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricelistEventSchedulerTest {

    private static final String QUEUE_ARN = "arn:aws:sqs:eu-central-1:1:catalog-pricelist-queue";

    @Mock
    private EventBridgeSchedules schedules;

    private PricelistEventScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PricelistEventScheduler(QUEUE_ARN, schedules);
    }

    @Test
    void schedulesTheStoredCronWithTheCatalogPayload() {
        // when
        scheduler.schedule("store-1", "cat-1", "0 5 * * ? *");

        // then
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(schedules).put(eq("pricelist-store-1-cat-1"), eq("cron(0 5 * * ? *)"), eq(QUEUE_ARN), input.capture());
        assertThat(input.getValue()).contains("\"storeId\":\"store-1\"").contains("\"catalogId\":\"cat-1\"");
    }

    @Test
    void fallsBackToRandomNightlyCronWhenNoScheduleGiven() {
        // when
        scheduler.schedule("store-1", "cat-1", null);

        // then
        ArgumentCaptor<String> expression = ArgumentCaptor.forClass(String.class);
        verify(schedules).put(eq("pricelist-store-1-cat-1"), expression.capture(), eq(QUEUE_ARN), anyString());
        assertThat(expression.getValue()).matches("cron\\(\\d{1,2} (23|0|1|2|3|4) \\* \\* \\? \\*\\)");
    }

    @Test
    void snapshotReadsTheLiveExpression() {
        // given
        when(schedules.expressionOf("pricelist-store-1-cat-1")).thenReturn(Optional.of("cron(0 5 * * ? *)"));

        // when / then
        assertThat(scheduler.snapshot("store-1", "cat-1")).contains("cron(0 5 * * ? *)");
    }

    @Test
    void restoringAPresentSnapshotPutsTheOldExpressionBack() {
        // when
        scheduler.restore("store-1", "cat-1", Optional.of("cron(0 5 * * ? *)"));

        // then
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(schedules).put(eq("pricelist-store-1-cat-1"), eq("cron(0 5 * * ? *)"), eq(QUEUE_ARN), input.capture());
        assertThat(input.getValue()).contains("\"catalogId\":\"cat-1\"");
    }

    @Test
    void restoringAnEmptySnapshotRemovesWhateverWasCreatedMeanwhile() {
        // when
        scheduler.restore("store-1", "cat-1", Optional.empty());

        // then
        verify(schedules).delete("pricelist-store-1-cat-1");
        verify(schedules, never()).put(anyString(), anyString(), anyString(), any());
    }

    @Test
    void deletesScheduleByName() {
        // when
        scheduler.deleteSchedule("store-1", "cat-1");

        // then
        verify(schedules).delete("pricelist-store-1-cat-1");
    }
}
