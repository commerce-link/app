package pl.commercelink.warehouse.builtin;

import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.warehouse.api.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

class BuiltInReservationService implements ReservationService {

    private final WarehouseRepository warehouseRepository;
    private final DeliveriesRepository deliveriesRepository;
    private final WarehouseItemFactory warehouseItemFactory;

    BuiltInReservationService(DeliveriesRepository deliveriesRepository, WarehouseRepository warehouseRepository, WarehouseItemFactory warehouseItemFactory) {
        this.deliveriesRepository = deliveriesRepository;
        this.warehouseRepository = warehouseRepository;
        this.warehouseItemFactory = warehouseItemFactory;
    }

    @Override
    public Reservation create(Reservation reservation) {
        reserveAvailableStock(reservation);
        reserveIncomingStock(reservation);
        return reservation;
    }

    private void reserveAvailableStock(Reservation reservation) {
        reserveStock(reservation, FulfilmentStatus.Delivered);
    }

    private void reserveIncomingStock(Reservation reservation) {
        reserveStock(reservation, FulfilmentStatus.Ordered);
    }

    @Override
    public void remove(Reservation reservation) {
        for (ReservationRemovalItem removalItem : reservation.getRemovalItems()) {
            if (reservation.isRma()) {
                WarehouseItem warehouseItem = warehouseItemFactory.create(reservation.getStoreId(), removalItem);
                warehouseItem.markAsInRMA();
                warehouseRepository.save(warehouseItem);
            } else {
                Optional<WarehouseItem> optional = warehouseRepository.findByDeliveryIdAndStatuses(
                                reservation.getStoreId(),
                                removalItem.getDeliveryId(),
                                Arrays.asList(FulfilmentStatus.Ordered, FulfilmentStatus.Delivered)
                        )
                        .stream()
                        .filter(i -> i.hasSameFulfilmentAs(removalItem))
                        .findFirst();

                if (optional.isPresent()) {
                    WarehouseItem warehouseItem = optional.get();
                    warehouseItem.setQty(warehouseItem.getQty() + removalItem.getQty());
                    warehouseRepository.save(warehouseItem);
                } else {
                    warehouseRepository.save(warehouseItemFactory.create(reservation.getStoreId(), removalItem));
                }
            }
        }
    }

    private void reserveStock(Reservation reservation, FulfilmentStatus fulfilmentStatus) {
        for (ReservationItem item : reservation.getUnfulfilledItems()) {
            List<WarehouseItem> inStockItems = warehouseRepository.findAllByMfnAndStatus(
                            reservation.getStoreId(), item.getMfn(), fulfilmentStatus
                    ).stream()
                    .sorted(fifoSortOrder(reservation.getStoreId()))
                    .toList();

            for (WarehouseItem inStockItem : inStockItems) {
                if (item.getRemainingQty() == 0) {
                    break;
                }

                item.add(reserve(inStockItem, item.getRemainingQty(), fulfilmentStatus == FulfilmentStatus.Delivered));
            }
        }
    }

    private ReservationConfirmation reserve(WarehouseItem warehouseItem, int qty, boolean inStock) {
        int qtyFromThisItem = Math.min(qty, warehouseItem.getQty());

        warehouseItem.setQty(warehouseItem.getQty() - qtyFromThisItem);
        if (warehouseItem.getQty() != 0) {
            warehouseRepository.save(warehouseItem);
        } else {
            warehouseRepository.delete(warehouseItem);
        }

        return new ReservationConfirmation(
                warehouseItem.getDeliveryId(),
                warehouseItem.getEan(),
                warehouseItem.getManufacturerCode(),
                Price.fromNet(warehouseItem.getCost(), warehouseItem.getTax()),
                qtyFromThisItem,
                inStock
        );
    }

    private Comparator<WarehouseItem> fifoSortOrder(String storeId) {
        return Comparator.comparing(i -> getDeliveryDate(storeId, i), Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private LocalDateTime getDeliveryDate(String storeId, WarehouseItem warehouseItem) {
        // internal reception items will not have deliveryId or might be missing delivery record
        if (warehouseItem.getDeliveryId() == null) {
            return null;
        }

        var delivery = deliveriesRepository.findById(storeId, warehouseItem.getDeliveryId());
        if (delivery == null) {
            return null;
        }

        // check if delivery has been received if so return receivedAt date
        if (delivery.hasBeenReceived()) {
            return delivery.getReceivedAt();
        }

        // in case of partial delivery where item has been received we assume delivery date is now
        if (warehouseItem.isDelivered()) {
            return LocalDateTime.now();
        }

        // in any other case when estimatedDeliveryDate is missing we set a delivery far into future
        if (delivery.getEstimatedDeliveryAt() == null) {
            return LocalDateTime.now().plusDays(5);
        }

        LocalDateTime estimatedDeliveryAt = delivery.getEstimatedDeliveryAt().atTime(16, 0, 0);
        if (LocalDateTime.now().isAfter(estimatedDeliveryAt)) {
            return LocalDateTime.now().plusDays(1);
        }

        return estimatedDeliveryAt;
    }
}
