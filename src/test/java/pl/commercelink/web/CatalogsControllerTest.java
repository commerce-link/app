package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.web.catalog.CatalogRow;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class CatalogsControllerTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private ProductCatalogRepository catalogRepository;
    @Mock
    private MessageSource messageSource;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        CustomUser user = new CustomUser(null, null, Map.of("storeId", STORE_ID, "role", "ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(messageSource.getMessage(eq("catalog.schedule.default"), any(), any(Locale.class))).thenReturn("default");
        mvc = MockMvcBuilders.standaloneSetup(new CatalogsController(catalogRepository, messageSource)).build();
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
}
