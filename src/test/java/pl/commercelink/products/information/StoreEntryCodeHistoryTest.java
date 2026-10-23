package pl.commercelink.products.information;

import org.junit.jupiter.api.Test;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoreEntryCodeHistoryTest {

    private static final String E1 = "5900000000011";
    private static final String E2 = "5900000000022";

    private final MovableClock clock = new MovableClock();
    private final StoreEntryCodeHistory history = new StoreEntryCodeHistory(clock);

    @Test
    void remembersTheCodesAnEntryHadBeforeItsEdit() {
        // given
        history.remember(List.of(entry("X", E1, "M1")));
        clock.advance(Duration.ofMinutes(5));

        // when
        history.remember(List.of(entry("X", E2, "M1")));

        // then
        assertThat(history.eansOf("X")).containsExactlyInAnyOrder(E1, E2);
        assertThat(history.mpnsOf("X")).containsExactly("M1");
    }

    @Test
    void forgetsCodesNotSeenForLongerThanTheKeepPeriod() {
        // given
        history.remember(List.of(entry("X", E1, "M1")));
        clock.advance(StoreEntryCodeHistory.KEEP.plusSeconds(1));

        // when
        history.remember(List.of(entry("X", E2, "M1")));

        // then
        assertThat(history.eansOf("X")).containsExactly(E2);
    }

    @Test
    void codesExpireForReadsEvenWithoutAFurtherRefresh() {
        // given
        history.remember(List.of(entry("X", E1, null)));

        // when
        clock.advance(StoreEntryCodeHistory.KEEP.plusSeconds(1));

        // then
        assertThat(history.eansOf("X")).isEmpty();
    }

    @Test
    void anEntryNeverSeenHasNoCodes() {
        // when / then
        assertThat(history.eansOf("unknown")).isEmpty();
    }

    private static PimEntry entry(String pimId, String gtin, String mpn) {
        List<PimIdentifier> identifiers = mpn == null
                ? List.of(new PimIdentifier(gtin, PimIdentifierType.GTIN))
                : List.of(new PimIdentifier(gtin, PimIdentifierType.GTIN), new PimIdentifier(mpn, PimIdentifierType.MPN));
        return new PimEntry(pimId, identifiers, "Brand", "Name", null, null, true, null, null, null, "s1");
    }

    private static final class MovableClock extends Clock {

        private Instant now = Instant.parse("2026-10-23T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
