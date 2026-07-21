package pl.commercelink.pricelist;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityAndPriceCsvTest {

    @Test
    void serviceFlagRoundTripsThroughCsv() {
        // given
        AvailabilityAndPrice row = new AvailabilityAndPrice("pim-1", "EAN", "MFN", "Brand",
                "Label", "Montaż PC", "Usługi dodatkowe", 100, 1, 1, 100, true);

        // when
        String[] csv = row.asStringArray();

        // then — service is the last column
        assertThat(csv[csv.length - 1]).isEqualTo("true");
        assertThat(AvailabilityAndPrice.HEADERS[AvailabilityAndPrice.HEADERS.length - 1]).isEqualTo("Service");
    }
}
