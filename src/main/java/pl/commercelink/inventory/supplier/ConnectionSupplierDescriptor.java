package pl.commercelink.inventory.supplier;

import pl.commercelink.inventory.supplier.api.FeedFormat;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.provider.api.AuthConfig;
import pl.commercelink.provider.api.EventBinding;
import pl.commercelink.provider.api.ProviderField;

import java.util.List;
import java.util.Map;

/**
 * The adapter descriptor of one store connection: same feed format, fields and provider factory as
 * the type, but every SupplierInfo it hands out carries the connection identity as its name, so
 * feed rows, S3 keys and log lines are attributed to this connection and not to the type.
 */
public final class ConnectionSupplierDescriptor implements SupplierProviderDescriptor {

    private final String identity;
    private final SupplierProviderDescriptor delegate;
    private final SupplierInfo info;

    public ConnectionSupplierDescriptor(String identity, SupplierProviderDescriptor delegate) {
        this.identity = identity;
        this.delegate = delegate;
        SupplierInfo typeInfo = delegate.supplierInfo();
        this.info = identity.equals(typeInfo.name()) ? typeInfo : SupplierInfos.renamed(typeInfo, identity);
    }

    @Override
    public SupplierInfo supplierInfo() {
        return info;
    }

    @Override
    public FeedFormat feedFormat() {
        return delegate.feedFormat();
    }

    @Override
    public String name() {
        return identity;
    }

    @Override
    public String displayName() {
        return identity;
    }

    @Override
    public List<ProviderField> configurationFields() {
        return delegate.configurationFields();
    }

    @Override
    public SupplierProvider create(Map<String, String> configuration) {
        return delegate.create(configuration);
    }

    @Override
    public SupplierProvider create(Map<String, String> configuration, Map<String, Object> context) {
        return delegate.create(configuration, context);
    }

    @Override
    public Map<String, String> metadata() {
        return delegate.metadata();
    }

    @Override
    public AuthConfig authConfig() {
        return delegate.authConfig();
    }

    @Override
    public List<EventBinding<?>> bindings() {
        return delegate.bindings();
    }
}
