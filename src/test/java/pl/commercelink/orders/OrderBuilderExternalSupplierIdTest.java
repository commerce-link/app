package pl.commercelink.orders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBuilderExternalSupplierIdTest {

    private static Order.Builder builder() {
        Order original = new Order("store-1");
        original.setBillingDetails(new BillingDetails());
        original.setShippingDetails(new ShippingDetails());
        return new Order.Builder(original);
    }

    @Test
    @DisplayName("withExternalSupplierId binds the order to the supplier the marketplace routed it to")
    void bindsTheOrderToTheRoutedSupplier() {
        Order order = builder().withExternalSupplierId("2").build();

        assertThat(order.getExternalSupplierId()).isEqualTo("2");
        assertThat(order.isBoundToExternalSupplier()).isTrue();
    }

    @Test
    @DisplayName("an order without a routed supplier is not bound")
    void leavesTheOrderUnboundWithoutRouting() {
        Order order = builder().withExternalSupplierId(null).build();

        assertThat(order.getExternalSupplierId()).isNull();
        assertThat(order.isBoundToExternalSupplier()).isFalse();
    }

    @Test
    @DisplayName("a blank routed supplier id does not bind the order")
    void treatsBlankIdAsUnbound() {
        Order order = builder().withExternalSupplierId(" ").build();

        assertThat(order.isBoundToExternalSupplier()).isFalse();
    }
}
