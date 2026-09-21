package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.shipping.api.Carrier;
import pl.commercelink.stores.AuthorizedCarrier;
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.IntegrationSettingsForm;
import pl.commercelink.web.settings.IntegrationStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.commercelink.testsupport.SecurityContextLogin.logInAs;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreShippingAccountControllerTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl");
    private static final IntegrationStatus CONFIGURED = new IntegrationStatus("furgonetka", "Furgonetka", true, true);

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private ShippingAccounts shippingAccounts;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StoreShippingAccountController controller;

    @BeforeEach
    void messagesAreTheirKeys() {
        when(messageSource.getMessage(anyString(), any(), eq(POLISH))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Store store(String storeId, AuthorizedCarrier... carriers) {
        Store store = new Store();
        store.setStoreId(storeId);
        ShippingConfiguration configuration = new ShippingConfiguration();
        configuration.setAuthorizedCarriers(new ArrayList<>(List.of(carriers)));
        store.setShippingConfiguration(configuration);
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
    }

    @Test
    void theChosenCarriersAreSavedWithTheirNamesFromTheAccount() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1", new AuthorizedCarrier("99", "local", "Kurier Lokalny"));
        when(shippingAccounts.status(store)).thenReturn(CONFIGURED);
        when(shippingAccounts.carriers(store)).thenReturn(new ShippingAccounts.CarrierLookup(
                List.of(new Carrier("1", "dpd", "DPD"), new Carrier("2", "gls", "GLS")), null));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.saveCarriers(List.of("1", "99"), null, new ExtendedModelMap(), POLISH, redirect,
                new MockHttpServletRequest(), new MockHttpServletResponse());

        // then: a carrier the account no longer offers stays while it is ticked
        verify(storesRepository).save(store);
        assertThat(store.getShippingConfiguration().getAuthorizedCarriers())
                .extracting(AuthorizedCarrier::getDisplayName).containsExactly("DPD", "Kurier Lokalny");
        assertThat(view).isEqualTo("redirect:/dashboard/store/shipping");
    }

    @Test
    void nothingTickedMeansNoCarrierIsChosen() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1", new AuthorizedCarrier("1", "dpd", "DPD"));
        when(shippingAccounts.status(store)).thenReturn(CONFIGURED);
        when(shippingAccounts.carriers(store)).thenReturn(new ShippingAccounts.CarrierLookup(List.of(new Carrier("1", "dpd", "DPD")), null));

        // when
        controller.saveCarriers(null, null, new ExtendedModelMap(), POLISH, new RedirectAttributesModelMap(),
                new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        assertThat(store.getShippingConfiguration().getAuthorizedCarriers()).isEmpty();
    }

    @Test
    void aFailedCallKeepsTheSavedCarriersAndSaysWhy() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1", new AuthorizedCarrier("1", "dpd", "DPD"));
        when(shippingAccounts.status(store)).thenReturn(CONFIGURED);
        when(shippingAccounts.carriers(store)).thenReturn(new ShippingAccounts.CarrierLookup(List.of(), "HTTP 401"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.saveCarriers(List.of(), "fetch", model, POLISH, new RedirectAttributesModelMap(),
                new MockHttpServletRequest(), response);

        // then
        verify(storesRepository, never()).save(any());
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(view).isEqualTo("store-shipping-carriers :: carriersForm");
        assertThat(model.get("lookupError")).isEqualTo("HTTP 401");
        assertThat(store.getShippingConfiguration().getAuthorizedCarriers()).hasSize(1);
    }

    @Test
    void theSavedCarriersTheAccountDropsAreListedAfterTheOffered() {
        // when
        List<StoreShippingAccountController.CarrierOption> options = StoreShippingAccountController.options(
                List.of(new Carrier("1", "dpd", "DPD")),
                List.of(new AuthorizedCarrier("1", "dpd", "DPD"), new AuthorizedCarrier("9", "old", "Stary kurier")),
                Set.of("9"));

        // then
        assertThat(options).extracting(StoreShippingAccountController.CarrierOption::displayName).containsExactly("DPD", "Stary kurier");
        assertThat(options).extracting(StoreShippingAccountController.CarrierOption::available).containsExactly(true, false);
        assertThat(options).extracting(StoreShippingAccountController.CarrierOption::selected).containsExactly(false, true);
    }

    @Test
    void anAccountWithoutItsRequiredSettingsIsRejected() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        when(shippingAccounts.fieldsOf("furgonetka")).thenReturn(List.of(
                new ProviderField("password", "API Password", FieldType.PASSWORD, true, null)));
        when(shippingAccounts.storedSecretKeys(store, "furgonetka")).thenReturn(Set.of());
        when(shippingAccounts.status(store)).thenReturn(IntegrationStatus.none());
        IntegrationSettingsForm form = new IntegrationSettingsForm();
        form.setProviderName("furgonetka");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.saveAccount(form, null, model, POLISH, new RedirectAttributesModelMap(),
                new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        verify(shippingAccounts, never()).save(any(), anyString(), anyMap());
        assertThat(view).isEqualTo("store-shipping-account");
        assertThat(model.get("errors")).isEqualTo(Map.of("setting-furgonetka-password", "integration.setting.required"));
    }

    @Test
    void superAdminSavesTheAccountOfTheStoreInThePath() {
        // given
        logInAs("SUPER_ADMIN", "own-store");
        Store store = store("store-7");
        when(shippingAccounts.fieldsOf("furgonetka")).thenReturn(List.of(
                new ProviderField("username", "API Username", FieldType.TEXT, true, null)));
        when(shippingAccounts.storedSecretKeys(store, "furgonetka")).thenReturn(Set.of());
        IntegrationSettingsForm form = new IntegrationSettingsForm();
        form.setProviderName("furgonetka");
        form.setSettings(new HashMap<>(Map.of("furgonetka.username", "sklep")));

        // when
        String view = controller.superAdminSaveAccount("store-7", form, null, new ExtendedModelMap(), POLISH,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        verify(shippingAccounts).save(store, "furgonetka", Map.of("username", "sklep"));
        verify(storesRepository).save(store);
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-7/shipping");
    }

    @Test
    void disconnectingDeletesTheAccountOfTheStore() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        when(shippingAccounts.current(store)).thenReturn("furgonetka");

        // when
        String view = controller.disconnect(POLISH, new RedirectAttributesModelMap());

        // then
        verify(shippingAccounts).disconnect(store, "furgonetka");
        verify(storesRepository).save(store);
        assertThat(view).isEqualTo("redirect:/dashboard/store/shipping");
    }
}
