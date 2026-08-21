package pl.commercelink.demo;

import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.warehouse.builtin.WarehouseDocument;
import pl.commercelink.warehouse.builtin.WarehouseDocumentItem;
import pl.commercelink.warehouse.builtin.WarehouseDocumentSequence;
import pl.commercelink.warehouse.builtin.WarehouseItem;

import java.util.List;

record WarehouseStock(List<WarehouseItem> items,
                      List<Delivery> deliveries,
                      List<WarehouseDocument> documents,
                      List<WarehouseDocumentItem> documentItems,
                      List<WarehouseDocumentSequence> sequences) {
}
