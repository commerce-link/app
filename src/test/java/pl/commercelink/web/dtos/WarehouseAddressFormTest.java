package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.ShippingDetails;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseAddressFormTest {

    private WarehouseAddressForm validForm() {
        WarehouseAddressForm form = new WarehouseAddressForm();
        form.setCompanyName("Sklep Demo — magazyn");
        form.setStreetAndNumber("ul. Magazynowa 12");
        form.setPostalCode("02-495");
        form.setCity("Warszawa");
        form.setCountry("PL");
        form.setEmail("magazyn@sklep-demo.pl");
        form.setPhone("+48 600 100 200");
        return form;
    }

    @Test
    void acceptsACompleteAddress() {
        // when / then
        assertThat(validForm().validate()).isEmpty();
    }

    @Test
    void requiresTheNameAndTheAddressInFormOrder() {
        // when
        Map<String, String> errors = new WarehouseAddressForm().validate();

        // then
        assertThat(errors.keySet()).containsExactly("companyName", "streetAndNumber", "postalCode", "city", "country");
    }

    @Test
    void acceptsAnAddressWithoutEmailAndPhone() {
        // given
        WarehouseAddressForm form = validForm();
        form.setEmail(" ");
        form.setPhone(null);

        // when
        ShippingDetails details = form.toNewShippingDetails();

        // then
        assertThat(form.validate()).isEmpty();
        assertThat(details.getEmail()).isNull();
        assertThat(details.getPhone()).isNull();
    }

    @Test
    void checksThePolishPostalCodeOnlyForPoland() {
        // given
        WarehouseAddressForm german = validForm();
        german.setCountry("DE");
        german.setPostalCode("10115");
        WarehouseAddressForm polish = validForm();
        polish.setPostalCode("02495");

        // when / then
        assertThat(german.validate()).isEmpty();
        assertThat(polish.validate()).containsEntry("postalCode", "billing.postalCode.invalid");
    }

    @Test
    void rejectsAMalformedEmailAndPhone() {
        // given
        WarehouseAddressForm form = validForm();
        form.setEmail("magazyn");
        form.setPhone("12");

        // when / then
        assertThat(form.validate()).containsEntry("email", "billing.email.invalid").containsEntry("phone", "billing.phone.invalid");
    }

    @Test
    void aNewAddressWithContactSatisfiesTheCustomerAddressCheck() {
        // when
        ShippingDetails details = validForm().toNewShippingDetails();

        // then
        assertThat(details.isProperlyFilled()).isTrue();
        assertThat(details.getCompanyName()).isEqualTo("Sklep Demo — magazyn");
    }

    @Test
    void editingKeepsTheIdAndTheDefaultFlag() {
        // given
        ShippingDetails existing = new ShippingDetails();
        existing.setId("a-1");
        existing.set_default(true);
        WarehouseAddressForm form = validForm();
        form.setCity("  Kraków ");

        // when
        form.applyTo(existing);

        // then
        assertThat(existing.getId()).isEqualTo("a-1");
        assertThat(existing.is_default()).isTrue();
        assertThat(existing.getCity()).isEqualTo("Kraków");
    }

    @Test
    void theEditedNameReplacesAPersonNameSoTheListShowsWhatWasSaved() {
        // given
        ShippingDetails existing = new ShippingDetails();
        existing.setName("Demo");
        existing.setSurname("Magazynier");
        existing.setCompanyName("Demo Store sp. z o.o.");
        WarehouseAddressForm form = validForm();
        form.setCompanyName("Demo Store — magazyn");

        // when
        form.applyTo(existing);

        // then
        assertThat(existing.getName()).isNull();
        assertThat(existing.getSurname()).isNull();
        assertThat(existing.getCompanyName()).isEqualTo("Demo Store — magazyn");
    }

    @Test
    void anAddressWithOnlyAPersonNameOpensWithThatNameAsTheRecipient() {
        // given
        ShippingDetails existing = new ShippingDetails();
        existing.setName("Jan");
        existing.setSurname("Kowalski");

        // when
        WarehouseAddressForm form = WarehouseAddressForm.from(existing);

        // then
        assertThat(form.getCompanyName()).isEqualTo("Jan Kowalski");
    }

    @Test
    void aNewAddressFormStartsInPoland() {
        // when / then
        assertThat(WarehouseAddressForm.empty().getCountry()).isEqualTo("PL");
    }
}
