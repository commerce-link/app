package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutConfigurationTest {

    private static DeliveryOption option(String name) {
        DeliveryOption option = new DeliveryOption();
        option.setName(name);
        return option;
    }

    /** Removing an option used to break every offer and paid basket that had chosen it: they look it up by id. */
    @Test
    void aRetiredOptionIsHiddenFromNewChoicesButStillFoundForBasketsThatChoseIt() {
        // given
        CheckoutConfiguration configuration = new CheckoutConfiguration();
        DeliveryOption courier = option("Kurier");
        DeliveryOption pickup = option("Odbiór");
        configuration.setDeliveryOptions(List.of(courier, pickup));

        // when
        boolean retired = configuration.retireDeliveryOption(courier.getId());

        // then
        assertThat(retired).isTrue();
        assertThat(configuration.getActiveDeliveryOptions()).containsExactly(pickup);
        assertThat(configuration.findDeliveryOption(courier.getId())).isSameAs(courier);
        assertThat(configuration.findActiveDeliveryOption(courier.getId())).isEmpty();
        assertThat(configuration.deliveryOptionsFor(courier.getId())).containsExactly(courier, pickup);
        assertThat(configuration.deliveryOptionsFor(null)).containsExactly(pickup);
        assertThat(configuration.getDeliveryOptions()).hasSize(2);
    }

    @Test
    void retiringAnUnknownOrAlreadyRetiredOptionChangesNothing() {
        // given
        CheckoutConfiguration configuration = new CheckoutConfiguration();
        DeliveryOption courier = option("Kurier");
        configuration.addDeliveryOption(courier);
        configuration.retireDeliveryOption(courier.getId());

        // expect
        assertThat(configuration.retireDeliveryOption(courier.getId())).isFalse();
        assertThat(configuration.retireDeliveryOption("unknown")).isFalse();
    }

    /** The demo seeder sets an immutable list. */
    @Test
    void anOptionCanBeAddedToAListSetInCode() {
        // given
        CheckoutConfiguration configuration = new CheckoutConfiguration();
        configuration.setDeliveryOptions(List.of(option("Kurier")));

        // when
        configuration.addDeliveryOption(option("Odbiór"));

        // then
        assertThat(configuration.getActiveDeliveryOptions()).extracting(DeliveryOption::getName).containsExactly("Kurier", "Odbiór");
    }
}
