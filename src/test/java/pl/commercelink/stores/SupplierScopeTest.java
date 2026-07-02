package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplierScopeTest {

    @Test
    void pricingExcludesDisabledConnectionEvenWhenIncludeInPricing() {
        // given
        StoreSupplierConnection connection = new StoreSupplierConnection("manual:A", ConnectionMode.MANUAL, true, true);
        connection.setEnabled(false);

        // when / then
        assertFalse(SupplierScope.PRICING.includes(connection));
    }

    @Test
    void fulfilmentIncludesEnabledConnectionWithIncludeInFulfilment() {
        // given
        StoreSupplierConnection connection = new StoreSupplierConnection("manual:A", ConnectionMode.MANUAL, true, true);
        connection.setEnabled(true);

        // when / then
        assertTrue(SupplierScope.FULFILMENT.includes(connection));
    }

    @Test
    void connectionIsEnabledByDefault() {
        // given / when / then
        assertTrue(new StoreSupplierConnection("Acme", ConnectionMode.GLOBAL).isEnabled());
    }
}
