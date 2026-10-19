package pl.commercelink.web.rma;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.ShippingDetails;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RmaAddressFormTest {

    private static ShippingDetails complete() {
        ShippingDetails d = new ShippingDetails();
        d.setId("addr-1");
        d.set_default(false);
        d.setName("Jan");
        d.setSurname("Kowalski");
        d.setStreetAndNumber("Długa 1");
        d.setPostalCode("00-001");
        d.setCity("Warszawa");
        d.setCountry("PL");
        d.setEmail("jan@example.com");
        d.setPhone("+48 600 700 800");
        return d;
    }

    private static RmaAddressForm posted(ShippingDetails d) {
        return RmaAddressForm.of(d);
    }

    @Test
    void anEmptyAddressReportsEveryRequiredFieldInFormOrder() {
        // when
        Map<String, String> errors = RmaAddressForm.of(null).validate(null);

        // then
        assertThat(errors).containsExactly(
                Map.entry("shippingDetails.name", "order.address.name.or.company.required"),
                Map.entry("shippingDetails.streetAndNumber", "billing.street.required"),
                Map.entry("shippingDetails.postalCode", "billing.postalCode.required"),
                Map.entry("shippingDetails.city", "billing.city.required"),
                Map.entry("shippingDetails.country", "billing.country.required"),
                Map.entry("shippingDetails.email", "billing.email.required"),
                Map.entry("shippingDetails.phone", "billing.phone.required"));
    }

    @Test
    void aCompanyWithoutAPersonIsEnough() {
        // given
        ShippingDetails d = complete();
        d.setName(null);
        d.setSurname(null);
        d.setCompanyName("ACME sp. z o.o.");

        // when
        Map<String, String> errors = posted(d).validate(complete());

        // then
        assertThat(errors).isEmpty();
    }

    @Test
    void requiredFieldsAreCheckedEvenWhenUnchanged() {
        // given: a marketplace address saved without a phone
        ShippingDetails saved = complete();
        saved.setPhone(null);

        // when
        Map<String, String> errors = posted(saved).validate(saved);

        // then
        assertThat(errors).containsExactly(Map.entry("shippingDetails.phone", "billing.phone.required"));
    }

    @Test
    void anUnchangedCountryNameFromAMarketplaceIsNotCheckedForFormat() {
        // given
        ShippingDetails saved = complete();
        saved.setCountry("Polska");
        ShippingDetails edited = complete();
        edited.setCountry("Polska");
        edited.setStreetAndNumber("Krótka 2");

        // when
        Map<String, String> errors = posted(edited).validate(saved);

        // then
        assertThat(errors).isEmpty();
    }

    @Test
    void aChangedFieldIsCheckedForFormat() {
        // given
        ShippingDetails edited = complete();
        edited.setCountry("Polska");
        edited.setEmail("jan-at-example");
        edited.setPhone("12");

        // when
        Map<String, String> errors = posted(edited).validate(complete());

        // then
        assertThat(errors).containsExactly(
                Map.entry("shippingDetails.country", "order.address.country.invalid"),
                Map.entry("shippingDetails.email", "billing.email.invalid"),
                Map.entry("shippingDetails.phone", "billing.phone.invalid"));
    }

    @Test
    void aLowerCaseCountryCodeIsAcceptedAndStoredInCapitals() {
        // given
        ShippingDetails edited = complete();
        edited.setCountry(" de ");

        // when
        Map<String, String> errors = posted(edited).validate(complete());
        ShippingDetails stored = posted(edited).applyTo(complete());

        // then
        assertThat(errors).isEmpty();
        assertThat(stored.getCountry()).isEqualTo("DE");
    }

    @Test
    void applyKeepsTheIdAndDefaultFlagAndTrimsValues() {
        // given
        ShippingDetails edited = complete();
        edited.setId(null);
        edited.setCity("  Kraków ");

        // when
        ShippingDetails stored = posted(edited).applyTo(complete());

        // then
        assertThat(stored.getId()).isEqualTo("addr-1");
        assertThat(stored.is_default()).isFalse();
        assertThat(stored.getCity()).isEqualTo("Kraków");
        assertThat(stored.isProperlyFilled()).isTrue();
    }

    @Test
    void applyKeepsAnUnchangedCountryAsItWas() {
        // given
        ShippingDetails saved = complete();
        saved.setCountry("Polska");

        // when
        ShippingDetails stored = posted(saved).applyTo(saved);

        // then
        assertThat(stored.getCountry()).isEqualTo("Polska");
    }

    @Test
    void applyOnAnRmaWithoutAnAddressCreatesOne() {
        // when
        ShippingDetails stored = posted(complete()).applyTo(null);

        // then
        assertThat(stored.isProperlyFilled()).isTrue();
        assertThat(stored.getCountry()).isEqualTo("PL");
    }

    @Test
    void differsFromComparesTrimmedFields() {
        // given
        ShippingDetails spaced = complete();
        spaced.setCity(" Warszawa ");
        ShippingDetails other = complete();
        other.setCity("Gdańsk");

        // then
        assertThat(posted(complete()).differsFrom(spaced)).isFalse();
        assertThat(posted(complete()).differsFrom(other)).isTrue();
        assertThat(posted(complete()).differsFrom(null)).isTrue();
    }

    @Test
    void hasAnyValueIgnoresTheDefaultCountry() {
        // then
        assertThat(RmaAddressForm.hasAnyValue(null)).isFalse();
        assertThat(RmaAddressForm.hasAnyValue(ShippingDetails._default())).isFalse();
        assertThat(RmaAddressForm.hasAnyValue(complete())).isTrue();
    }

    @Test
    void aSurnameAloneDoesNotNameThePerson() {
        // given: getFullName() would print "null Kowalski" on the customer's page
        ShippingDetails d = complete();
        d.setName(" ");

        // when
        Map<String, String> errors = posted(d).validate(null);

        // then
        assertThat(errors).containsExactly(Map.entry("shippingDetails.name", "order.address.name.or.company.required"));
    }

    @Test
    void aChangedValueLongerThanTheLimitIsRefusedInFormOrder() {
        // given
        ShippingDetails d = complete();
        d.setStreetAndNumber("U".repeat(RmaAddressForm.MAX_LENGTH + 1));
        d.setEmail("e".repeat(RmaAddressForm.MAX_LENGTH) + "@example.com");
        d.setCompanyName("C".repeat(RmaAddressForm.MAX_LENGTH));

        // when
        Map<String, String> errors = posted(d).validate(complete());

        // then
        assertThat(errors).containsExactly(
                Map.entry("shippingDetails.streetAndNumber", "rma.address.too.long"),
                Map.entry("shippingDetails.email", "rma.address.too.long"));
    }

    @Test
    void anUnchangedLongValueFromEarlierDataIsNotRefused() {
        // given
        ShippingDetails saved = complete();
        saved.setStreetAndNumber("U".repeat(200));

        // then
        assertThat(posted(saved).validate(saved)).isEmpty();
    }

    @Test
    void aCountryNameFromTheOrderBecomesItsCode() {
        // then
        assertThat(RmaAddressForm.countryCode("Polska")).isEqualTo("PL");
        assertThat(RmaAddressForm.countryCode(" poland ")).isEqualTo("PL");
        assertThat(RmaAddressForm.countryCode("Niemcy")).isEqualTo("DE");
        assertThat(RmaAddressForm.countryCode("de")).isEqualTo("DE");
        assertThat(RmaAddressForm.countryCode("Atlantyda")).isEqualTo("Atlantyda");
        assertThat(RmaAddressForm.countryCode(null)).isNull();
    }

    @Test
    void differsFromIgnoresTheCaseOfTheCountry() {
        // given
        ShippingDetails lower = complete();
        lower.setCountry("pl");

        // then
        assertThat(posted(lower).differsFrom(complete())).isFalse();
    }

    @Test
    void aChangedValueWithMarkupIsRefusedWhileQuotesAndAmpersandsAreKept() {
        // given
        ShippingDetails d = complete();
        d.setName("<script>alert(1)</script>");
        d.setCompanyName("Kowalski & Syn \"O'Brien\"");
        d.setCity("Łódź >");

        // when
        Map<String, String> errors = posted(d).validate(complete());

        // then
        assertThat(errors).containsExactly(
                Map.entry("shippingDetails.name", "rma.address.no.markup"),
                Map.entry("shippingDetails.city", "rma.address.no.markup"));
    }

    @Test
    void markupSavedEarlierDoesNotBlockASave() {
        // given
        ShippingDetails saved = complete();
        saved.setCompanyName("<b>Firma</b>");

        // then
        assertThat(posted(saved).validate(saved)).isEmpty();
    }
}
