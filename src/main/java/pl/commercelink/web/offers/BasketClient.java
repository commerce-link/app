package pl.commercelink.web.offers;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.baskets.Basket;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Who a store basket belongs to. The shop fills billing and shipping details at checkout and never the contact
 * (that is the offer's), so the contact is read first for offers turned into baskets, then billing, then shipping.
 */
final class BasketClient {

    private record Person(String person, String company) {
        boolean isEmpty() {
            return person == null && company == null;
        }
    }

    private BasketClient() {
    }

    /** Company, else "name surname"; null for a guest. The list's client column. */
    static String shortName(Basket basket) {
        Person p = first(basket);
        return p == null ? null : (p.company() != null ? p.company() : p.person());
    }

    /** "name surname · company" (either part may be missing); null for a guest. The page header. */
    static String fullName(Basket basket) {
        Person p = first(basket);
        if (p == null) {
            return null;
        }
        return Stream.of(p.person(), p.company()).filter(StringUtils::isNotBlank).collect(Collectors.joining(" · "));
    }

    private static Person first(Basket basket) {
        Stream<Person> people = Stream.of(
                basket.getContactDetails() == null ? null
                        : of(basket.getContactDetails().getName(), basket.getContactDetails().getSurname(), basket.getContactDetails().getCompanyName()),
                basket.getBillingDetails() == null ? null
                        : of(basket.getBillingDetails().getName(), basket.getBillingDetails().getSurname(), basket.getBillingDetails().getCompanyName()),
                basket.getShippingDetails() == null ? null
                        : of(basket.getShippingDetails().getName(), basket.getShippingDetails().getSurname(), basket.getShippingDetails().getCompanyName()));
        return people.filter(p -> p != null && !p.isEmpty()).findFirst().orElse(null);
    }

    private static Person of(String name, String surname, String company) {
        String person = StringUtils.trimToNull(StringUtils.trimToEmpty(name) + " " + StringUtils.trimToEmpty(surname));
        return new Person(person, StringUtils.trimToNull(company));
    }
}
