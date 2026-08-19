package pl.commercelink.marketplace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketplaceOfferSnapshotTest {

    @Test
    void publishedSnapshotHasNoRemovalAttemptsAndNoRejection() {
        // when
        MarketplaceOfferSnapshot snapshot = MarketplaceOfferSnapshot.published("pim-1", 1999L, 7L);

        // then
        assertThat(snapshot.pimId()).isEqualTo("pim-1");
        assertThat(snapshot.price()).isEqualTo(1999L);
        assertThat(snapshot.quantity()).isEqualTo(7L);
        assertThat(snapshot.removalAttempts()).isZero();
        assertThat(snapshot.outcome()).isEqualTo(MarketplaceOfferSnapshot.OUTCOME_PUBLISHED);
        assertThat(snapshot.reasonCode()).isNull();
        assertThat(snapshot.message()).isNull();
    }

    @Test
    void removalPendingSnapshotHasZeroQuantityAndKeepsTheAttemptCount() {
        // when
        MarketplaceOfferSnapshot snapshot = MarketplaceOfferSnapshot.removalPending("pim-1", 1999L, 2);

        // then
        assertThat(snapshot.quantity()).isZero();
        assertThat(snapshot.removalAttempts()).isEqualTo(2);
        assertThat(snapshot.outcome()).isEqualTo(MarketplaceOfferSnapshot.OUTCOME_REMOVAL_PENDING);
    }

    @Test
    void exportAbortedSnapshotCarriesOnlyTheMessage() {
        // when
        MarketplaceOfferSnapshot snapshot = MarketplaceOfferSnapshot.exportAborted("java.lang.IllegalStateException: boom");

        // then
        assertThat(snapshot.pimId()).isEmpty();
        assertThat(snapshot.price()).isZero();
        assertThat(snapshot.quantity()).isZero();
        assertThat(snapshot.removalAttempts()).isZero();
        assertThat(snapshot.outcome()).isEqualTo(MarketplaceOfferSnapshot.OUTCOME_EXPORT_ABORTED);
        assertThat(snapshot.message()).isEqualTo("java.lang.IllegalStateException: boom");
    }

    @Test
    void rejectedCopiesTheSnapshotAndFillsTheRejectionColumns() {
        // given
        MarketplaceOfferSnapshot published = MarketplaceOfferSnapshot.published("pim-1", 1999L, 7L);

        // when
        MarketplaceOfferSnapshot rejected = published.rejected("VALIDATION_ERROR", "price out of range");

        // then
        assertThat(rejected.pimId()).isEqualTo("pim-1");
        assertThat(rejected.price()).isEqualTo(1999L);
        assertThat(rejected.quantity()).isEqualTo(7L);
        assertThat(rejected.outcome()).isEqualTo(MarketplaceOfferSnapshot.OUTCOME_REJECTED);
        assertThat(rejected.reasonCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(rejected.message()).isEqualTo("price out of range");
        assertThat(published.outcome()).isEqualTo(MarketplaceOfferSnapshot.OUTCOME_PUBLISHED);
    }
}
