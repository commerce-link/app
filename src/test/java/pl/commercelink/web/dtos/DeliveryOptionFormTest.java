package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.stores.DeliveryOption;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryOptionFormTest {

    private static DeliveryOptionForm form(String name, String price, String type) {
        DeliveryOptionForm form = new DeliveryOptionForm();
        form.setName(name);
        form.setPrice(price);
        form.setType(type);
        return form;
    }

    @Test
    void aPolishPriceWithACommaIsAcceptedAndStoredAsANumber() {
        // given
        DeliveryOption option = new DeliveryOption();
        String id = option.getId();
        DeliveryOptionForm form = form(" Kurier DPD ", "19,99", "Courier");
        form.setDescription("  ");

        // when
        Map<String, String> errors = form.validate();
        form.applyTo(option);

        // then
        assertThat(errors).isEmpty();
        assertThat(option.getId()).isEqualTo(id);
        assertThat(option.getName()).isEqualTo("Kurier DPD");
        assertThat(option.getPrice()).isEqualTo(19.99);
        assertThat(option.getDescription()).isNull();
        assertThat(option.getType()).isEqualTo(ShipmentType.Courier);
    }

    @Test
    void theNameAPriceAndAKnownTypeAreRequired() {
        // when
        Map<String, String> errors = form("", "abc", "Drone").validate();

        // then
        assertThat(errors).containsEntry("name", "store.payments.delivery.name.required")
                .containsEntry("price", "store.payments.delivery.price.invalid")
                .containsEntry("type", "store.payments.delivery.type.required");
        assertThat(form("Odbiór", "", "PersonalCollection").validate())
                .containsEntry("price", "store.payments.delivery.price.required");
        assertThat(form("Odbiór", "0", "PersonalCollection").validate()).isEmpty();
        assertThat(form("Odbiór", "-5", "PersonalCollection").validate()).containsKey("price");
    }

    @Test
    void aStoredOptionIsShownWithTwoDecimalsAndAComma() {
        // given
        DeliveryOption option = new DeliveryOption();
        option.setName("Kurier");
        option.setPrice(20);
        option.setType(ShipmentType.PickupPoint);

        // when
        DeliveryOptionForm form = DeliveryOptionForm.from(option);

        // then
        assertThat(form.getPrice()).isEqualTo("20,00");
        assertThat(form.getType()).isEqualTo("PickupPoint");
    }
}
