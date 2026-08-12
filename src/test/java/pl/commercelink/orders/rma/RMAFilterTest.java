package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RMAFilterTest {

    @Test
    void hasAnyFilterReturnsFalseWhenNoCriteriaGiven() {
        // given
        RMAFilter filter = new RMAFilter(null, null, null, null, null);

        // when / then
        assertFalse(filter.hasAnyFilter());
        assertFalse(filter.hasStatuses());
    }

    @Test
    void hasAnyFilterReturnsTrueWhenStatusesSelected() {
        // given
        RMAFilter filter = new RMAFilter(null, null, null, null, null, List.of(RMAStatus.Completed, RMAStatus.Rejected));

        // when / then
        assertTrue(filter.hasAnyFilter());
        assertTrue(filter.hasStatuses());
    }

    @Test
    void hasAnyFilterReturnsTrueWhenSearchFieldSet() {
        // given
        RMAFilter filter = new RMAFilter(null, null, "client@example.com", null, null);

        // when / then
        assertTrue(filter.hasAnyFilter());
        assertFalse(filter.hasStatuses());
    }

    @Test
    void statusesDefaultToEmptyListWhenNullGiven() {
        // given
        RMAFilter filter = new RMAFilter("rma-1", null, null, LocalDate.of(2026, 8, 1), null, null);

        // when / then
        assertTrue(filter.getStatuses().isEmpty());
    }
}
