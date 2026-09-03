package pl.commercelink.orders.filters;

import pl.commercelink.orders.filters.exceptions.OrderFilterInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pl.commercelink.orders.filters.model.OrderFilterConditionSerializer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderFilterConditionsTest {

    private static OrderFilterCondition condition(OrderFilterField field, String rawValue) {
        return new OrderFilterCondition(field, field.normalize(rawValue));
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

        assertThat(first.conditions()).isEqualTo(second.conditions());
    }

    @Test
    @DisplayName("letter case does not change the filter, because matching ignores it too")
    void letterCaseDoesNotMatter() {
        OrderFilterConditions upper = OrderFilterConditions.of(List.of(condition(OrderFilterField.ShipmentType, "COURIER")));
        OrderFilterConditions lower = OrderFilterConditions.of(List.of(condition(OrderFilterField.ShipmentType, " courier ")));

        assertThat(upper.conditions()).isEqualTo(lower.conditions());
    }

    @Test
    @DisplayName("a postal code is normalised the same way the matcher normalises it")
    void postalCodeIsNormalised() {
        OrderFilterConditions dashed = OrderFilterConditions.of(List.of(condition(OrderFilterField.ShippingPostalCode, "00-9")));
        OrderFilterConditions plain = OrderFilterConditions.of(List.of(condition(OrderFilterField.ShippingPostalCode, "009")));

        assertThat(dashed.conditions()).isEqualTo(plain.conditions());
    }

    @Test
    @DisplayName("the same condition given twice counts once")
    void duplicatesCollapse() {
        OrderFilterConditions conditions = OrderFilterConditions.of(List.of(
                condition(OrderFilterField.ShipmentType, "Courier"),
                condition(OrderFilterField.ShipmentType, "Courier")));

        assertThat(conditions.conditions()).containsExactly(condition(OrderFilterField.ShipmentType, "Courier"));
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
        assertThatThrownBy(() -> OrderFilterConditions.of(List.of()))
                .isInstanceOf(OrderFilterInvalidException.class);
        assertThatThrownBy(() -> OrderFilterConditions.of(null))
                .isInstanceOf(OrderFilterInvalidException.class);
    }

    @Test
    @DisplayName("stored entries referring to an unknown field cannot be read back")
    void storedEntriesWithAnUnknownFieldCannotBeReadBack() {
        assertThat(OrderFilterConditionSerializer.fromStoredEntries(List.of("ShipmentType=COURIER"))).isPresent();
        assertThat(OrderFilterConditionSerializer.fromStoredEntries(List.of("RemovedField=WHATEVER"))).isEmpty();
        assertThat(OrderFilterConditionSerializer.fromStoredEntries(List.of("ShipmentType=COURIER", "RemovedField=X"))).isEmpty();
        assertThat(OrderFilterConditionSerializer.fromStoredEntries(List.of())).isEmpty();
    }

    @Test
    @DisplayName("what is written can be read back unchanged")
    void whatIsWrittenCanBeReadBack() {
        OrderFilterConditions original = OrderFilterConditions.of(List.of(
                condition(OrderFilterField.ShipmentType, "Courier"),
                condition(OrderFilterField.ShippingDue, "DueToday")));

        List<String> stored = OrderFilterConditionSerializer.toStoredEntries(original);

        assertThat(stored).containsExactly("ShipmentType=COURIER", "ShippingDue=DUETODAY");
        assertThat(OrderFilterConditionSerializer.fromStoredEntries(stored))
                .hasValueSatisfying(read -> assertThat(read.conditions()).isEqualTo(original.conditions()));
    }
}
