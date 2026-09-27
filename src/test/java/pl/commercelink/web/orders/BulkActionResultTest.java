package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersManager;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class BulkActionResultTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    // Local copy of the messages() helper (rather than a dependency on OrderClosingChecklistTest, which
    // belongs to the 9b dispatch and is not implemented yet): both test classes build the same lookup.
    private static MessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    @Test
    void aPartialResultSaysHowManyAndWhyTheRestWasLeft() {
        // given
        BulkActionResult partial = BulkActionResult.of(BulkAction.ALLOCATE, new OrdersManager.Result(new Order("s"), List.of(), 0, 2, 3));
        BulkActionResult dropship = BulkActionResult.of(BulkAction.TO_WAREHOUSE, new OrdersManager.Result(new Order("s"), List.of(), 1, 0, 2));
        BulkActionResult complete = BulkActionResult.of(BulkAction.REMOVE, new OrdersManager.Result(new Order("s"), List.of(), 0, 2, 2));

        // when / then
        assertThat(partial.complete()).isFalse();
        assertThat(partial.message(messages(), PL))
                .isEqualTo("Zmieniono: 2 z 3. Pominięte pozycje nie mają kompletu danych alokacji albo nie są nowe.");
        assertThat(dropship.message(messages(), PL))
                .isEqualTo("Zmieniono: 0 z 2. Pominięto pozycje w dostawie dropship — nie trafiają do magazynu.");
        assertThat(complete.complete()).isTrue();
        assertThat(complete.message(messages(), PL)).isEqualTo("Zmieniono: 2 z 2.");
    }
}
