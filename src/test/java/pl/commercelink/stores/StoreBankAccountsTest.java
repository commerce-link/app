package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoreBankAccountsTest {

    private static BankAccount account(String iban) {
        BankAccount account = new BankAccount();
        account.setIban(iban);
        return account;
    }

    @Test
    void theFirstAccountBecomesTheDefaultBecauseOnlyTheDefaultOneIsRead() {
        // given
        Store store = new Store();

        // when
        store.addBankAccount(account("PL1"), false);
        store.addBankAccount(account("PL2"), false);

        // then
        assertThat(store.getDefaultBankAccount().getIban()).isEqualTo("PL1");
    }

    @Test
    void addingAsDefaultMovesTheFlag() {
        // given
        Store store = new Store();
        store.addBankAccount(account("PL1"), false);

        // when
        store.addBankAccount(account("PL2"), true);

        // then
        assertThat(store.getDefaultBankAccount().getIban()).isEqualTo("PL2");
        assertThat(store.getBankAccounts()).filteredOn(BankAccount::is_default).hasSize(1);
    }

    @Test
    void makingAnotherAccountDefaultKeepsExactlyOneDefault() {
        // given
        Store store = new Store();
        store.addBankAccount(account("PL1"), false);
        BankAccount second = account("PL2");
        store.addBankAccount(second, false);

        // when
        boolean changed = store.makeDefaultBankAccount(second.getId());

        // then
        assertThat(changed).isTrue();
        assertThat(store.getDefaultBankAccount()).isSameAs(second);
        assertThat(store.makeDefaultBankAccount("unknown")).isFalse();
    }

    @Test
    void removingTheDefaultHandsTheFlagToTheFirstRemainingAccount() {
        // given
        Store store = new Store();
        BankAccount first = account("PL1");
        store.addBankAccount(first, false);
        store.addBankAccount(account("PL2"), false);

        // when
        boolean removed = store.removeBankAccount(first.getId());

        // then
        assertThat(removed).isTrue();
        assertThat(store.getDefaultBankAccount().getIban()).isEqualTo("PL2");
        assertThat(store.findBankAccount(first.getId())).isEmpty();
    }
}
