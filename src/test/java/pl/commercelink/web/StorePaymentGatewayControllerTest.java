package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.payments.PaymentProviderFactory;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.PaymentIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.IntegrationSettingsForm;
import pl.commercelink.web.settings.ConfirmAction;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StorePaymentGatewayControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private PaymentProviderFactory paymentProviderFactory;
    @Mock
    private MessageSource messageSource;

    private StorePaymentGatewayController controller;

    @BeforeEach
    void loggedInAsStoreAdmin() {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(call -> call.getArgument(0));
        when(paymentProviderFactory.loadConfigurationForUI(any(), anyString())).thenReturn(Map.of());
        controller = new StorePaymentGatewayController(storesRepository,
                PaymentGatewaysFixture.install(paymentProviderFactory, messageSource), messageSource);
        authenticateAs("store-1", "ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theNewGatewayPageOffersTheInstalledGatewaysWithTheirWebhookAddress() {
        // given
        store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.newGateway(model, PL);

        // then
        assertThat(view).isEqualTo("store-payment-gateway");
        assertThat(model.getAttribute("webhooks"))
                .isEqualTo(Map.of("stripe", "https://api.commercelink.pl/Store/store-1/Webhooks/Payments/stripe"));
        assertThat(model.getAttribute("firstGateway")).isEqualTo(true);
        assertThat(model.getAttribute("formAction")).isEqualTo("/dashboard/store/payments/gateways/new");
    }

    @Test
    void addingAGatewaySavesItsSettingsAndTheFirstOneBecomesTheDefault() {
        // given
        Store store = store("store-1");

        // when
        String view = controller.addGateway(gateway("stripe", Map.of("stripe.apiKey", "sk_live")), false, null,
                new ExtendedModelMap(), PL, new RedirectAttributesModelMap(), new MockHttpServletRequest(),
                new MockHttpServletResponse());

        // then
        verify(paymentProviderFactory).saveConfiguration(store, "stripe", Map.of("apiKey", "sk_live"));
        verify(storesRepository).save(store);
        assertThat(store.getPayments()).extracting(PaymentIntegration::getName).containsExactly("stripe");
        assertThat(store.getPayments().get(0).is_default()).isTrue();
        assertThat(view).isEqualTo("redirect:/dashboard/store/payments");
    }

    /** The old panel saved a gateway without its required key and reported success. */
    @Test
    void aGatewayWithoutItsRequiredKeyIsRejectedAtTheFieldWithoutSaving() {
        // given
        Store store = store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.addGateway(gateway("stripe", Map.of()), false, "fetch", model, PL,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), response);

        // then
        verify(paymentProviderFactory, never()).saveConfiguration(any(), anyString(), anyMap());
        assertThat(store.getPayments()).isEmpty();
        assertThat(view).isEqualTo("store-payment-gateway :: gatewayForm");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(model.getAttribute("errors")).isEqualTo(Map.of("setting-stripe-apiKey", "integration.setting.required"));
    }

    @Test
    void aGatewayTheStoreAlreadyUsesCannotBeAddedTwice() {
        // given
        Store store = store("store-1");
        store.addPaymentIntegration("stripe");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.addGateway(gateway("stripe", Map.of("stripe.apiKey", "sk")), false, null, model, PL,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        verify(storesRepository, never()).save(any(Store.class));
        assertThat(model.getAttribute("errors")).isEqualTo(Map.of("providerName", "integration.provider.required"));
    }

    /** An empty secret keeps the stored one; the gateway edited is the one in the address, not the posted one. */
    @Test
    void editingKeepsTheStoredKeyAndCanMakeTheGatewayDefault() {
        // given
        Store store = store("store-1");
        store.addPaymentIntegration("bank");
        store.addPaymentIntegration("stripe");
        when(paymentProviderFactory.loadConfigurationForUI(store, "stripe")).thenReturn(Map.of("apiKey", ""));
        IntegrationSettingsForm posted = gateway("bank", Map.of("stripe.shopId", "42"));

        // when
        String view = controller.updateGateway("stripe", posted, true, null, new ExtendedModelMap(), PL,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        verify(paymentProviderFactory).saveConfiguration(store, "stripe", Map.of("shopId", "42"));
        assertThat(store.getPaymentIntegration("stripe").is_default()).isTrue();
        assertThat(store.getPaymentIntegration("bank").is_default()).isFalse();
        assertThat(view).isEqualTo("redirect:/dashboard/store/payments");
    }

    @Test
    void savingAGatewayWhoseAdapterIsGoneGoesBackToTheList() {
        // given
        Store store = store("store-1");
        store.addPaymentIntegration("gone");

        // when
        String view = controller.updateGateway("gone", gateway("gone", Map.of()), false, null, new ExtendedModelMap(), PL,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/payments");
        verify(paymentProviderFactory, never()).saveConfiguration(any(), anyString(), anyMap());
    }

    @Test
    void superAdminWorksOnTheStoreFromThePath() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        Store store = store("store-2");

        // when
        String view = controller.superAdminAddGateway("store-2", gateway("stripe", Map.of("stripe.apiKey", "sk")), false,
                null, new ExtendedModelMap(), PL, new RedirectAttributesModelMap(), new MockHttpServletRequest(),
                new MockHttpServletResponse());

        // then
        assertThat(store.getPayments()).hasSize(1);
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-2/payments");
    }

    @Test
    void disconnectingTheDefaultDeletesItsKeysAndHandsTheDefaultOn() {
        // given
        Store store = store("store-1");
        store.addPaymentIntegration("stripe");
        store.addPaymentIntegration("bank");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.confirmDisconnect("stripe", model, PL);
        String view = controller.disconnect("stripe", PL, new RedirectAttributesModelMap());

        // then
        assertThat(((ConfirmAction) model.getAttribute("confirm")).message())
                .isEqualTo("store.payments.gateway.disconnect.message.default");
        verify(paymentProviderFactory).deleteConfiguration(store, "stripe");
        assertThat(store.getPayments()).extracting(PaymentIntegration::getName).containsExactly("bank");
        assertThat(store.getPaymentIntegration("bank").is_default()).isTrue();
        assertThat(view).isEqualTo("redirect:/dashboard/store/payments");
    }

    @Test
    void aGatewayTheStoreDoesNotUseAnswers404() {
        // given
        store("store-1");

        // expect
        assertThatThrownBy(() -> controller.editGateway("stripe", new ExtendedModelMap(), PL)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.confirmDisconnect("stripe", new ExtendedModelMap(), PL)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.updateGateway("stripe", gateway("stripe", Map.of()), false, null,
                new ExtendedModelMap(), PL, new RedirectAttributesModelMap(), new MockHttpServletRequest(),
                new MockHttpServletResponse())).isInstanceOf(ResponseStatusException.class);
        verify(paymentProviderFactory, never()).deleteConfiguration(any(), eq("stripe"));
    }

    @Test
    void whenEveryInstalledGatewayIsUsedTheNewPageGoesBackToTheList() {
        // given
        Store store = store("store-1");
        store.addPaymentIntegration("stripe");
        store.addPaymentIntegration("bank");

        // expect
        assertThat(controller.newGateway(new ExtendedModelMap(), PL)).isEqualTo("redirect:/dashboard/store/payments");
    }

    private Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
    }

    private static IntegrationSettingsForm gateway(String name, Map<String, String> settings) {
        IntegrationSettingsForm form = new IntegrationSettingsForm();
        form.setProviderName(name);
        form.setSettings(new HashMap<>(settings));
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
