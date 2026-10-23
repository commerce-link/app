package pl.commercelink.web.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.web.dtos.ProductsBulkAddForm;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductsAddReviewScopeTest {

    private static final String EAN = ScopedPimCatalog.STORE_GTIN;
    private static final String MFN = "M-591";

    @Mock private ProductRepository productRepository;
    @Mock private Inventory inventory;
    @Mock private InventoryView view;
    @Mock private BrandMapper brandMapper;
    @Mock private MessageSource messageSource;
    @Mock private CatalogPlacement catalogPlacement;

    private ProductsAddReview review(PimCatalog catalog) {
        when(inventory.withEnabledSuppliersOnly(anyString())).thenReturn(view);
        when(productRepository.findAll(anyString())).thenReturn(List.of());
        MatchedInventory matched = mock(MatchedInventory.class);
        when(matched.isEmpty()).thenReturn(false);
        when(matched.getInventoryKey()).thenReturn(new InventoryKey(EAN, MFN));
        when(matched.getTaxonomy()).thenReturn(new Taxonomy(EAN, MFN, "Brand", "Taxonomy name", "GPU", 1, null, null));
        when(matched.getLowestPrice()).thenReturn(Price.fromGross(1000));
        when(view.findByEan(EAN)).thenReturn(matched);
        return new ProductsAddReview(productRepository, inventory, catalog, brandMapper, messageSource, catalogPlacement);
    }

    private static PimCatalog catalog() {
        return ScopedPimCatalog.of(
                ScopedPimCatalog.globalEntry("g1", ScopedPimCatalog.GLOBAL_GTIN, "M-590", "GPU"),
                ScopedPimCatalog.storeEntry("s1-2", "s1", EAN, MFN, "GPU"));
    }

    @Test
    void storeEntryResolvesForItsStoreOnly() {
        // given
        ProductsAddReview review = review(catalog());
        CategoryDefinition category = new CategoryDefinition().withName("GPU").withGeneratedId();

        // when
        ProductsAddReview.Prepared owner = review.prepare("s1", category, List.of(EAN), null);
        ProductsAddReview.Prepared other = review.prepare("s2", category, List.of(EAN), null);

        // then
        assertThat(owner.form().getProducts()).singleElement().extracting(ProductsBulkAddForm.Row::getName).isEqualTo("Store s1-2");
        assertThat(other.form().getProducts()).singleElement().extracting(ProductsBulkAddForm.Row::getName).isEqualTo("Taxonomy name");
    }

    @Test
    void pimEntryOfResolvesInTheStoresView() {
        // given
        ProductsAddReview review = review(catalog());
        Product product = new Product();
        product.setEan(EAN);
        product.setManufacturerCode(MFN);
        MatchedInventory unknown = mock(MatchedInventory.class);
        when(unknown.isEmpty()).thenReturn(true);
        when(view.findByInventoryKey(org.mockito.ArgumentMatchers.any())).thenReturn(unknown);

        // when
        Optional<?> owner = review.pimEntryOf("s1", view, InventoryKey.fromProduct(product), product);
        Optional<?> other = review.pimEntryOf("s2", view, InventoryKey.fromProduct(product), product);

        // then
        assertThat(owner).isPresent();
        assertThat(other).isEmpty();
    }

    @Test
    void twoRowsLeadingToOneStoreEntryAddOneProduct() {
        // given: the second row carries another EAN but the store entry's MPN (bad feed data)
        ProductsAddReview review = review(catalog());
        CategoryDefinition category = new CategoryDefinition().withName("GPU").withGeneratedId();
        MatchedInventory unknown = mock(MatchedInventory.class);
        when(unknown.isEmpty()).thenReturn(true);
        when(view.findByInventoryKey(org.mockito.ArgumentMatchers.any())).thenReturn(unknown);
        ProductsBulkAddForm form = ProductsBulkAddForm.of(List.of(
                new Product(null, null, EAN, "OTHER-1", "Brand", "L", "First", "Default"),
                new Product(null, null, "5900000000999", MFN, "Brand", "L", "Second", "Default")));

        // when
        int added = review.save("s1", category, form);

        // then
        assertThat(added).isEqualTo(1);
        org.mockito.Mockito.verify(productRepository, org.mockito.Mockito.times(1)).save(org.mockito.ArgumentMatchers.argThat(
                p -> "s1-2".equals(p.getPimId())));
    }
}
