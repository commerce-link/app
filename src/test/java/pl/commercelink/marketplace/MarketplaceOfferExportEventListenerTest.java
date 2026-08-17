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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
    @Mock private MarketplaceExportRunService marketplaceExportRunService;

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

        when(marketplaceExportRunService.loadPreviousExport(STORE_ID, CATALOG_ID, MARKETPLACE))
                .thenReturn(List.of());
    }

    @Test
    void publishesProductWithRealQuantityWhenWarehouseCriteriaAreMet() {
        // given
        Product product = product("pim-A", "EAN-A");
        MarketplaceDefinition marketplaceDefinition = warehouseDefinition(5);
        configureCategoryWith(marketplaceDefinition, product, 10);
        priceFor(product, 100, 2);

        // when
        listener.handleMessage(request());

        // then
        List<MarketplaceOffer> publishedOffers = capturePublishedOffers();
        assertThat(publishedOffers).hasSize(1);
        MarketplaceOffer offer = publishedOffers.get(0);
        assertThat(offer.productId()).isEqualTo("pim-A");
        assertThat(offer.ean()).isEqualTo("EAN-A");
        assertThat(offer.quantity()).isEqualTo(10L);
        assertThat(offer.price()).isEqualTo(100L);
    }

    @Test
    void publishesProductWithQuantityZeroWhenWarehouseCriteriaAreNotMet() {
        // given
        Product product = product("pim-A", "EAN-A");
        MarketplaceDefinition marketplaceDefinition = warehouseDefinition(5);
        configureCategoryWith(marketplaceDefinition, product, 2);
        priceFor(product, 100, 2);

        // when
        listener.handleMessage(request());

        // then
        List<MarketplaceOffer> publishedOffers = capturePublishedOffers();
        assertThat(publishedOffers).hasSize(1);
        assertThat(publishedOffers.get(0).quantity()).isEqualTo(0L);
        assertThat(publishedOffers.get(0).price()).isEqualTo(100L);
    }

    @Test
    void incrementsRemovalAttemptsForProductsNoLongerInCategoryDefinitions() {
        // given
        noCategoriesConfigured();
        previousSnapshotContains(snapshot("pim-X", 999L, 5L, 0));

        // when
        listener.handleMessage(request());

        // then
        List<MarketplaceOffer> removed = captureRemovedOffers();
        assertThat(removed).hasSize(1);
        assertThat(removed.get(0).productId()).isEqualTo("pim-X");
        assertThat(removed.get(0).quantity()).isEqualTo(0L);
        assertThat(removed.get(0).price()).isEqualTo(999L);

        List<MarketplaceOfferSnapshot> saved = captureSavedOffers();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).pimId()).isEqualTo("pim-X");
        assertThat(saved.get(0).removalAttempts()).isEqualTo(1);
        assertThat(saved.get(0).quantity()).isEqualTo(0L);
        assertThat(saved.get(0).pendingRemoval()).isTrue();
    }

    @Test
    void dropsSnapshotEntryAfterReachingRetryThreshold() {
        // given
        noCategoriesConfigured();
        previousSnapshotContains(snapshot("pim-X", 999L, 0L, 3));

        // when
        listener.handleMessage(request());

        // then
        verify(provider, never()).exportOffers(any(), any());
        assertThat(captureSavedOffers()).isEmpty();
    }

    @Test
    void resetsRemovalAttemptsWhenProductReappearsInCategoryDefinitions() {
        // given
        Product product = product("pim-A", "EAN-A");
        configureCategoryWith(warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        previousSnapshotContains(snapshot("pim-A", 80L, 0L, 2));

        // when
        listener.handleMessage(request());

        // then
        List<MarketplaceOfferSnapshot> saved = captureSavedOffers();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).pimId()).isEqualTo("pim-A");
        assertThat(saved.get(0).quantity()).isEqualTo(10L);
        assertThat(saved.get(0).removalAttempts()).isEqualTo(0);
    }

    @Test
    void doesNotCallExportWhenThereIsNothingToPublishOrRemove() {
        // given
        noCategoriesConfigured();
        previousSnapshotContains();

        // when
        listener.handleMessage(request());

        // then
        verify(provider, never()).exportOffers(any(), any());
    }

    @Test
    void skipsProductsNotApprovedWhenExportSelectedProductsIsTrue() {
        // given
        Product product = product("pim-A", "EAN-A");
        MarketplaceDefinition marketplaceDefinition = warehouseDefinition(5);
        marketplaceDefinition.setExportSelectedProducts(true);
        configureCategoryWith(marketplaceDefinition, product, 10);
        priceFor(product, 100, 2);

        // when
        listener.handleMessage(request());

        // then
        verify(provider, never()).exportOffers(any(), any());
    }

    @Test
    void respectsConfigurableRemovalRetryCount() {
        // given
        ReflectionTestUtils.setField(listener, "removalRetryCount", 5);
        noCategoriesConfigured();
        previousSnapshotContains(snapshot("pim-X", 999L, 0L, 4));

        // when
        listener.handleMessage(request());

        // then
        assertThat(captureRemovedOffers()).hasSize(1);
        List<MarketplaceOfferSnapshot> saved = captureSavedOffers();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).removalAttempts()).isEqualTo(5);
    }

    @Test
    void exportsCategoryDefinitionNameAsTheOfferCategory() {
        // given
        Product product = product("pim-A", "EAN-A");
        configureCategoryWith(warehouseDefinition(5), product, 10, "Karty graficzne", "Karty do gier");
        priceFor(product, 100, 2);

        // when
        listener.handleMessage(request());

        // then
        List<MarketplaceOffer> published = capturePublishedOffers();
        assertThat(published).hasSize(1);
        assertThat(published.get(0).categoryName()).isEqualTo("Karty do gier");
    }

    @Test
    void republishesUnchangedOffersOnEveryCycleBecauseOfFullRefresh() {
        // given
        Product product = product("pim-A", "EAN-A");
        configureCategoryWith(warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        previousSnapshotContains(snapshot("pim-A", 100L, 10L, 0));

        // when
        listener.handleMessage(request());

        // then
        List<MarketplaceOffer> published = capturePublishedOffers();
        assertThat(published).hasSize(1);
        assertThat(published.get(0).productId()).isEqualTo("pim-A");
    }

    @Test
    void recordsCategoryWithoutMarketplaceDefinitionAsExcluded() {
        // given
        CategoryDefinition category = category("Laptops", null, List.of());
        when(catalog.getCategories()).thenReturn(List.of(category));

        // when
        listener.handleMessage(request());

        // then
        assertThat(reasonsOf(captureRun()))
                .containsExactly(MarketplaceExportSkipReason.CATEGORY_NOT_CONFIGURED_FOR_MARKETPLACE.name());
    }

    @Test
    void recordsDisabledMarketplaceDefinitionAsExcluded() {
        // given
        MarketplaceDefinition marketplaceDefinition = warehouseDefinition(5);
        marketplaceDefinition.setEnabled(false);
        CategoryDefinition category = category("Laptops", null, List.of(marketplaceDefinition));
        when(catalog.getCategories()).thenReturn(List.of(category));

        // when
        listener.handleMessage(request());

        // then
        assertThat(reasonsOf(captureRun()))
                .containsExactly(MarketplaceExportSkipReason.CATEGORY_MARKETPLACE_DEFINITION_DISABLED.name());
    }

    @Test
    void recordsDisabledProductAsExcluded() {
        // given
        Product product = product("pim-A", "EAN-A");
        product.setEnabled(false);
        configureCategoryWith(warehouseDefinition(5), product, 10);

        // when
        listener.handleMessage(request());

        // then
        assertThat(reasonsOf(captureRun()))
                .containsExactly(MarketplaceExportSkipReason.PRODUCT_DISABLED.name());
    }

    @Test
    void recordsProductWithoutPimIdAsExcluded() {
        // given
        Product product = product(null, "EAN-A");
        configureCategoryWith(warehouseDefinition(5), product, 10);

        // when
        listener.handleMessage(request());

        // then
        assertThat(reasonsOf(captureRun()))
                .containsExactly(MarketplaceExportSkipReason.PRODUCT_WITHOUT_PIM_ID.name());
    }

    @Test
    void recordsProductMissingFromPricelistAsExcluded() {
        // given
        Product product = product("pim-A", "EAN-A");
        configureCategoryWith(warehouseDefinition(5), product, 10);
        when(pricelist.findByPimId("pim-A")).thenReturn(Optional.empty());

        // when
        listener.handleMessage(request());

        // then
        MarketplaceExportRunDocument document = captureRun();
        assertThat(reasonsOf(document))
                .containsExactly(MarketplaceExportSkipReason.PRODUCT_NOT_IN_PRICELIST.name());
        assertThat(document.excluded().get(0).pimId()).isEqualTo("pim-A");
        assertThat(document.excluded().get(0).ean()).isEqualTo("EAN-A");
    }

    @Test
    void recordsUnapprovedProductAsExcludedWithItsReason() {
        // given
        Product product = product("pim-A", "EAN-A");
        MarketplaceDefinition marketplaceDefinition = warehouseDefinition(5);
        marketplaceDefinition.setExportSelectedProducts(true);
        configureCategoryWith(marketplaceDefinition, product, 10);
        priceFor(product, 100, 2);

        // when
        listener.handleMessage(request());

        // then
        assertThat(reasonsOf(captureRun()))
                .containsExactly(MarketplaceExportSkipReason.PRODUCT_NOT_APPROVED_FOR_MARKETPLACE.name());
    }

    @Test
    void recordsZeroedQuantityWithThresholdsAndStillPublishesTheOffer() {
        // given
        Product product = product("pim-A", "EAN-A");
        configureCategoryWith(warehouseDefinition(5), product, 2);
        priceFor(product, 100, 2);

        // when
        listener.handleMessage(request());

        // then
        MarketplaceExportRunDocument document = captureRun();
        assertThat(reasonsOf(document))
                .containsExactly(MarketplaceExportSkipReason.QUANTITY_ZEROED_BELOW_WAREHOUSE_THRESHOLD.name());
        assertThat(document.excluded().get(0).detail().warehouseQuantity()).isEqualTo(2L);
        assertThat(document.excluded().get(0).detail().minWarehouseQuantity()).isEqualTo(5);
        assertThat(document.offers()).hasSize(1);
        assertThat(document.offers().get(0).quantity()).isEqualTo(0L);
        assertThat(document.offers().get(0).quantityZeroedReason())
                .isEqualTo(MarketplaceExportSkipReason.QUANTITY_ZEROED_BELOW_WAREHOUSE_THRESHOLD.name());
    }

    @Test
    void recordsRunMetadataFromThePayload() {
        // given
        noCategoriesConfigured();

        // when
        listener.handleMessage(request());

        // then
        MarketplaceExportRunDocument document = captureRun();
        assertThat(document.storeId()).isEqualTo(STORE_ID);
        assertThat(document.catalogId()).isEqualTo(CATALOG_ID);
        assertThat(document.marketplace()).isEqualTo(MARKETPLACE);
        assertThat(document.pricelistId()).isEqualTo(PRICELIST_ID);
        assertThat(document.wasSuccessful()).isTrue();
    }

    @Test
    void marksProviderNotCalledWhenThereIsNothingToPublishOrRemove() {
        // given
        noCategoriesConfigured();

        // when
        listener.handleMessage(request());

        // then
        assertThat(captureRun().providerCalled()).isFalse();
    }

    @Test
    void savesFailedRunWithStackTraceAndRethrowsWhenProviderFails() {
        // given
        Product product = product("pim-A", "EAN-A");
        configureCategoryWith(warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        doThrow(new IllegalStateException("marketplace unavailable"))
                .when(provider).exportOffers(any(), any());

        // when / then
        assertThatThrownBy(() -> listener.handleMessage(request()))
                .isInstanceOf(IllegalStateException.class);

        MarketplaceExportRunDocument document = captureRun();
        assertThat(document.wasSuccessful()).isFalse();
        assertThat(document.failure()).isNotEmpty();
        assertThat(document.failure().get(0)).contains("marketplace unavailable");
        assertThat(document.offers()).hasSize(1);
    }

    private MarketplaceOfferExportRequest request() {
        return new MarketplaceOfferExportRequest(MARKETPLACE, STORE_ID, CATALOG_ID, PRICELIST_ID);
    }

    private Product product(String pimId, String ean) {
        return new Product(CATEGORY_ID, pimId, ean, "MFN-" + pimId, "Brand", "Label", "Name-" + pimId, "default");
    }

    private MarketplaceOfferSnapshot snapshot(String pimId, long price, long qty, int removalAttempts) {
        return new MarketplaceOfferSnapshot(pimId, price, qty, removalAttempts, removalAttempts > 0, null);
    }

    private MarketplaceDefinition warehouseDefinition(int minWarehouseQuantity) {
        MarketplaceDefinition marketplaceDefinition = new MarketplaceDefinition(MARKETPLACE, 1.0, 0, 0, 0, minWarehouseQuantity);
        marketplaceDefinition.setEnabled(true);
        return marketplaceDefinition;
    }

    private CategoryDefinition category(String categoryName, String definitionName, List<MarketplaceDefinition> definitions) {
        CategoryDefinition category = new CategoryDefinition();
        category.setCategoryId(CATEGORY_ID);
        category.setCategory(categoryName);
        category.setName(definitionName);
        category.setMarketplaceDefinitions(definitions);
        return category;
    }

    private void configureCategoryWith(MarketplaceDefinition marketplaceDefinition, Product product, int warehouseQuantity) {
        configureCategoryWith(marketplaceDefinition, product, warehouseQuantity, "Laptops");
    }

    private void configureCategoryWith(MarketplaceDefinition marketplaceDefinition, Product product, int warehouseQuantity, String categoryName) {
        configureCategoryWith(marketplaceDefinition, product, warehouseQuantity, categoryName, null);
    }

    private void configureCategoryWith(MarketplaceDefinition marketplaceDefinition, Product product, int warehouseQuantity, String categoryName, String definitionName) {
        when(catalog.getCategories()).thenReturn(List.of(category(categoryName, definitionName, List.of(marketplaceDefinition))));
        when(productRepository.findAll(CATEGORY_ID)).thenReturn(List.of(product));

        MatchedInventory matchedInventory = mockMatchedInventoryWithWarehouseQuantity(warehouseQuantity);
        when(inventoryView.findByProduct(product)).thenReturn(matchedInventory);
    }

    private MatchedInventory mockMatchedInventoryWithWarehouseQuantity(int warehouseQuantity) {
        MatchedInventory matchedInventory = mock(MatchedInventory.class);
        InventoryItem inventoryItem = mock(InventoryItem.class);
        when(inventoryItem.qty()).thenReturn(warehouseQuantity);
        when(matchedInventory.getInventoryItemsFromSupplier(SupplierRegistry.WAREHOUSE)).thenReturn(List.of(inventoryItem));
        return matchedInventory;
    }

    private void priceFor(Product product, long price, int deliveryDays) {
        AvailabilityAndPrice availabilityAndPrice = new AvailabilityAndPrice(
                product.getPimId(), product.getEan(), product.getManufacturerCode(),
                product.getBrand(), product.getLabel(), product.getName(),
                "Laptops", price, 0L, deliveryDays, 0L, false
        );
        when(pricelist.findByPimId(product.getPimId())).thenReturn(Optional.of(availabilityAndPrice));
    }

    @SuppressWarnings("unchecked")
    private List<MarketplaceOffer> capturePublishedOffers() {
        ArgumentCaptor<List<MarketplaceOffer>> captor = ArgumentCaptor.forClass(List.class);
        verify(provider).exportOffers(captor.capture(), any());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<MarketplaceOffer> captureRemovedOffers() {
        ArgumentCaptor<List<MarketplaceOffer>> captor = ArgumentCaptor.forClass(List.class);
        verify(provider).exportOffers(any(), captor.capture());
        return captor.getValue();
    }

    private MarketplaceExportRunDocument captureRun() {
        ArgumentCaptor<MarketplaceExportRun> captor = ArgumentCaptor.forClass(MarketplaceExportRun.class);
        verify(marketplaceExportRunService).saveRun(captor.capture());
        return captor.getValue().toDocument("2026-08-13_01-31-05", Instant.parse("2026-08-13T01:31:05Z"));
    }

    private List<MarketplaceOfferSnapshot> captureSavedOffers() {
        return captureRun().offers();
    }

    private List<String> reasonsOf(MarketplaceExportRunDocument document) {
        return document.excluded().stream().map(MarketplaceExportExcludedItem::reason).toList();
    }

    private void previousSnapshotContains(MarketplaceOfferSnapshot... snapshots) {
        when(marketplaceExportRunService.loadPreviousExport(STORE_ID, CATALOG_ID, MARKETPLACE))
                .thenReturn(List.of(snapshots));
    }

    private void noCategoriesConfigured() {
        when(catalog.getCategories()).thenReturn(List.of());
    }
}
