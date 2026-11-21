package pl.commercelink.web.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryViewFixture;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.ManagedCategoryProducts;
import pl.commercelink.products.ProductDataResolver;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.products.information.PimEntryRequester;
import pl.commercelink.products.information.StorePimAliases;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyRepository;
import pl.commercelink.web.dtos.ProductsBulkAddForm;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The review describes the products it found through a real view, in one read of the product data. */
class ProductsAddReviewProductDataTest {

    private static final String STORE_ID = "store-1";

    @Test
    void reviewedProductsAreDescribedInOneReadHoweverManyThereAre() {
        // given
        TaxonomyCache records = new TaxonomyCache(mock(TaxonomyRepository.class));
        SupplierRegistry supplierRegistry = mock(SupplierRegistry.class, RETURNS_DEEP_STUBS);
        List<String> eans = List.of("5901000000061", "5901000000062", "5901000000063");
        List<MatchedInventory> groups = eans.stream().map(ean -> {
            records.add(new Taxonomy(ean, "M-" + ean, "Brand", "GPU " + ean, "Karty graficzne", 1, null, null, null, "11"));
            return new MatchedInventory(new InventoryKey(ean, "M-" + ean),
                    List.of(new InventoryItem(ean, "M-" + ean, 100.0, "PLN", 1, 1, "AB")), supplierRegistry);
        }).toList();
        PimCatalog pim = ScopedPimCatalog.of();
        ProductDataResolver resolver = spy(new ProductDataResolver(pim, records));
        Inventory inventory = mock(Inventory.class);
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(InventoryViewFixture.over(STORE_ID, resolver,
                supplierRegistry, groups.toArray(MatchedInventory[]::new)));
        ProductRepository productRepository = mock(ProductRepository.class);
        StorePimAliases aliases = mock(StorePimAliases.class);
        ProductsAddReview review = new ProductsAddReview(productRepository, inventory, pim, mock(BrandMapper.class),
                mock(MessageSource.class), aliases, new ManagedCategoryProducts(productRepository,
                mock(CatalogPlacement.class), aliases, mock(PimEntryRequester.class)));
        CategoryDefinition category = new CategoryDefinition().withName("GPU").withGeneratedId();

        // when
        ProductsAddReview.Prepared prepared = review.prepare(STORE_ID, category, eans, null);

        // then
        assertThat(prepared.form().getProducts()).extracting(ProductsBulkAddForm.Row::getName)
                .containsExactly("GPU 5901000000061", "GPU 5901000000062", "GPU 5901000000063");
        verify(resolver, times(1)).ofGroups(eq(STORE_ID), any());
    }
}
