package pl.commercelink.marketplace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplaceOfferSnapshotTest {

    @Test
    void publishedSnapshotCarriesZeroedReasonAndIsNotPendingRemoval() {
        // when
        MarketplaceOfferSnapshot snapshot = MarketplaceOfferSnapshot.published(
                "pim-1", 1999L, 0L, MarketplaceExportSkipReason.QUANTITY_ZEROED_BELOW_WAREHOUSE_THRESHOLD.name());

        // then
        assertEquals(0, snapshot.removalAttempts());
        assertFalse(snapshot.pendingRemoval());
        assertEquals("QUANTITY_ZEROED_BELOW_WAREHOUSE_THRESHOLD", snapshot.quantityZeroedReason());
    }

    @Test
    void pendingRemovalSnapshotHasZeroQuantityAndIsFlagged() {
        // when
        MarketplaceOfferSnapshot snapshot = MarketplaceOfferSnapshot.pendingRemoval("pim-1", 1999L, 2);

        // then
        assertEquals(0L, snapshot.quantity());
        assertEquals(2, snapshot.removalAttempts());
        assertTrue(snapshot.pendingRemoval());
        assertNull(snapshot.quantityZeroedReason());
    }
}
