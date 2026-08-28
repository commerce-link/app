package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierOrderChoicesParamsTest {

    @Test
    void extractsSupplierOptionKeysAndIgnoresUnrelatedParams() {
        // given
        Map<String, String> params = Map.of(
                "supplierOrderChoices[deliveryMethod]", "DPD Kurier",
                "purchaseRef", "x",
                "supplierOrderChoices[]", "y");

        // when
        Map<String, String> options = SupplierOrderChoicesParams.fromRequest(params);

        // then
        assertThat(options).containsExactly(Map.entry("deliveryMethod", "DPD Kurier"));
    }

    @Test
    void dropsBlankValues() {
        // given
        Map<String, String> params = Map.of(
                "supplierOrderChoices[lane]", "",
                "supplierOrderChoices[warehouse]", "   ");

        // when
        Map<String, String> options = SupplierOrderChoicesParams.fromRequest(params);

        // then
        assertThat(options).isEmpty();
    }

    @Test
    void trimsSurroundingWhitespaceFromValues() {
        // given
        Map<String, String> params = Map.of("supplierOrderChoices[lane]", "  fast  ");

        // when
        Map<String, String> options = SupplierOrderChoicesParams.fromRequest(params);

        // then
        assertThat(options).containsExactly(Map.entry("lane", "fast"));
    }

    @Test
    void returnsAnEmptyMapWhenNothingMatches() {
        // given
        Map<String, String> params = Map.of("purchaseRef", "x", "deliveryAddressId", "17200617");

        // when
        Map<String, String> options = SupplierOrderChoicesParams.fromRequest(params);

        // then
        assertThat(options).isEmpty();
    }
}
