package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.Iban;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A bank account of the store. Only the default one is read: customers see it as the transfer details of an offer and
 * an order, and the carrier pays cash on delivery into it. The IBAN, holder and currency are required; the bank name
 * and SWIFT are optional because the customer order view already shows them only when present, except that a
 * foreign account needs its SWIFT for a transfer from Poland.
 */
@Getter
@Setter
public class BankAccountForm {

    private static final Pattern SWIFT = Pattern.compile("[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?");
    private static final String POLAND = "PL";

    private String bankName;
    private String iban;
    private String accountHolder;
    private String swiftCode;
    private String currency;
    private boolean makeDefault;

    public static BankAccountForm empty() {
        BankAccountForm form = new BankAccountForm();
        form.currency = CurrencyOptions.POLISH_ZLOTY;
        return form;
    }

    public static BankAccountForm from(BankAccount account) {
        BankAccountForm form = new BankAccountForm();
        form.bankName = account.getBankName();
        form.iban = Iban.grouped(account.getIban());
        form.accountHolder = account.getAccountHolder();
        form.swiftCode = account.getSwiftCode();
        form.currency = StringUtils.isNotBlank(account.getCurrency()) ? account.getCurrency() : CurrencyOptions.POLISH_ZLOTY;
        form.makeDefault = account.is_default();
        return form;
    }

    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        String normalizedIban = Iban.normalize(iban);
        if (FormRules.requireText(errors, "iban", iban, "bank.account.iban.required") && !Iban.isValid(normalizedIban)) {
            errors.put("iban", "bank.account.iban.invalid");
        }
        FormRules.requireText(errors, "accountHolder", accountHolder, "bank.account.holder.required");
        String swift = normalizedSwift();
        if (swift != null && !SWIFT.matcher(swift).matches()) {
            errors.put("swiftCode", "bank.account.swift.invalid");
        } else if (swift == null && Iban.isValid(normalizedIban) && !POLAND.equals(Iban.countryCode(normalizedIban))) {
            errors.put("swiftCode", "bank.account.swift.required");
        }
        FormRules.requireText(errors, "currency", currency, "bank.account.currency.required");
        return errors;
    }

    public BankAccount toNewBankAccount() {
        BankAccount account = new BankAccount();
        account.set_default(false);
        applyTo(account);
        return account;
    }

    /** Edits the account in place; its id and default flag stay as they were. */
    public void applyTo(BankAccount account) {
        account.setBankName(StringUtils.trimToNull(bankName));
        account.setIban(Iban.normalize(iban));
        account.setAccountHolder(StringUtils.trimToNull(accountHolder));
        account.setSwiftCode(normalizedSwift());
        account.setCurrency(StringUtils.trimToNull(currency));
    }

    private String normalizedSwift() {
        return StringUtils.isBlank(swiftCode) ? null : swiftCode.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }
}
