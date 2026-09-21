package pl.commercelink.web.inventory;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RelativeTimeTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 12, 0);

    @Test
    void describesAgeInTheCoarsestReadableUnit() {
        // when / then
        assertThat(RelativeTime.between(NOW.minusSeconds(20), NOW)).isEqualTo(new RelativeTime("inventory.time.minutes", 1));
        assertThat(RelativeTime.between(NOW.minusMinutes(42), NOW)).isEqualTo(new RelativeTime("inventory.time.minutes", 42));
        assertThat(RelativeTime.between(NOW.minusHours(3), NOW)).isEqualTo(new RelativeTime("inventory.time.hours", 3));
        assertThat(RelativeTime.between(NOW.minusHours(30), NOW)).isEqualTo(new RelativeTime("inventory.time.yesterday", 1));
        assertThat(RelativeTime.between(NOW.minusDays(5), NOW)).isEqualTo(new RelativeTime("inventory.time.days", 5));
    }
}
