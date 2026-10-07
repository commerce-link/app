package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.context.MessageSource;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.warehousedocuments.TestMessages;
import pl.commercelink.documents.DocumentType;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private WarehouseDocumentRepository warehouseDocumentRepository;
    @Mock
    private WarehouseDocumentItemRepository warehouseDocumentItemRepository;
    @Spy
    private MessageSource messageSource = TestMessages.polish();
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

    @Test
    void foreignDocumentRedirectsWithNotice() throws Exception {
        // given
        securityContext.when(CustomSecurityContext::getStoreId).thenReturn("s1");
        Store store = storeWithDocuments();
        when(storesRepository.findById("s1")).thenReturn(store);
        when(warehouseDocumentRepository.findByDocumentId("s1", "doc-of-s2")).thenReturn(null);

        // when / then
        mockMvc.perform(get("/dashboard/warehouse-documents/details").param("documentId", "doc-of-s2")
                        .locale(java.util.Locale.forLanguageTag("pl")))
                .andExpect(redirectedUrl("/dashboard/warehouse-documents"))
                .andExpect(flash().attribute("documentsNotice", "Nie znaleziono dokumentu."));
    }

    @Test
    void detailsRenderTheDocumentPage() throws Exception {
        // given
        securityContext.when(CustomSecurityContext::getStoreId).thenReturn("s1");
        Store store = storeWithDocuments();
        when(storesRepository.findById("s1")).thenReturn(store);
        when(warehouseDocumentRepository.findByDocumentId("s1", "doc-1")).thenReturn(receipt());
        when(warehouseDocumentItemRepository.findByDocumentId("doc-1")).thenReturn(List.of());

        // when / then
        mockMvc.perform(get("/dashboard/warehouse-documents/details").param("documentId", "doc-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("warehouse-document-details"))
                .andExpect(model().attributeExists("page"));
    }

    @Test
    void superAdminDetailsKeepWorkingOnTheStorePath() throws Exception {
        // given
        securityContext.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);
        Store store = storeWithDocuments();
        when(storesRepository.findById("s9")).thenReturn(store);
        when(warehouseDocumentRepository.findByDocumentId("s9", "doc-1")).thenReturn(receipt());
        when(warehouseDocumentItemRepository.findByDocumentId("doc-1")).thenReturn(List.of());

        // when / then
        mockMvc.perform(get("/dashboard/store/s9/warehouse-documents/details").param("documentId", "doc-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("warehouse-document-details"))
                .andExpect(model().attributeExists("page"));
    }

    private static Store storeWithDocuments() {
        Store store = org.mockito.Mockito.mock(Store.class);
        when(store.hasDocumentsGenerationEnabled()).thenReturn(true);
        return store;
    }

    private static WarehouseDocument receipt() {
        WarehouseDocument d = new WarehouseDocument();
        d.setDocumentId("doc-1");
        d.setStoreId("s1");
        d.setDocumentNo("PZ/MAG1/2026/000214");
        d.setType(DocumentType.GoodsReceipt);
        return d;
    }

    private static WarehouseDocumentListPage enabledPage() {
        WarehouseDocumentListQuery q = new WarehouseDocumentListQuery("/dashboard/warehouse-documents", null, List.of(), null, null, null, 1);
        return new WarehouseDocumentListPage(q, false, true, "/dashboard/warehouse-documents/list", List.of(), List.of(), null,
                null, List.of(), null, List.of(), null, null, "/dashboard/store/warehouse");
    }
}
