package pl.commercelink.inventory.supplier;

import org.springframework.stereotype.Service;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.inventory.supplier.manual.ManualSupplierDescriptor;
import pl.commercelink.inventory.supplier.manual.ManualSupplierNames;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.provider.ProviderFactory;

@Service
public class SupplierProviderFactory extends ProviderFactory<SupplierProviderDescriptor, SupplierProvider> {

    public SupplierProviderFactory(ProviderConfigurationManager configurationManager) {
        super(SupplierProviderDescriptor.class, configurationManager);
    }

    @Override
    public SupplierProviderDescriptor getDescriptor(String name) {
        if (ManualSupplierNames.isManual(name)) {
            return new ManualSupplierDescriptor(ManualSupplierNames.label(name));
        }
        return super.getDescriptor(name);
    }

    @Override
    public String resolveCredentialName(SupplierProviderDescriptor descriptor) {
        return descriptor.name();
    }
}
