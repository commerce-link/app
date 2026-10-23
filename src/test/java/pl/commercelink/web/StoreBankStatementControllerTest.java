package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.BankStatementFormatForm;
import pl.commercelink.web.settings.StatementPreview;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreBankStatementControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final String SAMPLE = "Bank;\n;\n#Data operacji;#Opis;#Kwota;\n2026-10-07;\"zakupy\";159,00;\n2026-10-08;\"oplata\";-9,00;\n";

    @Mock StoresRepository storesRepository;
    @Mock PaymentGateways paymentGateways;
    @Spy ResourceBundleMessageSource messageSource = messages();
    @InjectMocks StoreBankStatementController controller;

    @Test
    void opensWithTheStoredFormatOrTheDefaults() {
        try (MockedStatic<CustomSecurityContext> security = admin()) {
            // given
            when(storesRepository.findById("store-1")).thenReturn(store());
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = controller.show("account-1", model, PL);

            // then
            assertThat(view).isEqualTo("store-bank-statement");
            assertThat(((BankStatementFormatForm) model.get("form")).getEncoding()).isEqualTo("windows-1250");
            assertThat(model.get("formAction")).isEqualTo("/dashboard/store/payments/bank-accounts/account-1/statement");
            assertThat(model.get("preview")).isNull();
        }
    }

    @Test
    void aFirstSampleFillsTheFormInAndIsPreviewed() {
        try (MockedStatic<CustomSecurityContext> security = admin()) {
            // given
            when(storesRepository.findById("store-1")).thenReturn(store());
            ExtendedModelMap model = new ExtendedModelMap();
            MockMultipartFile sample = new MockMultipartFile("sample", "lista.csv", "text/csv",
                    SAMPLE.getBytes(Charset.forName("windows-1250")));

            // when
            String view = controller.submit("account-1", BankStatementFormatForm.defaults(), sample, "preview", model, PL,
                    new RedirectAttributesModelMap());

            // then
            assertThat(view).isEqualTo("store-bank-statement");
            BankStatementFormatForm form = (BankStatementFormatForm) model.get("form");
            assertThat(form.getHeaderRow()).isEqualTo("3");
            assertThat(form.getDateColumn()).isEqualTo("#Data operacji");
            assertThat(form.getAmountColumn()).isEqualTo("#Kwota");
            assertThat(form.getSampleName()).isEqualTo("lista.csv");
            StatementPreview preview = (StatementPreview) model.get("preview");
            assertThat(preview.error()).isNull();
            assertThat(preview.operations()).isEqualTo(2);
            assertThat(preview.incoming()).isEqualTo(1);
            verify(storesRepository, never()).save(any());
        }
    }

    @Test
    void savesAValidFormat() {
        try (MockedStatic<CustomSecurityContext> security = admin()) {
            // given
            Store store = store();
            when(storesRepository.findById("store-1")).thenReturn(store);
            BankStatementFormatForm form = BankStatementFormatForm.defaults();
            form.setHeaderRow("3");
            form.setDateColumn("#Data operacji");
            form.setAmountColumn("#Kwota");
            form.setTitleColumn1("#Opis");
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

            // when
            String view = controller.submit("account-1", form, null, "save", new ExtendedModelMap(), PL, redirect);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/store/payments");
            BankAccount account = store.getBankAccounts().get(0);
            assertThat(account.hasStatementFormat()).isTrue();
            assertThat(account.getStatementFormat().getTitleColumns()).containsExactly("#Opis");
            verify(storesRepository).save(store);
        }
    }

    @Test
    void showsWhatIsMissingInsteadOfSaving() {
        try (MockedStatic<CustomSecurityContext> security = admin()) {
            // given
            when(storesRepository.findById("store-1")).thenReturn(store());
            ExtendedModelMap model = new ExtendedModelMap();

            // when
            String view = controller.submit("account-1", BankStatementFormatForm.defaults(), null, "save", model, PL,
                    new RedirectAttributesModelMap());

            // then
            assertThat(view).isEqualTo("store-bank-statement");
            @SuppressWarnings("unchecked") Map<String, String> errors = (Map<String, String>) model.get("errors");
            assertThat(errors).containsKeys("dateColumn", "amountColumn", "titleColumn1");
            verify(storesRepository, never()).save(any());
        }
    }

    private static MockedStatic<CustomSecurityContext> admin() {
        MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class);
        security.when(CustomSecurityContext::getStoreId).thenReturn("store-1");
        security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);
        return security;
    }

    private static Store store() {
        Store store = new Store();
        store.setStoreId("store-1");
        BankAccount account = new BankAccount();
        account.setId("account-1");
        account.setBankName("mBank");
        account.setIban("PL61114020040000340276554567");
        store.setBankAccounts(new ArrayList<>(List.of(account)));
        return store;
    }

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return messages;
    }
}
