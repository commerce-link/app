package pl.commercelink.invoicing;

import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.invoicing.api.InvoicingProviderDescriptor;
import pl.commercelink.provider.api.ProviderField;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

/** Registered for {@link java.util.ServiceLoader} in {@code META-INF/services}; the target is never really called. */
public class FakeInvoicingProviderDescriptor implements InvoicingProviderDescriptor {

    public static final String NAME = "test-invoicing";
    public static final InvoicingProvider PROVIDER = (InvoicingProvider) Proxy.newProxyInstance(
            InvoicingProvider.class.getClassLoader(), new Class<?>[]{InvoicingProvider.class},
            (proxy, method, args) -> {
                throw new UnsupportedOperationException("not used by the factory wiring tests");
            });

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String displayName() {
        return "Test invoicing";
    }

    @Override
    public List<ProviderField> configurationFields() {
        return List.of();
    }

    @Override
    public InvoicingProvider create(Map<String, String> configuration) {
        return PROVIDER;
    }
}
