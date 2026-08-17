package pl.commercelink.warehouse.builtin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.AllocationType;
import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.fulfilment.FulfilmentItem;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class WarehouseAllocationsManager {

    @Autowired
    private WarehouseItemFactory warehouseItemFactory;
    @Autowired
    private WarehouseRepository warehouseRepository;

    public List<Allocation> fetchAll(String storeId) {
        return warehouseRepository.findAll(storeId, FulfilmentStatus.Allocation)
                .stream()
                .map(Allocation::fromWarehouseItem)
                .collect(Collectors.toList());
    }

    public List<Allocation> fetchAll(String storeId, String deliveryId) {
        return warehouseRepository.findByDeliveryId(storeId, deliveryId)
                .stream()
                .map(Allocation::fromWarehouseItem)
                .collect(Collectors.toList());
    }

    public void create(String storeId, List<FulfilmentItem> items) {
        List<WarehouseItem> warehouseItems = items
                .stream()
                .filter(FulfilmentItem::isAccepted)
                .map(c -> createWarehouseItemFromFulfilmentItem(storeId, c))
                .collect(Collectors.toList());

        warehouseRepository.batchSave(warehouseItems);
    }

    public void schedule(String storeId, List<String> itemIds) {
        for (String itemId : itemIds) {
            WarehouseItem item = warehouseRepository.findById(storeId, itemId);
            if (item.isReadyForAllocation()) {
                item.markAsInAllocation();
                warehouseRepository.save(item);
            }
        }
    }

    public void commit(String storeId, String deliveryId, String provider, List<DeliveryItem> items) {
        for (DeliveryItem item : items) {
            commitAllocations(storeId, deliveryId, provider, item);
        }
    }

    public boolean updateFulfilment(String storeId, String provider, String itemId, String ean, String mfn, double unitCost) {
        WarehouseItem warehouseItem = warehouseRepository.findById(storeId, itemId);
        if (warehouseItem == null || !warehouseItem.updateFulfilment(provider, ean, mfn, unitCost)) {
            return false;
        }

        warehouseRepository.save(warehouseItem);
        return true;
    }

    public void createOrdered(String storeId, String deliveryId, String provider, DeliveryItem item, int qty) {
        WarehouseItem warehouseItem = warehouseItemFactory.create(storeId, provider, item, qty);
        warehouseItem.markAsOrdered(deliveryId, item.getUnitCost());
        warehouseRepository.save(warehouseItem);
    }

    public void increaseQty(String storeId, String itemId, int amount) {
        WarehouseItem warehouseItem = warehouseRepository.findById(storeId, itemId);
        warehouseItem.setQty(warehouseItem.getQty() + amount);
        warehouseRepository.save(warehouseItem);
    }

    public int decreaseQty(String storeId, String itemId, int amount) {
        WarehouseItem warehouseItem = warehouseRepository.findById(storeId, itemId);
        int removed = Math.min(amount, warehouseItem.getQty());
        if (removed == warehouseItem.getQty()) {
            warehouseRepository.delete(warehouseItem);
        } else {
            warehouseItem.setQty(warehouseItem.getQty() - removed);
            warehouseRepository.save(warehouseItem);
        }
        return removed;
    }

    public void reassign(String storeId, String targetDeliveryId, List<Allocation> allocations) {
        for (Allocation allocation : allocations) {
            WarehouseItem warehouseItem = warehouseRepository.findById(storeId, allocation.getKey().getItemId());
            warehouseItem.setDeliveryId(targetDeliveryId);
            warehouseRepository.save(warehouseItem);
        }
    }

    public void remove(String storeId, String itemId) {
        WarehouseItem warehouseItem = warehouseRepository.findById(storeId, itemId);
        warehouseRepository.delete(warehouseItem);
    }

    public void release(String storeId, String deliveryId) {
        for (WarehouseItem item : warehouseRepository.findByDeliveryId(storeId, deliveryId)) {
            item.returnToAllocationPool();
            warehouseRepository.save(item);
        }
    }

    private WarehouseItem createWarehouseItemFromFulfilmentItem(String storeId, FulfilmentItem item) {
        return warehouseItemFactory.create(storeId, item);
    }

    private void commitAllocations(String storeId, String deliveryId, String provider, DeliveryItem item) {
        Allocation allocation = item.getSelectedAllocations(AllocationType.Warehouse).stream()
                .findFirst()
                .orElse(null);

        int warehouseQtyAdjustment = item.getWarehouseQtyAdjustment();

        if (allocation != null) {
            updateExistingWarehouseItem(storeId, deliveryId, allocation, item.getUnitCost(), warehouseQtyAdjustment);
        } else if (warehouseQtyAdjustment > 0) {
            createNewWarehouseItem(storeId, deliveryId, provider, item);
        }
    }

    private void updateExistingWarehouseItem(String storeId, String deliveryId, Allocation allocation, double unitCost, int qtyAdjustment) {
        WarehouseItem warehouseItem = warehouseRepository.findById(storeId, allocation.getKey().getItemId());
        warehouseItem.markAsOrdered(deliveryId, unitCost);
        if (qtyAdjustment != 0) {
            int newQty = warehouseItem.getQty() + qtyAdjustment;
            if (newQty > 0) {
                warehouseItem.setQty(newQty);
                warehouseRepository.save(warehouseItem);
            } else {
                warehouseRepository.delete(warehouseItem);
            }
        } else {
            warehouseRepository.save(warehouseItem);
        }
    }

    private void createNewWarehouseItem(String storeId, String deliveryId, String provider, DeliveryItem item) {
        WarehouseItem warehouseItem = warehouseItemFactory.create(storeId, provider, item);
        warehouseItem.markAsOrdered(deliveryId, item.getUnitCost());
        warehouseRepository.save(warehouseItem);
    }
}
