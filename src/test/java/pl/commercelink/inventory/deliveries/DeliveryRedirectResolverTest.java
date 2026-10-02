package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.fulfilment.FulfilmentType;


import static org.assertj.core.api.Assertions.assertThat;

class DeliveryRedirectResolverTest {

    private final DeliveryRedirectResolver resolver = new DeliveryRedirectResolver();

    private static Order order(FulfilmentType fulfilmentType) {
        Order order = new Order();
        order.setOrderId("order-1");
        order.setFulfilmentType(fulfilmentType);
        return order;
    }

    /** Resolved as for an order whose dropship assessment accepts the item's supplier. */
    private String resolve(Order order, OrderItem item) {
        return resolver.resolveFor(order, item, item.getDeliveryId() == null
                ? DropshipAssessment.rejected(DropshipRejection.NOTHING_ALLOCATED)
                : DropshipAssessment.of(java.util.List.of(item.getDeliveryId())));
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
        String url = resolve(order, item);

        // then
        assertThat(url).isEqualTo("/dashboard/warehouse");
    }

    @Test
    void newItemOnWarehouseOrderLinksToDeliveryCreation() {
        // given
        Order order = order(FulfilmentType.WarehouseFulfilment);
        OrderItem item = item("AcmeB", FulfilmentStatus.New);

        // when
        String url = resolve(order, item);

        // then
        assertThat(url).isEqualTo("/dashboard/deliveries/create/AcmeB");
    }

    @Test
    void newItemOnDirectToConsumerOrderLinksToDropshipPage() {
        // given
        Order order = order(FulfilmentType.DirectToConsumer);
        OrderItem item = item("AcmeB", FulfilmentStatus.New);

        // when
        String url = resolve(order, item);

        // then
        assertThat(url).isEqualTo("/dashboard/deliveries/create/AcmeB?order=order-1&from=order");
    }

    @Test
    void allocationItemOnDirectToConsumerOrderLinksToDropshipPage() {
        // given
        Order order = order(FulfilmentType.DirectToConsumer);
        OrderItem item = item("AcmeB", FulfilmentStatus.Allocation);

        // when
        String url = resolve(order, item);

        // then
        assertThat(url).isEqualTo("/dashboard/deliveries/create/AcmeB?order=order-1&from=order");
    }

    @Test
    void orderedItemLinksToDeliveryDetails() {
        // given
        Order order = order(FulfilmentType.DirectToConsumer);
        OrderItem item = item("AcmeB", FulfilmentStatus.Ordered);

        // when
        String url = resolve(order, item);

        // then
        assertThat(url).isEqualTo("/dashboard/deliveries/details?deliveryId=AcmeB");
    }

    @Test
    void warehouseFulfilledItemOnDirectToConsumerOrderLinksToTheWarehouse() {
        // given
        Order order = order(FulfilmentType.DirectToConsumer);
        OrderItem item = item(SupplierRegistry.WAREHOUSE, FulfilmentStatus.Allocation);

        // when
        String url = resolve(order, item);

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
        String url = resolve(order, item);

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
        String url = resolve(order, item);

        // then
        assertThat(url).isEqualTo("/dashboard/deliveries/details?deliveryId=delivery-1");
    }

    @Test
    void dropshipCreateLinkIsRecognisedByItsOrderParameterNotBySubstring() {
        // when / then
        assertThat(DeliveryRedirectResolver.isDropshipCreateLink("/dashboard/deliveries/create/Acme?order=o-1&from=order")).isTrue();
        assertThat(DeliveryRedirectResolver.isDropshipCreateLink("/dashboard/deliveries/create/Acme")).isFalse();
        assertThat(DeliveryRedirectResolver.isDropshipCreateLink("/dashboard/deliveries/details?deliveryId=dropship-1")).isFalse();
    }

    @Test
    void newItemWithAProviderNameRequiringEncodingLinksToTheEncodedDropshipPage() {
        // given
        Order order = order(FulfilmentType.DirectToConsumer);
        String provider = "Acme & B";
        OrderItem item = item(provider, FulfilmentStatus.New);

        // when
        String url = resolve(order, item);

        // then
        assertThat(url).isEqualTo("/dashboard/deliveries/create/Acme%20&%20B?order=order-1&from=order");
    }

    @Test
    void aDirectToConsumerItemOfASupplierWithoutDropshippingLinksToTheWarehouseDeliveryPlanning() {
        // given: DeliveriesPlanningService sends such items the ordinary warehouse route, the dropship page refuses them
        Order order = order(FulfilmentType.DirectToConsumer);
        OrderItem item = item("AcmeB", FulfilmentStatus.Allocation);

        // when
        String url = resolver.resolveFor(order, item, DropshipAssessment.of(java.util.List.of("Acme")));

        // then
        assertThat(url).isEqualTo("/dashboard/deliveries/create/AcmeB");
    }
}
