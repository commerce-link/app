package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.pim.api.PimView;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.supplier.StoreFeedItemLoader;
import pl.commercelink.inventory.supplier.SupplierProviderFactory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreInventoryProviderTest {

    @Mock
    private StoreInventoryCache cache;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private SupplierRegistry supplierRegistry;
    @Mock
    private SupplierProviderFactory supplierProviderFactory;
    @Mock
    private InventoryAutoDiscovery autoDiscovery;
    @Mock
    private StoreFeedItemLoader storeFeedItemLoader;
    @Mock
    private ExchangeRates exchangeRates;
    @Mock
    private pl.commercelink.pim.api.PimCatalog pimCatalog;
    @Mock
    private PimView pimView;
    @Mock
    private GlobalMatchedInventory globalInventory;
    @Mock
    private TaxonomyCache taxonomyCache;
    @Spy
    private InventoryMatchingProperties matchingProperties = InventoryMatchingProperties.allEnabled();

    @InjectMocks
    private StoreInventoryProvider provider;

    private InventoryItem item(String supplier) {
        return new InventoryItem("4711111111111", "MFN", 10.0, "PLN", 1, 1, supplier, true, true, false);
    }

    private SupplierInfo supplierInfo(String name) {
        return new SupplierInfo(name, SupplierType.Distributor, 1, "PL",
                new ShippingPolicy(new ShippingTerms(1, new ShippingCostPolicy.Free())));
    }

    @Test
    void returnsCachedEntryWithoutBuilding() {
        // given
        StoreInventory cached = new StoreInventory(InventoryIndex.of(new LinkedList<>()), LocalDateTime.now());
        when(cache.get("store-1")).thenReturn(Optional.of(cached));

        // when / then
        assertSame(cached, provider.get("store-1"));
        verify(cache, never()).put(any(), any(), any(), anyLong());
    }

    @Test
    void buildsFromOwnItemsThenStores() {
        // given
        when(cache.get("store-1")).thenReturn(Optional.empty());
        Store storeEntity = mock(Store.class);
        when(storesRepository.findById("store-1")).thenReturn(storeEntity);
        when(storeEntity.getOwnAndManualConnections())
                .thenReturn(List.of(new StoreSupplierConnection("Wortmann", ConnectionMode.OWN)));
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of("PLN", 1.0));
        SupplierProviderDescriptor descriptor = mock(SupplierProviderDescriptor.class);
        when(descriptor.supplierInfo()).thenReturn(supplierInfo("Wortmann"));
        when(supplierProviderFactory.getDescriptor("Wortmann")).thenReturn(descriptor);
        when(storeFeedItemLoader.load(eq("store-1"), eq("Wortmann"), same(descriptor), any()))
                .thenReturn(List.of(item("Wortmann")));
        MatchedInventory matched = mock(MatchedInventory.class);
        when(matched.getInventoryKey()).thenReturn(new InventoryKey());
        when(autoDiscovery.group(anyList(), any(), any(), any())).thenReturn(grouping(List.of(matched)));

        // when
        StoreInventory result = provider.get("store-1");

        // then
        assertEquals(List.of(matched), result.items());
        verify(cache).put(eq("store-1"), any(StoreInventory.class), any(Duration.class), anyLong());
        verify(autoDiscovery).group(argThat(list -> list.size() == 1), any(), any(), any());
    }

    @Test
    void refreshRebuildsAndOverwritesWithoutReadingTheSnapshot() {
        // given
        Store storeEntity = mock(Store.class);
        when(storesRepository.findById("store-1")).thenReturn(storeEntity);
        when(storeEntity.getInventoryCacheTtlMinutes()).thenReturn(Optional.of(30));
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of());
        when(autoDiscovery.group(anyList(), any(), any(), any())).thenReturn(grouping(List.of()));
        when(cache.builtSince("store-1", 1_000L)).thenReturn(false);

        // when
        provider.refresh("store-1", 1_000L);

        // then
        verify(cache, never()).get(any());
        verify(cache).put(eq("store-1"), any(StoreInventory.class), eq(Duration.ofMinutes(30)), anyLong());
    }

    @Test
    void requestAnsweredByALaterBuildRebuildsNothing() {
        // given
        when(cache.builtSince("store-1", 1_000L)).thenReturn(true);

        // when
        provider.refresh("store-1", 1_000L);

        // then
        verify(storesRepository, never()).findById(any());
        verify(cache, never()).put(any(), any(), any(), anyLong());
    }

    @Test
    void requestWithoutATimeAlwaysRebuilds() {
        // given
        Store storeEntity = mock(Store.class);
        when(storesRepository.findById("store-1")).thenReturn(storeEntity);
        when(storeEntity.getInventoryCacheTtlMinutes()).thenReturn(Optional.empty());
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of());
        when(autoDiscovery.group(anyList(), any(), any(), any())).thenReturn(grouping(List.of()));

        // when
        provider.refresh("store-1", null);

        // then
        verify(cache, never()).builtSince(any(), anyLong());
        verify(cache).put(eq("store-1"), any(StoreInventory.class), any(Duration.class), anyLong());
    }

    @Test
    void refreshOfAStoreThatIsGoneStoresNothing() {
        // given
        when(storesRepository.findById("store-1")).thenReturn(null);

        // when
        provider.refresh("store-1", null);

        // then
        verify(cache, never()).put(any(), any(), any(), anyLong());
    }

    @Test
    void buildIsTimedBeforeTheFeedsAreRead() {
        // given
        when(cache.get("store-1")).thenReturn(Optional.empty());
        when(cache.clockMillis()).thenReturn(4_242L);
        Store storeEntity = mock(Store.class);
        when(storesRepository.findById("store-1")).thenReturn(storeEntity);
        when(storeEntity.getInventoryCacheTtlMinutes()).thenReturn(Optional.empty());
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of());
        when(autoDiscovery.group(anyList(), any(), any(), any())).thenReturn(grouping(List.of()));

        // when
        provider.get("store-1");

        // then
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(cache, exchangeRates);
        order.verify(cache).clockMillis();
        order.verify(exchangeRates).getCurrentSellRates();
        order.verify(cache).put(eq("store-1"), any(StoreInventory.class), any(Duration.class), eq(4_242L));
    }

    @Test
    void buildsWithEmptyListWhenNoOwnSuppliers() {
        // given
        when(cache.get("store-1")).thenReturn(Optional.empty());
        Store storeEntity = mock(Store.class);
        when(storesRepository.findById("store-1")).thenReturn(storeEntity);
        when(storeEntity.getOwnSupplierNames()).thenReturn(List.of());
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of("PLN", 1.0));
        when(autoDiscovery.group(anyList(), any(), any(), any())).thenReturn(grouping(List.of()));

        // when
        provider.get("store-1");

        // then
        verify(autoDiscovery).group(argThat(List::isEmpty), any(), any(), any());
    }

    @Test
    void usesStoreTtlWhenConfigured() {
        // given
        when(cache.get("store-1")).thenReturn(Optional.empty());
        Store storeEntity = mock(Store.class);
        when(storesRepository.findById("store-1")).thenReturn(storeEntity);
        when(storeEntity.getOwnSupplierNames()).thenReturn(List.of());
        when(storeEntity.getInventoryCacheTtlMinutes()).thenReturn(Optional.of(30));
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of());
        when(autoDiscovery.group(anyList(), any(), any(), any())).thenReturn(grouping(List.of()));

        // when
        provider.get("store-1");

        // then
        verify(cache).put(eq("store-1"), any(StoreInventory.class), eq(Duration.ofMinutes(30)), anyLong());
    }

    @Test
    void usesDefaultTtlWhenStoreHasNone() {
        // given
        provider.defaultTtlMinutes = 60;
        when(cache.get("store-1")).thenReturn(Optional.empty());
        Store storeEntity = mock(Store.class);
        when(storesRepository.findById("store-1")).thenReturn(storeEntity);
        when(storeEntity.getOwnSupplierNames()).thenReturn(List.of());
        when(storeEntity.getInventoryCacheTtlMinutes()).thenReturn(Optional.empty());
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of());
        when(autoDiscovery.group(anyList(), any(), any(), any())).thenReturn(grouping(List.of()));

        // when
        provider.get("store-1");

        // then
        verify(cache).put(eq("store-1"), any(StoreInventory.class), eq(Duration.ofMinutes(60)), anyLong());
    }

    @Test
    void usesDefaultTtlWhenStoreTtlIsZeroOrNegative() {
        // given
        provider.defaultTtlMinutes = 60;
        when(cache.get("store-1")).thenReturn(Optional.empty());
        Store storeEntity = mock(Store.class);
        when(storesRepository.findById("store-1")).thenReturn(storeEntity);
        when(storeEntity.getOwnSupplierNames()).thenReturn(List.of());
        when(storeEntity.getInventoryCacheTtlMinutes()).thenReturn(Optional.of(0));
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of());
        when(autoDiscovery.group(anyList(), any(), any(), any())).thenReturn(grouping(List.of()));

        // when
        provider.get("store-1");

        // then
        verify(cache).put(eq("store-1"), any(StoreInventory.class), eq(Duration.ofMinutes(60)), anyLong());
    }

    @Test
    void buildsEmptyInventoryWhenStoreMissing() {
        // given
        when(cache.get("store-1")).thenReturn(Optional.empty());
        when(storesRepository.findById("store-1")).thenReturn(null);

        // when
        StoreInventory result = provider.get("store-1");

        // then
        assertEquals(0, result.items().size());
        verify(cache).put(eq("store-1"), any(StoreInventory.class), any(Duration.class), anyLong());
    }

    @Test
    void ownIndexReturnsEmptyIndexWhenStoreHasNoOwnConnections() {
        // given
        Store store = mock(Store.class);
        when(store.hasOwnSupplierConnections()).thenReturn(false);

        // when
        InventoryIndex result = provider.ownIndex(store);

        // then
        assertThat(result.all()).isEmpty();
        verify(cache, never()).get(any());
    }

    @Test
    void ownIndexReturnsEmptyIndexWhenStoreIsNull() {
        // when
        InventoryIndex result = provider.ownIndex(null);

        // then
        assertThat(result.all()).isEmpty();
        verify(cache, never()).get(any());
        verify(cache, never()).put(any(), any(), any(), anyLong());
    }

    @Test
    void ownIndexReturnsCachedIndexWhenStoreHasOwnConnections() {
        // given
        Store store = mock(Store.class);
        when(store.getStoreId()).thenReturn("store-1");
        when(store.hasOwnOrManualSupplierConnections()).thenReturn(true);
        MatchedInventory matched = new MatchedInventory(new InventoryKey("E1", "M1"), List.of(), taxonomyCache, supplierRegistry);
        when(cache.get("store-1")).thenReturn(Optional.of(new StoreInventory(InventoryIndex.of(List.of(matched)), LocalDateTime.now())));

        // when
        InventoryIndex result = provider.ownIndex(store);

        // then
        assertThat(result.findMatching(InventoryKey.fromEan("E1"))).containsExactly(matched);
        verify(storesRepository, never()).findById(any());
    }

    @Test
    void buildLoadsOnlyOwnSuppliersAndIgnoresGlobal() {
        // given
        Store store = mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(List.of("Asbis"));
        when(store.getOwnAndManualConnections())
                .thenReturn(List.of(new StoreSupplierConnection("Action", ConnectionMode.OWN)));
        when(store.getInventoryCacheTtlMinutes()).thenReturn(Optional.empty());
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(cache.get("store-1")).thenReturn(Optional.empty());
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of());
        SupplierProviderDescriptor descriptor = mock(SupplierProviderDescriptor.class);
        when(descriptor.supplierInfo()).thenReturn(supplierInfo("Action"));
        when(supplierProviderFactory.getDescriptor("Action")).thenReturn(descriptor);
        InventoryItem ownItem = new InventoryItem("111", "AAA", 100.0, "PLN", 5, 2, "Action", true, true, false);
        when(storeFeedItemLoader.load(eq("store-1"), eq("Action"), same(descriptor), anyMap()))
                .thenReturn(List.of(ownItem));
        when(autoDiscovery.group(anyList(), any(), any(), any())).thenAnswer(inv -> {
            List<InventoryItem> items = inv.getArgument(0);
            return grouping(List.of(new MatchedInventory(new InventoryKey("111", "AAA"), items, taxonomyCache, supplierRegistry)));
        });

        // when
        StoreInventory result = provider.get("store-1");

        // then
        List<String> loadedSuppliers = result.items().stream()
                .flatMap(m -> m.getSuppliers().stream())
                .toList();
        assertThat(loadedSuppliers).containsExactly("Action");
    }

    @Test
    void globalOffersWitnessTheStoreGrouping() {
        // given
        when(cache.get("store-1")).thenReturn(Optional.empty());
        Store storeEntity = mock(Store.class);
        when(storesRepository.findById("store-1")).thenReturn(storeEntity);
        when(storeEntity.getOwnAndManualConnections()).thenReturn(List.of());
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of("PLN", 1.0));
        InventoryItem globalOffer = item("Asbis");
        MatchedInventory globalGroup = new MatchedInventory(new InventoryKey("4711111111111", "MFN"), List.of(globalOffer), taxonomyCache, supplierRegistry);
        when(globalInventory.generation()).thenReturn(
                new GlobalMatchedInventory.Generation(1, List.of(globalGroup), InventoryIndex.of(List.of(globalGroup)), VariantCodes.none()));
        when(autoDiscovery.group(anyList(), any(), any(), any())).thenReturn(grouping(List.of()));

        // when
        provider.get("store-1");

        // then
        verify(autoDiscovery).group(anyList(), any(), argThat(witnesses -> List.copyOf(witnesses.get()).equals(List.of(globalOffer))), any());
        verify(globalInventory, never()).allItems();
    }

    @Test
    void storeGroupingInheritsGlobalVariantCodes() {
        // given
        when(cache.get("store-1")).thenReturn(Optional.empty());
        Store storeEntity = mock(Store.class);
        when(storesRepository.findById("store-1")).thenReturn(storeEntity);
        when(storeEntity.getOwnAndManualConnections()).thenReturn(List.of());
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of("PLN", 1.0));
        VariantCodes globalCodes = SharedManufacturerCodes.of(List.of(
                new InventoryItem("Nike", null, "5901234567801", "AIR1", "X", 10.0, "PLN", 1, 1, true, false, false),
                new InventoryItem("Nike", null, "5901234567802", "AIR1", "X", 10.0, "PLN", 1, 1, true, false, false)))
                .variantCodes();
        when(globalInventory.generation()).thenReturn(
                new GlobalMatchedInventory.Generation(3, List.of(), InventoryIndex.of(List.of()), globalCodes));
        when(autoDiscovery.group(anyList(), any(), any(), any())).thenReturn(grouping(List.of()));

        // when
        provider.get("store-1");

        // then
        verify(autoDiscovery).group(anyList(), any(), any(), same(globalCodes));
        verify(globalInventory, times(1)).generation();
    }

    private static final String GLOBAL_EAN = "8720689010825";
    private static final String OWN_EAN = "8720689021180";

    @Test
    void linksOwnGroupsToGlobalOnesWhenEnabled() {
        // given
        MatchedInventory global = philipsGlobal();
        storeSellingOwnOffers(philipsOwnOffers());
        globalPoolOf(List.of(global));
        when(autoDiscovery.group(anyList(), any(), any(), any())).thenAnswer(inv -> grouping(List.of(groupOf(inv.getArgument(0)))));

        // when
        StoreInventory result = provider.get("store-1");

        // then
        assertThat(result.index().all()).singleElement().satisfies(own ->
                assertThat(own.getInventoryKey().getLinkedEans()).containsExactly(GLOBAL_EAN));
        assertThat(result.index().findMatching(global.getInventoryKey())).hasSize(1);
    }

    @Test
    void doesNotLinkWhenTheSwitchIsOff() {
        // given
        StoreInventoryProvider withoutLinks = providerWith(autoDiscovery, new InventoryMatchingProperties(true, true, true, false));
        MatchedInventory global = philipsGlobal();
        storeSellingOwnOffers(philipsOwnOffers());
        globalPoolOf(List.of(global));
        when(autoDiscovery.group(anyList(), any(), any(), any())).thenAnswer(inv -> grouping(List.of(groupOf(inv.getArgument(0)))));

        // when
        StoreInventory result = withoutLinks.get("store-1");

        // then
        assertThat(result.index().all()).singleElement().satisfies(own ->
                assertThat(own.getInventoryKey().getLinkedEans()).isEmpty());
        assertThat(result.index().findMatching(global.getInventoryKey())).isEmpty();
    }

    @Test
    void ownOfferRejectedAsConflictingIsInNoLinkedGroup() {
        // given: the own Elko offer lists the code of the 7600 under the EAN of the 9060, both confirmed globally
        String ean7600 = "4895106293618";
        String ean9060 = "4895106296381";
        when(pimView.findAll()).thenReturn(List.of());
        InventoryAutoDiscovery realDiscovery = new InventoryAutoDiscovery(pimCatalog, taxonomyCache, supplierRegistry,
                InventoryMatchingProperties.allEnabled());
        InventoryAutoDiscovery.Grouping globalGrouping = realDiscovery.group(List.of(
                sapphire(ean7600, "11324-01-20G", 1843, "Kosatec"),
                sapphire(ean7600, "11324-01-20G", 1810, "Morele"),
                sapphire(ean9060, "11350-03-20G", 2613, "Kosatec"),
                sapphire(ean9060, "11350-03-20G", 2663, "IncomGroup")), pimView, List::of, VariantCodes.none());
        List<MatchedInventory> globalGroups = List.copyOf(globalGrouping.groups());
        when(globalInventory.generation()).thenReturn(new GlobalMatchedInventory.Generation(
                1, globalGroups, InventoryIndex.of(globalGroups), globalGrouping.variantCodes()));
        InventoryItem ownAction = sapphire("4895106299993", "11324-01-20G", 1850, "Action");
        InventoryItem ownElko = sapphire(ean9060, "11324-01-20G", 3563, "Elko");
        storeSellingOwnOffers(List.of(ownAction, ownElko));

        // when
        StoreInventory result = providerWith(realDiscovery, InventoryMatchingProperties.allEnabled()).get("store-1");

        // then
        assertThat(result.index().all()).flatExtracting(MatchedInventory::getInventoryItems).containsExactly(ownAction);
        assertThat(result.index().all()).singleElement().satisfies(own ->
                assertThat(own.getInventoryKey().getLinkedEans()).containsExactly(ean7600));
    }

    @Test
    void linkedInventorySurvivesTheSnapshotRoundTrip() {
        // given
        MatchedInventory global = philipsGlobal();
        InventoryIndex globalIndex = InventoryIndex.of(List.of(global));
        storeSellingOwnOffers(philipsOwnOffers());
        globalPoolOf(List.of(global));
        when(autoDiscovery.group(anyList(), any(), any(), any())).thenAnswer(inv -> grouping(List.of(groupOf(inv.getArgument(0)))));
        StoreInventory built = provider.get("store-1");

        // when
        StoreInventory restored = StoreInventorySnapshot.from(built).toStoreInventory(taxonomyCache, supplierRegistry);

        // then
        assertThat(restored.index().all()).singleElement().satisfies(own ->
                assertThat(globalIndex.covers(own.getInventoryKey(), StoreEntryKeys.none())).isTrue());
    }

    @Test
    void withTheLinkSwitchOffTheBuildNeverAsksForTheGlobalIndex() {
        // given
        StoreInventoryProvider withoutLinks = providerWith(autoDiscovery, new InventoryMatchingProperties(true, true, true, false));
        AtomicInteger indexRequests = new AtomicInteger();
        globalPoolCountingIndexRequests(List.of(philipsGlobal()), indexRequests);
        storeSellingOwnOffers(philipsOwnOffers());
        when(autoDiscovery.group(anyList(), any(), any(), any())).thenAnswer(inv -> grouping(List.of(groupOf(inv.getArgument(0)))));

        // when
        withoutLinks.get("store-1");

        // then
        assertThat(indexRequests).hasValue(0);
    }

    @Test
    void globalIndexIsFirstAskedForAfterGrouping() {
        // given
        AtomicInteger indexRequests = new AtomicInteger();
        AtomicInteger requestsWhileGrouping = new AtomicInteger(-1);
        globalPoolCountingIndexRequests(List.of(philipsGlobal()), indexRequests);
        storeSellingOwnOffers(philipsOwnOffers());
        when(autoDiscovery.group(anyList(), any(), any(), any())).thenAnswer(inv -> {
            requestsWhileGrouping.set(indexRequests.get());
            return grouping(List.of(groupOf(inv.getArgument(0))));
        });

        // when
        provider.get("store-1");

        // then
        assertThat(requestsWhileGrouping).hasValue(0);
        assertThat(indexRequests.get()).isPositive();
    }

    private void globalPoolCountingIndexRequests(List<MatchedInventory> groups, AtomicInteger indexRequests) {
        when(globalInventory.generation()).thenReturn(new GlobalMatchedInventory.Generation(1, groups, () -> {
            indexRequests.incrementAndGet();
            return InventoryIndex.of(groups);
        }, VariantCodes.none()));
    }

    private StoreInventoryProvider providerWith(InventoryAutoDiscovery discovery, InventoryMatchingProperties properties) {
        return new StoreInventoryProvider(cache, storesRepository, supplierProviderFactory, discovery,
                storeFeedItemLoader, exchangeRates, pimCatalog, globalInventory, properties);
    }

    private void storeSellingOwnOffers(List<InventoryItem> offers) {
        when(cache.get("store-1")).thenReturn(Optional.empty());
        Store storeEntity = mock(Store.class);
        when(storesRepository.findById("store-1")).thenReturn(storeEntity);
        when(storeEntity.getOwnAndManualConnections())
                .thenReturn(List.of(new StoreSupplierConnection("Action", ConnectionMode.OWN)));
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of("PLN", 1.0));
        SupplierProviderDescriptor descriptor = mock(SupplierProviderDescriptor.class);
        when(descriptor.supplierInfo()).thenReturn(supplierInfo("Action"));
        when(supplierProviderFactory.getDescriptor("Action")).thenReturn(descriptor);
        when(storeFeedItemLoader.load(eq("store-1"), eq("Action"), same(descriptor), any())).thenReturn(offers);
    }

    private void globalPoolOf(List<MatchedInventory> groups) {
        when(globalInventory.generation()).thenReturn(
                new GlobalMatchedInventory.Generation(1, groups, InventoryIndex.of(groups), VariantCodes.none()));
    }

    private MatchedInventory philipsGlobal() {
        return groupOf(List.of(philips(GLOBAL_EAN, 107.11, "MxSolution")));
    }

    private static List<InventoryItem> philipsOwnOffers() {
        return List.of(philips(OWN_EAN, 75.00, "Action"), philips(OWN_EAN, 65.76, "Elko"));
    }

    private MatchedInventory groupOf(List<InventoryItem> offers) {
        InventoryKey key = new InventoryKey();
        offers.forEach(offer -> {
            key.addEan(offer.ean());
            key.addManufacturerCode(offer.mfn());
        });
        return new MatchedInventory(key, offers, taxonomyCache, supplierRegistry);
    }

    private static InventoryItem philips(String ean, double netPrice, String supplier) {
        return new InventoryItem("Philips", null, ean, "QP1324/20", supplier, netPrice, "PLN", 5, 1, true, false, false);
    }

    private static InventoryItem sapphire(String ean, String mfn, double netPrice, String supplier) {
        return new InventoryItem("Sapphire", null, ean, mfn, supplier, netPrice, "PLN", 5, 1, true, false, false);
    }

    private static InventoryAutoDiscovery.Grouping grouping(List<MatchedInventory> groups) {
        return new InventoryAutoDiscovery.Grouping(groups, VariantCodes.none());
    }

    @org.junit.jupiter.api.BeforeEach
    void pimStoreViewIsTheCatalog() {
        org.mockito.Mockito.lenient().when(globalInventory.generation()).thenReturn(
                new GlobalMatchedInventory.Generation(0, List.of(), InventoryIndex.of(List.of()), VariantCodes.none()));
        org.mockito.Mockito.lenient().when(pimCatalog.withStoreData(org.mockito.ArgumentMatchers.any())).thenReturn(pimView);
        org.mockito.Mockito.lenient().when(pimCatalog.withGlobalData()).thenReturn(pimView);
    }
}
