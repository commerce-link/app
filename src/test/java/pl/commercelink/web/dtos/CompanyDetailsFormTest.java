package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.BillingDetails;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyDetailsFormTest {

    private CompanyDetailsForm validForm() {
        CompanyDetailsForm form = new CompanyDetailsForm();
        form.setCompanyName("Demo Store sp. z o.o.");
        form.setTaxId("1234567890");
        form.setStreetAndNumber("ul. Testowa 1");
        form.setPostalCode("00-001");
        form.setCity("Warszawa");
        form.setCountry("PL");
        form.setEmail("biuro@demo.pl");
        form.setPhone("");
        return form;
    }

    @Test
    void acceptsACompleteCompanyWithoutAPhone() {
        // when
        Map<String, String> errors = validForm().validate();

        // then
        assertThat(errors).isEmpty();
    }

    @Test
    void requiresCompanyNameTaxIdAddressAndEmailInFormOrder() {
        // given
        CompanyDetailsForm form = new CompanyDetailsForm();

        // when
        Map<String, String> errors = form.validate();

        // then
        assertThat(errors).containsExactly(
                Map.entry("companyName", "billing.companyName.required"),
                Map.entry("taxId", "billing.taxId.required"),
                Map.entry("streetAndNumber", "billing.street.required"),
                Map.entry("postalCode", "billing.postalCode.required"),
                Map.entry("city", "billing.city.required"),
                Map.entry("country", "billing.country.required"),
                Map.entry("email", "billing.email.required"));
    }

    @Test
    void treatsWhitespaceOnlyValuesAsMissing() {
        // given
        CompanyDetailsForm form = validForm();
        form.setCompanyName("   ");
        form.setTaxId(" ");

        // when
        Map<String, String> errors = form.validate();

        // then
        assertThat(errors).containsOnlyKeys("companyName", "taxId");
    }

    @Test
    void checksThePolishPostalCodeFormatOnlyForPoland() {
        // given
        CompanyDetailsForm polish = validForm();
        polish.setPostalCode("00001");
        CompanyDetailsForm german = validForm();
        german.setCountry("DE");
        german.setPostalCode("10115");

        // when / then
        assertThat(polish.validate()).containsExactly(Map.entry("postalCode", "billing.postalCode.invalid"));
        assertThat(german.validate()).isEmpty();
    }

    @Test
    void rejectsAMalformedEmailAndPhone() {
        // given
        CompanyDetailsForm form = validForm();
        form.setEmail("biuro-at-demo.pl");
        form.setPhone("12-34");

        // when
        Map<String, String> errors = form.validate();

        // then
        assertThat(errors).containsExactly(
                Map.entry("email", "billing.email.invalid"),
                Map.entry("phone", "billing.phone.invalid"));
    }

    @Test
    void savedDetailsSatisfyTheCompletenessCheckUsedByPointOfSale() {
        // given
        CompanyDetailsForm form = validForm();

        // when
        BillingDetails details = form.applyTo(null);

        // then
        assertThat(details.isProperlyFilled()).isTrue();
        assertThat(details.hasTaxId()).isTrue();
    }

    @Test
    void trimsValuesAndKeepsFieldsTheFormDoesNotEdit() {
        // given
        BillingDetails existing = new BillingDetails();
        existing.setName("Jan");
        existing.setSurname("Kowalski");
        CompanyDetailsForm form = validForm();
        form.setCity("  Kraków ");
        form.setPhone(" +48 600 100 200 ");

        // when
        BillingDetails details = form.applyTo(existing);

        // then
        assertThat(details.getCity()).isEqualTo("Kraków");
        assertThat(details.getPhone()).isEqualTo("+48 600 100 200");
        assertThat(details.getName()).isEqualTo("Jan");
        assertThat(details.getSurname()).isEqualTo("Kowalski");
        assertThat(existing.getCity()).isNull();
    }

    @Test
    void startsANewStoreWithPoland() {
        // when
        CompanyDetailsForm form = CompanyDetailsForm.from(null);

        // then
        assertThat(form.getCountry()).isEqualTo("PL");
        assertThat(form.getCompanyName()).isNull();
    }
}
