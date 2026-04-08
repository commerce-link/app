package pl.commercelink.warehouse.builtin;

import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.warehouse.api.GoodsReceiptItem;
import pl.commercelink.documents.Document;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.warehouse.api.RmaGoodsInHandler;
import pl.commercelink.warehouse.api.RmaGoodsInRequest;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

class BuiltInRmaGoodsInHandler implements RmaGoodsInHandler {

    private final String storeId;
    private final WarehouseRepository warehouseRepository;
    private final BuiltInDocumentCreationService documentCreationService;
    private final WarehouseItemFactory warehouseItemFactory;

    BuiltInRmaGoodsInHandler(
            String storeId,
            WarehouseRepository warehouseRepository,
            BuiltInDocumentCreationService documentCreationService,
            WarehouseItemFactory warehouseItemFactory
    ) {
        this.storeId = storeId;
        this.warehouseRepository = warehouseRepository;
        this.documentCreationService = documentCreationService;
        this.warehouseItemFactory = warehouseItemFactory;
    }

    @Override
    public OperationResult<Document> receive(RmaGoodsInRequest request, boolean documentsGenerationEnabled) {
        for (GoodsReceiptItem goodsReceiptItem : request.getItems()) {
            if (request.isItemsRequireRepair()) {
                moveRmaItemToWarehouseForRepair(goodsReceiptItem);
            } else {
                addToWarehouseStock(goodsReceiptItem);
            }
        }

        if (documentsGenerationEnabled && request.hasDocumentData()) {
            return createGoodsReceiptDocument(request);
        }

        return OperationResult.success();
    }

    private void addToWarehouseStock(GoodsReceiptItem goodsReceiptItem) {
        Optional<WarehouseItem> existing = warehouseRepository.findByDeliveryIdAndStatuses(
                storeId,
                goodsReceiptItem.getDeliveryId(),
                Collections.singletonList(FulfilmentStatus.Delivered)
        )
                .stream()
                .filter(i -> i.hasSameFulfilmentAs(goodsReceiptItem))
                .findFirst();

        if (existing.isPresent()) {
            WarehouseItem warehouseItem = existing.get();
            warehouseItem.setQty(warehouseItem.getQty() + goodsReceiptItem.getQty());
            warehouseRepository.save(warehouseItem);
        } else {
            warehouseRepository.save(warehouseItemFactory.create(storeId, goodsReceiptItem));
        }
    }

    private void moveRmaItemToWarehouseForRepair(GoodsReceiptItem goodsReceiptItem) {
        WarehouseItem warehouseItem = warehouseItemFactory.create(storeId, goodsReceiptItem);
        warehouseItem.markAsInRMA();
        warehouseRepository.save(warehouseItem);
    }

    private OperationResult<Document> createGoodsReceiptDocument(RmaGoodsInRequest request) {
        List<DocumentLineItem> items = request.getItems().stream()
                .map(DocumentLineItem::from)
                .collect(Collectors.toList());

        DocumentCreationRequest documentRequest = DocumentCreationRequest.builder(DocumentType.GoodsReceipt)
                .storeId(storeId)
                .issuer(IssuerDetails.from(request.getIssuer()))
                .counterparty(CounterpartyDetails.from(request.getCounterparty()))
                .warehouseId(request.getWarehouseId())
                .rmaId(request.getRmaId())
                .orderId(request.getOrderId())
                .reason(DocumentReason.CustomerReturn)
                .items(items)
                .createdBy(request.getCreatedBy())
                .build();

        return documentCreationService.createDocument(documentRequest);
    }
}
