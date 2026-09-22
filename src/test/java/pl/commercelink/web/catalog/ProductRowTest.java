package pl.commercelink.web.catalog;

import org.junit.jupiter.api.Test;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.MarketplaceDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductRecommendation;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductRowTest {

    private static Product product() {
        Product p = new Product("cat-1", "pim-1", "4719331361600", "GV-N5080", "Gigabyte", "RTX 5080",
                "Gigabyte RTX 5080 Gaming OC", "Ultra Premium");
        p.setProductId("p-1");
        return p;
    }

    private static CategoryDefinition category(String... labels) {
        CategoryDefinition category = new CategoryDefinition().withName("GPU").withGeneratedId();
        category.setCategoryId("cat-1");
        category.setGroupingOrder(List.of(labels));
        return category;
    }

    /** A definition the export accepts: named, with a markup and a warehouse criterion, so {@code isComplete}. */
    private static MarketplaceDefinition definition(String name, boolean exportSelectedProducts) {
        MarketplaceDefinition definition = new MarketplaceDefinition(name, 1.2, 0, 0, 0, 0, 1);
        definition.setExportSelectedProducts(exportSelectedProducts);
        return definition;
    }

    private static CategoryDefinition categoryWith(MarketplaceDefinition... definitions) {
        CategoryDefinition category = category("RTX 5080");
        category.setMarketplaceDefinitions(new ArrayList<>(List.of(definitions)));
        return category;
    }

    @Test
    void activeProductWithMarketplacesAndFeatures() {
        // given
        Product p = product();
        p.setMarketplaces(List.of("allegro", "empik"));
        p.setStockExpectedQty(2);
        p.setSuggestedRetailPrice(4999);

        // when
        ProductRow row = ProductRow.of(p, categoryWith(definition("allegro", true)), "c1", name -> name.toUpperCase());

        // then
        assertThat(row.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(row.marketplaceNames()).containsExactly("ALLEGRO", "EMPIK");
        assertThat(row.features()).containsExactlyInAnyOrder("marketplace", "stock", "srp");
        assertThat(row.featuresAttribute()).isEqualTo("marketplace stock srp");
        assertThat(row.labelOutside()).isFalse();
        assertThat(row.searchText()).contains("gigabyte rtx 5080").contains("4719331361600").contains("gv-n5080").contains("pim-1");
        assertThat(row.href()).isEqualTo("/dashboard/catalogs/c1/category/cat-1/products/p-1");
        assertThat(row.lowestGrossPrice()).isNull();
    }

    @Test
    void statusesAndLabelOutside() {
        // given
        Product disabled = product();
        disabled.setEnabled(false);
        Product noPim = product();
        noPim.setPimId(null);
        Product service = product();
        service.setService(true);
        CategoryDefinition gpu = category("RTX 5070");

        // when / then
        assertThat(ProductRow.of(disabled, gpu, "c1", n -> n).status()).isEqualTo(ProductStatus.DISABLED);
        assertThat(ProductRow.of(noPim, gpu, "c1", n -> n).status()).isEqualTo(ProductStatus.NO_PIM);
        assertThat(ProductRow.of(product(), gpu, "c1", n -> n).labelOutside()).isTrue();
        assertThat(ProductRow.of(service, gpu, "c1", n -> n).features()).contains("service");
    }

    /**
     * The mark means "the export would publish it", which is what the old {@code MarketplaceEligible} view listed: a
     * definition that exports the whole category takes every enabled product with a PIM id, a definition that exports
     * a selection takes only the approved ones, and a category with no usable definition exports nothing at all.
     */
    @Test
    void theMarketplaceMarkFollowsWhatTheExportWouldPublish() {
        // given
        Product approved = product();
        approved.setMarketplaces(List.of("allegro"));
        Product notApproved = product();

        // when / then
        assertThat(ProductRow.of(notApproved, categoryWith(definition("allegro", false)), "c1", n -> n).features())
                .contains("marketplace");
        assertThat(ProductRow.of(notApproved, categoryWith(definition("allegro", true)), "c1", n -> n).features())
                .doesNotContain("marketplace");
        assertThat(ProductRow.of(approved, categoryWith(definition("allegro", true)), "c1", n -> n).features())
                .contains("marketplace");
        assertThat(ProductRow.of(approved, category("RTX 5080"), "c1", n -> n).features())
                .doesNotContain("marketplace");
    }

    /** A definition the export skips -- switched off, or without a markup and a quantity rule -- publishes nothing. */
    @Test
    void aDisabledOrIncompleteMarketplaceDefinitionMarksNothing() {
        // given
        MarketplaceDefinition disabled = definition("allegro", false);
        disabled.setEnabled(false);
        MarketplaceDefinition incomplete = new MarketplaceDefinition("empik", 0, 0, 0, 0, 0, 0);

        // when / then
        assertThat(ProductRow.of(product(), categoryWith(disabled, incomplete), "c1", n -> n).features())
                .doesNotContain("marketplace");
    }

    /** The export reads enabled products with a PIM id only, so neither half of that is marked. */
    @Test
    void aDisabledProductOrOneWithoutAPimEntryIsNeverMarked() {
        // given
        Product disabled = product();
        disabled.setEnabled(false);
        disabled.setMarketplaces(List.of("allegro"));
        Product noPim = product();
        noPim.setPimId(null);
        noPim.setMarketplaces(List.of("allegro"));
        CategoryDefinition exporting = categoryWith(definition("allegro", false));

        // when / then
        assertThat(ProductRow.of(disabled, exporting, "c1", n -> n).features()).doesNotContain("marketplace");
        assertThat(ProductRow.of(noPim, exporting, "c1", n -> n).features()).doesNotContain("marketplace");
    }

    /** A category without a label list has no "outside" label: the catalog page counts such products the same way. */
    @Test
    void withoutALabelListNoLabelIsOutsideIt() {
        // given
        CategoryDefinition gpu = category();

        // when / then
        assertThat(ProductRow.of(product(), gpu, "c1", n -> n).labelOutside()).isFalse();
    }

    @Test
    void aRecommendationIsPricedAndHasNoPageOfItsOwn() {
        // given
        ProductRecommendation recommendation = mock(ProductRecommendation.class);
        when(recommendation.hasPimId()).thenReturn(true);
        when(recommendation.getLabel()).thenReturn("RTX 5080");
        when(recommendation.getName()).thenReturn("Gigabyte RTX 5080 Gaming OC");
        when(recommendation.getEan()).thenReturn("4719331361600");
        when(recommendation.getLowestGrossPrice()).thenReturn(2749.0);

        // when
        ProductRow row = ProductRow.ofRecommendation(recommendation, category("RTX 5080"));

        // then
        assertThat(row.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(row.lowestGrossPrice()).isEqualTo("2 749,00");
        assertThat(row.href()).isNull();
        assertThat(row.features()).isEmpty();
    }

    /** An automatic category shows a product whose label fell out of the list as disabled: the price list skips it. */
    @Test
    void aRecommendationWithALabelOutsideTheListIsDisabled() {
        // given
        ProductRecommendation recommendation = mock(ProductRecommendation.class);
        when(recommendation.hasPimId()).thenReturn(true);
        when(recommendation.getLabel()).thenReturn("RTX 5090");

        // when
        ProductRow row = ProductRow.ofRecommendation(recommendation, category("RTX 5080"));

        // then
        assertThat(row.status()).isEqualTo(ProductStatus.DISABLED);
        assertThat(row.labelOutside()).isTrue();
    }
}
