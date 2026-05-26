package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductWeightOriginComplianceReportRowTest {

    @Test
    void headersReturnTenLabelsInOrderWithCountryFirst() {
        assertThat(ProductWeightOriginComplianceReportRow.headers()).containsExactly(
                "Country", "Category", "Brand", "Name", "MFN", "Quantity",
                "Net weight (g)", "Gross weight (g)",
                "Total net weight (g)", "Total gross weight (g)"
        );
    }

    @Test
    void asStringArrayWritesAllValues() {
        ProductWeightOriginComplianceReportRow row = new ProductWeightOriginComplianceReportRow(
                "Germany", "GPU", "ASUS", "RTX 4070", "RTX4070-DUAL", 5,
                2100, 2400, 10500L, 12000L
        );

        assertThat(row.asStringArray()).containsExactly(
                "Germany", "GPU", "ASUS", "RTX 4070", "RTX4070-DUAL", "5",
                "2100", "2400", "10500", "12000"
        );
    }

    @Test
    void asStringArrayLeavesWeightCellsEmptyWhenNull() {
        ProductWeightOriginComplianceReportRow row = new ProductWeightOriginComplianceReportRow(
                "Unknown", "Other", null, "Foo", "FOO-1", 3,
                null, null, null, null
        );

        assertThat(row.asStringArray()).containsExactly(
                "Unknown", "Other", "", "Foo", "FOO-1", "3",
                "", "", "", ""
        );
    }

    @Test
    void asStringArrayHandlesPartialWeights() {
        ProductWeightOriginComplianceReportRow row = new ProductWeightOriginComplianceReportRow(
                "Poland", "PSU", "Corsair", "PSU-1", "PSU-1", 2,
                null, 950, null, 1900L
        );

        assertThat(row.asStringArray()).containsExactly(
                "Poland", "PSU", "Corsair", "PSU-1", "PSU-1", "2",
                "", "950", "", "1900"
        );
    }
}
