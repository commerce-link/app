package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountFormFieldsTest {

    @Test
    void errorsOfATargetAreNamedAfterItsField() {
        // given
        Map<String, String> unknown = new LinkedHashMap<>();
        Map<String, String> nothingPicked = new LinkedHashMap<>();

        // when
        DiscountFormFields.validateTarget(unknown, "conditionTarget", "Catalog", List.of());
        DiscountFormFields.validateTarget(nothingPicked, "conditionTarget", "Brand", List.of());

        // then
        assertThat(unknown).containsExactly(Map.entry("conditionTarget", "store.discounts.target.invalid"));
        assertThat(nothingPicked)
                .containsExactly(Map.entry("conditionTargetValues", "store.discounts.targetValues.required.Brand"));
    }
}
