package pl.commercelink.warehouse.api;

import pl.commercelink.invoicing.api.BillingParty;

import java.util.Map;

public record InvoiceSyncRequest(String deliveryId, Map<String, Double> costsByMfn, BillingParty counterparty) {

}
