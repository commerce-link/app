package pl.commercelink.inventory.supplier;

import org.springframework.stereotype.Service;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.provider.ProviderFactory;

@Service
public class SupplierProviderFactory extends ProviderFactory<SupplierProviderDescriptor, SupplierProvider> {

    public SupplierProviderFactory(ProviderConfigurationManager configurationManager) {
        super(SupplierProviderDescriptor.class, configurationManager);
    }

    @Override
    protected String descriptorNameFor(String providerName) {
        return SupplierIdentity.typeOf(providerName);
    }

    /** A connection's secret is keyed by its identity, so two instances of one type never share credentials. */
    @Override
    protected String credentialNameFor(String providerName, SupplierProviderDescriptor descriptor) {
        return providerName;
    }
}
