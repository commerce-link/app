package pl.commercelink.inventory.deliveries;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierParcel;

import java.util.List;

import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyEan;

final class DropshipParcelMatcher {

    private DropshipParcelMatcher() {
    }

    static List<Allocation> select(SupplierParcel parcel, List<Allocation> open, boolean absorbRemaining) {
        if (parcel.lines().isEmpty() || absorbRemaining) {
            return List.copyOf(open);
        }
        return open.stream()
                .filter(allocation -> parcel.lines().stream().anyMatch(line -> matches(line, allocation)))
                .toList();
    }

    private static boolean matches(SupplierOrderLine line, Allocation allocation) {
        if (StringUtils.isNotBlank(line.ean()) && StringUtils.isNotBlank(allocation.getEan())
                && unifyEan(line.ean()).equals(unifyEan(allocation.getEan()))) {
            return true;
        }
        return StringUtils.isNotBlank(line.mfn()) && StringUtils.isNotBlank(allocation.getMfn())
                && line.mfn().trim().equalsIgnoreCase(allocation.getMfn().trim());
    }
}
