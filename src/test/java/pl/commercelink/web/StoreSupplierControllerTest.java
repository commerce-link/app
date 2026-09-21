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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.SupplierSettingsForm;
import pl.commercelink.web.settings.ConfirmAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.commercelink.testsupport.SecurityContextLogin.logInAs;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreSupplierControllerTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl");

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private SupplierConnections suppliers;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StoreSupplierController controller;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Store store(String storeId, boolean canUseGlobal, StoreSupplierConnection... connections) {
        Store store = new Store();
        store.setStoreId(storeId);
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setCanUseGlobalSuppliers(canUseGlobal);
        config.setSupplierConnections(new ArrayList<>(List.of(connections)));
        store.setFulfilmentConfiguration(config);
        when(storesRepository.findById(storeId)).thenReturn(store);
        for (StoreSupplierConnection connection : connections) {
            when(suppliers.connection(store, connection.getSupplierName())).thenReturn(connection);
            when(suppliers.known(connection)).thenReturn(true);
        }
        when(suppliers.types()).thenReturn(List.of("Acme"));
        return store;
    }

    private static StoreSupplierConnection own(String identity, String label) {
        StoreSupplierConnection connection = new StoreSupplierConnection(identity, ConnectionMode.OWN, true, true);
        connection.setLabel(label);
        return connection;
    }

    private String add(SupplierSettingsForm form, String requestedWith, ExtendedModelMap model, RedirectAttributesModelMap redirect,
                       MockHttpServletResponse response) {
        return controller.addSupplier(form, requestedWith, model, POLISH, redirect, new MockHttpServletRequest(), response);
    }

    @Test
    void theNewSupplierPageOffersTheIntegrationsAndAPriceList() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", true);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.newSupplier(model, POLISH);

        // then
        assertThat(view).isEqualTo("store-supplier");
        assertThat(model.get("types")).isEqualTo(List.of("Acme"));
        assertThat(model.get("csvOption")).isEqualTo(true);
        assertThat(model.get("canChooseMode")).isEqualTo(true);
        assertThat(model.get("formAction")).isEqualTo("/dashboard/store/suppliers/new");
        SupplierSettingsForm form = (SupplierSettingsForm) model.get("form");
        assertThat(form.isIncludeInPricing()).isTrue();
        assertThat(form.isIncludeInFulfilment()).isTrue();
    }

    @Test
    void storeAdminAddsToTheStoreFromTheSessionAndReturnsToTheList() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1", false);
        when(suppliers.saveIntegration(eq(store), eq(null), any(), eq(POLISH)))
                .thenReturn(new SupplierConnections.SaveResult(Map.of(), null, "Acme-abcd1234"));
        when(messageSource.getMessage(eq("store.suppliers.added"), any(), eq(POLISH))).thenReturn("Dodano dostawcę Acme 2.");
        SupplierSettingsForm form = SupplierSettingsForm.newSupplier("Acme");
        form.setLabel(" Acme 2 ");
        form.setMode(ConnectionMode.GLOBAL.name());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = add(form, null, new ExtendedModelMap(), redirect, new MockHttpServletResponse());

        // then -- a store that may not use the global configuration adds its own connection whatever the form said
        assertThat(form.getMode()).isEqualTo("OWN");
        assertThat(view).isEqualTo("redirect:/dashboard/store/suppliers");
        assertThat(redirect.getFlashAttributes().get("settingsSavedMessage")).isEqualTo("Dodano dostawcę Acme 2.");
    }

    @Test
    void aRejectedSaveComesBackWithItsErrorsAnd422WhenSentWithoutReloading() {
        // given
        logInAs("ADMIN", "store-1");
        Store store = store("store-1", true);
        when(suppliers.saveIntegration(eq(store), eq(null), any(), eq(POLISH)))
                .thenReturn(new SupplierConnections.SaveResult(Map.of("label", "store.suppliers.label.required"), null, null));
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = add(SupplierSettingsForm.newSupplier("Acme"), "fetch", model, new RedirectAttributesModelMap(), response);

        // then
        assertThat(view).isEqualTo("store-supplier :: supplierForm");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(model.get("errors")).isEqualTo(Map.of("label", "store.suppliers.label.required"));
    }

    @Test
    void aSaveSentWithoutReloadingLeavesTheMessageForTheListAndTellsTheScriptWhereToGo() {
        // given
        logInAs("SUPER_ADMIN", "none");
        Store store = store("store-9", true);
        when(suppliers.saveCsv(eq(store), eq(null), any(), eq(POLISH)))
                .thenReturn(new SupplierConnections.SaveResult(Map.of(), null, "manual-abcd1234"));
        SupplierSettingsForm form = SupplierSettingsForm.newSupplier(SupplierSettingsForm.CSV);
        form.setLabel("Hurtownia");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletRequest request = new MockHttpServletRequest();
        FlashMap flash = new FlashMap();
        request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, flash);
        request.setAttribute(DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE, new SessionFlashMapManager());
        when(messageSource.getMessage(eq("store.suppliers.added"), any(), eq(POLISH))).thenReturn("Dodano dostawcę Hurtownia.");

        // when
        String view = controller.superAdminAddSupplier("store-9", form, "fetch", model, POLISH,
                new RedirectAttributesModelMap(), request, new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("store-supplier :: supplierForm");
        assertThat(model.get("redirectTo")).isEqualTo("/dashboard/store/store-9/suppliers");
        assertThat(flash.get("settingsSavedMessage")).isEqualTo("Dodano dostawcę Hurtownia.");
    }

    @Test
    void anEditSavesTheSupplierInTheAddressWhateverTheFormSays() {
        // given
        logInAs("ADMIN", "store-1");
        StoreSupplierConnection connection = own("Acme-abcd1234", "Acme 2");
        Store store = store("store-1", true, connection);
        when(suppliers.saveIntegration(eq(store), eq(connection), any(), eq(POLISH)))
                .thenReturn(new SupplierConnections.SaveResult(Map.of(), null, "Acme-abcd1234"));
        SupplierSettingsForm form = SupplierSettingsForm.newSupplier(SupplierSettingsForm.CSV);
        form.setLabel("Acme 2");
        form.setMode(ConnectionMode.GLOBAL.name());

        // when
        controller.updateSupplier("Acme-abcd1234", form, null, new ExtendedModelMap(), POLISH,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse());

        // then -- a tokened own connection cannot become global, and it is an integration, not a price list
        ArgumentCaptor<SupplierSettingsForm> saved = ArgumentCaptor.forClass(SupplierSettingsForm.class);
        verify(suppliers).saveIntegration(eq(store), eq(connection), saved.capture(), eq(POLISH));
        assertThat(saved.getValue().getProviderName()).isEqualTo("Acme");
        assertThat(saved.getValue().getMode()).isEqualTo("OWN");
        verify(suppliers, never()).saveCsv(any(), any(), any(), any());
    }

    @Test
    void aSupplierOfAnotherStoreIsNotFound() {
        // given
        logInAs("ADMIN", "store-1");
        store("store-1", true);

        // when / then
        assertThatThrownBy(() -> controller.editSupplier("Acme-abcd1234", new ExtendedModelMap(), POLISH))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void aSupplierWhoseIntegrationIsGoneIsNotEditedButCanBeDisconnected() {
        // given
        logInAs("ADMIN", "store-1");
        StoreSupplierConnection connection = own("Gone", "Gone");
        store("store-1", true, connection);
        when(suppliers.known(connection)).thenReturn(false);

        // when
        String view = controller.editSupplier("Gone", new ExtendedModelMap(), POLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/suppliers");
    }

    @Test
    void aGlobalConnectionOfAStoreThatMayNoLongerUseItIsCompletedAsAnOwnOne() {
        // given
        logInAs("ADMIN", "store-1");
        StoreSupplierConnection connection = new StoreSupplierConnection("Acme", ConnectionMode.GLOBAL, true, true);
        Store store = store("store-1", false, connection);
        SupplierSettingsForm form = SupplierSettingsForm.newSupplier("Acme");
        form.setMode(ConnectionMode.GLOBAL.name());
        when(suppliers.formOf(store, connection)).thenReturn(form);
        when(suppliers.storedSecretKeys(any(), any())).thenReturn(Set.of());
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.editSupplier("Acme", model, POLISH);

        // then
        assertThat(form.getMode()).isEqualTo("OWN");
        assertThat(model.get("globalNotAllowed")).isEqualTo(true);
        assertThat(model.get("canChooseMode")).isEqualTo(false);
    }

    @Test
    void removingAPriceListIsConfirmedAsDeletingAndAnIntegrationAsDisconnecting() {
        // given
        logInAs("ADMIN", "store-1");
        StoreSupplierConnection priceList = new StoreSupplierConnection("manual-abcd1234", ConnectionMode.MANUAL, true, true);
        priceList.setLabel("Hurtownia");
        store("store-1", true, priceList);
        when(messageSource.getMessage(eq("store.suppliers.delete.title"), any(), eq(POLISH))).thenReturn("Usunąć dostawcę Hurtownia?");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.confirmRemove("manual-abcd1234", model, POLISH);

        // then
        assertThat(view).isEqualTo("settings-confirm");
        ConfirmAction confirm = (ConfirmAction) model.get("confirm");
        assertThat(confirm.title()).isEqualTo("Usunąć dostawcę Hurtownia?");
        assertThat(confirm.actionPath()).isEqualTo("/dashboard/store/suppliers/manual-abcd1234/delete");
    }

    @Test
    void aFailedRemovalSaysSoInsteadOfReportingSuccess() {
        // given
        logInAs("ADMIN", "store-1");
        StoreSupplierConnection connection = own("Acme-abcd1234", "Acme 2");
        Store store = store("store-1", true, connection);
        when(suppliers.remove(store, connection)).thenReturn(false);
        when(messageSource.getMessage(eq("store.supplier.connection.error.update.failed"), any(), eq(POLISH))).thenReturn("Błąd");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.remove("Acme-abcd1234", POLISH, redirect);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/suppliers");
        Map<String, Object> flash = new java.util.HashMap<>(redirect.getFlashAttributes());
        assertThat(flash).containsEntry("errorMessage", "Błąd").doesNotContainKey("settingsSavedMessage");
    }
}
