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

    private final String attributeName;
}
