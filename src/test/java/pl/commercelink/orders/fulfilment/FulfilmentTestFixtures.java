package pl.commercelink.orders.fulfilment;

import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierType;

import java.util.List;

final class FulfilmentTestFixtures {

    private FulfilmentTestFixtures() {
    }

    static FulfilmentGroup group(String provider, String orderItemId, double priceNet) {
        FulfilmentSource source = new FulfilmentSource();
        source.setProvider(provider);
        source.setPriceNet(priceNet);
        source.setPriceGross(priceNet);
        source.setQty(10);
        FulfilmentAllocation allocation = new FulfilmentAllocation();
        allocation.setOrderId("order-1");
        allocation.setOrderItemId(orderItemId);
        allocation.setOrderItemQty(1);
        allocation.setOrderItemPrice(priceNet);
        return new FulfilmentGroup(source, List.of(allocation), false);
    }

    static SupplierInfo supplier(String name, ShippingCostPolicy costPolicy) {
        return new SupplierInfo(name, SupplierType.Distributor, 1, "PL", new ShippingPolicy(new ShippingTerms(1, costPolicy)));
    }
}
