package pl.commercelink.web.catalog;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import pl.commercelink.products.InventoryDefinition;
import pl.commercelink.products.filters.InventoryFilterType;
import pl.commercelink.starter.dynamodb.Metadata;
import pl.commercelink.web.dtos.RecommendationFiltersForm;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryFilterLabelsTest {

    @Test
    void everyFilterTypeHasAKind() {
        // when / then
        for (InventoryFilterType type : InventoryFilterType.values()) {
            assertThat(InventoryFilterLabels.kindOf(type)).as(type.name()).isNotNull();
        }
        assertThat(InventoryFilterLabels.kindOf(InventoryFilterType.PRICE_RANGE)).isEqualTo(InventoryFilterLabels.Kind.PRICE_RANGE);
        assertThat(InventoryFilterLabels.kindOf(InventoryFilterType.PRODUCT_LINE_BY_BRAND)).isEqualTo(InventoryFilterLabels.Kind.BY_BRAND);
        assertThat(InventoryFilterLabels.kindOf(InventoryFilterType.EAN_NOT_EQ)).isEqualTo(InventoryFilterLabels.Kind.LIST);
    }

    @Test
    void listFiltersRoundTripThroughTheirMetadataKey() {
        // given
        InventoryDefinition brands = new InventoryDefinition(InventoryFilterType.BRAND_NAME, List.of(new Metadata("Brands", "MSI, ASUS")));

        // when
        RecommendationFiltersForm.FilterForm form = InventoryFilterLabels.fromDefinition(brands);
        List<Metadata> back = InventoryFilterLabels.toMetadata(form);

        // then
        assertThat(form.getType()).isEqualTo("BRAND_NAME");
        assertThat(form.getValues()).isEqualTo("MSI, ASUS");
        assertThat(back).extracting(Metadata::getKey, Metadata::getValue).containsExactly(Tuple.tuple("Brands", "MSI, ASUS"));
    }

    /** Both price keys are always written: the filter only runs when its metadata has each of them. */
    @Test
    void priceRangeUsesMinAndMaxPriceAndWritesBothBounds() {
        // given
        InventoryDefinition range = new InventoryDefinition(InventoryFilterType.PRICE_RANGE,
                List.of(new Metadata("MinPrice", "900"), new Metadata("MaxPrice", "5000")));

        // when
        RecommendationFiltersForm.FilterForm form = InventoryFilterLabels.fromDefinition(range);
        form.setMaxPrice("");
        List<Metadata> back = InventoryFilterLabels.toMetadata(form);

        // then
        assertThat(form.getMinPrice()).isEqualTo("900");
        assertThat(back).extracting(Metadata::getKey, Metadata::getValue)
                .containsExactly(Tuple.tuple("MinPrice", "900"), Tuple.tuple("MaxPrice", "2147483647"));
    }

    /**
     * The filter parses its bounds with Integer.parseInt, so a typed group separator ("5 000", the format the panel
     * itself prints) has to be gone by the time it is saved — otherwise the filter throws and drops every product.
     */
    @Test
    void priceBoundsAreStoredAsTheParsedNumber() {
        // given
        RecommendationFiltersForm.FilterForm form = new RecommendationFiltersForm.FilterForm();
        form.setType("PRICE_RANGE");
        form.setMinPrice("5 000");
        form.setMaxPrice("12 500");

        // when
        List<Metadata> metadata = InventoryFilterLabels.toMetadata(form);

        // then
        assertThat(metadata).extracting(Metadata::getKey, Metadata::getValue)
                .containsExactly(Tuple.tuple("MinPrice", "5000"), Tuple.tuple("MaxPrice", "12500"));
        assertThat(metadata).allSatisfy(entry -> assertThat(Integer.parseInt(entry.getValue())).isPositive());
    }

    @Test
    void anEmptyBoundIsStoredAsTheValueTheFilterReadsAsNoBound() {
        // given
        RecommendationFiltersForm.FilterForm form = new RecommendationFiltersForm.FilterForm();
        form.setType("PRICE_RANGE");
        form.setMinPrice("");
        form.setMaxPrice(null);

        // when / then
        assertThat(InventoryFilterLabels.toMetadata(form)).extracting(Metadata::getKey, Metadata::getValue)
                .containsExactly(Tuple.tuple("MinPrice", "0"), Tuple.tuple("MaxPrice", "2147483647"));
    }

    /** The bounds the filter reads as "no bound" are shown as empty fields, not as 0 and 2147483647. */
    @Test
    void openPriceBoundsAreShownAsEmptyFields() {
        // given
        InventoryDefinition range = new InventoryDefinition(InventoryFilterType.PRICE_RANGE,
                List.of(new Metadata("MinPrice", "0"), new Metadata("MaxPrice", "2147483647")));

        // when
        RecommendationFiltersForm.FilterForm form = InventoryFilterLabels.fromDefinition(range);

        // then
        assertThat(form.getMinPrice()).isEmpty();
        assertThat(form.getMaxPrice()).isEmpty();
    }

    @Test
    void byBrandFiltersBecomeOneLinePerBrand() {
        // given
        InventoryDefinition lines = new InventoryDefinition(InventoryFilterType.PRODUCT_LINE_BY_BRAND,
                List.of(new Metadata("Gigabyte", "Windforce, Gaming OC"), new Metadata("MSI", "Ventus")));

        // when
        RecommendationFiltersForm.FilterForm form = InventoryFilterLabels.fromDefinition(lines);
        List<Metadata> back = InventoryFilterLabels.toMetadata(form);

        // then
        assertThat(form.getBrandLines()).isEqualTo("Gigabyte: Windforce, Gaming OC\nMSI: Ventus");
        assertThat(back).extracting(Metadata::getKey, Metadata::getValue)
                .containsExactly(Tuple.tuple("Gigabyte", "Windforce, Gaming OC"), Tuple.tuple("MSI", "Ventus"));
    }

    @Test
    void brandLinesWithoutAColonReportTheLineNumber() {
        // when / then
        assertThatThrownBy(() -> InventoryFilterLabels.parseBrandLines("Gigabyte: Windforce\nMSI Ventus"))
                .isInstanceOf(InventoryFilterLabels.BrandLineException.class)
                .extracting(e -> ((InventoryFilterLabels.BrandLineException) e).lineNumber()).isEqualTo(2);
        assertThat(InventoryFilterLabels.parseBrandLines(" ASUS : Dual, TUF \n\n")).containsExactly(Map.entry("ASUS", "Dual, TUF"));
    }

    @Test
    void unknownMetadataIsKeptAsideNotDropped() {
        // given
        InventoryDefinition odd = new InventoryDefinition(InventoryFilterType.BRAND_NAME,
                List.of(new Metadata("Brands", "MSI"), new Metadata("Legacy", "x")));

        // when
        RecommendationFiltersForm.FilterForm form = InventoryFilterLabels.fromDefinition(odd);

        // then
        assertThat(form.getUnknownKeys()).containsExactly("Legacy");
        assertThat(InventoryFilterLabels.toMetadata(form)).extracting(Metadata::getKey).containsExactly("Brands", "Legacy");
    }

    /** The hidden key and value are one pair per row of the page; a post carrying only half of one is not a server error. */
    @Test
    void anUnknownKeyWithoutItsValueIsDropped() {
        // given
        RecommendationFiltersForm.FilterForm form = new RecommendationFiltersForm.FilterForm();
        form.setType("BRAND_NAME");
        form.setValues("MSI");
        form.getUnknownKeys().add("Legacy");

        // when / then
        assertThat(InventoryFilterLabels.toMetadata(form)).extracting(Metadata::getKey).containsExactly("Brands");
    }

    @Test
    void theTypeOptionsCarryTheKindAndTheMessageKeysOfEveryType() {
        // when
        List<Map<String, String>> options = InventoryFilterLabels.options();

        // then
        assertThat(options).hasSize(InventoryFilterType.values().length);
        assertThat(options).extracting(option -> option.get("value"))
                .containsExactly("BRAND_NAME", "PRICE_RANGE", "PRODUCT_TITLE_CONTAINS", "PRODUCT_TITLE_DOES_NOT_CONTAIN",
                        "EAN_NOT_EQ", "PRODUCT_LINE_BY_BRAND", "PRODUCT_LINE_BY_BRAND_NOT_CONTAIN", "PRODUCT_EAN_BY_BRAND_NOT_EQ");
        assertThat(options.get(0)).containsEntry("value", "BRAND_NAME").containsEntry("kind", "LIST")
                .containsEntry("labelKey", "catalog.filter.type.BRAND_NAME")
                .containsEntry("fieldKey", "catalog.filter.type.BRAND_NAME.field")
                .containsEntry("helpKey", "catalog.filter.type.BRAND_NAME.help");
    }

    @Test
    void theSharedFieldOfAKindFallsBackToTheFirstTypeOfThatKind() {
        // when / then
        assertThat(InventoryFilterLabels.shownType("LIST", "EAN_NOT_EQ")).isEqualTo("EAN_NOT_EQ");
        assertThat(InventoryFilterLabels.shownType("LIST", "PRICE_RANGE")).isEqualTo("BRAND_NAME");
        assertThat(InventoryFilterLabels.shownType("BY_BRAND", null)).isEqualTo("PRODUCT_LINE_BY_BRAND");
    }

    @Test
    void theVariantsOfAKindAreTheValuesTheChoiceCanTake() {
        // when / then
        assertThat(InventoryFilterLabels.variants(InventoryFilterLabels.Kind.PRICE_RANGE)).isEqualTo("PRICE_RANGE");
        assertThat(InventoryFilterLabels.variants(InventoryFilterLabels.Kind.BY_BRAND))
                .isEqualTo("PRODUCT_LINE_BY_BRAND|PRODUCT_LINE_BY_BRAND_NOT_CONTAIN|PRODUCT_EAN_BY_BRAND_NOT_EQ");
        assertThat(InventoryFilterLabels.variants(InventoryFilterLabels.Kind.LIST))
                .isEqualTo("BRAND_NAME|PRODUCT_TITLE_CONTAINS|PRODUCT_TITLE_DOES_NOT_CONTAIN|EAN_NOT_EQ");
    }
}
