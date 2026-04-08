package pl.commercelink.warehouse.api;

import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.Delivery;

import java.util.List;

public interface DocumentQueryService {
    List<Allocation> fetchAllocations(Delivery delivery);
}
