package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.products.ProductData;
import pl.commercelink.products.ProductDataResolver;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static pl.commercelink.pim.ScopedPimCatalog.globalEntry;
import static pl.commercelink.pim.ScopedPimCatalog.storeEntry;

class InventoryViewProductDataTest {

    private static final String STORE_ID = "s1";
    private static final String CPU_EAN = "5900000000101";
    private static final String GPU_EAN = "5900000000102";
    private static final String SSD_EAN = "5900000000103";

    private final SupplierRegistry supplierRegistry = mock(SupplierRegistry.class);
    private final TaxonomyCache taxonomy = spy(new TaxonomyCache(mock(TaxonomyRepository.class)));

    @Test
    void listingReadsProductDataInTwoCallsHoweverManyRowsItLists() {
        // given
        taxonomy.add(record(CPU_EAN, "CPU one", "989"));
        taxonomy.add(record(GPU_EAN, "CPU two", "989"));
        taxonomy.add(record(SSD_EAN, "SSD", "1563"));
        ProductDataResolver resolver = spy(new ProductDataResolver(ScopedPimCatalog.of(), taxonomy));
        InventoryView view = view(STORE_ID, resolver, group(CPU_EAN), group(GPU_EAN), group(SSD_EAN));

        // when
        Map<String, Collection<MatchedInventory>> listing = view.findAllByProductCategoryIds(List.of("989"));
        listing.get("989").forEach(MatchedInventory::getProductData);
        listing.get("989").forEach(MatchedInventory::getTaxonomy);

        // then
        assertThat(listing.get("989")).extracting(row -> row.getProductData().name())
                .containsExactlyInAnyOrder("CPU one", "CPU two");
        verify(resolver, times(2)).ofGroups(eq(STORE_ID), any());
        verify(taxonomy, times(2)).findAll(any());
    }

    @Test
    void listingPicksRowsByTheCategoryOfTheResolvedProductNotOfTheTaxonomyRecord() {
        // given
        taxonomy.add(record(CPU_EAN, "Listed as a CPU by a supplier", "989"));
        taxonomy.add(record(GPU_EAN, "GPU", "170"));
        PimEntry reviewedAsGpu = new PimEntry("g1", ScopedPimCatalog.identifiers(CPU_EAN, null), "Brand", "Really a GPU",
                "Karty graficzne", null, true, null, null, "170");
        InventoryView view = view(STORE_ID, new ProductDataResolver(ScopedPimCatalog.of(reviewedAsGpu), taxonomy),
                group(CPU_EAN), group(GPU_EAN));

        // when
        Map<String, Collection<MatchedInventory>> listing = view.findAllByProductCategoryIds(List.of("989", "170"));

        // then
        assertThat(listing.get("989")).isEmpty();
        assertThat(listing.get("170")).extracting(row -> row.getProductData().name())
                .containsExactlyInAnyOrder("Really a GPU", "GPU");
    }

    @Test
    void listedRowStillCarriesItsTaxonomyRecord() {
        // given
        taxonomy.add(record(CPU_EAN, "Taxonomy name", "989"));
        PimEntry entry = new PimEntry("g1", ScopedPimCatalog.identifiers(CPU_EAN, null), "Brand", "Global g1",
                "Procesory", null, true, null, null, "989");
        InventoryView view = view(STORE_ID, new ProductDataResolver(ScopedPimCatalog.of(entry), taxonomy), group(CPU_EAN));

        // when
        MatchedInventory row = view.findAllByProductCategoryIds(List.of("989")).get("989").iterator().next();

        // then
        assertThat(row.getProductData().name()).isEqualTo("Global g1");
        assertThat(row.getTaxonomy().name()).isEqualTo("Taxonomy name");
    }

    @Test
    void singleLookupReadsNoProductDataUntilSomeoneAsksAndThenReadsItOnce() {
        // given
        taxonomy.add(record(CPU_EAN, "CPU one", "989"));
        ProductDataResolver resolver = spy(new ProductDataResolver(ScopedPimCatalog.of(), taxonomy));
        InventoryView view = view(STORE_ID, resolver, group(CPU_EAN));

        // when
        MatchedInventory row = view.findByEan(CPU_EAN);
        row.getLowestPrice();

        // then
        verifyNoInteractions(resolver);

        // when
        ProductData first = row.getProductData();
        ProductData second = row.getProductData();
        row.getTaxonomy();

        // then
        assertThat(first.name()).isEqualTo("CPU one");
        assertThat(second).isSameAs(first);
        verify(resolver, times(1)).ofGroups(eq(STORE_ID), any());
    }

