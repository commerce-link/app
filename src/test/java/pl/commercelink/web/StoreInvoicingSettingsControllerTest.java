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
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.invoicing.api.InvoicingProviderDescriptor;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.IntegrationSettingsForm;
import pl.commercelink.web.dtos.InvoicingSettingsForm;
import pl.commercelink.web.settings.IntegrationStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreInvoicingSettingsControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final String SYSTEM = "fakturownia";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private InvoicingProviderFactory invoicingProviderFactory;
    @Mock
    private MessageSource messageSource;

    private StoreInvoicingSettingsController controller;

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
        controller = new StoreInvoicingSettingsController(storesRepository, new InvoicingSystems(invoicingProviderFactory),
                messageSource);
        authenticateAs("store-1", "ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void savesInvoiceSettingsOnTheStoreFromTheSession() {
        // given
        Store store = store("store-1");

        // when
        String view = controller.saveInvoices(invoices("14"), null, new ExtendedModelMap(), PL,
                new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getInvoicingConfiguration().getPaymentTerms()).isEqualTo(14);
        assertThat(view).isEqualTo("redirect:/dashboard/store/invoicing");
    }

    @Test
    void superAdminSavesOnTheStoreFromThePath() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        Store store = store("store-2");

        // when
        String view = controller.superAdminSaveInvoices("store-2", invoices("7"), null, new ExtendedModelMap(), PL,
                new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-2/invoicing");
    }

    @Test
    void anInvalidPaymentTermIsRejectedAtTheFieldWithoutSaving() {
        // given
        store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.saveInvoices(invoices("-1"), "fetch", model, PL, new RedirectAttributesModelMap(), response);

        // then
        verify(storesRepository, never()).save(any(Store.class));
        assertThat(view).isEqualTo("store-invoicing :: invoicesForm");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(model.getAttribute("invoicesErrors")).isEqualTo(Map.of("paymentTerms", "store.invoicing.paymentTerms.invalid"));
    }







    /** The page carries one form: the system is only a status here, changed on its own page. */
    @Test
    void thePageShowsTheSystemAsAStatusWithALinkToItsOwnPage() {
        // given
        Store store = store("store-1");
        store.setConfigurationValue(IntegrationType.INVOICING_PROVIDER, SYSTEM);
        when(invoicingProviderFactory.loadConfigurationForUI(store)).thenReturn(Map.of("domain", "firma.fakturownia.pl", "apiToken", ""));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.invoicing(model);

        // then
        assertThat(view).isEqualTo("store-invoicing");
        assertThat(model.getAttribute("systemStatus")).isEqualTo(new IntegrationStatus(SYSTEM, "Fakturownia", true, true));
        assertThat(model.getAttribute("systemHref")).isEqualTo("/dashboard/store/invoicing/system");
        assertThat(model.containsAttribute("systemForm")).isFalse();
    }

    @Test
    void anUnknownStoreAnswers404() {
        // given
        when(storesRepository.findById("store-1")).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> controller.invoicing(new ExtendedModelMap())).isInstanceOf(ResponseStatusException.class);
    }

    private Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
    }

    private static InvoicingSettingsForm invoices(String paymentTerms) {
        InvoicingSettingsForm form = new InvoicingSettingsForm();
        form.setPaymentTerms(paymentTerms);
        return form;
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
