package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;
import pl.commercelink.marketplace.MarketplaceConnectionService;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.rest.client.OAuth2DeviceTokenResult;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.MarketplaceAuthorization.AuthorizationException;
import pl.commercelink.web.MarketplaceAuthorization.Pending;
import pl.commercelink.web.settings.SettingsFlash;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.commercelink.web.MarketplacesFixture.PL;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreMarketplaceAuthorizationControllerTest {

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

    private StoreMarketplaceAuthorizationController controller;
    private MockHttpSession session;

    @BeforeEach
    void loggedInAsStoreAdmin() {
        var messages = MarketplacesFixture.polishMessages();
        controller = new StoreMarketplaceAuthorizationController(storesRepository,
                MarketplacesFixture.install(providerFactory, connectionService, authorization, catalogRepository, messages),
                authorization, messages);
        session = new MockHttpSession();
        authenticateAs("store-1", "ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theStartPageSaysWhatConnectingDoes() {
        // given
        store("store-1", false);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.page("Allegro", session, model, PL);

        // then
        assertThat(view).isEqualTo("store-marketplace-authorize");
        assertThat(model.getAttribute("pending")).isNull();
        assertThat(model.getAttribute("connected")).isEqualTo(false);
        assertThat(model.getAttribute("pageTitle")).isEqualTo("Połącz konto Allegro");
        assertThat(model.getAttribute("startAction")).isEqualTo("/dashboard/store/marketplaces/Allegro/authorize");
    }

    /** CS-Cart is connected with keys only: it has no account to connect on its own page. */
    @Test
    void aMarketplaceWithoutAnAccountToConnectOrNotConnectedToTheStoreAnswers404() {
        // given
        Store store = store("store-1", false);
        store.getMarketplaces().add(new MarketplaceIntegration("CsCart"));

        // when / then
        assertThatThrownBy(() -> controller.page("CsCart", session, new ExtendedModelMap(), PL))
                .isInstanceOf(ResponseStatusException.class);
        store.getMarketplaces().clear();
        assertThatThrownBy(() -> controller.start("Allegro", session, PL, new RedirectAttributesModelMap()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void startingKeepsTheCodeInTheSessionAndShowsIt() {
        // given
        Store store = store("store-1", false);
        Pending pending = pending("store-1", Instant.now().plusSeconds(600));
        when(authorization.start(store, "Allegro")).thenReturn(pending);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.start("Allegro", session, PL, new RedirectAttributesModelMap());
        controller.page("Allegro", session, model, PL);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/marketplaces/Allegro/authorize");
        assertThat(model.getAttribute("pending")).isEqualTo(pending);
        assertThat(model.getAttribute("expiresAtText")).isNotNull();
    }

    @Test
    void aStartTheMarketplaceRefusesIsExplainedInPolish() {
        // given
        Store store = store("store-1", false);
        when(authorization.start(store, "Allegro"))
                .thenThrow(new AuthorizationException("store.marketplace.authorize.error.rejected"));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        controller.start("Allegro", session, PL, redirect);

        // then
        assertThat(redirect.getFlashAttributes().get("authorizeError"))
                .isEqualTo("Allegro nie przyjął danych dostępu (Client ID, Client Secret). Sprawdź je i zapisz ponownie.");
        assertThat(session.getAttribute(StoreMarketplaceAuthorizationController.PENDING_ATTRIBUTE)).isNull();
    }

    @Test
    void checkingBeforeTheOperatorConfirmedStaysOnThePageWithAHint() {
        // given
        Store store = store("store-1", false);
        Pending pending = pending("store-1", Instant.now().plusSeconds(600));
        session.setAttribute(StoreMarketplaceAuthorizationController.PENDING_ATTRIBUTE, pending);
        when(authorization.check(store, pending)).thenReturn(OAuth2DeviceTokenResult.Status.PENDING);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.check("Allegro", session, PL, redirect);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/marketplaces/Allegro/authorize");
        assertThat(redirect.getFlashAttributes().get("authorizeInfo")).isEqualTo("Połączenie nie zostało jeszcze potwierdzone w Allegro.");
        assertThat(session.getAttribute(StoreMarketplaceAuthorizationController.PENDING_ATTRIBUTE)).isEqualTo(pending);
    }

    @Test
    void aConfirmedConnectionReturnsToTheListAndForgetsTheCode() {
        // given
        Store store = store("store-1", false);
        Pending pending = pending("store-1", Instant.now().plusSeconds(600));
        session.setAttribute(StoreMarketplaceAuthorizationController.PENDING_ATTRIBUTE, pending);
        when(authorization.check(store, pending)).thenReturn(OAuth2DeviceTokenResult.Status.AUTHORIZED);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.check("Allegro", session, PL, redirect);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/marketplaces");
        assertThat(redirect.getFlashAttributes().get(SettingsFlash.SAVED_MESSAGE))
                .isEqualTo("Połączono konto Allegro. Zamówienia będą pobierane według harmonogramu.");
        assertThat(session.getAttribute(StoreMarketplaceAuthorizationController.PENDING_ATTRIBUTE)).isNull();
    }

    @Test
    void theScriptGetsTheAnswerAsJsonAndIsSentOnOnlyWhenItIsFinal() {
        // given
        Store store = store("store-1", false);
        Pending pending = pending("store-1", Instant.now().plusSeconds(600));
        session.setAttribute(StoreMarketplaceAuthorizationController.PENDING_ATTRIBUTE, pending);
        when(authorization.check(store, pending)).thenReturn(OAuth2DeviceTokenResult.Status.SLOW_DOWN,
                OAuth2DeviceTokenResult.Status.FAILED);

        // when
        ResponseEntity<Map<String, Object>> waiting = controller.status("Allegro", session, PL,
                new MockHttpServletRequest(), new MockHttpServletResponse());
        ResponseEntity<Map<String, Object>> refused = controller.status("Allegro", session, PL,
                requestWithFlash(), new MockHttpServletResponse());

        // then
        assertThat(waiting.getBody()).containsEntry("status", "PENDING").containsEntry("redirect", null);
        assertThat(refused.getBody()).containsEntry("status", "FAILED")
                .containsEntry("redirect", "/dashboard/store/marketplaces/Allegro/authorize");
        assertThat(session.getAttribute(StoreMarketplaceAuthorizationController.PENDING_ATTRIBUTE)).isNull();
    }

    /** Without this the script navigated back to the page and the operator saw no reason why nothing was connected. */
    @Test
    void aRefusedOrExpiredCodeLeavesItsMessageForThePageTheScriptOpens() {
        // given
        Store store = store("store-1", false);
        Pending pending = pending("store-1", Instant.now().plusSeconds(600));
        session.setAttribute(StoreMarketplaceAuthorizationController.PENDING_ATTRIBUTE, pending);
        when(authorization.check(store, pending)).thenReturn(OAuth2DeviceTokenResult.Status.FAILED);
        MockHttpServletRequest refusedRequest = requestWithFlash();
        MockHttpServletRequest expiredRequest = requestWithFlash();

        // when
        controller.status("Allegro", session, PL, refusedRequest, new MockHttpServletResponse());
        controller.status("Allegro", session, PL, expiredRequest, new MockHttpServletResponse());

        // then
        assertThat((String) flash(refusedRequest).get("authorizeError")).startsWith("Połączenie z Allegro nie powiodło się");
        assertThat((String) flash(expiredRequest).get("authorizeError")).startsWith("Kod wygasł");
        assertThat(flash(refusedRequest).getTargetRequestPath()).isEqualTo("/dashboard/store/marketplaces/Allegro/authorize");
    }

    private static MockHttpServletRequest requestWithFlash() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
        request.setAttribute(DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE, new SessionFlashMapManager());
        return request;
    }

    private static FlashMap flash(MockHttpServletRequest request) {
        return (FlashMap) request.getAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE);
    }

    /** A code of another store (a super admin switching stores) or an expired one is never checked. */
    @Test
    void anExpiredCodeOrOneOfAnotherStoreIsNotChecked() {
        // given
        store("store-1", false);
        session.setAttribute(StoreMarketplaceAuthorizationController.PENDING_ATTRIBUTE,
                pending("store-1", Instant.now().minusSeconds(1)));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        controller.check("Allegro", session, PL, redirect);
        session.setAttribute(StoreMarketplaceAuthorizationController.PENDING_ATTRIBUTE,
                pending("store-2", Instant.now().plusSeconds(600)));
        controller.check("Allegro", session, PL, new RedirectAttributesModelMap());

        // then
        verify(authorization, never()).check(any(), any());
        assertThat((String) redirect.getFlashAttributes().get("authorizeError")).startsWith("Kod wygasł");
    }

    /** The old endpoints were ADMIN only and used the session's store: the super admin got 403. */
    @Test
    void aSuperAdminConnectsTheStoreOfTheAddress() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        Store store = store("store-9", false);
        when(authorization.start(store, "Allegro")).thenReturn(pending("store-9", Instant.now().plusSeconds(600)));

        // when
        String view = controller.superAdminStart("store-9", "Allegro", session, PL, new RedirectAttributesModelMap());

        // then
        verify(authorization).start(store, "Allegro");
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-9/marketplaces/Allegro/authorize");
    }

    private Store store(String storeId, boolean allegroConnected) {
        Store store = new Store();
        store.setStoreId(storeId);
        MarketplaceIntegration allegro = store.connectMarketplace("Allegro", !allegroConnected);
        allegro.setLoggedIn(allegroConnected);
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
    }

    private static Pending pending(String storeId, Instant expiresAt) {
        return new Pending(storeId, "Allegro", "dev-1", "ABC123", "https://allegro.example/skojarz",
                "https://allegro.example/skojarz?code=ABC123", expiresAt, 5);
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
