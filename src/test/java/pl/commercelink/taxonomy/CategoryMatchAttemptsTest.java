package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryMatchAttemptsTest {

    private static final String EAN_1 = "5901234567801";
    private static final String EAN_2 = "5901234567802";

    private final CategoryMatchAttempts attempts = new CategoryMatchAttempts();

    @Test
    void recordIncrementsPerEan() {
        // when
        int first = attempts.record(EAN_1);
        int second = attempts.record(EAN_1);
        int other = attempts.record(EAN_2);

        // then
        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(2);
        assertThat(other).isEqualTo(1);
        assertThat(attempts.trackedCount()).isEqualTo(2);
    }

    @Test
    void eanWithLeadingZerosCountsAsTheSameProduct() {
        // given
        attempts.record("0" + EAN_1);

        // when
        int second = attempts.record(EAN_1);

        // then
        assertThat(second).isEqualTo(2);
        assertThat(attempts.exhausted("00" + EAN_1, 2)).isTrue();
    }

    @Test
    void exhaustedRespectsThreshold() {
        // given
        attempts.record(EAN_1);
        attempts.record(EAN_1);

        // when / then
        assertThat(attempts.exhausted(EAN_1, 3)).isFalse();
        assertThat(attempts.exhausted(EAN_1, 2)).isTrue();
        assertThat(attempts.exhausted(EAN_2, 2)).isFalse();
    }

    @Test
    void zeroMaxAttemptsNeverExhausts() {
        // given
        attempts.record(EAN_1);
        attempts.record(EAN_1);

        // when / then
        assertThat(attempts.exhausted(EAN_1, 0)).isFalse();
    }

    @Test
    void clearResetsCounter() {
        // given
        attempts.record(EAN_1);
        attempts.record(EAN_1);

        // when
        attempts.clear("0" + EAN_1);

        // then
        assertThat(attempts.exhausted(EAN_1, 1)).isFalse();
        assertThat(attempts.trackedCount()).isZero();
        assertThat(attempts.record(EAN_1)).isEqualTo(1);
    }

    @Test
    void nullAndBlankEanAreIgnored() {
        // when / then
        assertThat(attempts.record(null)).isZero();
        assertThat(attempts.record("  ")).isZero();
        assertThat(attempts.exhausted(null, 1)).isFalse();
        attempts.clear(null);
        assertThat(attempts.trackedCount()).isZero();
    }
}
