package pl.commercelink.warehouse.builtin;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class WarehouseItemUpdateService {

    private final WarehouseRepository warehouseRepository;

    /** Returns the id of the item that holds the data after the save: the joined one when a condition change merged them. */
    String update(String storeId, WarehouseItem existingItem, WarehouseItem updatedItem) {
        boolean conditionChanged = existingItem.getCondition() != updatedItem.getCondition();
        existingItem.update(updatedItem);

        if (conditionChanged) {
            Optional<WarehouseItem> joinable = findJoinable(storeId, existingItem);
            if (joinable.isPresent()) {
                WarehouseItem target = joinable.get();
                target.absorb(existingItem);
                warehouseRepository.save(target);
                warehouseRepository.delete(existingItem);
                return target.getItemId();
            }
        }

        warehouseRepository.save(existingItem);
        return existingItem.getItemId();
    }

    private Optional<WarehouseItem> findJoinable(String storeId, WarehouseItem item) {
        if (item.getDeliveryId() == null) {
            return Optional.empty();
        }
        return warehouseRepository.findByDeliveryIdAndStatuses(storeId, item.getDeliveryId(), List.of(item.getStatus()))
                .stream()
                .filter(candidate -> !candidate.getItemId().equals(item.getItemId()))
                .filter(candidate -> candidate.canJoinWith(item))
                .findFirst();
    }
}
