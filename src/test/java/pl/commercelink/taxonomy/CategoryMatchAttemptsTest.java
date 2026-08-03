package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryMatchAttemptsTest {

    private final CategoryMatchAttempts attempts = new CategoryMatchAttempts();

    @Test
    void recordIncrementsPerMfn() {
        // when
        int first = attempts.record("MFN-1");
        int second = attempts.record("MFN-1");
        int other = attempts.record("MFN-2");

        // then
        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(2);
        assertThat(other).isEqualTo(1);
        assertThat(attempts.trackedCount()).isEqualTo(2);
    }

    @Test
    void exhaustedRespectsThreshold() {
        // given
        attempts.record("MFN-1");
        attempts.record("MFN-1");

        // when / then
        assertThat(attempts.exhausted("MFN-1", 3)).isFalse();
        assertThat(attempts.exhausted("MFN-1", 2)).isTrue();
        assertThat(attempts.exhausted("MFN-UNKNOWN", 2)).isFalse();
    }

    @Test
    void zeroMaxAttemptsNeverExhausts() {
        // given
        attempts.record("MFN-1");
        attempts.record("MFN-1");

        // when / then
        assertThat(attempts.exhausted("MFN-1", 0)).isFalse();
    }

    @Test
    void clearResetsCounter() {
        // given
        attempts.record("MFN-1");
        attempts.record("MFN-1");

        // when
        attempts.clear("MFN-1");

        // then
        assertThat(attempts.exhausted("MFN-1", 1)).isFalse();
        assertThat(attempts.trackedCount()).isZero();
        assertThat(attempts.record("MFN-1")).isEqualTo(1);
    }

    @Test
    void nullAndBlankMfnAreIgnored() {
        // when / then
        assertThat(attempts.record(null)).isZero();
        assertThat(attempts.record("  ")).isZero();
        assertThat(attempts.exhausted(null, 1)).isFalse();
        attempts.clear(null);
        assertThat(attempts.trackedCount()).isZero();
    }
}
