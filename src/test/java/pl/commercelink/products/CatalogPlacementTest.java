package pl.commercelink.products;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.InventoryKey;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogPlacementTest {

    private static final String STORE_ID = "store-1";

    private final ProductCatalogRepository catalogs = mock(ProductCatalogRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final CatalogPlacement placement = new CatalogPlacement(catalogs, products);

    @BeforeEach
    void setUp() {
        CategoryDefinition gpus = category("cat-gpu", "Karta graficzna", CategoryDefinitionType.Managed, "11");
        CategoryDefinition auto = category("cat-auto", "Promocje", CategoryDefinitionType.Dynamic, "11");
        CategoryDefinition b2b = category("cat-b2b", "Karty graficzne", CategoryDefinitionType.Managed, "11", "12");
        ProductCatalog parts = catalog("c-1", "Podzespoły komputerowe", gpus, auto);
        ProductCatalog shop = catalog("c-2", "Sklep B2B", b2b);
        when(catalogs.findAll(STORE_ID)).thenReturn(List.of(shop, parts));
        Product existing = mock(Product.class);
        when(existing.getEan()).thenReturn("5901000000001");
        when(existing.getManufacturerCode()).thenReturn("GPU-1");
        when(existing.getProductId()).thenReturn("p-1");
        when(products.findAll("cat-gpu")).thenReturn(List.of(existing));
        when(products.findAll("cat-b2b")).thenReturn(List.of());
    }

    @Test
    void targetsAreManagedCategoriesOnlyOrderedByCatalogName() {
        // when
        List<CatalogPlacement.Target> targets = placement.forStore(STORE_ID).targets();

        // then
        assertThat(targets).extracting(CatalogPlacement.Target::label)
                .containsExactly("Podzespoły komputerowe › Karta graficzna", "Sklep B2B › Karty graficzne");
    }

    @Test
    void existingFindsAProductByEanOrByManufacturerCode() {
        // given
        CatalogPlacement.StorePlacement store = placement.forStore(STORE_ID);

        // when / then
        assertThat(store.existing(InventoryKey.fromEan("5901000000001"))).extracting(CatalogPlacement.Existing::productId)
                .containsExactly("p-1");
        assertThat(store.existing(InventoryKey.fromMfn("GPU-1"))).hasSize(1);
        assertThat(store.isIn("cat-gpu", InventoryKey.fromEan("5901000000001"))).isTrue();
        assertThat(store.isIn("cat-b2b", InventoryKey.fromEan("5901000000001"))).isFalse();
        assertThat(store.existing(InventoryKey.fromEan("5909999999999"))).isEmpty();
    }

    @Test
    void targetIsFoundByItsFormValue() {
        // when / then
        assertThat(placement.forStore(STORE_ID).target("c-2/cat-b2b")).map(CatalogPlacement.Target::categoryName)
                .contains("Karty graficzne");
        assertThat(placement.forStore(STORE_ID).target("c-1/cat-auto")).isEmpty();
    }

    @Test
    void placementIsCachedUntilEvicted() {
        // when
        placement.forStore(STORE_ID);
        placement.forStore(STORE_ID);
        placement.evict(STORE_ID);
        placement.forStore(STORE_ID);

        // then
        verify(catalogs, times(2)).findAll(STORE_ID);
    }

    private static CategoryDefinition category(String id, String name, CategoryDefinitionType type,
                                               String... pimIds) {
        CategoryDefinition category = mock(CategoryDefinition.class);
        when(category.getCategoryId()).thenReturn(id);
        when(category.getName()).thenReturn(name);
        when(category.getType()).thenReturn(type);
        when(category.hasType(type)).thenReturn(true);
        when(category.getPimCategoryIds()).thenReturn(List.of(pimIds));
        return category;
    }

    private static ProductCatalog catalog(String id, String name, CategoryDefinition... categories) {
        ProductCatalog catalog = mock(ProductCatalog.class);
        when(catalog.getCatalogId()).thenReturn(id);
        when(catalog.getName()).thenReturn(name);
        when(catalog.getCategories()).thenReturn(List.of(categories));
        return catalog;
    }
}
