package pl.commercelink.orders.fulfilment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.commercelink.orders.OrderItem;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FulfilmentGroupsGeneratorCandidateFilterTest {

    private static OrderItem serviceItem() {
        OrderItem item = new OrderItem("order-1", OrderItem.DELIVERY_CATEGORY, "Dostawa", 1, 9.99, "SHIPPING", false);
        item.setService(true);
        return item;
    }

    @Test
    @DisplayName("without a candidate filter every candidate with a provider is kept")
    void keepsCandidatesByDefault() {
        List<FulfilmentItem> items = FulfilmentGroupsGenerator.builder().build().run(List.of(serviceItem()));

        assertThat(items).hasSize(1);
    }

    @Test
    @DisplayName("the candidate filter drops candidates it rejects")
    void dropsRejectedCandidates() {
        List<FulfilmentItem> items = FulfilmentGroupsGenerator.builder()
                .withCandidateFilter(candidate -> false)
                .build()
                .run(List.of(serviceItem()));

        assertThat(items).isEmpty();
    }
}
