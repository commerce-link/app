package pl.commercelink.warehouse.builtin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.warehouse.api.Reservation;
import pl.commercelink.warehouse.api.ReservationItem;

import java.util.Collections;
import java.util.Optional;

@Component
class WarehouseInternalReservationService {

    @Autowired
    private WarehouseRepository warehouseRepository;

    void create(Reservation reservation) {
        for (ReservationItem items : reservation.getItems()) {
            WarehouseItem item = warehouseRepository.findById(reservation.getStoreId(), items.getItemId());
            WarehouseItem targetItem = splitIfNeeded(item, items.getQty());
            if (reservation.isRma()) {
                targetItem.markAsInRMA();
            } else {
                targetItem.markAsReserved();
            }
            warehouseRepository.save(targetItem);
        }
    }

    void remove(Reservation reservation) {
        for (ReservationItem selection : reservation.getItems()) {
            WarehouseItem item = warehouseRepository.findById(reservation.getStoreId(), selection.getItemId());
            int qtyToMove = Math.min(selection.getQty(), item.getQty());

            Optional<WarehouseItem> op = warehouseRepository.findByDeliveryIdAndStatuses(
                            reservation.getStoreId(), item.getDeliveryId(), Collections.singletonList(FulfilmentStatus.Delivered))
                    .stream()
                    .filter(d -> d.canJoinWith(item))
                    .findFirst();

            if (op.isPresent()) {
                WarehouseItem existing = op.get();
                existing.setQty(existing.getQty() + qtyToMove);
                warehouseRepository.save(existing);

                if (qtyToMove >= item.getQty()) {
                    warehouseRepository.delete(item);
                } else {
                    item.setQty(item.getQty() - qtyToMove);
                    warehouseRepository.save(item);
                }
            } else {
                WarehouseItem targetItem = splitIfNeeded(item, qtyToMove);
                targetItem.markAsAvailable();
                warehouseRepository.save(targetItem);
            }
        }
    }

    private WarehouseItem splitIfNeeded(WarehouseItem item, int requestedQty) {
        if (requestedQty >= item.getQty()) {
            return item;
        }
        WarehouseItem splitItem = item.splitOff(requestedQty);
        warehouseRepository.save(item);
        return splitItem;
    }

}
