package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseReportRowTest {

    @Test
    void headersReturnFiveLabelsInOrderWithSupplierFirst() {
        // when / then
        assertThat(PurchaseReportRow.headers()).containsExactly("Supplier", "Category", "MFN", "Name", "Quantity");
    }

    @Test
    void asStringArrayWritesAllValues() {
        // given
        PurchaseReportRow row = new PurchaseReportRow("AcmeA", "GPU", "RTX4070-DUAL", "RTX 4070", 5);

        // when / then
        assertThat(row.asStringArray()).containsExactly("AcmeA", "GPU", "RTX4070-DUAL", "RTX 4070", "5");
    }

    @Test
    void asStringArrayLeavesCellsEmptyWhenNull() {
        // given
        PurchaseReportRow row = new PurchaseReportRow(null, null, null, null, 3);

        // when / then
        assertThat(row.asStringArray()).containsExactly("", "", "", "", "3");
    }
}
