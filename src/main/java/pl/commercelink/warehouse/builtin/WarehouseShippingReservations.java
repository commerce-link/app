package pl.commercelink.warehouse.builtin;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds warehouse items for the shipment being created for them. The goods-out that takes them out of stock follows
 * only once the provider confirms the shipment, seconds or minutes later; until then the hold keeps a second shipment
 * (another tab, another operator) from paying for a second label for the same items. Each hold is a versioned write of
 * the item, so of two shipments started at once only one gets it. A hold left by a command nobody settles expires with
 * ProviderCommandTimeout.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarehouseShippingReservations {

    private final WarehouseRepository warehouseRepository;

    /** Whether every item is still in the warehouse and not held by another shipment: what a new shipment needs. */
    public boolean canShip(String storeId, List<String> itemIds) {
        LocalDateTime now = LocalDateTime.now();
        return itemIds.stream()
                .map(id -> warehouseRepository.findById(storeId, id))
                .allMatch(item -> item != null && item.isNotShippedOut() && !item.isBeingShipped(now));
    }

    /** Holds all the items for the command, or none of them when one is shipped out or held already. */
    public boolean hold(String storeId, List<String> itemIds, String commandId) {
        LocalDateTime now = LocalDateTime.now();
        List<String> held = new ArrayList<>();
        for (String itemId : itemIds) {
            WarehouseItem item = warehouseRepository.findById(storeId, itemId);
            if (item == null || !item.isNotShippedOut() || item.isBeingShipped(now)) {
                release(storeId, held, commandId);
                return false;
            }
            item.holdForShipping(commandId, now);
            try {
                warehouseRepository.save(item);
            } catch (ConditionalCheckFailedException e) {
                // changed since it was read, e.g. held by a shipment started at the same time
                release(storeId, held, commandId);
                return false;
            }
            held.add(itemId);
        }
        return true;
    }

    /** Lets the items go when the command did not create a shipment; items held by another command stay as they are. */
    public void release(String storeId, List<String> itemIds, String commandId) {
        for (String itemId : itemIds) {
            try {
                WarehouseItem item = warehouseRepository.findById(storeId, itemId);
                if (item != null && item.isHeldBy(commandId)) {
                    item.releaseFromShipping();
                    warehouseRepository.save(item);
                }
            } catch (RuntimeException e) {
                // the hold then expires on its own
                log.warn("Warehouse item {} of store {} stays held by shipment command {} until the hold expires",
                        itemId, storeId, commandId, e);
            }
        }
    }
}
