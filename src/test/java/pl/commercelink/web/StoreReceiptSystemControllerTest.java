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
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.receipts.FakeReceiptProviderDescriptor;
import pl.commercelink.receipts.InMemoryReceiptAttemptStore;
import pl.commercelink.receipts.ReceiptAttempt;
import pl.commercelink.receipts.ReceiptAttemptState;
import pl.commercelink.receipts.ReceiptAttemptStore;
import pl.commercelink.receipts.ReceiptProviderFactory;
import pl.commercelink.receipts.api.ReceiptProviderDescriptor;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.IntegrationSettingsForm;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreReceiptSystemControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final String SYSTEM = FakeReceiptProviderDescriptor.NAME;
    private static final String API_DOMAIN = "https://api.test";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private ReceiptProviderFactory receiptProviderFactory;
    @Mock
    private ReceiptAttemptStore attempts;
    @Mock
    private MessageSource messageSource;

    private StoreReceiptSystemController controller;

    @BeforeEach
    void loggedInAsStoreAdmin() {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(call -> call.getArgument(0));
        FakeReceiptProviderDescriptor descriptor = new FakeReceiptProviderDescriptor();
        when(receiptProviderFactory.getDescriptor(SYSTEM)).thenReturn(descriptor);
        when(receiptProviderFactory.availableProviders()).thenReturn(List.of(descriptor));
        when(receiptProviderFactory.loadConfigurationForUI(any())).thenReturn(Map.of());
        controller = new StoreReceiptSystemController(storesRepository,
                new ReceiptSystems(receiptProviderFactory, attempts, API_DOMAIN), messageSource);
        authenticateAs("store-1", "ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theSubpageShowsTheWebhookAddressOfTheFakeProvider() {
        // given
        Store store = store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.system(model, PL);

        // then
        assertThat(view).isEqualTo("store-receipt-system");
        assertThat(store.getStoreId()).isEqualTo("store-1");
        @SuppressWarnings("unchecked")
        Map<String, String> webhooks = (Map<String, String>) model.getAttribute("webhooks");
        assertThat(webhooks).containsEntry(SYSTEM, "https://api.test/Store/store-1/Webhooks/Receipts/test");
    }

    @Test
    void aSystemWithoutItsRequiredSettingsIsRejectedAndTheStoreKeepsItsProvider() {
        // given
        store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.saveSystem(system(Map.of()), "fetch", model, PL, new RedirectAttributesModelMap(),
                new MockHttpServletRequest(), response);

        // then
        verify(receiptProviderFactory, never()).saveConfiguration(any(), anyString(), anyMap());
        verify(storesRepository, never()).save(any(Store.class));
        assertThat(store("store-1").getConfigurationValue(IntegrationType.RECEIPT_PROVIDER)).isNull();
        assertThat(view).isEqualTo("store-receipt-system :: systemForm");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(model.getAttribute("errors")).isEqualTo(Map.of("setting-" + SYSTEM + "-token", "integration.setting.required"));
    }

    @Test
    void aCompleteSystemIsSavedAndTheSaveReturnsToTheReceiptsPage() {
        // given
        Store store = store("store-1");

        // when
        String redirect = controller.saveSystem(system(Map.of(SYSTEM + ".token", "secret")), null, new ExtendedModelMap(),
                PL, new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        verify(receiptProviderFactory).saveConfiguration(store, SYSTEM, Map.of("token", "secret"));
        verify(storesRepository).save(store);
        assertThat(store.getConfigurationValue(IntegrationType.RECEIPT_PROVIDER)).isEqualTo(SYSTEM);
        assertThat(redirect).isEqualTo("redirect:/dashboard/store/receipts");
    }

    @Test
    void disconnectIsRefusedWhileReceiptsAreStillBeingIssued() {
        // given
        Store store = configuredStore();
        when(attempts.hasLiveAttempts("store-1")).thenReturn(true);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = controller.disconnect(PL, redirectAttributes);

        // then
        verify(receiptProviderFactory, never()).deleteConfiguration(any(), anyString());
        assertThat(store.getConfigurationValue(IntegrationType.RECEIPT_PROVIDER)).isEqualTo(SYSTEM);
        assertThat(view).isEqualTo("redirect:/dashboard/store/receipts");
        assertThat(redirectAttributes.getFlashAttributes().get("settingsErrorMessage"))
                .isEqualTo("store.receipts.system.disconnect.live");
    }

    @Test
    void disconnectingDeletesTheSettingsAndTheProviderWhenNothingIsLive() {
        // given
        Store store = configuredStore();
        when(attempts.hasLiveAttempts("store-1")).thenReturn(false);

        // when
        String view = controller.disconnect(PL, new RedirectAttributesModelMap());

        // then
        verify(receiptProviderFactory).deleteConfiguration(store, SYSTEM);
        verify(storesRepository).save(store);
        assertThat(store.getConfigurationValue(IntegrationType.RECEIPT_PROVIDER)).isNull();
        assertThat(view).isEqualTo("redirect:/dashboard/store/receipts");
    }

    /** Switching provider while attempts of the old one are still live would strip them of the secret they need
     *  (ReceiptSystems.save deletes the previous provider's configuration once the new one is saved). */
    @Test
    void switchingTheProviderIsRefusedWhileTheOldOnesReceiptsAreStillBeingIssued() {
        // given
        Store store = configuredStore();
        ReceiptProviderDescriptor other = mock(ReceiptProviderDescriptor.class);
        when(other.name()).thenReturn("other-receipts");
        when(other.configurationFields()).thenReturn(List.of(new ProviderField("token", "Token",
                ProviderField.FieldType.PASSWORD, true, null)));
        when(receiptProviderFactory.getDescriptor("other-receipts")).thenReturn(other);
        when(attempts.hasLiveAttempts("store-1")).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.saveSystem(otherSystem(Map.of("other-receipts.token", "secret")), "fetch",
                new ExtendedModelMap(), PL, new RedirectAttributesModelMap(), new MockHttpServletRequest(), response);

        // then
        verify(receiptProviderFactory, never()).saveConfiguration(any(), anyString(), anyMap());
        assertThat(store.getConfigurationValue(IntegrationType.RECEIPT_PROVIDER)).isEqualTo(SYSTEM);
        assertThat(view).isEqualTo("store-receipt-system :: systemForm");
        assertThat(response.getStatus()).isEqualTo(422);
    }

    /** A fiscalised receipt still waiting for its link (no document, no give-up) is still polled through the
     *  provider: disconnecting now would strand it, so the customer would never get the e-mail. */
    @Test
    void disconnectIsRefusedWhileAFiscalisedReceiptStillWaitsForItsLink() {
        // given
        InMemoryReceiptAttemptStore realAttempts = new InMemoryReceiptAttemptStore();
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setStoreId("store-1");
        attempt.setReceiptKey("o1:R1");
        attempt.setOrderId("o1");
        attempt.setState(ReceiptAttemptState.FISCALISED);
        realAttempts.create(attempt);
        StoreReceiptSystemController controllerWithRealAttempts = new StoreReceiptSystemController(storesRepository,
                new ReceiptSystems(receiptProviderFactory, realAttempts, API_DOMAIN), messageSource);
        Store store = configuredStore();
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        authenticateAs("store-1", "ADMIN");

        // when
        String view = controllerWithRealAttempts.disconnect(PL, redirectAttributes);

        // then
        verify(receiptProviderFactory, never()).deleteConfiguration(any(), anyString());
        assertThat(store.getConfigurationValue(IntegrationType.RECEIPT_PROVIDER)).isEqualTo(SYSTEM);
        assertThat(view).isEqualTo("redirect:/dashboard/store/receipts");
        assertThat(redirectAttributes.getFlashAttributes().get("settingsErrorMessage"))
                .isEqualTo("store.receipts.system.disconnect.live");
    }

    /** {@code save} can also fail with an unrelated {@code IllegalStateException} (e.g. from the provider factory);
     *  only {@link ReceiptSystemBusyException} means "live receipts", so anything else must propagate instead of
     *  being reported as the switch-blocked error. */
    @Test
    void aDifferentIllegalStateExceptionFromSavingIsNotReportedAsLiveReceipts() {
        // given
        store("store-1");
        doThrow(new IllegalStateException("storage unavailable"))
                .when(receiptProviderFactory).saveConfiguration(any(), anyString(), anyMap());

        // when / then
        assertThatThrownBy(() -> controller.saveSystem(system(Map.of(SYSTEM + ".token", "secret")), null,
                new ExtendedModelMap(), PL, new RedirectAttributesModelMap(), new MockHttpServletRequest(),
                new MockHttpServletResponse()))
                .isExactlyInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(ReceiptSystemBusyException.class);
    }

    private Store configuredStore() {
        Store store = store("store-1");
        store.setConfigurationValue(IntegrationType.RECEIPT_PROVIDER, SYSTEM);
        when(receiptProviderFactory.loadConfigurationForUI(store)).thenReturn(Map.of());
        return store;
    }

    private Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
    }

    private static IntegrationSettingsForm system(Map<String, String> settings) {
        IntegrationSettingsForm form = new IntegrationSettingsForm();
        form.setProviderName(SYSTEM);
        form.setSettings(new HashMap<>(settings));
        return form;
    }

    private static IntegrationSettingsForm otherSystem(Map<String, String> settings) {
        IntegrationSettingsForm form = new IntegrationSettingsForm();
        form.setProviderName("other-receipts");
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
