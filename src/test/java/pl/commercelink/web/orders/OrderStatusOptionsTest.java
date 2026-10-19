package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusOptionsTest {

    @Test
    void manualStatusesWithTheirEffectAndDeliveredBlockedWithoutShipmentData() {
        // given
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Assembly);
        order.addShipment(new Shipment(ShipmentType.Courier));
        order.setEmailNotificationsEnabled(true);
        order.setExternalOrderId("7012973720");
        order.setSource(new OrderSource("Allegro", OrderSourceType.Marketplace));

        // when
        OrderStatusOptions options = OrderStatusOptions.of(order);

        // then
        assertThat(options.options()).extracting(OrderStatusOptions.Option::status).containsExactly(
                OrderStatus.New, OrderStatus.Blocked, OrderStatus.Assembly, OrderStatus.Assembled,
                OrderStatus.Realization, OrderStatus.Shipping, OrderStatus.Delivered);
        OrderStatusOptions.Option assembly = options.options().get(2);
        assertThat(assembly.current()).isTrue();
        assertThat(assembly.effectKey()).isEqualTo("order.status.effect.Assembly");
        OrderStatusOptions.Option delivered = options.options().get(6);
        assertThat(delivered.disabled()).isTrue();
        assertThat(delivered.reasonKey()).isEqualTo("order.status.effect.Delivered.unavailable");
        assertThat(options.emailNote()).isTrue();
        assertThat(options.marketplaceName()).isEqualTo("Allegro");
    }
}
