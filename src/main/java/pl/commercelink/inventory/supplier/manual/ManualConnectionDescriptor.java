package pl.commercelink.inventory.supplier.manual;

import pl.commercelink.inventory.supplier.api.FeedFormat;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;

import java.util.Map;
import java.util.Optional;

/**
 * Manual-supplier descriptor built straight from a connection identity (`manual:Label` or
 * `manual-token`), replacing supplier-manual's label-based constructor which can only produce the
 * legacy shape.
 */
public final class ManualConnectionDescriptor implements SupplierProviderDescriptor {

    private final String identity;

    public ManualConnectionDescriptor(String identity) {
        this.identity = identity;
    }

    @Override
    public SupplierInfo supplierInfo() {
        return ManualSupplierInfos.forIdentity(identity);
    }

    @Override
    public FeedFormat feedFormat() {
        return new FeedFormat.Csv(new ManualCsvRowParser(identity), ';');
    }

    @Override
    public SupplierProvider create(Map<String, String> configuration) {
        return Optional::empty;
    }
}
