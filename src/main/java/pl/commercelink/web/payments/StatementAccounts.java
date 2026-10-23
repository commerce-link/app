package pl.commercelink.web.payments;

import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.Iban;
import pl.commercelink.stores.Store;
import pl.commercelink.web.dtos.PickerOption;
import pl.commercelink.web.settings.BankAccountView;

import java.util.List;

/** The store's accounts whose statements can be uploaded: the ones with a statement format, labelled for a picker. */
public final class StatementAccounts {

    private StatementAccounts() {
    }

    public static List<PickerOption> of(Store store) {
        if (store == null) {
            return List.of();
        }
        return store.getBankAccounts().stream()
                .filter(BankAccount::hasStatementFormat)
                .map(account -> new PickerOption(account.getId(), label(account)))
                .toList();
    }

    private static String label(BankAccount account) {
        String iban = Iban.normalize(account.getIban());
        String ending = iban == null || iban.length() < 4 ? "" : " · \u2026" + iban.substring(iban.length() - 4);
        return BankAccountView.of(account, "").title() + ending;
    }
}
