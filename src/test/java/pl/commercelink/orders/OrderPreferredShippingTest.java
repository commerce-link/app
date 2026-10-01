package pl.commercelink.orders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPreferredShippingTest {

    // Monday
    private static final LocalDate ESTIMATED = LocalDate.of(2026, 9, 21);
    private static final Set<DayOfWeek> WEEKDAYS = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

    @Test
    @DisplayName("client can set the preferred date only while assembling or realizing an order with an estimated date")
    void clientCanSetOnlyInAssemblyToRealizationWithEstimate() {
        for (OrderStatus status : OrderStatus.values()) {
            // given
            Order order = orderWithEstimate();
            order.setStatus(status);

            // when / then
            boolean expected = status.isOneOf(OrderStatus.Assembly, OrderStatus.Assembled, OrderStatus.Realization);
            assertThat(order.canClientSetPreferredShippingAt()).as(status.name()).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("client cannot set the preferred date before the estimated shipping date is known")
    void clientCannotSetWithoutEstimate() {
        // given
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Assembly);

        // when / then
        assertThat(order.canClientSetPreferredShippingAt()).isFalse();
        assertThat(order.getPreferredShippingWindowEnd()).isNull();
    }

    @Test
    @DisplayName("the window spans from the estimated date to 14 days later, on the allowed days only")
    void windowCoversAllowedDaysFromEstimateToFourteenDaysLater() {
        // given
        Order order = orderWithEstimate();

        // when / then
        assertThat(order.getPreferredShippingWindowEnd()).isEqualTo(ESTIMATED.plusDays(14));
        assertThat(order.isWithinPreferredShippingWindow(ESTIMATED, WEEKDAYS)).isTrue();
        assertThat(order.isWithinPreferredShippingWindow(ESTIMATED.plusDays(14), WEEKDAYS)).isTrue();
        assertThat(order.isWithinPreferredShippingWindow(ESTIMATED.minusDays(1), WEEKDAYS)).isFalse();
        assertThat(order.isWithinPreferredShippingWindow(ESTIMATED.plusDays(15), WEEKDAYS)).isFalse();
        assertThat(order.isWithinPreferredShippingWindow(ESTIMATED.plusDays(5), WEEKDAYS)).as("saturday").isFalse();
        assertThat(order.isWithinPreferredShippingWindow(ESTIMATED.plusDays(6), WEEKDAYS)).as("sunday").isFalse();
    }

    @Test
    @DisplayName("a saturday is inside the window once the store allows it")
    void saturdayIsAllowedWhenConfigured() {
        // given
        Order order = orderWithEstimate();
        Set<DayOfWeek> withSaturday = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY);

        // when / then
        assertThat(order.isWithinPreferredShippingWindow(ESTIMATED.plusDays(5), withSaturday)).isTrue();
        assertThat(order.isWithinPreferredShippingWindow(ESTIMATED.plusDays(6), withSaturday)).isFalse();
    }

    @Test
    @DisplayName("the shipment type comes from the first shipment and defaults to courier")
    void shipmentTypeComesFromFirstShipment() {
        // given
        Order order = orderWithEstimate();
        Order collection = orderWithEstimate();
        collection.addShipment(new Shipment(ShipmentType.PersonalCollection));

        // when / then
        assertThat(order.getShipmentType()).isEqualTo(ShipmentType.Courier);
        assertThat(collection.getShipmentType()).isEqualTo(ShipmentType.PersonalCollection);
    }

    @Test
    @DisplayName("the shipping due date is the preferred date when set, otherwise the estimated one")
    void shippingDueFallsBackToEstimate() {
        // given
        Order order = orderWithEstimate();

        // when / then
        assertThat(order.getShippingDueAt()).isEqualTo(ESTIMATED);
        order.setPreferredShippingAt(ESTIMATED.plusDays(3));
        assertThat(order.getShippingDueAt()).isEqualTo(ESTIMATED.plusDays(3));
    }

    @Test
    @DisplayName("booking is flagged as early only before the preferred shipping date")
    void bookingEarlyOnlyBeforePreferredDate() {
        // given
        Order order = orderWithEstimate();
        order.setPreferredShippingAt(LocalDate.of(2026, 9, 28));

        // when / then
        assertThat(order.isCourierBookingEarlierThanPreferred(LocalDate.of(2026, 9, 25))).isTrue();
        assertThat(order.isCourierBookingEarlierThanPreferred(LocalDate.of(2026, 9, 28))).isFalse();
        assertThat(order.isCourierBookingEarlierThanPreferred(LocalDate.of(2026, 9, 29))).isFalse();
    }

    @Test
    @DisplayName("booking is never flagged when no preferred date is set")
    void bookingNotFlaggedWithoutPreferredDate() {
        // given
        Order order = orderWithEstimate();

        // when / then
        assertThat(order.isCourierBookingEarlierThanPreferred(ESTIMATED.minusDays(10))).isFalse();
    }

    private static Order orderWithEstimate() {
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Assembly);
        order.setEstimatedShippingAt(ESTIMATED);
        return order;
    }
}
