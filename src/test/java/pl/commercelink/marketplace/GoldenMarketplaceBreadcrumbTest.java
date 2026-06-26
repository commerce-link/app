package pl.commercelink.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
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
import pl.commercelink.starter.localization.EnumLocalizer;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.taxonomy.ProductGroup;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoldenMarketplaceBreadcrumbTest {

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
    @Mock private MarketplaceOfferExportRepository marketplaceOfferExportRepository;
    @Mock private EnumLocalizer enumLocalizer;

    @Mock private Store store;
    @Mock private ProductCatalog catalog;
    @Mock private Pricelist pricelist;
    @Mock private InventoryView inventoryView;
    @Mock private MarketplaceProvider provider;

    @InjectMocks
    private MarketplaceOfferExportEventListener listener;

    @BeforeEach
    void setUpHappyPath() {
        // given
        ReflectionTestUtils.setField(listener, "removalRetryCount", 3);

        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(store.hasActiveMarketplaceIntegration(MARKETPLACE)).thenReturn(true);
        when(store.getStoreId()).thenReturn(STORE_ID);

        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(catalog.isMarketplaceExportEnabled(MARKETPLACE)).thenReturn(true);
        when(catalog.getCatalogId()).thenReturn(CATALOG_ID);

        when(pricelistRepository.find(CATALOG_ID, PRICELIST_ID)).thenReturn(pricelist);
        when(inventory.withEnabledSuppliersAndWarehouseData(STORE_ID)).thenReturn(inventoryView);

        when(providerFactory.get(store, MARKETPLACE)).thenReturn(provider);

        when(marketplaceOfferExportRepository.loadPreviousExport(STORE_ID, CATALOG_ID, MARKETPLACE))
                .thenReturn(List.of());
    }

    @Test
    void breadcrumbIsLocalizedProductGroupSlashLocalizedCategoryPlural() {
        // given
        Product product = product("pim-A", "EAN-A");
        configureCategoryWith(ProductCategory.CPU, warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        when(enumLocalizer.localize(ProductGroup.PcComponents)).thenReturn("Podzespoly komputerowe");
        when(enumLocalizer.localize(ProductCategory.CPU, "plural")).thenReturn("Procesory");

        // when
        listener.handleMessage(request());

        // then
        MarketplaceOffer offer = capturePublishedOffers().get(0);
        assertThat(offer.categoryName()).isEqualTo("Podzespoly komputerowe / Procesory");
    }

    @Test
    void breadcrumbUsesProductGroupOfTheCategoryNotTheCategoryItself() {
        // given
        Product product = product("pim-B", "EAN-B");
        configureCategoryWith(ProductCategory.Laptops, warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        when(enumLocalizer.localize(ProductGroup.Computers)).thenReturn("Komputery");
        when(enumLocalizer.localize(ProductCategory.Laptops, "plural")).thenReturn("Laptopy");

        // when
        listener.handleMessage(request());

        // then
        MarketplaceOffer offer = capturePublishedOffers().get(0);
        assertThat(offer.categoryName()).isEqualTo("Komputery / Laptopy");
    }

    @Test
    void breadcrumbSeparatorIsSpaceSlashSpaceEvenWhenLocalizedPartsAreEmpty() {
        // given
        Product product = product("pim-C", "EAN-C");
        configureCategoryWith(ProductCategory.GPU, warehouseDefinition(5), product, 10);
        priceFor(product, 100, 2);
        when(enumLocalizer.localize(ProductGroup.PcComponents)).thenReturn("");
        when(enumLocalizer.localize(ProductCategory.GPU, "plural")).thenReturn("");

        // when
        listener.handleMessage(request());

        // then
        MarketplaceOffer offer = capturePublishedOffers().get(0);
        assertThat(offer.categoryName()).isEqualTo(" / ");
    }

    @Test
    void breadcrumbIsAttachedIdenticallyToEveryOfferInTheSameCategory() {
        // given
        Product productA = product("pim-A", "EAN-A");
        Product productB = product("pim-B", "EAN-B");
        configureCategoryWith(ProductCategory.Tablets, warehouseDefinition(5), 10, productA, productB);
        priceFor(productA, 100, 2);
        priceFor(productB, 200, 3);
        when(enumLocalizer.localize(ProductGroup.SmartphonesTablets)).thenReturn("Smartfony i tablety");
        when(enumLocalizer.localize(ProductCategory.Tablets, "plural")).thenReturn("Tablety");

        // when
        listener.handleMessage(request());

        // then
        List<MarketplaceOffer> offers = capturePublishedOffers();
        assertThat(offers).hasSize(2);
        assertThat(offers).allMatch(o -> o.categoryName().equals("Smartfony i tablety / Tablety"));
    }

    // --- helpers ---------------------------------------------------------

    private MarketplaceOfferExportRequest request() {
        return new MarketplaceOfferExportRequest(MARKETPLACE, STORE_ID, CATALOG_ID, PRICELIST_ID);
    }

    private Product product(String pimId, String ean) {
        return new Product(CATEGORY_ID, pimId, ean, "MFN-" + pimId, "Brand", "Label", "Name-" + pimId,
                ProductCategory.Laptops.name(), "default");
    }

    private MarketplaceDefinition warehouseDefinition(int minWarehouseQty) {
        MarketplaceDefinition def = new MarketplaceDefinition(MARKETPLACE, 1.0, 0, 0, 0, minWarehouseQty);
        def.setEnabled(true);
        return def;
    }

    private void configureCategoryWith(ProductCategory productCategory, MarketplaceDefinition def, Product product, int warehouseQty) {
        configureCategoryWith(productCategory, def, warehouseQty, product);
    }

    private void configureCategoryWith(ProductCategory productCategory, MarketplaceDefinition def, int warehouseQty, Product... products) {
        CategoryDefinition category = new CategoryDefinition();
        category.setCategoryId(CATEGORY_ID);
        category.setCategory(productCategory.name());
        category.setMarketplaceDefinitions(List.of(def));

        when(catalog.getCategories()).thenReturn(List.of(category));
        when(productRepository.findAllProductsWithPimId(CATEGORY_ID, true)).thenReturn(List.of(products));

        for (Product product : products) {
            MatchedInventory matched = mockMatchedInventoryWithWarehouseQty(warehouseQty);
            when(inventoryView.findByProduct(product)).thenReturn(matched);
        }
    }

    private MatchedInventory mockMatchedInventoryWithWarehouseQty(int warehouseQty) {
        MatchedInventory matched = mock(MatchedInventory.class);
        InventoryItem item = mock(InventoryItem.class);
        when(item.qty()).thenReturn(warehouseQty);
        when(matched.getInventoryItemsFromSupplier(SupplierRegistry.WAREHOUSE)).thenReturn(List.of(item));
        return matched;
    }

    private void priceFor(Product product, long price, int deliveryDays) {
        AvailabilityAndPrice ap = new AvailabilityAndPrice(
                product.getPimId(), product.getEan(), product.getManufacturerCode(),
                product.getBrand(), product.getSubcategory(), product.getName(),
                product.getCategory(), price, 0L, deliveryDays, 0L
        );
        when(pricelist.findByPimId(product.getPimId())).thenReturn(Optional.of(ap));
    }

    @SuppressWarnings("unchecked")
    private List<MarketplaceOffer> capturePublishedOffers() {
        ArgumentCaptor<List<MarketplaceOffer>> captor = ArgumentCaptor.forClass(List.class);
        verify(provider).exportOffers(captor.capture(), any());
        return captor.getValue();
    }
}
