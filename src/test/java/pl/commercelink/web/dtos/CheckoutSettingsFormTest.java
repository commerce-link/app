package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.CheckoutConfiguration;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.Store;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutSettingsFormTest {

    private static CheckoutSettingsForm form(String successUrl, String cancelUrl, String currency, String pricelists) {
        CheckoutSettingsForm form = new CheckoutSettingsForm();
        form.setSuccessUrl(successUrl);
        form.setCancelUrl(cancelUrl);
        form.setCurrency(currency);
        form.setAcceptedPricelists(pricelists);
        return form;
    }

    private static CheckoutSettingsForm valid() {
        return form("https://sklep.pl/dziekujemy/", "https://sklep.pl/koszyk", "pln", "3");
    }

    @Test
    void aCompleteFormHasNoErrorsAndSavesTheValuesKeepingTheDeliveryOptions() {
        // given
        Store store = new Store();
        CheckoutConfiguration configuration = new CheckoutConfiguration();
        DeliveryOption courier = new DeliveryOption();
        courier.setName("Kurier");
        configuration.setDeliveryOptions(List.of(courier));
        store.setCheckoutConfiguration(configuration);

        // when
        Map<String, String> errors = valid().validate("pln");
        valid().applyTo(store);

        // then
        assertThat(errors).isEmpty();
        assertThat(store.getCheckoutConfiguration().getSuccessUrl()).isEqualTo("https://sklep.pl/dziekujemy/");
        assertThat(store.getCheckoutConfiguration().getNumberOfAcceptedPricelists()).isEqualTo(3);
        assertThat(store.getCheckoutConfiguration().getDeliveryOptions()).containsExactly(courier);
    }

    @Test
    void addressesMustBeFullHttpAddresses() {
        // when
        Map<String, String> errors = form("", "sklep.pl/koszyk", "pln", "1").validate("pln");

        // then
        assertThat(errors).containsEntry("successUrl", "store.payments.checkout.url.required")
                .containsEntry("cancelUrl", "store.payments.checkout.url.invalid");
    }

    /** Offers print PLN, so another currency would charge the same number in a different money. */
    @Test
    void onlyTheZlotyIsAcceptedUnlessTheStoreAlreadySavedAnotherCurrency() {
        // when
        Map<String, String> newEuro = form("https://a.pl", "https://a.pl", "eur", "1").validate("pln");
        Map<String, String> keptEuro = form("https://a.pl", "https://a.pl", "eur", "1").validate("EUR");
        Map<String, String> upperCaseZloty = form("https://a.pl", "https://a.pl", "PLN", "1").validate("eur");

        // then
        assertThat(newEuro).containsEntry("currency", "store.payments.checkout.currency.invalid");
        assertThat(keptEuro).isEmpty();
        assertThat(upperCaseZloty).isEmpty();
    }

    @Test
    void theNumberOfPricelistsIsAWholeNumberFromOneToFifty() {
        // expect
        assertThat(form("https://a.pl", "https://a.pl", "pln", "").validate("pln"))
                .containsEntry("acceptedPricelists", "store.payments.checkout.pricelists.required");
        assertThat(form("https://a.pl", "https://a.pl", "pln", "0").validate("pln"))
                .containsEntry("acceptedPricelists", "store.payments.checkout.pricelists.invalid");
        assertThat(form("https://a.pl", "https://a.pl", "pln", "51").validate("pln")).containsKey("acceptedPricelists");
        assertThat(form("https://a.pl", "https://a.pl", "pln", "2,5").validate("pln")).containsKey("acceptedPricelists");
        assertThat(form("https://a.pl", "https://a.pl", "pln", "50").validate("pln")).isEmpty();
    }

    @Test
    void aNewStoreStartsWithTheLocalDefaultsWhichAreRecognisedAsUnreachable() {
        // when
        CheckoutSettingsForm form = CheckoutSettingsForm.from(null);

        // then
        assertThat(form.getCurrency()).isEqualTo("pln");
        assertThat(form.getAcceptedPricelists()).isEqualTo("1");
        assertThat(CheckoutSettingsForm.pointsToLocalMachine(form.getSuccessUrl())).isTrue();
        assertThat(CheckoutSettingsForm.pointsToLocalMachine("http://127.0.0.1:8080/x")).isTrue();
        assertThat(CheckoutSettingsForm.pointsToLocalMachine("https://sklep.pl/localhost")).isFalse();
    }
}
