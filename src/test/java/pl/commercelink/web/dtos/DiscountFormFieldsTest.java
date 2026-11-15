package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.discounts.DiscountScope;
import pl.commercelink.discounts.TargetValue;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class DiscountFormFieldsTest {

    @Test
    void scopeKeepsEveryListTrimmedWithoutBlanksAndRepeats() {
        // given
        List<String> categories = List.of("Monitory", " Monitory ", "", "Laptopy");
        List<String> brands = Arrays.asList(" ASUS", null, "ASUS");
        List<TargetValue> products = Arrays.asList(new TargetValue(" PIM-1 ", " Monitor 27 "), null,
                new TargetValue("PIM-1", "Monitor 27 (kopia)"), new TargetValue("PIM-2", " "));

        // when
        DiscountScope scope = DiscountFormFields.scope(categories, brands, products);

        // then
        assertThat(scope.getCategories()).containsExactly("Monitory", "Laptopy");
        assertThat(scope.getBrands()).containsExactly("ASUS");
        assertThat(scope.getProducts()).extracting(TargetValue::getId, TargetValue::getName)
                .containsExactly(tuple("PIM-1", "Monitor 27"), tuple("PIM-2", "PIM-2"));
    }

    @Test
    void scopeOfMissingListsIsEmpty() {
        // when / then
        assertThat(DiscountFormFields.scope(null, null, null).isEmpty()).isTrue();
    }
}
