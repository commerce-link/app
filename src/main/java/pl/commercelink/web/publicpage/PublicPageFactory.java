package pl.commercelink.web.publicpage;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.stores.BrandPalette;
import pl.commercelink.stores.Branding;
import pl.commercelink.stores.Store;

import java.net.URLEncoder;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class PublicPageFactory {

    private static final String POLISH = "pl";
    private static final String ENGLISH = "en";

    private PublicPageFactory() {
    }

    public static PublicPage of(Store store, String path, Locale locale) {
        return of(store, path, Map.of(), locale);
    }

    public static PublicPage of(Store store, String path, Map<String, String> keptParameters, Locale locale) {
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
                otherLanguageHref(path, keptParameters, other));
    }

    public static PublicPage unknownStore(String path, Locale locale) {
        String language = languageOf(locale);
        String other = ENGLISH.equals(language) ? POLISH : ENGLISH;
        return new PublicPage(null, "", null, BrandPalette.of(null), null, null, null, null, null,
                language, other, path + "?lang=" + other);
    }

    // Blank values are dropped so an unset step parameter does not leak into the link as an empty token.
    private static String otherLanguageHref(String path, Map<String, String> keptParameters, String other) {
        StringBuilder href = new StringBuilder(path).append('?');
        keptParameters.forEach((key, value) -> {
            if (StringUtils.isNotBlank(value)) {
                href.append(key).append('=').append(URLEncoder.encode(value, UTF_8)).append('&');
            }
        });
        return href.append("lang=").append(other).toString();
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
