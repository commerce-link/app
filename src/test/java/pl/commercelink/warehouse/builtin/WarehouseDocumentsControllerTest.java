package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentListPage;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentListQuery;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Standalone MockMvc: roles ({@code @PreAuthorize}) are not evaluated here, the annotations stay as they were. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WarehouseDocumentsControllerTest {

    @Mock
    private WarehouseDocumentListService listService;
    @InjectMocks
    private WarehouseDocumentsController controller;

    private MockMvc mockMvc;
    private MockedStatic<CustomSecurityContext> securityContext;

    @BeforeEach
    void setup() {
        securityContext = mockStatic(CustomSecurityContext.class);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setViewResolvers(new InternalResourceViewResolver("/templates/", ".html")).build();
    }

    @AfterEach
    void tearDown() {
        securityContext.close();
    }

    @Test
    void oldBookmarkRedirectsToTheNewAddress() throws Exception {
        // given
        securityContext.when(CustomSecurityContext::getStoreId).thenReturn("s1");

        // when / then
        mockMvc.perform(get("/dashboard/warehouse-documents").param("type", "GoodsReceipt").param("ean", "590"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard/warehouse-documents?type=PZ&q=590"));
    }

    @Test
    void listFragmentRendersOnlyTheResults() throws Exception {
        // given
        securityContext.when(CustomSecurityContext::getStoreId).thenReturn("s1");
        when(listService.page(eq("s1"), eq(false), any(), any())).thenReturn(enabledPage());

        // when / then
        mockMvc.perform(get("/dashboard/warehouse-documents/list").param("type", "PZ"))
                .andExpect(status().isOk())
                .andExpect(view().name("warehouse-documents :: results"))
                .andExpect(model().attributeExists("page"));
    }

    @Test
    void superAdminListUsesTheStorePath() throws Exception {
        // given
        securityContext.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);
        ArgumentCaptor<WarehouseDocumentListQuery> query = ArgumentCaptor.forClass(WarehouseDocumentListQuery.class);
        when(listService.page(eq("s9"), eq(true), query.capture(), any())).thenReturn(enabledPage());

        // when
        mockMvc.perform(get("/dashboard/store/s9/warehouse-documents")).andExpect(status().isOk())
                .andExpect(view().name("warehouse-documents"));

        // then
        assertThat(query.getValue().path()).isEqualTo("/dashboard/store/s9/warehouse-documents");
    }

    @Test
    void superAdminFragmentUsesTheStorePathToo() throws Exception {
        // given
        securityContext.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);
        ArgumentCaptor<WarehouseDocumentListQuery> query = ArgumentCaptor.forClass(WarehouseDocumentListQuery.class);
        when(listService.page(eq("s9"), eq(true), query.capture(), any())).thenReturn(enabledPage());

        // when
        mockMvc.perform(get("/dashboard/store/s9/warehouse-documents/list")).andExpect(status().isOk())
                .andExpect(view().name("warehouse-documents :: results"));

        // then
        assertThat(query.getValue().path()).isEqualTo("/dashboard/store/s9/warehouse-documents");
    }

    private static WarehouseDocumentListPage enabledPage() {
        WarehouseDocumentListQuery q = new WarehouseDocumentListQuery("/dashboard/warehouse-documents", null, List.of(), null, null, null, 1);
        return new WarehouseDocumentListPage(q, false, true, "/dashboard/warehouse-documents/list", List.of(), List.of(), null,
                null, List.of(), null, List.of(), null, null, "/dashboard/store/warehouse");
    }
}
