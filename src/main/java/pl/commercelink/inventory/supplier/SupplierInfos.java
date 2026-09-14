package pl.commercelink.inventory.supplier;

import pl.commercelink.inventory.supplier.api.SupplierInfo;

final class SupplierInfos {

    private SupplierInfos() {
    }

    /** The same supplier metadata under a connection identity instead of the adapter type name. */
    static SupplierInfo renamed(SupplierInfo info, String name) {
        return new SupplierInfo(name, info.type(), info.accuracyScore(), info.origin(),
                info.shippingPolicy(), info.partnerSiteUrlTemplate());
    }
}
