package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.inventory.supplier.manual.ManualSupplierDescriptor;
import pl.commercelink.provider.ProviderConfigurationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

class SupplierProviderFactoryManualTest {

    private final SupplierProviderFactory factory =
            new SupplierProviderFactory(mock(ProviderConfigurationManager.class));

    @Test
    void buildsManualDescriptorForManualNames() {
        // when
        SupplierProviderDescriptor descriptor = factory.getDescriptor("manual:Hurtownia A");

        // then
        assertInstanceOf(ManualSupplierDescriptor.class, descriptor);
        assertEquals("manual:Hurtownia A", descriptor.name());
    }
}
