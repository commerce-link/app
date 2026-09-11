package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.fulfilment.FulfilmentType;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryRedirectResolverTest {

    private final DeliveryRedirectResolver resolver = new DeliveryRedirectResolver();

    private static Order order(FulfilmentType fulfilmentType) {
        Order order = new Order();
        order.setOrderId("order-1");
        order.setFulfilmentType(fulfilmentType);
        return order;
    }

    private static OrderItem item(String deliveryId, FulfilmentStatus status) {
        OrderItem item = new OrderItem();
        item.setDeliveryId(deliveryId);
        item.setStatus(status);
        return item;
    }

    @Test
    void warehouseItemLinksToTheWarehouse() {
        // given
        Order order = order(FulfilmentType.WarehouseFulfilment);
        OrderItem item = item(SupplierRegistry.WAREHOUSE, FulfilmentStatus.Delivered);

        // when
        String url = resolver.resolveFor(order, item);

        // then
        assertThat(url).isEqualTo("/dashboard/warehouse");
    }

    @Test
    void newItemOnWarehouseOrderLinksToDeliveryCreation() {
        // given
        Order order = order(FulfilmentType.WarehouseFulfilment);
        OrderItem item = item("AcmeB", FulfilmentStatus.New);

        // when
        String url = resolver.resolveFor(order, item);

        // then
        assertThat(url).isEqualTo("/dashboard/deliveries/create/AcmeB");
    }

    @Test
    void newItemOnDirectToConsumerOrderLinksToDropshipPage() {
        // given
        Order order = order(FulfilmentType.DirectToConsumer);
        OrderItem item = item("AcmeB", FulfilmentStatus.New);

        // when
        String url = resolver.resolveFor(order, item);

        // then
        assertThat(url).isEqualTo("/dashboard/orders/order-1/dropship?provider=AcmeB");
    }

    @Test
    void allocationItemOnDirectToConsumerOrderLinksToDropshipPage() {
        // given
        Order order = order(FulfilmentType.DirectToConsumer);
        OrderItem item = item("AcmeB", FulfilmentStatus.Allocation);

        // when
        String url = resolver.resolveFor(order, item);

        // then
        assertThat(url).isEqualTo("/dashboard/orders/order-1/dropship?provider=AcmeB");
    }

    @Test
    void orderedItemLinksToDeliveryDetails() {
        // given
        Order order = order(FulfilmentType.DirectToConsumer);
        OrderItem item = item("AcmeB", FulfilmentStatus.Ordered);

        // when
        String url = resolver.resolveFor(order, item);

        // then
        assertThat(url).isEqualTo("/dashboard/deliveries/details?deliveryId=AcmeB");
    }

    @Test
    void warehouseFulfilledItemOnDirectToConsumerOrderLinksToTheWarehouse() {
        // given
        Order order = order(FulfilmentType.DirectToConsumer);
        OrderItem item = item(SupplierRegistry.WAREHOUSE, FulfilmentStatus.Allocation);

        // when
        String url = resolver.resolveFor(order, item);

        // then
        assertThat(url).isEqualTo("/dashboard/warehouse");
    }

    @Test
    void claimedItemLinksToTheDeliveryThatClaimedItInsteadOfDeliveryCreation() {
        // given
        Order order = order(FulfilmentType.WarehouseFulfilment);
        OrderItem item = item("AcmeB", FulfilmentStatus.Allocation);
        item.markAsClaimed("delivery-1");

        // when
        String url = resolver.resolveFor(order, item);

        // then
        assertThat(url).isEqualTo("/dashboard/deliveries/details?deliveryId=delivery-1");
    }

    @Test
    void claimedItemOnDirectToConsumerOrderLinksToTheDeliveryInsteadOfTheDropshipPage() {
        // given
        Order order = order(FulfilmentType.DirectToConsumer);
        OrderItem item = item("AcmeB", FulfilmentStatus.Allocation);
        item.markAsClaimed("delivery-1");

        // when
        String url = resolver.resolveFor(order, item);

        // then
        assertThat(url).isEqualTo("/dashboard/deliveries/details?deliveryId=delivery-1");
    }

    @Test
    void newItemWithAProviderNameRequiringEncodingLinksToTheEncodedDropshipPage() {
        // given
        Order order = order(FulfilmentType.DirectToConsumer);
        String provider = "Acme & B";
        OrderItem item = item(provider, FulfilmentStatus.New);

        // when
        String url = resolver.resolveFor(order, item);

        // then
        String encodedProvider = URLEncoder.encode(provider, StandardCharsets.UTF_8);
        assertThat(url).isEqualTo("/dashboard/orders/order-1/dropship?provider=" + encodedProvider);
        assertThat(url).isEqualTo("/dashboard/orders/order-1/dropship?provider=Acme+%26+B");
    }
}
