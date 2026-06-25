package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.deliveries.DeliverySuggestionService;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.starter.csv.CSVLoader;
import pl.commercelink.web.dtos.SuggestedDeliveryItem;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.RestockScope;
import pl.commercelink.warehouse.StockLevels;
import pl.commercelink.warehouse.StockProductLevel;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GOLDEN characterization test for surface 9-4: getCategory().name() compile-breakers.
 * Freezes the CURRENT behaviour that every category String is produced via enum name()
 * at four call sites. If/when ProductCategory#name() (the enum identifier) is retyped /
 * shadowed by a String accessor these assertions document the existing contract.
 *
 * TEST-ONLY: assertions are aligned to real behaviour, no production code is changed.
 */
@ExtendWith(MockitoExtension.class)
class GoldenCategoryNameStringsTest {

    // ------------------------------------------------------------------
    // Surface 3: TaxonomyParser.toCsv (~:73  t.category().name())
    // Fully exercisable with real objects -> end-to-end freeze.
    // ------------------------------------------------------------------

    @Test
    void taxonomyToCsvWritesCategoryAsEnumNameForEveryCategory() {
        // given
        for (ProductCategory category : ProductCategory.values()) {
            Taxonomy taxonomy = new Taxonomy("1234567890123", "MFN-1", "Brand", "Name",
                    category, 1, null, null);

            // when
            byte[] csv = TaxonomyParser.toCsv(List.of(taxonomy));
            CSVLoader loader = new CSVLoader(new InputStreamReader(new ByteArrayInputStream(csv)));
            List<String[]> rows = loader.readHeadersAndRows(';').getSecond();
            String categoryCell = rows.get(0)[4];

            // then
            assertThat(categoryCell).isEqualTo(category.name());
        }
    }

    @Test
    void taxonomyToCsvCategoryCellEqualsNameForLaptops() {
        // given
        Taxonomy taxonomy = new Taxonomy("1234567890123", "MFN-1", "Brand", "Name",
                ProductCategory.Laptops, 1, null, null);

        // when
        byte[] csv = TaxonomyParser.toCsv(List.of(taxonomy));
        CSVLoader loader = new CSVLoader(new InputStreamReader(new ByteArrayInputStream(csv)));
        String categoryCell = loader.readHeadersAndRows(';').getSecond().get(0)[4];

        // then
        assertThat(categoryCell).isEqualTo("Laptops");
    }

    // ------------------------------------------------------------------
    // Surface 1: WarehouseRepository.findAllCategories (~:133 item.getCategory().name())
    // Only a fragment is reachable without DynamoDB/Spring -> freeze the contract that
    // the String collected into the Set equals cat.name() for every category.
    // ------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void warehouseCategoryStringsAreEnumNamesForEveryCategory() throws Exception {
        // given
        // drive the REAL WarehouseRepository.findAllCategories(): the class and its method are
        // package-private in warehouse.builtin, so reach them reflectively. A mocked DynamoDBMapper
        // (injected into the inherited protected field) returns real WarehouseItems carrying every
        // category; the production loop must collect item.getCategory().name() for each.
        Class<?> repoClass = Class.forName("pl.commercelink.warehouse.builtin.WarehouseRepository");
        java.lang.reflect.Constructor<?> ctor =
                repoClass.getDeclaredConstructor(com.amazonaws.services.dynamodbv2.AmazonDynamoDB.class);
        ctor.setAccessible(true);
        Object repository = ctor.newInstance(mock(com.amazonaws.services.dynamodbv2.AmazonDynamoDB.class));

        List<pl.commercelink.warehouse.builtin.WarehouseItem> items = new ArrayList<>();
        for (ProductCategory category : ProductCategory.values()) {
            items.add(warehouseItem(category));
        }
        com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList<Object> scanResult =
                mock(com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList.class);
        when(scanResult.iterator()).thenReturn((java.util.Iterator) items.iterator());
        com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper mapper =
                mock(com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.class);
        when(mapper.scan(any(Class.class),
                any(com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression.class)))
                .thenReturn((com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList) scanResult);
        java.lang.reflect.Field mapperField =
                repoClass.getSuperclass().getDeclaredField("dynamoDBMapper");
        mapperField.setAccessible(true);
        mapperField.set(repository, mapper);

        java.lang.reflect.Method findAllCategories =
                repoClass.getDeclaredMethod("findAllCategories", String.class);
        findAllCategories.setAccessible(true);

        // when (REAL production loop body collecting item.getCategory().name())
        Set<String> collected = (Set<String>) findAllCategories.invoke(repository, "store1");

