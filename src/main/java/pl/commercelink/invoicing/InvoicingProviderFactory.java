package pl.commercelink.invoicing;

import org.springframework.stereotype.Service;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.invoicing.api.InvoicingProviderDescriptor;
import pl.commercelink.provider.ProviderCallLimiter;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.provider.ProviderFactory;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;

@Service
public class InvoicingProviderFactory extends ProviderFactory<InvoicingProviderDescriptor, InvoicingProvider> {

    private final ProviderCallLimiter limiter;

    public InvoicingProviderFactory(ProviderConfigurationManager configurationManager, ProviderCallLimiter limiter) {
        super(InvoicingProviderDescriptor.class, IntegrationType.INVOICING_PROVIDER, configurationManager);
        this.limiter = limiter;
    }

    @Override
    public InvoicingProvider get(Store store, String providerName) {
        return limiter.wrap(InvoicingProvider.class, providerName, super.get(store, providerName));
    }
}
