package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierParcel;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DropshipParcelMatcherTest {

    private static Allocation allocation(String itemId, String ean, String mfn) {
        Order order = new Order("store-1");
        order.setOrderId("order-1");
        BillingDetails billingDetails = new BillingDetails();
        billingDetails.setEmail("customer@example.com");
        order.setBillingDetails(billingDetails);
        OrderItem item = new OrderItem("order-1", "Category", "Product " + itemId, 1, 100.0, null, false);
        item.setItemId(itemId);
        item.setStatus(FulfilmentStatus.Ordered);
        item.setEan(ean);
        item.setManufacturerCode(mfn);
        Allocation allocation = Allocation.fromOrderItem(order, item);
        allocation.setInAllocation(true);
        return allocation;
    }

    private static SupplierParcel parcel(SupplierOrderLine... lines) {
        return new SupplierParcel("DPD", "PKG-1", null, null, List.of(lines));
    }

    @Test
    void parcelWithoutLinesTakesEveryOpenAllocation() {
        // given
        List<Allocation> open = List.of(allocation("1", "5900000000001", "MFN-1"), allocation("2", "5900000000002", "MFN-2"));

        // when
        List<Allocation> selected = DropshipParcelMatcher.select(parcel(), open, false);

        // then
        assertThat(selected).containsExactlyElementsOf(open);
    }

    @Test
    void matchesByUnifiedEan() {
        // given
        Allocation first = allocation("1", "5900000000001", "MFN-1");
        Allocation second = allocation("2", "5900000000002", "MFN-2");

        // when
        List<Allocation> selected = DropshipParcelMatcher.select(
                parcel(new SupplierOrderLine(null, "05900000000002", null, 1)), List.of(first, second), false);

        // then
        assertThat(selected).containsExactly(second);
    }

    @Test
    void matchesByManufacturerCodeIgnoringCase() {
        // given
        Allocation first = allocation("1", null, "MFN-1");
        Allocation second = allocation("2", null, "MFN-2");

        // when
        List<Allocation> selected = DropshipParcelMatcher.select(
                parcel(new SupplierOrderLine(null, null, " mfn-1 ", 1)), List.of(first, second), false);

        // then
        assertThat(selected).containsExactly(first);
    }

    @Test
    void absorbRemainingTakesEverythingEvenWithLines() {
        // given
        Allocation first = allocation("1", "5900000000001", "MFN-1");
        Allocation second = allocation("2", "5900000000002", "MFN-2");

        // when
        List<Allocation> selected = DropshipParcelMatcher.select(
                parcel(new SupplierOrderLine(null, "5900000000001", null, 1)), List.of(first, second), true);

        // then
        assertThat(selected).containsExactly(first, second);
    }

    @Test
    void unmatchedLinesSelectNothing() {
        // given
        Allocation first = allocation("1", "5900000000001", "MFN-1");

        // when
        List<Allocation> selected = DropshipParcelMatcher.select(
                parcel(new SupplierOrderLine(null, "5900000000009", "MFN-9", 1)), List.of(first), false);

        // then
        assertThat(selected).isEmpty();
    }
}
