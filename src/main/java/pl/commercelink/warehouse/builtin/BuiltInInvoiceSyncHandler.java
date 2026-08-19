package pl.commercelink.warehouse.builtin;

import pl.commercelink.documents.DocumentType;
import pl.commercelink.warehouse.api.InvoiceSyncHandler;
import pl.commercelink.warehouse.api.InvoiceSyncRequest;

import java.util.Map;

class BuiltInInvoiceSyncHandler implements InvoiceSyncHandler {

    private final String storeId;
    private final WarehouseDocumentRepository warehouseDocumentRepository;
    private final WarehouseDocumentItemRepository warehouseDocumentItemRepository;

    BuiltInInvoiceSyncHandler(
            String storeId,
            WarehouseDocumentRepository warehouseDocumentRepository,
            WarehouseDocumentItemRepository warehouseDocumentItemRepository
    ) {
        this.storeId = storeId;
        this.warehouseDocumentRepository = warehouseDocumentRepository;
        this.warehouseDocumentItemRepository = warehouseDocumentItemRepository;
    }

    @Override
    public void sync(InvoiceSyncRequest request) {
        if (request.counterparty() != null) {
            updateGoodsReceiptCounterparty(request);
        }

        if (request.costsByMfn().isEmpty()) {
            return;
        }

        updateWarehouseDocumentItems(request.deliveryId(), request.costsByMfn());
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
}
