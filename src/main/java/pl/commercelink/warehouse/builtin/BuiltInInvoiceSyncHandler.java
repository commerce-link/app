package pl.commercelink.warehouse.builtin;

import pl.commercelink.documents.DocumentType;
import pl.commercelink.warehouse.api.InvoiceSyncHandler;
import pl.commercelink.warehouse.api.InvoiceSyncRequest;

import java.util.Map;

class BuiltInInvoiceSyncHandler implements InvoiceSyncHandler {

    private final String storeId;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseDocumentRepository warehouseDocumentRepository;
    private final WarehouseDocumentItemRepository warehouseDocumentItemRepository;

    BuiltInInvoiceSyncHandler(
            String storeId,
            WarehouseRepository warehouseRepository,
            WarehouseDocumentRepository warehouseDocumentRepository,
            WarehouseDocumentItemRepository warehouseDocumentItemRepository
    ) {
        this.storeId = storeId;
        this.warehouseRepository = warehouseRepository;
        this.warehouseDocumentRepository = warehouseDocumentRepository;
        this.warehouseDocumentItemRepository = warehouseDocumentItemRepository;
    }

    @Override
    public double sync(InvoiceSyncRequest request) {
        Map<String, Double> costsByMfn = request.costsByMfn();

        if (request.counterparty() != null) {
            updateGoodsReceiptCounterparty(request);
        }

        if (costsByMfn.isEmpty()) {
            return 0;
        }

        updateWarehouseDocumentItems(request.deliveryId(), costsByMfn);
        return updateWarehouseItems(request.deliveryId(), costsByMfn);
    }

    private void updateGoodsReceiptCounterparty(InvoiceSyncRequest request) {
        CounterpartyDetails counterparty = CounterpartyDetails.from(request.counterparty());
        for (WarehouseDocument document : warehouseDocumentRepository.findByDeliveryId(storeId, request.deliveryId())) {
            if (document.getType() == DocumentType.GoodsReceipt) {
                document.setCounterparty(counterparty);
                warehouseDocumentRepository.save(document);
            }
        }
    }

    private void updateWarehouseDocumentItems(String deliveryId, Map<String, Double> costsByMfn) {
        for (WarehouseDocumentItem item : warehouseDocumentItemRepository.findByDeliveryId(deliveryId)) {
            if (costsByMfn.containsKey(item.getMfn())) {
                double delta = item.updateUnitPrice(costsByMfn.get(item.getMfn()));
                if (delta != 0) {
                    warehouseDocumentItemRepository.save(item);
                }
            }
        }
    }

    private double updateWarehouseItems(String deliveryId, Map<String, Double> costsByMfn) {
        double delta = 0;
        for (WarehouseItem item : warehouseRepository.findByDeliveryId(storeId, deliveryId)) {
            if (costsByMfn.containsKey(item.getManufacturerCode())) {
                double itemDelta = item.updateCost(costsByMfn.get(item.getManufacturerCode()));
                if (itemDelta == 0) {
                    continue;
                }
                delta += itemDelta;
                warehouseRepository.save(item);
            }
        }
        return delta;
    }
}
