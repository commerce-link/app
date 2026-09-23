package pl.commercelink.invoicing;

import org.junit.jupiter.api.Test;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.provider.ProviderCallLimiter;
import pl.commercelink.provider.ProviderCallLimitProperties;
import pl.commercelink.provider.ProviderConfigurationManager;
import pl.commercelink.stores.Store;

import java.time.Duration;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Invoicing calls share the {@code fakturownia} bucket with receipts (see {@link pl.commercelink.provider.ProviderCallLimiter}),
 * but a queue-driven invoicing call must never wait for a permit longer than its own message's visibility timeout;
 * the actual waiting behaviour of a short acquire timeout is pinned at the limiter level in
 * {@code ProviderCallLimiterTest}. This test only pins that the factory asks for the configured short timeout, not
 * the shared default.
 */
class InvoicingProviderFactoryTest {

    @Test
    void invoicingCallsAreWrappedWithTheConfiguredShortAcquireTimeoutNotTheSharedDefault() {
        ProviderConfigurationManager configurationManager = mock(ProviderConfigurationManager.class);
        when(configurationManager.loadConfiguration(any(Store.class), eq(FakeInvoicingProviderDescriptor.NAME)))
                .thenReturn(Map.of());
        ProviderCallLimiter limiter = mock(ProviderCallLimiter.class);
        ProviderCallLimitProperties properties = new ProviderCallLimitProperties(
                Duration.ofMinutes(2), Duration.ofSeconds(20), Map.of());
        InvoicingProviderFactory factory = new InvoicingProviderFactory(configurationManager, limiter, properties);
        Store store = new Store();
        store.setStoreId("store-1");

        factory.get(store, FakeInvoicingProviderDescriptor.NAME);

        verify(limiter).wrap(InvoicingProvider.class, FakeInvoicingProviderDescriptor.NAME,
                FakeInvoicingProviderDescriptor.PROVIDER, Duration.ofSeconds(20));
    }
}
