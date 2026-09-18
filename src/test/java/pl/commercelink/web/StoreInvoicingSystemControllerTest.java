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
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.invoicing.api.InvoicingProviderDescriptor;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.IntegrationSettingsForm;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreInvoicingSystemControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final String SYSTEM = "fakturownia";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private InvoicingProviderFactory invoicingProviderFactory;
    @Mock
    private MessageSource messageSource;

    private StoreInvoicingSystemController controller;

    @BeforeEach
    void loggedInAsStoreAdmin() {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(call -> call.getArgument(0));
        InvoicingProviderDescriptor descriptor = mock(InvoicingProviderDescriptor.class);
        when(descriptor.name()).thenReturn(SYSTEM);
        when(descriptor.displayName()).thenReturn("Fakturownia");
        when(descriptor.configurationFields()).thenReturn(List.of(
                new ProviderField("domain", "Domena", FieldType.TEXT, true, "firma.fakturownia.pl"),
                new ProviderField("apiToken", "Token API", FieldType.PASSWORD, true, null)));
        when(invoicingProviderFactory.getDescriptor(SYSTEM)).thenReturn(descriptor);
        when(invoicingProviderFactory.availableProviders()).thenReturn(List.of(descriptor));
        when(invoicingProviderFactory.loadConfigurationForUI(any())).thenReturn(Map.of());
        controller = new StoreInvoicingSystemController(storesRepository, new InvoicingSystems(invoicingProviderFactory),
                messageSource);
        authenticateAs("store-1", "ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theSubpageShowsTheStoredSettingsButNeverTheSecret() {
        // given
        Store store = configuredStore();
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.system(model, PL);

        // then
        assertThat(view).isEqualTo("store-invoicing-system");
        IntegrationSettingsForm form = (IntegrationSettingsForm) model.getAttribute("form");
        assertThat(form.getProviderName()).isEqualTo(SYSTEM);
        assertThat(form.getSettings()).isEqualTo(Map.of(SYSTEM + ".domain", "firma.fakturownia.pl"));
        assertThat(model.getAttribute("storedSecretIds")).isEqualTo(Set.of("setting-" + SYSTEM + "-apiToken"));
        assertThat(model.getAttribute("pageTitle")).isEqualTo("store.invoicing.system.change.title");
        assertThat(model.getAttribute("formAction")).isEqualTo("/dashboard/store/invoicing/system");
        assertThat(store.getStoreId()).isEqualTo("store-1");
    }

    /** The old panel switched the store to a provider whose secret had not been created and reported success. */
    @Test
    void aSystemWithoutItsRequiredSettingsIsRejectedAndTheStoreKeepsItsProvider() {
        // given
        Store store = store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.saveSystem(system(Map.of(SYSTEM + ".domain", "firma.fakturownia.pl")), "fetch", model, PL,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), response);

        // then
        verify(invoicingProviderFactory, never()).saveConfiguration(any(), anyString(), anyMap());
        verify(storesRepository, never()).save(any(Store.class));
        assertThat(store.getConfigurationValue(IntegrationType.INVOICING_PROVIDER)).isNull();
        assertThat(view).isEqualTo("store-invoicing-system :: systemForm");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(model.getAttribute("errors")).isEqualTo(Map.of("setting-" + SYSTEM + "-apiToken", "integration.setting.required"));
        assertThat(model.getAttribute("errorLabels")).isEqualTo(Map.of(
                "setting-" + SYSTEM + "-domain", "Domena", "setting-" + SYSTEM + "-apiToken", "Token API"));
    }

    @Test
    void aCompleteSystemIsSavedAndTheSaveReturnsToTheInvoicingPage() {
        // given
        Store store = store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String redirect = controller.saveSystem(system(Map.of(SYSTEM + ".domain", " firma.fakturownia.pl ", SYSTEM + ".apiToken", "secret")),
                null, new ExtendedModelMap(), PL, new RedirectAttributesModelMap(), new MockHttpServletRequest(),
                new MockHttpServletResponse());
        String async = controller.saveSystem(system(Map.of(SYSTEM + ".domain", "firma.fakturownia.pl", SYSTEM + ".apiToken", "secret2")),
                "fetch", model, PL, new RedirectAttributesModelMap(), requestWithFlash(), new MockHttpServletResponse());

        // then
        verify(invoicingProviderFactory).saveConfiguration(store, SYSTEM, Map.of("domain", "firma.fakturownia.pl", "apiToken", "secret"));
        assertThat(store.getConfigurationValue(IntegrationType.INVOICING_PROVIDER)).isEqualTo(SYSTEM);
        assertThat(redirect).isEqualTo("redirect:/dashboard/store/invoicing");
        assertThat(async).isEqualTo("store-invoicing-system :: systemForm");
        assertThat(model.getAttribute("redirectTo")).isEqualTo("/dashboard/store/invoicing");
    }

    @Test
    void anEmptySecretOfTheCurrentProviderKeepsTheStoredOne() {
        // given
        Store store = configuredStore();

        // when
        controller.saveSystem(system(Map.of(SYSTEM + ".domain", "nowa.fakturownia.pl", SYSTEM + ".apiToken", "")),
                null, new ExtendedModelMap(), PL, new RedirectAttributesModelMap(), new MockHttpServletRequest(),
                new MockHttpServletResponse());

        // then -- the configuration manager merges the stored secret back in for a key left out of the map
        verify(invoicingProviderFactory).saveConfiguration(store, SYSTEM, Map.of("domain", "nowa.fakturownia.pl"));
    }

    @Test
    void aStoredSystemWhoseAdapterIsGoneGetsAnEmptyChoiceInsteadOfTheFirstSystem() {
        // given
        store("store-1").setConfigurationValue(IntegrationType.INVOICING_PROVIDER, "retired");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.system(model, PL);

        // then
        assertThat(model.getAttribute("providerKnown")).isEqualTo(false);
    }

    @Test
    void superAdminSavesTheSystemOfTheStoreInThePath() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        Store store = store("store-2");

        // when
        String view = controller.superAdminSaveSystem("store-2", system(Map.of(SYSTEM + ".domain", "x.fakturownia.pl", SYSTEM + ".apiToken", "t")),
                null, new ExtendedModelMap(), PL, new RedirectAttributesModelMap(), new MockHttpServletRequest(),
                new MockHttpServletResponse());

        // then
        assertThat(store.getConfigurationValue(IntegrationType.INVOICING_PROVIDER)).isEqualTo(SYSTEM);
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-2/invoicing");
    }

    @Test
    void disconnectingDeletesTheSettingsAndTheProvider() {
        // given
        Store store = configuredStore();

        // when
        String view = controller.disconnect(PL, new RedirectAttributesModelMap());

        // then
        verify(invoicingProviderFactory).deleteConfiguration(store, SYSTEM);
        verify(storesRepository).save(store);
        assertThat(store.getConfigurationValue(IntegrationType.INVOICING_PROVIDER)).isNull();
        assertThat(view).isEqualTo("redirect:/dashboard/store/invoicing");
    }

    private static MockHttpServletRequest requestWithFlash() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
        request.setAttribute(DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE, new SessionFlashMapManager());
        return request;
    }

    private Store configuredStore() {
        Store store = store("store-1");
        store.setConfigurationValue(IntegrationType.INVOICING_PROVIDER, SYSTEM);
        when(invoicingProviderFactory.loadConfigurationForUI(store)).thenReturn(Map.of("domain", "firma.fakturownia.pl", "apiToken", ""));
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

    private void authenticateAs(String storeId, String role) {
        Map<String, String> attributes = storeId != null
                ? Map.of("storeId", storeId, "role", role)
                : Map.of("role", role);
        CustomUser user = new CustomUser(null, null, attributes);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
