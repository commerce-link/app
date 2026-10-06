package pl.commercelink.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryBrowseTest {

    private static final String STORE_ID = "store-1";

    @Mock private TaxonomyCache taxonomyCache;
    @Mock private SupplierRegistry supplierRegistry;
    @Mock private StoresRepository storesRepository;
    @Mock private StoreInventoryProvider storeInventoryProvider;
    @Mock private Store store;

    private final Map<String, Taxonomy> taxonomies = new HashMap<>();
    private final GlobalMatchedInventory global = new GlobalMatchedInventory();
    private final List<MatchedInventory> own = new ArrayList<>();
    private InventoryBrowse browse;

    @BeforeEach
    void setUp() {
        when(taxonomyCache.find(any())).thenAnswer(call -> {
            InventoryKey key = call.getArgument(0);
            return key.getProductCodes().stream().map(taxonomies::get).filter(Objects::nonNull).findFirst()
                    .orElse(Taxonomy.EMPTY);
        });
        when(supplierRegistry.exists(anyString())).thenReturn(false);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(store.getGlobalSupplierNames()).thenReturn(List.of("AB"));
        when(storeInventoryProvider.ownInventory(store))
                .thenAnswer(call -> new StoreInventory(InventoryIndex.of(own), LocalDateTime.now()));
        browse = new InventoryBrowse(new BrowseIndexHolder(global, Runnable::run), global, storesRepository,
                storeInventoryProvider, supplierRegistry);
    }

    @Test
    void disabledGlobalSupplierProductsAreHidden() {
        // given
        global.replace(List.of(
                product("5901000000001", "GPU-1", "RTX 4060", "11", item("5901000000001", "GPU-1", "AB", 100, 5)),
                product("5901000000002", "GPU-2", "RX 7600", "11", item("5901000000002", "GPU-2", "Action", 90, 5))));

        // when
        BrowseResult result = browse.browse(STORE_ID, BrowseCriteria.all());
        BrowseSummary summary = browse.summary(STORE_ID);

        // then
        assertThat(result.rows()).extracting(BrowseRow::name).containsExactly("RTX 4060");
        assertThat(summary.byCategory()).containsEntry("11", 1);
        assertThat(summary.bySupplier()).containsOnlyKeys("AB");
        assertThat(summary.total()).isEqualTo(1);
    }

    @Test
    void storeWithoutSuppliersHasNoEntries() {
        // given
        when(store.getGlobalSupplierNames()).thenReturn(List.of());
        global.replace(List.of(product("5901000000001", "GPU-1", "RTX 4060", "11", item("5901000000001", "GPU-1", "AB", 100, 5))));

        // when / then
        assertThat(browse.browse(STORE_ID, BrowseCriteria.all()).total()).isZero();
        assertThat(browse.summary(STORE_ID).total()).isZero();
    }

    @Test
    void ownOfferOfGlobalProductIsMergedNotDuplicated() {
        // given
        global.replace(List.of(product("5901000000001", "GPU-1", "RTX 4060", "11", item("5901000000001", "GPU-1", "AB", 100, 5))));
        own.add(product("5901000000001", "GPU-1", "RTX 4060", "11", item("5901000000001", "GPU-1", "MyFeed", 80, 2)));

        // when
        BrowseResult result = browse.browse(STORE_ID, BrowseCriteria.all());

        // then
        assertThat(result.total()).isEqualTo(1);
        BrowseRow row = result.rows().get(0);
        assertThat(row.suppliers()).isEqualTo(2);
        assertThat(row.qty()).isEqualTo(7);
        assertThat(row.lowestSupplier()).isEqualTo("MyFeed");
    }

    @Test
    void ownOnlyProductIsListed() {
        // given
        global.replace(List.of());
        own.add(product("5901000000009", "OWN-9", "Kabel HDMI", "30", item("5901000000009", "OWN-9", "MyFeed", 10, 40)));

        // when
        BrowseResult result = browse.browse(STORE_ID, BrowseCriteria.all().inCategories(Set.of("30")));

        // then
        assertThat(result.rows()).extracting(BrowseRow::name).containsExactly("Kabel HDMI");
    }

    @Test
    void filtersBySupplierAndStock() {
        // given
        when(store.getGlobalSupplierNames()).thenReturn(List.of("AB", "Action"));
        global.replace(List.of(
                product("5901000000001", "GPU-1", "RTX 4060", "11", item("5901000000001", "GPU-1", "AB", 100, 0)),
                product("5901000000002", "GPU-2", "RX 7600", "11", item("5901000000002", "GPU-2", "Action", 90, 5))));

        // when
        BrowseResult fromAb = browse.browse(STORE_ID, BrowseCriteria.all().fromSuppliers(Set.of("AB")));
        BrowseResult inStock = browse.browse(STORE_ID, BrowseCriteria.all().withStock(BrowseCriteria.Stock.IN_STOCK));
        BrowseResult onOrder = browse.browse(STORE_ID, BrowseCriteria.all().withStock(BrowseCriteria.Stock.ON_ORDER));

        // then
        assertThat(fromAb.rows()).extracting(BrowseRow::name).containsExactly("RTX 4060");
        assertThat(inStock.rows()).extracting(BrowseRow::name).containsExactly("RX 7600");
        assertThat(onOrder.rows()).extracting(BrowseRow::name).containsExactly("RTX 4060");
    }

    @Test
    void sortsByDeliveredCostNotByPrice() {
        // given
        when(store.getGlobalSupplierNames()).thenReturn(List.of("AB", "Action"));
        SupplierInfo expensiveShipping = mock(SupplierInfo.class);
        when(supplierRegistry.exists("Action")).thenReturn(true);
        when(supplierRegistry.get("Action")).thenReturn(expensiveShipping);
        when(expensiveShipping.shippingTermsFor("PL")).thenReturn(new ShippingTerms(1, new ShippingCostPolicy.FlatRate(100_000, 50)));
        global.replace(List.of(
                product("5901000000001", "GPU-1", "A cheap with shipping", "11", item("5901000000001", "GPU-1", "Action", 90, 5)),
                product("5901000000002", "GPU-2", "B dearer but free", "11", item("5901000000002", "GPU-2", "AB", 100, 5))));

        // when
        BrowseResult result = browse.browse(STORE_ID, BrowseCriteria.all().sortedBy(BrowseCriteria.Sort.COST, false));

        // then
        assertThat(result.rows()).extracting(BrowseRow::name).containsExactly("B dearer but free", "A cheap with shipping");
        assertThat(result.rows().get(1).lowestDeliveredNet()).isEqualTo(140.0);
    }

    @Test
    void textMatchesAreCapped() {
        // given
        List<MatchedInventory> many = new ArrayList<>();
        for (int i = 0; i < BrowseCriteria.MAX_TEXT_MATCHES + 5; i++) {
            String ean = String.format("59020000%05d", i);
            many.add(product(ean, "CAB-" + i, "Kabel " + i, "30", item(ean, "CAB-" + i, "AB", 10, 1)));
        }
        global.replace(many);

        // when
        BrowseResult result = browse.browse(STORE_ID, BrowseCriteria.all().withText("kabel").page(0, 50));

        // then
        assertThat(result.truncated()).isTrue();
        assertThat(result.total()).isEqualTo(BrowseCriteria.MAX_TEXT_MATCHES);
        assertThat(result.rows()).hasSize(50);
    }

    @Test
    void offsetPastEndReturnsEmptyRowsWithTotal() {
        // given
        global.replace(List.of(product("5901000000001", "GPU-1", "RTX 4060", "11", item("5901000000001", "GPU-1", "AB", 100, 5))));

        // when
        BrowseResult result = browse.browse(STORE_ID, BrowseCriteria.all().page(500, 50));

        // then
        assertThat(result.rows()).isEmpty();
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void rowFilterNarrowsTheResult() {
        // given
        global.replace(List.of(
                product("5901000000001", "GPU-1", "RTX 4060", "11", item("5901000000001", "GPU-1", "AB", 100, 5)),
                product("5901000000002", "GPU-2", "RX 7600", "11", item("5901000000002", "GPU-2", "AB", 90, 5))));

        // when
        BrowseResult result = browse.browse(STORE_ID, BrowseCriteria.all().withRowFilter(row -> row.name().startsWith("RX")));

        // then
        assertThat(result.rows()).extracting(BrowseRow::name).containsExactly("RX 7600");
    }

    @Test
    void superAdminSeesEveryGlobalSupplierAndNoOwnFeed() {
        // given
        global.replace(List.of(
                product("5901000000001", "GPU-1", "RTX 4060", "11", item("5901000000001", "GPU-1", "AB", 100, 5)),
                product("5901000000002", "GPU-2", "RX 7600", "11", item("5901000000002", "GPU-2", "Action", 90, 5))));
        own.add(product("5901000000009", "OWN-9", "Kabel HDMI", "30", item("5901000000009", "OWN-9", "MyFeed", 10, 40)));

        // when
        BrowseResult result = browse.browse(null, BrowseCriteria.all());

        // then
        assertThat(result.rows()).extracting(BrowseRow::name).containsExactly("RTX 4060", "RX 7600");
    }

    private MatchedInventory product(String ean, String mfn, String name, String categoryId, InventoryItem... items) {
        InventoryKey key = new InventoryKey(ean, mfn);
        Taxonomy taxonomy = new Taxonomy(ean, mfn, "Brand", name, "Kategoria " + categoryId, 1, null, null, null, categoryId);
        key.getProductCodes().forEach(code -> taxonomies.put(code, taxonomy));
        return new MatchedInventory(key, List.of(items), taxonomyCache, supplierRegistry);
    }

    private static InventoryItem item(String ean, String mfn, String supplier, double net, int qty) {
        return new InventoryItem(ean, mfn, net, "PLN", qty, 1, supplier);
    }
}