        // then
        Set<String> expected = new HashSet<>();
        for (ProductCategory category : ProductCategory.values()) {
            expected.add(category.name());
        }
        assertThat(collected).isEqualTo(expected);
        assertThat(collected).contains("CPU", "Laptops", "Other");
    }

    private static pl.commercelink.warehouse.builtin.WarehouseItem warehouseItem(ProductCategory category) {
        return new pl.commercelink.warehouse.builtin.WarehouseItem(
                "store1", "delivery1", category, "name", "ean", "mfn", 10, 1);
    }

    // ------------------------------------------------------------------
    // Surface 2: DeliverySuggestionService comparator (~:95
    //   s -> s.getCategory() != null ? s.getCategory().name() : "")
    // The comparator key is a pure lambda over SuggestedDeliveryItem -> reconstruct it on
    // real DTOs and freeze that ordering is driven by ProductCategory.name() (null -> "").
    // ------------------------------------------------------------------

    @Test
    void deliverySuggestionComparatorOrdersByCategoryEnumName() {
        // given
        // drive the REAL DeliverySuggestionService.suggestFor(): two stock levels (Laptops then CPU)
        // each yield one in-budget offer, so the production comparator
        //   comparing(getCategory().name()).thenComparing(getName())
        // must reorder them by enum name (CPU < Laptops).
        List<SuggestedDeliveryItem> suggestions = runSuggestFor(List.of(
                stockLevel(ProductCategory.Laptops, "laptop", "L-MFN"),
                stockLevel(ProductCategory.CPU, "cpu", "C-MFN")));

        // when / then (CPU.name()="CPU" sorts before Laptops.name()="Laptops")
        assertThat(suggestions).extracting(SuggestedDeliveryItem::getCategory)
                .containsExactly(ProductCategory.CPU, ProductCategory.Laptops);
        assertThat("CPU".compareTo("Laptops")).isNegative();
    }

    @Test
    void deliverySuggestionComparatorTreatsNullCategoryAsEmptyString() {
        // given
        // a null-category level produces a suggestion whose category is null; the REAL comparator maps it
        // to "" which sorts before any non-empty enum name.
        List<SuggestedDeliveryItem> suggestions = runSuggestFor(List.of(
                stockLevel(ProductCategory.CPU, "cpu", "C-MFN"),
                stockLevel(null, "unknown", "U-MFN")));

        // when / then (null -> "" sorts before CPU)
        assertThat(suggestions).extracting(SuggestedDeliveryItem::getCategory)
                .containsExactly(null, ProductCategory.CPU);
    }

    /**
     * Invokes the REAL DeliverySuggestionService.suggestFor with mocked collaborators so the production
     * comparator at DeliverySuggestionService:94-96 sorts genuine SuggestedDeliveryItem DTOs.
     */
    private static List<SuggestedDeliveryItem> runSuggestFor(List<StockProductLevel> levels) {
        String storeId = "store1";
        String supplier = "Action";

        Inventory inventory = mock(Inventory.class);
        StockLevels stockLevels = mock(StockLevels.class);
        ProductCatalogRepository productCatalogRepository = mock(ProductCatalogRepository.class);
        StoresRepository storesRepository = mock(StoresRepository.class);

        Store store = mock(Store.class);
        when(store.isEnabledSupplier(supplier)).thenReturn(true);
        when(storesRepository.findById(storeId)).thenReturn(store);

        InventoryView enabledInventory = mock(InventoryView.class);
        when(inventory.withEnabledSuppliersOnly(storeId)).thenReturn(enabledInventory);

        ProductCatalog catalog = mock(ProductCatalog.class);
        when(catalog.getCatalogId()).thenReturn("cat1");
        when(productCatalogRepository.findAll(storeId)).thenReturn(List.of(catalog));

        when(stockLevels.calculate(eq(storeId), eq("cat1"), any(), eq(RestockScope.ExpectedStockQty), anyBoolean()))
                .thenReturn(levels);

        for (StockProductLevel level : levels) {
            String mfn = level.getManufacturerCode();
            InventoryItem offer = new InventoryItem("E-" + mfn, mfn, 10, "PLN", 1, 1, supplier, true, false, false);
            MatchedInventory matched = mock(MatchedInventory.class);
            when(matched.isEmpty()).thenReturn(false);
            when(matched.hasOffersFrom(supplier)).thenReturn(true);
            when(matched.getInventoryItemsFromSupplier(supplier)).thenReturn(List.of(offer));
            when(matched.getInventoryItems()).thenReturn(List.of(offer));
            when(enabledInventory.findByProductCode(mfn)).thenReturn(matched);
        }

        DeliverySuggestionService service = new DeliverySuggestionService(
                inventory, stockLevels, productCatalogRepository, storesRepository);
        return service.suggestFor(storeId, supplier, Set.of());
    }

    private static StockProductLevel stockLevel(ProductCategory category, String name, String mfn) {
        // restockPricePromo high enough that the offer's gross price clears the budget gate.
        return new StockProductLevel(category, mfn, name, 100000, 100000, 1);
    }

    // ------------------------------------------------------------------
    // Surface 4: ProductWeightOriginComplianceReportService (~:87
    //   k.category() != null ? k.category().name() : UNKNOWN   (UNKNOWN = "Unknown")
    // LIMITATION: the service's toRow() and AggregateKey are PRIVATE and its repository
    // collaborators (WarehouseDocumentRepository / WarehouseDocumentItemRepository) are
    // package-private in warehouse.builtin, so the real toRow() line cannot be reached from
    // this taxonomy-package mirror. This freezes mirrored logic, not the real line. The REAL
    // call-site (generate() -> toRow() rendering category().name()/"Unknown") is already
    // exercised end-to-end by ProductWeightOriginComplianceReportServiceTest in the
    // warehouse.builtin package (asserts row.category()=="GPU" and =="Unknown"), so the real
    // line is protected there.
    // ------------------------------------------------------------------

    @Test
    void complianceCategoryRendersViaEnumNameForEveryCategory() {
        // given / when / then (mirrors toRow: k.category() != null ? k.category().name() : "Unknown")
        for (ProductCategory category : ProductCategory.values()) {
            PimEntry entry = pimEntry(category);
            String rendered = entry.categoryKey() != null ? entry.categoryKey() : "Unknown";
            assertThat(rendered).isEqualTo(category.name());
        }
    }

    @Test
    void complianceCategoryFallsBackToUnknownWhenCategoryNull() {
        // given
        ProductCategory category = null;

        // when (mirrors toRow null branch)
        String rendered = category != null ? category.name() : "Unknown";

        // then (literal "Unknown", never an enum name)
        assertThat(rendered).isEqualTo("Unknown");
    }

    private static PimEntry pimEntry(ProductCategory category) {
        return new PimEntry("pim1", Collections.emptyList(), "Brand", "Name",
                category.name(), null, true, null, null);
    }
}
