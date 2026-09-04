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
import pl.commercelink.marketplace.api.MarketplaceExportReport;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
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
    void evaluatesCriteriaOnInventoryNarrowedToPricelistPriceWithoutMarkup() {
        // given
        Product product = product("pim-A", "EAN-A");
        MarketplaceDefinition marketplaceDefinition = warehouseDefinition(5);
        marketplaceDefinition.setMarkup(1.1);
        MatchedInventory fullInventory = mock(MatchedInventory.class);
        MatchedInventory inventoryAtPrice = mockMatchedInventoryWithWarehouseQuantity(10);
        when(fullInventory.atPricePoint(100L)).thenReturn(inventoryAtPrice);
        configureCategoryWith(marketplaceDefinition, product, fullInventory);
        priceFor(product, 100, 2);

        // when
        listener.handleMessage(request());

        // then
        List<MarketplaceOffer> published = capturePublishedOffers();
        assertThat(published).hasSize(1);
        assertThat(published.get(0).price()).isEqualTo(110L);
        assertThat(published.get(0).quantity()).isEqualTo(10L);
        verify(fullInventory, never()).getTotalAvailableQtyFromSupplier(any());
    }

    @Test
    void incrementsRemovalAttemptsForProductsNoLongerInCategoryDefinitions() {
        // given
        noCategoriesConfigured();
        previousSnapshotContains(MarketplaceOfferSnapshot.published("pim-X", 999L, 5L));

        // when
        listener.handleMessage(request());

        // then
        List<MarketplaceOffer> removed = captureRemovedOffers();
        assertThat(removed).hasSize(1);
        assertThat(removed.get(0).productId()).isEqualTo("pim-X");
        assertThat(removed.get(0).quantity()).isEqualTo(0L);
        assertThat(removed.get(0).price()).isEqualTo(999L);

        List<MarketplaceOfferSnapshot> saved = captureSavedRows();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).pimId()).isEqualTo("pim-X");
        assertThat(saved.get(0).removalAttempts()).isEqualTo(1);
        assertThat(saved.get(0).quantity()).isEqualTo(0L);
        assertThat(saved.get(0).outcome()).isEqualTo(MarketplaceOfferSnapshot.OUTCOME_REMOVAL_PENDING);
    }

    @Test
    void dropsSnapshotEntryAfterReachingRetryThreshold() {
        // given
        noCategoriesConfigured();
        previousSnapshotContains(MarketplaceOfferSnapshot.removalPending("pim-X", 999L, 3));

        // when
        listener.handleMessage(request());

        // then
        verify(provider, never()).exportOffers(any(), any(), any());
        assertThat(captureSavedRows()).isEmpty();
    }

    @Test
    void resetsRemovalAttemptsWhenProductReappearsInCategoryDefinitions() {
        // given
        Product product = product("pim-A", "EAN-A");
        configureCategoryWith(warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        previousSnapshotContains(MarketplaceOfferSnapshot.removalPending("pim-A", 80L, 2));

        // when
        listener.handleMessage(request());

        // then
        List<MarketplaceOfferSnapshot> saved = captureSavedRows();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).pimId()).isEqualTo("pim-A");
        assertThat(saved.get(0).quantity()).isEqualTo(10L);
        assertThat(saved.get(0).removalAttempts()).isEqualTo(0);
        assertThat(saved.get(0).outcome()).isEqualTo(MarketplaceOfferSnapshot.OUTCOME_PUBLISHED);
    }

    @Test
    void doesNotCallExportWhenThereIsNothingToPublishOrRemove() {
        // given
        noCategoriesConfigured();
        previousSnapshotContains();

        // when
        listener.handleMessage(request());

        // then
        verify(provider, never()).exportOffers(any(), any(), any());
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
        verify(provider, never()).exportOffers(any(), any(), any());
    }

    @Test
    void skipsProductsMissingFromThePricelist() {
        // given
        Product product = product("pim-A", "EAN-A");
        configureCategoryWith(warehouseDefinition(5), product, 10);
        when(pricelist.findByPimId("pim-A")).thenReturn(Optional.empty());

        // when
        listener.handleMessage(request());

        // then
        verify(provider, never()).exportOffers(any(), any(), any());
    }

    @Test
    void skipsCategoriesWithoutAnEnabledMarketplaceDefinition() {
        // given
        MarketplaceDefinition marketplaceDefinition = warehouseDefinition(5);
        marketplaceDefinition.setEnabled(false);
        when(catalog.getCategories()).thenReturn(List.of(category("Laptops", null, List.of(marketplaceDefinition))));

        // when
        listener.handleMessage(request());

        // then
        verify(provider, never()).exportOffers(any(), any(), any());
    }

    @Test
    void respectsConfigurableRemovalRetryCount() {
        // given
        ReflectionTestUtils.setField(listener, "removalRetryCount", 5);
        noCategoriesConfigured();
        previousSnapshotContains(MarketplaceOfferSnapshot.removalPending("pim-X", 999L, 4));

        // when
        listener.handleMessage(request());

        // then
        assertThat(captureRemovedOffers()).hasSize(1);
        List<MarketplaceOfferSnapshot> saved = captureSavedRows();
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
        previousSnapshotContains(MarketplaceOfferSnapshot.published("pim-A", 100L, 10L));

        // when
        listener.handleMessage(request());

        // then
        List<MarketplaceOffer> published = capturePublishedOffers();
        assertThat(published).hasSize(1);
        assertThat(published.get(0).productId()).isEqualTo("pim-A");
    }

    @Test
    void recordsRejectionsReportedByTheConnectorOnTheMatchingRow() {
        // given
        Product rejectedProduct = product("pim-A", "EAN-A");
        configureCategoryWith(warehouseDefinition(5), rejectedProduct, 10);
        priceFor(rejectedProduct, 100, 2);
        connectorRejects("pim-A", "VALIDATION_ERROR", "price out of range");

        // when
        listener.handleMessage(request());

        // then
        List<MarketplaceOfferSnapshot> saved = captureSavedRows();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).pimId()).isEqualTo("pim-A");
        assertThat(saved.get(0).outcome()).isEqualTo(MarketplaceOfferSnapshot.OUTCOME_REJECTED);
        assertThat(saved.get(0).reasonCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(saved.get(0).message()).isEqualTo("price out of range");
    }

    @Test
    void appendsARowForARejectionReportedForAProductThatIsNotAmongTheOffers() {
        // given
        Product product = product("pim-A", "EAN-A");
        configureCategoryWith(warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        connectorRejects("pim-OTHER", "VALIDATION_ERROR", "price out of range");

        // when
        listener.handleMessage(request());

        // then
        List<MarketplaceOfferSnapshot> saved = captureSavedRows();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).pimId()).isEqualTo("pim-A");
        assertThat(saved.get(0).outcome()).isEqualTo(MarketplaceOfferSnapshot.OUTCOME_PUBLISHED);
        assertThat(saved.get(0).reasonCode()).isNull();
        assertThat(saved.get(1).pimId()).isEqualTo("pim-OTHER");
        assertThat(saved.get(1).outcome()).isEqualTo(MarketplaceOfferSnapshot.OUTCOME_REJECTED);
        assertThat(saved.get(1).reasonCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(saved.get(1).message()).isEqualTo("price out of range");
    }

    @Test
    void savesFailedRunWithTheExceptionSummaryAndRethrowsWhenProviderFails() {
        // given
        Product product = product("pim-A", "EAN-A");
        configureCategoryWith(warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        doThrow(new IllegalStateException("marketplace unavailable"))
                .when(provider).exportOffers(any(), any(), any());

        // when / then
        assertThatThrownBy(() -> listener.handleMessage(request()))
                .isInstanceOf(IllegalStateException.class);

        MarketplaceExportRun run = captureRun();
        assertThat(run.isFailed()).isTrue();

        List<MarketplaceOfferSnapshot> saved = run.toRows();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).pimId()).isEqualTo("pim-A");
        assertThat(saved.get(1).outcome()).isEqualTo(MarketplaceOfferSnapshot.OUTCOME_EXPORT_ABORTED);
        assertThat(saved.get(1).message())
                .isEqualTo("java.lang.IllegalStateException: marketplace unavailable");
    }

    @Test
    void recordsRunMetadataFromThePayload() {
        // given
        noCategoriesConfigured();

        // when
        listener.handleMessage(request());

        // then
        MarketplaceExportRun run = captureRun();
        assertThat(run.getStoreId()).isEqualTo(STORE_ID);
        assertThat(run.getCatalogId()).isEqualTo(CATALOG_ID);
        assertThat(run.getMarketplace()).isEqualTo(MARKETPLACE);
        assertThat(run.isFailed()).isFalse();
    }

    private MarketplaceOfferExportRequest request() {
        return new MarketplaceOfferExportRequest(MARKETPLACE, STORE_ID, CATALOG_ID, PRICELIST_ID);
    }

    private Product product(String pimId, String ean) {
        return new Product(CATEGORY_ID, pimId, ean, "MFN-" + pimId, "Brand", "Label", "Name-" + pimId, "default");
    }

    private MarketplaceDefinition warehouseDefinition(int minWarehouseQuantity) {
        MarketplaceDefinition marketplaceDefinition = new MarketplaceDefinition(MARKETPLACE, 1.0, 0, 0, 0, 0, minWarehouseQuantity);
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
        configureCategoryWith(marketplaceDefinition, product, mockMatchedInventoryWithWarehouseQuantity(warehouseQuantity), categoryName, definitionName);
    }

    private void configureCategoryWith(MarketplaceDefinition marketplaceDefinition, Product product, MatchedInventory matchedInventory) {
        configureCategoryWith(marketplaceDefinition, product, matchedInventory, "Laptops", null);
    }

    private void configureCategoryWith(MarketplaceDefinition marketplaceDefinition, Product product, MatchedInventory matchedInventory, String categoryName, String definitionName) {
        when(catalog.getCategories()).thenReturn(List.of(category(categoryName, definitionName, List.of(marketplaceDefinition))));
        when(productRepository.findAllProductsWithPimId(CATEGORY_ID, true)).thenReturn(List.of(product));
        when(inventoryView.findByProduct(product)).thenReturn(matchedInventory);
    }

    private MatchedInventory mockMatchedInventoryWithWarehouseQuantity(int warehouseQuantity) {
        MatchedInventory matchedInventory = mock(MatchedInventory.class);
        when(matchedInventory.atPricePoint(anyLong())).thenReturn(matchedInventory);
        when(matchedInventory.getTotalAvailableQtyFromSupplier(SupplierRegistry.WAREHOUSE)).thenReturn((long) warehouseQuantity);
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

    private void connectorRejects(String pimId, String reasonCode, String message) {
        doAnswer(invocation -> {
            invocation.getArgument(2, MarketplaceExportReport.class).rejected(pimId, reasonCode, message);
            return null;
        }).when(provider).exportOffers(any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    private List<MarketplaceOffer> capturePublishedOffers() {
        ArgumentCaptor<List<MarketplaceOffer>> captor = ArgumentCaptor.forClass(List.class);
        verify(provider).exportOffers(captor.capture(), any(), any());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<MarketplaceOffer> captureRemovedOffers() {
        ArgumentCaptor<List<MarketplaceOffer>> captor = ArgumentCaptor.forClass(List.class);
        verify(provider).exportOffers(any(), captor.capture(), any());
        return captor.getValue();
    }

    private MarketplaceExportRun captureRun() {
        ArgumentCaptor<MarketplaceExportRun> captor = ArgumentCaptor.forClass(MarketplaceExportRun.class);
        verify(marketplaceExportRunService).saveRun(captor.capture());
        return captor.getValue();
    }

    private List<MarketplaceOfferSnapshot> captureSavedRows() {
        return captureRun().toRows();
    }

    private void previousSnapshotContains(MarketplaceOfferSnapshot... snapshots) {
        when(marketplaceExportRunService.loadPreviousExport(STORE_ID, CATALOG_ID, MARKETPLACE))
                .thenReturn(List.of(snapshots));
    }

    private void noCategoriesConfigured() {
        when(catalog.getCategories()).thenReturn(List.of());
    }
}
