package pl.commercelink.receipts;

import org.springframework.stereotype.Service;
import pl.commercelink.provider.ProviderCallLimiter;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.provider.ProviderFactory;
import pl.commercelink.receipts.api.ReceiptProvider;
import pl.commercelink.receipts.api.ReceiptProviderDescriptor;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;

/**
 * Receipt providers of a store. The provider's network methods (its non-{@code default} SPI methods) go through the
 * shared provider call limiter; {@code default} methods are local helpers and pass straight through without a permit.
 */
@Service
public class ReceiptProviderFactory extends ProviderFactory<ReceiptProviderDescriptor, ReceiptProvider> {

    private final ProviderCallLimiter limiter;

    public ReceiptProviderFactory(ProviderConfigurationManager configurationManager, ProviderCallLimiter limiter) {
        super(ReceiptProviderDescriptor.class, IntegrationType.RECEIPT_PROVIDER, configurationManager);
        this.limiter = limiter;
    }

    @Override
    public ReceiptProvider get(Store store, String providerName) {
        return limiter.wrap(ReceiptProvider.class, providerName, super.get(store, providerName));
    }
}
