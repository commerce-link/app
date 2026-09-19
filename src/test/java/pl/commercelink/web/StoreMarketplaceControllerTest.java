package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.inventory.supplier.ErrorMessage;
import pl.commercelink.marketplace.MarketplaceConnectionService;
import pl.commercelink.marketplace.MarketplaceConnectionService.ConnectionUpdateResult;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.MarketplaceSettingsForm;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.SettingsFlash;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.commercelink.web.MarketplacesFixture.PL;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreMarketplaceControllerTest {

    private static final ConnectionUpdateResult OK = new ConnectionUpdateResult(List.of());
    private static final String EVERY_30_MINUTES = "0/30 * * * ? *";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private MarketplaceProviderFactory providerFactory;
    @Mock
    private MarketplaceConnectionService connectionService;
    @Mock
    private MarketplaceAuthorization authorization;
    @Mock
    private ProductCatalogRepository catalogRepository;

    private StoreMarketplaceController controller;

    @BeforeEach
    void loggedInAsStoreAdmin() {
        var messages = MarketplacesFixture.polishMessages();
        controller = new StoreMarketplaceController(storesRepository,
                MarketplacesFixture.install(providerFactory, connectionService, authorization, catalogRepository, messages),
                messages);
        authenticateAs("store-1", "ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theNewPageOffersOnlyMarketplacesTheStoreIsNotConnectedTo() {
        // given
        store("store-1").getMarketplaces().add(new MarketplaceIntegration("Allegro"));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.newMarketplace(model, PL);

        // then
        assertThat(view).isEqualTo("store-marketplace");
        assertThat(((List<?>) model.getAttribute("providers"))).hasSize(1);
        // a single choice is preselected so its fields show at once
        assertThat(((MarketplaceSettingsForm) model.getAttribute("form")).getProviderName()).isEqualTo("CsCart");
        assertThat(model.getAttribute("formAction")).isEqualTo("/dashboard/store/marketplaces/new");
    }

    @Test
    void addingAMarketplaceConnectedWithKeysSavesSettingsAndScheduleAndReturnsToTheList() {
        // given
        Store store = store("store-1");
        when(connectionService.connectOrUpdate(any(), anyString(), anyMap(), any(), any())).thenReturn(OK);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.addMarketplace(form("CsCart", Map.of("CsCart.apiUrl", "https://sklep.pl", "CsCart.apiKey", "k"),
                EVERY_30_MINUTES, "garbage"), null, new ExtendedModelMap(), PL, redirect, new MockHttpServletRequest(),
                new MockHttpServletResponse());

        // then
        // CS-Cart imports no returns, so its returns schedule is neither validated nor saved
        verify(connectionService).connectOrUpdate(eq(store), eq("CsCart"),
                eq(Map.of("apiUrl", "https://sklep.pl", "apiKey", "k")), eq(EVERY_30_MINUTES), isNull());
        assertThat(view).isEqualTo("redirect:/dashboard/store/marketplaces");
        assertThat(redirect.getFlashAttributes().get(SettingsFlash.SAVED_MESSAGE)).isEqualTo("Dodano CS-Cart Multi-Vendor.");
    }

    /** Allegro is not usable until the seller account is connected on the Allegro page, so the save leads there. */
    @Test
    void addingAMarketplaceConnectedOnItsOwnPageGoesOnToConnectTheAccount() {
        // given
        Store store = store("store-1");
        when(connectionService.connectOrUpdate(any(), eq("Allegro"), anyMap(), any(), any())).thenAnswer(call -> {
            store.connectMarketplace("Allegro", true);
            return OK;
        });
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.addMarketplace(form("Allegro", Map.of("Allegro.clientId", "id", "Allegro.clientSecret", "s"),
                "", ""), null, new ExtendedModelMap(), PL, redirect, new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/marketplaces/Allegro/authorize");
        assertThat(redirect.getFlashAttributes().get(SettingsFlash.SAVED_MESSAGE))
                .isEqualTo("Zapisano dane dostępu Allegro. Teraz połącz konto.");
    }

    @Test
    void aMissingRequiredFieldIsReportedAtTheFieldWithoutSaving() {
        // given
        store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.addMarketplace(form("CsCart", Map.of("CsCart.apiUrl", "https://sklep.pl"), "", ""), "fetch",
                model, PL, new RedirectAttributesModelMap(), new MockHttpServletRequest(), response);

        // then
        verify(connectionService, never()).connectOrUpdate(any(), anyString(), anyMap(), any(), any());
        assertThat(view).isEqualTo("store-marketplace :: marketplaceForm");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(model.getAttribute("errors")).isEqualTo(Map.of("setting-CsCart-apiKey", "integration.setting.required"));
        // the adapter's asterisk does not reach the error summary
        @SuppressWarnings("unchecked")
        Map<String, String> labels = (Map<String, String>) model.getAttribute("errorLabels");
        assertThat(labels).containsEntry("setting-CsCart-apiKey", "API Key");
    }

    @Test
    void aScheduleMoreFrequentThanAllowedIsReportedAtItsField() {
        // given
        store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.addMarketplace(form("Allegro", Map.of("Allegro.clientId", "id", "Allegro.clientSecret", "s"),
                "", "0/5 * * * ? *"), null, model, PL, new RedirectAttributesModelMap(), new MockHttpServletRequest(),
                new MockHttpServletResponse());

        // then
        verify(connectionService, never()).connectOrUpdate(any(), anyString(), anyMap(), any(), any());
        assertThat(model.getAttribute("errors"))
                .isEqualTo(Map.of("schedule-Allegro-returns", "store.marketplace.schedule.tooFrequent"));
    }

    @Test
    void editingKeepsTheMarketplaceOfTheAddressAndShowsItsSchedules() {
        // given
        Store store = store("store-1");
        MarketplaceIntegration integration = new MarketplaceIntegration("Allegro");
        integration.setOrdersImportSchedule(EVERY_30_MINUTES);
        store.getMarketplaces().add(integration);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.editMarketplace("Allegro", model, PL);

        // then
        assertThat(view).isEqualTo("store-marketplace");
        MarketplaceSettingsForm form = (MarketplaceSettingsForm) model.getAttribute("form");
        assertThat(form.schedule("Allegro", MarketplaceSettingsForm.ORDERS)).isEqualTo(EVERY_30_MINUTES);
        assertThat(model.getAttribute("editing")).isEqualTo(true);
        assertThat(model.getAttribute("pageTitle")).isEqualTo("Allegro");
    }

    @Test
    void savingAnEditIgnoresAnotherMarketplaceNamedInTheForm() {
        // given
        Store store = store("store-1");
        store.getMarketplaces().add(new MarketplaceIntegration("CsCart"));
        when(connectionService.connectOrUpdate(any(), anyString(), anyMap(), any(), any())).thenReturn(OK);

        // when
        controller.updateMarketplace("CsCart", form("Allegro", Map.of("CsCart.apiUrl", "https://sklep.pl",
                        "CsCart.apiKey", "k"), "", ""), null, new ExtendedModelMap(), PL, new RedirectAttributesModelMap(),
                new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        verify(connectionService).connectOrUpdate(eq(store), eq("CsCart"), anyMap(), isNull(), isNull());
    }

    @Test
    void aMarketplaceTheStoreIsNotConnectedToAnswers404() {
        // given
        store("store-1");

        // when / then
        assertThatThrownBy(() -> controller.editMarketplace("Allegro", new ExtendedModelMap(), PL))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.confirmDisconnect("Allegro", new ExtendedModelMap(), PL))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.updateMarketplace("Allegro", form("Allegro", Map.of(), "", ""), null,
                new ExtendedModelMap(), PL, new RedirectAttributesModelMap(), new MockHttpServletRequest(),
                new MockHttpServletResponse())).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void aSaveTheServiceRolledBackIsShownAsAFailureNotAsSaved() {
        // given
        store("store-1");
        when(connectionService.connectOrUpdate(any(), anyString(), anyMap(), any(), any()))
                .thenReturn(new ConnectionUpdateResult(List.of(ErrorMessage.of("store.marketplaces.error.update.failed"))));
        ExtendedModelMap model = new ExtendedModelMap();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.addMarketplace(form("CsCart", Map.of("CsCart.apiUrl", "https://sklep.pl", "CsCart.apiKey", "k"),
                "", ""), null, model, PL, redirect, new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("store-marketplace");
        assertThat((String) model.getAttribute("failure")).startsWith("Nie udało się zapisać ustawień marketplace");
        assertThat(redirect.getFlashAttributes()).isEmpty();
    }

    /** The old confirmation did not say that listed offers stay on the marketplace, no longer updated. */
    @Test
    void theDisconnectConfirmationSaysWhatHappensToOrdersReturnsAndListedOffers() {
        // given
        store("store-1").getMarketplaces().add(new MarketplaceIntegration("Allegro"));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.confirmDisconnect("Allegro", model, PL);

        // then
        assertThat(view).isEqualTo("settings-confirm");
        ConfirmAction confirm = (ConfirmAction) model.getAttribute("confirm");
        assertThat(confirm.title()).isEqualTo("Odłączyć Allegro?");
        assertThat(confirm.message()).contains("zamówienia i zwroty z Allegro")
                .contains("Wystawione oferty zostaną na Allegro z ostatnią ceną i stanem");
        assertThat(confirm.actionPath()).isEqualTo("/dashboard/store/marketplaces/Allegro/disconnect");
    }

    @Test
    void disconnectingGoesThroughTheServiceAndSaysSo() {
        // given
        Store store = store("store-1");
        store.getMarketplaces().add(new MarketplaceIntegration("CsCart"));
        when(connectionService.disconnect(store, "CsCart")).thenReturn(OK);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.disconnect("CsCart", PL, redirect);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/marketplaces");
        assertThat(redirect.getFlashAttributes().get(SettingsFlash.SAVED_MESSAGE)).isEqualTo("Odłączono CS-Cart Multi-Vendor.");
    }

    @Test
    void aFailedDisconnectIsNotReportedAsDone() {
        // given
        Store store = store("store-1");
        store.getMarketplaces().add(new MarketplaceIntegration("CsCart"));
        when(connectionService.disconnect(store, "CsCart"))
                .thenReturn(new ConnectionUpdateResult(List.of(ErrorMessage.of("store.marketplaces.error.update.failed"))));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        controller.disconnect("CsCart", PL, redirect);

        // then
        assertThat(redirect.getFlashAttributes()).doesNotContainKey(SettingsFlash.SAVED_MESSAGE).containsKey("errorMessage");
    }

    /** A super admin works on the store named in the address, and the form posts back to that address. */
    @Test
    void aSuperAdminWorksOnTheStoreOfTheAddress() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        Store store = store("store-9");
        when(connectionService.connectOrUpdate(any(), anyString(), anyMap(), any(), any())).thenReturn(OK);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.superAdminNewMarketplace("store-9", model, PL);
        String view = controller.superAdminAddMarketplace("store-9", form("CsCart",
                        Map.of("CsCart.apiUrl", "https://sklep.pl", "CsCart.apiKey", "k"), "", ""), null,
                new ExtendedModelMap(), PL, new RedirectAttributesModelMap(), new MockHttpServletRequest(),
                new MockHttpServletResponse());

        // then
        assertThat(model.getAttribute("formAction")).isEqualTo("/dashboard/store/store-9/marketplaces/new");
        verify(connectionService).connectOrUpdate(eq(store), eq("CsCart"), anyMap(), isNull(), isNull());
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-9/marketplaces");
    }

    private Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
    }

    private static MarketplaceSettingsForm form(String name, Map<String, String> settings, String orders, String returns) {
        MarketplaceSettingsForm form = new MarketplaceSettingsForm();
        form.setProviderName(name);
        form.setSettings(new HashMap<>(settings));
        form.getSchedules().put(name + ".orders", orders);
        form.getSchedules().put(name + ".returns", returns);
        return form;
    }

    private void authenticateAs(String storeId, String role) {
        Map<String, String> attributes = storeId != null
                ? Map.of("storeId", storeId, "role", role)
                : Map.of("role", role);
        CustomUser user = new CustomUser(null, null, attributes);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
