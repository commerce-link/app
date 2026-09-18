package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.BankAccount;

import static org.assertj.core.api.Assertions.assertThat;

class BankAccountFormTest {

    private static BankAccountForm form(String iban, String holder, String swift, String currency) {
        BankAccountForm form = BankAccountForm.empty();
        form.setIban(iban);
        form.setAccountHolder(holder);
        form.setSwiftCode(swift);
        form.setCurrency(currency);
        return form;
    }

    @Test
    void aPolishAccountNeedsNoSwiftNorBankName() {
        // when / then
        assertThat(form("PL61 1090 1014 0000 0712 1981 2874", "Sklep Demo sp. z o.o.", null, "PLN").validate()).isEmpty();
    }

    @Test
    void anEmptyFormNamesTheRequiredFields() {
        // given
        BankAccountForm form = new BankAccountForm();

        // when / then
        assertThat(form.validate()).containsOnlyKeys("iban", "accountHolder", "currency");
    }

    @Test
    void aMistypedIbanIsRejectedAtTheField() {
        // when / then
        assertThat(form("PL61 1090 1014 0000 0712 1981 2875", "Sklep", null, "PLN").validate())
                .containsEntry("iban", "bank.account.iban.invalid");
    }

    @Test
    void aForeignAccountNeedsItsSwiftAndAGivenSwiftMustBeWellFormed() {
        // when / then
        assertThat(form("DE89 3704 0044 0532 0130 00", "Shop GmbH", null, "EUR").validate())
                .containsEntry("swiftCode", "bank.account.swift.required");
        assertThat(form("DE89 3704 0044 0532 0130 00", "Shop GmbH", "cobadeff", "EUR").validate()).isEmpty();
        assertThat(form("PL61 1090 1014 0000 0712 1981 2874", "Sklep", "BAD", "PLN").validate())
                .containsEntry("swiftCode", "bank.account.swift.invalid");
    }

    @Test
    void storesTheIbanCompactAndTheSwiftUpperCase() {
        // given
        BankAccountForm form = form("61 1090 1014 0000 0712 1981 2874", " Sklep ", "wbkpplpp", "PLN");

        // when
        BankAccount account = form.toNewBankAccount();

        // then
        assertThat(account.getIban()).isEqualTo("PL61109010140000071219812874");
        assertThat(account.getSwiftCode()).isEqualTo("WBKPPLPP");
        assertThat(account.getAccountHolder()).isEqualTo("Sklep");
        assertThat(account.getBankName()).isNull();
    }

    @Test
    void editingShowsTheIbanInGroups() {
        // given
        BankAccount account = new BankAccount();
        account.setIban("PL61109010140000071219812874");

        // when / then
        assertThat(BankAccountForm.from(account).getIban()).isEqualTo("PL61 1090 1014 0000 0712 1981 2874");
        assertThat(BankAccountForm.from(account).getCurrency()).isEqualTo("PLN");
    }
}
