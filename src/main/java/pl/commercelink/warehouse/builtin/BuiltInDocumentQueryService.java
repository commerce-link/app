package pl.commercelink.warehouse.builtin;

import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.warehouse.api.DocumentQueryService;

import java.util.Collections;
import java.util.List;

class BuiltInDocumentQueryService implements DocumentQueryService {
    @Override
    public List<Allocation> fetchAllocations(Delivery delivery) {
        return Collections.emptyList();
    }
}
