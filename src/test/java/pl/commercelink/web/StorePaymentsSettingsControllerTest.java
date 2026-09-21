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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.payments.PaymentProviderFactory;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.CheckoutConfiguration;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.PaymentIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.CheckoutSettingsForm;
import pl.commercelink.web.dtos.PickerOption;
import pl.commercelink.web.settings.DeliveryOptionView;
import pl.commercelink.web.settings.PaymentGatewayView;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StorePaymentsSettingsControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private PaymentProviderFactory paymentProviderFactory;
    @Mock
    private MessageSource messageSource;

    private StorePaymentsSettingsController controller;

    @BeforeEach
    void loggedInAsStoreAdmin() {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(call -> call.getArgument(0));
        when(paymentProviderFactory.loadConfigurationForUI(any(), anyString())).thenReturn(Map.of());
        controller = new StorePaymentsSettingsController(storesRepository,
                PaymentGatewaysFixture.install(paymentProviderFactory, messageSource), messageSource);
        authenticateAs("store-1", "ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** A store created by StoreCreationService has no checkout configuration until something is saved. */
    @Test
    void aStoreThatNeverSavedCheckoutSettingsGetsDefaultsAndAWarningWithoutBlankRows() {
        // given
        Store store = store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.payments(model, PL);

        // then
        assertThat(view).isEqualTo("store-payments");
        assertThat(store.getCheckoutConfiguration()).isNull();
        assertThat((List<?>) model.getAttribute("deliveryOptions")).isEmpty();
        assertThat(model.getAttribute("returnToLocalMachine")).isEqualTo(true);
        assertThat(((CheckoutSettingsForm) model.getAttribute("checkoutForm")).getCurrency()).isEqualTo("pln");
        assertThat(model.getAttribute("newGatewayHref")).isEqualTo("/dashboard/store/payments/gateways/new");
    }

    /** The page cannot create a duplicate, but a record edited by hand could, and it took the whole page down. */
    @Test
    @SuppressWarnings("unchecked")
    void thePageOpensWhenTwoGatewaysAreStoredUnderTheSameName() {
        // given
        Store store = store("store-1");
        store.addPaymentIntegration("stripe");
        store.getPayments().add(new PaymentIntegration("stripe"));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.payments(model, PL);

        // then
        assertThat(view).isEqualTo("store-payments");
        assertThat((Map<String, String>) model.getAttribute("gatewayDisconnectMessages")).containsKey("stripe");
    }

    @Test
    void thePageListsGatewaysAndOnlyActiveDeliveryOptions() {
        // given
        Store store = store("store-1");
        store.addPaymentIntegration("stripe");
        store.addPaymentIntegration("retired-gateway");
        CheckoutConfiguration configuration = new CheckoutConfiguration();
        DeliveryOption courier = option("Kurier");
        DeliveryOption old = option("Stary kurier");
        configuration.setDeliveryOptions(List.of(courier, old));
        configuration.retireDeliveryOption(old.getId());
        store.setCheckoutConfiguration(configuration);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.payments(model, PL);

        // then
        @SuppressWarnings("unchecked")
        List<PaymentGatewayView> gateways = (List<PaymentGatewayView>) model.getAttribute("gateways");
        assertThat(gateways).extracting(PaymentGatewayView::displayName).containsExactly("Stripe", "retired-gateway");
        assertThat(gateways.get(0).isDefault()).isTrue();
        assertThat(gateways.get(0).configured()).isFalse();
        assertThat(gateways.get(1).installed()).isFalse();
        @SuppressWarnings("unchecked")
        List<DeliveryOptionView> options = (List<DeliveryOptionView>) model.getAttribute("deliveryOptions");
        assertThat(options).extracting(DeliveryOptionView::name).containsExactly("Kurier");
        assertThat(options.get(0).deleteHref()).isEqualTo("/dashboard/store/payments/delivery-options/" + courier.getId() + "/delete");
    }

    @Test
    void savingCheckoutSettingsKeepsTheDeliveryOptionsAndStoresTheZlotyInLowerCase() {
        // given
        Store store = store("store-1");
        CheckoutConfiguration configuration = new CheckoutConfiguration();
        DeliveryOption courier = option("Kurier");
        configuration.setDeliveryOptions(List.of(courier));
        store.setCheckoutConfiguration(configuration);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.saveCheckout(checkout("https://sklep.pl/ok", "PLN", "2"), null, new ExtendedModelMap(), PL,
                redirect, new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getCheckoutConfiguration().getCurrency()).isEqualTo("pln");
        assertThat(store.getCheckoutConfiguration().getDeliveryOptions()).containsExactly(courier);
        assertThat(view).isEqualTo("redirect:/dashboard/store/payments");
    }

    @Test
    void anInvalidSaveWithoutReloadingAnswers422WithTheFormFragment() {
        // given
        store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.saveCheckout(checkout("sklep.pl", "eur", "2"), "fetch", model, PL,
                new RedirectAttributesModelMap(), response);

        // then
        verify(storesRepository, never()).save(any(Store.class));
        assertThat(view).isEqualTo("store-payments :: checkoutForm");
        assertThat(response.getStatus()).isEqualTo(422);
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) model.getAttribute("checkoutErrors");
        assertThat(errors).containsOnlyKeys("successUrl", "currency");
    }

    @Test
    void aCurrencySavedEarlierStaysAChoiceMarkedAsSuch() {
        // given
        Store store = store("store-1");
        CheckoutConfiguration configuration = new CheckoutConfiguration();
        configuration.setCurrency("eur");
        store.setCheckoutConfiguration(configuration);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.payments(model, PL);

        // then
        assertThat(model.getAttribute("currencies")).isEqualTo(List.of(
                new PickerOption("eur", "store.payments.checkout.currency.legacy"),
                new PickerOption("pln", "store.payments.checkout.currency.pln")));
    }

    @Test
    void superAdminSavesTheStoreFromThePath() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        Store store = store("store-2");

        // when
        String view = controller.superAdminSaveCheckout("store-2", checkout("https://sklep.pl/ok", "pln", "1"), null,
                new ExtendedModelMap(), PL, new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        assertThat(store.getCheckoutConfiguration().getSuccessUrl()).isEqualTo("https://sklep.pl/ok");
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-2/payments");
    }

    @Test
    void makingAGatewayDefaultMovesTheFlagAndAnUnknownOneAnswers404() {
        // given
        Store store = store("store-1");
        store.addPaymentIntegration("stripe");
        store.addPaymentIntegration("bank");

        // when
        controller.makeDefault("bank", PL, new RedirectAttributesModelMap());

        // then
        assertThat(store.getPaymentIntegration("bank").is_default()).isTrue();
        assertThat(store.getPaymentIntegration("stripe").is_default()).isFalse();
        assertThatThrownBy(() -> controller.makeDefault("other", PL, new RedirectAttributesModelMap()))
                .isInstanceOf(ResponseStatusException.class);
    }

    private static CheckoutSettingsForm checkout(String successUrl, String currency, String pricelists) {
        CheckoutSettingsForm form = new CheckoutSettingsForm();
        form.setSuccessUrl(successUrl);
        form.setCancelUrl("https://sklep.pl/koszyk");
        form.setCurrency(currency);
        form.setAcceptedPricelists(pricelists);
        return form;
    }

    private static DeliveryOption option(String name) {
        DeliveryOption option = new DeliveryOption();
        option.setName(name);
        return option;
    }

    private Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
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
