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
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
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
    private StoreEanOnlyCodes storeEanOnlyCodes;
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
        verify(cache, never()).put(any(), any(), any());
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
        when(storeFeedItemLoader.load(eq("store-1"), eq("Wortmann"), same(descriptor), any(), any()))
                .thenReturn(List.of(item("Wortmann")));
        MatchedInventory matched = mock(MatchedInventory.class);
        when(matched.getInventoryKey()).thenReturn(new InventoryKey());
        when(autoDiscovery.group(anyList(), any(), any(), any(), any())).thenReturn(grouping(List.of(matched)));

        // when
        StoreInventory result = provider.get("store-1");

        // then
        assertEquals(List.of(matched), result.items());
        verify(cache).put(eq("store-1"), any(StoreInventory.class), any(Duration.class));
        verify(autoDiscovery).group(argThat(list -> list.size() == 1), any(), any(), any(), any());
    }

    @Test
    void buildsWithEmptyListWhenNoOwnSuppliers() {
        // given
        when(cache.get("store-1")).thenReturn(Optional.empty());
        Store storeEntity = mock(Store.class);
        when(storesRepository.findById("store-1")).thenReturn(storeEntity);
        when(storeEntity.getOwnSupplierNames()).thenReturn(List.of());
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of("PLN", 1.0));
        when(autoDiscovery.group(anyList(), any(), any(), any(), any())).thenReturn(grouping(List.of()));

        // when
        provider.get("store-1");

        // then
        verify(autoDiscovery).group(argThat(List::isEmpty), any(), any(), any(), any());
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
        when(autoDiscovery.group(anyList(), any(), any(), any(), any())).thenReturn(grouping(List.of()));

        // when
        provider.get("store-1");

        // then
        verify(cache).put(eq("store-1"), any(StoreInventory.class), eq(Duration.ofMinutes(30)));
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
        when(autoDiscovery.group(anyList(), any(), any(), any(), any())).thenReturn(grouping(List.of()));

        // when
        provider.get("store-1");

        // then
        verify(cache).put(eq("store-1"), any(StoreInventory.class), eq(Duration.ofMinutes(60)));
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
        when(autoDiscovery.group(anyList(), any(), any(), any(), any())).thenReturn(grouping(List.of()));

        // when
        provider.get("store-1");

        // then
        verify(cache).put(eq("store-1"), any(StoreInventory.class), eq(Duration.ofMinutes(60)));
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
        verify(cache).put(eq("store-1"), any(StoreInventory.class), any(Duration.class));
    }

    @Test
    void emptyBuildOfAMissingStoreIsCachedDespiteItsPairs() {
        // given: PIM still holds a flagged entry of a store that no longer exists
        when(cache.get("store-1")).thenReturn(Optional.empty());
        when(storesRepository.findById("store-1")).thenReturn(null);
        when(storeEanOnlyCodes.of("store-1")).thenReturn(PimEanOnlyCodes.ofStore("store-1", List.of(storePhilips(true))));

        // when
        StoreInventory result = provider.get("store-1");

        // then
        verify(cache).put(eq("store-1"), same(result), any(Duration.class));
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
        verify(cache, never()).put(any(), any(), any());
    }

    @Test
    void ownIndexReturnsCachedIndexWhenStoreHasOwnConnections() {
        // given
        Store store = mock(Store.class);
        when(store.getStoreId()).thenReturn("store-1");
        when(store.hasOwnOrManualSupplierConnections()).thenReturn(true);
        MatchedInventory matched = new MatchedInventory(new InventoryKey("E1", "M1"), List.of(), supplierRegistry);
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
        when(storeFeedItemLoader.load(eq("store-1"), eq("Action"), same(descriptor), anyMap(), any()))
                .thenReturn(List.of(ownItem));
        when(autoDiscovery.group(anyList(), any(), any(), any(), any())).thenAnswer(inv -> {
            List<InventoryItem> items = inv.getArgument(0);
            return grouping(List.of(new MatchedInventory(new InventoryKey("111", "AAA"), items, supplierRegistry)));
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
        MatchedInventory globalGroup = new MatchedInventory(new InventoryKey("4711111111111", "MFN"), List.of(globalOffer), supplierRegistry);
        currentGlobalGeneration(
                new GlobalMatchedInventory.Generation(1, List.of(globalGroup), InventoryIndex.of(List.of(globalGroup)), VariantCodes.none()));
        when(autoDiscovery.group(anyList(), any(), any(), any(), any())).thenReturn(grouping(List.of()));

        // when
        provider.get("store-1");

        // then
        verify(autoDiscovery).group(anyList(), any(), argThat(witnesses -> List.copyOf(witnesses.get()).equals(List.of(globalOffer))), any(), any());
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
        currentGlobalGeneration(
                new GlobalMatchedInventory.Generation(3, List.of(), InventoryIndex.of(List.of()), globalCodes));
        when(autoDiscovery.group(anyList(), any(), any(), any(), any())).thenReturn(grouping(List.of()));

        // when
        provider.get("store-1");

        // then
        verify(autoDiscovery).group(anyList(), any(), any(), same(globalCodes), any());
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
        when(autoDiscovery.group(anyList(), any(), any(), any(), any())).thenAnswer(inv -> grouping(List.of(groupOf(inv.getArgument(0)))));

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
        StoreInventoryProvider withoutLinks = providerWith(autoDiscovery, new InventoryMatchingProperties(true, true, true, false, true, true));
        MatchedInventory global = philipsGlobal();
        storeSellingOwnOffers(philipsOwnOffers());
        globalPoolOf(List.of(global));
        when(autoDiscovery.group(anyList(), any(), any(), any(), any())).thenAnswer(inv -> grouping(List.of(groupOf(inv.getArgument(0)))));

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
        InventoryAutoDiscovery realDiscovery = new InventoryAutoDiscovery(pimCatalog, supplierRegistry,
                InventoryMatchingProperties.allEnabled());
        InventoryAutoDiscovery.Grouping globalGrouping = realDiscovery.group(List.of(
                sapphire(ean7600, "11324-01-20G", 1843, "Kosatec"),
                sapphire(ean7600, "11324-01-20G", 1810, "Morele"),
                sapphire(ean9060, "11350-03-20G", 2613, "Kosatec"),
                sapphire(ean9060, "11350-03-20G", 2663, "IncomGroup")), pimView, List::of, VariantCodes.none());
        List<MatchedInventory> globalGroups = List.copyOf(globalGrouping.groups());
        currentGlobalGeneration(new GlobalMatchedInventory.Generation(
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
    void storeBuildUsesEanOnlyCodesOfTheGlobalGeneration() {
        // given: the store view holds an unflagged store entry that shadows the flagged global one by GTIN
        String ean176 = "6977728943176";
        String ean169 = "6977728943169";
        when(pimView.findAll()).thenReturn(List.of(new pl.commercelink.pim.api.PimEntry("store-mova", List.of(
                new pl.commercelink.pim.api.PimIdentifier(ean176, pl.commercelink.pim.api.PimIdentifierType.GTIN),
                new pl.commercelink.pim.api.PimIdentifier("SHINE10", pl.commercelink.pim.api.PimIdentifierType.MPN)),
                "Mova", "Name", null, null, true, null, null, null, "store-1", null, false)));
        VariantCodes eanOnly = PimEanOnlyCodes.of(List.of(new pl.commercelink.pim.api.PimEntry("pim-mova", List.of(
                new pl.commercelink.pim.api.PimIdentifier(ean176, pl.commercelink.pim.api.PimIdentifierType.GTIN),
                new pl.commercelink.pim.api.PimIdentifier("SHINE10", pl.commercelink.pim.api.PimIdentifierType.MPN)),
                "Mova", "Name", null, null, true, null, null, null, null, null, true)));
        currentGlobalGeneration(new GlobalMatchedInventory.Generation(
                1, List.of(), InventoryIndex.of(List.of()), VariantCodes.none(), eanOnly));
        InventoryItem kosatec = mova(ean176, 1200, "Kosatec");
        InventoryItem wave = mova(ean169, 1210, "Wave");
        storeSellingOwnOffers(List.of(kosatec, wave));
        InventoryAutoDiscovery realDiscovery = new InventoryAutoDiscovery(pimCatalog, supplierRegistry,
                InventoryMatchingProperties.allEnabled());

        // when
        StoreInventory result = providerWith(realDiscovery, InventoryMatchingProperties.allEnabled()).get("store-1");

        // then
        assertThat(result.index().all())
                .extracting(group -> java.util.Set.copyOf(group.getInventoryItems()))
                .containsExactlyInAnyOrder(java.util.Set.of(kosatec), java.util.Set.of(wave));
    }

    @Test
    void linkedInventorySurvivesTheSnapshotRoundTrip() {
        // given
        MatchedInventory global = philipsGlobal();
        InventoryIndex globalIndex = InventoryIndex.of(List.of(global));
        storeSellingOwnOffers(philipsOwnOffers());
        globalPoolOf(List.of(global));
        when(autoDiscovery.group(anyList(), any(), any(), any(), any())).thenAnswer(inv -> grouping(List.of(groupOf(inv.getArgument(0)))));
        StoreInventory built = provider.get("store-1");

        // when
        StoreInventory restored = StoreInventorySnapshot.from(built).toStoreInventory(supplierRegistry);

        // then
        assertThat(restored.index().all()).singleElement().satisfies(own ->
                assertThat(globalIndex.covers(own.getInventoryKey(), StoreEntryKeys.none())).isTrue());
    }

    @Test
    void withTheLinkSwitchOffTheBuildNeverAsksForTheGlobalIndex() {
        // given
        StoreInventoryProvider withoutLinks = providerWith(autoDiscovery, new InventoryMatchingProperties(true, true, true, false, true, true));
        AtomicInteger indexRequests = new AtomicInteger();
        globalPoolCountingIndexRequests(List.of(philipsGlobal()), indexRequests);
        storeSellingOwnOffers(philipsOwnOffers());
        when(autoDiscovery.group(anyList(), any(), any(), any(), any())).thenAnswer(inv -> grouping(List.of(groupOf(inv.getArgument(0)))));

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
        when(autoDiscovery.group(anyList(), any(), any(), any(), any())).thenAnswer(inv -> {
            requestsWhileGrouping.set(indexRequests.get());
            return grouping(List.of(groupOf(inv.getArgument(0))));
        });

        // when
        provider.get("store-1");

        // then
        assertThat(requestsWhileGrouping).hasValue(0);
        assertThat(indexRequests.get()).isPositive();
    }

    @Test
    void doesNotCacheABuildWhoseGlobalGenerationWasReplaced() {
        // given: the global inventory is regrouped while the store groups its own offers
        GlobalMatchedInventory realGlobal = new GlobalMatchedInventory();
        storeSellingOwnOffers(philipsOwnOffers());
        when(autoDiscovery.group(anyList(), any(), any(), any(), any())).thenAnswer(inv -> {
            realGlobal.replace(List.of(philipsGlobal()));
            return grouping(List.of(groupOf(inv.getArgument(0))));
        });

        // when
        StoreInventory result = providerWith(autoDiscovery, InventoryMatchingProperties.allEnabled(), realGlobal).get("store-1");

        // then
        assertThat(result.index().all()).singleElement().satisfies(own ->
                assertThat(own.getInventoryItems()).containsExactlyInAnyOrderElementsOf(philipsOwnOffers()));
        verify(cache, never()).put(any(), any(), any());
    }

    @Test
    void cachesABuildWhenTheGlobalGenerationIsUnchanged() {
        // given
        GlobalMatchedInventory realGlobal = new GlobalMatchedInventory();
        realGlobal.replace(List.of(philipsGlobal()));
        storeSellingOwnOffers(philipsOwnOffers());
        when(autoDiscovery.group(anyList(), any(), any(), any(), any())).thenAnswer(inv -> grouping(List.of(groupOf(inv.getArgument(0)))));

        // when
        StoreInventory result = providerWith(autoDiscovery, InventoryMatchingProperties.allEnabled(), realGlobal).get("store-1");

        // then
        verify(cache).put(eq("store-1"), same(result), any(Duration.class));
    }

    private static final String MOVA_EAN_176 = "6977728943176";
    private static final String MOVA_EAN_169 = "6977728943169";

    @Test
    void storeFlagBlocksTheLinkOfAnOwnOfferAsVariantCode() {
        // given: the own Mova offer and the global one share only the code SHINE10, which the store flagged
        InventoryItem own = mova(MOVA_EAN_176, 1200, "Action");
        globalPoolGroupedFrom(List.of(mova(MOVA_EAN_169, 1210, "Wave")), VariantCodes.none());
        storeSellingOwnOffers(List.of(own));
        PimCatalog catalog = ScopedPimCatalog.of(storeMova("store-mova", "store-1", true, gtin(MOVA_EAN_176), mpn("SHINE10")));

        // when
        StoreInventory result = providerOver(catalog, InventoryMatchingProperties.allEnabled()).get("store-1");

        // then: the own group stands alone, so the view shows two rows
        assertThat(result.index().all()).singleElement().satisfies(group -> {
            assertThat(group.getInventoryItems()).containsExactly(own);
            assertThat(group.getInventoryKey().getLinkedEans()).isEmpty();
        });
        assertThat(result.index().findMatching(InventoryKey.fromEan(MOVA_EAN_169))).isEmpty();
    }

    @Test
    void withoutStoreFlagTheOwnOfferIsLinked() {
        // given
        globalPoolGroupedFrom(List.of(mova(MOVA_EAN_169, 1210, "Wave")), VariantCodes.none());
        storeSellingOwnOffers(List.of(mova(MOVA_EAN_176, 1200, "Action")));
        PimCatalog catalog = ScopedPimCatalog.of(storeMova("store-mova", "store-1", false, gtin(MOVA_EAN_176), mpn("SHINE10")));

        // when
        StoreInventory result = providerOver(catalog, InventoryMatchingProperties.allEnabled()).get("store-1");

        // then
        assertThat(result.index().all()).singleElement().satisfies(group ->
                assertThat(group.getInventoryKey().getLinkedEans()).containsExactly(MOVA_EAN_169));
    }

    @Test
    void storeFlagOfAnotherStoreChangesNothing() {
        // given: store-2 flagged its own Mova entry; store-1 builds
        globalPoolGroupedFrom(List.of(mova(MOVA_EAN_169, 1210, "Wave")), VariantCodes.none());
        storeSellingOwnOffers(List.of(mova(MOVA_EAN_176, 1200, "Action")));
        PimCatalog catalog = ScopedPimCatalog.of(
                storeMova("store-mova", "store-1", false, gtin(MOVA_EAN_176), mpn("SHINE10")),
                storeMova("other-mova", "store-2", true, gtin(MOVA_EAN_176), mpn("SHINE10")));

        // when
        StoreInventory result = providerOver(catalog, InventoryMatchingProperties.allEnabled()).get("store-1");

        // then
        assertThat(result.index().all()).singleElement().satisfies(group ->
                assertThat(group.getInventoryKey().getLinkedEans()).containsExactly(MOVA_EAN_169));
    }

    @Test
    void storeFlagSplitsTwoOwnOffersOfDifferentEans() {
        // given
        InventoryItem lightGold = mova(MOVA_EAN_176, 1200, "Action");
        InventoryItem silver = mova(MOVA_EAN_169, 1210, "Elko");
        storeSellingOwnOffers(List.of(lightGold, silver));
        PimCatalog catalog = ScopedPimCatalog.of(storeMova("store-mova", "store-1", true, gtin(MOVA_EAN_176), mpn("SHINE10")));

        // when
        StoreInventory result = providerOver(catalog, InventoryMatchingProperties.allEnabled()).get("store-1");

        // then
        assertThat(offerSetsOf(result)).isEqualTo(Set.of(Set.of(lightGold), Set.of(silver)));
    }

    @Test
    void storeFlagKeepsOwnOffersOfTheSameEanTogether() {
        // given
        InventoryItem action = mova(MOVA_EAN_176, 1200, "Action");
        InventoryItem elko = mova(MOVA_EAN_176, 1190, "Elko");
        storeSellingOwnOffers(List.of(action, elko));
        PimCatalog catalog = ScopedPimCatalog.of(storeMova("store-mova", "store-1", true, mpn("SHINE10")));

        // when
        StoreInventory result = providerOver(catalog, InventoryMatchingProperties.allEnabled()).get("store-1");

        // then
        assertThat(offerSetsOf(result)).isEqualTo(Set.of(Set.of(action, elko)));
    }

    @Test
    void storeEntryWithFlagDoesNotPullByCode() {
        // given: the flagged store entry knows the light gold EAN only; the silver offer shares just its code
        InventoryItem silver = mova(MOVA_EAN_169, 1210, "Action");
        storeSellingOwnOffers(List.of(silver));
        PimCatalog catalog = ScopedPimCatalog.of(storeMova("store-mova", "store-1", true, gtin(MOVA_EAN_176), mpn("SHINE10")));

        // when
        StoreInventory result = providerOver(catalog, InventoryMatchingProperties.allEnabled()).get("store-1");

        // then
        assertThat(result.index().all()).filteredOn(group -> group.getInventoryItems().contains(silver)).singleElement()
                .satisfies(group -> assertThat(group.getInventoryKey().getId()).isNull());
    }

    @Test
    void cloneFlagAndGlobalFlagAddUp() {
        // given: the global entry flags SHINE10; the store's clone dropped that code for SHINE10-LG and flags it
        VariantCodes globalPairs = PimEanOnlyCodes.of(List.of(new PimEntry("pim-mova", List.of(mpn("SHINE10")),
                "Mova", "Name", null, null, true, null, null, null, null, null, true)));
        globalPoolGroupedFrom(List.of(), globalPairs);
        InventoryItem code176 = mova(MOVA_EAN_176, 1200, "Action");
        InventoryItem code169 = mova(MOVA_EAN_169, 1210, "Elko");
        InventoryItem lg183 = movaLg("6977728943183", 1300, "Action");
        InventoryItem lg190 = movaLg("6977728943190", 1310, "Elko");
        storeSellingOwnOffers(List.of(code176, code169, lg183, lg190));
        PimCatalog catalog = ScopedPimCatalog.of(new PimEntry("clone-mova", List.of(mpn("SHINE10-LG")),
                "Mova", "Name", null, null, true, null, null, null, "store-1", "pim-mova", true));

        // when
        StoreInventory result = providerOver(catalog, InventoryMatchingProperties.allEnabled()).get("store-1");

        // then
        assertThat(offerSetsOf(result)).isEqualTo(Set.of(Set.of(code176), Set.of(code169), Set.of(lg183), Set.of(lg190)));
    }

    @Test
    void conflictingOffersAreJudgedTheSameWithStorePairs() {
        // given: the Sapphire layout of rule A; the store flags its own entry of the RX 7600 code
        String ean7600 = "4895106293618";
        String ean9060 = "4895106296381";
        List<InventoryItem> globalOffers = List.of(
                sapphire(ean7600, "11324-01-20G", 1843, "Kosatec"),
                sapphire(ean7600, "11324-01-20G", 1810, "Morele"),
                sapphire(ean9060, "11350-03-20G", 2613, "Kosatec"),
                sapphire(ean9060, "11350-03-20G", 2663, "IncomGroup"));
        globalPoolGroupedFrom(globalOffers, VariantCodes.none());
        InventoryItem ownAction = sapphire("4895106299993", "11324-01-20G", 1850, "Action");
        InventoryItem ownElko = sapphire(ean9060, "11324-01-20G", 3563, "Elko");
        storeSellingOwnOffers(List.of(ownAction, ownElko));
        Set<InventoryItem> allOffers = new HashSet<>(globalOffers);
        allOffers.add(ownAction);
        allOffers.add(ownElko);
        PimEntry rx9060 = new PimEntry("gpbdsxwa19", List.of(gtin(ean9060), mpn("11350-03-20G")),
                "Sapphire", "Name", null, null, true, null, null);

        // when
        StoreInventory without = providerOver(ScopedPimCatalog.of(rx9060, storeSapphire7600(false)),
                InventoryMatchingProperties.allEnabled()).get("store-1");
        StoreInventory with = providerOver(ScopedPimCatalog.of(rx9060, storeSapphire7600(true)),
                InventoryMatchingProperties.allEnabled()).get("store-1");

        // then -- the offers rule A leaves out of every group are the same with and without store pairs
        assertThat(excludedFrom(with, allOffers)).isEqualTo(excludedFrom(without, allOffers)).contains(ownElko)
                .doesNotContain(ownAction);
        for (StoreInventory result : List.of(without, with)) {
            assertThat(result.index().all()).flatExtracting(MatchedInventory::getInventoryItems)
                    .doesNotContain(ownElko).contains(ownAction);
            assertThat(result.index().all()).filteredOn(group -> "gpbdsxwa19".equals(group.getInventoryKey().getId()))
                    .flatExtracting(MatchedInventory::getInventoryItems)
                    .noneMatch(offer -> "11324-01-20G".equals(offer.mfn()));
        }
    }

    private static Set<InventoryItem> excludedFrom(StoreInventory result, Set<InventoryItem> offers) {
        Set<InventoryItem> excluded = new HashSet<>(offers);
        result.index().all().forEach(group -> group.getInventoryItems().forEach(excluded::remove));
        return excluded;
    }

    @Test
    void buildPassesTheStorePairsToTheFeedLoader() {
        // given
        when(pimView.findAll()).thenReturn(List.of(storePhilips(true)));
        storeSellingOwnOffers(philipsOwnOffers());
        globalPoolOf(List.of());
        when(autoDiscovery.group(anyList(), any(), any(), any(), any())).thenAnswer(inv -> grouping(List.of(groupOf(inv.getArgument(0)))));

        // when
        providerWith(autoDiscovery, InventoryMatchingProperties.allEnabled()).get("store-1");

        // then
        verify(storeFeedItemLoader).load(eq("store-1"), eq("Action"), any(), any(),
                argThat((VariantCodes pairs) -> pairs.contains("philips", "QP1324/20")));
    }

    @Test
    void storeFeedOfferWithoutEanUnderStorePairIsLeftOutOfTheGlobalEntryGroup() {
        // given: the loader hands over the offer as the store pair leaves it, without the PIM's EAN
        InventoryItem global = mova(MOVA_EAN_169, 1210, "Wave");
        globalPoolGroupedFrom(List.of(global), VariantCodes.none());
        InventoryItem withoutEan = mova("", 1200, "Action");
        storeSellingOwnOffers(List.of(withoutEan));
        PimCatalog catalog = ScopedPimCatalog.of(storeMova("store-mova", "store-1", true, gtin(MOVA_EAN_169), mpn("SHINE10")));

        // when
        StoreInventory result = providerOver(catalog, InventoryMatchingProperties.allEnabled()).get("store-1");

        // then
        assertThat(result.index().all()).flatExtracting(MatchedInventory::getInventoryItems)
                .doesNotContain(global);
        assertThat(result.index().findMatching(InventoryKey.fromEan(MOVA_EAN_169))).isEmpty();
    }

    @Test
    void buildIsNotCachedWhenStorePairsChangedDuringIt() {
        // given: the store flags its Philips entry in PIM while the build groups
        AtomicReference<List<PimEntry>> storeEntries = new AtomicReference<>(List.of());
        when(pimView.findAll()).thenAnswer(inv -> storeEntries.get());
        storeSellingOwnOffers(philipsOwnOffers());
        globalPoolOf(List.of());
        when(autoDiscovery.group(anyList(), any(), any(), any(), any())).thenAnswer(inv -> {
            storeEntries.set(List.of(storePhilips(true)));
            return grouping(List.of(groupOf(inv.getArgument(0))));
        });

        // when
        StoreInventory result = providerWith(autoDiscovery, InventoryMatchingProperties.allEnabled()).get("store-1");

        // then: the build still serves this request
        assertThat(result.index().all()).singleElement().satisfies(group ->
                assertThat(group.getInventoryItems()).containsExactlyInAnyOrderElementsOf(philipsOwnOffers()));
        verify(cache, never()).put(any(), any(), any());
    }

    @Test
    void buildIsCachedWhenStorePairsAreUnchanged() {
        // given
        when(pimView.findAll()).thenReturn(List.of(storePhilips(true)));
        storeSellingOwnOffers(philipsOwnOffers());
        globalPoolOf(List.of());
        when(autoDiscovery.group(anyList(), any(), any(), any(), any())).thenAnswer(inv -> grouping(List.of(groupOf(inv.getArgument(0)))));

        // when
        StoreInventory result = providerWith(autoDiscovery, InventoryMatchingProperties.allEnabled()).get("store-1");

        // then
        verify(cache).put(eq("store-1"), same(result), any(Duration.class));
    }

    @Test
    void buildIsCachedWhenTheStoreHasNoPairs() {
        // given
        when(pimView.findAll()).thenReturn(List.of(storePhilips(false)));
        storeSellingOwnOffers(philipsOwnOffers());
        globalPoolOf(List.of());
        when(autoDiscovery.group(anyList(), any(), any(), any(), any())).thenAnswer(inv -> grouping(List.of(groupOf(inv.getArgument(0)))));

        // when
        StoreInventory result = providerWith(autoDiscovery, InventoryMatchingProperties.allEnabled()).get("store-1");

        // then
        verify(cache).put(eq("store-1"), same(result), any(Duration.class));
    }

    @Test
    void storeSwitchOffMakesTheFlagInert() {
        // given
        globalPoolGroupedFrom(List.of(mova(MOVA_EAN_169, 1210, "Wave")), VariantCodes.none());
        storeSellingOwnOffers(List.of(mova(MOVA_EAN_176, 1200, "Action")));
        PimCatalog catalog = ScopedPimCatalog.of(storeMova("store-mova", "store-1", true, gtin(MOVA_EAN_176), mpn("SHINE10")));

        // when
        StoreInventory result = providerOver(catalog, new InventoryMatchingProperties(true, true, true, true, true, false))
                .get("store-1");

        // then
        assertThat(result.index().all()).singleElement().satisfies(group ->
                assertThat(group.getInventoryKey().getLinkedEans()).containsExactly(MOVA_EAN_169));
    }

    // the global pool as Inventory.load groups it: from global offers only, with the global EAN-only pairs
    private void globalPoolGroupedFrom(List<InventoryItem> globalOffers, VariantCodes globalPairs) {
        InventoryAutoDiscovery.Grouping global = new InventoryAutoDiscovery(ScopedPimCatalog.of(), supplierRegistry, InventoryMatchingProperties.allEnabled())
                .group(globalOffers, ScopedPimCatalog.of().withGlobalData(), List::of, VariantCodes.none(), globalPairs);
        List<MatchedInventory> groups = List.copyOf(global.groups());
        currentGlobalGeneration(new GlobalMatchedInventory.Generation(
                1, groups, InventoryIndex.of(groups), global.variantCodes(), globalPairs));
    }

    private static Set<Set<InventoryItem>> offerSetsOf(StoreInventory inventory) {
        return inventory.index().all().stream()
                .filter(group -> !group.getInventoryItems().isEmpty())
                .map(group -> Set.copyOf(group.getInventoryItems()))
                .collect(Collectors.toSet());
    }

    private static PimEntry storeMova(String pimId, String storeId, boolean eanOnly, PimIdentifier... identifiers) {
        return new PimEntry(pimId, List.of(identifiers), "Mova", "Name", null, null, true, null, null, null, storeId,
                null, eanOnly);
    }

    private static PimEntry storeSapphire7600(boolean eanOnly) {
        return new PimEntry("store-7600", List.of(mpn("11324-01-20G")), "Sapphire", "Name", null, null, true, null, null,
                null, "store-1", null, eanOnly);
    }

    private static PimEntry storePhilips(boolean eanOnly) {
        return new PimEntry("store-philips", List.of(gtin(OWN_EAN), mpn("QP1324/20")), "Philips", "Name", null, null,
                true, null, null, null, "store-1", null, eanOnly);
    }

    private static PimIdentifier gtin(String value) {
        return new PimIdentifier(value, PimIdentifierType.GTIN);
    }

    private static PimIdentifier mpn(String value) {
        return new PimIdentifier(value, PimIdentifierType.MPN);
    }

    private static InventoryItem movaLg(String ean, double netPrice, String supplier) {
        return new InventoryItem("Mova", null, ean, "SHINE10-LG", supplier, netPrice, "PLN", 5, 1, true, false, false);
    }

    private void globalPoolCountingIndexRequests(List<MatchedInventory> groups, AtomicInteger indexRequests) {
        currentGlobalGeneration(new GlobalMatchedInventory.Generation(1, groups, () -> {
            indexRequests.incrementAndGet();
            return InventoryIndex.of(groups);
        }, VariantCodes.none()));
    }

    // the mocked global inventory reports the version of the generation it hands out, as the real one does
    private void currentGlobalGeneration(GlobalMatchedInventory.Generation generation) {
        when(globalInventory.generation()).thenReturn(generation);
        when(globalInventory.version()).thenReturn(generation.version());
    }

    private StoreInventoryProvider providerWith(InventoryAutoDiscovery discovery, InventoryMatchingProperties properties) {
        return providerWith(discovery, properties, globalInventory);
    }

    private StoreInventoryProvider providerWith(InventoryAutoDiscovery discovery, InventoryMatchingProperties properties,
                                                GlobalMatchedInventory global) {
        return new StoreInventoryProvider(cache, storesRepository, supplierProviderFactory, discovery,
                storeFeedItemLoader, exchangeRates, pimCatalog, global, properties,
                new StoreEanOnlyCodes(pimCatalog, properties));
    }

    // grouping, linking and the store pairs all read the given catalog, whose store view holds only that store's entries
    private StoreInventoryProvider providerOver(PimCatalog catalog, InventoryMatchingProperties properties) {
        InventoryAutoDiscovery discovery = new InventoryAutoDiscovery(catalog, supplierRegistry, properties);
        return new StoreInventoryProvider(cache, storesRepository, supplierProviderFactory, discovery,
                storeFeedItemLoader, exchangeRates, catalog, globalInventory, properties,
                new StoreEanOnlyCodes(catalog, properties));
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
        when(storeFeedItemLoader.load(eq("store-1"), eq("Action"), same(descriptor), any(), any())).thenReturn(offers);
    }

    private void globalPoolOf(List<MatchedInventory> groups) {
        currentGlobalGeneration(
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
        return new MatchedInventory(key, offers, supplierRegistry);
    }

    private static InventoryItem philips(String ean, double netPrice, String supplier) {
        return new InventoryItem("Philips", null, ean, "QP1324/20", supplier, netPrice, "PLN", 5, 1, true, false, false);
    }

    private static InventoryItem mova(String ean, double netPrice, String supplier) {
        return new InventoryItem("Mova", null, ean, "SHINE10", supplier, netPrice, "PLN", 5, 1, true, false, false);
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
        org.mockito.Mockito.lenient().when(storeEanOnlyCodes.of(org.mockito.ArgumentMatchers.any())).thenReturn(VariantCodes.none());
    }
}
