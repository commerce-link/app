package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseReportRowTest {

    @Test
    void headersReturnSixLabelsInOrderWithCategoryFirst() {
        // when / then
        assertThat(PurchaseReportRow.headers()).containsExactly("Category", "Supplier", "Brand", "Name", "MFN", "Quantity");
    }

    @Test
    void asStringArrayWritesAllValues() {
        // given
        PurchaseReportRow row = new PurchaseReportRow("GPU", "AcmeA", "ASUS", "RTX 4070", "RTX4070-DUAL", 5);

        // when / then
        assertThat(row.asStringArray()).containsExactly("GPU", "AcmeA", "ASUS", "RTX 4070", "RTX4070-DUAL", "5");
    }

    @Test
    void asStringArrayLeavesCellsEmptyWhenNull() {
        // given
        PurchaseReportRow row = new PurchaseReportRow(null, null, null, null, null, 3);

        // when / then
        assertThat(row.asStringArray()).containsExactly("", "", "", "", "", "3");
    }
}
