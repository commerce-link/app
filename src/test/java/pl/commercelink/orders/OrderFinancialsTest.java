package pl.commercelink.orders;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderFinancialsTest {

    private static OrderItem item(double price, double cost, FulfilmentStatus status) {
        OrderItem item = new OrderItem("o1", "CPU", "Ryzen", 1, price, "SKU", false, 0);
        item.setCost(cost);
        item.setStatus(status);
        return item;
    }

    @Test
    void aCancelledItemKeepsItsPriceButIsNeitherRevenueNorCost() {
        // given
        Order order = new Order("store-1");
        OrderItem cancelled = item(300, 200, FulfilmentStatus.Cancelled);
        OrderItem sold = item(100, 50, FulfilmentStatus.Delivered);

        // when
        OrderFinancials financials = new OrderFinancials(order, List.of(cancelled, sold));

        // then
        assertThat(cancelled.getPrice()).isEqualTo(300);
        assertThat(financials.getTotalPrice()).isEqualTo(100);
        assertThat(financials.getTotalItemsCost()).isEqualTo(50);
    }

    @Test
    void aFullyCancelledOrderReportsNoSale() {
        // given
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Cancelled);

        // when
        OrderFinancials financials = new OrderFinancials(order, List.of(item(300, 0, FulfilmentStatus.Cancelled)));

        // then
        assertThat(financials.getTotalPrice()).isZero();
        assertThat(financials.getTotalProfit()).isZero();
    }
}
