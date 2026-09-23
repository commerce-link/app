package pl.commercelink.invoicing;

import org.springframework.stereotype.Service;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.invoicing.api.InvoicingProviderDescriptor;
import pl.commercelink.provider.ProviderCallLimiter;
import pl.commercelink.provider.ProviderCallLimitProperties;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.provider.ProviderFactory;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;

import java.time.Duration;

/**
 * Invoicing calls share the {@code fakturownia} bucket with receipts, but queue-driven invoicing
 * (order-invoicing-queue.fifo) must never wait for a permit longer than the message stays invisible, or the queue
 * redelivers it while this very call is still queued behind another instance's; receipts keep the shared default.
 */
@Service
public class InvoicingProviderFactory extends ProviderFactory<InvoicingProviderDescriptor, InvoicingProvider> {

    private final ProviderCallLimiter limiter;
    private final Duration acquireTimeout;

    public InvoicingProviderFactory(ProviderConfigurationManager configurationManager, ProviderCallLimiter limiter,
            ProviderCallLimitProperties properties) {
        super(InvoicingProviderDescriptor.class, IntegrationType.INVOICING_PROVIDER, configurationManager);
        this.limiter = limiter;
        this.acquireTimeout = properties.invoicingAcquireTimeout();
    }

    @Override
    public InvoicingProvider get(Store store, String providerName) {
        return limiter.wrap(InvoicingProvider.class, providerName, super.get(store, providerName), acquireTimeout);
    }
}
