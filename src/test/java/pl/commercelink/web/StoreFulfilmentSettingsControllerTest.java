package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.inventory.supplier.ErrorMessage;
import pl.commercelink.inventory.supplier.StoreSupplierConnectionService;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.FulfilmentSettingsForm;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.commercelink.testsupport.SecurityContextLogin.logInAs;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreFulfilmentSettingsControllerTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl");

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private StoreSupplierConnectionService storeSupplierConnectionService;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StoreFulfilmentSettingsController controller;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setOrderAssemblyDays(2);
        config.setOrderRealizationDays(5);
        config.setCanUseGlobalSuppliers(true);
        config.setInventoryCacheTtlMinutes(30);
        config.setSupplierConnections(List.of(new StoreSupplierConnection("Acme", ConnectionMode.GLOBAL, true, true)));
        store.setFulfilmentConfiguration(config);
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
    }

    private FulfilmentSettingsForm form(String assemblyDays, String realizationDays) {
        FulfilmentSettingsForm form = new FulfilmentSettingsForm();
        form.setOrderAssemblyDays(assemblyDays);
        form.setOrderRealizationDays(realizationDays);
        form.setDefaultFulfilmentType(FulfilmentType.DirectToConsumer.name());
        form.setAutomatedFulfilment(true);
        form.setClientOrderPageEnabled(true);
        form.setClientShippingAddressChangeEnabled(true);
        return form;
    }

    private void saveSucceeds() {
        when(storeSupplierConnectionService.applyStoreSettings(any(), any(), anyBoolean()))
                .thenReturn(new StoreSupplierConnectionService.ConnectionUpdateResult(List.of(), null, Set.of(), Set.of(), Set.of()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> errors(ExtendedModelMap model) {
        return (Map<String, String>) model.get("errors");
    }

    @Test
    void rendersTheStoresSettingsAndPostsBackToThePage() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.fulfilment(model);

        // then
        assertThat(view).isEqualTo("store-fulfilment");
        FulfilmentSettingsForm form = (FulfilmentSettingsForm) model.get("form");
        assertThat(form.getOrderAssemblyDays()).isEqualTo("2");
        assertThat(form.getOrderRealizationDays()).isEqualTo("5");
        assertThat(model.get("formAction")).isEqualTo("/dashboard/store/fulfilment");
    }

    @Test
    void storeAdminSavesTheStoreFromTheSessionAndKeepsItsSupplierSettings() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1");
        saveSucceeds();
        when(messageSource.getMessage(eq("store.fulfilment.settings.update.success"), any(), eq(POLISH))).thenReturn("Zapisano");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.save(form("3", "4"), null, new ExtendedModelMap(), POLISH, redirect,
                new MockHttpServletResponse());

        // then
        ArgumentCaptor<FulfilmentConfiguration> saved = ArgumentCaptor.forClass(FulfilmentConfiguration.class);
        verify(storeSupplierConnectionService).applyStoreSettings(eq(store), saved.capture(), eq(false));
        assertThat(saved.getValue().getOrderAssemblyDays()).isEqualTo(3);
        assertThat(saved.getValue().getOrderRealizationDays()).isEqualTo(4);
        assertThat(saved.getValue().getDefaultFulfilmentType()).isEqualTo(FulfilmentType.DirectToConsumer);
        assertThat(saved.getValue().isAutomatedFulfilment()).isTrue();
        assertThat(saved.getValue().isClientShippingAddressChangeEnabled()).isTrue();
        assertThat(saved.getValue().isCanUseGlobalSuppliers()).isTrue();
        assertThat(saved.getValue().getInventoryCacheTtlMinutes()).isEqualTo(30);
        assertThat(saved.getValue().getSupplierConnections()).extracting(StoreSupplierConnection::getSupplierName)
                .containsExactly("Acme");
        assertThat(view).isEqualTo("redirect:/dashboard/store/fulfilment");
        assertThat(redirect.getFlashAttributes().get("settingsSavedMessage")).isEqualTo("Zapisano");
    }

    @Test
    void superAdminSavesTheStoreFromThePathWithoutResettingTheGlobalSupplierFlag() {
        // given
        logInAs("SUPER_ADMIN", "none");
        Store store = store("store-9");
        saveSucceeds();

        // when
        String view = controller.superAdminSave("store-9", form("1", "1"), null, new ExtendedModelMap(), POLISH,
                new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        ArgumentCaptor<FulfilmentConfiguration> saved = ArgumentCaptor.forClass(FulfilmentConfiguration.class);
        verify(storeSupplierConnectionService).applyStoreSettings(eq(store), saved.capture(), eq(true));
        assertThat(saved.getValue().isCanUseGlobalSuppliers()).isTrue();
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-9/fulfilment");
    }

    @Test
    void blankOrTooLargeDayCountsComeBackAtTheirFieldsInsteadOfABindingError() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.save(form("", "61"), "fetch", model, POLISH, new RedirectAttributesModelMap(), response);

        // then
        verify(storeSupplierConnectionService, never()).applyStoreSettings(any(), any(), anyBoolean());
        assertThat(view).isEqualTo("store-fulfilment :: fulfilmentForm");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(errors(model)).containsOnlyKeys("orderAssemblyDays", "orderRealizationDays");
        assertThat(((FulfilmentSettingsForm) model.get("form")).getOrderRealizationDays()).isEqualTo("61");
    }

    @Test
    void aFailedSaveShowsAFailureAndNoSuccessMessage() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1");
        when(storeSupplierConnectionService.applyStoreSettings(any(), any(), anyBoolean()))
                .thenReturn(new StoreSupplierConnectionService.ConnectionUpdateResult(
                        List.of(ErrorMessage.of("store.supplier.connection.error.update.failed")), null, Set.of(), Set.of(), Set.of()));
        when(messageSource.getMessage(eq("store.supplier.connection.error.update.failed"), any(), eq(POLISH))).thenReturn("Nie udało się");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.save(form("1", "1"), null, model, POLISH, new RedirectAttributesModelMap(),
                new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("store-fulfilment");
        assertThat(model.get("failure")).isEqualTo("Nie udało się");
        assertThat(model.get("savedMessage")).isNull();
    }

    @Test
    void anUnknownStoreIsNotFound() {
        // given
        logInAs("SUPER_ADMIN", "none");

        // when / then
        assertThatThrownBy(() -> controller.superAdminFulfilment("missing", new ExtendedModelMap()))
                .isInstanceOf(ResponseStatusException.class);
    }
}
