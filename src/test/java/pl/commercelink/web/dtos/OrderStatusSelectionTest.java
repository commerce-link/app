package pl.commercelink.web.dtos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusSelectionTest {

    private static Order orderWith(OrderStatus status) {
        Order order = new Order();
        order.setStatus(status);
        return order;
    }

    private static final List<Order> MIXED =
            List.of(orderWith(OrderStatus.New), orderWith(OrderStatus.Assembly), orderWith(OrderStatus.Assembled));

    @Test
    @DisplayName("statuses picked by hand win over the request to skip the default")
    void handPickedStatusesWin() {
        OrderStatusSelection selection = OrderStatusSelection.resolve(MIXED, List.of("New"), true);

        assertThat(selection.selected()).containsExactly("New");
        assertThat(selection.narrow(MIXED)).hasSize(1);
    }

    @Test
    @DisplayName("skipping the default leaves every open order in place")
    void skippingTheDefaultKeepsEveryOrder() {
        OrderStatusSelection selection = OrderStatusSelection.resolve(MIXED, List.of(), true);

        assertThat(selection.selected()).isEmpty();
        assertThat(selection.narrow(MIXED)).isEqualTo(MIXED);
    }

    @Test
    @DisplayName("the default prefers assembled, then assembly, then new")
    void defaultFollowsThePriority() {
        assertThat(OrderStatusSelection.resolve(MIXED, null, false).selected())
                .containsExactly(OrderStatus.Assembled.name());
        assertThat(OrderStatusSelection.resolve(MIXED.subList(0, 2), null, false).selected())
                .containsExactly(OrderStatus.Assembly.name());
        assertThat(OrderStatusSelection.resolve(MIXED.subList(0, 1), null, false).selected())
                .containsExactly(OrderStatus.New.name());
    }

    @Test
    @DisplayName("with nothing to show the default falls back to new")
    void emptyListFallsBackToNew() {
        assertThat(OrderStatusSelection.resolve(List.of(), null, false).selected())
                .containsExactly(OrderStatus.New.name());
    }
}
