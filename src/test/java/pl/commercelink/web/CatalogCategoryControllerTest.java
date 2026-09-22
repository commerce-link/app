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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.CategoryDefinitions;
import pl.commercelink.products.PimCategoryOptions;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.catalog.CatalogAccess;
import pl.commercelink.web.dtos.CategoryBasicsForm;
import pl.commercelink.web.settings.ConfirmAction;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
                storesRepository, pimCategoryOptions, messageSource)).build();
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
}
