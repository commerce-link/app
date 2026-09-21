package pl.commercelink.web.settings;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.Iban;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A bank account summarised for the list on the invoicing page: titled by the bank (or the holder when the bank is not
 * given), the number in groups of four underneath. {@code ibanValid} is false for a number saved before the check-digit
 * rule, which customers would copy into a failing transfer; the row says so instead of looking healthy.
 */
public record BankAccountView(String id, String title, String iban, String detailLine, boolean isDefault,
                              boolean ibanValid, String editHref, String defaultHref, String deleteHref) {

    public static BankAccountView of(BankAccount account, String accountsPath) {
        String title = StringUtils.isNotBlank(account.getBankName()) ? account.getBankName() : account.getAccountHolder();
        String holder = StringUtils.isNotBlank(account.getBankName()) ? account.getAccountHolder() : null;
        String swift = StringUtils.isNotBlank(account.getSwiftCode()) ? "SWIFT " + account.getSwiftCode() : null;
        String detailLine = Stream.of(holder, account.getCurrency(), swift)
                .filter(Objects::nonNull).filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(" · "));
        String base = accountsPath + "/" + account.getId();
        return new BankAccountView(account.getId(), title, Iban.grouped(account.getIban()),
                detailLine.isEmpty() ? null : detailLine, account.is_default(),
                Iban.isValid(Iban.normalize(account.getIban())), base, base + "/default", base + "/delete");
    }
}
