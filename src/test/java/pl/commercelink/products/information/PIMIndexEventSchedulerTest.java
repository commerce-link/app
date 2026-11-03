package pl.commercelink.products.information;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.Brand;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimEntryRequest;
import pl.commercelink.pim.api.PimView;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRecommendationEngine;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PIMIndexEventSchedulerTest {

    @Mock private PimCatalog pimCatalog;
    @Mock
    private PimView pimView;
    @Mock private ProductRepository productRepository;
    @Mock private ProductCatalogRepository productCatalogRepository;
    @Mock private StoresRepository storesRepository;
    @Mock private Inventory inventory;
    @Mock private ProductRecommendationEngine recommendationEngine;
    @Mock private StoreActivity storeActivity;

    @InjectMocks
    private PIMIndexEventScheduler scheduler;

    @org.junit.jupiter.api.BeforeEach
    void pimStoreViewIsTheCatalog() {
        org.mockito.Mockito.lenient().when(pimCatalog.withStoreData(org.mockito.ArgumentMatchers.any())).thenReturn(pimView);
        org.mockito.Mockito.lenient().when(pimCatalog.withGlobalData()).thenReturn(pimView);
    }


    private static Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        return store;
    }

    @Test
    void scansProductsOfActiveStoresOnly() {
        // given
        Store active = store("active");
        Store inactive = store("inactive");
        when(storesRepository.findAll()).thenReturn(List.of(active, inactive));
        when(storeActivity.isActive(active)).thenReturn(true);
        when(storeActivity.isActive(inactive)).thenReturn(false);
        when(productCatalogRepository.findAll("active")).thenReturn(List.of());

        // when
        scheduler.scanAndStoreProductsInPimQueue("tick");

        // then
        verify(productCatalogRepository).findAll("active");
        verify(productCatalogRepository, never()).findAll("inactive");
    }

    @Test
    void productWhoseCodeBelongsToAnotherBrandsEntryIsRequested() {
        // given
        PimCatalog index = ScopedPimCatalog.of(entry("Logitech", GLOBAL_EAN, "A1"));
        Product gembird = product(OTHER_EAN, "A1", "Gembird");

        // when
        List<PimEntryRequest> requests = scan(index, new BrandMapper(index), gembird);

        // then
        assertThat(requests).extracting(PimEntryRequest::mfn, PimEntryRequest::brand)
                .containsExactly(tuple("A1", "Gembird"));
    }

    @Test
    void productUnderAnAliasOfTheEntrysBrandIsKnownByCode() {
        // given
        PimCatalog index = ScopedPimCatalog.of(entry("Western Digital", GLOBAL_EAN, "A1"));
        PimCatalog brands = org.mockito.Mockito.mock(PimCatalog.class);
        when(brands.allBrands()).thenReturn(List.of(new Brand("Western Digital", List.of("sandisk"))));
        Product sandisk = product(OTHER_EAN, "A1", "SanDisk");

        // when
        List<PimEntryRequest> requests = scan(index, new BrandMapper(brands), sandisk);

        // then
        assertThat(requests).isEmpty();
    }

    @Test
    void productWithoutBrandIsKnownByEanOnly() {
        // given
        PimCatalog index = ScopedPimCatalog.of(entry("Logitech", GLOBAL_EAN, "A1"));
        Product byCode = product(OTHER_EAN, "A1", null);
        Product byEan = product(GLOBAL_EAN, "B2", null);

        // when
        List<PimEntryRequest> requests = scan(index, new BrandMapper(index), byCode, byEan);

        // then
        assertThat(requests).extracting(PimEntryRequest::ean).containsExactly(OTHER_EAN);
    }

    private static final String GLOBAL_EAN = "5900000000590";
    private static final String OTHER_EAN = "5900000000699";

    @Mock private InventoryView inventoryView;

    private List<PimEntryRequest> scan(PimCatalog index, BrandMapper brandMapper, Product... products) {
        Store store = store("s1");
        when(storesRepository.findAll()).thenReturn(List.of(store));
        when(storeActivity.isActive(store)).thenReturn(true);
        when(productCatalogRepository.findAll("s1")).thenReturn(List.of(catalogWithCategory("c1")));
        when(productRepository.findAll("c1")).thenReturn(List.of(products));
        org.mockito.Mockito.lenient().when(inventory.withEnabledSuppliersOnly("s1")).thenReturn(inventoryView);
        PIMIndexEventScheduler scanner = new PIMIndexEventScheduler(index, productRepository, productCatalogRepository,
                storesRepository, inventory, recommendationEngine, storeActivity, brandMapper);

        scanner.scanAndStoreProductsInPimQueue("tick");

        ArgumentCaptor<PimEntryRequest> submitted = ArgumentCaptor.forClass(PimEntryRequest.class);
        verify(index, org.mockito.Mockito.atLeast(0)).submit(submitted.capture());
        return submitted.getAllValues();
    }

    private static PimEntry entry(String brand, String gtin, String mpn) {
        return new PimEntry("G", ScopedPimCatalog.identifiers(gtin, mpn), brand, "Global G", "cat", null, true, 1000, 1200);
    }

    private static Product product(String ean, String mfn, String brand) {
        Product product = new Product();
        product.setEan(ean);
        product.setManufacturerCode(mfn);
        product.setBrand(brand);
        return product;
    }

    private static ProductCatalog catalogWithCategory(String categoryId) {
        ProductCatalog catalog = new ProductCatalog();
        CategoryDefinition category = new CategoryDefinition();
        category.setCategoryId(categoryId);
        catalog.getCategories().add(category);
        return catalog;
    }
}
