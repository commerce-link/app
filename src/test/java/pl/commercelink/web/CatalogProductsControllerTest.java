package pl.commercelink.web;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.MarketplaceDefinition;
import pl.commercelink.products.PimCategoryOptions;
import pl.commercelink.products.PriceDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductRecommendation;
import pl.commercelink.products.ProductRecommendationEngine;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;
import pl.commercelink.testsupport.RetryingOptimisticLockingExecutor;
import pl.commercelink.web.catalog.CatalogAccess;
import pl.commercelink.web.catalog.CategoryFilter;
import pl.commercelink.web.catalog.CategoryPageModel;
import pl.commercelink.web.catalog.ProductRow;
import pl.commercelink.web.catalog.ProductStatus;
import pl.commercelink.web.catalog.RecommendationRow;
import pl.commercelink.web.dtos.ProductForm;
import pl.commercelink.web.dtos.ProductsBulkAddForm;
import pl.commercelink.web.settings.ConfirmAction;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class CatalogProductsControllerTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private CatalogAccess access;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductRecommendationEngine recommendationEngine;
    @Mock
    private Inventory inventory;
    @Mock
    private InventoryView inventoryView;
    @Mock
    private MatchedInventory emptyInventory;
    @Mock
    private MarketplaceConnections marketplaces;
    @Mock
    private PimCategoryOptions pimCategoryOptions;
    @Mock
    private SupplierLabels supplierLabels;
    @Mock
    private SupplierLabelMap supplierLabelMap;
    @Mock
    private PimCatalog pimCatalog;
    @Mock
    private BrandMapper brandMapper;
    @Mock
    private MessageSource messageSource;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private Store store;
    @Mock
    private OptimisticLockingExecutor optimisticLockingExecutor;

    private ProductCatalog catalog;
    private CategoryDefinition gpu;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        CustomUser user = new CustomUser(null, null, Map.of("storeId", STORE_ID, "role", "ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        catalog = new ProductCatalog(STORE_ID, "Podzespoły");
        catalog.setCatalogId("c1");
        gpu = new CategoryDefinition().withName("GPU").withGeneratedId();
        catalog.getCategories().add(gpu);
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(catalog);
        when(access.requireCategory(catalog, gpu.getCategoryId())).thenReturn(gpu);
        lenient().when(pimCategoryOptions.namesOf(any())).thenReturn(List.of());
        lenient().when(marketplaces.displayName(anyString())).thenAnswer(call -> call.getArgument(0));
        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(call -> call.getArgument(0));
        lenient().when(storesRepository.findById(STORE_ID)).thenReturn(store);
        // The save looks every row up in the inventory; by default nothing is there and the PIM is asked directly.
        lenient().when(emptyInventory.isEmpty()).thenReturn(true);
        lenient().when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(inventoryView);
        lenient().when(inventoryView.findByInventoryKey(any())).thenReturn(emptyInventory);
        lenient().when(store.getMarketplaces()).thenReturn(List.of());
        lenient().when(store.getEnabledCategories()).thenReturn(List.of());
        lenient().when(pimCategoryOptions.namedOptions(any(), any())).thenReturn(List.of());
        lenient().when(pimCategoryOptions.ancestorsOfNames(any())).thenReturn(List.of());
        // Behaves like the real proxy: conflicts retried, anything else wrapped (RetryingOptimisticLockingExecutorTest).
        lenient().when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.retryingModifyAndSave(3));
        mvc = MockMvcBuilders.standaloneSetup(new CatalogProductsController(access, productRepository, storesRepository,
                recommendationEngine, inventory, marketplaces, pimCategoryOptions, supplierLabels, pimCatalog,
                brandMapper, messageSource, optimisticLockingExecutor)).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private String categoryPath() {
        return "/dashboard/catalogs/c1/category/" + gpu.getCategoryId();
    }

    @Test
    void manualCategoryRendersAllProductsWithCountsAndTheStartFilterFromTheQuery() throws Exception {
        // given
        Product a = new Product(gpu.getCategoryId(), "pim", "1", "m", "MSI", "RTX 5070", "MSI RTX 5070", "Default");
        Product b = new Product(gpu.getCategoryId(), "pim", "2", "m", "ASUS", "RTX 5060", "ASUS RTX 5060", "Default");
        b.setEnabled(false);
        when(productRepository.findAll(gpu.getCategoryId())).thenReturn(List.of(a, b));

        // when
        var result = mvc.perform(get(categoryPath()).param("status", "disabled"))
                .andExpect(status().isOk())
                .andExpect(view().name("catalog/category"))
                .andExpect(model().attribute("bulkAction", categoryPath() + "/products/bulk"))
                .andExpect(model().attribute("addHref", categoryPath() + "/products/add"))
                .andExpect(model().attribute("backHref", "/dashboard/catalogs/c1"))
                .andReturn();

        // then
        CategoryPageModel page = (CategoryPageModel) result.getModelAndView().getModel().get("page");
        assertThat(page.rows()).extracting(ProductRow::name).containsExactly("ASUS RTX 5060", "MSI RTX 5070");
        assertThat(page.statusCounts()).containsEntry(ProductStatus.ACTIVE, 1).containsEntry(ProductStatus.DISABLED, 1);
        // The default is what the page shows without a filter in the address; the script starts from the address and
        // drops a parameter only when it equals this default, so "status=all" (and "status=disabled") stay in it.
        assertThat(result.getModelAndView().getModel().get("filterDefault")).isEqualTo("status:active feature:all label:all");
        verify(recommendationEngine, never()).getRecommendations(any(), any());
    }

    /**
     * The start filter is a space-separated list of {@code group:value} pairs, so a label with a space in it would be
     * read as two pairs; the label group is only declared here and its value is taken from the address by the script.
     */
    @Test
    void anUnknownStartFilterFallsBackToTheActiveProductsAndTheLabelIsNeverInlined() throws Exception {
        // given
        when(productRepository.findAll(gpu.getCategoryId())).thenReturn(List.of());

        // when / then
        mvc.perform(get(categoryPath()).param("status", "deleted").param("feature", "colour").param("label", "RTX 5070"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filterDefault", "status:active feature:all label:all"));
    }

    /** The row links and the product pages behind them know the filter to come back to. */
    @Test
    void theCategoryPageCarriesItsFilterIntoTheProductLinks() throws Exception {
        // given
        when(productRepository.findAll(gpu.getCategoryId())).thenReturn(List.of());

        // when / then
        mvc.perform(get(categoryPath()).param("status", "all").param("feature", "stock").param("label", "RTX 5070")
                        .param("q", "msi"))
                .andExpect(model().attribute("filterQuery", "?status=all&feature=stock&label=RTX+5070&q=msi"));
    }

    @Test
    void dynamicCategoryRowsComeFromTheRecommendationEngine() throws Exception {
        // given
        gpu.setType(CategoryDefinitionType.Dynamic);
        gpu.setPimCategoryIds(List.of("pim-gpu"));
        ProductRecommendation recommendation = mock(ProductRecommendation.class);
        when(recommendation.hasPimId()).thenReturn(true);
        when(recommendation.getLabel()).thenReturn("RTX 5070");
        when(recommendation.getName()).thenReturn("MSI RTX 5070");
        when(recommendation.getEan()).thenReturn("1");
        when(recommendation.getLowestGrossPrice()).thenReturn(2749.0);
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(inventoryView);
        when(recommendationEngine.getRecommendations(gpu, inventoryView)).thenReturn(List.of(recommendation));

        // when
        var result = mvc.perform(get(categoryPath())).andExpect(status().isOk()).andReturn();

        // then
        CategoryPageModel page = (CategoryPageModel) result.getModelAndView().getModel().get("page");
        assertThat(page.dynamic()).isTrue();
        assertThat(page.rows().get(0).lowestGrossPrice()).isEqualTo("2 749,00");
        verify(productRepository, never()).findAll(anyString());
    }

    /** Without PIM categories the engine has nothing to match, so the page says so instead of asking the inventory. */
    @Test
    void dynamicCategoryWithoutAPimMappingIsEmptyAndReadsNothing() throws Exception {
        // given
        gpu.setType(CategoryDefinitionType.Dynamic);

        // when
        var result = mvc.perform(get(categoryPath())).andExpect(status().isOk()).andReturn();

        // then
        CategoryPageModel page = (CategoryPageModel) result.getModelAndView().getModel().get("page");
        assertThat(page.hasMapping()).isFalse();
        assertThat(page.rows()).isEmpty();
        verify(recommendationEngine, never()).getRecommendations(any(), any());
        verify(inventory, never()).withEnabledSuppliersOnly(anyString());
    }

    /**
     * The "Wystawiane na marketplace" count is what the export would publish: with a definition that exports the whole
     * category every enabled product with a PIM entry is counted, approved or not, and a product the export skips is
     * not counted although it still carries an approval in the "Marketplace'y" column.
     */
    @Test
    void theMarketplaceCountIsWhatTheExportWouldPublish() throws Exception {
        // given
        MarketplaceDefinition allegro = new MarketplaceDefinition("allegro", 1.2, 0, 0, 0, 0, 1);
        gpu.getMarketplaceDefinitions().add(allegro);
        // Two definitions the export never reads (RF-14): one without a name, one for a marketplace the store lacks.
        gpu.getMarketplaceDefinitions().add(new MarketplaceDefinition(null, 1.2, 0, 0, 0, 0, 1));
        gpu.getMarketplaceDefinitions().add(new MarketplaceDefinition("empik", 1.2, 0, 0, 0, 0, 1));
        MarketplaceIntegration connected = new MarketplaceIntegration();
        connected.setName("allegro");
        when(store.getMarketplaces()).thenReturn(List.of(connected));
        Product listed = new Product(gpu.getCategoryId(), "pim-1", "1", "m", "MSI", "RTX 5070", "MSI RTX 5070", "Default");
        Product withoutPim = new Product(gpu.getCategoryId(), null, "2", "m", "ASUS", "RTX 5060", "ASUS RTX 5060", "Default");
        withoutPim.setMarketplaces(List.of("allegro"));
        when(productRepository.findAll(gpu.getCategoryId())).thenReturn(List.of(listed, withoutPim));

        // when
        var result = mvc.perform(get(categoryPath())).andExpect(status().isOk()).andReturn();

        // then
        CategoryPageModel page = (CategoryPageModel) result.getModelAndView().getModel().get("page");
        assertThat(page.featureCounts()).containsEntry("marketplace", 1);
        assertThat(page.rows()).filteredOn(row -> row.features().contains("marketplace"))
                .extracting(ProductRow::name).containsExactly("MSI RTX 5070");
        assertThat(page.rows()).filteredOn(row -> !row.marketplaceNames().isEmpty())
                .extracting(ProductRow::name).containsExactly("ASUS RTX 5060");
    }

    /**
     * The old {@code ?status=MarketplaceEligible} view of an automatic category listed every mapped proposal, which the
     * export never published: {@code MarketplaceOfferExportEventListener} reads a category's offers from
     * {@code productRepository.findAllProductsWithPimId}, and an automatic category has no products of its own. The
     * view is therefore not brought back; its address stays a working link that opens the whole list.
     */
    @Test
    void theLegacyMarketplaceViewOfAnAutomaticCategoryOpensTheWholeList() throws Exception {
        // given
        gpu.setType(CategoryDefinitionType.Dynamic);
        gpu.setPimCategoryIds(List.of("pim-gpu"));
        gpu.getMarketplaceDefinitions().add(new MarketplaceDefinition("allegro", 1.2, 0, 0, 0, 0, 1));
        ProductRecommendation recommendation = mock(ProductRecommendation.class);
        when(recommendation.hasPimId()).thenReturn(true);
        when(recommendation.getName()).thenReturn("MSI RTX 5070");
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(inventoryView);
        when(recommendationEngine.getRecommendations(gpu, inventoryView)).thenReturn(List.of(recommendation));

        // when
        var result = mvc.perform(get(categoryPath()).param("status", "all").param("feature", "marketplace"))
                .andExpect(status().isOk())
                .andReturn();

        // then
        CategoryPageModel page = (CategoryPageModel) result.getModelAndView().getModel().get("page");
        assertThat(page.rows()).extracting(ProductRow::name).containsExactly("MSI RTX 5070");
        assertThat(page.featureCounts()).containsEntry("marketplace", 0);
    }

    /** Both kinds of category read the same way: by label, then by name, regardless of case. */
    @Test
    void automaticCategoryRowsAreSortedByLabelThenNameLikeAManualOne() throws Exception {
        // given
        gpu.setType(CategoryDefinitionType.Dynamic);
        gpu.setPimCategoryIds(List.of("pim-gpu"));
        List<ProductRecommendation> engineOrder = List.of(
                recommendation("RTX 5070", "msi rtx 5070"),
                recommendation("RTX 5060", "Zotac RTX 5060"),
                recommendation("RTX 5060", "ASUS RTX 5060"),
                recommendation("RTX 5070", "Gigabyte RTX 5070"));
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(inventoryView);
        when(recommendationEngine.getRecommendations(gpu, inventoryView)).thenReturn(engineOrder);

        // when
        var result = mvc.perform(get(categoryPath())).andExpect(status().isOk()).andReturn();

        // then
        CategoryPageModel page = (CategoryPageModel) result.getModelAndView().getModel().get("page");
        assertThat(page.rows()).extracting(ProductRow::name)
                .containsExactly("ASUS RTX 5060", "Zotac RTX 5060", "Gigabyte RTX 5070", "msi rtx 5070");
    }

    private static ProductRecommendation recommendation(String label, String name) {
        ProductRecommendation recommendation = mock(ProductRecommendation.class);
        lenient().when(recommendation.hasPimId()).thenReturn(true);
        lenient().when(recommendation.getLabel()).thenReturn(label);
        lenient().when(recommendation.getName()).thenReturn(name);
        return recommendation;
    }

    @Test
    void bulkDisableSavesEachProductAndReportsTheCount() throws Exception {
        // given
        Product a = new Product(gpu.getCategoryId(), "pim", "1", "m", "MSI", "l", "n", "Default");
        a.setProductId("p1");
        when(productRepository.findByProductId(gpu.getCategoryId(), "p1")).thenReturn(a);
        when(productRepository.findByProductId(gpu.getCategoryId(), "missing")).thenReturn(null);
        when(messageSource.getMessage(eq("catalog.products.bulk.disabled.skipped"), eq(new Object[]{1, 1}), any(Locale.class)))
                .thenReturn("Disabled 1, skipped 1");

        // when / then
        mvc.perform(post(categoryPath() + "/products/bulk")
                        .param("action", "disable").param("productIds", "p1", "missing").param("status", "active"))
                .andExpect(redirectedUrl(categoryPath() + "?status=active"))
                .andExpect(flash().attribute("settingsSavedMessage", "Disabled 1, skipped 1"));
        assertThat(a.isEnabled()).isFalse();
        verify(productRepository).save(a);
    }

    /** Every product was found: the sentence says how many changed and nothing about skipped ones. */
    @Test
    void bulkEnableReportsTheCountAlone() throws Exception {
        // given
        Product a = new Product(gpu.getCategoryId(), "pim", "1", "m", "MSI", "l", "n", "Default");
        a.setProductId("p1");
        a.setEnabled(false);
        when(productRepository.findByProductId(gpu.getCategoryId(), "p1")).thenReturn(a);

        // when / then
        mvc.perform(post(categoryPath() + "/products/bulk").param("action", "enable").param("productIds", "p1", "p1"))
                .andExpect(flash().attribute("settingsSavedMessage", "catalog.products.bulk.enabled"));
        verify(messageSource).getMessage(eq("catalog.products.bulk.enabled"), eq(new Object[]{1}), any(Locale.class));
    }

    /** An automatic category computes its list: a hand-made POST must not enable, disable or delete leftovers in it. */
    @Test
    void bulkIsRefusedForAnAutomaticCategory() throws Exception {
        // given
        gpu.setType(CategoryDefinitionType.Dynamic);

        // when / then
        mvc.perform(post(categoryPath() + "/products/bulk").param("action", "disable").param("productIds", "p1"))
                .andExpect(redirectedUrl(categoryPath()))
                .andExpect(flash().attribute("catalogError", "catalog.products.add.dynamic"));
        verify(productRepository, never()).findByProductId(any(), any());
        verify(productRepository, never()).save(any(Product.class));
        verify(productRepository, never()).delete(any(Product.class));
    }

    /** Nothing changed is not a success: the outcome is the warning alert, and it still names what was skipped. */
    @Test
    void aBulkActionThatChangesNothingWarns() throws Exception {
        // given
        when(productRepository.findByProductId(gpu.getCategoryId(), "foreign")).thenReturn(null);

        // when / then
        mvc.perform(post(categoryPath() + "/products/bulk").param("action", "delete").param("productIds", "foreign"))
                .andExpect(flash().attribute("catalogWarning", "catalog.products.bulk.deleted.skipped"))
                .andExpect(flash().attribute("settingsSavedMessage", nullValue()));
        verify(messageSource).getMessage(eq("catalog.products.bulk.deleted.skipped"), eq(new Object[]{0, 1}), any(Locale.class));
    }

    /**
     * The bulk form copies the address of the page into itself; the redirect echoes back only the filter the page
     * understands -- status, "Pokaż", label and search -- each re-validated and encoded, and nothing else.
     */
    @Test
    void bulkReturnsToTheFilterItWasSentFrom() throws Exception {
        // given
        Product a = new Product(gpu.getCategoryId(), "pim", "1", "m", "MSI", "l", "n", "Default");
        a.setProductId("p1");
        when(productRepository.findByProductId(gpu.getCategoryId(), "p1")).thenReturn(a);

        // when / then
        mvc.perform(post(categoryPath() + "/products/bulk").param("action", "disable").param("productIds", "p1")
                        .param("status", "all").param("feature", "srp").param("label", "RTX 5080 Ti")
                        .param("q", "ryzen 7").param("next", "https://example.com"))
                .andExpect(redirectedUrl(categoryPath() + "?status=all&feature=srp&label=RTX+5080+Ti&q=ryzen+7"));
    }

    /** A value from the address can neither add a header nor pick a filter the page does not offer. */
    @Test
    void aFilterValueCannotBreakOutOfTheRedirect() throws Exception {
        // when / then
        mvc.perform(post(categoryPath() + "/products/bulk").param("action", "enable")
                        .param("status", "deleted").param("feature", "colour")
                        .param("label", "a\r\nSet-Cookie: x=1").param("q", "a&status=all"))
                .andExpect(redirectedUrl(categoryPath()
                        + "?status=active&label=a%0D%0ASet-Cookie%3A+x%3D1&q=a%26status%3Dall"));
    }

    @Test
    void bulkDeleteRemovesTheSelectedProducts() throws Exception {
        // given
        Product a = new Product(gpu.getCategoryId(), "pim", "1", "m", "MSI", "l", "n", "Default");
        a.setProductId("p1");
        when(productRepository.findByProductId(gpu.getCategoryId(), "p1")).thenReturn(a);

        // when / then
        mvc.perform(post(categoryPath() + "/products/bulk").param("action", "delete").param("productIds", "p1"))
                .andExpect(redirectedUrl(categoryPath() + "?status=active"));
        verify(productRepository).deleteWhateverItsVersion(a);
    }

    @Test
    void bulkWithoutSelectionFlashesAnError() throws Exception {
        // given
        when(messageSource.getMessage(eq("catalog.products.bulk.none"), any(), any(Locale.class))).thenReturn("none");

        // when / then
        mvc.perform(post(categoryPath() + "/products/bulk").param("action", "enable"))
                .andExpect(redirectedUrl(categoryPath() + "?status=active"))
                .andExpect(flash().attribute("catalogError", "none"));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void anUnknownBulkActionIsRefused() throws Exception {
        // when / then
        mvc.perform(post(categoryPath() + "/products/bulk").param("action", "archive").param("productIds", "p1"))
                .andExpect(status().isBadRequest());
        verify(productRepository, never()).delete(any(Product.class));
    }

    /** A product id from another category is not found under this category's key, so a bulk action cannot touch it. */
    @Test
    void aProductOfAnotherCategoryIsIgnored() throws Exception {
        // given
        when(productRepository.findByProductId(gpu.getCategoryId(), "foreign")).thenReturn(null);

        // when / then
        mvc.perform(post(categoryPath() + "/products/bulk").param("action", "enable").param("productIds", "foreign"))
                .andExpect(redirectedUrl(categoryPath() + "?status=active"));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theProposalsAreRowsOfTheRecommendationEngineCountedByBrand() throws Exception {
        // given
        gpu.setPimCategoryIds(List.of("pim-gpu"));
        ProductRecommendation recommendation = mock(ProductRecommendation.class);
        when(recommendation.getEan()).thenReturn("1");
        when(recommendation.getName()).thenReturn("MSI RTX 5070");
        when(recommendation.getBrand()).thenReturn("MSI");
        when(recommendation.getManufacturerCode()).thenReturn("MFN-1");
        when(recommendation.getLowestGrossPrice()).thenReturn(2749.0);
        when(recommendation.getAlternativeSuppliers()).thenReturn(List.of("Acme"));
        when(recommendation.getAlternativeEans()).thenReturn(List.of("1", "2"));
        when(recommendation.getAlternativeProductCodes()).thenReturn(List.of("MFN-1"));
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(inventoryView);
        when(recommendationEngine.getRecommendations(gpu, inventoryView)).thenReturn(List.of(recommendation));
        when(supplierLabels.forStoreId(STORE_ID)).thenReturn(supplierLabelMap);
        when(supplierLabelMap.of("Acme")).thenReturn("Acme Parts");

        // when
        var result = mvc.perform(get(categoryPath() + "/products/add"))
                .andExpect(status().isOk())
                .andExpect(view().name("catalog/products-add"))
                .andExpect(model().attribute("hasMapping", true))
                .andExpect(model().attribute("reviewAction", categoryPath() + "/products/add/review"))
                .andReturn();

        // then
        List<RecommendationRow> rows = (List<RecommendationRow>) result.getModelAndView().getModel().get("rows");
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.lowestGrossPrice()).isEqualTo("2 749,00");
            assertThat(row.suppliers()).containsExactly("Acme Parts");
            assertThat(row.alternatives()).isEqualTo("2");
            assertThat(row.addHref()).isEqualTo(categoryPath() + "/products/new?ean=1");
        });
        assertThat((Map<String, Long>) result.getModelAndView().getModel().get("brandCounts")).containsEntry("MSI", 1L);
    }

    /** Without PIM categories the engine has nothing to match, so the page says so instead of asking the inventory. */
    @Test
    void aCategoryWithoutAPimMappingProposesNothingAndReadsNoInventory() throws Exception {
        // when / then
        mvc.perform(get(categoryPath() + "/products/add"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasMapping", false))
                .andExpect(model().attribute("rows", List.of()));
        verify(recommendationEngine, never()).getRecommendations(any(), any());
        verify(inventory, never()).withEnabledSuppliersOnly(anyString());
        verify(supplierLabels, never()).forStoreId(anyString());
    }

    @Test
    void reviewingNothingComesBackToTheProposalsWithAnError() throws Exception {
        // when / then
        mvc.perform(post(categoryPath() + "/products/add/review"))
                .andExpect(redirectedUrl(categoryPath() + "/products/add"))
                .andExpect(flash().attribute("catalogError", "catalog.products.review.none"));
        verify(inventory, never()).withEnabledSuppliersOnly(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void reviewSkipsEansMissingFromInventoryAndListsThem() throws Exception {
        // given
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(inventoryView);
        MatchedInventory found = mock(MatchedInventory.class);
        when(found.isEmpty()).thenReturn(false);
        when(found.getInventoryKey()).thenReturn(new InventoryKey("1", "MFN-1"));
        when(found.getTaxonomy()).thenReturn(new Taxonomy("1", "MFN-1", "MSI", "MSI RTX 5070", "GPU", 1, null, null));
        when(found.getLowestPrice()).thenReturn(Price.fromGross(2749));
        MatchedInventory missing = mock(MatchedInventory.class);
        when(missing.isEmpty()).thenReturn(true);
        when(inventoryView.findByEan("1")).thenReturn(found);
        when(inventoryView.findByEan("2")).thenReturn(missing);
        when(pimCatalog.findByPimIdOrGtinsOrMpns(any(), any(), any())).thenReturn(Optional.empty());

        // when
        var result = mvc.perform(post(categoryPath() + "/products/add/review").param("eans", "1", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("catalog/products-add-review"))
                .andExpect(model().attribute("saveAction", categoryPath() + "/products/add/save"))
                .andReturn();

        // then
        assertThat((List<String>) result.getModelAndView().getModel().get("skipped")).containsExactly("2");
        assertThat((List<String>) result.getModelAndView().getModel().get("skippedExisting")).isEmpty();
        assertThat(((ProductsBulkAddForm) result.getModelAndView().getModel().get("form")).getProducts())
                .extracting(ProductsBulkAddForm.Row::getName).containsExactly("MSI RTX 5070");
    }

    /**
     * The category and the id are the application's to give: a product grown by the binder carries neither, and a
     * category or an id smuggled into the form would let it land on -- or overwrite -- somebody else's record.
     */
    @Test
    void saveGivesEachProductAnIdOfItsOwnAndIgnoresTheSubmittedCategoryAndId() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        // Product normalises the identifiers it is given, so the lookup asks with the unified code.
        when(pimCatalog.findByGtinOrMpn("5901234567890", "M")).thenReturn(Optional.empty());

        // when / then
        mvc.perform(post(categoryPath() + "/products/add/save")
                        .param("products[0].categoryId", "someone-elses").param("products[0].productId", "forged")
                        .param("products[0].name", "X").param("products[0].ean", "5901234567890")
                        .param("products[0].manufacturerCode", "m").param("products[0].label", "L")
                        .param("products[0].pricingGroup", "Default"))
                .andExpect(redirectedUrl(categoryPath()))
                .andExpect(flash().attribute("settingsSavedMessage", "catalog.products.added"));
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getCategoryId()).isEqualTo(gpu.getCategoryId());
        assertThat(saved.getValue().getProductId()).isNotBlank().isNotEqualTo("forged");
    }

    /**
     * The review posts a row of its own, not a product: the PIM entry is resolved here, so a forged pim id cannot
     * bind the product to another entry, and a property the review never shows is not part of the form at all.
     */
    @Test
    void saveResolvesThePimEntryItselfAndIgnoresWhatTheReviewDoesNotEdit() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        PimEntry entry = mock(PimEntry.class);
        when(entry.pimId()).thenReturn("pim-9");
        when(entry.brand()).thenReturn("msi");
        when(pimCatalog.findByGtinOrMpn("5901234567890", "M")).thenReturn(Optional.of(entry));
        when(brandMapper.unifyBrand("msi")).thenReturn("MSI");

        // when
        mvc.perform(post(categoryPath() + "/products/add/save")
                        .param("products[0].pimId", "forged").param("products[0].version", "9")
                        .param("products[0].productPage", "<p>forged</p>").param("products[0].enabled", "false")
                        .param("products[0].name", "X").param("products[0].ean", "5901234567890")
                        .param("products[0].manufacturerCode", "m").param("products[0].brand", "Fake")
                        .param("products[0].label", "L").param("products[0].pricingGroup", "Default"))
                .andExpect(redirectedUrl(categoryPath()));

        // then
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getPimId()).isEqualTo("pim-9");
        assertThat(saved.getValue().getBrand()).isEqualTo("MSI");
        assertThat(saved.getValue().getProductPage()).isNull();
        assertThat(saved.getValue().getVersion()).isNull();
        assertThat(saved.getValue().isEnabled()).isTrue();
    }

    /**
     * The review of one selection can be sent twice (Back, a double click), and the review step only skips what the
     * category had when it was rendered: the save makes the same decision again, against the category as it is now.
     */
    @Test
    void saveSkipsProductsTheCategoryAlreadyHasAndSaysNothingWasAdded() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        when(productRepository.findAll(gpu.getCategoryId())).thenReturn(List.of(
                new Product(gpu.getCategoryId(), "pim", "5901234567890", "MFN-1", "MSI", "L", "MSI RTX 5070", "Default")));

        // when / then
        mvc.perform(post(categoryPath() + "/products/add/save")
                        .param("products[0].name", "MSI RTX 5070").param("products[0].ean", "5901234567890")
                        .param("products[0].manufacturerCode", "MFN-1").param("products[0].pricingGroup", "Default"))
                .andExpect(redirectedUrl(categoryPath()))
                .andExpect(flash().attribute("catalogWarning", "catalog.products.added.none"))
                .andExpect(flash().attribute("settingsSavedMessage", nullValue()));
        verify(productRepository, never()).save(any(Product.class));
        verify(pimCatalog, never()).findByGtinOrMpn(any(), any());
    }

    /** A selection where only some rows are new: the outcome counts what was added, not what was sent. */
    @Test
    void saveCountsOnlyTheProductsItActuallyAdded() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        when(productRepository.findAll(gpu.getCategoryId())).thenReturn(List.of(
                new Product(gpu.getCategoryId(), "pim", "5901234567890", "MFN-1", "MSI", "L", "MSI RTX 5070", "Default")));
        when(pimCatalog.findByGtinOrMpn("5901234567891", null)).thenReturn(Optional.empty());

        // when / then
        mvc.perform(post(categoryPath() + "/products/add/save")
                        .param("products[0].name", "MSI RTX 5070").param("products[0].ean", "5901234567890")
                        .param("products[0].manufacturerCode", "MFN-1").param("products[0].pricingGroup", "Default")
                        .param("products[1].name", "ASUS RTX 5060").param("products[1].ean", "5901234567891")
                        .param("products[1].pricingGroup", "Default"))
                .andExpect(redirectedUrl(categoryPath()));
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("ASUS RTX 5060");
        verify(messageSource).getMessage(eq("catalog.products.added"), eq(new Object[]{1}), any(Locale.class));
    }

    /** Without an entry in the PIM the product is saved without a pim id; the one from the request is never used. */
    @Test
    void saveLeavesTheProductWithoutAPimIdWhenThePimDoesNotKnowIt() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        when(pimCatalog.findByGtinOrMpn("5901234567890", null)).thenReturn(Optional.empty());

        // when
        mvc.perform(post(categoryPath() + "/products/add/save")
                        .param("products[0].pimId", "forged").param("products[0].name", "X")
                        .param("products[0].ean", "5901234567890").param("products[0].pricingGroup", "Default"))
                .andExpect(redirectedUrl(categoryPath()));

        // then
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getPimId()).isNull();
    }

    /**
     * The identifiers are editable in the review, and they decide which PIM entry the product resolves to: what the
     * review sends is what is saved and what the catalogue is asked about.
     */
    @Test
    void anEanCorrectedInTheReviewIsTheOneThatIsSaved() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        PimEntry entry = mock(PimEntry.class);
        when(entry.pimId()).thenReturn("pim-9");
        when(entry.brand()).thenReturn("msi");
        when(pimCatalog.findByGtinOrMpn("5901234567899", "MFN-9")).thenReturn(Optional.of(entry));
        when(brandMapper.unifyBrand("msi")).thenReturn("MSI");

        // when
        mvc.perform(post(categoryPath() + "/products/add/save")
                        .param("products[0].name", "MSI RTX 5070").param("products[0].ean", "5901234567899")
                        .param("products[0].manufacturerCode", "MFN-9").param("products[0].pricingGroup", "Default"))
                .andExpect(redirectedUrl(categoryPath()));

        // then
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getEan()).isEqualTo("5901234567899");
        assertThat(saved.getValue().getManufacturerCode()).isEqualTo("MFN-9");
        assertThat(saved.getValue().getPimId()).isEqualTo("pim-9");
    }

    /** A wrong identifier is refused where it is typed, instead of creating a product nothing can be matched to. */
    @Test
    @SuppressWarnings("unchecked")
    void aRowWithoutAnIdentifierOrWithAMalformedEanComesBackToTheReview() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));

        // when
        var result = mvc.perform(post(categoryPath() + "/products/add/save")
                        .param("products[0].name", "X").param("products[0].pricingGroup", "Default")
                        .param("products[1].name", "Y").param("products[1].ean", "12345")
                        .param("products[1].pricingGroup", "Default"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("catalog/products-add-review"))
                .andReturn();

        // then
        assertThat((Map<String, String>) result.getModelAndView().getModel().get("errors"))
                .containsEntry("product-0-ean", "product.error.identifier.required")
                .containsEntry("product-1-ean", "product.error.ean.invalid");
        // the summary names the row as the operator counts it; the message at the field stays without the number
        assertThat((Map<String, String>) result.getModelAndView().getModel().get("errorSummary"))
                .containsEntry("product-1-ean", "catalog.products.review.error.n");
        verify(messageSource).getMessage(eq("catalog.products.review.error.n"), eq(new Object[]{"2", "product.error.ean.invalid"}),
                any(Locale.class));
        verify(productRepository, never()).save(any(Product.class));
    }

    /**
     * The review resolves a proposal through the whole inventory key -- every EAN and product code the suppliers list
     * the item under -- and the save must not be narrower, or a product whose PIM entry is known only by a sibling
     * identifier would be written without a pim id and drop out of the price list.
     */
    @Test
    void saveResolvesThePimEntryThroughTheWholeInventoryKey() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        InventoryKey key = new InventoryKey(Set.of("5901234567890", "4719331361600"), Set.of("MFN-1"));
        MatchedInventory matched = mock(MatchedInventory.class);
        when(matched.isEmpty()).thenReturn(false);
        when(matched.getInventoryKey()).thenReturn(key);
        when(inventoryView.findByInventoryKey(any())).thenReturn(matched);
        PimEntry entry = mock(PimEntry.class);
        when(entry.pimId()).thenReturn("pim-9");
        when(entry.brand()).thenReturn("msi");
        when(pimCatalog.findByPimIdOrGtinsOrMpns(key.getId(), key.getProductEans(), key.getProductCodes()))
                .thenReturn(Optional.of(entry));
        when(brandMapper.unifyBrand("msi")).thenReturn("MSI");

        // when
        mvc.perform(post(categoryPath() + "/products/add/save")
                        .param("products[0].name", "MSI RTX 5070").param("products[0].ean", "5901234567890")
                        .param("products[0].pricingGroup", "Default"))
                .andExpect(redirectedUrl(categoryPath()));

        // then
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getPimId()).isEqualTo("pim-9");
        assertThat(saved.getValue().getBrand()).isEqualTo("MSI");
        verify(pimCatalog, never()).findByGtinOrMpn(any(), any());
    }

    /**
     * The inventory knows the product by its EAN but the catalogue has no entry under that key: the manufacturer code
     * the operator has just corrected in the review is the last thing left to ask about, so it is asked.
     */
    @Test
    void saveStillAsksAboutTheCorrectedCodeWhenTheInventoryKeyResolvesToNothing() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        InventoryKey key = new InventoryKey(Set.of("5901234567890"), Set.of());
        MatchedInventory matched = mock(MatchedInventory.class);
        when(matched.isEmpty()).thenReturn(false);
        when(matched.getInventoryKey()).thenReturn(key);
        when(inventoryView.findByInventoryKey(any())).thenReturn(matched);
        when(pimCatalog.findByPimIdOrGtinsOrMpns(key.getId(), key.getProductEans(), key.getProductCodes()))
                .thenReturn(Optional.empty());
        PimEntry entry = mock(PimEntry.class);
        when(entry.pimId()).thenReturn("pim-5");
        when(pimCatalog.findByGtinOrMpn("5901234567890", "MFN-9")).thenReturn(Optional.of(entry));

        // when
        mvc.perform(post(categoryPath() + "/products/add/save")
                        .param("products[0].name", "MSI RTX 5070").param("products[0].ean", "5901234567890")
                        .param("products[0].manufacturerCode", "MFN-9").param("products[0].pricingGroup", "Default"))
                .andExpect(redirectedUrl(categoryPath()));

        // then
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getPimId()).isEqualTo("pim-5");
    }

    /** A product that left the inventory between the review and the save is still asked about by its own two codes. */
    @Test
    void saveFallsBackToTheSubmittedIdentifiersWhenTheInventoryNoLongerHasTheProduct() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        PimEntry entry = mock(PimEntry.class);
        when(entry.pimId()).thenReturn("pim-7");
        when(pimCatalog.findByGtinOrMpn("5901234567890", "MFN-1")).thenReturn(Optional.of(entry));

        // when
        mvc.perform(post(categoryPath() + "/products/add/save")
                        .param("products[0].name", "MSI RTX 5070").param("products[0].ean", "5901234567890")
                        .param("products[0].manufacturerCode", "MFN-1").param("products[0].pricingGroup", "Default"))
                .andExpect(redirectedUrl(categoryPath()));

        // then
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getPimId()).isEqualTo("pim-7");
        verify(pimCatalog, never()).findByPimIdOrGtinsOrMpns(any(), any(), any());
    }

    /** Selecting every proposal of a large category posts more rows than Spring grows a list to by default. */
    @Test
    void saveBindsMoreProductsThanTheDefaultCollectionLimit() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        when(pimCatalog.findByGtinOrMpn(any(), any())).thenReturn(Optional.empty());
        MockHttpServletRequestBuilder request = post(categoryPath() + "/products/add/save");
        for (int index = 0; index < 300; index++) {
            request.param("products[" + index + "].name", "Product " + index)
                    .param("products[" + index + "].ean", String.format("59012345%05d", index))
                    .param("products[" + index + "].pricingGroup", "Default");
        }

        // when / then
        mvc.perform(request).andExpect(redirectedUrl(categoryPath()));
        verify(productRepository, times(300)).save(any(Product.class));
    }

    /**
     * An automatic category computes its products from the inventory, so a product added by hand would show up
     * neither on its page nor among the proposals.
     */
    @Test
    void anAutomaticCategoryTakesNoProductsAddedByHand() throws Exception {
        // given
        gpu.setType(CategoryDefinitionType.Dynamic);

        // when / then
        for (String path : List.of("/products/add", "/products/add/review", "/products/add/save")) {
            mvc.perform(path.endsWith("/add") ? get(categoryPath() + path) : post(categoryPath() + path))
                    .andExpect(redirectedUrl(categoryPath()))
                    .andExpect(flash().attribute("catalogError", "catalog.products.add.dynamic"));
        }
        verify(productRepository, never()).save(any(Product.class));
        verify(inventory, never()).withEnabledSuppliersOnly(anyString());
    }

    /** The same selection sent twice (Back, a double click) must not add the product a second time. */
    @Test
    @SuppressWarnings("unchecked")
    void reviewSkipsProductsTheCategoryAlreadyHas() throws Exception {
        // given
        when(productRepository.findAll(gpu.getCategoryId())).thenReturn(List.of(
                new Product(gpu.getCategoryId(), "pim", "1", "MFN-1", "MSI", "RTX 5070", "MSI RTX 5070", "Default")));
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(inventoryView);
        MatchedInventory found = mock(MatchedInventory.class);
        when(found.isEmpty()).thenReturn(false);
        when(found.getInventoryKey()).thenReturn(new InventoryKey("1", "MFN-1"));
        when(inventoryView.findByEan("1")).thenReturn(found);

        // when
        var result = mvc.perform(post(categoryPath() + "/products/add/review").param("eans", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("catalog/products-add-review"))
                .andReturn();

        // then
        assertThat((List<String>) result.getModelAndView().getModel().get("skippedExisting")).containsExactly("1");
        assertThat(((ProductsBulkAddForm) result.getModelAndView().getModel().get("form")).getProducts()).isEmpty();
        verify(pimCatalog, never()).findByPimIdOrGtinsOrMpns(any(), any(), any());
    }

    /** D-M48: a forged way of pricing is a mistake of its field, answered like any other with the review and 422. */
    @Test
    void aForgedAvailabilityInTheReviewComesBackAtItsFieldWith422() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));

        // when / then
        mvc.perform(post(categoryPath() + "/products/add/save")
                        .param("products[0].name", "MSI RTX 5070").param("products[0].ean", "5901234567890")
                        .param("products[0].pricingGroup", "Default").param("products[0].availabilityType", "Forged"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(model().attribute("errors",
                        hasEntry("product-0-availabilityType", "product.error.availability.invalid")));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveWithAnUnknownPricingGroupRerendersTheReviewWith422() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));

        // when
        var result = mvc.perform(post(categoryPath() + "/products/add/save")
                        .param("products[0].name", "X").param("products[0].ean", "5901234567890")
                        .param("products[0].pricingGroup", "Nope"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("catalog/products-add-review"))
                .andReturn();

        // then
        assertThat((Map<String, String>) result.getModelAndView().getModel().get("errors"))
                .containsEntry("product-0-pricingGroup", "product.error.group.unknown");
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void newProductStartsFromAnEmptyActiveFormWithTheDefaultPricingGroup() throws Exception {
        // when
        var result = mvc.perform(get(categoryPath() + "/products/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("catalog/product"))
                .andExpect(model().attribute("existing", false))
                .andExpect(model().attribute("formAction", categoryPath() + "/products/new"))
                .andExpect(model().attribute("deleteHref", nullValue()))
                .andReturn();

        // then
        ProductForm form = (ProductForm) result.getModelAndView().getModel().get("form");
        assertThat(form.isEnabled()).isTrue();
        assertThat(form.getPricingGroup()).isEqualTo(PriceDefinition.DEFAULT_PRICING_GROUP);
        verify(inventory, never()).withEnabledSuppliersOnly(anyString());
    }

    /**
     * N8: two rows of the same group spelled with another case or spacing show as one option, the first spelling,
     * until the page is answered otherwise -- a duplicate-looking pair in the select is what re-saving the category
     * page used to leave behind.
     */
    @Test
    @SuppressWarnings("unchecked")
    void pricingGroupsOnTheFormAreDistinctByCaseAndSpacingKeepingTheFirstSpelling() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Ultra Premium"));
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "ultra  premium"));
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));

        // when
        var result = mvc.perform(get(categoryPath() + "/products/new")).andExpect(status().isOk()).andReturn();

        // then
        List<String> pricingGroups = (List<String>) result.getModelAndView().getModel().get("pricingGroups");
        assertThat(pricingGroups).containsExactly("Ultra Premium", "Default");
    }

    @Test
    void newProductFromTheInventoryIsPrefilledButStillCountsAsNew() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(inventoryView);
        MatchedInventory found = mock(MatchedInventory.class);
        when(found.isEmpty()).thenReturn(false);
        when(found.getInventoryKey()).thenReturn(new InventoryKey("1", "MFN-1"));
        when(found.getTaxonomy()).thenReturn(new Taxonomy("1", "MFN-1", "MSI", "MSI RTX 5070", "GPU", 1, null, null));
        when(found.getLowestPrice()).thenReturn(Price.fromGross(2749));
        when(inventoryView.findByEan("1")).thenReturn(found);
        when(pimCatalog.findByPimIdOrGtinsOrMpns(any(), any(), any())).thenReturn(Optional.empty());

        // when
        var result = mvc.perform(get(categoryPath() + "/products/new").param("ean", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("catalog/product"))
                .andExpect(model().attribute("existing", false))
                .andReturn();

        // then
        ProductForm form = (ProductForm) result.getModelAndView().getModel().get("form");
        assertThat(form.getName()).isEqualTo("MSI RTX 5070");
        assertThat(form.getEan()).isEqualTo("1");
        assertThat(form.getBrand()).isEqualTo("MSI");
        assertThat(form.getExistingPimId()).isNull();
        assertThat(result.getModelAndView().getModel().get("prefillNotice")).isNull();
    }

    /** An address carrying an EAN the inventory no longer has must not be an error page: the form still opens. */
    @Test
    void newProductFromAnEanOutsideTheInventoryOpensTheEmptyFormWithANotice() throws Exception {
        // given
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(inventoryView);
        MatchedInventory missing = mock(MatchedInventory.class);
        when(missing.isEmpty()).thenReturn(true);
        when(inventoryView.findByEan("999")).thenReturn(missing);

        // when
        var result = mvc.perform(get(categoryPath() + "/products/new").param("ean", "999"))
                .andExpect(status().isOk())
                .andExpect(view().name("catalog/product"))
                .andExpect(model().attribute("prefillNotice", "catalog.products.new.eanNotInInventory"))
                .andReturn();

        // then
        ProductForm form = (ProductForm) result.getModelAndView().getModel().get("form");
        assertThat(form.getEan()).isNull();
        assertThat(form.isEnabled()).isTrue();
    }

    /** Only a changed identifier is asked about; one leading to another PIM entry is refused at the EAN. */
    @Test
    void savingAChangedIdentifierThatWouldMatchAnotherPimEntryIsRejected() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        Product existing = new Product(gpu.getCategoryId(), "pim-1", "4719331361600", "m", "b", "l", "n", "Default");
        existing.setProductId("p1");
        when(access.requireProduct(gpu, "p1")).thenReturn(existing);
        PimEntry other = mock(PimEntry.class);
        when(other.pimId()).thenReturn("pim-2");
        when(pimCatalog.findByGtinOrMpn("4719331361601", "m")).thenReturn(Optional.of(other));

        // when / then
        mvc.perform(post(categoryPath() + "/products/p1").header("X-Requested-With", "fetch")
                        .param("name", "n").param("ean", "4719331361601").param("manufacturerCode", "m")
                        .param("availabilityType", "BasedOnSupply").param("pricingGroup", "Default"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(model().attribute("errors", hasEntry("ean", "product.error.pim.changed")));
        verify(productRepository, never()).save(any(Product.class));
    }

    /**
     * OD-1: the product holds an entry matched through a sibling EAN, so its own codes lead elsewhere in the PIM. A
     * save that leaves the codes as they are (here: a new price) is not a change of identity and goes through.
     */
    @Test
    void savingPriceOfAProductWithASiblingPimEntryPasses() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        Product existing = new Product(gpu.getCategoryId(), "pim-1", "4719331361600", "M", "b", "l", "n", "Default");
        existing.setProductId("p1");
        when(access.requireProduct(gpu, "p1")).thenReturn(existing);
        PimEntry other = mock(PimEntry.class);
        lenient().when(other.pimId()).thenReturn("pim-2");
        lenient().when(pimCatalog.findByGtinOrMpn(any(), any())).thenReturn(Optional.of(other));

        // when
        mvc.perform(post(categoryPath() + "/products/p1")
                        .param("name", "n").param("ean", "4719331361600").param("manufacturerCode", "M")
                        .param("availabilityType", "BasedOnSupply").param("pricingGroup", "Default")
                        .param("suggestedRetailPrice", "5199"))
                .andExpect(redirectedUrl(categoryPath() + "?status=active"));

        // then
        verify(productRepository).save(existing);
        assertThat(existing.getSuggestedRetailPrice()).isEqualTo(5199);
        assertThat(existing.getPimId()).isEqualTo("pim-1");
    }

    /**
     * OD-2 + OD-6: a save that leaves the pricing group and the approvals alone keeps a group the category no longer
     * lists and an approval for a marketplace the store is no longer connected to.
     */
    @Test
    void savingWithoutTouchingThemKeepsAGroupOutsideTheListAndAnUnconnectedApproval() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        Product existing = new Product(gpu.getCategoryId(), null, "4719331361600", "m", "b", "l", "n", "Ultra");
        existing.setProductId("p1");
        existing.setMarketplaces(new LinkedList<>(List.of("Morele")));
        when(access.requireProduct(gpu, "p1")).thenReturn(existing);
        MarketplaceIntegration allegro = new MarketplaceIntegration();
        allegro.setName("Allegro");
        when(store.getMarketplaces()).thenReturn(List.of(allegro));

        // when
        mvc.perform(post(categoryPath() + "/products/p1")
                        .param("name", "n").param("ean", "4719331361600").param("manufacturerCode", "m")
                        .param("availabilityType", "BasedOnSupply").param("pricingGroup", "Ultra")
                        .param("marketplaces", "Allegro").param("marketplaces", "Morele"))
                .andExpect(redirectedUrl(categoryPath() + "?status=active"));

        // then
        verify(productRepository).save(existing);
        assertThat(existing.getPricingGroup()).isEqualTo("Ultra");
        assertThat(existing.getMarketplaces()).containsExactly("Allegro", "Morele");
    }

    /** The page of a saved product states the id; a product being created has none yet. */
    @Test
    void theProductPageCarriesTheIdOfASavedProductOnly() throws Exception {
        // given
        Product product = new Product(gpu.getCategoryId(), "pim", "5901234567890", "m", "MSI", "L", "MSI RTX 5070", "Default");
        product.setProductId("p-1");
        when(access.requireProduct(gpu, "p-1")).thenReturn(product);

        // when / then
        mvc.perform(get(categoryPath() + "/products/p-1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("productId", "p-1"));
        mvc.perform(get(categoryPath() + "/products/new"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("productId", nullValue()));
    }

    /** The lead travels as plain parts; the template escapes them, so nothing here is HTML (D-M50). */
    @Test
    void theProductPageLeadIsGivenAsPlainTextParts() throws Exception {
        // given
        Product product = new Product(gpu.getCategoryId(), "pim-7", "5901234567890", "M<1>", "<b>MSI</b>", "L", "MSI RTX 5070", "Default");
        product.setProductId("p-1");
        when(access.requireProduct(gpu, "p-1")).thenReturn(product);

        // when / then
        mvc.perform(get(categoryPath() + "/products/p-1"))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("lead"))
                .andExpect(model().attribute("leadParts", List.of(
                        new CatalogProductsController.LeadPart("EAN 5901234567890", false),
                        new CatalogProductsController.LeadPart("M<1>", false),
                        new CatalogProductsController.LeadPart("PIM pim-7", false),
                        new CatalogProductsController.LeadPart("<b>MSI</b>", false))));
    }

    @Test
    void savedProductRedirectsToTheCategoryWithAFlash() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        Product existing = new Product(gpu.getCategoryId(), "pim-1", "4719331361600", "m", "b", "l", "Old", "Default");
        existing.setProductId("p1");
        when(access.requireProduct(gpu, "p1")).thenReturn(existing);

        // when / then
        mvc.perform(post(categoryPath() + "/products/p1")
                        .param("name", "New name").param("ean", "4719331361600").param("manufacturerCode", "m")
                        .param("availabilityType", "BasedOnSupply").param("pricingGroup", "Default")
                        .param("enabled", "true"))
                .andExpect(redirectedUrl(categoryPath() + "?status=active"))
                .andExpect(flash().attribute("settingsSavedMessage", "product.saved"));
        assertThat(existing.getName()).isEqualTo("New name");
        verify(productRepository).save(existing);
    }

    /** The status filter shown on the category page travels through the hidden field and survives the redirect back. */
    @Test
    void savedProductRedirectsKeepingTheStatusFilterItCameFrom() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        Product existing = new Product(gpu.getCategoryId(), "pim-1", "4719331361600", "m", "b", "l", "Old", "Default");
        existing.setProductId("p1");
        when(access.requireProduct(gpu, "p1")).thenReturn(existing);

        // when / then
        mvc.perform(post(categoryPath() + "/products/p1").param("status", "disabled")
                        .param("name", "New name").param("ean", "4719331361600").param("manufacturerCode", "m")
                        .param("availabilityType", "BasedOnSupply").param("pricingGroup", "Default")
                        .param("enabled", "true"))
                .andExpect(redirectedUrl(categoryPath() + "?status=disabled"));
    }

    /**
     * The whole filter comes back, not only the status. The label travels as {@code filterLabel}: {@code label} is the
     * product's own field in the form.
     */
    @Test
    void savedProductRedirectsKeepingTheWholeFilterItCameFrom() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        Product existing = new Product(gpu.getCategoryId(), "pim-1", "4719331361600", "m", "b", "l", "Old", "Default");
        existing.setProductId("p1");
        when(access.requireProduct(gpu, "p1")).thenReturn(existing);

        // when / then
        mvc.perform(post(categoryPath() + "/products/p1").param("status", "all").param("feature", "stock")
                        .param("filterLabel", "RTX 5070").param("q", "msi")
                        .param("name", "New name").param("ean", "4719331361600").param("manufacturerCode", "m")
                        .param("label", "l").param("availabilityType", "BasedOnSupply").param("pricingGroup", "Default")
                        .param("enabled", "true"))
                .andExpect(redirectedUrl(categoryPath() + "?status=all&feature=stock&label=RTX+5070&q=msi"));
        assertThat(existing.getLabel()).isEqualTo("l");
    }

    /** Opened from a filtered list, the product page keeps that filter for its form and for its way back. */
    @Test
    void theProductPageKeepsTheFilterItWasOpenedFrom() throws Exception {
        // given
        Product existing = new Product(gpu.getCategoryId(), "pim-1", "4719331361600", "m", "b", "l", "Old", "Default");
        existing.setProductId("p1");
        when(access.requireProduct(gpu, "p1")).thenReturn(existing);

        // when
        var result = mvc.perform(get(categoryPath() + "/products/p1").param("status", "disabled").param("label", "RTX 5070"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("backHref", categoryPath() + "?status=disabled&label=RTX+5070"))
                .andReturn();

        // then
        CategoryFilter filter =
                (CategoryFilter) result.getModelAndView().getModel().get("filter");
        assertThat(filter.status()).isEqualTo("disabled");
        assertThat(filter.label()).isEqualTo("RTX 5070");
    }

    /**
     * The DTO carries only the fields the page edits; everything else the entity holds (its page number, its optimistic
     * lock, and the identity the controller — not the form — assigns) must survive a save untouched.
     */
    @Test
    void savingAProductKeepsFieldsOutsideTheForm() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        Product existing = new Product(gpu.getCategoryId(), "pim-1", "4719331361600", "m", "b", "l", "Old", "Default");
        existing.setProductId("p1");
        existing.setProductPage("<p>Old description</p>");
        existing.setVersion(7L);
        when(access.requireProduct(gpu, "p1")).thenReturn(existing);

        // when
        mvc.perform(post(categoryPath() + "/products/p1")
                        .param("name", "New name").param("ean", "4719331361600").param("manufacturerCode", "m")
                        .param("availabilityType", "BasedOnSupply").param("pricingGroup", "Default")
                        .param("enabled", "true"))
                .andExpect(redirectedUrl(categoryPath() + "?status=active"));

        // then
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getCategoryId()).isEqualTo(gpu.getCategoryId());
        assertThat(saved.getValue().getProductId()).isEqualTo("p1");
        assertThat(saved.getValue().getProductPage()).isEqualTo("<p>Old description</p>");
        assertThat(saved.getValue().getVersion()).isEqualTo(7L);
    }

    @Test
    void createdProductTakesItsPimEntryAndBrandFromTheCatalog() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        PimEntry entry = mock(PimEntry.class);
        when(entry.pimId()).thenReturn("pim-9");
        when(entry.brand()).thenReturn("msi");
        when(pimCatalog.findByGtinOrMpn("4719331361600", "MFN-1")).thenReturn(Optional.of(entry));
        when(brandMapper.unifyBrand("msi")).thenReturn("MSI");

        // when
        mvc.perform(post(categoryPath() + "/products/new")
                        .param("name", "MSI RTX 5070").param("ean", "4719331361600").param("manufacturerCode", "MFN-1")
                        .param("availabilityType", "BasedOnSupply").param("pricingGroup", "Default")
                        .param("enabled", "true"))
                .andExpect(redirectedUrl(categoryPath() + "?status=active"))
                .andExpect(flash().attribute("settingsSavedMessage", "product.added"));

        // then
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getCategoryId()).isEqualTo(gpu.getCategoryId());
        assertThat(saved.getValue().getProductId()).isNotBlank();
        assertThat(saved.getValue().getPimId()).isEqualTo("pim-9");
        assertThat(saved.getValue().getBrand()).isEqualTo("MSI");
        assertThat(saved.getValue().isEnabled()).isTrue();
    }

    /**
     * RF-1: "Dodaj i edytuj" shows the product with the PIM entry its inventory key resolves to; the save resolves it
     * the same way, so an entry the catalogue holds under a sibling EAN of the key is not lost on the way.
     */
    @Test
    void createProductResolvesThePimEntryThroughTheWholeInventoryKey() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        InventoryKey key = new InventoryKey(Set.of("5901234567890", "4719331361600"), Set.of("MFN-1"));
        MatchedInventory matched = mock(MatchedInventory.class);
        when(matched.isEmpty()).thenReturn(false);
        when(matched.getInventoryKey()).thenReturn(key);
        when(inventoryView.findByInventoryKey(any())).thenReturn(matched);
        PimEntry entry = mock(PimEntry.class);
        when(entry.pimId()).thenReturn("pim-9");
        when(entry.brand()).thenReturn("msi");
        when(pimCatalog.findByPimIdOrGtinsOrMpns(key.getId(), key.getProductEans(), key.getProductCodes()))
                .thenReturn(Optional.of(entry));
        when(brandMapper.unifyBrand("msi")).thenReturn("MSI");

        // when
        mvc.perform(post(categoryPath() + "/products/new")
                        .param("name", "MSI RTX 5070").param("ean", "5901234567890")
                        .param("availabilityType", "BasedOnSupply").param("pricingGroup", "Default")
                        .param("enabled", "true"))
                .andExpect(redirectedUrl(categoryPath() + "?status=active"));

        // then
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getPimId()).isEqualTo("pim-9");
        assertThat(saved.getValue().getBrand()).isEqualTo("MSI");
        verify(pimCatalog, never()).findByGtinOrMpn(any(), any());
    }

    @Test
    void serviceFlagFollowsTheCheckbox() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        Product existing = new Product(gpu.getCategoryId(), null, "4719331361600", "m", "b", "l", "Assembly", "Default");
        existing.setProductId("p1");
        when(access.requireProduct(gpu, "p1")).thenReturn(existing);

        // when / then
        mvc.perform(saveService().param("service", "true")).andExpect(redirectedUrl(categoryPath() + "?status=active"));
        assertThat(existing.isService()).isTrue();
        mvc.perform(saveService()).andExpect(redirectedUrl(categoryPath() + "?status=active"));
        assertThat(existing.isService()).isFalse();
    }

    /** The same save without the checkbox, which an unticked box does not send. */
    private MockHttpServletRequestBuilder saveService() {
        return post(categoryPath() + "/products/p1")
                .param("name", "Assembly").param("ean", "4719331361600").param("availabilityType", "BasedOnSupply")
                .param("pricingGroup", "Default");
    }

    @Test
    void deletingAProductGoesThroughTheConfirmationEndpoint() throws Exception {
        // given
        Product existing = new Product(gpu.getCategoryId(), "pim-1", "4719331361600", "m", "b", "l", "n", "Default");
        existing.setProductId("p1");
        when(access.requireProduct(gpu, "p1")).thenReturn(existing);

        // when / then
        mvc.perform(get(categoryPath() + "/products/p1/delete")).andExpect(view().name("settings-confirm"));
        verify(productRepository, never()).delete(any(Product.class));
        mvc.perform(post(categoryPath() + "/products/p1/delete"))
                .andExpect(redirectedUrl(categoryPath()))
                .andExpect(flash().attribute("settingsSavedMessage", "product.deleted"));
        verify(productRepository).deleteWhateverItsVersion(existing);
    }

    @Test
    void theConfirmationPageAsksAboutTheProductAndPostsToItsOwnAddress() throws Exception {
        // given
        Product existing = new Product(gpu.getCategoryId(), "pim-1", "4719331361600", "m", "b", "l", "RTX 5070", "Default");
        existing.setProductId("p1");
        when(access.requireProduct(gpu, "p1")).thenReturn(existing);

        // when
        var result = mvc.perform(get(categoryPath() + "/products/p1/delete")).andReturn();

        // then
        ConfirmAction confirm = (ConfirmAction) result.getModelAndView().getModel().get("confirm");
        assertThat(confirm.actionPath()).isEqualTo(categoryPath() + "/products/p1/delete");
        assertThat(confirm.cancelPath()).isEqualTo(categoryPath() + "/products/p1");
        assertThat(confirm.destructive()).isTrue();
        assertThat(result.getModelAndView().getModel().get("backLabel")).isEqualTo("RTX 5070");
    }

    /** An automatic category computes its list from the inventory, so a product saved there would show up nowhere. */
    @Test
    void anAutomaticCategoryRefusesEveryProductAction() throws Exception {
        // given
        gpu.setType(CategoryDefinitionType.Dynamic);

        // when / then
        mvc.perform(get(categoryPath() + "/products/new"))
                .andExpect(redirectedUrl(categoryPath()))
                .andExpect(flash().attribute("catalogError", "catalog.products.add.dynamic"));
        mvc.perform(post(categoryPath() + "/products/p1").param("name", "n"))
                .andExpect(redirectedUrl(categoryPath()));
        verify(productRepository, never()).save(any(Product.class));
    }

    /**
     * The category being automatic must not hide that the product itself does not exist: a stray link or a bookmark
     * to a product that was never there (or has since been removed) is a 404, not a redirect that reads as "found it,
     * but you can't touch it here".
     */
    @Test
    void missingProductInAnAutomaticCategoryIs404NotARedirect() throws Exception {
        // given
        gpu.setType(CategoryDefinitionType.Dynamic);
        when(access.requireProduct(gpu, "missing")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        // when / then
        mvc.perform(get(categoryPath() + "/products/missing")).andExpect(status().isNotFound());
        mvc.perform(post(categoryPath() + "/products/missing").param("name", "n")).andExpect(status().isNotFound());
        mvc.perform(get(categoryPath() + "/products/missing/delete")).andExpect(status().isNotFound());
        mvc.perform(post(categoryPath() + "/products/missing/delete")).andExpect(status().isNotFound());
    }

    /** A mistake in a folded section must not stay folded, or the summary links to a field nobody can see. */
    @Test
    void anUnfinishedCustomFilterOpensTheSectionItSitsIn() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        Product existing = new Product(gpu.getCategoryId(), null, "4719331361600", "m", "b", "l", "n", "Default");
        existing.setProductId("p1");
        when(access.requireProduct(gpu, "p1")).thenReturn(existing);

        // when / then
        mvc.perform(post(categoryPath() + "/products/p1").header("X-Requested-With", "fetch")
                        .param("name", "n").param("ean", "4719331361600").param("availabilityType", "BasedOnSupply")
                        .param("pricingGroup", "Default").param("customAttributesFilters[0].name", "Socket"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(model().attribute("errors",
                        hasEntry("customAttributeFilter-0-value", "product.error.filter.incomplete")))
                .andExpect(model().attribute("openClient", true))
                .andExpect(model().attribute("openStock", false));
        verify(productRepository, never()).save(any(Product.class));
    }

    /**
     * RF-5: the form posted twice (Back and send again, a double click without JavaScript) must not add the product
     * twice. The second POST finds the first one in the category by the same identifiers the review uses.
     */
    @Test
    void aSecondCreateProductWithTheSameEanIsRefusedAndTheCategoryKeepsOneRecord() throws Exception {
        // given -- the repository remembers what it saved
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        List<Product> stored = new ArrayList<>();
        when(productRepository.findAll(gpu.getCategoryId())).thenAnswer(call -> List.copyOf(stored));
        doAnswer(call -> stored.add(call.getArgument(0))).when(productRepository).save(any(Product.class));
        var create = post(categoryPath() + "/products/new").header("X-Requested-With", "fetch")
                .param("name", "MSI RTX 5070").param("ean", "4719331361600").param("manufacturerCode", "MFN-1")
                .param("availabilityType", "BasedOnSupply").param("pricingGroup", "Default").param("enabled", "true");

        // when
        mvc.perform(create).andExpect(status().isOk());
        mvc.perform(create)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("catalog/product :: productForm"))
                .andExpect(model().attribute("errors", hasEntry("ean", "product.error.duplicate")));

        // then
        assertThat(stored).hasSize(1);
        verify(productRepository, times(1)).save(any(Product.class));
    }

    /** The guard compares the same way as the review: a product with the code alone is the same product too. */
    @Test
    void createProductIsRefusedWhenTheCategoryHasTheManufacturerCodeAlready() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        when(productRepository.findAll(gpu.getCategoryId())).thenReturn(List.of(
                new Product(gpu.getCategoryId(), null, null, "mfn-1", "MSI", null, "MSI RTX 5070", "Default")));

        // when / then
        mvc.perform(post(categoryPath() + "/products/new")
                        .param("name", "MSI RTX 5070 OC").param("ean", "4719331361600").param("manufacturerCode", "MFN-1")
                        .param("availabilityType", "BasedOnSupply").param("pricingGroup", "Default").param("enabled", "true"))
                .andExpect(view().name("catalog/product"))
                .andExpect(model().attribute("errors", hasEntry("ean", "product.error.duplicate")));
        verify(productRepository, never()).save(any(Product.class));
    }

    /** D-I6 for a product: another save got there first, so this one is refused at the form instead of a 500. */
    @Test
    void aProductChangedMeanwhileAnswers422WithTheMessageAtTheForm() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        Product existing = new Product(gpu.getCategoryId(), "pim-1", "4719331361600", "m", "b", "l", "Old", "Default");
        existing.setProductId("p1");
        when(access.requireProduct(gpu, "p1")).thenReturn(existing);
        doThrow(new ConditionalCheckFailedException("version changed")).when(productRepository).save(existing);

        // when / then
        mvc.perform(post(categoryPath() + "/products/p1").header("X-Requested-With", "fetch")
                        .param("name", "New name").param("ean", "4719331361600").param("manufacturerCode", "m")
                        .param("availabilityType", "BasedOnSupply").param("pricingGroup", "Default").param("enabled", "true"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("catalog/product :: productForm"))
                .andExpect(model().attribute("errors", hasEntry("product-form", "product.conflict")));
    }

    @Test
    void aProductChangedMeanwhileWithoutJavaScriptAnswers422WithThePage() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        Product existing = new Product(gpu.getCategoryId(), "pim-1", "4719331361600", "m", "b", "l", "Old", "Default");
        existing.setProductId("p1");
        when(access.requireProduct(gpu, "p1")).thenReturn(existing);
        doThrow(new ConditionalCheckFailedException("version changed")).when(productRepository).save(existing);

        // when / then
        mvc.perform(post(categoryPath() + "/products/p1")
                        .param("name", "New name").param("ean", "4719331361600").param("manufacturerCode", "m")
                        .param("availabilityType", "BasedOnSupply").param("pricingGroup", "Default").param("enabled", "true"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("catalog/product"))
                .andExpect(model().attribute("errors", hasEntry("product-form", "product.conflict")));
    }

    /**
     * RF-6: a parallel POST of the same review saved this row first -- the conditional put of the row's id fails --
     * so the row is skipped like a product the category already has, not answered with a 500.
     */
    @Test
    void saveSkipsARowAParallelSaveOfTheSameReviewStoredFirst() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        doThrow(new ConditionalCheckFailedException("exists")).when(productRepository).save(any(Product.class));

        // when / then
        mvc.perform(post(categoryPath() + "/products/add/save")
                        .param("reviewId", "0b7a52d4-8a51-4a53-9f4d-2f7d2c0b8c11")
                        .param("products[0].name", "X").param("products[0].ean", "5901234567890")
                        .param("products[0].manufacturerCode", "m").param("products[0].pricingGroup", "Default"))
                .andExpect(redirectedUrl(categoryPath()))
                .andExpect(flash().attribute("catalogWarning", "catalog.products.added.none"));
    }

    @Test
    void saveGivesTheRowsOfOneReviewTheIdsTheReviewDetermines() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        ProductsBulkAddForm review = new ProductsBulkAddForm();
        review.setReviewId("0b7a52d4-8a51-4a53-9f4d-2f7d2c0b8c11");
        ProductsBulkAddForm.Row row = new ProductsBulkAddForm.Row();
        row.setEan("5901234567890");
        row.setManufacturerCode("m");
        review.getProducts().add(row);

        // when
        mvc.perform(post(categoryPath() + "/products/add/save")
                .param("reviewId", "0b7a52d4-8a51-4a53-9f4d-2f7d2c0b8c11")
                .param("products[0].name", "X").param("products[0].ean", "5901234567890")
                .param("products[0].manufacturerCode", "m").param("products[0].pricingGroup", "Default"));

        // then
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getProductId()).isEqualTo(review.toProduct(0, gpu.getCategoryId()).getProductId());
    }

    /**
     * RF-6 follow-up: two rows of one review naming the same product (overlapping identifiers). The parallel request
     * saved the first row; this one must remember its key, or the second row -- saved under an id of its own -- would
     * add the product a second time.
     */
    @Test
    void aRowLostToAParallelSaveStillGuardsTheRowsAfterIt() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));
        doThrow(new ConditionalCheckFailedException("exists")).doNothing().when(productRepository).save(any(Product.class));

        // when
        mvc.perform(post(categoryPath() + "/products/add/save")
                        .param("reviewId", "0b7a52d4-8a51-4a53-9f4d-2f7d2c0b8c11")
                        .param("products[0].name", "X").param("products[0].ean", "5901234567890")
                        .param("products[0].manufacturerCode", "m-1").param("products[0].pricingGroup", "Default")
                        .param("products[1].name", "X bis").param("products[1].ean", "5901234567890")
                        .param("products[1].manufacturerCode", "m-2").param("products[1].pricingGroup", "Default"))
                .andExpect(flash().attribute("catalogWarning", "catalog.products.added.none"));

        // then
        verify(productRepository, times(1)).save(any(Product.class));
    }

    /**
     * A bulk switch racing a save of the same product (another tab) re-reads the product and applies the switch to
     * the fresh copy: the other save is kept, the switch lands, and nothing answers with a 500.
     */
    @Test
    void bulkEnableReappliesTheSwitchToAProductSavedMeanwhile() throws Exception {
        // given
        Product stale = new Product(gpu.getCategoryId(), "pim", "1", "m", "MSI", "l", "Old name", "Default");
        stale.setProductId("p1");
        stale.setEnabled(false);
        Product fresh = new Product(gpu.getCategoryId(), "pim", "1", "m", "MSI", "l", "Renamed meanwhile", "Default");
        fresh.setProductId("p1");
        fresh.setEnabled(false);
        when(productRepository.findByProductId(gpu.getCategoryId(), "p1")).thenReturn(stale, fresh);
        doThrow(new ConditionalCheckFailedException("version changed")).when(productRepository).save(stale);
        doAnswer(OptimisticLockingExecutorMocks.retryingModifyAndSave(3))
                .when(optimisticLockingExecutor).modifyAndSave(any(), any(), any());

        // when / then
        mvc.perform(post(categoryPath() + "/products/bulk").param("action", "enable").param("productIds", "p1"))
                .andExpect(redirectedUrl(categoryPath() + "?status=active"))
                .andExpect(flash().attribute("settingsSavedMessage", "catalog.products.bulk.enabled"));
        verify(productRepository).save(fresh);
        assertThat(fresh.isEnabled()).isTrue();
        assertThat(fresh.getName()).isEqualTo("Renamed meanwhile");
    }

    /** A product that keeps changing under the switch is counted with the skipped ones instead of failing the request. */
    @Test
    void bulkDisableCountsAProductThatKeepsChangingAsSkipped() throws Exception {
        // given
        Product a = new Product(gpu.getCategoryId(), "pim", "1", "m", "MSI", "l", "n", "Default");
        a.setProductId("p1");
        Product b = new Product(gpu.getCategoryId(), "pim", "2", "m", "MSI", "l", "n", "Default");
        b.setProductId("p2");
        when(productRepository.findByProductId(gpu.getCategoryId(), "p1")).thenReturn(a);
        when(productRepository.findByProductId(gpu.getCategoryId(), "p2")).thenReturn(b);
        lenient().doThrow(new ConditionalCheckFailedException("version changed")).when(productRepository).save(b);
        doAnswer(OptimisticLockingExecutorMocks.retryingModifyAndSave(3))
                .when(optimisticLockingExecutor).modifyAndSave(any(), any(), any());
        when(messageSource.getMessage(eq("catalog.products.bulk.disabled.skipped"), eq(new Object[]{1, 1}), any(Locale.class)))
                .thenReturn("Disabled 1, skipped 1");

        // when / then
        mvc.perform(post(categoryPath() + "/products/bulk").param("action", "disable").param("productIds", "p1", "p2"))
                .andExpect(redirectedUrl(categoryPath() + "?status=active"))
                .andExpect(flash().attribute("settingsSavedMessage", "Disabled 1, skipped 1"));
        assertThat(a.isEnabled()).isFalse();
    }

    /**
     * A product deleted by another request while the switch was retried: skipped and counted, never a 500. Over the
     * real Spring-Retry-proxied executor, which hands any exception other than a conflict out wrapped.
     */
    @Test
    void bulkEnableCountsAProductDeletedMeanwhileAsSkippedThroughTheRealExecutor() throws Exception {
        // given -- the bulk read finds it; the save conflicts; the retry's read finds it gone
        Product stale = new Product(gpu.getCategoryId(), "pim", "1", "m", "MSI", "l", "n", "Default");
        stale.setProductId("p1");
        stale.setEnabled(false);
        when(productRepository.findByProductId(gpu.getCategoryId(), "p1")).thenReturn(stale, (Product) null);
        doThrow(new ConditionalCheckFailedException("version changed")).when(productRepository).save(stale);
        when(messageSource.getMessage(eq("catalog.products.bulk.enabled.skipped"), eq(new Object[]{0, 1}), any(Locale.class)))
                .thenReturn("Enabled 0, skipped 1");
        MockMvc real = MockMvcBuilders.standaloneSetup(new CatalogProductsController(access, productRepository, storesRepository,
                recommendationEngine, inventory, marketplaces, pimCategoryOptions, supplierLabels, pimCatalog,
                brandMapper, messageSource, RetryingOptimisticLockingExecutor.create())).build();

        // when / then
        real.perform(post(categoryPath() + "/products/bulk").param("action", "enable").param("productIds", "p1"))
                .andExpect(redirectedUrl(categoryPath() + "?status=active"))
                .andExpect(flash().attribute("catalogWarning", "Enabled 0, skipped 1"));
    }

    /** A bulk deletion deletes what was chosen even when it was saved meanwhile: the delete does not ask for a version. */
    @Test
    void bulkDeleteDeletesWhateverTheVersionOfEachProduct() throws Exception {
        // given
        Product a = new Product(gpu.getCategoryId(), "pim", "1", "m", "MSI", "l", "n", "Default");
        a.setProductId("p1");
        when(productRepository.findByProductId(gpu.getCategoryId(), "p1")).thenReturn(a);

        // when / then
        mvc.perform(post(categoryPath() + "/products/bulk").param("action", "delete").param("productIds", "p1"))
                .andExpect(flash().attribute("settingsSavedMessage", "catalog.products.bulk.deleted"));
        verify(productRepository).deleteWhateverItsVersion(a);
        verify(productRepository, never()).delete(any(Product.class));
    }
}
