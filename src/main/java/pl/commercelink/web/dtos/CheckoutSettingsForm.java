package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.stores.CheckoutConfiguration;
import pl.commercelink.stores.Store;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Settings › Payments › Checkout: where the gateway sends the customer back and what the online-store checkout accepts.
 * The currency is limited to the złoty because offers print their prices in PLN; a store that saved another currency
 * before keeps it as a choice until it switches, so saving the rest of the form does not change what customers are
 * charged in. The number of accepted pricelists is read as text, so an empty value gets a message at the field instead
 * of a binding error page.
 */
@Getter
@Setter
public class CheckoutSettingsForm {

    public static final String POLISH_ZLOTY = "pln";
    static final int MAX_ACCEPTED_PRICELISTS = 50;

    private String successUrl;
    private String cancelUrl;
    private String currency;
    private String acceptedPricelists;

    public static CheckoutSettingsForm from(CheckoutConfiguration configuration) {
        CheckoutConfiguration source = configuration != null ? configuration : new CheckoutConfiguration();
        CheckoutSettingsForm form = new CheckoutSettingsForm();
        form.successUrl = source.getSuccessUrl();
        form.cancelUrl = source.getCancelUrl();
        form.currency = StringUtils.isNotBlank(source.getCurrency()) ? source.getCurrency().toLowerCase(Locale.ROOT) : POLISH_ZLOTY;
        form.acceptedPricelists = String.valueOf(source.getNumberOfAcceptedPricelists());
        return form;
    }

    /** @param storedCurrency the currency saved for the store, accepted besides the złoty */
    public Map<String, String> validate(String storedCurrency) {
        Map<String, String> errors = new LinkedHashMap<>();
        validateUrl(errors, "successUrl", successUrl);
        validateUrl(errors, "cancelUrl", cancelUrl);
        String chosen = StringUtils.lowerCase(StringUtils.trimToNull(currency), Locale.ROOT);
        if (!POLISH_ZLOTY.equals(chosen) && (chosen == null || !chosen.equalsIgnoreCase(StringUtils.trim(storedCurrency)))) {
            errors.put("currency", "store.payments.checkout.currency.invalid");
        }
        if (FormRules.requireText(errors, "acceptedPricelists", acceptedPricelists, "store.payments.checkout.pricelists.required")
                && acceptedPricelistCount() == null) {
            errors.put("acceptedPricelists", "store.payments.checkout.pricelists.invalid");
        }
        return errors;
    }

    /** Keeps the delivery options: they are edited on their own pages. */
    public void applyTo(Store store) {
        CheckoutConfiguration configuration = Objects.requireNonNullElseGet(store.getCheckoutConfiguration(), CheckoutConfiguration::new);
        configuration.setSuccessUrl(successUrl.trim());
        configuration.setCancelUrl(cancelUrl.trim());
        configuration.setCurrency(currency.trim().toLowerCase(Locale.ROOT));
        configuration.setNumberOfAcceptedPricelists(acceptedPricelistCount());
        store.setCheckoutConfiguration(configuration);
    }

    /** A customer cannot reach an address on the admin's own machine: the default of a new store points there. */
    public static boolean pointsToLocalMachine(String url) {
        String host = host(url);
        return host != null && (host.equalsIgnoreCase("localhost") || host.startsWith("127.") || host.equals("[::1]"));
    }

    private static void validateUrl(Map<String, String> errors, String field, String value) {
        if (FormRules.requireText(errors, field, value, "store.payments.checkout.url.required") && host(value) == null) {
            errors.put(field, "store.payments.checkout.url.invalid");
        }
    }

    /** The host of an http(s) address, or null when the value is not one. */
    private static String host(String url) {
        String value = StringUtils.trimToEmpty(url);
        if (!value.startsWith("https://") && !value.startsWith("http://")) {
            return null;
        }
        try {
            return URI.create(value).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Integer acceptedPricelistCount() {
        String value = StringUtils.trimToEmpty(acceptedPricelists);
        if (!value.matches("\\d{1,2}")) {
            return null;
        }
        int count = Integer.parseInt(value);
        return count >= 1 && count <= MAX_ACCEPTED_PRICELISTS ? count : null;
    }
}
