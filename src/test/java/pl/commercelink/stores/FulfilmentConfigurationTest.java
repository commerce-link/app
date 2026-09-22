package pl.commercelink.stores;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.fulfilment.FulfilmentType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
        source.setClientOrderPageEnabled(true);
        source.setClientShippingAddressChangeEnabled(true);
        source.setClientPreferredShippingDateEnabled(true);
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
        assertTrue(copy.isClientOrderPageEnabled());
        assertTrue(copy.isClientShippingAddressChangeEnabled());
        assertTrue(copy.isClientPreferredShippingDateEnabled());
        assertEquals(List.of("Kosatec"),
                copy.getSupplierConnections().stream().map(StoreSupplierConnection::getSupplierName).toList());
        assertEquals(List.of("Elko"),
                source.getSupplierConnections().stream().map(StoreSupplierConnection::getSupplierName).toList());
    }

    @Test
    void withConnectionsCarriesOverEveryFieldIncludingOnesAddedLater() throws Exception {
        // given -- every field set to a non-default value by reflection, so a field added to the
        // class but forgotten in withConnections fails here; a per-supplier save would otherwise
        // silently reset it to its default, which is how clientOrderPageEnabled and
        // clientShippingAddressChangeEnabled would have been lost
        FulfilmentConfiguration source = new FulfilmentConfiguration();
        List<Field> fields = new ArrayList<>();
        for (Field field : FulfilmentConfiguration.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getName().equals("supplierConnections")) {
                continue;
            }
            field.setAccessible(true);
            field.set(source, nonDefaultValueFor(field, field.get(source)));
            fields.add(field);
        }

        // when
        FulfilmentConfiguration copy = source.withConnections(List.of());

        // then
        for (Field field : fields) {
            assertEquals(field.get(source), field.get(copy), "withConnections does not copy " + field.getName());
        }
    }

    private static Object nonDefaultValueFor(Field field, Object defaultValue) {
        Class<?> type = field.getType();
        if (type == boolean.class || type == Boolean.class) {
            return true;
        }
        if (type == int.class || type == Integer.class) {
            return 7;
        }
        if (type == List.class) {
            return List.of("value");
        }
        if (type.isEnum()) {
            for (Object constant : type.getEnumConstants()) {
                if (!constant.equals(defaultValue)) {
                    return constant;
                }
            }
        }
        throw new IllegalStateException("No non-default value defined for " + field.getName() + " of type " + type);
    }
}
