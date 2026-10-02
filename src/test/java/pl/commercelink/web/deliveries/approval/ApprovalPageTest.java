package pl.commercelink.web.deliveries.approval;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.AllocationKey;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryType;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.stores.Store;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class ApprovalPageTest {

    private static Allocation allocation(String orderId, int qty, double unitCost) {
        Allocation allocation = mock(Allocation.class);
        lenient().when(allocation.getKey()).thenReturn(new AllocationKey(orderId, "item-" + qty, null));
        lenient().when(allocation.getQty()).thenReturn(qty);
        lenient().when(allocation.getTotalCost()).thenReturn(qty * unitCost);
        return allocation;
    }

    private static Order order(String orderId, String name, String email) {
        Order order = mock(Order.class);
        BillingDetails billing = mock(BillingDetails.class);
        lenient().when(billing.getName()).thenReturn(name);
        lenient().when(billing.getEmail()).thenReturn(email);
        lenient().when(order.getOrderId()).thenReturn(orderId);
        lenient().when(order.getShortenedOrderId()).thenReturn(orderId.substring(0, 8));
        lenient().when(order.getBillingDetails()).thenReturn(billing);
        return order;
    }

    private static Delivery delivery(Allocation... allocations) {
        Delivery delivery = new Delivery();
        delivery.setStoreId("uma2dqukxr");
        delivery.setDeliveryId("dd000010-0000-4000-8000-000000000010");
        delivery.setProvider("Acme");
        delivery.setOrderedAt(LocalDateTime.of(2026, 10, 2, 8, 30));
        delivery.setAllocations(new LinkedList<>(List.of(allocations)));
        return delivery;
    }

    @Test
    void groupsTheGoodsByCustomerOrderAndSumsTheWarehouseLines() {
        // given
        Delivery delivery = delivery(allocation("dd0e0011-aaaa", 1, 1299.0), allocation("dd0e0011-aaaa", 2, 10.0),
                allocation("dd0e0012-bbbb", 1, 100.0), allocation(null, 3, 1.0), allocation(null, 1, 1.0));
        Store store = mock(Store.class);
        lenient().when(store.getName()).thenReturn("Demo Store");
        List<Order> orders = List.of(order("dd0e0011-aaaa", "  ", "klient11@example.com"),
                order("dd0e0012-bbbb", "Piotr Zieliński", "p@example.com"));

        // when
        ApprovalPage page = ApprovalPage.of(delivery, store, orders, "Acme", null, null, false);

        // then
        assertThat(page.requestFor()).extracting(ApprovalPage.RequestLine::orderLabel)
                .containsExactly("#dd0e0011", "#dd0e0012");
        assertThat(page.requestFor()).extracting(ApprovalPage.RequestLine::customer)
                .containsExactly("klient11@example.com", "Piotr Zieliński");
        assertThat(page.requestFor()).extracting(ApprovalPage.RequestLine::qty).containsExactly(3, 1);
        assertThat(page.requestFor().get(0).href()).isEqualTo("/dashboard/store/uma2dqukxr/orders/dd0e0011-aaaa");
        assertThat(page.warehousePieces()).isEqualTo(4);
        assertThat(page.pieces()).isEqualTo(8);
        assertThat(page.net()).isEqualTo("1\u00A0423,00");
        assertThat(page.storeName()).isEqualTo("Demo Store");
        assertThat(page.storeHref()).isEqualTo("/dashboard/store/uma2dqukxr");
        assertThat(page.requestedAt()).isEqualTo("02.10.2026, 08:30");
        assertThat(page.shortDeliveryId()).isEqualTo("dd000010");
        assertThat(page.dropship()).isFalse();
    }

    @Test
    void anOrderThatCannotBeReadStillGetsALineWithItsShortNumber() {
        // given
        Delivery delivery = delivery(allocation("dd0e0099-cccc", 2, 5.0));

        // when
        ApprovalPage page = ApprovalPage.of(delivery, null, List.of(), "Acme", null, null, true);

        // then
        assertThat(page.requestFor()).singleElement().satisfies(line -> {
            assertThat(line.orderLabel()).isEqualTo("#dd0e0099");
            assertThat(line.customer()).isNull();
            assertThat(line.qty()).isEqualTo(2);
        });
        assertThat(page.storeName()).isEqualTo("uma2dqukxr");
        assertThat(page.openReject()).isTrue();
    }

    @Test
    void linksPointAtTheStoreScopedSuperAdminRoutes() {
        // when
        ApprovalPage page = ApprovalPage.of(delivery(), null, List.of(), "Acme", null, null, false);

        // then
        assertThat(page.detailsHref()).isEqualTo("/dashboard/store/uma2dqukxr/deliveries/details?deliveryId=dd000010-0000-4000-8000-000000000010");
        assertThat(page.rejectHref()).isEqualTo("/dashboard/store/uma2dqukxr/deliveries/dd000010-0000-4000-8000-000000000010/reject");
        assertThat(page.validateHref()).isEqualTo("/dashboard/store/uma2dqukxr/deliveries/dd000010-0000-4000-8000-000000000010/approval/validate");
        assertThat(page.approveHref()).isEqualTo("/dashboard/store/uma2dqukxr/deliveries/dd000010-0000-4000-8000-000000000010/approve");
    }
}
