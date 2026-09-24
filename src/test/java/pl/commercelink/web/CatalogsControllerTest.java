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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import pl.commercelink.pricelist.PricelistEventScheduler;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.CategoryDefinitions;
import pl.commercelink.products.PimCategoryOptions;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogDetailsService;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.web.catalog.CatalogAccess;
import pl.commercelink.web.catalog.CatalogRow;
import pl.commercelink.web.catalog.CategoryRow;
import pl.commercelink.web.dtos.CatalogSettingsForm;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
class CatalogsControllerTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private ProductCatalogRepository catalogRepository;
    @Mock
    private MessageSource messageSource;
    @Mock
    private ProductCatalogDetailsService detailsService;
    @Mock
    private CatalogAccess access;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private PimCategoryOptions pimCategoryOptions;
    @Mock
    private MarketplaceConnections marketplaces;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        CustomUser user = new CustomUser(null, null, Map.of("storeId", STORE_ID, "role", "ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        lenient().when(messageSource.getMessage(eq("catalog.schedule.default"), any(), any(Locale.class))).thenReturn("default");
        lenient().when(marketplaces.displayName(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        mvc = MockMvcBuilders.standaloneSetup(new CatalogsController(catalogRepository, messageSource, detailsService,
                access, productRepository, pimCategoryOptions, marketplaces,
                new CategoryDefinitions(catalogRepository, productRepository, mock(OptimisticLockingExecutor.class)))).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsCatalogsSortedByNameWithCategoryCounts() throws Exception {
        // given
        ProductCatalog parts = new ProductCatalog(STORE_ID, "Podzespoły");
        parts.getCategories().add(new CategoryDefinition().withName("GPU").withGeneratedId());
        CategoryDefinition dynamic = new CategoryDefinition().withName("OS").withGeneratedId();
        dynamic.setType(CategoryDefinitionType.Dynamic);
        parts.getCategories().add(dynamic);
        ProductCatalog accessories = new ProductCatalog(STORE_ID, "Akcesoria");
        when(catalogRepository.findAll(STORE_ID)).thenReturn(List.of(parts, accessories));

        // when
        var result = mvc.perform(get("/dashboard/catalogs")).andExpect(status().isOk())
                .andExpect(view().name("catalog/catalogs")).andReturn();

        // then
        @SuppressWarnings("unchecked")
        List<CatalogRow> rows = (List<CatalogRow>) result.getModelAndView().getModel().get("catalogs");
        assertThat(rows).extracting(CatalogRow::name).containsExactly("Akcesoria", "Podzespoły");
        assertThat(rows.get(1).categories()).isEqualTo(2);
        assertThat(rows.get(1).managed()).isEqualTo(1);
        assertThat(rows.get(1).dynamic()).isEqualTo(1);
        assertThat(rows.get(1).scheduleText()).isEqualTo("default");
        assertThat(rows.get(1).href()).isEqualTo("/dashboard/catalogs/" + parts.getCatalogId());
    }

    /**
     * D-M25/RF-30: the schedule text sits mid-sentence ("Cennik: {0}"), so a message that starts a sentence elsewhere
     * ("Co 30 min") must be lower-cased here.
     */
    @Test
    void scheduleTextLowercasesItsFirstLetterSoItReadsInsideASentence() {
        // given
        CatalogsController controller = new CatalogsController(catalogRepository, messageSource, detailsService,
                access, productRepository, pimCategoryOptions, marketplaces,
                new CategoryDefinitions(catalogRepository, productRepository, mock(OptimisticLockingExecutor.class)));
        Locale polish = Locale.forLanguageTag("pl");
        when(messageSource.getMessage(eq("store.supplier.schedule.summary.every.minutes"), any(), eq(polish)))
                .thenReturn("Co 30 min");

        // when
        String text = controller.scheduleText("0/30 * * * ? *", polish);

        // then
        assertThat(text).isEqualTo("co 30 min");
    }

    @Test
    void invalidSettingsAnswer422WithTheFormFragmentWhenAsync() throws Exception {
        // given
        when(detailsService.minIntervalMinutes()).thenReturn(5);
        ProductCatalog catalog = new ProductCatalog(STORE_ID, "Parts");
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(catalog);

        // when / then
        mvc.perform(post("/dashboard/catalogs/c1/settings").header("X-Requested-With", "fetch").param("name", ""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("catalog/catalog-settings :: settingsForm"))
                .andExpect(model().attribute("errors", hasKey("name")));
        verify(detailsService, never()).save(any(), any(), any());
    }

    /**
     * RF-27: a save the service could not complete (the repository or the schedule behind it failed) is no fault of
     * the schedule field; it is shown as an alert above the form, with no field marked.
     */
    @Test
    void aFailedSaveIsAnAlertAboveTheFormNotAnErrorOfTheSchedule() throws Exception {
        // given
        when(detailsService.minIntervalMinutes()).thenReturn(5);
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(new ProductCatalog(STORE_ID, "Parts"));
        when(detailsService.save(eq(STORE_ID), eq("c1"), any())).thenReturn(new ProductCatalogDetailsService.UpdateResult(
                List.of(pl.commercelink.inventory.supplier.ErrorMessage.of("catalog.save.error.failed"))));

        // when / then
        mvc.perform(post("/dashboard/catalogs/c1/settings").header("X-Requested-With", "fetch").param("name", "Parts"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("catalog/catalog-settings :: settingsForm"))
                .andExpect(model().attribute("errors", Map.of()))
                .andExpect(model().attribute("formError", "catalog.save.error.failed"));
    }

    /** A schedule the service refuses (too frequent, unreadable) stays at the schedule field. */
    @Test
    void aScheduleTheServiceRefusesStaysAtTheScheduleField() throws Exception {
        // given
        when(detailsService.minIntervalMinutes()).thenReturn(5);
        when(detailsService.save(eq(STORE_ID), anyString(), any())).thenReturn(new ProductCatalogDetailsService.UpdateResult(
                List.of(pl.commercelink.inventory.supplier.ErrorMessage.of("catalog.pricelist.schedule.error.too.frequent", "rate(1 minute)", 5))));

        // when / then
        mvc.perform(post("/dashboard/catalogs/new").param("name", "Parts").param("newCatalogId", "k3y0000009"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("errors", hasKey("pricelistSchedule")))
                .andExpect(model().attributeDoesNotExist("formError"));
    }

    @Test
    void savedSettingsRedirectToTheCatalogWithAFlash() throws Exception {
        // given
        when(detailsService.minIntervalMinutes()).thenReturn(5);
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(new ProductCatalog(STORE_ID, "Parts"));
        when(detailsService.save(eq(STORE_ID), eq("c1"), any())).thenReturn(new ProductCatalogDetailsService.UpdateResult(List.of()));
        when(messageSource.getMessage(eq("catalog.saved"), any(), any(Locale.class))).thenReturn("Saved");

        // when / then
        mvc.perform(post("/dashboard/catalogs/c1/settings").param("name", "Parts").param("deletionProtection", "true"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/dashboard/catalogs/c1"))
                .andExpect(flash().attribute("settingsSavedMessage", "Saved"));
    }

    @Test
    void protectedCatalogIsNotDeleted() throws Exception {
        // given
        ProductCatalog catalog = new ProductCatalog(STORE_ID, "Parts");
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(catalog);
        when(messageSource.getMessage(eq("catalog.delete.protected"), any(), any(Locale.class))).thenReturn("protected");

        // when / then
        mvc.perform(post("/dashboard/catalogs/c1/delete")).andExpect(redirectedUrl("/dashboard/catalogs/c1/settings"))
                .andExpect(flash().attribute("catalogError", "protected"));
        verify(detailsService, never()).delete(any(), any());
    }

    @Test
    void unprotectedCatalogIsDeletedThroughTheService() throws Exception {
        // given
        ProductCatalog catalog = new ProductCatalog(STORE_ID, "Parts");
        catalog.setDeletionProtection(false);
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(catalog);
        when(detailsService.delete(STORE_ID, "c1")).thenReturn(new ProductCatalogDetailsService.UpdateResult(List.of()));
        when(messageSource.getMessage(eq("catalog.deleted"), any(), any(Locale.class))).thenReturn("Deleted");

        // when / then
        mvc.perform(post("/dashboard/catalogs/c1/delete")).andExpect(redirectedUrl("/dashboard/catalogs"))
                .andExpect(flash().attribute("settingsSavedMessage", "Deleted"));
    }

    @Test
    void untickingTheProtectionSavesItOff() throws Exception {
        // given
        when(detailsService.minIntervalMinutes()).thenReturn(5);
        ProductCatalog catalog = new ProductCatalog(STORE_ID, "Parts");
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(catalog);
        when(detailsService.save(eq(STORE_ID), eq("c1"), any())).thenReturn(new ProductCatalogDetailsService.UpdateResult(List.of()));

        // when
        mvc.perform(post("/dashboard/catalogs/c1/settings").param("name", "Parts")).andExpect(status().isFound());

        // then
        ArgumentCaptor<ProductCatalog> submitted = ArgumentCaptor.forClass(ProductCatalog.class);
        verify(detailsService).save(eq(STORE_ID), eq("c1"), submitted.capture());
        assertThat(submitted.getValue().isDeletionProtection()).isFalse();
    }

    @Test
    void aNewCatalogStartsProtected() throws Exception {
        // given
        when(detailsService.minIntervalMinutes()).thenReturn(5);

        // when
        var result = mvc.perform(get("/dashboard/catalogs/new")).andExpect(status().isOk()).andReturn();

        // then
        CatalogSettingsForm form = (CatalogSettingsForm) result.getModelAndView().getModel().get("form");
        assertThat(form.isDeletionProtection()).isTrue();
    }

    @Test
    void aNewCatalogIsSavedUnderAFreshIdAndOpensIt() throws Exception {
        // given
        when(detailsService.minIntervalMinutes()).thenReturn(5);
        when(detailsService.save(eq(STORE_ID), anyString(), any())).thenReturn(new ProductCatalogDetailsService.UpdateResult(List.of()));
        when(messageSource.getMessage(eq("catalog.created"), any(), any(Locale.class))).thenReturn("Created");

        // when
        var result = mvc.perform(post("/dashboard/catalogs/new").param("name", "Parts"))
                .andExpect(status().isFound())
                .andExpect(flash().attribute("settingsSavedMessage", "Created"))
                .andReturn();

        // then
        ArgumentCaptor<String> catalogId = ArgumentCaptor.forClass(String.class);
        verify(detailsService).save(eq(STORE_ID), catalogId.capture(), any());
        assertThat(catalogId.getValue()).isNotBlank();
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/dashboard/catalogs/" + catalogId.getValue());
    }

    @Test
    void aNewCatalogWithoutANameAnswers422WithTheFormFragment() throws Exception {
        // given
        when(detailsService.minIntervalMinutes()).thenReturn(5);

        // when / then
        mvc.perform(post("/dashboard/catalogs/new").header("X-Requested-With", "fetch").param("name", "  "))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("catalog/catalog-settings :: settingsForm"))
                .andExpect(model().attribute("errors", hasKey("name")));
        verify(detailsService, never()).save(any(), any(), any());
    }

    @Test
    void aProtectedCatalogIsNotOfferedForDeletion() throws Exception {
        // given
        when(detailsService.minIntervalMinutes()).thenReturn(5);
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(new ProductCatalog(STORE_ID, "Parts"));

        // when
        var result = mvc.perform(get("/dashboard/catalogs/c1/settings")).andExpect(status().isOk())
                .andExpect(view().name("catalog/catalog-settings")).andReturn();

        // then
        assertThat(result.getModelAndView().getModel()).containsEntry("deleteHref", null);
        verify(productRepository, never()).findAll(any(ProductCatalog.class));
    }

    @Test
    void anUnprotectedCatalogIsOfferedForDeletionWithItsCounts() throws Exception {
        // given
        when(detailsService.minIntervalMinutes()).thenReturn(5);
        ProductCatalog catalog = new ProductCatalog(STORE_ID, "Parts");
        catalog.setDeletionProtection(false);
        catalog.getCategories().add(new CategoryDefinition().withName("GPU").withGeneratedId());
        when(access.requireCatalog(STORE_ID, catalog.getCatalogId())).thenReturn(catalog);
        when(productRepository.findAll(catalog)).thenReturn(List.of(new Product("k1"), new Product("k1")));

        // when / then
        mvc.perform(get("/dashboard/catalogs/" + catalog.getCatalogId() + "/settings")).andExpect(status().isOk())
                .andExpect(model().attribute("deleteHref", "/dashboard/catalogs/" + catalog.getCatalogId() + "/delete"))
                .andExpect(model().attribute("categoriesCount", 1))
                .andExpect(model().attribute("productsCount", 2));
    }

    @Test
    void theDeleteConfirmationIsRefusedForAProtectedCatalog() throws Exception {
        // given
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(new ProductCatalog(STORE_ID, "Parts"));
        when(messageSource.getMessage(eq("catalog.delete.protected"), any(), any(Locale.class))).thenReturn("protected");

        // when / then
        mvc.perform(get("/dashboard/catalogs/c1/delete")).andExpect(redirectedUrl("/dashboard/catalogs/c1/settings"))
                .andExpect(flash().attribute("catalogError", "protected"));
        verify(productRepository, never()).findAll(any(ProductCatalog.class));
    }

    @Test
    void catalogPageListsCategoriesInSequenceOrderWithProductCounts() throws Exception {
        // given
        ProductCatalog catalog = new ProductCatalog(STORE_ID, "Parts");
        CategoryDefinition cpu = new CategoryDefinition().withName("CPU").withGeneratedId().withSequenceNumber(2);
        CategoryDefinition gpu = new CategoryDefinition().withName("GPU").withGeneratedId().withSequenceNumber(1);
        catalog.getCategories().addAll(List.of(cpu, gpu));
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(catalog);
        when(pimCategoryOptions.namesOf(any())).thenReturn(List.of());
        when(productRepository.labelsOf(gpu.getCategoryId())).thenReturn(List.of("l"));
        when(productRepository.labelsOf(cpu.getCategoryId())).thenReturn(List.of());

        // when
        var result = mvc.perform(get("/dashboard/catalogs/c1")).andExpect(status().isOk())
                .andExpect(view().name("catalog/catalog")).andReturn();

        // then
        @SuppressWarnings("unchecked")
        List<CategoryRow> rows = (List<CategoryRow>) result.getModelAndView().getModel().get("categories");
        assertThat(rows).extracting(CategoryRow::name).containsExactly("GPU", "CPU");
        assertThat(rows.get(0).productsCount()).isEqualTo(1);
        assertThat(result.getModelAndView().getModel().get("productsTotal")).isEqualTo(1);
    }

    /**
     * Removing a category keeps its products when another category of the catalog is mapped to one of the same PIM
     * categories. The dialog on the catalog page must say the same as the confirmation page behind the link.
     */
    @Test
    void theCatalogPageSaysWhetherDeletingACategoryWouldKeepItsProducts() throws Exception {
        // given
        ProductCatalog catalog = new ProductCatalog(STORE_ID, "Parts");
        CategoryDefinition gpu = managed("GPU", "pim-gpu");
        CategoryDefinition gaming = managed("Gaming", "pim-gpu");
        CategoryDefinition cpu = managed("CPU", "pim-cpu");
        catalog.getCategories().addAll(List.of(gpu, gaming, cpu));
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(catalog);
        when(pimCategoryOptions.namesOf(any())).thenReturn(List.of());
        when(productRepository.labelsOf(gpu.getCategoryId())).thenReturn(List.of());
        when(productRepository.labelsOf(gaming.getCategoryId())).thenReturn(List.of());
        when(productRepository.labelsOf(cpu.getCategoryId())).thenReturn(List.of("l"));

        // when
        var result = mvc.perform(get("/dashboard/catalogs/c1")).andExpect(status().isOk()).andReturn();

        // then
        @SuppressWarnings("unchecked")
        List<CategoryRow> rows = (List<CategoryRow>) result.getModelAndView().getModel().get("categories");
        assertThat(rows.get(0).productsKept()).isTrue();
        assertThat(rows.get(0).productsToDelete()).isZero();
        assertThat(rows.get(2).productsKept()).isFalse();
        assertThat(rows.get(2).productsToDelete()).isEqualTo(1);
    }

    private static CategoryDefinition managed(String name, String pimCategoryId) {
        CategoryDefinition category = new CategoryDefinition().withName(name).withGeneratedId();
        category.setPimCategoryIds(List.of(pimCategoryId));
        return category;
    }

    @Test
    void anAutomaticCategoryIsNotCountedAgainstTheProductsTable() throws Exception {
        // given
        ProductCatalog catalog = new ProductCatalog(STORE_ID, "Parts");
        CategoryDefinition os = new CategoryDefinition().withName("OS").withGeneratedId();
        os.setType(CategoryDefinitionType.Dynamic);
        catalog.getCategories().add(os);
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(catalog);
        when(pimCategoryOptions.namesOf(any())).thenReturn(List.of());

        // when
        var result = mvc.perform(get("/dashboard/catalogs/c1")).andExpect(status().isOk()).andReturn();

        // then
        assertThat(result.getModelAndView().getModel().get("productsTotal")).isEqualTo(0);
        verify(productRepository, never()).findAll(anyString());
        verify(productRepository, never()).labelsOf(anyString());
    }

    /**
     * D-I7: the page needs a count and the labels of each manual category, never the products themselves (production:
     * 23 queries and 1 193 full items on every visit). The labels answer the count, the products off the label list
     * and the deletion preview.
     */
    @Test
    void theCatalogPageReadsTheLabelsOfTheProductsNotTheProducts() throws Exception {
        // given
        ProductCatalog catalog = new ProductCatalog(STORE_ID, "Parts");
        CategoryDefinition gpu = managed("GPU", "pim-gpu");
        gpu.setGroupingOrder(List.of("RTX 5060"));
        catalog.getCategories().add(gpu);
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(catalog);
        when(pimCategoryOptions.namesOf(any())).thenReturn(List.of());
        when(productRepository.labelsOf(gpu.getCategoryId())).thenReturn(List.of("RTX 5060", "RTX 4060", "RTX 4060"));

        // when
        var result = mvc.perform(get("/dashboard/catalogs/c1")).andExpect(status().isOk()).andReturn();

        // then
        @SuppressWarnings("unchecked")
        List<CategoryRow> rows = (List<CategoryRow>) result.getModelAndView().getModel().get("categories");
        assertThat(rows.get(0).productsCount()).isEqualTo(3);
        assertThat(rows.get(0).productsOutsideLabels()).isEqualTo(2);
        assertThat(rows.get(0).productsToDelete()).isEqualTo(3);
        assertThat(result.getModelAndView().getModel().get("productsTotal")).isEqualTo(3);
        verify(productRepository).labelsOf(gpu.getCategoryId());
        verify(productRepository, never()).findAll(anyString());
        verify(productRepository, never()).findAll(any(ProductCatalog.class));
    }

    @Test
    void theCatalogPageLinksToItsSettingsAndTheNewCategory() throws Exception {
        // given
        when(access.requireCatalog(STORE_ID, "c1")).thenReturn(new ProductCatalog(STORE_ID, "Parts"));

        // when / then
        mvc.perform(get("/dashboard/catalogs/c1")).andExpect(status().isOk())
                .andExpect(model().attribute("settingsHref", "/dashboard/catalogs/c1/settings"))
                .andExpect(model().attribute("addCategoryHref", "/dashboard/catalogs/c1/category/new"))
                .andExpect(model().attribute("scheduleText", "default"));
    }

    @Test
    void unknownCatalogIs404() throws Exception {
        // given
        when(access.requireCatalog(STORE_ID, "nope")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        // when / then
        mvc.perform(get("/dashboard/catalogs/nope")).andExpect(status().isNotFound());
    }

    @Test
    void theNewCatalogFormCarriesTheIdTheCatalogWillGet() throws Exception {
        // given
        when(detailsService.minIntervalMinutes()).thenReturn(5);

        // when
        var result = mvc.perform(get("/dashboard/catalogs/new")).andExpect(status().isOk()).andReturn();

        // then
        CatalogSettingsForm form = (CatalogSettingsForm) result.getModelAndView().getModel().get("form");
        assertThat(form.getNewCatalogId()).matches("[a-z0-9]{10}");
    }

    /**
     * RF-5: the id is given when the form is shown, so the form sent twice (Back, a double click without JavaScript)
     * creates one catalog with one schedule; the second POST finds it and opens it. Runs the real details service
     * over a repository that remembers what it saved.
     */
    @Test
    void twoPostsOfOneNewCatalogFormCreateOneCatalogAndOneSchedule() throws Exception {
        // given
        Map<String, ProductCatalog> stored = new HashMap<>();
        when(catalogRepository.findById(eq(STORE_ID), anyString())).thenAnswer(call -> stored.get(call.<String>getArgument(1)));
        doAnswer(call -> stored.put(call.<ProductCatalog>getArgument(0).getCatalogId(), call.getArgument(0)))
                .when(catalogRepository).save(any(ProductCatalog.class));
        PricelistEventScheduler scheduler = mock(PricelistEventScheduler.class);
        ProductCatalogDetailsService realDetails = new ProductCatalogDetailsService(catalogRepository, productRepository, scheduler, 5);
        MockMvc withRealDetails = MockMvcBuilders.standaloneSetup(new CatalogsController(catalogRepository, messageSource,
                realDetails, access, productRepository, pimCategoryOptions, marketplaces,
                new CategoryDefinitions(catalogRepository, productRepository, mock(OptimisticLockingExecutor.class)))).build();
        when(messageSource.getMessage(eq("catalog.created"), any(), any(Locale.class))).thenReturn("Created");
        var create = post("/dashboard/catalogs/new").param("name", "Parts").param("newCatalogId", "k3y0000001");

        // when
        withRealDetails.perform(create).andExpect(redirectedUrl("/dashboard/catalogs/k3y0000001"));
        withRealDetails.perform(create)
                .andExpect(redirectedUrl("/dashboard/catalogs/k3y0000001"))
                .andExpect(flash().attribute("settingsSavedMessage", "Created"));

        // then
        assertThat(stored).containsOnlyKeys("k3y0000001");
        verify(catalogRepository).save(any(ProductCatalog.class));
        verify(scheduler).schedule(eq(STORE_ID), eq("k3y0000001"), any());
    }

    /** Only the generator's format is taken from the request: an id of another shape was never on the page. */
    @Test
    void aNewCatalogIdOfAnotherShapeIsRefused() throws Exception {
        // when / then
        mvc.perform(post("/dashboard/catalogs/new").param("name", "Parts").param("newCatalogId", "cat-local-01"))
                .andExpect(status().isBadRequest());
        verify(detailsService, never()).save(any(), any(), any());
    }

    /** The second POST lost the race inside the service: the catalog is the first one's, and it is opened. */
    @Test
    void aNewCatalogCreatedMeanwhileByTheSameFormIsOpened() throws Exception {
        // given
        when(detailsService.minIntervalMinutes()).thenReturn(5);
        when(detailsService.save(eq(STORE_ID), eq("k3y0000002"), any()))
                .thenReturn(new ProductCatalogDetailsService.UpdateResult(List.of(), true));

        // when / then
        mvc.perform(post("/dashboard/catalogs/new").param("name", "Parts").param("newCatalogId", "k3y0000002"))
                .andExpect(redirectedUrl("/dashboard/catalogs/k3y0000002"));
    }
}
