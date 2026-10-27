package pl.commercelink.marketplace;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
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
import pl.commercelink.products.information.StorePimAliases;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock private StoreActivity storeActivity;
    @Mock private StorePimAliases aliases;

    @InjectMocks
    private MarketplaceOfferExportEventListener listener;

    // the offers published in each run of chainRuns (an empty list for a run that did not call the marketplace)
    private final List<List<MarketplaceOffer>> publishedPerRun = new ArrayList<>();

    @BeforeEach
    void storesAreActive() {
        lenient().when(storeActivity.isActive(any(Store.class))).thenReturn(true);
    }

    @BeforeEach
    void productsHaveNoCloneByDefault() {
        lenient().when(aliases.find(any(), any(), any())).thenAnswer(invocation ->
                invocation.getArgument(1, Pricelist.class).findByPimId(invocation.getArgument(2)));
        lenient().when(aliases.offerIdOf(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        lenient().when(aliases.knows(any(), any())).thenReturn(true);
    }

    @BeforeEach
    void setUpDefaults() {
        ReflectionTestUtils.setField(listener, "removalRetryCount", 3);
        ReflectionTestUtils.setField(listener, "unknownEntryMaxWaitingRuns", 3);

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
    void productOnACloneIsExportedUnderTheSourceId() {
        // given
        Product product = product("clone-C", "EAN-A");
        configureCategoryWith(warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        when(aliases.offerIdOf(STORE_ID, "clone-C")).thenReturn("source-S");

        // when
        listener.handleMessage(request());

        // then
        List<MarketplaceOffer> published = capturePublishedOffers();
        assertThat(published).singleElement().satisfies(offer -> {
            assertThat(offer.productId()).isEqualTo("source-S");
            assertThat(offer.ean()).isEqualTo("EAN-A");
        });
        assertThat(captureSavedRows()).singleElement()
                .satisfies(row -> assertThat(row.pimId()).isEqualTo("source-S"));
    }

    @Test
    void movingToTheCloneDoesNotOrphanTheOffer() {
        // given the offer was published as S, the product now sits on the clone C (pricelist already keyed by C)
        Product product = product("clone-C", "EAN-A");
        configureCategoryWith(warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        when(aliases.offerIdOf(STORE_ID, "clone-C")).thenReturn("source-S");
        previousSnapshotContains(MarketplaceOfferSnapshot.published("source-S", 100L, 10L, "EAN-A", "MFN-clone-C"));

        // when
        listener.handleMessage(request());

        // then
        assertThat(captureRemovedOffers()).isEmpty();
        assertThat(capturePublishedOffers()).extracting(MarketplaceOffer::productId).containsExactly("source-S");
    }

    @Test
    void afterDeletingTheCloneTheOfferIdIsUnchanged() {
        // given the product is back on S, the pricelist was built while the clone existed and still carries a C row
        Product product = product("source-S", "EAN-A");
        configureCategoryWith(warehouseDefinition(5), product, 10);
        usePricelist(new Pricelist(PRICELIST_ID, List.of(row("clone-C", product, 100))));
        previousSnapshotContains(MarketplaceOfferSnapshot.published("source-S", 100L, 10L, "EAN-A", "MFN-source-S"));

        // when
        listener.handleMessage(request());

        // then
        assertThat(captureRemovedOffers()).isEmpty();
        assertThat(capturePublishedOffers()).singleElement().satisfies(offer -> {
            assertThat(offer.productId()).isEqualTo("source-S");
            assertThat(offer.price()).isEqualTo(100L);
        });
    }

    @Test
    void aProductLeavingTheCatalogIsUnpublishedEvenWhenAnotherProductSharesItsEan() {
        // given pim-B left the catalog; pim-A, still exported, carries the same EAN (the marketplace keys offers by id)
        Product product = product("pim-A", "EAN-A");
        configureCategoryWith(warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        previousSnapshotContains(
                MarketplaceOfferSnapshot.published("pim-A", 100L, 10L, "EAN-A", "MFN-pim-A"),
                MarketplaceOfferSnapshot.published("pim-B", 90L, 4L, "EAN-A", "MFN-pim-B"));

        // when
        listener.handleMessage(request());

        // then
        assertThat(captureRemovedOffers()).extracting(MarketplaceOffer::productId).containsExactly("pim-B");
        assertThat(capturePublishedOffers()).extracting(MarketplaceOffer::productId).containsExactly("pim-A");
    }

    @Test
    void aProductRepinnedToAnotherEntryWithTheSameEanUnpublishesTheOldOffer() {
        // given the offer was published as pim-OLD; the product is now pinned to pim-NEW with the same EAN
        Product product = product("pim-NEW", "EAN-A");
        configureCategoryWith(warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        previousSnapshotContains(MarketplaceOfferSnapshot.published("pim-OLD", 100L, 10L, "EAN-A", "MFN-pim-NEW"));

        // when
        listener.handleMessage(request());

        // then
        assertThat(captureRemovedOffers()).extracting(MarketplaceOffer::productId).containsExactly("pim-OLD");
        assertThat(capturePublishedOffers()).extracting(MarketplaceOffer::productId).containsExactly("pim-NEW");
        assertThat(captureSavedRows()).extracting(MarketplaceOfferSnapshot::pimId)
                .containsExactlyInAnyOrder("pim-OLD", "pim-NEW");
    }

    @Test
    void aProductTheIndexCopyDoesNotKnowIsSkippedAndItsOfferKept() {
        // given the product moved onto the clone C, but this instance's index copy predates the clone
        Product product = product("clone-C", "5900000000011");
        configureCategoryWith(warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        when(aliases.knows(STORE_ID, "clone-C")).thenReturn(false);
        // the snapshot carries the same EAN in its GTIN-14 form: the two match once unified
        MarketplaceOfferSnapshot published = MarketplaceOfferSnapshot.published("source-S", 100L, 10L, "05900000000011", "MFN-S");
        previousSnapshotContains(published);

        // when
        listener.handleMessage(request());

        // then neither C is published nor S taken off; S waits unchanged for a run with a fresher copy
        verify(provider, never()).exportOffers(any(), any(), any());
        assertThat(captureSavedRows()).containsExactly(published.withWaitingRuns(1));
    }

    @Test
    void aSkippedProductShieldsOnlyPublishedSnapshotsWithItsEan() {
        // given
        Product unknown = product("clone-C", "EAN-A");
        configureCategoryWith(warehouseDefinition(5), unknown, 10);
        priceFor(unknown, 100, 2);
        when(aliases.knows(STORE_ID, "clone-C")).thenReturn(false);
        MarketplaceOfferSnapshot published = MarketplaceOfferSnapshot.published("source-S", 100L, 10L, "EAN-A", "MFN-S");
        MarketplaceOfferSnapshot pending = MarketplaceOfferSnapshot.removalPending("pim-P", 100L, 1, "EAN-A", "MFN-P");
        MarketplaceOfferSnapshot failed = MarketplaceOfferSnapshot.removalFailed(
                "pim-F", 100L, 3, "GONE", "refused", "EAN-A", "MFN-F");
        previousSnapshotContains(published, pending, failed,
                MarketplaceOfferSnapshot.published("pim-Z", 50L, 3L, "EAN-Z", "MFN-Z"));

        // when
        listener.handleMessage(request());

        // then only the live offer with the EAN is kept; a removal in progress goes on and a failed one is forgotten
        assertThat(captureRemovedOffers()).extracting(MarketplaceOffer::productId).containsExactlyInAnyOrder("pim-P", "pim-Z");
        assertThat(capturePublishedOffers()).isEmpty();
        List<MarketplaceOfferSnapshot> saved = captureSavedRows();
        assertThat(saved).contains(published.withWaitingRuns(1))
                .extracting(MarketplaceOfferSnapshot::pimId).containsExactlyInAnyOrder("source-S", "pim-P", "pim-Z");
        assertThat(saved).filteredOn(row -> row.pimId().equals("pim-P")).singleElement().satisfies(row -> {
            assertThat(row.removalAttempts()).isEqualTo(2);
            assertThat(row.waitingRuns()).isZero();
        });
    }

    @Test
    void aDeadPinWithAnOfferUnderItsOwnIdIsExportedWithFreshStockEveryRun() {
        // given a product pinned to an entry no index copy knows (its global entry deleted, the delete event lost),
        // already published under that id before; its actual stock changes from run to run
        Product product = product("dead-D", "5900000000011");
        MatchedInventory stock = mock(MatchedInventory.class);
        when(stock.atPricePoint(anyLong())).thenReturn(stock);
        when(stock.getTotalAvailableQtyFromSupplier(SupplierRegistry.WAREHOUSE)).thenReturn(26L, 27L, 25L, 24L, 23L);
        configureCategoryWith(warehouseDefinition(5), product, stock);
        priceFor(product, 100, 2);
        when(aliases.knows(STORE_ID, "dead-D")).thenReturn(false);
        List<List<String>> removedPerRun = chainRuns(
                MarketplaceOfferSnapshot.published("dead-D", 100L, 28L, "5900000000011", "MFN-dead-D"));

        try (LogCapture logs = LogCapture.start()) {
            // when
            for (int run = 1; run <= 5; run++) {
                listener.handleMessage(request());
            }

            // then it is exported as before, never frozen and never taken off; each run warns once to re-pin it
            assertThat(removedPerRun).containsOnly(List.of());
            assertThat(publishedPerRun).hasSize(5).allSatisfy(offers -> assertThat(offers).singleElement()
                    .satisfies(offer -> assertThat(offer.productId()).isEqualTo("dead-D")));
            assertThat(publishedPerRun).extracting(offers -> offers.get(0).quantity()).containsExactly(26L, 27L, 25L, 24L, 23L);
            assertThat(logs.errors()).isEmpty();
            assertThat(logs.warnings()).hasSize(5).allSatisfy(warning -> assertThat(warning)
                    .contains(STORE_ID).contains(product.getProductId()).contains("dead-D"));
        }
    }

    @Test
    void aDeadPinWithNoPreviousOfferIsExportedOnEveryRun() {
        // given a product pinned to an entry no index copy knows, with nothing in the previous export (a new
        // marketplace or category, or a previous export that could not be read)
        Product product = product("dead-D", "5900000000011");
        configureCategoryWith(warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        when(aliases.knows(STORE_ID, "dead-D")).thenReturn(false);
        List<List<String>> removedPerRun = chainRuns();

        try (LogCapture logs = LogCapture.start()) {
            // when
            for (int run = 1; run <= 5; run++) {
                listener.handleMessage(request());
            }

            // then it is never held back, as before stores had clones; each run warns once to re-pin it
            assertThat(removedPerRun).containsOnly(List.of());
            assertThat(publishedPerRun).hasSize(5).allSatisfy(offers -> assertThat(offers)
                    .extracting(MarketplaceOffer::productId).containsExactly("dead-D"));
            assertThat(logs.errors()).isEmpty();
            assertThat(logs.warnings()).hasSize(5).allSatisfy(warning -> assertThat(warning)
                    .contains(product.getProductId()).contains("dead-D").contains("re-pin"));
        }
    }

    @Test
    void aDeadPinSharingTheEanOfAnOrphanIsHeldOnlyWhileTheShieldLasts() {
        // given no offer under the product's own id, but a previous offer with its EAN that no product offers now
        Product product = product("dead-D", "5900000000011");
        configureCategoryWith(warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        when(aliases.knows(STORE_ID, "dead-D")).thenReturn(false);
        List<List<String>> removedPerRun = chainRuns(
                MarketplaceOfferSnapshot.published("pim-GONE", 80L, 2L, "5900000000011", "MFN-GONE"));

        try (LogCapture logs = LogCapture.start()) {
            // when
            for (int run = 1; run <= 6; run++) {
                listener.handleMessage(request());
            }

            // then held while the shield lasts, reported once when it ends, exported under its own id afterwards
            assertThat(removedPerRun).containsExactly(
                    List.of(), List.of(), List.of(), List.of("pim-GONE"), List.of("pim-GONE"), List.of("pim-GONE"));
            assertThat(publishedPerRun).extracting(offers -> offers.stream().map(MarketplaceOffer::productId).toList())
                    .containsExactly(List.of(), List.of(), List.of(), List.of(), List.of("dead-D"), List.of("dead-D"));
            assertThat(logs.errors()).singleElement().satisfies(error -> assertThat(error)
                    .contains("dead-D").contains("pim-GONE"));
        }
    }

    @Test
    void aRemovedProductsOfferIsNotKeptAliveByADeadPinSharingItsEan() {
        // given pim-GONE left the catalog; the dead-pinned product carries its EAN and has its own previous offer
        Product product = product("dead-D", "5900000000011");
        configureCategoryWith(warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        when(aliases.knows(STORE_ID, "dead-D")).thenReturn(false);
        previousSnapshotContains(
                MarketplaceOfferSnapshot.published("dead-D", 100L, 10L, "5900000000011", "MFN-dead-D"),
                MarketplaceOfferSnapshot.published("pim-GONE", 80L, 2L, "5900000000011", "MFN-GONE"));

        // when
        listener.handleMessage(request());

        // then
        assertThat(captureRemovedOffers()).extracting(MarketplaceOffer::productId).containsExactly("pim-GONE");
        assertThat(capturePublishedOffers()).extracting(MarketplaceOffer::productId).containsExactly("dead-D");
        assertThat(captureSavedRows()).allSatisfy(row -> assertThat(row.waitingRuns()).isZero());
    }

    @Test
    void aProductUnknownForSeveralRunsStopsShieldingAndIsReported() {
        // given a product on an entry no index copy ever learns, with no previous offer under its own id (so the copy
        // cannot tell a fresh clone from a dead pin): its codes keep the offer of its source for a bounded time only
        Product product = product("dead-D", "5900000000011");
        configureCategoryWith(warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        when(aliases.knows(STORE_ID, "dead-D")).thenReturn(false);
        List<List<String>> removedPerRun = chainRuns(
                MarketplaceOfferSnapshot.published("source-S", 100L, 10L, "5900000000011", "MFN-S"));

        try (LogCapture logs = LogCapture.start()) {
            // when
            for (int run = 1; run <= 5; run++) {
                listener.handleMessage(request());
            }

            // then three runs keep the offer, the fourth takes it off and reports it once, the fifth retries the removal
            assertThat(removedPerRun).containsExactly(List.of(), List.of(), List.of(), List.of("source-S"), List.of("source-S"));
            assertThat(logs.errors()).singleElement().satisfies(error -> assertThat(error)
                    .contains(STORE_ID).contains(product.getProductId()).contains("dead-D").contains("source-S")
                    .contains("5900000000011"));
        }
    }

    @Test
    void aProductKnownAgainIsPublishedUnderItsOfferIdAndTheWaitingCounterIsGone() {
        // given the offer waited two runs; the copy now knows the clone
        Product product = product("clone-C", "5900000000011");
        configureCategoryWith(warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        when(aliases.offerIdOf(STORE_ID, "clone-C")).thenReturn("source-S");
        previousSnapshotContains(MarketplaceOfferSnapshot.published("source-S", 100L, 10L, "5900000000011", "MFN-S")
                .withWaitingRuns(2));

        // when
        listener.handleMessage(request());

        // then
        assertThat(captureRemovedOffers()).isEmpty();
        assertThat(capturePublishedOffers()).extracting(MarketplaceOffer::productId).containsExactly("source-S");
        assertThat(captureSavedRows()).singleElement().satisfies(row -> {
            assertThat(row.pimId()).isEqualTo("source-S");
            assertThat(row.outcome()).isEqualTo(MarketplaceOfferSnapshot.OUTCOME_PUBLISHED);
            assertThat(row.waitingRuns()).isZero();
        });
    }

    @Test
    void aSkippedProductWithoutEanShieldsAPreviousOfferWithoutEanByManufacturerCode() {
        // given
        Product product = product("clone-C", null);
        configureCategoryWith(warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        when(aliases.knows(STORE_ID, "clone-C")).thenReturn(false);
        MarketplaceOfferSnapshot withoutEan = MarketplaceOfferSnapshot.published("source-S", 100L, 10L, null, " mfn-clone-c ");
        previousSnapshotContains(withoutEan,
                // a snapshot with an EAN is matched by its EAN only, even when the manufacturer code is the same
                MarketplaceOfferSnapshot.published("pim-Y", 100L, 10L, "5900000000099", "MFN-clone-C"));

        // when
        listener.handleMessage(request());

        // then
        assertThat(captureRemovedOffers()).extracting(MarketplaceOffer::productId).containsExactly("pim-Y");
        assertThat(captureSavedRows()).contains(withoutEan.withWaitingRuns(1));
    }

    @Test
    void aRemovedProductSharingAnEanWithASkippedProductIsKeptUntilTheShieldExpires() {
        // given pim-GONE really left the catalog, but a skipped product carries its EAN: the accepted cost of the shield
        Product product = product("clone-C", "5900000000011");
        configureCategoryWith(warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        when(aliases.knows(STORE_ID, "clone-C")).thenReturn(false);
        List<List<String>> removedPerRun = chainRuns(
                MarketplaceOfferSnapshot.published("pim-GONE", 80L, 2L, "5900000000011", "MFN-GONE"));

        // when
        for (int run = 1; run <= 4; run++) {
            listener.handleMessage(request());
        }

        // then
        assertThat(removedPerRun).containsExactly(List.of(), List.of(), List.of(), List.of("pim-GONE"));
    }

    @Test
    void stillUnpublishesAPreviousOfferWhoseEanIsInNoCurrentOffer() {
        // given
        Product product = product("pim-B", "EAN-B");
        configureCategoryWith(warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        previousSnapshotContains(MarketplaceOfferSnapshot.published("source-S", 100L, 10L, "EAN-A", "MFN-source-S"));

        // when
        listener.handleMessage(request());

        // then
        assertThat(captureRemovedOffers()).extracting(MarketplaceOffer::productId).containsExactly("source-S");
    }

    @Test
    void exportsOneOfferPerOfferIdTheFirstProductWinsOnEqualStock() {
        // given a product still on S in category A and the store's clone of S in category B
        Product onSource = product("source-S", "EAN-A");
        Product onClone = product("clone-C", "EAN-A");
        CategoryDefinition categoryA = category("A", null, List.of(warehouseDefinition(5)));
        categoryA.setCategoryId("cat-A");
        CategoryDefinition categoryB = category("B", null, List.of(warehouseDefinition(5)));
        categoryB.setCategoryId("cat-B");
        when(catalog.getCategories()).thenReturn(List.of(categoryA, categoryB));
        when(productRepository.findAllProductsWithPimId("cat-A", true)).thenReturn(List.of(onSource));
        when(productRepository.findAllProductsWithPimId("cat-B", true)).thenReturn(List.of(onClone));
        MatchedInventory matched = mockMatchedInventoryWithWarehouseQuantity(10);
        when(inventoryView.findByProduct(onSource)).thenReturn(matched);
        when(inventoryView.findByProduct(onClone)).thenReturn(matched);
        priceFor(onSource, 100, 2);
        priceFor(onClone, 110, 2);
        when(aliases.offerIdOf(STORE_ID, "clone-C")).thenReturn("source-S");

        // when
        listener.handleMessage(request());

        // then
        assertThat(capturePublishedOffers()).singleElement().satisfies(offer -> {
            assertThat(offer.productId()).isEqualTo("source-S");
            assertThat(offer.price()).isEqualTo(100L);
        });
    }

    @Test
    void exportsOneOfferPerOfferIdPreferringTheProductWithMoreStock() {
        // given the product on S has no stock left; the product on the store's clone of S has some
        Product onSource = product("source-S", "EAN-A");
        Product onClone = product("clone-C", "EAN-A");
        CategoryDefinition categoryA = category("A", null, List.of(warehouseDefinition(1)));
        categoryA.setCategoryId("cat-A");
        CategoryDefinition categoryB = category("B", null, List.of(warehouseDefinition(1)));
        categoryB.setCategoryId("cat-B");
        when(catalog.getCategories()).thenReturn(List.of(categoryA, categoryB));
        when(productRepository.findAllProductsWithPimId("cat-A", true)).thenReturn(List.of(onSource));
        when(productRepository.findAllProductsWithPimId("cat-B", true)).thenReturn(List.of(onClone));
        MatchedInventory empty = mockMatchedInventoryWithWarehouseQuantity(0);
        MatchedInventory inStock = mockMatchedInventoryWithWarehouseQuantity(7);
        when(inventoryView.findByProduct(onSource)).thenReturn(empty);
        when(inventoryView.findByProduct(onClone)).thenReturn(inStock);
        priceFor(onSource, 100, 2);
        priceFor(onClone, 110, 2);
        when(aliases.offerIdOf(STORE_ID, "clone-C")).thenReturn("source-S");

        // when
        listener.handleMessage(request());

        // then
        assertThat(capturePublishedOffers()).singleElement().satisfies(offer -> {
            assertThat(offer.productId()).isEqualTo("source-S");
            assertThat(offer.quantity()).isEqualTo(7L);
            assertThat(offer.price()).isEqualTo(110L);
        });
    }

    @Test
    void aProductAbsentFromThePricelistDoesNotTakeAnotherProductsRowByCodes() {
        // given pim-B has no row; its EAN and MFN are on the row of pim-A, a product of this export with its own row
        Product absent = product("pim-B", "EAN-A");
        Product other = product("pim-A", "EAN-A");
        ReflectionTestUtils.setField(absent, "manufacturerCode", other.getManufacturerCode());
        when(catalog.getCategories()).thenReturn(List.of(category("A", null, List.of(warehouseDefinition(5)))));
        when(productRepository.findAllProductsWithPimId(CATEGORY_ID, true)).thenReturn(List.of(absent, other));
        MatchedInventory matched = mockMatchedInventoryWithWarehouseQuantity(10);
        when(inventoryView.findByProduct(absent)).thenReturn(matched);
        when(inventoryView.findByProduct(other)).thenReturn(matched);
        usePricelist(new Pricelist(PRICELIST_ID, List.of(row("pim-A", other, 100))));

        // when
        listener.handleMessage(request());

        // then
        assertThat(capturePublishedOffers()).extracting(MarketplaceOffer::productId).containsExactly("pim-A");
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
    void sendsTheEanAndManufacturerCodeItWasPublishedWithWhenTakingTheOfferOff() {
        // given
        Product product = product("pim-A", "EAN-A");
        configureCategoryWith(warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        listener.handleMessage(request());
        List<MarketplaceOfferSnapshot> published = captureSavedRows();
        reset(marketplaceExportRunService, provider);
        previousSnapshotContains(published.toArray(new MarketplaceOfferSnapshot[0]));
        noCategoriesConfigured();

        // when
        listener.handleMessage(request());

        // then
        List<MarketplaceOffer> removed = captureRemovedOffers();
        assertThat(removed).hasSize(1);
        assertThat(removed.get(0).productId()).isEqualTo("pim-A");
        assertThat(removed.get(0).ean()).isEqualTo("EAN-A");
        assertThat(removed.get(0).manufacturerCode()).isEqualTo("MFN-PIM-A");
        assertThat(removed.get(0).quantity()).isZero();
    }

    @Test
    void keepsTheEanOnTheRemovalPendingRowForTheNextAttempt() {
        // given
        noCategoriesConfigured();
        previousSnapshotContains(MarketplaceOfferSnapshot.published("pim-X", 999L, 5L, "EAN-X", "MFN-X"));

        // when
        listener.handleMessage(request());

        // then
        List<MarketplaceOfferSnapshot> saved = captureSavedRows();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).ean()).isEqualTo("EAN-X");
        assertThat(saved.get(0).manufacturerCode()).isEqualTo("MFN-X");
    }

    @Test
    void marksTheRemovalAsFailedWhenTheMarketplaceRefusedTheLastAttempt() {
        // given
        noCategoriesConfigured();
        previousSnapshotContains(
                MarketplaceOfferSnapshot.removalPending("pim-X", 999L, 3, "EAN-X", "MFN-X")
                        .rejected("NOT_FOUND", "no such offer"));

        // when
        listener.handleMessage(request());

        // then
        verify(provider, never()).exportOffers(any(), any(), any());
        List<MarketplaceOfferSnapshot> saved = captureSavedRows();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).pimId()).isEqualTo("pim-X");
        assertThat(saved.get(0).outcome()).isEqualTo(MarketplaceOfferSnapshot.OUTCOME_REMOVAL_FAILED);
        assertThat(saved.get(0).removalAttempts()).isEqualTo(3);
        assertThat(saved.get(0).reasonCode()).isEqualTo("NOT_FOUND");
        assertThat(saved.get(0).ean()).isEqualTo("EAN-X");
    }

    @Test
    void doesNotCallARejectedPublicationAFailedRemovalWhenRemovalIsSwitchedOff() {
        // given
        ReflectionTestUtils.setField(listener, "removalRetryCount", 0);
        noCategoriesConfigured();
        previousSnapshotContains(
                MarketplaceOfferSnapshot.published("pim-X", 999L, 5L).rejected("VALIDATION", "title too short"));

        // when
        listener.handleMessage(request());

        // then
        verify(provider, never()).exportOffers(any(), any(), any());
        assertThat(captureSavedRows()).isEmpty();
    }

    @Test
    void forgetsAFailedRemovalOnTheRunAfterItWasRecorded() {
        // given
        noCategoriesConfigured();
        previousSnapshotContains(MarketplaceOfferSnapshot.removalFailed(
                "pim-X", 999L, 3, "NOT_FOUND", "no such offer", "EAN-X", "MFN-X"));

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

    private void usePricelist(Pricelist real) {
        when(pricelistRepository.find(STORE_ID, CATALOG_ID, PRICELIST_ID)).thenReturn(real);
    }

    private AvailabilityAndPrice row(String pimId, Product product, long price) {
        return new AvailabilityAndPrice(pimId, product.getEan(), product.getManufacturerCode(), "Brand", "Label",
                "Name", "Laptops", price, 0L, 2, 0L, false);
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

    /**
     * Feeds every saved run back as the previous export of the next one, starting with {@code initial}; returns the
     * offer ids taken off in each run (an empty list for a run that did not call the marketplace).
     */
    private List<List<String>> chainRuns(MarketplaceOfferSnapshot... initial) {
        AtomicReference<List<MarketplaceOfferSnapshot>> previous = new AtomicReference<>(List.of(initial));
        List<List<String>> removedPerRun = new ArrayList<>();
        when(marketplaceExportRunService.loadPreviousExport(STORE_ID, CATALOG_ID, MARKETPLACE))
                .thenAnswer(invocation -> {
                    removedPerRun.add(new ArrayList<>());
                    publishedPerRun.add(new ArrayList<>());
                    return previous.get();
                });
        doAnswer(invocation -> {
            List<MarketplaceOffer> removals = invocation.getArgument(1);
            removedPerRun.get(removedPerRun.size() - 1).addAll(removals.stream().map(MarketplaceOffer::productId).toList());
            publishedPerRun.get(publishedPerRun.size() - 1).addAll(invocation.getArgument(0));
            return null;
        }).when(provider).exportOffers(any(), any(), any());
        doAnswer(invocation -> {
            previous.set(invocation.getArgument(0, MarketplaceExportRun.class).toRows());
            return null;
        }).when(marketplaceExportRunService).saveRun(any());
        return removedPerRun;
    }

    private static final class LogCapture implements AutoCloseable {

        private final Logger logger = (Logger) LoggerFactory.getLogger(MarketplaceOfferExportEventListener.class);
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        static LogCapture start() {
            LogCapture capture = new LogCapture();
            capture.appender.start();
            capture.logger.addAppender(capture.appender);
            return capture;
        }

        List<String> errors() {
            return messagesAt(Level.ERROR);
        }

        List<String> warnings() {
            return messagesAt(Level.WARN);
        }

        private List<String> messagesAt(Level level) {
            return appender.list.stream().filter(event -> event.getLevel() == level)
                    .map(ILoggingEvent::getFormattedMessage).toList();
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
        }
    }

    private void noCategoriesConfigured() {
        when(catalog.getCategories()).thenReturn(List.of());
    }

    @Test
    void exportsNothingForInactiveStore() {
        // given
        when(storeActivity.isActive(store)).thenReturn(false);

        // when
        listener.handleMessage(request());

        // then
        verifyNoInteractions(provider, marketplaceExportRunService, inventory);
    }
}
