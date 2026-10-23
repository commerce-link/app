package pl.commercelink.web.clientoffer;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.web.dtos.ClientDataDto;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.web.clientoffer.ClientOfferDataForm.COMPANY;
import static pl.commercelink.web.clientoffer.ClientOfferDataForm.OTHER;
import static pl.commercelink.web.clientoffer.ClientOfferDataForm.PERSON;
import static pl.commercelink.web.clientoffer.ClientOfferDataForm.SAME;

class ClientOfferDataFormTest {

    @Test
    void personNeedsNameSurnameAddressEmailAndPhone() {
        // given
        ClientOfferDataForm form = ClientOfferDataForm.of(new ClientDataDto(), PERSON, SAME, false);

        // when
        Map<String, String> errors = form.validate();

        // then
        assertThat(errors).containsExactly(
                Map.entry("billingDetails.name", "client.offer.error.name.required"),
                Map.entry("billingDetails.surname", "client.offer.error.surname.required"),
                Map.entry("billingDetails.streetAndNumber", "client.offer.error.street.required"),
                Map.entry("billingDetails.postalCode", "client.offer.error.postalCode.required"),
                Map.entry("billingDetails.city", "client.offer.error.city.required"),
                Map.entry("billingDetails.country", "client.offer.error.country.required"),
                Map.entry("billingDetails.email", "client.offer.error.email.required"),
                Map.entry("billingDetails.phone", "client.offer.error.phone.required"));
    }

    @Test
    void companyNeedsCompanyNameAndTaxId() {
        // given
        ClientOfferDataForm form = ClientOfferDataForm.of(validDto(), COMPANY, SAME, false);

        // when
        Map<String, String> errors = form.validate();

        // then
        assertThat(errors).containsExactly(
                Map.entry("billingDetails.companyName", "client.offer.error.company.required"),
                Map.entry("billingDetails.taxId", "client.offer.error.taxId.required"));
    }

    @Test
    void polishTaxIdIsTenDigitsIgnoringSeparatorsAndPrefix() {
        // given
        ClientDataDto valid = companyDto("PL 525-281-44-10", "PL");
        ClientDataDto tooShort = companyDto("525-281-44", "PL");
        ClientDataDto abroad = companyDto("DE123", "DE");

        // when
        Map<String, String> validErrors = ClientOfferDataForm.of(valid, COMPANY, SAME, true).validate();
        Map<String, String> shortErrors = ClientOfferDataForm.of(tooShort, COMPANY, SAME, true).validate();
        Map<String, String> abroadErrors = ClientOfferDataForm.of(abroad, COMPANY, SAME, true).validate();

        // then
        assertThat(validErrors).isEmpty();
        assertThat(shortErrors).containsExactly(Map.entry("billingDetails.taxId", "client.offer.error.taxId.invalid"));
        assertThat(abroadErrors).isEmpty();
    }

    @Test
    void polishPostalCodeNeedsTheDashFormat() {
        // given
        ClientDataDto polish = validDto();
        polish.getBillingDetails().setPostalCode("00950");
        ClientDataDto german = validDto();
        german.getBillingDetails().setCountry("DE");
        german.getBillingDetails().setPostalCode("10115");

        // when
        Map<String, String> polishErrors = ClientOfferDataForm.of(polish, PERSON, SAME, true).validate();
        Map<String, String> germanErrors = ClientOfferDataForm.of(german, PERSON, SAME, true).validate();

        // then
        assertThat(polishErrors).containsExactly(
                Map.entry("billingDetails.postalCode", "client.offer.error.postalCode.invalid"));
        assertThat(germanErrors).isEmpty();
    }

    @Test
    void phoneAcceptsSpacesAndPlus() {
        // given
        ClientDataDto valid = validDto();
        valid.getBillingDetails().setPhone("+48 22 390 45 10");
        ClientDataDto tooShort = validDto();
        tooShort.getBillingDetails().setPhone("600 100");

        // when
        Map<String, String> validErrors = ClientOfferDataForm.of(valid, PERSON, SAME, false).validate();
        Map<String, String> shortErrors = ClientOfferDataForm.of(tooShort, PERSON, SAME, false).validate();

        // then
        assertThat(validErrors).isEmpty();
        assertThat(shortErrors).containsExactly(Map.entry("billingDetails.phone", "client.offer.error.phone.invalid"));
    }

