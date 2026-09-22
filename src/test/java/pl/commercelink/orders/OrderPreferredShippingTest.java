package pl.commercelink.orders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPreferredShippingTest {

    // Monday
    private static final LocalDate ESTIMATED = LocalDate.of(2026, 9, 21);

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
    @DisplayName("the window spans from the estimated date to 14 days later, weekdays only")
    void windowCoversWeekdaysFromEstimateToFourteenDaysLater() {
        // given
        Order order = orderWithEstimate();

        // when / then
        assertThat(order.getPreferredShippingWindowEnd()).isEqualTo(ESTIMATED.plusDays(14));
        assertThat(order.isWithinPreferredShippingWindow(ESTIMATED)).isTrue();
        assertThat(order.isWithinPreferredShippingWindow(ESTIMATED.plusDays(14))).isTrue();
        assertThat(order.isWithinPreferredShippingWindow(ESTIMATED.minusDays(1))).isFalse();
        assertThat(order.isWithinPreferredShippingWindow(ESTIMATED.plusDays(15))).isFalse();
        assertThat(order.isWithinPreferredShippingWindow(ESTIMATED.plusDays(5))).as("saturday").isFalse();
        assertThat(order.isWithinPreferredShippingWindow(ESTIMATED.plusDays(6))).as("sunday").isFalse();
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
