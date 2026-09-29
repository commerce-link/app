package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

class FilterConditionLabelsTest {

    private static final BiFunction<String, Object[], String> PL = lookup();

    private static BiFunction<String, Object[], String> lookup() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        return (key, args) -> messages.getMessage(key, args, new Locale("pl"));
    }

    @Test
    void aPillJoinsEveryValueOfTheField() {
        // when / then
        assertThat(FilterConditionLabels.pill("SourceName", List.of("Allegro", "Ceneo"), PL)).isEqualTo("Marketplace: Allegro, Ceneo");
        assertThat(FilterConditionLabels.pill("PaymentSource", List.of("CashOnDelivery", "card"), PL)).isEqualTo("Płatność: Za pobraniem, Karta");
    }

    @Test
    void aPillOfOneValueReadsAsBefore() {
        // when / then
        assertThat(FilterConditionLabels.pill("Status", List.of("New"), PL)).isEqualTo("Status: Nowe");
    }

    @Test
    void anUnknownValueIsShownAsStored() {
        // when / then
        assertThat(FilterConditionLabels.pill("Status", List.of("Archived"), PL)).isEqualTo("Status: Archived");
    }

    @Test
    void theTriggerSaysAnyTheOneValueOrHowMany() {
        // when / then
        assertThat(FilterConditionLabels.summary("Status", List.of(), PL)).isEqualTo("Dowolny");
        assertThat(FilterConditionLabels.summary("Status", null, PL)).isEqualTo("Dowolny");
        assertThat(FilterConditionLabels.summary("Status", List.of("blocked"), PL)).isEqualTo("Zablokowane");
        assertThat(FilterConditionLabels.summary("SourceName", List.of("Allegro", "Ceneo", "Morele"), PL)).isEqualTo("Wybrano: 3");
    }

    @Test
    void aStoredValueIsPickedWhateverItsCase() {
        // when / then
        assertThat(FilterConditionLabels.isPicked(List.of("courier"), "Courier")).isTrue();
        assertThat(FilterConditionLabels.isPicked(List.of("Courier"), "PickupPoint")).isFalse();
        assertThat(FilterConditionLabels.isPicked(null, "Courier")).isFalse();
    }
}
