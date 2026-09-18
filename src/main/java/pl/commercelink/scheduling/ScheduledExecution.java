package pl.commercelink.scheduling;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ScheduledExecution {
    ORDERS_IMPORT("ordersImport"),
    RETURNS_IMPORT("returnsImport"),
    SUPPLIER_FEED("supplierFeed"),
    PRICELIST("pricelist");

    private static final String DIMENSION_SEPARATOR = "#";

    private final String attributeName;

    public String attributeNameFor(String dimension) {
        return attributeName + DIMENSION_SEPARATOR + dimension;
    }
}
