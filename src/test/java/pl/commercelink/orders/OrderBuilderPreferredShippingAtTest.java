package pl.commercelink.orders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBuilderPreferredShippingAtTest {

    private static Order.Builder builder() {
        Order original = new Order("store-1");
        original.setBillingDetails(new BillingDetails());
        original.setShippingDetails(new ShippingDetails());
        return new Order.Builder(original);
    }

    @Test
    @DisplayName("withPreferredShippingAt sets the date coming from the marketplace as the preferred one")
    void setsTheDateFromMarketplace() {
        Order order = builder().withPreferredShippingAt(LocalDate.of(2026, 9, 3)).build();

        assertThat(order.getPreferredShippingAt()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(order.getEstimatedShippingAt()).isNull();
    }

    @Test
    @DisplayName("withPreferredShippingAt leaves the order untouched when the marketplace gives no date")
    void leavesOrderUntouchedForMissingDate() {
        Order order = builder().withPreferredShippingAt(null).build();

        assertThat(order.getPreferredShippingAt()).isNull();
        assertThat(order.getEstimatedShippingAt()).isNull();
    }
}
