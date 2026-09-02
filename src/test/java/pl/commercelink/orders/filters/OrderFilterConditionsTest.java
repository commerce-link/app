package pl.commercelink.orders.filters;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderFilterConditionsTest {

    @Test
    @DisplayName("the order in which conditions were given does not change the filter")
    void orderOfConditionsDoesNotMatter() {
        OrderFilterConditions first = OrderFilterConditions.of(List.of("ShipmentType=Courier", "ShippingDue=DueToday"));
        OrderFilterConditions second = OrderFilterConditions.of(List.of("ShippingDue=DueToday", "ShipmentType=Courier"));

        assertThat(first.canonicalForm()).isEqualTo(second.canonicalForm());
        assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
    }

    @Test
    @DisplayName("letter case does not change the filter, because matching ignores it too")
    void letterCaseDoesNotMatter() {
        OrderFilterConditions upper = OrderFilterConditions.of(List.of("ShipmentType=COURIER"));
        OrderFilterConditions lower = OrderFilterConditions.of(List.of("shipmenttype=courier"));

        assertThat(upper.fingerprint()).isEqualTo(lower.fingerprint());
    }

    @Test
    @DisplayName("a postal code is normalised the same way the matcher normalises it")
    void postalCodeIsNormalised() {
        OrderFilterConditions dashed = OrderFilterConditions.of(List.of("ShippingPostalCode=00-9"));
        OrderFilterConditions plain = OrderFilterConditions.of(List.of("ShippingPostalCode=009"));

        assertThat(dashed.fingerprint()).isEqualTo(plain.fingerprint());
    }

    @Test
    @DisplayName("the same condition given twice counts once")
    void duplicatesCollapse() {
        OrderFilterConditions conditions = OrderFilterConditions.of(
                List.of("ShipmentType=Courier", "ShipmentType=Courier"));

        assertThat(conditions.entries()).containsExactly("ShipmentType=COURIER");
    }

    @Test
    @DisplayName("different conditions produce different fingerprints")
    void differentConditionsDiffer() {
        OrderFilterConditions courier = OrderFilterConditions.of(List.of("ShipmentType=Courier"));
        OrderFilterConditions pickupPoint = OrderFilterConditions.of(List.of("ShipmentType=PickupPoint"));

        assertThat(courier.fingerprint()).isNotEqualTo(pickupPoint.fingerprint());
    }

    @Test
    @DisplayName("a filter without conditions cannot be built")
    void emptyFilterIsRejected() {
        assertThatThrownBy(() -> OrderFilterConditions.of(List.of()))
                .isInstanceOf(OrderFilterInvalidException.class);
        assertThatThrownBy(() -> OrderFilterConditions.of(List.of("   ")))
                .isInstanceOf(OrderFilterInvalidException.class);
    }

    @Test
    @DisplayName("a field outside the whitelist is rejected")
    void fieldOutsideTheWhitelistIsRejected() {
        assertThatThrownBy(() -> OrderFilterConditions.of(List.of("version=1")))
                .isInstanceOf(OrderFilterInvalidException.class);
        assertThatThrownBy(() -> OrderFilterConditions.of(List.of("Courier")))
                .isInstanceOf(OrderFilterInvalidException.class);
    }
}