    @Test
    void rowsFoundOneByOneAreDescribedInOneCall() {
        // given
        taxonomy.add(record(CPU_EAN, "CPU one", "989"));
        taxonomy.add(record(GPU_EAN, "GPU", "170"));
        ProductDataResolver resolver = spy(new ProductDataResolver(ScopedPimCatalog.of(), taxonomy));
        InventoryView view = view(STORE_ID, resolver, group(CPU_EAN), group(GPU_EAN));
        List<MatchedInventory> rows = List.of(view.findByEan(CPU_EAN), view.findByEan(GPU_EAN));

        // when
        view.loadProductData(rows);
        view.loadProductData(rows);

        // then
        assertThat(rows).extracting(row -> row.getProductData().name()).containsExactly("CPU one", "GPU");
        verify(resolver, times(1)).ofGroups(eq(STORE_ID), any());
    }

    @Test
    void storeViewDescribesARowByTheStoreEntryAndTheGlobalViewByTheGlobalOne() {
        // given
        ProductDataResolver resolver = new ProductDataResolver(ScopedPimCatalog.of(
                globalEntry("g1", CPU_EAN, "M-1", "CPU"), storeEntry("s1-1", STORE_ID, CPU_EAN, "M-1", "CPU")), taxonomy);

        // when
        ProductData inStore = view(STORE_ID, resolver, group(CPU_EAN)).findByEan(CPU_EAN).getProductData();
        ProductData inGlobal = view(null, resolver, group(CPU_EAN)).findByEan(CPU_EAN).getProductData();

        // then
        assertThat(inStore.name()).isEqualTo("Store s1-1");
        assertThat(inGlobal.name()).isEqualTo("Global g1");
    }

    @Test
    void rowWithoutAnyDataIsDescribedAsUnknown() {
        // given
        InventoryView view = view(STORE_ID, new ProductDataResolver(ScopedPimCatalog.of(), taxonomy), group(CPU_EAN));

        // when
        MatchedInventory row = view.findByEan(CPU_EAN);

        // then
        assertThat(row.getProductData()).isEqualTo(ProductData.UNKNOWN);
        assertThat(row.getTaxonomy()).isSameAs(Taxonomy.EMPTY);
    }

    @Test
    void rowCutToAPricePointKeepsItsProductData() {
        // given
        taxonomy.add(record(CPU_EAN, "CPU one", "989"));
        ProductDataResolver resolver = spy(new ProductDataResolver(ScopedPimCatalog.of(), taxonomy));
        MatchedInventory row = view(STORE_ID, resolver, group(CPU_EAN)).findByEan(CPU_EAN);
        row.getProductData();

        // when
        ProductData atPricePoint = row.atPricePoint(10_000).getProductData();

        // then
        assertThat(atPricePoint.name()).isEqualTo("CPU one");
        verify(resolver, times(1)).ofGroups(eq(STORE_ID), any());
    }

    @Test
    void groupOfThePoolHasNoProductDataOfItsOwn() {
        // given
        MatchedInventory poolGroup = group(CPU_EAN);

        // when / then
        assertThatThrownBy(poolGroup::getProductData).isInstanceOf(IllegalStateException.class);
    }

    private InventoryView view(String storeId, ProductDataResolver resolver, MatchedInventory... groups) {
        InventoryIndex globalIndex = InventoryIndex.of(List.of(groups));
        return new InventoryView(storeId, resolver, globalIndex, InventoryIndex.of(List.of()), StoreEntryKeys.none(),
                supplierRegistry, GroupInventorySource.global(globalIndex, supplier -> true));
    }

    private MatchedInventory group(String ean) {
        return new MatchedInventory(new InventoryKey(ean, "M-" + ean),
                List.of(new InventoryItem(ean, "M-" + ean, 100.0, "PLN", 5, 1, "Elko", true, true, false)),
                supplierRegistry);
    }

    private static Taxonomy record(String ean, String name, String categoryId) {
        return new Taxonomy(ean, "M-" + ean, "Brand", name, "Category " + categoryId, 1, null, null, null, categoryId);
    }
}
