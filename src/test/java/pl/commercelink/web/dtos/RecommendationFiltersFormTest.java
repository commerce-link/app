package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.InventoryDefinition;
import pl.commercelink.products.filters.InventoryFilterType;
import pl.commercelink.starter.dynamodb.Metadata;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationFiltersFormTest {

    private static RecommendationFiltersForm.FilterForm filter(String type) {
        RecommendationFiltersForm.FilterForm form = new RecommendationFiltersForm.FilterForm();
        form.setType(type);
        return form;
    }

    @Test
    void emptyListIsValidAndMeansNoFilters() {
        // given
        RecommendationFiltersForm form = new RecommendationFiltersForm();

        // when / then
        assertThat(form.validate()).isEmpty();
        assertThat(form.toDefinitions()).isEmpty();
    }

    @Test
    void listFilterNeedsValues() {
        // given
        RecommendationFiltersForm form = new RecommendationFiltersForm();
        form.getFilters().add(filter("PRODUCT_TITLE_CONTAINS"));

        // when / then
        assertThat(form.validate()).containsEntry("filter-0-values", "catalog.filter.values.required");
    }

    @Test
    void priceRangeNeedsAtLeastOneBoundAndMaxAboveMin() {
        // given
        RecommendationFiltersForm form = new RecommendationFiltersForm();
        RecommendationFiltersForm.FilterForm empty = filter("PRICE_RANGE");
        RecommendationFiltersForm.FilterForm inverted = filter("PRICE_RANGE");
        inverted.setMinPrice("900");
        inverted.setMaxPrice("100");
        form.getFilters().addAll(List.of(empty, inverted));

        // when
        Map<String, String> errors = form.validate();

        // then
        assertThat(errors).containsEntry("filter-0-minPrice", "catalog.filter.price.required")
                .containsEntry("filter-1-maxPrice", "catalog.filter.price.order");
    }

    @Test
    void aPriceThatIsNotAWholeAmountIsNamedAtItsOwnField() {
        // given
        RecommendationFiltersForm form = new RecommendationFiltersForm();
        RecommendationFiltersForm.FilterForm price = filter("PRICE_RANGE");
        price.setMinPrice("tanio");
        form.getFilters().add(price);

        // when / then
        assertThat(form.validate()).containsExactly(Map.entry("filter-0-minPrice", "catalog.filter.price.invalid"));
    }

    @Test
    void brandLinesErrorNamesTheLine() {
        // given
        RecommendationFiltersForm form = new RecommendationFiltersForm();
        RecommendationFiltersForm.FilterForm byBrand = filter("PRODUCT_LINE_BY_BRAND_NOT_CONTAIN");
        byBrand.setBrandLines("Gigabyte: Eagle\nbroken line");
        form.getFilters().add(byBrand);

        // when / then
        assertThat(form.validate()).containsEntry("filter-0-brandLines", "catalog.filter.brandLines.line:2");
    }

    @Test
    void byBrandFilterNeedsAtLeastOneBrand() {
        // given
        RecommendationFiltersForm form = new RecommendationFiltersForm();
        form.getFilters().add(filter("PRODUCT_LINE_BY_BRAND"));

        // when / then
        assertThat(form.validate()).containsEntry("filter-0-brandLines", "catalog.filter.brandLines.required");
    }

    @Test
    void unknownTypeIsAnError() {
        // given
        RecommendationFiltersForm form = new RecommendationFiltersForm();
        form.getFilters().add(filter("NOPE"));

        // when / then
        assertThat(form.validate()).containsEntry("filter-0-type", "catalog.filter.type.invalid");
    }

    @Test
    void fromAndToDefinitionsRoundTrip() {
        // given
        CategoryDefinition category = new CategoryDefinition().withName("GPU").withGeneratedId();
        category.getInventoryDefinitions().add(new InventoryDefinition(InventoryFilterType.BRAND_NAME, List.of(new Metadata("Brands", "MSI"))));
        category.getInventoryDefinitions().add(new InventoryDefinition(InventoryFilterType.PRICE_RANGE, List.of(new Metadata("MinPrice", "900"))));

        // when
        RecommendationFiltersForm form = RecommendationFiltersForm.from(category);
        List<InventoryDefinition> back = form.toDefinitions();

        // then
        assertThat(form.getFilters()).hasSize(2);
        assertThat(back).hasSize(2);
        assertThat(back.get(0).getType()).isEqualTo(InventoryFilterType.BRAND_NAME);
        assertThat(back.get(1).getMetadata()).extracting(Metadata::getKey).containsExactly("MinPrice", "MaxPrice");
        assertThat(back).allMatch(InventoryDefinition::isComplete);
    }

    /** The id of a field is also the key of its error, so the error summary can link to the field. */
    @Test
    void theFieldIdCarriesTheIndexAndTheFieldName() {
        // when / then
        assertThat(RecommendationFiltersForm.fieldId(2, "brandLines")).isEqualTo("filter-2-brandLines");
    }
}
