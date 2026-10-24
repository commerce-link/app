package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.DiscountedOrderEntry;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.web.dtos.FormNumbers;
import pl.commercelink.web.settings.DiscountCodeDetailsView.OrderRow;
import pl.commercelink.web.settings.DiscountCodeDetailsView.Orders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountCodeDetailsViewTest {

    private static final String ORDERS_PATH = "/dashboard/orders";

    private static DiscountedOrderEntry entry(String orderId, double totalPrice, double discountAmount, OrderStatus status) {
        return new DiscountedOrderEntry(orderId, LocalDateTime.of(2026, 10, 20, 9, 30), "ewa@example.com", totalPrice,
                discountAmount, status);
    }

    @Test
    void theFiguresAddUpTheListedOrders() {
        // given
        List<DiscountedOrderEntry> entries = List.of(
                entry("c381d457-0000-0000-0000-000000000001", 1800, 200, OrderStatus.New),
                entry("b5a8331d-0000-0000-0000-000000000002", 450, 50, OrderStatus.Cancelled),
                entry("9e41b7a0-0000-0000-0000-000000000003", 900, 100, OrderStatus.Completed));

        // when
        Orders orders = Orders.of(Optional.of(entries), null, null, ORDERS_PATH);

        // then
        assertThat(orders.available()).isTrue();
        assertThat(orders.count()).isEqualTo(3);
        assertThat(orders.discount()).isEqualTo(FormNumbers.format(350));
        assertThat(orders.value()).isEqualTo(FormNumbers.format(3150));
        assertThat(orders.average()).isEqualTo(FormNumbers.format(1050));
        assertThat(orders.rows()).extracting(OrderRow::shortId).containsExactly("c381d457", "b5a8331d", "9e41b7a0");
    }

    @Test
    void theAverageIsRoundedHalfUpToTheGrosz() {
        // given
        List<DiscountedOrderEntry> entries = List.of(
                entry("a", 100, 10, OrderStatus.New),
                entry("b", 50.01, 5, OrderStatus.New));

        // when
        Orders orders = Orders.of(Optional.of(entries), null, null, ORDERS_PATH);

        // then
        assertThat(orders.value()).isEqualTo("150,01");
        assertThat(orders.average()).isEqualTo("75,01");
    }

    @Test
    void withoutOrdersThereIsNoAverage() {
        // when
        Orders orders = Orders.of(Optional.of(List.of()), null, null, ORDERS_PATH);

        // then
        assertThat(orders.count()).isZero();
        assertThat(orders.discount()).isEqualTo("0,00");
        assertThat(orders.value()).isEqualTo("0,00");
        assertThat(orders.average()).isNull();
        assertThat(orders.rows()).isEmpty();
    }

    @Test
    void aRowShowsTheOrderAsTheOrdersListDoes() {
        // when
        OrderRow row = Orders.of(Optional.of(List.of(entry("c381d457-1111-2222-3333-444455556666", 4289, 50, OrderStatus.Delivered))),
                null, null, ORDERS_PATH).rows().get(0);

        // then
        assertThat(row).isEqualTo(new OrderRow("c381d457", "/dashboard/orders/c381d457-1111-2222-3333-444455556666",
                "20.10.2026, 09:30", "ewa@example.com", FormNumbers.format(4289), "50,00", "OrderStatus.Delivered", "is-ok"));
    }

    @Test
    void withoutAnOrdersPathTheRowsLinkNowhere() {
        // when
        Orders orders = Orders.of(Optional.of(List.of(entry("c381d457-0000", 100, 10, OrderStatus.New))), null, null, null);

        // then
        assertThat(orders.rows().get(0).href()).isNull();
    }

    @Test
    void theDaysAreKeptForTheFormAndTheLabel() {
        // when
        Orders orders = Orders.of(Optional.of(List.of()), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), ORDERS_PATH);

        // then
        assertThat(orders.from()).isEqualTo("2026-10-01");
        assertThat(orders.to()).isEqualTo("2026-10-31");
        assertThat(orders.fromLabel()).isEqualTo("01.10.2026");
        assertThat(orders.toLabel()).isEqualTo("31.10.2026");
        assertThat(orders.narrowed()).isTrue();
    }

    @Test
    void anIndexThatCannotBeReadYetShowsNoFigures() {
        // when
        Orders orders = Orders.of(Optional.empty(), null, null, ORDERS_PATH);

        // then
        assertThat(orders.available()).isFalse();
        assertThat(orders.narrowed()).isFalse();
        assertThat(orders.rows()).isEmpty();
    }
}
