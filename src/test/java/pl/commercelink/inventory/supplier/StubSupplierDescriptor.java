package pl.commercelink.inventory.supplier;

import pl.commercelink.inventory.supplier.api.FeedData;
import pl.commercelink.inventory.supplier.api.FeedFormat;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.Supplier;
import pl.commercelink.inventory.supplier.api.SupplierDescriptor;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierType;

import java.util.Map;
import java.util.Optional;

public class StubSupplierDescriptor implements SupplierDescriptor {

    public static final SupplierInfo INFO = new SupplierInfo("Stub", SupplierType.Distributor, 1, "PL",
            new ShippingPolicy(new ShippingTerms(1, new ShippingCostPolicy.Free())), null);

    @Override
    public Supplier create(Map<String, String> config) {
        return () -> Optional.of(FeedData.csv(config.getOrDefault("url", "x").getBytes()));
    }

    @Override
    public FeedFormat feedFormat() {
        return new FeedFormat.Csv(row -> null, ';');
    }

    @Override
    public SupplierInfo supplierInfo() {
        return INFO;
    }
}
