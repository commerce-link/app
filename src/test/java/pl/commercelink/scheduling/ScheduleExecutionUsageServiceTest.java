package pl.commercelink.scheduling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleExecutionUsageServiceTest {

    @Mock
    private DailyScheduleExecutionCountRepository repository;

    @InjectMocks
    private ScheduleExecutionUsageService service;

    private static DailyScheduleExecutionCount ordersImport(long executions) {
        DailyScheduleExecutionCount count = new DailyScheduleExecutionCount();
        count.setExecutionType(ScheduledExecution.ORDERS_IMPORT);
        count.setExecutionCount(executions);
        return count;
    }

    @Test
    void monthlyUsageSumsTheCountsOfThatMonth() {
        // given
        when(repository.findMonth("store-1", YearMonth.of(2026, 9))).thenReturn(List.of(ordersImport(10), ordersImport(5)));

        // when
        ScheduleExecutionUsage usage = service.monthlyUsage("store-1", YearMonth.of(2026, 9));

        // then
        assertThat(usage.total(ScheduledExecution.ORDERS_IMPORT)).isEqualTo(15L);
    }

    @Test
    void dailyUsageSumsTheCountsOfThatDay() {
        // given
        when(repository.findDay("store-1", LocalDate.of(2026, 9, 18))).thenReturn(List.of(ordersImport(4)));

        // when
        ScheduleExecutionUsage usage = service.dailyUsage("store-1", LocalDate.of(2026, 9, 18));

        // then
        assertThat(usage.total(ScheduledExecution.ORDERS_IMPORT)).isEqualTo(4L);
    }
}
