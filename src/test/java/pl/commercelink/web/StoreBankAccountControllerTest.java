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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.BankAccountForm;
import pl.commercelink.web.settings.ConfirmAction;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreBankAccountControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final String IBAN = "PL61 1090 1014 0000 0712 1981 2874";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private StoreBankAccountController controller;

    @BeforeEach
    void loggedInAsStoreAdmin() {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(call -> call.getArgument(0));
        authenticateAs("store-1", "ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theFirstAccountIsStoredCompactOnTheSessionStoreAndBecomesTheDefault() {
        // given
        Store store = store("store-1");

        // when
        String view = controller.createAccount(form(IBAN), null, new ExtendedModelMap(), PL, new RedirectAttributesModelMap(),
                new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        verify(storesRepository).save(store);
        assertThat(store.getDefaultBankAccount().getIban()).isEqualTo("PL61109010140000071219812874");
        assertThat(view).isEqualTo("redirect:/dashboard/store/payments");
    }

    /** The old table dropped an incomplete account without a word; now the field says what is wrong. */
    @Test
    void aMistypedIbanIsRejectedAtTheFieldWithoutSaving() {
        // given
        store("store-1");
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.createAccount(form("PL61 1090 1014 0000 0712 1981 2875"), "fetch", model, PL,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), response);

        // then
        verify(storesRepository, never()).save(any(Store.class));
        assertThat(view).isEqualTo("store-bank-account :: accountForm");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(model.getAttribute("errors")).isEqualTo(Map.of("iban", "bank.account.iban.invalid"));
    }

    @Test
    void superAdminAddsToTheStoreFromThePathAndReturnsToIt() {
        // given
        authenticateAs(null, "SUPER_ADMIN");
        Store store = store("store-2");

        // when
        String view = controller.superAdminCreateAccount("store-2", form(IBAN), null, new ExtendedModelMap(), PL,
                new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse());

        // then
        assertThat(store.getBankAccounts()).hasSize(1);
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-2/payments");
    }

    @Test
    void anAccountOfAnotherStoreAnswers404OnEveryAction() {
        // given
        store("store-1");
        List<Consumer<String>> calls = List.of(
                id -> controller.editAccount(id, new ExtendedModelMap(), PL),
                id -> controller.updateAccount(id, form(IBAN), null, new ExtendedModelMap(), PL,
                        new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse()),
                id -> controller.makeDefault(id, PL, new RedirectAttributesModelMap()),
                id -> controller.confirmDelete(id, new ExtendedModelMap(), PL));

        // when / then
        calls.forEach(call -> assertThatThrownBy(() -> call.accept("foreign")).isInstanceOf(ResponseStatusException.class));
        verify(storesRepository, never()).save(any(Store.class));
    }

    @Test
    void editingKeepsTheIdAndMakingAnotherDefaultMovesTheFlag() {
        // given
        Store store = store("store-1");
        BankAccount first = account(store, "PL61109010140000071219812874");
        BankAccount second = account(store, "DE89370400440532013000");
        BankAccountForm edit = form(IBAN);
        edit.setBankName("Nowy bank");

        // when
        controller.updateAccount(first.getId(), edit, null, new ExtendedModelMap(), PL, new RedirectAttributesModelMap(),
                new MockHttpServletRequest(), new MockHttpServletResponse());
        controller.makeDefault(second.getId(), PL, new RedirectAttributesModelMap());

        // then
        assertThat(store.findBankAccount(first.getId())).get().extracting(BankAccount::getBankName).isEqualTo("Nowy bank");
        assertThat(store.getDefaultBankAccount()).isSameAs(second);
    }

    @Test
    void deletingTheOnlyAccountWarnsAboutTransfersAndCashOnDelivery() {
        // given
        Store store = store("store-1");
        BankAccount only = account(store, "PL61109010140000071219812874");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.confirmDelete(only.getId(), model, PL);
        controller.deleteAccount(only.getId(), PL, new RedirectAttributesModelMap());

        // then
        assertThat(((ConfirmAction) model.getAttribute("confirm")).message()).isEqualTo("store.payments.account.delete.message.last");
        assertThat(store.getBankAccounts()).isEmpty();
    }

    private Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        when(storesRepository.findById(storeId)).thenReturn(store);
        return store;
    }

    private static BankAccount account(Store store, String iban) {
        BankAccount account = new BankAccount();
        account.setIban(iban);
        account.setAccountHolder("Sklep");
        account.setCurrency("PLN");
        store.addBankAccount(account, false);
        return account;
    }

    private static BankAccountForm form(String iban) {
        BankAccountForm form = BankAccountForm.empty();
        form.setIban(iban);
        form.setAccountHolder("Sklep Demo sp. z o.o.");
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
