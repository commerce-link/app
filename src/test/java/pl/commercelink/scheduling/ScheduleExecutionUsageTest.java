package pl.commercelink.scheduling;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleExecutionUsageTest {

    private static DailyScheduleExecutionCount count(int day, ScheduledExecution type, String target, long executions) {
        DailyScheduleExecutionCount count = new DailyScheduleExecutionCount();
        count.setExecutionDate(LocalDate.of(2026, 9, day));
        count.setExecutionType(type);
        count.setTarget(target);
        count.setExecutionCount(executions);
        return count;
    }

    private final ScheduleExecutionUsage usage = new ScheduleExecutionUsage(List.of(
            count(17, ScheduledExecution.ORDERS_IMPORT, "Allegro", 144),
            count(17, ScheduledExecution.ORDERS_IMPORT, "Empik", 3),
            count(18, ScheduledExecution.ORDERS_IMPORT, "Allegro", 100),
            count(18, ScheduledExecution.SUPPLIER_FEED, "AcmeB", 1)));

    @Test
    void theTotalOfATypeSumsEveryDayAndTarget() {
        // then
        assertThat(usage.total(ScheduledExecution.ORDERS_IMPORT)).isEqualTo(247L);
        assertThat(usage.total(ScheduledExecution.PRICELIST)).isZero();
    }

    @Test
    void totalsByTypeListOnlyTypesThatRan() {
        // then
        assertThat(usage.totalsByType()).containsOnly(
                Map.entry(ScheduledExecution.ORDERS_IMPORT, 247L),
                Map.entry(ScheduledExecution.SUPPLIER_FEED, 1L));
    }

    @Test
    void totalsByTargetSumOneTypeAcrossDaysSortedByTarget() {
        // then
        assertThat(usage.totalsByTarget(ScheduledExecution.ORDERS_IMPORT)).containsExactly(
                Map.entry("Allegro", 244L),
                Map.entry("Empik", 3L));
    }

    @Test
    void totalsByDaySumOneTypeAcrossTargetsInDateOrder() {
        // then
        assertThat(usage.totalsByDay(ScheduledExecution.ORDERS_IMPORT)).containsExactly(
                Map.entry(LocalDate.of(2026, 9, 17), 147L),
                Map.entry(LocalDate.of(2026, 9, 18), 100L));
    }
}
