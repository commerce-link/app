package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.PimCategoryOptions;
import pl.commercelink.products.PriceDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductRecommendation;
import pl.commercelink.products.ProductRecommendationEngine;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.web.catalog.CatalogAccess;
import pl.commercelink.web.catalog.CategoryPageModel;
import pl.commercelink.web.catalog.ProductRow;
import pl.commercelink.web.catalog.ProductStatus;
import pl.commercelink.web.catalog.RecommendationRow;
import pl.commercelink.web.dtos.ProductsBulkAddForm;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        mvc = MockMvcBuilders.standaloneSetup(new CatalogProductsController(access, productRepository,
                recommendationEngine, inventory, marketplaces, pimCategoryOptions, supplierLabels, pimCatalog,
                brandMapper, messageSource)).build();
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
        assertThat(result.getModelAndView().getModel().get("filterDefault")).isEqualTo("status:disabled feature:all label:all");
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

    @Test
    void bulkDisableSavesEachProductAndReportsTheCount() throws Exception {
        // given
        Product a = new Product(gpu.getCategoryId(), "pim", "1", "m", "MSI", "l", "n", "Default");
        a.setProductId("p1");
        when(productRepository.findByProductId(gpu.getCategoryId(), "p1")).thenReturn(a);
        when(productRepository.findByProductId(gpu.getCategoryId(), "missing")).thenReturn(null);
        when(messageSource.getMessage(eq("catalog.products.bulk.disabled"), eq(new Object[]{1}), any(Locale.class)))
                .thenReturn("Disabled 1");

        // when / then
        mvc.perform(post(categoryPath() + "/products/bulk")
                        .param("action", "disable").param("productIds", "p1", "missing").param("status", "active"))
                .andExpect(redirectedUrl(categoryPath() + "?status=active"))
                .andExpect(flash().attribute("settingsSavedMessage", "Disabled 1"));
        assertThat(a.isEnabled()).isFalse();
        verify(productRepository).save(a);
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
        verify(productRepository).delete(a);
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
                .extracting(Product::getName).containsExactly("MSI RTX 5070");
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
        when(pimCatalog.findByGtinOrMpn("1", "M")).thenReturn(Optional.empty());

        // when / then
        mvc.perform(post(categoryPath() + "/products/add/save")
                        .param("products[0].categoryId", "someone-elses").param("products[0].productId", "forged")
                        .param("products[0].name", "X").param("products[0].ean", "1")
                        .param("products[0].manufacturerCode", "m").param("products[0].label", "L")
                        .param("products[0].pricingGroup", "Default"))
                .andExpect(redirectedUrl(categoryPath()))
                .andExpect(flash().attribute("settingsSavedMessage", "catalog.products.added"));
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getCategoryId()).isEqualTo(gpu.getCategoryId());
        assertThat(saved.getValue().getProductId()).isNotBlank().isNotEqualTo("forged");
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
                    .param("products[" + index + "].ean", String.valueOf(index))
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

    @Test
    @SuppressWarnings("unchecked")
    void saveWithAnUnknownPricingGroupRerendersTheReviewWith422() throws Exception {
        // given
        gpu.getPriceDefinitions().add(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"));

        // when
        var result = mvc.perform(post(categoryPath() + "/products/add/save")
                        .param("products[0].name", "X").param("products[0].pricingGroup", "Nope"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("catalog/products-add-review"))
                .andReturn();

        // then
        assertThat((Map<String, String>) result.getModelAndView().getModel().get("errors"))
                .containsEntry("product-0-pricingGroup", "product.error.group.unknown");
        verify(productRepository, never()).save(any(Product.class));
    }
}
