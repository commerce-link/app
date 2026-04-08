package pl.commercelink.warehouse.builtin;

import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.Collection;
import java.util.List;
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
                .map(this::fromInternal)
                .collect(Collectors.toList());
    }

    @Override
    public List<WarehouseItemView> searchByMfns(String storeId, Collection<String> mfns) {
        return warehouseRepository.findAllByMfns(storeId, mfns)
                .stream()
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
    public WarehouseItemView findBySerialNo(String storeId, String serialNo) {
        WarehouseItem warehouseItem = warehouseRepository.findBySerialNo(storeId, serialNo);
        if (warehouseItem == null) {
            return null;
        }
        return fromInternal(warehouseItem);
    }

    private WarehouseItemView fromInternal(WarehouseItem warehouseItem) {
        return new WarehouseItemView(
                warehouseItem.getStoreId(),
                warehouseItem.getItemId(),
                warehouseItem.getEan(),
                warehouseItem.getManufacturerCode(),
                Price.fromNet(warehouseItem.getCost(), warehouseItem.getTax()),
                warehouseItem.getQty(),
                warehouseItem.getStatus()
        );
    }
}
