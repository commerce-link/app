package pl.commercelink.web.orders;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.commercelink.orders.filters.OrderFilterField;

import static org.assertj.core.api.Assertions.assertThat;

class FilterConditionLabelsTest {

    @ParameterizedTest
    @CsvSource({
            "Status, status",
            "ShipmentType, shipment.type",
            "PaymentSource, payment.source",
            "SourceName, marketplace",
            "ShippingPostalCode, postal.code",
            "ShippingDue, shipping.due"
    })
    void fieldKeyIsTheTailOfTheMessageKey(OrderFilterField field, String expected) {
        assertThat(FilterConditionLabels.fieldKey(field)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "Status, status",
            "ShipmentType, shipmentType",
            "PaymentSource, paymentSource",
            "SourceName, sourceName",
            "ShippingPostalCode, shippingPostalCode",
            "ShippingDue, shippingDue"
    })
    void formFieldIsTheFormParameterName(String fieldName, String expected) {
        assertThat(FilterConditionLabels.formField(fieldName)).isEqualTo(expected);
    }
}
