package pl.commercelink.orders;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentPickupWindowTest {

    @Test
    void theWindowIsStoredAsTextAndShownWithoutLeadingZeros() {
        // when
        ShipmentPickupWindow window = ShipmentPickupWindow.of(LocalDate.of(2026, 10, 8), LocalTime.of(9, 0),
                LocalTime.of(17, 30));

        // then
        assertThat(window.getDate()).isEqualTo("2026-10-08");
        assertThat(window.getFrom()).isEqualTo("09:00");
        assertThat(window.getTo()).isEqualTo("17:30");
        assertThat(window.formatDay(Locale.forLanguageTag("pl"))).isEqualTo("czw. 8 paź");
        assertThat(window.formatFrom()).isEqualTo("9:00");
        assertThat(window.formatTo()).isEqualTo("17:30");
    }

    @Test
    void aValueThatDoesNotParseIsShownAsStored() {
        // given
        ShipmentPickupWindow window = new ShipmentPickupWindow();
        window.setDate("jutro");
        window.setFrom("rano");

        // then
        assertThat(window.formatDay(Locale.forLanguageTag("pl"))).isEqualTo("jutro");
        assertThat(window.formatFrom()).isEqualTo("rano");
        assertThat(window.formatTo()).isNull();
    }
}
