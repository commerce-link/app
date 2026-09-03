package pl.commercelink.orders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBuilderEstimatedShippingAtTest {

    private static Order.Builder builder() {
        Order original = new Order("store-1");
        original.setBillingDetails(new BillingDetails());
        original.setShippingDetails(new ShippingDetails());
        return new Order.Builder(original);
    }

    @Test
    @DisplayName("withEstimatedShippingAt sets the date coming from the marketplace")
    void setsTheDateFromMarketplace() {
        Order order = builder().withEstimatedShippingAt(LocalDate.of(2026, 9, 3)).build();

        assertThat(order.getEstimatedShippingAt()).isEqualTo(LocalDate.of(2026, 9, 3));
    }

    @Test
    @DisplayName("withEstimatedShippingAt leaves the order untouched when the marketplace gives no date")
    void leavesOrderUntouchedForMissingDate() {
        Order order = builder().withEstimatedShippingAt(null).build();

        assertThat(order.getEstimatedShippingAt()).isNull();
    }
}
