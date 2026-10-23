package pl.commercelink.web.publicpage;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.stores.BrandPalette;
import pl.commercelink.stores.Branding;
import pl.commercelink.stores.Store;

import java.util.Locale;
import java.util.stream.Stream;

public final class PublicPageFactory {

    private static final String POLISH = "pl";
    private static final String ENGLISH = "en";

    private PublicPageFactory() {
    }

    public static PublicPage of(Store store, String path, Locale locale) {
        Branding branding = store.getBranding() != null ? store.getBranding() : new Branding();
        BillingDetails billing = store.getBillingDetails() != null ? store.getBillingDetails() : new BillingDetails();

        String language = languageOf(locale);
        String other = ENGLISH.equals(language) ? POLISH : ENGLISH;
        return new PublicPage(
                store.getStoreId(),
                StringUtils.defaultString(store.getName()),
                logoUrl(store.getStoreId(), branding),
                BrandPalette.of(branding.getSafePrimaryColor()),
                blankToNull(billing.getCompanyName()),
                blankToNull(billing.getTaxId()),
                address(billing),
                blankToNull(store.getClientContactEmail()),
                blankToNull(billing.getPhone()),
                language,
                other,
                path + "?lang=" + other);
    }

    public static PublicPage unknownStore(String path, Locale locale) {
        String language = languageOf(locale);
        String other = ENGLISH.equals(language) ? POLISH : ENGLISH;
        return new PublicPage(null, "", null, BrandPalette.of(null), null, null, null, null, null,
                language, other, path + "?lang=" + other);
    }

    private static String languageOf(Locale locale) {
        return locale != null && ENGLISH.equals(locale.getLanguage()) ? ENGLISH : POLISH;
    }

    private static String logoUrl(String storeId, Branding branding) {
        if (branding.getLogo() == null) {
            return null;
        }
        return "/StoreLogo/" + storeId + (branding.getLogoVersion() != null ? "?v=" + branding.getLogoVersion() : "");
    }

    private static String address(BillingDetails billing) {
        String postalAndCity = Stream.of(billing.getPostalCode(), billing.getCity())
                .filter(StringUtils::isNotBlank).map(String::trim).reduce((a, b) -> a + " " + b).orElse(null);
        return Stream.of(billing.getStreetAndNumber(), postalAndCity)
                .filter(StringUtils::isNotBlank).map(String::trim).reduce((a, b) -> a + ", " + b).orElse(null);
    }

    private static String blankToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }
}
