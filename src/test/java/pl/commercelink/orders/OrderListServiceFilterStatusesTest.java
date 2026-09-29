package pl.commercelink.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.filters.OrderFilterField;
import pl.commercelink.orders.filters.model.OrderFilter;
import pl.commercelink.orders.filters.model.OrderFilterCondition;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderListServiceFilterStatusesTest {

    @Test
    void aFilterWithoutStoredConditionsNamesNoStatuses() {
        // given
        OrderFilter filter = OrderFilter.of("empty", List.of(OrderFilterCondition.of(OrderFilterField.Status, "New")));
        filter.setConditions(null);

        // when / then
        assertThat(OrderListService.filterStatuses(filter)).isEmpty();
    }

    @Test
    void closedAndUnknownStatusesAreIgnoredAndOpenOnesKept() {
        // given
        OrderFilter filter = OrderFilter.of("mixed", List.of(
                OrderFilterCondition.of(OrderFilterField.Status, "new"),
                OrderFilterCondition.of(OrderFilterField.Status, "Completed")));

        // when / then
        assertThat(OrderListService.filterStatuses(filter)).containsExactly(OrderStatus.New);
    }
}
