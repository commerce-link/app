package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class FulfilmentConfigurationTest {

    @Test
    void newConfigurationHasGlobalSuppliersDisabledByDefault() {
        // when / then
        assertFalse(new FulfilmentConfiguration().isCanUseGlobalSuppliers());
    }

    @Test
    void newConfigurationLeavesEnabledCategoriesUnsetToDetectFormsWithoutCategoryCheckboxes() {
        // when / then
        assertNull(new FulfilmentConfiguration().getEnabledCategories());
    }
}
