package pl.commercelink.orders.filters;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderFilterMatcherTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 3);

    private final OrderFilterMatcher matcher = new OrderFilterMatcher(TODAY);

    private static Order order() {
        Order order = new Order("store-1");
        order.setShippingDetails(new ShippingDetails());
        return order;
    }

    private static OrderFilter filter(OrderFilterCondition... conditions) {
        OrderFilter filter = new OrderFilter();
        filter.setConditions(List.of(conditions));
        return filter;
    }

    private static OrderFilterCondition condition(OrderFilterField field, String... values) {
        return new OrderFilterCondition(field, List.of(values));
    }

    @Test
    @DisplayName("a filter without conditions matches every order")
    void emptyFilterMatchesEverything() {
        assertThat(matcher.matches(order(), filter())).isTrue();
        assertThat(matcher.matches(order(), null)).isTrue();
    }

    @Test
    @DisplayName("values of one condition are combined with OR")
    void valuesOfOneConditionAreOred() {
        Order courier = order();
        courier.addShipment(new Shipment(ShipmentType.Courier));

        Order pickupPoint = order();
        pickupPoint.addShipment(new Shipment(ShipmentType.PickupPoint));

        Order personal = order();
        personal.addShipment(new Shipment(ShipmentType.PersonalCollection));

        OrderFilter courierOrPickupPoint = filter(condition(OrderFilterField.ShipmentType, "Courier", "PickupPoint"));

        assertThat(matcher.matches(courier, courierOrPickupPoint)).isTrue();
        assertThat(matcher.matches(pickupPoint, courierOrPickupPoint)).isTrue();
        assertThat(matcher.matches(personal, courierOrPickupPoint)).isFalse();
    }

    @Test
    @DisplayName("separate conditions are combined with AND")
    void separateConditionsAreAnded() {
        Order matching = order();
        matching.addShipment(new Shipment(ShipmentType.PickupPoint));
        matching.addPayment(new Payment(PaymentSource.CashOnDelivery));

        Order wrongPayment = order();
        wrongPayment.addShipment(new Shipment(ShipmentType.PickupPoint));
        wrongPayment.addPayment(new Payment(PaymentSource.Card));

        OrderFilter pickupPointOnDelivery = filter(
                condition(OrderFilterField.ShipmentType, "PickupPoint"),
                condition(OrderFilterField.PaymentSource, "CashOnDelivery"));

        assertThat(matcher.matches(matching, pickupPointOnDelivery)).isTrue();
        assertThat(matcher.matches(wrongPayment, pickupPointOnDelivery)).isFalse();
    }

    @Test
    @DisplayName("orders due today include the ones already overdue")
    void dueTodayIncludesOverdue() {
        Order overdue = order();
        overdue.setEstimatedShippingAt(TODAY.minusDays(2));

        Order dueToday = order();
        dueToday.setEstimatedShippingAt(TODAY);

        Order later = order();
        later.setEstimatedShippingAt(TODAY.plusDays(1));

        OrderFilter due = filter(condition(OrderFilterField.ShippingDue, "DueToday"));

        assertThat(matcher.matches(overdue, due)).isTrue();
        assertThat(matcher.matches(dueToday, due)).isTrue();
        assertThat(matcher.matches(later, due)).isFalse();
    }

    @Test
    @DisplayName("overdue excludes orders due today")
    void overdueExcludesToday() {
        Order overdue = order();
        overdue.setEstimatedShippingAt(TODAY.minusDays(1));

        Order dueToday = order();
        dueToday.setEstimatedShippingAt(TODAY);

        OrderFilter filter = filter(condition(OrderFilterField.ShippingDue, "Overdue"));

        assertThat(matcher.matches(overdue, filter)).isTrue();
        assertThat(matcher.matches(dueToday, filter)).isFalse();
    }

    @Test
    @DisplayName("orders without a shipping date are matched only by the unscheduled option")
    void unscheduledMatchesOrdersWithoutDate() {
        Order withoutDate = order();

        assertThat(matcher.matches(withoutDate, filter(condition(OrderFilterField.ShippingDue, "Unscheduled")))).isTrue();
        assertThat(matcher.matches(withoutDate, filter(condition(OrderFilterField.ShippingDue, "DueToday")))).isFalse();
        assertThat(matcher.matches(withoutDate, filter(condition(OrderFilterField.ShippingDue, "Overdue")))).isFalse();
    }

    @Test
    @DisplayName("postal code is matched by prefix regardless of the dash")
    void postalCodeIsMatchedByPrefix() {
        Order warsaw = order();
        warsaw.getShippingDetails().setPostalCode("00-950");

        OrderFilter filter = filter(condition(OrderFilterField.ShippingPostalCode, "00"));
        OrderFilter withDash = filter(condition(OrderFilterField.ShippingPostalCode, "00-9"));
        OrderFilter other = filter(condition(OrderFilterField.ShippingPostalCode, "02"));

        assertThat(matcher.matches(warsaw, filter)).isTrue();
        assertThat(matcher.matches(warsaw, withDash)).isTrue();
        assertThat(matcher.matches(warsaw, other)).isFalse();
    }

    @Test
    @DisplayName("marketplace is matched by the source name")
    void marketplaceIsMatchedBySourceName() {
        Order fromAllegro = order();
        fromAllegro.setSource(new OrderSource("Allegro", OrderSourceType.Marketplace));

        assertThat(matcher.matches(fromAllegro, filter(condition(OrderFilterField.SourceName, "allegro")))).isTrue();
        assertThat(matcher.matches(fromAllegro, filter(condition(OrderFilterField.SourceName, "Empik")))).isFalse();
    }

    @Test
    @DisplayName("status is matched by name")
    void statusIsMatchedByName() {
        Order assembled = order();
        assembled.setStatus(OrderStatus.Assembled);

        assertThat(matcher.matches(assembled, filter(condition(OrderFilterField.Status, "Assembled", "New")))).isTrue();
        assertThat(matcher.matches(assembled, filter(condition(OrderFilterField.Status, "Blocked")))).isFalse();
    }

    @Test
    @DisplayName("apply keeps only the matching orders and their order")
    void applyKeepsMatchingOrders() {
        Order first = order();
        first.setEstimatedShippingAt(TODAY.minusDays(1));
        Order second = order();
        second.setEstimatedShippingAt(TODAY.plusDays(5));
        Order third = order();
        third.setEstimatedShippingAt(TODAY);

        List<Order> matching = matcher.apply(List.of(first, second, third),
                filter(condition(OrderFilterField.ShippingDue, "DueToday")));

        assertThat(matching).containsExactly(first, third);
    }

    @Test
    @DisplayName("an unknown value never matches instead of breaking the list")
    void unknownValueDoesNotMatch() {
        Order courier = order();
        courier.addShipment(new Shipment(ShipmentType.Courier));

        assertThat(matcher.matches(courier, filter(condition(OrderFilterField.ShipmentType, "Teleportation")))).isFalse();
    }
}
