package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import pl.commercelink.marketplace.api.MarketplaceReturnStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RMAMarketplaceFieldsTest {

    @Test
    void manuallyCreatedRmaIsNotAMarketplaceReturn() {
        // given
        RMA rma = new RMA("store-1");

        // when / then
        assertFalse(rma.isMarketplaceReturn());
    }

    @Test
    void rmaWithExternalReturnIdIsAMarketplaceReturn() {
        // given
        RMA rma = new RMA("store-1");
        rma.setMarketplace("Allegro");
        rma.setExternalReturnId("r-1");
        rma.setExternalReturnReference("XGQX/2026");
        rma.setExternalReturnStatus(MarketplaceReturnStatus.IN_TRANSIT);

        // when / then
        assertTrue(rma.isMarketplaceReturn());
        assertEquals("Allegro", rma.getMarketplace());
        assertEquals(MarketplaceReturnStatus.IN_TRANSIT, rma.getExternalReturnStatus());
    }
}
