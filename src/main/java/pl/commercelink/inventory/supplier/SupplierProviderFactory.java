package pl.commercelink.inventory.supplier;

import org.springframework.stereotype.Service;
import pl.commercelink.inventory.supplier.api.Supplier;
import pl.commercelink.inventory.supplier.api.SupplierDescriptor;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.provider.ProviderFactory;

@Service
public class SupplierProviderFactory extends ProviderFactory<SupplierDescriptor, Supplier> {

    public SupplierProviderFactory(ProviderConfigurationManager configurationManager) {
        super(SupplierDescriptor.class, null, configurationManager);
    }

    @Override
    public String resolveCredentialName(SupplierDescriptor descriptor) {
        return descriptor.name();
    }
}
