package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ConcurrentModel;
import pl.commercelink.inventory.supplier.ErrorMessage;
import pl.commercelink.marketplace.MarketplaceConnectionService;
import pl.commercelink.marketplace.MarketplaceIntegrationView;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.MarketplaceConnectionForm;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreMarketplaceControllerTest {

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private MarketplaceConnectionService marketplaceConnectionService;
    @Mock
    private MessageSource messageSource;

    private MockedStatic<CustomSecurityContext> security;
    private StoreMarketplaceController controller;
    private Store store;

    @BeforeEach
    void setUp() {
        security = mockStatic(CustomSecurityContext.class);
        security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
        security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);
        store = new Store();
        store.setStoreId("store-1");
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(marketplaceConnectionService.views(store)).thenReturn(List.of(
                new MarketplaceIntegrationView("Empik", "EmpikPlace", true, false, null, "0/15 * * * ? *", null)));
        when(marketplaceConnectionService.availableMarketplaces(store)).thenReturn(List.of());
        when(marketplaceConnectionService.marketplacesWithStoredConfiguration(store)).thenReturn(Set.of("Empik"));
        when(marketplaceConnectionService.defaultIntervalMinutes()).thenReturn(10);
        when(marketplaceConnectionService.returnsDefaultIntervalMinutes()).thenReturn(60);
        controller = new StoreMarketplaceController(storesRepository, marketplaceConnectionService, messageSource);
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    @Test
    void aSuccessfulSaveReturnsTheRefreshedSectionWithItsSuccessMessage() {
        // given
        MarketplaceConnectionForm form = form("Empik", "0/15 * * * ? *");
        when(marketplaceConnectionService.connectOrUpdate(store, "Empik", form.getConfiguration(), "0/15 * * * ? *", "0 8 * * ? *"))
                .thenReturn(new MarketplaceConnectionService.ConnectionUpdateResult(List.of()));
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.save(form, Locale.ENGLISH, model, response);

        // then
        assertThat(view).isEqualTo("fragments/marketplace-section :: marketplaceSection");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(model.get("sectionSuccessMessage")).isEqualTo("store.marketplaces.saved");
        assertThat(model.get("sectionAddDisabled")).isEqualTo(true);
        assertThat(model.get("sectionMarketplacesWithStoredConfig")).isEqualTo("Empik");
        assertThat(model.get("sectionBasePath")).isEqualTo("/dashboard/store");
        assertThat(model.get("sectionDefaultIntervalMinutes")).isEqualTo(10);
        assertThat(model.get("sectionReturnsDefaultIntervalMinutes")).isEqualTo(60);
        verify(messageSource).getMessage(eq("store.marketplaces.saved"), eq(new Object[]{"Empik"}), any(Locale.class));
    }

    @Test
    void aRejectedSaveAnswersWithTheErrorFragmentAndStatus400() {
        // given
        MarketplaceConnectionForm form = form("Empik", "0/2 * * * ? *");
        when(marketplaceConnectionService.connectOrUpdate(any(), any(), any(), any(), any()))
                .thenReturn(new MarketplaceConnectionService.ConnectionUpdateResult(List.of(
                        ErrorMessage.of("store.marketplaces.import.schedule.error.too.frequent", "Empik", "0/2 * * * ? *", 5))));
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.save(form, Locale.ENGLISH, model, response);

        // then
        assertThat(view).isEqualTo("fragments/marketplace-section :: sectionError");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(model.get("errorMessage")).isEqualTo("store.marketplaces.import.schedule.error.too.frequent");
    }

    @Test
    void anAdminSavesIntoTheirOwnStoreAndASuperAdminIntoTheAddressedOne() {
        // given
        Store other = new Store();
        other.setStoreId("store-2");
        when(storesRepository.findById("store-2")).thenReturn(other);
        when(marketplaceConnectionService.connectOrUpdate(any(), any(), any(), any(), any()))
                .thenReturn(new MarketplaceConnectionService.ConnectionUpdateResult(List.of()));
        security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

        // when
        controller.saveForStore("store-2", form("Empik", ""), Locale.ENGLISH, new ConcurrentModel(), new MockHttpServletResponse());

        // then
        verify(marketplaceConnectionService).connectOrUpdate(eq(other), eq("Empik"), any(), eq(""), eq("0 8 * * ? *"));
        verify(marketplaceConnectionService, never()).connectOrUpdate(eq(store), any(), any(), any(), any());
    }

    @Test
    void disconnectReturnsTheRefreshedSection() {
        // given
        when(marketplaceConnectionService.disconnect(store, "Empik"))
                .thenReturn(new MarketplaceConnectionService.ConnectionUpdateResult(List.of()));
        ConcurrentModel model = new ConcurrentModel();

        // when
        String view = controller.disconnect("Empik", Locale.ENGLISH, model, new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("fragments/marketplace-section :: marketplaceSection");
        assertThat(model.get("sectionSuccessMessage")).isEqualTo("store.marketplaces.disconnect.success");
    }

    @Test
    void anUnknownStoreAnswersWithTheErrorFragment() {
        // given
        when(storesRepository.findById("store-1")).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.disconnect("Empik", Locale.ENGLISH, new ConcurrentModel(), response);

        // then
        assertThat(view).isEqualTo("fragments/marketplace-section :: sectionError");
        assertThat(response.getStatus()).isEqualTo(400);
        verify(marketplaceConnectionService, never()).disconnect(any(), any());
    }

    private static MarketplaceConnectionForm form(String marketplace, String schedule) {
        MarketplaceConnectionForm form = new MarketplaceConnectionForm();
        form.setMarketplace(marketplace);
        form.setConfiguration(Map.of("apiKey", "secret"));
        form.setSchedule(schedule);
        form.setReturnsSchedule("0 8 * * ? *");
        return form;
    }
}
