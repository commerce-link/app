package pl.commercelink.web;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import pl.commercelink.products.AvailabilityDefinition;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.CategoryDefinitions;
import pl.commercelink.products.MarketplaceDefinition;
import pl.commercelink.products.PimCategoryOptions;
import pl.commercelink.products.PriceDefinition;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.products.InventoryDefinition;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductRecommendationEngine;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.StockDefinition;
import pl.commercelink.products.filters.InventoryFilterType;
import pl.commercelink.starter.dynamodb.Metadata;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.catalog.CatalogAccess;
import pl.commercelink.web.catalog.MarketplaceDefinitionRow;
import pl.commercelink.web.dtos.CategoryBasicsForm;
import pl.commercelink.web.dtos.CategoryPricingForm;
import pl.commercelink.web.dtos.RecommendationFiltersForm;
import pl.commercelink.web.settings.ConfirmAction;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
class CatalogCategoryControllerTest {

    private static final String STORE_ID = "store-1";

    private static final String BRAND_LINE_KEY = "catalog.filter.brandLines.line";

    @Mock
    private CatalogAccess access;
    @Mock
    private CategoryDefinitions definitions;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private PimCategoryOptions pimCategoryOptions;
    @Mock
    private MarketplaceConnections marketplaces;
    @Mock
    private ProductRecommendationEngine recommendationEngine;
    @Mock
    private Inventory inventory;
    @Mock
    private MessageSource messageSource;
    @Mock
    private Store store;

