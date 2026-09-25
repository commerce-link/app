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
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.receipts.FakeReceiptProviderDescriptor;
import pl.commercelink.receipts.ReceiptAttemptStore;
import pl.commercelink.receipts.ReceiptProviderFactory;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.ReceiptSettingsForm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreReceiptsSettingsControllerTest {

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

    private StoreReceiptsSettingsController controller;

    @BeforeEach
    void loggedInAsStoreAdmin() {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(call -> call.getArgument(0));
        FakeReceiptProviderDescriptor descriptor = new FakeReceiptProviderDescriptor();
        when(receiptProviderFactory.getDescriptor(SYSTEM)).thenReturn(descriptor);
        when(receiptProviderFactory.availableProviders()).thenReturn(List.of(descriptor));
        when(receiptProviderFactory.loadConfigurationForUI(any())).thenReturn(Map.of());
        controller = new StoreReceiptsSettingsController(storesRepository,
                new ReceiptSystems(receiptProviderFactory, attempts, API_DOMAIN), messageSource);
        authenticateAs("store-1", "ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void showsTheDefaultFormForAStoreThatNeverConfiguredReceipts() {
        // given
        store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.receipts(model);

        // then
        assertThat(view).isEqualTo("store-receipts");
        ReceiptSettingsForm form = (ReceiptSettingsForm) model.getAttribute("receiptsForm");
        assertThat(form.isEnabled()).isFalse();
    }

    @Test
    void enablingWithoutAConfiguredSystemIsRejectedAndTheStoreIsNotSaved() {
        // given
        Store store = store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.saveReceipts(receipts(true), "fetch", model, PL,
                new RedirectAttributesModelMap(), response);

        // then
        verify(storesRepository, never()).save(any(Store.class));
        assertThat(store.getReceiptConfiguration().isEnabled()).isFalse();
        assertThat(view).isEqualTo("store-receipts :: receiptsForm");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(model.getAttribute("receiptsErrors")).isEqualTo(Map.of("enabled", "store.receipts.enabled.noSystem"));
    }

    @Test
    void enablingWithAConfiguredSystemIsSaved() {
        // given
        Store store = store("store-1");
        store.setConfigurationValue(IntegrationType.RECEIPT_PROVIDER, SYSTEM);
        when(receiptProviderFactory.loadConfigurationForUI(store)).thenReturn(Map.of("token", ""));

        // when
        String view = controller.saveReceipts(receipts(true), null, new ExtendedModelMap(), PL,
                new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getReceiptConfiguration().isEnabled()).isTrue();
        assertThat(store.getReceiptConfiguration().getEnabledAt()).isNotNull();
        assertThat(view).isEqualTo("redirect:/dashboard/store/receipts");
    }

    @Test
    void enabledCheckboxIsDescribedByItsErrorWhenOneIsShown() throws Exception {
        String html = template("store-receipts");

        // the shared check fragment gets enabled-error as its errorId once receiptsErrors['enabled'] is set
        // (SettingsFormCheckFragmentTest pins what the fragment renders from it)
        assertThat(html).contains("receiptsErrors['enabled'] != null ? 'enabled-error' : null");
        assertThat(html).contains("id=\"enabled-error\"");
    }

    private static String template(String name) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/" + name + ".html"), StandardCharsets.UTF_8);
    }

    private Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
    }

    private static ReceiptSettingsForm receipts(boolean enabled) {
        ReceiptSettingsForm form = new ReceiptSettingsForm();
        form.setEnabled(enabled);
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
