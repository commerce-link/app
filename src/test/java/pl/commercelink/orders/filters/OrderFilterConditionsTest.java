package pl.commercelink.orders.filters;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderFilterConditionsTest {

    private static OrderFilterCondition condition(OrderFilterField field, String value) {
        return OrderFilterCondition.of(field, value).orElseThrow();
    }

    @Test
    @DisplayName("the order in which conditions were given does not change the filter")
    void orderOfConditionsDoesNotMatter() {
        OrderFilterConditions first = OrderFilterConditions.of(List.of(
                condition(OrderFilterField.ShipmentType, "Courier"),
                condition(OrderFilterField.ShippingDue, "DueToday")));
        OrderFilterConditions second = OrderFilterConditions.of(List.of(
                condition(OrderFilterField.ShippingDue, "DueToday"),
                condition(OrderFilterField.ShipmentType, "Courier")));

        assertThat(first.canonicalForm()).isEqualTo(second.canonicalForm());
        assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
    }

    @Test
    @DisplayName("letter case does not change the filter, because matching ignores it too")
    void letterCaseDoesNotMatter() {
        OrderFilterConditions upper = OrderFilterConditions.of(List.of(condition(OrderFilterField.ShipmentType, "COURIER")));
        OrderFilterConditions lower = OrderFilterConditions.of(List.of(condition(OrderFilterField.ShipmentType, " courier ")));

        assertThat(upper.fingerprint()).isEqualTo(lower.fingerprint());
    }

    @Test
    @DisplayName("a postal code is normalised the same way the matcher normalises it")
    void postalCodeIsNormalised() {
        OrderFilterConditions dashed = OrderFilterConditions.of(List.of(condition(OrderFilterField.ShippingPostalCode, "00-9")));
        OrderFilterConditions plain = OrderFilterConditions.of(List.of(condition(OrderFilterField.ShippingPostalCode, "009")));

        assertThat(dashed.fingerprint()).isEqualTo(plain.fingerprint());
    }

    @Test
    @DisplayName("the same condition given twice counts once")
    void duplicatesCollapse() {
        OrderFilterConditions conditions = OrderFilterConditions.of(List.of(
                condition(OrderFilterField.ShipmentType, "Courier"),
                condition(OrderFilterField.ShipmentType, "Courier")));

        assertThat(conditions.entries()).containsExactly("ShipmentType=COURIER");
    }

    @Test
    @DisplayName("different conditions produce different fingerprints")
    void differentConditionsDiffer() {
        OrderFilterConditions courier = OrderFilterConditions.of(List.of(condition(OrderFilterField.ShipmentType, "Courier")));
        OrderFilterConditions pickupPoint = OrderFilterConditions.of(List.of(condition(OrderFilterField.ShipmentType, "PickupPoint")));

        assertThat(courier.fingerprint()).isNotEqualTo(pickupPoint.fingerprint());
    }

    @Test
    @DisplayName("a blank value does not become a condition")
    void blankValueIsNotACondition() {
        assertThat(OrderFilterCondition.of(OrderFilterField.SourceName, "   ")).isEmpty();
        assertThat(OrderFilterCondition.of(OrderFilterField.SourceName, null)).isEmpty();
    }

    @Test
    @DisplayName("a filter without conditions cannot be built")
    void emptyFilterIsRejected() {
        assertThatThrownBy(() -> OrderFilterConditions.of(List.of()))
                .isInstanceOf(OrderFilterInvalidException.class);
        assertThatThrownBy(() -> OrderFilterConditions.of(null))
                .isInstanceOf(OrderFilterInvalidException.class);
    }

    @Test
    @DisplayName("stored conditions referring to an unknown field are not readable")
    void storedConditionsWithAnUnknownFieldAreNotReadable() {
        assertThat(OrderFilterConditions.stored(List.of("ShipmentType=COURIER")).isReadable()).isTrue();
        assertThat(OrderFilterConditions.stored(List.of("RemovedField=WHATEVER")).isReadable()).isFalse();
        assertThat(OrderFilterConditions.stored(List.of("ShipmentType=COURIER", "RemovedField=X")).isReadable()).isFalse();
        assertThat(OrderFilterConditions.stored(List.of()).isReadable()).isFalse();
    }
}
