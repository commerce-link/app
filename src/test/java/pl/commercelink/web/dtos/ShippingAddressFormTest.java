package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.ShippingDetails;

import static org.assertj.core.api.Assertions.assertThat;

class ShippingAddressFormTest {

    private static ShippingAddressForm valid() {
        ShippingAddressForm form = ShippingAddressForm.empty();
        form.setCompanyName("Demo Store");
        form.setStreetAndNumber("Marszałkowska 1");
        form.setPostalCode("00-001");
        form.setCity("Warszawa");
        form.setEmail("magazyn@sklep.pl");
        form.setPhone("501 234 567");
        return form;
    }

    @Test
    void theCourierNeedsAnEmailAndAPhoneUnlikeAGoodsReceivingAddress() {
        // given
        ShippingAddressForm form = valid();
        form.setEmail(" ");
        form.setPhone(null);

        // when / then
        assertThat(form.validate()).containsOnlyKeys("email", "phone");
    }

    @Test
    void theContactPersonIsOptionalAndGoesToTheAddressName() {
        // given
        ShippingAddressForm form = valid();
        form.setContactPerson(" Jan Kowalski ");

        // when
        ShippingDetails details = form.toNewShippingDetails();

        // then
        assertThat(valid().validate()).isEmpty();
        assertThat(details.getFullName()).isEqualTo("Jan Kowalski");
        assertThat(details.getCompanyName()).isEqualTo("Demo Store");
        assertThat(details.isProperlyFilled()).isTrue();
    }

    @Test
    void anOldAddressWithOnlyAPersonNameKeepsItAsTheAddressName() {
        // given
        ShippingDetails details = new ShippingDetails();
        details.setName("Jan");
        details.setSurname("Kowalski");

        // when
        ShippingAddressForm form = ShippingAddressForm.from(details);

        // then
        assertThat(form.getCompanyName()).isEqualTo("Jan Kowalski");
        assertThat(form.getContactPerson()).isNull();
        assertThat(form.getCountry()).isEqualTo(CountryOptions.POLAND);
    }

    @Test
    void aLabelSenderIsCheckedOnlyWhenItIsNotThePickupAddress() {
        // given
        LabelSenderForm form = LabelSenderForm.from(null);

        // when / then
        assertThat(form.other()).isFalse();
        assertThat(form.validate()).isEmpty();
        assertThat(form.toSender(null)).isNull();

        // when
        form.setSender(LabelSenderForm.OTHER);

        // then
        assertThat(form.validate()).containsKeys("companyName", "streetAndNumber", "email", "phone");
    }

    @Test
    void aSavedLabelSenderIsEditedInPlace() {
        // given
        ShippingDetails saved = valid().toNewShippingDetails();
        saved.setId("sender-1");
        LabelSenderForm form = LabelSenderForm.from(saved);
        form.setCity("Kraków");

        // when
        ShippingDetails sender = form.toSender(saved);

        // then
        assertThat(form.other()).isTrue();
        assertThat(sender.getId()).isEqualTo("sender-1");
        assertThat(sender.getCity()).isEqualTo("Kraków");
    }
}
