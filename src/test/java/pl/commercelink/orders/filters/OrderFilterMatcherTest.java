package pl.commercelink.orders.filters;

import pl.commercelink.orders.filters.model.OrderFilter;

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

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderFilterMatcherTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 3);

    private final OrderFilterMatcher matcher =
            new OrderFilterMatcher(Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));

    private static Order order() {
        Order order = new Order("store-1");
        order.setShippingDetails(new ShippingDetails());
        return order;
    }

    private static OrderFilter filter(String... conditions) {
        List<OrderFilterCondition> parsed = List.of(conditions).stream()
                .map(entry -> {
                    String[] parts = entry.split("=", 2);
                    return OrderFilterCondition.of(OrderFilterField.parse(parts[0]).orElseThrow(), parts[1]).orElseThrow();
                })
                .toList();
        return OrderFilter.of("test", OrderFilterConditions.of(parsed));
    }

    @Test
    @DisplayName("all conditions have to hold")
    void allConditionsHaveToHold() {
        Order matching = order();
        matching.addShipment(new Shipment(ShipmentType.PickupPoint));
        matching.addPayment(new Payment(PaymentSource.CashOnDelivery));

        Order wrongPayment = order();
        wrongPayment.addShipment(new Shipment(ShipmentType.PickupPoint));
        wrongPayment.addPayment(new Payment(PaymentSource.Card));

        OrderFilter pickupPointOnDelivery = filter("ShipmentType=PickupPoint", "PaymentSource=CashOnDelivery");

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

        OrderFilter due = filter("ShippingDue=DueToday");

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

        assertThat(matcher.matches(overdue, filter("ShippingDue=Overdue"))).isTrue();
        assertThat(matcher.matches(dueToday, filter("ShippingDue=Overdue"))).isFalse();
    }

    @Test
    @DisplayName("orders without a shipping date are matched only by the unscheduled option")
    void unscheduledMatchesOrdersWithoutDate() {
        Order withoutDate = order();

        assertThat(matcher.matches(withoutDate, filter("ShippingDue=Unscheduled"))).isTrue();
        assertThat(matcher.matches(withoutDate, filter("ShippingDue=DueToday"))).isFalse();
    }

    @Test
    @DisplayName("postal code is matched by prefix regardless of the dash")
    void postalCodeIsMatchedByPrefix() {
        Order warsaw = order();
        warsaw.getShippingDetails().setPostalCode("00-950");

        assertThat(matcher.matches(warsaw, filter("ShippingPostalCode=00"))).isTrue();
        assertThat(matcher.matches(warsaw, filter("ShippingPostalCode=00-9"))).isTrue();
        assertThat(matcher.matches(warsaw, filter("ShippingPostalCode=02"))).isFalse();
    }

    @Test
    @DisplayName("marketplace is matched by the source name, ignoring case")
    void marketplaceIsMatchedBySourceName() {
        Order fromAllegro = order();
        fromAllegro.setSource(new OrderSource("Allegro", OrderSourceType.Marketplace));

        assertThat(matcher.matches(fromAllegro, filter("SourceName=allegro"))).isTrue();
        assertThat(matcher.matches(fromAllegro, filter("SourceName=Empik"))).isFalse();
    }

    @Test
    @DisplayName("status is matched by name")
    void statusIsMatchedByName() {
        Order assembled = order();
        assembled.setStatus(OrderStatus.Assembled);

        assertThat(matcher.matches(assembled, filter("Status=Assembled"))).isTrue();
        assertThat(matcher.matches(assembled, filter("Status=Blocked"))).isFalse();
    }

    @Test
    @DisplayName("a filter whose stored conditions cannot be read matches nothing")
    void unreadableFilterMatchesNothing() {
        OrderFilter broken = new OrderFilter();
        broken.setId("broken");
        broken.setConditions(List.of("RemovedField=WHATEVER"));

        assertThat(matcher.matches(order(), broken)).isFalse();
        assertThat(matcher.apply(List.of(order(), order()), broken)).isEmpty();
    }

    @Test
    @DisplayName("no filter at all lets every order through")
    void noFilterLetsEverythingThrough() {
        assertThat(matcher.matches(order(), null)).isTrue();
    }

    @Test
    @DisplayName("apply keeps the matching orders in their original order")
    void applyKeepsMatchingOrders() {
        Order first = order();
        first.setEstimatedShippingAt(TODAY.minusDays(1));
        Order second = order();
        second.setEstimatedShippingAt(TODAY.plusDays(5));
        Order third = order();
        third.setEstimatedShippingAt(TODAY);

        List<Order> matching = matcher.apply(List.of(first, second, third), filter("ShippingDue=DueToday"));

        assertThat(matching).containsExactly(first, third);
    }
}
