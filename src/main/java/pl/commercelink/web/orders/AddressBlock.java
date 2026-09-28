package pl.commercelink.web.orders;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.web.dtos.CountryOptions;

import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

public record AddressBlock(String name, String company, String taxId, String street, String cityLine, String country,
                           String email, String phone) {

    public static AddressBlock of(BillingDetails details, Locale locale) {
        if (details == null) {
            return empty();
        }
        return build(details.getName(), details.getSurname(), details.getCompanyName(), details.getTaxId(),
                details.getStreetAndNumber(), details.getPostalCode(), details.getCity(), details.getCountry(),
                details.getEmail(), details.getPhone(), locale);
    }

    public static AddressBlock of(ShippingDetails details, Locale locale) {
        if (details == null) {
            return empty();
        }
        return build(details.getName(), details.getSurname(), details.getCompanyName(), null,
                details.getStreetAndNumber(), details.getPostalCode(), details.getCity(), details.getCountry(),
                details.getEmail(), details.getPhone(), locale);
    }

    private static AddressBlock build(String name, String surname, String company, String taxId, String street,
                                      String postalCode, String city, String country, String email, String phone,
                                      Locale locale) {
        String person = StringUtils.trimToNull((Objects.toString(name, "") + " " + Objects.toString(surname, "")).trim());
        String cityLine = StringUtils.trimToNull((Objects.toString(postalCode, "") + " " + Objects.toString(city, "")).trim());
        return new AddressBlock(person, StringUtils.trimToNull(company), StringUtils.trimToNull(taxId),
                StringUtils.trimToNull(street), cityLine, CountryOptions.displayName(StringUtils.trimToNull(country), locale),
                StringUtils.trimToNull(email), StringUtils.trimToNull(phone));
    }

    private static AddressBlock empty() {
        return new AddressBlock(null, null, null, null, null, null, null, null);
    }

    /**
     * The shipping block reads "same as billing" when the parcel goes where the invoice does; the recipient's own
     * e-mail and phone are compared separately (CustomerView), so they stay visible under that line.
     */
    public boolean sameAs(AddressBlock other) {
        return Objects.equals(name, other.name) && Objects.equals(company, other.company)
                && Objects.equals(street, other.street) && Objects.equals(cityLine, other.cityLine)
                && Objects.equals(country, other.country);
    }

    /** The name the orders list shows for this address: the company, else the person. */
    public String companyOrPerson() {
        return company != null ? company : name;
    }

    public boolean isEmpty() {
        return Stream.of(name, company, street, cityLine, email, phone).allMatch(Objects::isNull);
    }
}
