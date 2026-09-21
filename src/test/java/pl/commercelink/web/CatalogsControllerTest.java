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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogDetailsService;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.web.catalog.CatalogAccess;
import pl.commercelink.web.catalog.CatalogRow;
import pl.commercelink.web.dtos.CatalogSettingsForm;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
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

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        CustomUser user = new CustomUser(null, null, Map.of("storeId", STORE_ID, "role", "ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        lenient().when(messageSource.getMessage(eq("catalog.schedule.default"), any(), any(Locale.class))).thenReturn("default");
        mvc = MockMvcBuilders.standaloneSetup(
                new CatalogsController(catalogRepository, messageSource, detailsService, access, productRepository)).build();
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
                .andExpect(flash().attribute("errorMessage", "protected"));
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
}
