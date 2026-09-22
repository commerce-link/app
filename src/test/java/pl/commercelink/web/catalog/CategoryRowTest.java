package pl.commercelink.web.catalog;

import org.junit.jupiter.api.Test;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.CategoryDefinitions;
import pl.commercelink.products.MarketplaceDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryRowTest {

    private static Product product(String label) {
        return new Product("cat", "pim", "590", "MFN", "Brand", label, "Name", "Default");
    }

    @Test
    void countsProductsAndLabelsOutsideTheListForAManualCategory() {
        // given
        ProductCatalog catalog = new ProductCatalog("store", "Parts");
        CategoryDefinition gpu = new CategoryDefinition().withName("GPU").withGeneratedId();
        gpu.setGroupingOrder(List.of("RTX 5060", "RTX 5070"));
        gpu.setPimCategoryIds(List.of("pim-1"));
        gpu.getMarketplaceDefinitions().add(new MarketplaceDefinition("allegro", 1.1, 0, 1, 2, 0, 3));
        gpu.getMarketplaceDefinitions().add(new MarketplaceDefinition("empik", 0, 0, 0, 0, 0, 0)); // incomplete: not listed

        // when
        CategoryRow row = CategoryRow.of(catalog, gpu, List.of("Graphics cards"),
                List.of(product("RTX 5060"), product("RTX 5070"), product("RTX 4060")), name -> name.toUpperCase(),
                new CategoryDefinitions.DeletionPreview(false, 3));

        // then
        assertThat(row.dynamic()).isFalse();
        assertThat(row.productsCount()).isEqualTo(3);
        assertThat(row.labelsCount()).isEqualTo(2);
        assertThat(row.productsOutsideLabels()).isEqualTo(1);
        assertThat(row.productsKept()).isFalse();
        assertThat(row.productsToDelete()).isEqualTo(3);
        assertThat(row.marketplaceNames()).containsExactly("ALLEGRO");
        assertThat(row.deletable()).isFalse();
        assertThat(row.href()).isEqualTo("/dashboard/catalogs/" + catalog.getCatalogId() + "/category/" + gpu.getCategoryId());
    }

    @Test
    void dynamicCategoryHasNoProductCount() {
        // given
        CategoryDefinition os = new CategoryDefinition().withName("OS").withGeneratedId();
        os.setType(CategoryDefinitionType.Dynamic);
        os.setDeletionProtection(false);

        // when
        CategoryRow row = CategoryRow.of(new ProductCatalog("store", "Parts"), os, List.of(), List.of(), n -> n,
                new CategoryDefinitions.DeletionPreview(true, 0));

        // then
        assertThat(row.dynamic()).isTrue();
        assertThat(row.productsCount()).isNull();
        assertThat(row.deletable()).isTrue();
        assertThat(row.pimCategoryNames()).isEmpty();
        assertThat(row.productsKept()).isTrue();
    }
}
