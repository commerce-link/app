package pl.commercelink.web.clientreturn;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.rma.RMAReturnForm;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClientReturnFormCheckTest {

    private static final List<ReturnPackageOption> OPTIONS = List.of(
            new ReturnPackageOption("return-small", "Small", 1, null, true),
            new ReturnPackageOption("return-big", "Big", 2, "2 x big", false));

    @Test
    void aCompleteFormHasNoErrors() {
        // given
        RMAReturnForm form = validForm();

        // when
        Map<String, String> errors = ClientReturnFormCheck.validate(form, OPTIONS);

        // then
        assertThat(errors).isEmpty();
    }

    @Test
    void requiredFieldsInFieldOrder() {
        // given
        RMAReturnForm form = new RMAReturnForm();
        form.setShippingDetails(new ShippingDetails());

        // when
        Map<String, String> errors = ClientReturnFormCheck.validate(form, OPTIONS);

        // then
        assertThat(errors).containsExactly(
                Map.entry("shippingDetails.name", "client.return.form.name.required"),
                Map.entry("shippingDetails.surname", "client.return.form.surname.required"),
                Map.entry("shippingDetails.streetAndNumber", "client.return.form.street.required"),
                Map.entry("shippingDetails.postalCode", "client.return.form.postal.required"),
                Map.entry("shippingDetails.city", "client.return.form.city.required"),
                Map.entry("shippingDetails.email", "client.return.form.email.required"),
                Map.entry("shippingDetails.phone", "client.return.form.phone.required"),
                Map.entry("selectedPackageTemplateId", "client.return.form.package.invalid"));
    }

    @Test
    void aMissingShippingDetailsBlockDoesNotThrow() {
        // given
        RMAReturnForm form = new RMAReturnForm();

        // when
        Map<String, String> errors = ClientReturnFormCheck.validate(form, OPTIONS);

        // then
        assertThat(errors).containsKey("shippingDetails.name");
    }

    @Test
    void postalCodeIsPolish() {
        // given
        RMAReturnForm form = validForm();
        form.getShippingDetails().setPostalCode("00950");

        // when
        Map<String, String> errors = ClientReturnFormCheck.validate(form, OPTIONS);

        // then
        assertThat(errors).containsExactly(Map.entry("shippingDetails.postalCode", "client.return.form.postal.invalid"));
    }

    @Test
    void emailMustLookLikeAnEmail() {
        // given
        RMAReturnForm form = validForm();
        form.getShippingDetails().setEmail("jan@example");

        // when
        Map<String, String> errors = ClientReturnFormCheck.validate(form, OPTIONS);

        // then
        assertThat(errors).containsExactly(Map.entry("shippingDetails.email", "client.return.form.email.invalid"));
    }

    @Test
    void phoneFollowsTheOfferRules() {
        // given
        RMAReturnForm dashed = validForm();
        dashed.getShippingDetails().setPhone("601-234-567");
        RMAReturnForm plain = validForm();
        plain.getShippingDetails().setPhone("601234567");

        // when
        Map<String, String> dashedErrors = ClientReturnFormCheck.validate(dashed, OPTIONS);
        Map<String, String> plainErrors = ClientReturnFormCheck.validate(plain, OPTIONS);

        // then
        assertThat(dashedErrors).containsExactly(Map.entry("shippingDetails.phone", "client.return.form.phone.invalid"));
        assertThat(plainErrors).isEmpty();
    }

    @Test
    void fieldsLongerThan100AreRefused() {
        // given
        String tooLong = "a".repeat(101);
        RMAReturnForm form = validForm();
        form.getShippingDetails().setName(tooLong);
        form.getShippingDetails().setCompanyName(tooLong);
        form.getShippingDetails().setCity("  " + "a".repeat(100) + "  ");

        // when
        Map<String, String> errors = ClientReturnFormCheck.validate(form, OPTIONS);

        // then
        assertThat(errors).containsOnly(
                Map.entry("shippingDetails.name", "client.return.form.tooLong"),
                Map.entry("shippingDetails.companyName", "client.return.form.tooLong"));
    }

    @Test
    void aTemplateOutsideTheReturnListIsRefused() {
        for (String id : new String[]{null, "", "small"}) {
            // given
            RMAReturnForm form = validForm();
            form.setSelectedPackageTemplateId(id);

            // when
            Map<String, String> errors = ClientReturnFormCheck.validate(form, OPTIONS);

            // then
            assertThat(errors).containsExactly(Map.entry("selectedPackageTemplateId", "client.return.form.package.invalid"));
        }
    }

    @Test
    void normalizedTrimsAndForcesPoland() {
        // given
        RMAReturnForm form = validForm();
        form.getShippingDetails().setName("  Jan ");
        form.getShippingDetails().setCompanyName("   ");
        form.getShippingDetails().setCountry("DE");

        // when
        RMAReturnForm normalized = ClientReturnFormCheck.normalized(form);

        // then
        assertThat(normalized.getShippingDetails().getName()).isEqualTo("Jan");
        assertThat(normalized.getShippingDetails().getCompanyName()).isNull();
        assertThat(normalized.getShippingDetails().getCountry()).isEqualTo("PL");
        assertThat(normalized.getSelectedPackageTemplateId()).isEqualTo("return-small");
        assertThat(form.getShippingDetails().getName()).isEqualTo("  Jan ");
        assertThat(form.getShippingDetails().getCountry()).isEqualTo("DE");
    }

    @Test
    void normalizedToleratesMissingShippingDetails() {
        // given
        RMAReturnForm form = new RMAReturnForm();

        // when
        RMAReturnForm normalized = ClientReturnFormCheck.normalized(form);

        // then
        assertThat(normalized.getShippingDetails().getCountry()).isEqualTo("PL");
        assertThat(normalized.getShippingDetails().getName()).isNull();
    }

    private static RMAReturnForm validForm() {
        ShippingDetails details = new ShippingDetails();
        details.setName("Jan");
        details.setSurname("Kowalski");
        details.setStreetAndNumber("Prosta 1");
        details.setPostalCode("00-950");
        details.setCity("Warszawa");
        details.setEmail("jan@example.com");
        details.setPhone("+48 601 234 567");
        details.setCompanyName("");
        RMAReturnForm form = new RMAReturnForm();
        form.setShippingDetails(details);
        form.setSelectedPackageTemplateId("return-small");
        return form;
    }
}
