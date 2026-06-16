package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class FulfilmentConfigurationTest {

    @Test
    void newConfigurationHasGlobalSuppliersDisabledByDefault() {
        assertFalse(new FulfilmentConfiguration().isCanUseGlobalSuppliers());
    }
}
