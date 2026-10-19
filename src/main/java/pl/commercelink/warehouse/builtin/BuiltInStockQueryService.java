package pl.commercelink.warehouse.builtin;

import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.SerialNumbers;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.StockSummary;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class BuiltInStockQueryService implements StockQueryService {

    private final WarehouseRepository warehouseRepository;

    BuiltInStockQueryService(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    @Override
    public List<WarehouseItemView> searchAvailableByMfns(String storeId, Collection<String> mfns) {
        return warehouseRepository.findAllAvailableByMfns(storeId, mfns)
                .stream()
                .filter(WarehouseItem::isSealed)
                .map(this::fromInternal)
                .collect(Collectors.toList());
    }

    @Override
    public List<WarehouseItemView> searchNotSealedAvailableByMfns(String storeId, Collection<String> mfns) {
        return warehouseRepository.findAllAvailableByMfns(storeId, mfns)
                .stream()
                .filter(item -> !item.isSealed())
                .map(this::fromInternal)
                .collect(Collectors.toList());
    }

    @Override
    public List<WarehouseItemView> searchByMfns(String storeId, Collection<String> mfns) {
        return warehouseRepository.findAllByMfns(storeId, mfns)
                .stream()
                .filter(WarehouseItem::isSealed)
                .filter(e -> !e.hasOneOfTheStatuses(
                        FulfilmentStatus.InRMA,
                        FulfilmentStatus.InExternalService,
                        FulfilmentStatus.Returned,
                        FulfilmentStatus.Replaced,
                        FulfilmentStatus.Destroyed
                ))
                .map(this::fromInternal)
                .collect(Collectors.toList());
    }

    @Override
    public List<WarehouseItemView> findAllBySerialNo(String storeId, String serialNo) {
        return warehouseRepository.findBySerialNoCandidates(storeId, serialNo).stream()
                .filter(item -> SerialNumbers.contains(item.getSerialNo(), serialNo))
                .map(this::fromInternal)
                .toList();
    }

    @Override
    public WarehouseItemView findById(String storeId, String itemId) {
        WarehouseItem warehouseItem = warehouseRepository.findById(storeId, itemId);
        if (warehouseItem == null) {
            return null;
        }
        return fromInternal(warehouseItem);
    }

    @Override
    public List<WarehouseItemView> searchAllAvailableByMfns(String storeId, Collection<String> mfns) {
        return warehouseRepository.findAllAvailableByMfns(storeId, mfns)
                .stream()
                .map(this::fromInternal)
                .collect(Collectors.toList());
    }

    @Override
    public List<WarehouseItemView> searchAllAvailable(String storeId) {
        return findAllAvailable(storeId).stream()
                .map(this::fromInternal)
                .toList();
    }

    @Override
    public StockSummary summarizeAvailable(String storeId) {
        List<WarehouseItem> items = findAllAvailable(storeId);
        Set<String> products = new HashSet<>();
        int inStockQty = 0;
        int inDeliveryQty = 0;
        for (WarehouseItem item : items) {
            if (item.getManufacturerCode() != null) {
                products.add(item.getManufacturerCode());
            }
            if (item.getStatus() == FulfilmentStatus.Ordered) {
                inDeliveryQty += item.getQty();
            } else {
                inStockQty += item.getQty();
            }
        }
        return new StockSummary(products.size(), inStockQty, inDeliveryQty);
    }

    private List<WarehouseItem> findAllAvailable(String storeId) {
        return warehouseRepository.findAllFiltered(storeId, null,
                List.of(FulfilmentStatus.Ordered, FulfilmentStatus.Delivered));
    }

    private WarehouseItemView fromInternal(WarehouseItem warehouseItem) {
        return new WarehouseItemView(
                warehouseItem.getStoreId(),
                warehouseItem.getItemId(),
                warehouseItem.getName(),
                warehouseItem.getEan(),
                warehouseItem.getManufacturerCode(),
                Price.fromNet(warehouseItem.getEffectiveUnitSystemCost(), warehouseItem.getTax()),
                warehouseItem.getQty(),
                warehouseItem.getStatus(),
                warehouseItem.getCondition()
        );
    }
}
