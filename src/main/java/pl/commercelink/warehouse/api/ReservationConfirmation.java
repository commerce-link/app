package pl.commercelink.warehouse.api;

import pl.commercelink.invoicing.api.Price;

public record ReservationConfirmation(
        String deliveryId,
        String ean,
        String mfn,
        Price cost,
        int qty,
        boolean inStock
) {}