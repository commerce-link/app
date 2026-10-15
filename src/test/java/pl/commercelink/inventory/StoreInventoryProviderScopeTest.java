package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.supplier.StoreFeedItemLoader;
import pl.commercelink.inventory.supplier.SupplierProviderFactory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreInventoryProviderScopeTest {

    private static final String FEED_EAN = "5900000000591";
    private static final String SIBLING_EAN = "5900000000592";
    private static final String MPN = "MPN-S1";

    @Mock private StoreInventoryCache cache;
    @Mock private StoresRepository storesRepository;
    @Mock private SupplierProviderFactory supplierProviderFactory;
    @Mock private StoreFeedItemLoader storeFeedItemLoader;
    @Mock private ExchangeRates exchangeRates;
    @Mock private TaxonomyCache taxonomyCache;
    @Mock private SupplierRegistry supplierRegistry;

    private Collection<MatchedInventory> inventoryOf(String storeId) {
        PimEntry storeEntry = new PimEntry("s1-2", List.of(
                new PimIdentifier(FEED_EAN, PimIdentifierType.GTIN),
                new PimIdentifier(SIBLING_EAN, PimIdentifierType.GTIN),
                new PimIdentifier(MPN, PimIdentifierType.MPN)),
                "Brand", "Store product", "GPU", null, true, null, null, null, "s1");
        PimCatalog pimCatalog = ScopedPimCatalog.of(storeEntry);
        InventoryAutoDiscovery autoDiscovery = new InventoryAutoDiscovery(pimCatalog, taxonomyCache, supplierRegistry);
        StoreInventoryProvider provider = new StoreInventoryProvider(cache, storesRepository, supplierProviderFactory,
                autoDiscovery, storeFeedItemLoader, exchangeRates, pimCatalog);

        Store store = new Store();
        store.setStoreId(storeId);
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(List.of(new StoreSupplierConnection("manual:H1", ConnectionMode.MANUAL)));
        store.setFulfilmentConfiguration(config);
        when(cache.get(storeId)).thenReturn(Optional.empty());
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of());
        when(storeFeedItemLoader.load(eq(storeId), eq("manual:H1"), any(SupplierProviderDescriptor.class), any()))
                .thenReturn(List.of(new InventoryItem(FEED_EAN, MPN, 10.0, "PLN", 1, 1, "manual:H1", true, true, false)));
        return provider.ownInventory(store).items();
    }

    @Test
    void storeFeedGroupingUsesTheStoresView() {
        // when
        Collection<MatchedInventory> owner = inventoryOf("s1");
        Collection<MatchedInventory> other = inventoryOf("s2");

        // then
        assertThat(owner).singleElement().satisfies(group ->
                assertThat(group.getInventoryKey().getId()).isEqualTo("s1-2"));
        assertThat(other).singleElement().satisfies(group ->
                assertThat(group.getInventoryKey().getId()).isNull());
    }
}
