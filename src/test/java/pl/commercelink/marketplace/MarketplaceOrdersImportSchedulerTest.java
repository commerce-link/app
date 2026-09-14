package pl.commercelink.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.scheduling.EventBridgeSchedules;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarketplaceOrdersImportSchedulerTest {

    private static final String QUEUE_ARN = "arn:aws:sqs:eu-central-1:1:marketplace-orders-import-queue";

    @Mock
    private EventBridgeSchedules schedules;

    private MarketplaceOrdersImportScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new MarketplaceOrdersImportScheduler(QUEUE_ARN, schedules);
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
                "{\"marketplace\":\"CsCartMultiVendor\",\"storeId\":\"store-1\"}", 1);
    }

    @Test
    void blankScheduleRemovesTheStoreSchedule() {
        // when
        scheduler.apply("store-1", "Allegro", "  ");

        // then
        verify(schedules).delete("orders-import-store-1-allegro");
        verify(schedules, never()).put(anyString(), anyString(), anyString(), any(), anyInt());
    }

    @Test
    void deleteRemovesTheStoreSchedule() {
        // when
        scheduler.delete("store-1", "Allegro");

        // then
        verify(schedules).delete("orders-import-store-1-allegro");
    }
}
