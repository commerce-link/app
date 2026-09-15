package pl.commercelink.marketplace;

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
class MarketplaceOrdersImportSchedulerTest {

    private static final String QUEUE_ARN = "arn:aws:sqs:eu-central-1:1:marketplace-orders-import-queue";

    @Mock
    private EventBridgeSchedules schedules;

    private MarketplaceOrdersImportScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new MarketplaceOrdersImportScheduler(QUEUE_ARN, 10, schedules);
    }

    @Test
    void appliesCronAsPerStoreScheduleCarryingTheStoreId() {
        // when
        scheduler.apply("store-1", "CsCartMultiVendor", "0/15 * * * ? *");

        // then
        verify(schedules).put(
                "orders-import-store-1-cscartmultivendor",
                "cron(0/15 * * * ? *)",
                QUEUE_ARN,
                "{\"marketplace\":\"CsCartMultiVendor\",\"storeId\":\"store-1\"}");
    }

    @Test
    void blankScheduleBecomesTheDefaultIntervalWithARandomMinuteOffset() {
        // when
        scheduler.apply("store-1", "Allegro", "  ");

        // then
        ArgumentCaptor<String> expression = ArgumentCaptor.forClass(String.class);
        verify(schedules).put(eq("orders-import-store-1-allegro"), expression.capture(), eq(QUEUE_ARN), anyString());
        assertThat(expression.getValue()).matches("cron\\([0-9]/10 \\* \\* \\* \\? \\*\\)");
        verify(schedules, never()).delete(anyString());
    }

    @Test
    void deleteRemovesTheStoreSchedule() {
        // when
        scheduler.delete("store-1", "Allegro");

        // then
        verify(schedules).delete("orders-import-store-1-allegro");
    }

    @Test
    void snapshotReadsTheLiveExpressionOfTheStoreSchedule() {
        // given
        when(schedules.expressionOf("orders-import-store-1-allegro")).thenReturn(Optional.of("cron(0/15 * * * ? *)"));

        // when / then
        assertThat(scheduler.snapshot("store-1", "Allegro")).contains("cron(0/15 * * * ? *)");
    }

    @Test
    void restoringAPresentSnapshotPutsTheOldExpressionBackWithTheSameTarget() {
        // when
        scheduler.restore("store-1", "Allegro", Optional.of("cron(0 9 * * ? *)"));

        // then
        verify(schedules).put("orders-import-store-1-allegro", "cron(0 9 * * ? *)", QUEUE_ARN,
                "{\"marketplace\":\"Allegro\",\"storeId\":\"store-1\"}");
    }

    @Test
    void restoringAnEmptySnapshotRemovesWhateverWasCreatedMeanwhile() {
        // when
        scheduler.restore("store-1", "Allegro", Optional.empty());

        // then
        verify(schedules).delete("orders-import-store-1-allegro");
        verify(schedules, never()).put(anyString(), anyString(), anyString(), any());
    }
}