    private ProductCatalog catalog;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        CustomUser user = new CustomUser(null, null, Map.of("storeId", STORE_ID, "role", "ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        catalog = new ProductCatalog(STORE_ID, "Podzespoły");
        catalog.setCatalogId("c1");
        lenient().when(storesRepository.findById(STORE_ID)).thenReturn(store);
        lenient().when(store.getEnabledCategories()).thenReturn(List.of());
        lenient().when(pimCategoryOptions.leafOptionsUnder(any(), any())).thenReturn(List.of());
        lenient().when(pimCategoryOptions.optionsOf(any())).thenReturn(List.of());
        lenient().when(pimCategoryOptions.ancestorsOf(any())).thenReturn(List.of());
        lenient().when(productRepository.findAll(any(String.class))).thenReturn(List.of());
        lenient().when(messageSource.getMessage(any(String.class), any(), any(Locale.class))).thenAnswer(call -> call.getArgument(0));
        mvc = MockMvcBuilders.standaloneSetup(new CatalogCategoryController(access, definitions, productRepository,
                storesRepository, pimCategoryOptions, marketplaces, recommendationEngine, inventory, messageSource)).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private CategoryDefinition categoryOf(String name) {
        CategoryDefinition category = new CategoryDefinition().withName(name).withGeneratedId();
        catalog.getCategories().add(category);
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(catalog);
        when(access.requireCategory(catalog, category.getCategoryId())).thenReturn(category);
        return category;
    }

    @Test
    void theSettingsHubLinksToTheFourPagesOfTheCategory() throws Exception {
        // given
        CategoryDefinition gpu = categoryOf("GPU");

        // when / then
        mvc.perform(get("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings"))
                .andExpect(status().isOk())
                .andExpect(view().name("catalog/category-settings"))
                .andExpect(model().attribute("basicsHref", "/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/basics"))
                .andExpect(model().attribute("pricingHref", "/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/pricing"))
                .andExpect(model().attribute("marketplacesHref", "/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/marketplaces"))
                .andExpect(model().attribute("filtersHref", "/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/filters"))
                .andExpect(model().attribute("backHref", "/dashboard/catalogs/c1/category/" + gpu.getCategoryId()));
    }

    @Test
    void theNewCategoryFormStartsProtectedAndWithoutADeleteLink() throws Exception {
        // given
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(catalog);

        // when
        var result = mvc.perform(get("/dashboard/catalogs/c1/category/new")).andExpect(status().isOk())
                .andExpect(view().name("catalog/category-basics"))
                .andExpect(model().attribute("existing", false))
                .andExpect(model().attribute("deleteHref", (Object) null))
                .andExpect(model().attribute("formAction", "/dashboard/catalogs/c1/category/new"))
                .andReturn();

        // then
        CategoryBasicsForm form = (CategoryBasicsForm) result.getModelAndView().getModel().get("form");
        assertThat(form.isDeletionProtection()).isTrue();
    }

    @Test
    void creatingACategoryRedirectsToItsPageWithTheDefaultsNotice() throws Exception {
        // given
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(catalog);
        CategoryDefinition created = new CategoryDefinition().withName("CPU").withGeneratedId();
        when(definitions.create(eq(catalog), any())).thenReturn(created);
        when(messageSource.getMessage(eq("catalog.category.created"), any(), any(Locale.class))).thenReturn("Added");
        when(messageSource.getMessage(eq("catalog.category.created.defaults"), any(), any(Locale.class))).thenReturn("Defaults");

        // when / then
        mvc.perform(post("/dashboard/catalogs/c1/category/new").param("name", "CPU").param("type", "Managed").param("maxQty", "1"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/dashboard/catalogs/c1/category/" + created.getCategoryId()))
                .andExpect(flash().attribute("settingsSavedMessage", "Added"))
                .andExpect(flash().attribute("categoryNotice", "Defaults"));
    }

    @Test
    void anAsyncCreationAnswersWithTheFormFragmentAndTheAddressToOpen() throws Exception {
        // given
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(catalog);
        CategoryDefinition created = new CategoryDefinition().withName("CPU").withGeneratedId();
        when(definitions.create(eq(catalog), any())).thenReturn(created);

        // when / then
        mvc.perform(post("/dashboard/catalogs/c1/category/new").header("X-Requested-With", "fetch")
                        .param("name", "CPU").param("type", "Managed").param("maxQty", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("catalog/category-basics :: basicsForm"))
                .andExpect(model().attribute("redirectTo", "/dashboard/catalogs/c1/category/" + created.getCategoryId()));
    }

    @Test
    void duplicateNameIsRejectedWith422WhenAsync() throws Exception {
        // given
        catalog.getCategories().add(new CategoryDefinition().withName("CPU").withGeneratedId());
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(catalog);

        // when / then
        mvc.perform(post("/dashboard/catalogs/c1/category/new").header("X-Requested-With", "fetch")
                        .param("name", "cpu").param("type", "Managed").param("maxQty", "1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("catalog/category-basics :: basicsForm"))
                .andExpect(model().attribute("errors", hasEntry("name", "catalog.category.name.duplicate")));
        verify(definitions, never()).create(any(), any());
    }

    @Test
    void savingBasicsGoesThroughTheServiceAndBackToTheHub() throws Exception {
        // given
        CategoryDefinition gpu = categoryOf("GPU");
        when(messageSource.getMessage(eq("catalog.category.basics.saved"), any(), any(Locale.class))).thenReturn("Saved");

        // when / then
        mvc.perform(post("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/basics")
                        .param("name", "Karty graficzne").param("type", "Managed").param("maxQty", "3").param("sequenceNumber", "2")
                        .param("labels[0]", "RTX 5060"))
                .andExpect(redirectedUrl("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings"))
                .andExpect(flash().attribute("settingsSavedMessage", "Saved"));
        ArgumentCaptor<CategoryDefinitions.Basics> basics = ArgumentCaptor.forClass(CategoryDefinitions.Basics.class);
        verify(definitions).saveBasics(eq(catalog), eq(gpu), basics.capture());
        assertThat(basics.getValue().name()).isEqualTo("Karty graficzne");
        assertThat(basics.getValue().labels()).containsExactly("RTX 5060");
    }

    /** An unticked protection box is not posted at all; the save must switch the protection off, not keep the old value. */
    @Test
    void savingBasicsWithoutTheProtectionBoxSwitchesTheProtectionOff() throws Exception {
        // given
        CategoryDefinition gpu = categoryOf("GPU");

        // when
        mvc.perform(post("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/basics")
                .param("name", "GPU").param("type", "Managed").param("maxQty", "1"));

        // then
        ArgumentCaptor<CategoryDefinitions.Basics> basics = ArgumentCaptor.forClass(CategoryDefinitions.Basics.class);
        verify(definitions).saveBasics(eq(catalog), eq(gpu), basics.capture());
        assertThat(basics.getValue().deletionProtection()).isFalse();
    }

    @Test
    void turningAManagedCategoryIntoAnAutomaticOneSaysWhatHappensToItsProducts() throws Exception {
        // given
        CategoryDefinition gpu = categoryOf("GPU");
        when(messageSource.getMessage(eq("catalog.category.basics.saved"), any(), any(Locale.class))).thenReturn("Saved.");
        when(messageSource.getMessage(eq("catalog.category.type.changed.dynamic"), any(), any(Locale.class))).thenReturn("Manual products go in 7 days.");

        // when / then
        mvc.perform(post("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/basics")
                        .param("name", "GPU").param("type", "Dynamic").param("maxQty", "1"))
                .andExpect(flash().attribute("settingsSavedMessage", "Saved. Manual products go in 7 days."));
    }

    @Test
    void deletingAProtectedCategoryIsRefused() throws Exception {
        // given
        CategoryDefinition gpu = categoryOf("GPU");
        when(messageSource.getMessage(eq("catalog.category.delete.protected"), any(), any(Locale.class))).thenReturn("protected");

        // when / then
        mvc.perform(post("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/delete"))
                .andExpect(redirectedUrl("/dashboard/catalogs/c1")).andExpect(flash().attribute("catalogError", "protected"));
        verify(definitions, never()).remove(any(), any());
    }

    @Test
    void deletingAnUnprotectedCategoryGoesThroughTheServiceAndBackToTheCatalog() throws Exception {
        // given
        CategoryDefinition gpu = categoryOf("GPU");
        gpu.setDeletionProtection(false);
        when(definitions.remove(catalog, gpu)).thenReturn(new CategoryDefinitions.RemoveResult(false, 3));
        when(messageSource.getMessage(eq("catalog.category.deleted"), any(), any(Locale.class))).thenReturn("Deleted");

        // when / then
        mvc.perform(post("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/delete"))
                .andExpect(redirectedUrl("/dashboard/catalogs/c1"))
                .andExpect(flash().attribute("settingsSavedMessage", "Deleted"));
        verify(definitions).remove(catalog, gpu);
    }

    private ConfirmAction confirmationOf(MvcResult result) {
        return (ConfirmAction) result.getModelAndView().getModel().get("confirm");
    }

    @Test
    void confirmationPageSaysProductsAreKeptWhenTheServiceWouldKeepThem() throws Exception {
        // given
        CategoryDefinition gpu = categoryOf("GPU");
        when(definitions.deletionPreview(catalog, gpu)).thenReturn(new CategoryDefinitions.DeletionPreview(true, 0));
        when(messageSource.getMessage(eq("catalog.category.delete.message.kept"), any(), any(Locale.class))).thenReturn("kept");
        when(messageSource.getMessage(eq("catalog.category.delete.title"), any(), any(Locale.class))).thenReturn("title");
        when(messageSource.getMessage(eq("catalog.category.delete"), any(), any(Locale.class))).thenReturn("delete");

        // when
        MvcResult result = mvc.perform(get("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/delete"))
                .andExpect(view().name("settings-confirm")).andReturn();

        // then
        assertThat(confirmationOf(result).message()).isEqualTo("kept");
    }

    @Test
    void confirmationPageCountsTheProductsTheServiceWouldDelete() throws Exception {
        // given
        CategoryDefinition gpu = categoryOf("GPU");
        when(definitions.deletionPreview(catalog, gpu)).thenReturn(new CategoryDefinitions.DeletionPreview(false, 3));
        when(messageSource.getMessage(eq("catalog.category.delete.message"), any(), any(Locale.class)))
                .thenAnswer(call -> "deletes " + ((Object[]) call.getArgument(1))[0]);

        // when
        MvcResult result = mvc.perform(get("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/delete")).andReturn();

        // then
        assertThat(confirmationOf(result).message()).isEqualTo("deletes 3");
    }

    @Test
    void confirmationPageOfAnAutomaticCategorySaysNothingElseIsRemoved() throws Exception {
        // given
        CategoryDefinition os = categoryOf("OS");
        os.setType(CategoryDefinitionType.Dynamic);
        when(messageSource.getMessage(eq("catalog.category.delete.message.dynamic"), any(), any(Locale.class))).thenReturn("computed");

        // when
        MvcResult result = mvc.perform(get("/dashboard/catalogs/c1/category/" + os.getCategoryId() + "/delete")).andReturn();

        // then
        assertThat(confirmationOf(result).message()).isEqualTo("computed");
        verify(definitions, never()).deletionPreview(any(), any());
    }

    @Test
    void thePricingFormIsBuiltFromTheSavedCategory() throws Exception {
        // given
        CategoryDefinition gpu = categoryOf("GPU");
        gpu.withStockDefinition(new StockDefinition(2, 4, 10)).withAvailabilityDefinition(new AvailabilityDefinition(3, 1))
                .withPriceDefinition(new PriceDefinition(1.05, 49, 0, 0, 0, "Default"));

        // when
        MvcResult result = mvc.perform(get("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/pricing"))
                .andExpect(status().isOk())
                .andExpect(view().name("catalog/category-pricing"))
                .andExpect(model().attribute("formAction", "/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/pricing"))
                .andExpect(model().attribute("backHref", "/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings"))
                .andReturn();

        // then
        CategoryPricingForm form = (CategoryPricingForm) result.getModelAndView().getModel().get("form");
        assertThat(form.getCritical()).isEqualTo("2");
        assertThat(form.getGroups()).extracting(CategoryPricingForm.PriceGroupForm::getName).containsExactly("Default");
    }

    /** A group is removed by leaving its fields out of the post, so only the groups that did not come back are looked up. */
    @Test
    void pricingRemovedGroupsAreComputedFromTheDefinitionBeforeValidation() throws Exception {
        // given
        CategoryDefinition gpu = categoryOf("GPU");
        gpu.withPriceDefinition(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"))
                .withPriceDefinition(new PriceDefinition(1.1, 0, 0, 0, 0, "Premium"));
        when(definitions.productsInPriceGroup(gpu, "Premium")).thenReturn(4);

        // when / then
        mvc.perform(post("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/pricing")
                        .header("X-Requested-With", "fetch")
                        .param("critical", "1").param("low", "10").param("high", "30").param("minQty", "3").param("minProviders", "1")
                        .param("groups[0].name", "Default").param("groups[0].multiplier", "1,00").param("groups[0].minProfit", "0")
                        .param("groups[0].critical", "0").param("groups[0].low", "0").param("groups[0].medium", "0"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("catalog/category-pricing :: pricingForm"))
                .andExpect(model().attribute("errors", hasEntry("groups", "catalog.category.pricing.group.inUse")));
        verify(definitions, never()).savePricing(any(), any(), any(), any(), any());
        verify(definitions, never()).productsInPriceGroup(gpu, "Default");
    }

    @Test
    void validPricingIsSavedThroughTheService() throws Exception {
        // given
        CategoryDefinition gpu = categoryOf("GPU");
        when(messageSource.getMessage(eq("catalog.category.pricing.saved"), any(), any(Locale.class))).thenReturn("Saved");

        // when / then
        mvc.perform(post("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/pricing")
                        .param("critical", "1").param("low", "10").param("high", "30").param("minQty", "3").param("minProviders", "1")
                        .param("groups[0].name", "Default").param("groups[0].multiplier", "1,05").param("groups[0].minProfit", "49")
                        .param("groups[0].critical", "0").param("groups[0].low", "0").param("groups[0].medium", "0"))
                .andExpect(redirectedUrl("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings"))
                .andExpect(flash().attribute("settingsSavedMessage", "Saved"));
        verify(definitions).savePricing(eq(catalog), eq(gpu), any(StockDefinition.class), any(AvailabilityDefinition.class),
                argThat(groups -> groups.size() == 1 && groups.get(0).getMultiplier() == 1.05));
    }

    @Test
    void theFiltersFormIsBuiltFromTheSavedDefinitions() throws Exception {
        // given
        CategoryDefinition gpu = categoryOf("GPU");
        gpu.getInventoryDefinitions().add(new InventoryDefinition(InventoryFilterType.BRAND_NAME,
                List.of(new Metadata("Brands", "MSI, ASUS"))));

        // when
        MvcResult result = mvc.perform(get("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/filters"))
                .andExpect(status().isOk())
                .andExpect(view().name("catalog/category-filters"))
                .andExpect(model().attribute("formAction", "/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/filters"))
                .andExpect(model().attribute("backHref", "/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings"))
                .andReturn();

        // then
        RecommendationFiltersForm form = (RecommendationFiltersForm) result.getModelAndView().getModel().get("form");
        assertThat(form.getFilters()).extracting(RecommendationFiltersForm.FilterForm::getValues).containsExactly("MSI, ASUS");
        // a category without PIM categories has nothing to count, so the inventory is not read at all
        verify(inventory, never()).withEnabledSuppliersOnly(any());
    }

    /** The count in the lead is a pass over the inventory; an inventory that cannot answer must not take the page down. */
    @Test
    void theFiltersPageOpensWhenTheRecommendationsCannotBeCounted() throws Exception {
        // given
        CategoryDefinition gpu = categoryOf("GPU");
        gpu.setPimCategoryIds(List.of("pim-1"));
        InventoryView view = mock(InventoryView.class);
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(view);
        when(recommendationEngine.getRecommendations(gpu, view)).thenThrow(new IllegalStateException("PIM is down"));
        when(messageSource.getMessage(eq("catalog.category.filters.lead"), any(), any(Locale.class))).thenReturn("filters");

        // when / then
        mvc.perform(get("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/filters"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("lead", "GPU \u00b7 filters"));
    }

    @Test
    void validFiltersAreSavedThroughTheService() throws Exception {
        // given
        CategoryDefinition gpu = categoryOf("GPU");
        when(messageSource.getMessage(eq("catalog.category.filters.saved"), any(), any(Locale.class))).thenReturn("Saved");

        // when / then
        mvc.perform(post("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/filters")
                        .param("filters[0].type", "BRAND_NAME").param("filters[0].values", "MSI, ASUS")
                        .param("filters[1].type", "PRICE_RANGE").param("filters[1].minPrice", "900"))
                .andExpect(redirectedUrl("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings"))
                .andExpect(flash().attribute("settingsSavedMessage", "Saved"));
        ArgumentCaptor<List<InventoryDefinition>> filters = ArgumentCaptor.forClass(List.class);
        verify(definitions).saveFilters(eq(catalog), eq(gpu), filters.capture());
        assertThat(filters.getValue()).extracting(InventoryDefinition::getType)
                .containsExactly(InventoryFilterType.BRAND_NAME, InventoryFilterType.PRICE_RANGE);
        assertThat(filters.getValue()).allMatch(InventoryDefinition::isComplete);
    }

    /** The line of a broken brand line is part of the message, so the page is given the text instead of the key. */
    @Test
    void brandLineErrorIsTranslatedWithTheLineNumber() throws Exception {
        // given
        CategoryDefinition gpu = categoryOf("GPU");
        when(messageSource.getMessage(eq(BRAND_LINE_KEY), eq(new Object[]{"2"}), any(Locale.class))).thenReturn("Line 2 broken");

        // when / then
        mvc.perform(post("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/filters")
                        .header("X-Requested-With", "fetch")
                        .param("filters[0].type", "PRODUCT_LINE_BY_BRAND").param("filters[0].brandLines", "Gigabyte: Eagle\nMSI Ventus"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("catalog/category-filters :: filtersForm"))
                .andExpect(model().attribute("errors", hasEntry("filter-0-brandLines", "Line 2 broken")));
        verify(definitions, never()).saveFilters(any(), any(), any());
    }

    @Test
    void theBasicsFormOfAManagedCategoryCountsItsProductsOnce() throws Exception {
        // given
        CategoryDefinition gpu = categoryOf("GPU");
        gpu.setGroupingOrder(List.of("RTX 5060"));

        // when / then
        mvc.perform(get("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/basics"))
                .andExpect(status().isOk())
                .andExpect(view().name("catalog/category-basics"))
                .andExpect(model().attribute("existing", true))
                .andExpect(model().attribute("formAction", "/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/basics"));
        verify(productRepository).findAll(gpu.getCategoryId());
    }

    @Test
    void marketplacesPageListsStoreMarketplacesAndOrphanedDefinitions() throws Exception {
        // given
        CategoryDefinition gpu = new CategoryDefinition().withName("GPU").withGeneratedId()
                .withMarketplaceDefinition(new MarketplaceDefinition("allegro", 1.1, 5, 1, 2, 1, 3))
                .withMarketplaceDefinition(new MarketplaceDefinition(null, 1.0, 0, 5, 3, 0, 0));
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(catalog);
        when(access.requireCategory(catalog, gpu.getCategoryId())).thenReturn(gpu);
        MarketplaceIntegration allegro = mock(MarketplaceIntegration.class);
        when(allegro.getName()).thenReturn("allegro");
        MarketplaceIntegration empik = mock(MarketplaceIntegration.class);
        when(empik.getName()).thenReturn("empik");
        when(store.getMarketplaces()).thenReturn(List.of(allegro, empik));
        when(marketplaces.displayName("allegro")).thenReturn("Allegro");
        when(marketplaces.displayName("empik")).thenReturn("Empik");

        // when
        MvcResult result = mvc.perform(get("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/marketplaces"))
                .andExpect(status().isOk()).andReturn();

        // then
        @SuppressWarnings("unchecked")
        List<MarketplaceDefinitionRow> rows = (List<MarketplaceDefinitionRow>) result.getModelAndView().getModel().get("rows");
        assertThat(rows).extracting(MarketplaceDefinitionRow::displayName).containsExactly("Allegro", "Empik");
        assertThat(rows.get(0).state()).isEqualTo(MarketplaceDefinitionRow.State.EXPORTING);
        assertThat(rows.get(1).state()).isEqualTo(MarketplaceDefinitionRow.State.NOT_CONFIGURED);
        @SuppressWarnings("unchecked")
        List<MarketplaceDefinitionRow> orphans = (List<MarketplaceDefinitionRow>) result.getModelAndView().getModel().get("orphans");
        assertThat(orphans).hasSize(1);
        assertThat(orphans.get(0).deleteHref()).endsWith("/settings/marketplaces/_unnamed_/delete");
    }

    @Test
    void savingADefinitionForAMarketplaceTheStoreDoesNotHaveIs404() throws Exception {
        // given
        CategoryDefinition gpu = new CategoryDefinition().withName("GPU").withGeneratedId();
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(catalog);
        when(access.requireCategory(catalog, gpu.getCategoryId())).thenReturn(gpu);
        when(access.requireMarketplace(store, "empik")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        // when / then
        mvc.perform(post("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/marketplaces/empik").param("markup", "1,1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void untickingTheExportBoxTurnsTheDefinitionOff() throws Exception {
        // given — an unticked checkbox is absent from the POST, so the bound form must read it as "off"
        CategoryDefinition gpu = new CategoryDefinition().withName("GPU").withGeneratedId()
                .withMarketplaceDefinition(new MarketplaceDefinition("allegro", 1.1, 5, 1, 2, 1, 3));
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(catalog);
        when(access.requireCategory(catalog, gpu.getCategoryId())).thenReturn(gpu);
        when(access.requireMarketplace(store, "allegro")).thenReturn("allegro");

        // when / then
        mvc.perform(post("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/marketplaces/allegro")
                        .param("markup", "1,10").param("minWarehouseQty", "3").param("minQtyPerDistributor", "0")
                        .param("minNumOfDistributors", "0").param("minNumOfLocalDistributors", "0")
                        .param("minDistributorsQty", "0"))
                .andExpect(redirectedUrl("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/marketplaces"));
        verify(definitions).saveMarketplace(eq(catalog), eq(gpu),
                argThat(definition -> !definition.isEnabled() && definition.getMinWarehouseQty() == 3));
    }

    @Test
    void removingTheUnnamedDefinitionRemovesExactlyTheOneWithoutAName() throws Exception {
        // given
        CategoryDefinition gpu = new CategoryDefinition().withName("GPU").withGeneratedId()
                .withMarketplaceDefinition(new MarketplaceDefinition(null, 1.0, 0, 5, 3, 0, 0));
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(catalog);
        when(access.requireCategory(catalog, gpu.getCategoryId())).thenReturn(gpu);
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class))).thenReturn("x");

        // when / then
        mvc.perform(post("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/marketplaces/_unnamed_/delete"))
                .andExpect(redirectedUrl("/dashboard/catalogs/c1/category/" + gpu.getCategoryId() + "/settings/marketplaces"));
        verify(definitions).removeMarketplace(catalog, gpu, null);
    }
}