    @Test
    void countryMustBeTwoLetters() {
        // given
        ClientDataDto spelledOut = validDto();
        spelledOut.getBillingDetails().setCountry("Polska");
        ClientDataDto lowerCase = validDto();
        lowerCase.getBillingDetails().setCountry("de");
        lowerCase.getBillingDetails().setPostalCode("10115");

        // when
        Map<String, String> spelledOutErrors = ClientOfferDataForm.of(spelledOut, PERSON, SAME, true).validate();
        ClientOfferDataForm lowerCaseForm = ClientOfferDataForm.of(lowerCase, PERSON, SAME, true);

        // then
        assertThat(spelledOutErrors).containsExactly(
                Map.entry("billingDetails.country", "client.offer.error.country.invalid"));
        assertThat(lowerCaseForm.validate()).isEmpty();
        assertThat(lowerCaseForm.toBilling().getCountry()).isEqualTo("DE");
    }

    @Test
    void otherShippingAddressIsValidatedAndForcedToPoland() {
        // given
        ClientDataDto dto = validDto();
        ClientOfferDataForm empty = ClientOfferDataForm.of(dto, PERSON, OTHER, false);

        // when
        Map<String, String> errors = empty.validate();

        // then
        assertThat(errors).containsExactly(
                Map.entry("shippingDetails.name", "client.offer.error.shipping.name.required"),
                Map.entry("shippingDetails.surname", "client.offer.error.shipping.surname.required"),
                Map.entry("shippingDetails.streetAndNumber", "client.offer.error.shipping.street.required"),
                Map.entry("shippingDetails.postalCode", "client.offer.error.shipping.postalCode.required"),
                Map.entry("shippingDetails.city", "client.offer.error.shipping.city.required"),
                Map.entry("shippingDetails.email", "client.offer.error.shipping.email.required"),
                Map.entry("shippingDetails.phone", "client.offer.error.shipping.phone.required"));
        assertThat(empty.toShipping().getCountry()).isEqualTo("PL");
    }

    @Test
    void otherShippingAddressFormatsAreChecked() {
        // given
        ClientDataDto dto = validDto();
        ShippingDetails shipping = new ShippingDetails();
        shipping.setName("Jan");
        shipping.setSurname("Kowalski");
        shipping.setStreetAndNumber("Polna 1");
        shipping.setPostalCode("00950");
        shipping.setCity("Warszawa");
        shipping.setEmail("jan");
        shipping.setPhone("1");
        dto.setShippingDetails(shipping);

        // when
        Map<String, String> errors = ClientOfferDataForm.of(dto, PERSON, OTHER, false).validate();

        // then
        assertThat(errors).containsExactly(
                Map.entry("shippingDetails.postalCode", "client.offer.error.shipping.postalCode.invalid"),
                Map.entry("shippingDetails.email", "client.offer.error.shipping.email.invalid"),
                Map.entry("shippingDetails.phone", "client.offer.error.shipping.phone.invalid"));
    }

    @Test
    void sameAddressAbroadIsRefused() {
        // given
        ClientDataDto dto = validDto();
        dto.getBillingDetails().setCountry("DE");
        dto.getBillingDetails().setPostalCode("10115");

        // when
        Map<String, String> errors = ClientOfferDataForm.of(dto, PERSON, SAME, false).validate();

        // then
        assertThat(errors).containsExactly(Map.entry("shippingAddress", "client.offer.error.shipping.country"));
    }

    @Test
    void sameAddressCopiesTheBillingAddress() {
        // given
        ClientDataDto dto = validDto();
        ClientOfferDataForm form = ClientOfferDataForm.of(dto, PERSON, SAME, false);

        // when
        ShippingDetails shipping = form.toShipping();

        // then
        assertThat(form.validate()).isEmpty();
        assertThat(List.of(shipping.getName(), shipping.getSurname(), shipping.getStreetAndNumber(),
                shipping.getPostalCode(), shipping.getCity(), shipping.getCountry(), shipping.getEmail(),
                shipping.getPhone()))
                .containsExactly("Jan", "Kowalski", "Polna 1", "00-950", "Warszawa", "PL", "jan@example.com",
                        "600 100 200");
    }

