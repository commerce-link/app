package pl.commercelink.products;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.AvailabilityAndPriceList;
import pl.commercelink.pricelist.RollingPriceAggregateRepository;
import pl.commercelink.pricelist.SellingPriceHistoryRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DynamicCategoryPricelistTest {

    private static final String STORE_ID = "store-1";
    private static final String CATALOG_ID = "catalog-1";
    private static final String CATEGORY_ID = "category-1";
    private static final String PIM_ID = "pim-1";

    @Mock
    private InventoryView inventory;

    @Mock
    private PimCatalog pimCatalog;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductCatalogRepository productCatalogRepository;

    @Mock
    private RollingPriceAggregateRepository rollingPriceAggregateRepository;

    @Mock
    private SellingPriceHistoryRepository sellingPriceHistoryRepository;

    @Mock
    private MatchedInventory matchedInventory;

    private ProductRecommendationEngine recommendationEngine;

    @BeforeEach
    void setUp() {
        recommendationEngine = new ProductRecommendationEngine(pimCatalog, productRepository);

        when(rollingPriceAggregateRepository.loadAll()).thenReturn(Map.of());
        when(sellingPriceHistoryRepository.load(STORE_ID, CATALOG_ID)).thenReturn(Map.of());
        when(productRepository.findAll(CATEGORY_ID)).thenReturn(List.of());
        when(pimCatalog.findByPimIdOrGtinsOrMpns(eq(PIM_ID), any(), any())).thenReturn(Optional.of(pimEntry()));

        when(inventory.findAllByProductCategory("GPU")).thenReturn(List.of(matchedInventory));
        when(inventory.findByProduct(any())).thenReturn(matchedInventory);

        mockInventoryWithSingleGraphicsCard();
    }

    @Test
    void dynamicDefinitionWithIcecatLeafCategoryGeneratesTheSamePricelistAsLegacyEnumCategory() {
        // given / when
        List<AvailabilityAndPrice> fromIcecatLeaf = generatePricelistFor("Karty graficzne");
        List<AvailabilityAndPrice> fromLegacyEnum = generatePricelistFor("GPU");

        // then
        assertThat(fromLegacyEnum).hasSize(1);
        assertThat(fromIcecatLeaf)
                .usingRecursiveFieldByFieldElementComparator()
                .isEqualTo(fromLegacyEnum);
    }

    @Test
    void dynamicDefinitionWithCategoryOutsideTheBridgeGeneratesEmptyPricelist() {
        // given / when
        List<AvailabilityAndPrice> pricelist = generatePricelistFor("Kołdry");

        // then
        assertThat(pricelist).isEmpty();
    }

    private List<AvailabilityAndPrice> generatePricelistFor(String category) {
        ProductCatalog catalog = mock(ProductCatalog.class);
        when(catalog.getCategories()).thenReturn(List.of(dynamicDefinition(category)));
        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);

        AvailabilityAndPriceList availabilityAndPriceList = new AvailabilityAndPriceList(
                inventory,
                productCatalogRepository,
                productRepository,
                recommendationEngine,
                rollingPriceAggregateRepository,
                sellingPriceHistoryRepository
        );

        return availabilityAndPriceList.generate(STORE_ID, CATALOG_ID);
    }

    private CategoryDefinition dynamicDefinition(String category) {
        CategoryDefinition definition = new CategoryDefinition();
        definition.setCategoryId(CATEGORY_ID);
        definition.setName("Karta graficzna");
        definition.setType(CategoryDefinitionType.Dynamic);
        definition.setCategory(category);
        definition.setStockDefinition(new StockDefinition(2, 5, 20));
        definition.setPriceDefinitions(List.of(new PriceDefinition(1.2, 100, 0, 0, 0, PriceDefinition.DEFAULT_PRICING_GROUP)));
        definition.setAvailabilityDefinition(new AvailabilityDefinition(1, 2));
        return definition;
    }

    private void mockInventoryWithSingleGraphicsCard() {
        when(matchedInventory.hasAnyOffers()).thenReturn(true);
        when(matchedInventory.getInventoryKey()).thenReturn(new InventoryKey(PIM_ID));
        when(matchedInventory.getTaxonomy()).thenReturn(taxonomy());

        when(matchedInventory.getLowestPrice()).thenReturn(new Price(1000, 1230));
        when(matchedInventory.getLowestPrice(true, null)).thenReturn(new Price(1000, 1230));
        when(matchedInventory.getLowestPrice(SupplierType.Distributor)).thenReturn(new Price(1000, 1230));
        when(matchedInventory.getLowestPrice(SupplierType.Retailer)).thenReturn(new Price(0, 0));
        when(matchedInventory.getMedianPrice()).thenReturn(new Price(1100, 1353));
        when(matchedInventory.canBeFulfilledFromWarehouseAtPricePoint(anyDouble())).thenReturn(true);

        when(matchedInventory.hasOffersFrom(anyString())).thenReturn(false);
        when(matchedInventory.hasOffersFrom(SupplierRegistry.WAREHOUSE)).thenReturn(false);
        when(matchedInventory.hasOffersFromLocalSuppliers()).thenReturn(true);
        when(matchedInventory.hasOffersFromMultipleSuppliers(anyInt())).thenReturn(true);
        when(matchedInventory.hasTotalMinQty(anyInt())).thenReturn(true);
        when(matchedInventory.getNoOfSuppliersWithProduct(any())).thenReturn(3L);
        when(matchedInventory.getTotalAvailableQty(any(SupplierType.class))).thenReturn(50L);
        when(matchedInventory.getTotalAvailableQty(anyLong())).thenReturn(5L);
        when(matchedInventory.getEstimatedDeliveryDays(anyLong())).thenReturn(3);
    }

    private PimEntry pimEntry() {
        return new PimEntry(PIM_ID, List.of(), "Gigabyte", "Gigabyte RTX 5070", "GPU", "RTX 5070",
                true, null, null);
    }

    private Taxonomy taxonomy() {
        return new Taxonomy("5900000000001", "MFN-1", "Gigabyte", "Gigabyte RTX 5070", "GPU", 1, null, null);
    }
}
