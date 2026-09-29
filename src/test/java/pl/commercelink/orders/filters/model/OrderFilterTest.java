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
import java.util.Map;

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

        @Test
        @DisplayName("values of one field are alternatives, different fields all have to hold")
        void valuesOfOneFieldAreAlternatives() {
            // given
            Order allegroCourier = order();
            allegroCourier.setSource(new OrderSource("Allegro", OrderSourceType.Marketplace));
            allegroCourier.addShipment(new Shipment(ShipmentType.Courier));
            Order ceneoCourier = order();
            ceneoCourier.setSource(new OrderSource("Ceneo", OrderSourceType.Marketplace));
            ceneoCourier.addShipment(new Shipment(ShipmentType.Courier));
            Order ceneoPickup = order();
            ceneoPickup.setSource(new OrderSource("Ceneo", OrderSourceType.Marketplace));
            ceneoPickup.addShipment(new Shipment(ShipmentType.PickupPoint));
            Order moreleCourier = order();
            moreleCourier.setSource(new OrderSource("Morele", OrderSourceType.Marketplace));
            moreleCourier.addShipment(new Shipment(ShipmentType.Courier));
            OrderFilter allegroOrCeneoByCourier = filter(
                    condition(OrderFilterField.SourceName, "Allegro"),
                    condition(OrderFilterField.SourceName, "Ceneo"),
                    condition(OrderFilterField.ShipmentType, "Courier"));

            // when / then
            assertThat(allegroOrCeneoByCourier.matches(allegroCourier, TODAY)).isTrue();
            assertThat(allegroOrCeneoByCourier.matches(ceneoCourier, TODAY)).isTrue();
            assertThat(allegroOrCeneoByCourier.matches(ceneoPickup, TODAY)).isFalse();
            assertThat(allegroOrCeneoByCourier.matches(moreleCourier, TODAY)).isFalse();
        }

        @Test
        @DisplayName("several statuses match an order in any of them")
        void severalStatusesMatchAnyOfThem() {
            // given
            Order blocked = order();
            blocked.setStatus(OrderStatus.Blocked);
            Order assembled = order();
            assembled.setStatus(OrderStatus.Assembled);
            OrderFilter newOrBlocked = filter(
                    condition(OrderFilterField.Status, "New"),
                    condition(OrderFilterField.Status, "Blocked"));

            // when / then
            assertThat(newOrBlocked.matches(blocked, TODAY)).isTrue();
            assertThat(newOrBlocked.matches(assembled, TODAY)).isFalse();
        }

        @Test
        @DisplayName("ignoring a field drops all of its values, the other fields still have to hold")
        void ignoringAFieldDropsAllItsValues() {
            // given
            Order assembledFromAllegro = order();
            assembledFromAllegro.setStatus(OrderStatus.Assembled);
            assembledFromAllegro.setSource(new OrderSource("Allegro", OrderSourceType.Marketplace));
            Order assembledFromMorele = order();
            assembledFromMorele.setStatus(OrderStatus.Assembled);
            assembledFromMorele.setSource(new OrderSource("Morele", OrderSourceType.Marketplace));
            OrderFilter newOrBlockedFromAllegro = filter(
                    condition(OrderFilterField.Status, "New"),
                    condition(OrderFilterField.Status, "Blocked"),
                    condition(OrderFilterField.SourceName, "Allegro"));

            // when / then
            assertThat(newOrBlockedFromAllegro.matchesIgnoring(OrderFilterField.Status, assembledFromAllegro, TODAY)).isTrue();
            assertThat(newOrBlockedFromAllegro.matchesIgnoring(OrderFilterField.Status, assembledFromMorele, TODAY)).isFalse();
        }

        @Test
        @DisplayName("a filter of statuses only lets every order through once the status is ignored")
        void statusOnlyFilterIgnoringStatusMatchesEverything() {
            // given
            Order assembled = order();
            assembled.setStatus(OrderStatus.Assembled);
            OrderFilter newOrBlocked = filter(
                    condition(OrderFilterField.Status, "New"),
                    condition(OrderFilterField.Status, "Blocked"));

            // when / then
            assertThat(newOrBlocked.matchesIgnoring(OrderFilterField.Status, assembled, TODAY)).isTrue();
        }
    }

    @Nested
    class ByField {

        @Test
        @DisplayName("a filter whose conditions were never stored groups to nothing instead of failing")
        void filterWithoutConditionsHasNoValuesPerField() {
            // given
            OrderFilter stored = filter(condition(OrderFilterField.Status, "New"));
            stored.setConditions(null);

            // when / then
            assertThat(stored.getConditionsByField()).isEmpty();
        }

        @Test
        @DisplayName("several values of one field are all kept, only an exact repeat collapses")
        void severalValuesOfOneFieldAreKept() {
            // when
            OrderFilter filter = filter(
                    condition(OrderFilterField.SourceName, "Allegro"),
                    condition(OrderFilterField.SourceName, "Ceneo"),
                    condition(OrderFilterField.SourceName, "Allegro"));

            // then
            assertThat(filter.getConditions()).containsExactly(
                    condition(OrderFilterField.SourceName, "Allegro"),
                    condition(OrderFilterField.SourceName, "Ceneo"));
        }

        @Test
        @DisplayName("the form gets every value of a field, in the order they were saved")
        void everyValueOfAFieldInSavedOrder() {
            // given
            OrderFilter filter = filter(
                    condition(OrderFilterField.SourceName, "Ceneo"),
                    condition(OrderFilterField.Status, "New"),
                    condition(OrderFilterField.SourceName, "Allegro"));

            // when
            Map<String, List<String>> byField = filter.getConditionsByField();

            // then
            assertThat(byField).containsExactly(
                    Map.entry("SourceName", List.of("Ceneo", "Allegro")),
                    Map.entry("Status", List.of("New")));
        }

        @Test
        @DisplayName("a filter saved with one value per field reads back as before")
        void oneValuePerFieldReadsBackAsBefore() {
            // given
            OrderFilter filter = filter(
                    condition(OrderFilterField.Status, "Assembled"),
                    condition(OrderFilterField.ShippingPostalCode, "00-9"));

            // when
            Map<String, List<String>> byField = filter.getConditionsByField();

            // then
            assertThat(byField).containsExactly(
                    Map.entry("Status", List.of("Assembled")),
                    Map.entry("ShippingPostalCode", List.of("00-9")));
        }
    }
}
