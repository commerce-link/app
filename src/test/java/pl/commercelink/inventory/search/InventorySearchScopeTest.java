package pl.commercelink.inventory.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.warehouse.api.Warehouse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventorySearchScopeTest {

    private static final String STORE_EAN = ScopedPimCatalog.STORE_GTIN;
    private static final String PRIMARY_EAN = "5900000000592";

    @Mock private Inventory inventory;
    @Mock private StoresRepository storesRepository;
    @Mock private TaxonomyCache taxonomyCache;
    @Mock private Warehouse warehouse;
    @Mock private SupplierRegistry supplierRegistry;
    @Mock private InventoryView view;

    private InventorySearch search() {
        MatchedInventory none = new MatchedInventory(new InventoryKey(), taxonomyCache, supplierRegistry);
        when(view.findByEan(anyString())).thenReturn(none);
        when(view.findByProductCode(anyString())).thenReturn(none);
        when(view.findByInventoryKey(any())).thenReturn(none);
        when(inventory.withEnabledSuppliersOnly(anyString())).thenReturn(view);
        when(inventory.withGlobalData()).thenReturn(view);
        PimEntry global = ScopedPimCatalog.globalEntry("g1", ScopedPimCatalog.GLOBAL_GTIN, "M-590", "GPU");
        PimEntry store = new PimEntry("s1-2", List.of(
                new PimIdentifier(STORE_EAN, PimIdentifierType.GTIN),
                new PimIdentifier(PRIMARY_EAN, PimIdentifierType.GTIN, true),
                new PimIdentifier("M-A", PimIdentifierType.MPN),
                new PimIdentifier("M-B", PimIdentifierType.MPN, true)),
                "Brand", "Store product", "GPU", null, true, null, null, null, "s1");
        return new InventorySearch(inventory, storesRepository, ScopedPimCatalog.of(global, store), taxonomyCache, warehouse,
                new SupplierLabels(storesRepository), supplierRegistry);
    }

    @Test
    void storeSearchFindsTheStoresEntryAndGlobalSearchDoesNot() {
        // given
        InventorySearch search = search();

        // when
        InventorySearchResult owner = search.search("s1", STORE_EAN);
        InventorySearchResult otherStore = search.search("s2", STORE_EAN);
        InventorySearchResult global = search.searchGlobal(STORE_EAN);

        // then
        assertThat(owner).isInstanceOf(InventorySearchResult.KnownWithoutOffers.class);
        ProductHeader header = ((InventorySearchResult.KnownWithoutOffers) owner).product();
        assertThat(header.name()).isEqualTo("Store product");
        assertThat(header.ean()).isEqualTo(PRIMARY_EAN);
        assertThat(header.mfn()).isEqualTo("M-B");
        assertThat(otherStore).isInstanceOf(InventorySearchResult.NotFound.class);
        assertThat(global).isInstanceOf(InventorySearchResult.NotFound.class);
    }

    @Test
    void storeEntryIsFoundByItsPimIdInTheOwningStoreOnly() {
        // given
        InventorySearch search = search();

        // when / then
        assertThat(search.search("s1", "s1-2")).isInstanceOf(InventorySearchResult.KnownWithoutOffers.class);
        assertThat(search.search("s2", "s1-2")).isInstanceOf(InventorySearchResult.NotFound.class);
        assertThat(search.searchGlobal("s1-2")).isInstanceOf(InventorySearchResult.NotFound.class);
    }
}
