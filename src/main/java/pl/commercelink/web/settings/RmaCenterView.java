package pl.commercelink.web.settings;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.rma.RMACenter;
import pl.commercelink.web.dtos.CountryOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One RMA centre summarised for the list. The title is the supplier's display label, because that is what the centre is
 * looked up by when goods are shipped back; the address is the payload underneath. {@code shared} marks a centre kept
 * for every store, which a store admin sees but cannot change; {@code knownProvider} is false once a supplier leaves the
 * registry, so a centre no shipment can reach any more says so instead of looking healthy. {@code lastForProvider}
 * drives the warning in the delete dialog: removing the only centre of a supplier leaves its shipments with no
 * destination address.
 */
public record RmaCenterView(String id, String title, boolean shared, boolean knownProvider, boolean lastForProvider,
                            String summary, String contactLine, List<String> missingFieldKeys, String editHref,
                            String deleteHref) {

    /**
     * @param title the supplier label, or the wording for a centre whose supplier was never set — resolved by the
     *              caller, so the template never has to choose between the two
     */
    public static RmaCenterView of(RMACenter center, String title, boolean shared, boolean knownProvider,
                                   boolean lastForProvider, Locale locale, String basePath) {
        ShippingDetails details = center.getShippingDetails();
        String base = basePath + "/" + center.getRmaCenterId();
        String address = join(" · ", streetOf(details), place(details), country(details, locale));
        return new RmaCenterView(center.getRmaCenterId(), title, shared, knownProvider, lastForProvider,
                join(" · ", companyName(details), address),
                details == null ? null : join(" · ", details.getEmail(), details.getPhone()),
                missingFieldKeys(center), base, base + "/delete");
    }

    public boolean complete() {
        return missingFieldKeys.isEmpty();
    }

    /** The fields {@link pl.commercelink.web.dtos.RmaCenterForm} requires; the email is the only optional one. */
    private static List<String> missingFieldKeys(RMACenter center) {
        List<String> missing = new ArrayList<>();
        addIfBlank(missing, center.getProvider(), "rma.center.missing.provider");
        ShippingDetails details = center.getShippingDetails();
        addIfBlank(missing, companyName(details), "rma.center.missing.name");
        addIfBlank(missing, streetOf(details), "rma.center.missing.street");
        addIfBlank(missing, details == null ? null : details.getPostalCode(), "rma.center.missing.postalCode");
        addIfBlank(missing, details == null ? null : details.getCity(), "rma.center.missing.city");
        addIfBlank(missing, details == null ? null : details.getCountry(), "rma.center.missing.country");
        addIfBlank(missing, details == null ? null : details.getPhone(), "rma.center.missing.phone");
        return missing;
    }

    private static String companyName(ShippingDetails details) {
        if (details == null) {
            return null;
        }
        return StringUtils.isNotBlank(details.getCompanyName()) ? details.getCompanyName() : details.getFullName();
    }

    private static String streetOf(ShippingDetails details) {
        return details == null ? null : details.getStreetAndNumber();
    }

    private static String place(ShippingDetails details) {
        return details == null ? null : join(" ", details.getPostalCode(), details.getCity());
    }

    private static String country(ShippingDetails details, Locale locale) {
        return details == null ? null : CountryOptions.displayName(details.getCountry(), locale);
    }

    private static void addIfBlank(List<String> missing, String value, String key) {
        if (StringUtils.isBlank(value)) {
            missing.add(key);
        }
    }

    private static String join(String separator, String... parts) {
        String joined = Stream.of(parts).filter(Objects::nonNull).filter(StringUtils::isNotBlank).map(String::trim)
                .collect(Collectors.joining(separator));
        return joined.isEmpty() ? null : joined;
    }
}
