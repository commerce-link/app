package pl.commercelink.orders.filters.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import pl.commercelink.orders.filters.OrderFilterField;
import pl.commercelink.orders.filters.exceptions.OrderFilterInvalidException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderFilterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 3);

    private static OrderFilterCondition condition(OrderFilterField field, String rawValue) {
        return OrderFilterCondition.of(field, rawValue);
    }

    private static OrderFilter filter(OrderFilterCondition... conditions) {
        return OrderFilter.of("test", List.of(conditions));
    }

    private static Order order() {
        Order order = new Order("store-1");
        order.setShippingDetails(new ShippingDetails());
        return order;
    }

    @Nested
    class Building {

        @Test
        @DisplayName("a condition keeps the value as it was typed, only trimmed")
        void conditionKeepsWhatWasTyped() {
            // when / then
            assertThat(condition(OrderFilterField.ShippingPostalCode, "00-9").getValue()).isEqualTo("00-9");
            assertThat(condition(OrderFilterField.ShipmentType, " courier ").getValue()).isEqualTo("courier");
        }

        @Test
        @DisplayName("letter case and punctuation are ignored when the condition is matched, not when it is stored")
        void spellingIsIgnoredWhenMatching() {
            // given
            Order courierToWarsaw = order();
            courierToWarsaw.addShipment(new Shipment(ShipmentType.Courier));
            courierToWarsaw.getShippingDetails().setPostalCode("00-950");

            // when / then
            assertThat(filter(condition(OrderFilterField.ShipmentType, " courier ")).matches(courierToWarsaw, TODAY))
                    .isTrue();
            assertThat(filter(condition(OrderFilterField.ShippingPostalCode, "00-9")).matches(courierToWarsaw, TODAY))
                    .isTrue();
        }

        @Test
        @DisplayName("the same condition given twice counts once")
        void duplicatesCollapse() {
            OrderFilter filter = filter(
                    condition(OrderFilterField.ShipmentType, "Courier"),
                    condition(OrderFilterField.ShipmentType, "Courier"));

            assertThat(filter.getConditions()).containsExactly(condition(OrderFilterField.ShipmentType, "Courier"));
        }

        @Test
        @DisplayName("a blank value does not become a condition")
        void blankValueIsNotACondition() {
            assertThat(OrderFilterField.SourceName.normalize("   ")).isEmpty();
            assertThat(OrderFilterField.SourceName.normalize(null)).isEmpty();
        }

        @Test
        @DisplayName("a filter without conditions cannot be built")
        void emptyFilterIsRejected() {
            assertThatThrownBy(() -> OrderFilter.of("test", List.of()))
                    .isInstanceOf(OrderFilterInvalidException.class);
            assertThatThrownBy(() -> OrderFilter.of("test", null))
                    .isInstanceOf(OrderFilterInvalidException.class);
        }

        @Test
        @DisplayName("a filter needs a label")
        void filterNeedsALabel() {
            assertThatThrownBy(() -> OrderFilter.of("  ", List.of(condition(OrderFilterField.ShipmentType, "Courier"))))
                    .isInstanceOf(OrderFilterInvalidException.class);
        }
    }

    @Nested
    class Matching {

        @Test
        @DisplayName("all conditions have to hold")
        void allConditionsHaveToHold() {
            Order matching = order();
            matching.addShipment(new Shipment(ShipmentType.PickupPoint));
            matching.addPayment(new Payment(PaymentSource.CashOnDelivery));

            Order wrongPayment = order();
            wrongPayment.addShipment(new Shipment(ShipmentType.PickupPoint));
            wrongPayment.addPayment(new Payment(PaymentSource.Card));

            OrderFilter pickupPointOnDelivery = filter(
                    condition(OrderFilterField.ShipmentType, "PickupPoint"),
                    condition(OrderFilterField.PaymentSource, "CashOnDelivery"));

            assertThat(pickupPointOnDelivery.matches(matching, TODAY)).isTrue();
            assertThat(pickupPointOnDelivery.matches(wrongPayment, TODAY)).isFalse();
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

            assertThat(due.matches(overdue, TODAY)).isTrue();
            assertThat(due.matches(dueToday, TODAY)).isTrue();
            assertThat(due.matches(later, TODAY)).isFalse();
        }

        @Test
        @DisplayName("overdue excludes orders due today")
        void overdueExcludesToday() {
            Order overdue = order();
            overdue.setEstimatedShippingAt(TODAY.minusDays(1));
            Order dueToday = order();
            dueToday.setEstimatedShippingAt(TODAY);

            OrderFilter late = filter(condition(OrderFilterField.ShippingDue, "Overdue"));

            assertThat(late.matches(overdue, TODAY)).isTrue();
            assertThat(late.matches(dueToday, TODAY)).isFalse();
        }

        @Test
        @DisplayName("orders without a shipping date are matched only by the unscheduled option")
        void unscheduledMatchesOrdersWithoutDate() {
            Order withoutDate = order();

            assertThat(filter(condition(OrderFilterField.ShippingDue, "Unscheduled")).matches(withoutDate, TODAY)).isTrue();
            assertThat(filter(condition(OrderFilterField.ShippingDue, "DueToday")).matches(withoutDate, TODAY)).isFalse();
        }

        @Test
        @DisplayName("a postal code prefix that carries no digits matches nothing instead of everything")
        void postalCodeWithoutDigitsMatchesNothing() {
            // given
            Order warsaw = order();
            warsaw.getShippingDetails().setPostalCode("00-950");

            // when / then
            assertThat(filter(condition(OrderFilterField.ShippingPostalCode, "abc")).matches(warsaw, TODAY)).isFalse();
            assertThat(filter(condition(OrderFilterField.ShippingPostalCode, "--")).matches(warsaw, TODAY)).isFalse();
        }

        @Test
        @DisplayName("letters in a postal code prefix are ignored, the digits still decide")
        void lettersInAPostalCodePrefixAreIgnored() {
            // given
            Order warsaw = order();
            warsaw.getShippingDetails().setPostalCode("00-950");

            // when / then
            assertThat(filter(condition(OrderFilterField.ShippingPostalCode, "00-9x")).matches(warsaw, TODAY)).isTrue();
            assertThat(filter(condition(OrderFilterField.ShippingPostalCode, "02x")).matches(warsaw, TODAY)).isFalse();
        }

        @Test
        @DisplayName("postal code is matched by prefix regardless of the dash")
        void postalCodeIsMatchedByPrefix() {
            Order warsaw = order();
            warsaw.getShippingDetails().setPostalCode("00-950");

            assertThat(filter(condition(OrderFilterField.ShippingPostalCode, "00")).matches(warsaw, TODAY)).isTrue();
            assertThat(filter(condition(OrderFilterField.ShippingPostalCode, "00-9")).matches(warsaw, TODAY)).isTrue();
            assertThat(filter(condition(OrderFilterField.ShippingPostalCode, "02")).matches(warsaw, TODAY)).isFalse();
        }

        @Test
        @DisplayName("marketplace is matched by the source name, ignoring case")
        void marketplaceIsMatchedBySourceName() {
            Order fromAllegro = order();
            fromAllegro.setSource(new OrderSource("Allegro", OrderSourceType.Marketplace));

            assertThat(filter(condition(OrderFilterField.SourceName, "allegro")).matches(fromAllegro, TODAY)).isTrue();
            assertThat(filter(condition(OrderFilterField.SourceName, "Empik")).matches(fromAllegro, TODAY)).isFalse();
        }

        @Test
        @DisplayName("status is matched by name")
        void statusIsMatchedByName() {
            Order assembled = order();
            assembled.setStatus(OrderStatus.Assembled);

            assertThat(filter(condition(OrderFilterField.Status, "Assembled")).matches(assembled, TODAY)).isTrue();
            assertThat(filter(condition(OrderFilterField.Status, "Blocked")).matches(assembled, TODAY)).isFalse();
        }

        @Test
        @DisplayName("a stored filter that lost its conditions matches nothing")
        void filterWithoutConditionsMatchesNothing() {
            OrderFilter broken = new OrderFilter();
            broken.setId("broken");
            broken.setConditions(List.of());

            assertThat(broken.matches(order(), TODAY)).isFalse();
        }
    }
}
