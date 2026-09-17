package pl.commercelink.scheduling;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledDailyExecutionCountersTest {

    @Test
    void oneDocumentHoldsACounterMapPerScheduledExecution() {
        // given
        ScheduledDailyExecutionCounters counters = new ScheduledDailyExecutionCounters("store-1", LocalDate.of(2026, 9, 16));
        counters.setOrdersImport(Map.of("Allegro", 144L, "Empik", 3L));
        counters.setReturnsImport(Map.of("Allegro", 2L));
        counters.setSupplierFeed(Map.of("Wortmann", 1L));
        counters.setPricelist(Map.of("catalog-1", 1L));

        // then
        assertThat(counters.getExecutionDate()).isEqualTo("2026-09-16");
        assertThat(counters.countersOf(ScheduledExecution.ORDERS_IMPORT)).containsEntry("Empik", 3L);
        assertThat(counters.countersOf(ScheduledExecution.RETURNS_IMPORT)).containsEntry("Allegro", 2L);
        assertThat(counters.countersOf(ScheduledExecution.SUPPLIER_FEED)).containsEntry("Wortmann", 1L);
        assertThat(counters.countersOf(ScheduledExecution.PRICELIST)).containsEntry("catalog-1", 1L);
        assertThat(counters.total(ScheduledExecution.ORDERS_IMPORT)).isEqualTo(147L);
    }
}
