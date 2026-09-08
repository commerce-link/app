package pl.commercelink.pricelist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.scheduling.EventBridgeSchedules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PricelistEventSchedulerTest {

    private static final String QUEUE_ARN = "arn:aws:sqs:eu-central-1:1:catalog-pricelist-queue";

    @Mock
    private EventBridgeSchedules schedules;

    @InjectMocks
    private PricelistEventScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "pricelistQueueArn", QUEUE_ARN);
    }

    @Test
    void createsScheduleWithTheSuppliedCronAndCatalogPayload() {
        // when
        scheduler.createRecurringSchedule("store-1", "cat-1", "0 5 * * ? *");

        // then
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(schedules).put(eq("pricelist-store-1-cat-1"), eq("cron(0 5 * * ? *)"), eq(QUEUE_ARN), input.capture());
        assertThat(input.getValue()).contains("\"storeId\":\"store-1\"").contains("\"catalogId\":\"cat-1\"");
    }

    @Test
    void fallsBackToRandomNightlyCronWhenNoScheduleGiven() {
        // when
        scheduler.createRecurringSchedule("store-1", "cat-1", " ");

        // then
        ArgumentCaptor<String> expression = ArgumentCaptor.forClass(String.class);
        verify(schedules).put(eq("pricelist-store-1-cat-1"), expression.capture(), eq(QUEUE_ARN), anyString());
        assertThat(expression.getValue()).matches("cron\\(\\d{1,2} (23|0|1|2|3|4) \\* \\* \\? \\*\\)");
    }

    @Test
    void updatesThroughTheSamePutCall() {
        // when
        scheduler.updateSchedule("store-1", "cat-1", "0/30 9-17 * * ? *");

        // then
        verify(schedules).put(eq("pricelist-store-1-cat-1"), eq("cron(0/30 9-17 * * ? *)"), eq(QUEUE_ARN), anyString());
    }

    @Test
    void deletesScheduleByName() {
        // when
        scheduler.deleteSchedule("store-1", "cat-1");

        // then
        verify(schedules).delete("pricelist-store-1-cat-1");
    }
}
