package pl.commercelink.stores;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.fulfilment.FulfilmentType;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void withConnectionsCopiesEveryOtherFieldAndDoesNotTouchTheSource() {
        // given
        FulfilmentConfiguration source = new FulfilmentConfiguration();
        source.setOrderAssemblyDays(3);
        source.setOrderRealizationDays(5);
        source.setAutomatedFulfilment(true);
        source.setDefaultFulfilmentType(FulfilmentType.DirectToConsumer);
        source.setEnabledProductGroups(List.of("group"));
        source.setEnabledCategories(List.of("category"));
        source.setCanUseGlobalSuppliers(true);
        source.setInventoryCacheTtlMinutes(42);
        source.setSupplierConnections(new ArrayList<>(List.of(
                new StoreSupplierConnection("Elko", ConnectionMode.OWN, true, true))));

        // when
        FulfilmentConfiguration copy = source.withConnections(List.of(
                new StoreSupplierConnection("Kosatec", ConnectionMode.OWN, true, true)));

        // then
        assertEquals(3, copy.getOrderAssemblyDays());
        assertEquals(5, copy.getOrderRealizationDays());
        assertTrue(copy.isAutomatedFulfilment());
        assertEquals(FulfilmentType.DirectToConsumer, copy.getDefaultFulfilmentType());
        assertEquals(List.of("group"), copy.getEnabledProductGroups());
        assertEquals(List.of("category"), copy.getEnabledCategories());
        assertTrue(copy.isCanUseGlobalSuppliers());
        assertEquals(42, copy.getInventoryCacheTtlMinutes());
        assertEquals(List.of("Kosatec"),
                copy.getSupplierConnections().stream().map(StoreSupplierConnection::getSupplierName).toList());
        assertEquals(List.of("Elko"),
                source.getSupplierConnections().stream().map(StoreSupplierConnection::getSupplierName).toList());
    }
}
