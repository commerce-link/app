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
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
        browse = new InventoryBrowse(new BrowseIndexHolder(global, Runnable::run), storesRepository,
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
    void filtersBySupplier() {
        // given
        when(store.getGlobalSupplierNames()).thenReturn(List.of("AB", "Action"));
        global.replace(List.of(
                product("5901000000001", "GPU-1", "RTX 4060", "11", item("5901000000001", "GPU-1", "AB", 100, 0)),
                product("5901000000002", "GPU-2", "RX 7600", "11", item("5901000000002", "GPU-2", "Action", 90, 5))));

        // when
        BrowseResult fromAb = browse.browse(STORE_ID, BrowseCriteria.all().fromSuppliers(Set.of("AB")));

        // then
        assertThat(fromAb.rows()).extracting(BrowseRow::name).containsExactly("RTX 4060");
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
    void offsetPastEndReturnsTheLastPage() {
        // given
        global.replace(List.of(
                product("5901000000001", "GPU-1", "A1", "11", item("5901000000001", "GPU-1", "AB", 100, 5)),
                product("5901000000002", "GPU-2", "A2", "11", item("5901000000002", "GPU-2", "AB", 100, 5)),
                product("5901000000003", "GPU-3", "A3", "11", item("5901000000003", "GPU-3", "AB", 100, 5))));

        // when
        BrowseResult result = browse.browse(STORE_ID, BrowseCriteria.all().page(500, 2));

        // then
        assertThat(result.rows()).extracting(BrowseRow::name).containsExactly("A3");
        assertThat(result.total()).isEqualTo(3);
    }

    @Test
    void offerWithoutAComparablePriceStillMakesARow() {
        // given
        global.replace(List.of(product("5901000000001", "GPU-1", "RTX 4060", "11",
                item("5901000000001", "GPU-1", "AB", Double.NaN, 5))));

        // when
        BrowseResult result = browse.browse(STORE_ID, BrowseCriteria.all());

        // then
        assertThat(result.rows()).extracting(BrowseRow::lowestSupplier).containsExactly("AB");
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

    @Test
    void nameOrderFollowsPolishCollationAcrossGlobalAndOwnProducts() {
        // given
        global.replace(List.of(
                product("5901000000001", "P-1", "Zebra", "11", item("5901000000001", "P-1", "AB", 10, 1)),
                product("5901000000002", "P-2", "Łódka", "11", item("5901000000002", "P-2", "AB", 10, 1)),
                product("5901000000003", "P-3", "lampa", "11", item("5901000000003", "P-3", "AB", 10, 1)),
                product("5901000000004", "P-4", "Ćma", "11", item("5901000000004", "P-4", "AB", 10, 1)),
                product("5901000000005", "P-5", "Cebula", "11", item("5901000000005", "P-5", "AB", 10, 1))));
        own.add(product("5901000000006", "P-6", "Lustro", "11", item("5901000000006", "P-6", "MyFeed", 10, 1)));
        own.add(product("5901000000007", "P-7", "Ćwiek", "11", item("5901000000007", "P-7", "MyFeed", 10, 1)));
        own.add(product("5901000000008", "P-8", "Żaba", "11", item("5901000000008", "P-8", "MyFeed", 10, 1)));

        // when
        BrowseResult ascending = browse.browse(STORE_ID, BrowseCriteria.all());
        BrowseResult descending = browse.browse(STORE_ID, BrowseCriteria.all().sortedBy(BrowseCriteria.Sort.NAME, true));

        // then
        assertThat(ascending.rows()).extracting(BrowseRow::name)
                .containsExactly("Cebula", "Ćma", "Ćwiek", "lampa", "Lustro", "Łódka", "Zebra", "Żaba");
        assertThat(descending.rows()).extracting(BrowseRow::name)
                .containsExactly("Żaba", "Zebra", "Łódka", "Lustro", "lampa", "Ćwiek", "Ćma", "Cebula");
    }

    @Test
    void equalQuantitiesFallBackToTheNameOrder() {
        // given
        global.replace(List.of(
                product("5901000000001", "P-1", "Beta", "11", item("5901000000001", "P-1", "AB", 10, 3)),
                product("5901000000002", "P-2", "Alfa", "11", item("5901000000002", "P-2", "AB", 10, 3)),
                product("5901000000003", "P-3", "Gamma", "11", item("5901000000003", "P-3", "AB", 10, 9))));

        // when
        BrowseResult result = browse.browse(STORE_ID, BrowseCriteria.all().sortedBy(BrowseCriteria.Sort.QTY, true));

        // then
        assertThat(result.rows()).extracting(BrowseRow::name).containsExactly("Gamma", "Alfa", "Beta");
    }

    @Test
    void nextPageReusesTheSortedSelectionAndPricesOnlyItsOwnRows() {
        // given
        List<MatchedInventory> many = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            String ean = String.format("59030000%05d", i);
            many.add(product(ean, "GPU-" + i, "Karta " + i, "11", item(ean, "GPU-" + i, "AB", 100 + i, 1)));
        }
        global.replace(many);
        BrowseCriteria byCost = BrowseCriteria.all().sortedBy(BrowseCriteria.Sort.COST, false);
        BrowseResult first = browse.browse(STORE_ID, byCost.page(0, 50));
        clearInvocations(supplierRegistry);

        // when
        BrowseResult second = browse.browse(STORE_ID, byCost.page(50, 50));

        // then
        assertThat(first.rows().get(0).lowestDeliveredNet()).isEqualTo(100.0);
        assertThat(second.rows().get(0).lowestDeliveredNet()).isEqualTo(150.0);
        assertThat(second.total()).isEqualTo(120);
        verify(supplierRegistry, times(50)).exists(anyString());
    }

    @Test
    void cappedPhraseKeepsTheCheapestMatchesNotTheFirstFound() {
        // given
        List<MatchedInventory> many = new ArrayList<>();
        int count = BrowseCriteria.MAX_TEXT_MATCHES + 5;
        for (int i = 0; i < count; i++) {
            String ean = String.format("59020000%05d", i);
            many.add(product(ean, "CAB-" + i, "Kabel " + i, "30", item(ean, "CAB-" + i, "AB", count - i, 1)));
        }
        global.replace(many);

        // when
        BrowseResult result = browse.browse(STORE_ID, BrowseCriteria.all().withText("kabel")
                .sortedBy(BrowseCriteria.Sort.COST, false).page(0, 50));

        // then
        assertThat(result.truncated()).isTrue();
        assertThat(result.total()).isEqualTo(BrowseCriteria.MAX_TEXT_MATCHES);
        assertThat(result.rows().get(0).lowestDeliveredNet()).isEqualTo(1.0);
    }

    @Test
    void ownOfferJoinsTheIndexedProductWhileTheIndexLagsAReload() {
        // given
        List<Runnable> rebuilds = new ArrayList<>();
        InventoryBrowse lagging = new InventoryBrowse(new BrowseIndexHolder(global, rebuilds::add), storesRepository,
                storeInventoryProvider, supplierRegistry);
        global.replace(List.of(product("5901000000001", "GPU-1", "RTX 4060", "11", item("5901000000001", "GPU-1", "AB", 100, 5))));
        lagging.summary(null);
        global.replace(List.of(product("5901000000001", "GPU-1", "RTX 4060", "11", item("5901000000001", "GPU-1", "AB", 95, 5))));
        own.add(product("5901000000001", "GPU-1", "RTX 4060", "11", item("5901000000001", "GPU-1", "MyFeed", 80, 2)));

        // when
        BrowseResult result = lagging.browse(STORE_ID, BrowseCriteria.all());

        // then
        assertThat(rebuilds).hasSize(1);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.rows().get(0).suppliers()).isEqualTo(2);
    }

    @Test
    void facetsCountSuppliersInTheCategoryWhicheverSupplierIsTicked() {
        // given
        when(store.getGlobalSupplierNames()).thenReturn(List.of("AB", "Action"));
        global.replace(List.of(
                product("5901000000001", "GPU-1", "RTX 4060", "11", item("5901000000001", "GPU-1", "AB", 100, 5),
                        item("5901000000001", "GPU-1", "Action", 99, 5)),
                product("5901000000002", "GPU-2", "RX 7600", "11", item("5901000000002", "GPU-2", "Action", 90, 5)),
                product("5901000000003", "SSD-1", "Dysk", "12", item("5901000000003", "SSD-1", "AB", 90, 5))));

        // when
        BrowseFacets facets = browse.facets(STORE_ID, Set.of("11"), Set.of("AB"), null);

        // then
        assertThat(facets.bySupplier()).containsOnly(entry("AB", 1), entry("Action", 2));
        assertThat(facets.byCategory()).containsOnly(entry("11", 1));
    }

    @Test
    void facetsFollowThePhrase() {
        // given
        global.replace(List.of(
                product("5901000000001", "GPU-1", "RTX 4060", "11", item("5901000000001", "GPU-1", "AB", 100, 5)),
                product("5901000000002", "CAB-1", "Kabel HDMI", "21", item("5901000000002", "CAB-1", "AB", 10, 5))));

        // when
        BrowseFacets facets = browse.facets(STORE_ID, null, Set.of(), "kabel");

        // then
        assertThat(facets.byCategory()).containsOnly(entry("21", 1));
        assertThat(facets.bySupplier()).containsOnly(entry("AB", 1));
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
