package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.SupplierInfo;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupplierRegistryManualTest {

    @Test
    void manualSupplierResolvesToLocalDistributorNotOther() {
        // given
        SupplierProviderFactory factory = mock(SupplierProviderFactory.class);
        when(factory.availableProviders()).thenReturn(List.of());
        SupplierRegistry registry = new SupplierRegistry(factory);

        // when
        SupplierInfo info = registry.get("manual:Hurtownia A");

        // then
        assertEquals("manual:Hurtownia A", info.name());
        assertEquals("PL", info.origin());
        assertTrue(info.isLocalFor("PL"));
    }
}