    @Test
    void personalCollectionCopiesBillingAndSkipsShippingFields() {
        // given
        ClientDataDto dto = validDto();
        dto.getBillingDetails().setCountry("DE");
        dto.getBillingDetails().setPostalCode("10115");
        ClientOfferDataForm form = ClientOfferDataForm.of(dto, PERSON, OTHER, true);

        // when
        ShippingDetails shipping = form.toShipping();

        // then
        assertThat(form.validate()).isEmpty();
        assertThat(form.isOtherShipping()).isFalse();
        assertThat(shipping.getCountry()).isEqualTo("DE");
        assertThat(shipping.getStreetAndNumber()).isEqualTo("Polna 1");
    }

    @Test
    void personDropsCompanyFieldsBeforeSaving() {
        // given
        ClientDataDto dto = validDto();
        dto.getBillingDetails().setCompanyName("X");
        dto.getBillingDetails().setTaxId("Y");
        ClientOfferDataForm form = ClientOfferDataForm.of(dto, PERSON, SAME, false);

        // when
        BillingDetails billing = form.toBilling();
        ShippingDetails shipping = form.toShipping();

        // then
        assertThat(billing.getCompanyName()).isNull();
        assertThat(billing.getTaxId()).isNull();
        assertThat(shipping.getCompanyName()).isNull();
    }

    @Test
    void taxIdIsStoredAsTyped() {
        // given
        ClientOfferDataForm form = ClientOfferDataForm.of(companyDto(" PL 525-281-44-10 ", "PL"), COMPANY, SAME, false);

        // when
        String stored = form.toBilling().getTaxId();

        // then
        assertThat(stored).isEqualTo("PL 525-281-44-10");
    }

    @Test
    void unknownChoicesFallBackToPersonAndSameAddress() {
        // given
        ClientOfferDataForm form = ClientOfferDataForm.of(null, "x", "y", false);

        // when / then
        assertThat(form.customerType()).isEqualTo(PERSON);
        assertThat(form.shippingAddress()).isEqualTo(SAME);
        assertThat(form.dto().getBillingDetails()).isNotNull();
        assertThat(form.dto().getShippingDetails()).isNotNull();
    }

    @Test
    void emptyFormDefaultsBothCountriesToPoland() {
        // given
        ClientOfferDataForm form = ClientOfferDataForm.empty(false);

        // when / then
        assertThat(form.dto().getBillingDetails().getCountry()).isEqualTo("PL");
        assertThat(form.dto().getShippingDetails().getCountry()).isEqualTo("PL");
    }

    @Test
    void shippingCopyOfTakesTheAddressWithoutTheTaxId() {
        // given
        BillingDetails billing = validDto().getBillingDetails();
        billing.setCompanyName("ACME");
        billing.setTaxId("5252814410");

        // when
        ShippingDetails shipping = ClientOfferDataForm.shippingCopyOf(billing);

        // then
        assertThat(shipping.getName()).isEqualTo("Jan");
        assertThat(shipping.getSurname()).isEqualTo("Kowalski");
        assertThat(shipping.getCompanyName()).isEqualTo("ACME");
        assertThat(shipping.getStreetAndNumber()).isEqualTo("Polna 1");
        assertThat(shipping.getPostalCode()).isEqualTo("00-950");
        assertThat(shipping.getCity()).isEqualTo("Warszawa");
        assertThat(shipping.getCountry()).isEqualTo("PL");
        assertThat(shipping.getEmail()).isEqualTo("jan@example.com");
        assertThat(shipping.getPhone()).isEqualTo("600 100 200");
    }

    private static ClientDataDto companyDto(String taxId, String country) {
        ClientDataDto dto = validDto();
        dto.getBillingDetails().setCompanyName("ACME");
        dto.getBillingDetails().setTaxId(taxId);
        dto.getBillingDetails().setCountry(country);
        if (!"PL".equals(country)) {
            dto.getBillingDetails().setPostalCode("10115");
        }
        return dto;
    }

    private static ClientDataDto validDto() {
        BillingDetails billing = new BillingDetails();
        billing.setName("Jan");
        billing.setSurname("Kowalski");
        billing.setStreetAndNumber("Polna 1");
        billing.setPostalCode("00-950");
        billing.setCity("Warszawa");
        billing.setCountry("PL");
        billing.setEmail("jan@example.com");
        billing.setPhone("600 100 200");
        ClientDataDto dto = new ClientDataDto();
        dto.setBillingDetails(billing);
        return dto;
    }
}
