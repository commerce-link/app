package pl.commercelink.web.inventory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ConcurrentModel;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryStatistics;
import pl.commercelink.inventory.search.InventorySearch;
import pl.commercelink.inventory.search.InventorySearchResult;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.api.StockSummary;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryPageControllerTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private Inventory inventory;
    @Mock
    private InventorySearch inventorySearch;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private InventorySourcesViewFactory sourcesViewFactory;
    @Mock
    private WarehouseSummaryService warehouseSummaryService;
    @Mock
    private TechnicalInventoryViewFactory technicalViewFactory;

    @InjectMocks
    private InventoryPageController controller;

    private MockedStatic<CustomSecurityContext> security;
    private Store store;

    @BeforeEach
    void setUp() {
        security = mockStatic(CustomSecurityContext.class);
        signedInAs("ADMIN");
        store = mock(Store.class);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    private void signedInAs(String role) {
        security.when(CustomSecurityContext::getStoreId).thenReturn("SUPER_ADMIN".equals(role) ? null : STORE_ID);
        security.when(() -> CustomSecurityContext.hasRole(anyString())).thenAnswer(call -> role.equals(call.getArgument(0)));
    }

    @Test
    void pageWithoutQueryRendersTheShellWithoutSearching() {
        // given
        ConcurrentModel model = new ConcurrentModel();

        // when
        String view = controller.page(null, model);

        // then
        assertThat(view).isEqualTo("inventory");
        assertThat(model.getAttribute("query")).isEqualTo("");
        assertThat(model.getAttribute("result")).isNull();
        assertThat(model.getAttribute("canManageSuppliers")).isEqualTo(true);
        assertThat(model.getAttribute("manageSuppliersUrl")).isEqualTo("/dashboard/store/suppliers");
        verifyNoInteractions(inventorySearch);
    }

    @Test
    void pageWithQueryRendersTheResultInline() {
        // given
        ConcurrentModel model = new ConcurrentModel();
        InventorySearchResult notFound = new InventorySearchResult.NotFound("5901234123457");
        when(inventorySearch.search(STORE_ID, "5901234123457")).thenReturn(notFound);

        // when
        controller.page("  5901234123457 ", model);

        // then
        assertThat(model.getAttribute("query")).isEqualTo("5901234123457");
        assertThat(model.getAttribute("result")).isEqualTo(notFound);
    }

    @Test
    void tooShortQueryIsRejectedWithoutSearching() {
        // given
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.search("9 ", model, response);

        // then
        assertThat(view).isEqualTo("fragments/inventory-results :: results");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.getAttribute("validationError")).isEqualTo(true);
        verifyNoInteractions(inventorySearch);
    }

    @Test
    void superAdminSearchesAllGlobalSuppliers() {
        // given
        signedInAs("SUPER_ADMIN");
        ConcurrentModel model = new ConcurrentModel();
        when(inventorySearch.searchGlobal("MFN-1")).thenReturn(new InventorySearchResult.NotFound("MFN-1"));

        // when
        controller.search("MFN-1", model, new MockHttpServletResponse());

        // then
        verify(inventorySearch).searchGlobal("MFN-1");
        verify(inventorySearch, never()).search(any(), any());
        assertThat(model.getAttribute("canManageSuppliers")).isEqualTo(false);
    }

    @Test
    void userRoleCannotManageSuppliers() {
        // given
        signedInAs("USER");
        ConcurrentModel model = new ConcurrentModel();

        // when
        controller.page(null, model);

        // then
        assertThat(model.getAttribute("canManageSuppliers")).isEqualTo(false);
    }

    @Test
    void summaryCombinesStatisticsWithTheSourcesBar() {
        // given
        ConcurrentModel model = new ConcurrentModel();
        InventoryStatistics statistics = new InventoryStatistics(10, 4, java.util.Map.of());
        when(inventory.storeStatistics(STORE_ID)).thenReturn(statistics);
        when(sourcesViewFactory.build(any(), any(), any())).thenReturn(InventorySourcesView.EMPTY);

        // when
        String view = controller.summary(model);

        // then
        assertThat(view).isEqualTo("fragments/inventory-summary :: summary");
        assertThat(model.getAttribute("statistics")).isEqualTo(statistics);
        assertThat(model.getAttribute("sources")).isEqualTo(InventorySourcesView.EMPTY);
    }

    @Test
    void summaryForSuperAdminIsTheTechnicalPanel() {
        // given
        signedInAs("SUPER_ADMIN");
        ConcurrentModel model = new ConcurrentModel();
        TechnicalInventoryView technical = new TechnicalInventoryView(1, 2, "t.csv", 3, List.of());
        when(technicalViewFactory.build(any(LocalDateTime.class))).thenReturn(technical);

        // when
        String view = controller.summary(model);

        // then
        assertThat(view).isEqualTo("fragments/inventory-technical :: technical");
        assertThat(model.getAttribute("technical")).isEqualTo(technical);
        verifyNoInteractions(inventory);
    }

    @Test
    void warehouseTileUsesTheCachedSummary() {
        // given
        ConcurrentModel model = new ConcurrentModel();
        when(warehouseSummaryService.summaryFor(STORE_ID)).thenReturn(new StockSummary(184, 612, 2));

        // when
        String view = controller.warehouse(model);

        // then
        assertThat(view).isEqualTo("fragments/inventory-summary :: warehouseTile");
        assertThat(model.getAttribute("warehouseSummary")).isEqualTo(new StockSummary(184, 612, 2));
        assertThat(model.getAttribute("externalWarehouse")).isEqualTo(false);
    }

    @Test
    void externalWarehouseIsNotCounted() {
        // given
        when(store.hasIntegration(IntegrationType.WMS_PROVIDER)).thenReturn(true);
        ConcurrentModel model = new ConcurrentModel();

        // when
        controller.warehouse(model);

        // then
        assertThat(model.getAttribute("externalWarehouse")).isEqualTo(true);
        verifyNoInteractions(warehouseSummaryService);
    }

    @Test
    void legacyCheckPriceMfnLinksRedirectToTheNewQuery() {
        // when / then
        // URLEncoder encodes a space as '+'; the servlet container decodes '+' back to a space in a query string
        assertThat(controller.legacyCheckPrice("MFN 1", "5901234123457", null))
                .isEqualTo("redirect:/dashboard/inventory?q=MFN+1");
    }

    @Test
    void legacyCheckPriceEncodesAPlusSoItIsNotDecodedAsASpace() {
        // when / then
        assertThat(controller.legacyCheckPrice("A+B", "5901234123457", null))
                .isEqualTo("redirect:/dashboard/inventory?q=A%2BB");
    }

    @Test
    void legacyCheckPriceEncodesCurlyBracesSoTheyAreNotTreatedAsAUriTemplateVariable() {
        // when / then
        assertThat(controller.legacyCheckPrice("{x}", "5901234123457", null))
                .isEqualTo("redirect:/dashboard/inventory?q=%7Bx%7D");
    }

    @Test
    void legacyCheckPricePrefersPimIdOverMfn() {
        // when / then
        assertThat(controller.legacyCheckPrice("MFN-1", "5901234123457", "PIM-7"))
                .isEqualTo("redirect:/dashboard/inventory?q=PIM-7");
    }

    @Test
    void legacyCheckPriceFallsBackToEanWhenMfnIsBlank() {
        // when / then
        assertThat(controller.legacyCheckPrice(" ", "5901234123457", ""))
                .isEqualTo("redirect:/dashboard/inventory?q=5901234123457");
    }

    @Test
    void legacyCheckPriceWithNoParametersRedirectsToThePlainPage() {
        // when / then
        assertThat(controller.legacyCheckPrice(null, null, null))
                .isEqualTo("redirect:/dashboard/inventory");
    }
}
