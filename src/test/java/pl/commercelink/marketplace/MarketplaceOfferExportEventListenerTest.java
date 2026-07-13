package pl.commercelink.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.starter.localization.EnumLocalizer;
import pl.commercelink.taxonomy.CategoryLocalizer;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.marketplace.api.MarketplaceOffer;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.Pricelist;
import pl.commercelink.pricelist.PricelistRepository;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.MarketplaceDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.ProductGroup;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceOfferExportEventListenerTest {

    private static final String STORE_ID = "store-1";
    private static final String CATALOG_ID = "catalog-1";
    private static final String PRICELIST_ID = "pricelist-1";
    private static final String MARKETPLACE = "Morele";
    private static final String CATEGORY_ID = "category-1";

    @Mock private StoresRepository storesRepository;
    @Mock private ProductCatalogRepository productCatalogRepository;
    @Mock private ProductRepository productRepository;
    @Mock private PricelistRepository pricelistRepository;
    @Mock private Inventory inventory;
    @Mock private MarketplaceProviderFactory providerFactory;
    @Mock private MarketplaceOfferExportRepository marketplaceOfferExportRepository;
    @Mock private EnumLocalizer enumLocalizer;
    @Mock private CategoryLocalizer categoryLocalizer;

    @Mock private Store store;
    @Mock private ProductCatalog catalog;
    @Mock private Pricelist pricelist;
    @Mock private InventoryView inventoryView;
    @Mock private MarketplaceProvider provider;

    @InjectMocks
    private MarketplaceOfferExportEventListener listener;

    @BeforeEach
    void setUpDefaults() {
        ReflectionTestUtils.setField(listener, "removalRetryCount", 3);

        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(store.hasActiveMarketplaceIntegration(MARKETPLACE)).thenReturn(true);
        when(store.getStoreId()).thenReturn(STORE_ID);

        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(catalog.isMarketplaceExportEnabled(MARKETPLACE)).thenReturn(true);
        when(catalog.getCatalogId()).thenReturn(CATALOG_ID);

        when(pricelistRepository.find(STORE_ID, CATALOG_ID, PRICELIST_ID)).thenReturn(pricelist);
        when(inventory.withEnabledSuppliersAndWarehouseData(STORE_ID)).thenReturn(inventoryView);

        when(providerFactory.get(store, MARKETPLACE)).thenReturn(provider);

        when(marketplaceOfferExportRepository.loadPreviousExport(STORE_ID, CATALOG_ID, MARKETPLACE))
                .thenReturn(List.of());
    }

    @Test
    void publishesProductWithRealQtyWhenWarehouseCriteriaAreMet() {
        Product product = product("pim-A", "EAN-A");
        MarketplaceDefinition def = warehouseDefinition(/* minWarehouseQty */ 5);
        configureCategoryWith(def, product, /* warehouseQty */ 10);
        priceFor(product, /* price */ 100, /* deliveryDays */ 2);

        listener.handleMessage(request());

        List<MarketplaceOffer> publishedOffers = capturePublishedOffers();
        assertThat(publishedOffers).hasSize(1);
        MarketplaceOffer offer = publishedOffers.get(0);
        assertThat(offer.productId()).isEqualTo("pim-A");
        assertThat(offer.ean()).isEqualTo("EAN-A");
        assertThat(offer.quantity()).isEqualTo(10L);
        assertThat(offer.price()).isEqualTo(100L);
    }

    @Test
    void publishesProductWithQtyZeroWhenWarehouseCriteriaAreNotMet() {
        Product product = product("pim-A", "EAN-A");
        MarketplaceDefinition def = warehouseDefinition(/* minWarehouseQty */ 5);
        configureCategoryWith(def, product, /* warehouseQty */ 2);
        priceFor(product, /* price */ 100, /* deliveryDays */ 2);

        listener.handleMessage(request());

        List<MarketplaceOffer> publishedOffers = capturePublishedOffers();
        assertThat(publishedOffers).hasSize(1);
        assertThat(publishedOffers.get(0).quantity()).isEqualTo(0L);
        assertThat(publishedOffers.get(0).price()).isEqualTo(100L);
    }

    // --- helpers ---------------------------------------------------------

    private MarketplaceOfferExportRequest request() {
        return new MarketplaceOfferExportRequest(MARKETPLACE, STORE_ID, CATALOG_ID, PRICELIST_ID);
    }

    private Product product(String pimId, String ean) {
        Product p = new Product(CATEGORY_ID, pimId, ean, "MFN-" + pimId, "Brand", "Label", "Name-" + pimId,
                "Laptops", "default");
        return p;
    }

    private MarketplaceDefinition warehouseDefinition(int minWarehouseQty) {
        MarketplaceDefinition def = new MarketplaceDefinition(MARKETPLACE, 1.0, 0, 0, 0, minWarehouseQty);
        def.setEnabled(true);
        return def;
    }

    private void configureCategoryWith(MarketplaceDefinition def, Product product, int warehouseQty) {
        configureCategoryWith(def, product, warehouseQty, "Laptops");
    }

    private void configureCategoryWith(MarketplaceDefinition def, Product product, int warehouseQty, String categoryName) {
        CategoryDefinition category = new CategoryDefinition();
        category.setCategoryId(CATEGORY_ID);
        category.setCategory(categoryName);
        category.setMarketplaceDefinitions(List.of(def));

        when(catalog.getCategories()).thenReturn(List.of(category));
        when(productRepository.findAllProductsWithPimId(CATEGORY_ID, true)).thenReturn(List.of(product));

        MatchedInventory matched = mockMatchedInventoryWithWarehouseQty(warehouseQty);
        when(inventoryView.findByProduct(product)).thenReturn(matched);
    }

    private MatchedInventory mockMatchedInventoryWithWarehouseQty(int warehouseQty) {
        MatchedInventory matched = mock(MatchedInventory.class);
        InventoryItem item = mock(InventoryItem.class);
        when(item.qty()).thenReturn(warehouseQty);
        when(matched.getInventoryItemsFromSupplier(SupplierRegistry.WAREHOUSE)).thenReturn(List.of(item));
        return matched;
    }

    private void priceFor(Product product, long price, int deliveryDays) {
        AvailabilityAndPrice ap = new AvailabilityAndPrice(
                product.getPimId(), product.getEan(), product.getManufacturerCode(),
                product.getBrand(), product.getLabel(), product.getName(),
                product.getCategory(), price, 0L, deliveryDays, 0L
        );
        when(pricelist.findByPimId(product.getPimId())).thenReturn(Optional.of(ap));
    }

    @SuppressWarnings("unchecked")
    private List<MarketplaceOffer> capturePublishedOffers() {
        ArgumentCaptor<List<MarketplaceOffer>> captor = ArgumentCaptor.forClass(List.class);
        verify(provider).exportOffers(captor.capture(), any());
        return captor.getValue();
    }

    private void previousSnapshotContains(MarketplaceOfferSnapshot... snapshots) {
        when(marketplaceOfferExportRepository.loadPreviousExport(STORE_ID, CATALOG_ID, MARKETPLACE))
                .thenReturn(List.of(snapshots));
    }

    @SuppressWarnings("unchecked")
    private List<MarketplaceOffer> captureRemovedOffers() {
        ArgumentCaptor<List<MarketplaceOffer>> captor = ArgumentCaptor.forClass(List.class);
        verify(provider).exportOffers(any(), captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<MarketplaceOfferSnapshot> captureSavedSnapshots() {
        ArgumentCaptor<List<MarketplaceOfferSnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(marketplaceOfferExportRepository).saveCurrentExport(
                org.mockito.ArgumentMatchers.eq(STORE_ID),
                org.mockito.ArgumentMatchers.eq(CATALOG_ID),
                org.mockito.ArgumentMatchers.eq(MARKETPLACE),
                captor.capture()
        );
        return captor.getValue();
    }

    private void noCategoriesConfigured() {
        when(catalog.getCategories()).thenReturn(List.of());
    }

    @Test
    void incrementsRemovalAttemptsForProductsNoLongerInCategoryDefinitions() {
        noCategoriesConfigured();
        previousSnapshotContains(new MarketplaceOfferSnapshot("pim-X", 999L, 5L, 0));

        listener.handleMessage(request());

        List<MarketplaceOffer> removed = captureRemovedOffers();
        assertThat(removed).hasSize(1);
        assertThat(removed.get(0).productId()).isEqualTo("pim-X");
        assertThat(removed.get(0).quantity()).isEqualTo(0L);
        assertThat(removed.get(0).price()).isEqualTo(999L);

        List<MarketplaceOfferSnapshot> saved = captureSavedSnapshots();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getPimId()).isEqualTo("pim-X");
        assertThat(saved.get(0).getRemovalAttempts()).isEqualTo(1);
        assertThat(saved.get(0).getQty()).isEqualTo(0L);
    }

    @Test
    void dropsSnapshotEntryAfterReachingRetryThreshold() {
        noCategoriesConfigured();
        previousSnapshotContains(new MarketplaceOfferSnapshot("pim-X", 999L, 0L, 3));

        listener.handleMessage(request());

        verify(provider, never()).exportOffers(any(), any());
        assertThat(captureSavedSnapshots()).isEmpty();
    }

    @Test
    void resetsRemovalAttemptsWhenProductReappearsInCategoryDefinitions() {
        Product product = product("pim-A", "EAN-A");
        configureCategoryWith(warehouseDefinition(5), product, /* warehouseQty */ 10);
        priceFor(product, 100, 2);
        previousSnapshotContains(new MarketplaceOfferSnapshot("pim-A", 80L, 0L, 2));

        listener.handleMessage(request());

        List<MarketplaceOfferSnapshot> saved = captureSavedSnapshots();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getPimId()).isEqualTo("pim-A");
        assertThat(saved.get(0).getQty()).isEqualTo(10L);
        assertThat(saved.get(0).getRemovalAttempts()).isEqualTo(0);
    }

    @Test
    void doesNotCallExportWhenThereIsNothingToPublishOrRemove() {
        noCategoriesConfigured();
        previousSnapshotContains();

        listener.handleMessage(request());

        verify(provider, never()).exportOffers(any(), any());
    }

    @Test
    void skipsProductsNotApprovedWhenExportSelectedProductsIsTrue() {
        Product product = product("pim-A", "EAN-A");
        MarketplaceDefinition def = warehouseDefinition(5);
        def.setExportSelectedProducts(true);
        configureCategoryWith(def, product, 10);
        priceFor(product, 100, 2);

        listener.handleMessage(request());

        verify(provider, never()).exportOffers(any(), any());
    }

    @Test
    void respectsConfigurableRemovalRetryCount() {
        ReflectionTestUtils.setField(listener, "removalRetryCount", 5);
        noCategoriesConfigured();
        previousSnapshotContains(new MarketplaceOfferSnapshot("pim-X", 999L, 0L, 4));

        listener.handleMessage(request());

        assertThat(captureRemovedOffers()).hasSize(1);
        List<MarketplaceOfferSnapshot> saved = captureSavedSnapshots();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getRemovalAttempts()).isEqualTo(5);
    }

    @Test
    void exportsProductGroupResolvedThroughIcecatBridgeForLeafCategory() {
        // given
        Product product = product("pim-A", "EAN-A");
        configureCategoryWith(warehouseDefinition(5), product, /* warehouseQty */ 10, "Karty graficzne");
        priceFor(product, 100, 2);
        when(enumLocalizer.localize(ProductGroup.PcComponents)).thenReturn("Podzespoły komputerowe");
        when(categoryLocalizer.localize("Karty graficzne", "plural")).thenReturn("Karty graficzne");

        // when
        listener.handleMessage(request());

        // then
        List<MarketplaceOffer> published = capturePublishedOffers();
        assertThat(published).hasSize(1);
        assertThat(published.get(0).categoryName()).isEqualTo("Podzespoły komputerowe / Karty graficzne");
    }

    @Test
    void exportsCategoryWithoutProductGroupWhenCategoryIsOutsideTheBridge() {
        // given
        Product product = product("pim-A", "EAN-A");
        configureCategoryWith(warehouseDefinition(5), product, /* warehouseQty */ 10, "Kołdry");
        priceFor(product, 100, 2);
        when(categoryLocalizer.localize("Kołdry", "plural")).thenReturn("Kołdry");

        // when
        listener.handleMessage(request());

        // then
        List<MarketplaceOffer> published = capturePublishedOffers();
        assertThat(published).hasSize(1);
        assertThat(published.get(0).categoryName()).isEqualTo("Kołdry");
    }

    @Test
    void republishesUnchangedOffersOnEveryCycleBecauseOfFullRefresh() {
        Product product = product("pim-A", "EAN-A");
        configureCategoryWith(warehouseDefinition(5), product, /* warehouseQty */ 10);
        priceFor(product, 100, 2);
        previousSnapshotContains(new MarketplaceOfferSnapshot("pim-A", 100L, 10L, 0));

        listener.handleMessage(request());

        List<MarketplaceOffer> published = capturePublishedOffers();
        assertThat(published).hasSize(1);
        assertThat(published.get(0).productId()).isEqualTo("pim-A");
    }
}
