package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OrderFormatsTest {

    @Test
    void formatsDatesTheWayThePanelShowsThem() {
        // given
        LocalDateTime orderedAt = LocalDateTime.of(2026, 9, 3, 9, 7, 30, 123_000_000);

        // when / then
        assertThat(OrderFormats.date(orderedAt.toLocalDate())).isEqualTo("03.09.2026");
        assertThat(OrderFormats.date(orderedAt)).isEqualTo("03.09.2026");
        assertThat(OrderFormats.dateTime(orderedAt)).isEqualTo("03.09.2026, 09:07");
        assertThat(OrderFormats.isoDate(orderedAt.toLocalDate())).isEqualTo("2026-09-03");
    }

    @Test
    void anAbsentDateStaysAbsent() {
        // when / then
        assertThat(OrderFormats.date((LocalDate) null)).isNull();
        assertThat(OrderFormats.dateTime(null)).isNull();
        assertThat(OrderFormats.isoDate(null)).isNull();
    }
}
