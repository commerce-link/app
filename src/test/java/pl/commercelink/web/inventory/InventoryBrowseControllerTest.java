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
import org.springframework.ui.ConcurrentModel;
import org.springframework.util.LinkedMultiValueMap;
import pl.commercelink.starter.security.CustomSecurityContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryBrowseControllerTest {

    private static final String STORE_ID = "store-1";

    @Mock private BrowsePageFactory pageFactory;
    @InjectMocks private InventoryBrowseController controller;
    private MockedStatic<CustomSecurityContext> security;

    @BeforeEach
    void setUp() {
        security = mockStatic(CustomSecurityContext.class);
        signedInAs("ADMIN");
        when(pageFactory.build(any(), any(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(BrowsePage.of(BrowsePage.Status.READY, BrowseQuery.start(), true));
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
    void bareInventoryPathRendersTheSupplierAssortment() {
        // given
        ConcurrentModel model = new ConcurrentModel();
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("cat", "11");

        // when
        String view = controller.page(params, model);

        // then
        assertThat(view).isEqualTo("inventory");
        assertThat(model.getAttribute("browse")).isNotNull();
    }

    @Test
    void oldCodeSearchBookmarkRedirectsToThePriceComparison() {
        // given
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("q", "A+B {x}");

        // when
        String view = controller.page(params, new ConcurrentModel());

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/inventory/prices?q=A%2BB+%7Bx%7D");
        verifyNoInteractions(pageFactory);
    }

    @Test
    void emptyOrBlankCodeSearchOpensTheBrowseStart() {
        // given
        LinkedMultiValueMap<String, String> empty = new LinkedMultiValueMap<>();
        empty.add("q", "");
        LinkedMultiValueMap<String, String> blank = new LinkedMultiValueMap<>();
        blank.add("q", "   ");

        // when
        String emptyView = controller.page(empty, new ConcurrentModel());
        String blankView = controller.page(blank, new ConcurrentModel());

        // then
        assertThat(emptyView).isEqualTo("inventory");
        assertThat(blankView).isEqualTo("inventory");
        verify(pageFactory, times(2)).build(eq(STORE_ID), eq(BrowseQuery.start()), eq(true), eq(false), eq(false));
    }

    @Test
    void returnFromTheCatalogsReviewReadsTheCatalogStatusAfresh() {
        // given
        ConcurrentModel model = new ConcurrentModel();
        model.addAttribute(InventoryBrowseController.NOTICE_FLASH, "Dodano 2 produkty");

        // when
        controller.page(new LinkedMultiValueMap<>(), model);
        controller.page(new LinkedMultiValueMap<>(), new ConcurrentModel());
        controller.results(new LinkedMultiValueMap<>(), new ConcurrentModel());

        // then
        verify(pageFactory).build(eq(STORE_ID), any(), eq(true), eq(false), eq(true));
        verify(pageFactory, times(2)).build(eq(STORE_ID), any(), eq(true), eq(false), eq(false));
    }

    @Test
    void oldCodeSearchBookmarkKeepsItsWayBackToBrowsing() {
        // given
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("q", "MX-1");
        params.add("from", "/dashboard/inventory?cat=11&supplier=AB");

        // when
        String view = controller.page(params, new ConcurrentModel());

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/inventory/prices?q=MX-1&from=%2Fdashboard%2Finventory%3Fcat%3D11%26supplier%3DAB");
    }

    /**
     * A super admin account can carry a store id (the local seed's does); the browse is still the global one, as the code
     * search is, and never that store's own feeds or its choice of suppliers.
     */
    @Test
    void superAdminBrowsesTheGlobalInventoryEvenWithAStoreInTheSession() {
        // given
        security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
        security.when(() -> CustomSecurityContext.hasRole(anyString())).thenAnswer(call -> "SUPER_ADMIN".equals(call.getArgument(0)));
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();

        // when
        controller.page(params, new ConcurrentModel());
        controller.results(params, new ConcurrentModel());

        // then
        verify(pageFactory, times(2)).build(isNull(), any(), eq(false), eq(true), eq(false));
    }

    @Test
    void fragmentReturnsTheResultsBlock() {
        // when
        String view = controller.results(new LinkedMultiValueMap<>(), new ConcurrentModel());

        // then
        assertThat(view).isEqualTo("fragments/inventory-browse :: results");
    }
}
