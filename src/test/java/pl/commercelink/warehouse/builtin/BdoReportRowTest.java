package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BdoReportRowTest {

    @Test
    void headersReturnNineLabelsInOrder() {
        assertThat(BdoReportRow.headers()).containsExactly(
                "Kategoria", "Nazwa", "MFN", "Ilość",
                "Waga netto (g)", "Waga brutto (g)",
                "Waga netto razem (g)", "Waga brutto razem (g)",
                "Dostawca"
        );
    }

    @Test
    void asStringArrayWritesAllValues() {
        BdoReportRow row = new BdoReportRow(
                "GPU", "RTX 4070", "RTX4070-DUAL", 5,
                2100, 2400, 10500L, 12000L,
                "IngramMicro"
        );

        assertThat(row.asStringArray()).containsExactly(
                "GPU", "RTX 4070", "RTX4070-DUAL", "5",
                "2100", "2400", "10500", "12000",
                "IngramMicro"
        );
    }

    @Test
    void asStringArrayLeavesWeightCellsEmptyWhenNull() {
        BdoReportRow row = new BdoReportRow(
                "Other", "Foo", "FOO-1", 3,
                null, null, null, null,
                "Unknown"
        );

        assertThat(row.asStringArray()).containsExactly(
                "Other", "Foo", "FOO-1", "3",
                "", "", "", "",
                "Unknown"
        );
    }

    @Test
    void asStringArrayHandlesPartialWeights() {
        BdoReportRow row = new BdoReportRow(
                "PSU", "PSU-1", "PSU-1", 2,
                null, 950, null, 1900L,
                "Wortmann"
        );

        assertThat(row.asStringArray()).containsExactly(
                "PSU", "PSU-1", "PSU-1", "2",
                "", "950", "", "1900",
                "Wortmann"
        );
    }
}
