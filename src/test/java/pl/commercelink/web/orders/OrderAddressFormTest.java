package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.BillingDetails;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrderAddressFormTest {

    @Test
    void phoneNumbersWithSeparatorsAndNineToFifteenDigitsAreAccepted() {
        // given
        List<String> phones = List.of("+48 22 390 45 10", "(22) 390-45-10", "600700800", "+48 600 700 800",
                "22.390.45.10.1", "+123456789012345");

        // then
        assertThat(phones).allMatch(OrderAddressForm::isPhone);
    }

    @Test
    void tooShortTooLongOrLetteredPhoneNumbersAreRefused() {
        // given
        List<String> phones = List.of("12345", "600 700 80", "tel. 600700800", "600-700-ABC", "+1234567890123456",
                "48+600700800", "+");

        // then
        assertThat(phones).noneMatch(OrderAddressForm::isPhone);
    }

    @Test
    void aValidPhoneIsKeptAsTypedAndRaisesNoError() {
        // given
        BillingDetails billing = new BillingDetails();
        billing.setName("Jan");
        billing.setSurname("Kowalski");
        billing.setStreetAndNumber("ul. Grzybowska 87");
        billing.setPostalCode("00-844");
        billing.setCity("Warszawa");
        billing.setCountry("PL");
        billing.setEmail("jan@example.pl");
        billing.setPhone("+48 22 390 45 10");
        OrderAddressForm form = OrderAddressForm.billing("o-1", billing);

        // when
        Map<String, String> errors = form.validate();

        // then
        assertThat(errors).isEmpty();
        assertThat(form.phone()).isEqualTo("+48 22 390 45 10");
    }

    @Test
    void anInvalidPhoneIsReportedAtItsField() {
        // given
        BillingDetails billing = new BillingDetails();
        billing.setPhone("12345");
        OrderAddressForm form = OrderAddressForm.billing("o-1", billing);

        // when
        Map<String, String> errors = form.validate();

        // then
        assertThat(errors).containsEntry("billingDetails.phone", "billing.phone.invalid");
    }
}
