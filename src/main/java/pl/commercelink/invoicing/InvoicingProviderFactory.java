package pl.commercelink.invoicing;

import org.springframework.stereotype.Service;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.invoicing.api.InvoicingProviderDescriptor;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.provider.ProviderFactory;
import pl.commercelink.stores.IntegrationType;

@Service
public class InvoicingProviderFactory extends ProviderFactory<InvoicingProviderDescriptor, InvoicingProvider> {

    public InvoicingProviderFactory(ProviderConfigurationManager configurationManager) {
        super(InvoicingProviderDescriptor.class, IntegrationType.INVOICING_PROVIDER, configurationManager);
    }
}
