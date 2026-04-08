package pl.commercelink.warehouse.builtin;

import pl.commercelink.documents.DocumentType;
import pl.commercelink.warehouse.api.GoodsOutRequest;
import pl.commercelink.warehouse.api.GoodsOutHandler;
import pl.commercelink.documents.Document;
import pl.commercelink.starter.util.OperationResult;

import java.util.List;
import java.util.stream.Collectors;

class BuiltInGoodsOutHandler implements GoodsOutHandler {

    private final BuiltInDocumentCreationService documentCreationService;

    BuiltInGoodsOutHandler(BuiltInDocumentCreationService documentCreationService) {
        this.documentCreationService = documentCreationService;
    }

    @Override
    public OperationResult<Document> issue(GoodsOutRequest request, boolean documentsGenerationEnabled) {
        if (documentsGenerationEnabled) {
            List<DocumentLineItem> items = request.getItems().stream()
                    .map(DocumentLineItem::from)
                    .collect(Collectors.toList());

            DocumentCreationRequest documentRequest = DocumentCreationRequest.builder(DocumentType.GoodsIssue)
                    .storeId(request.getStoreId())
                    .issuer(IssuerDetails.from(request.getIssuer()))
                    .counterparty(CounterpartyDetails.from(request.getCounterparty()))
                    .deliveryAddress(request.getDeliveryAddress() != null ? DeliveryAddress.from(request.getDeliveryAddress()) : null)
                    .warehouseId(request.getWarehouseId())
                    .orderId(request.getOrderId())
                    .reason(request.getReason())
                    .items(items)
                    .createdBy(request.getCreatedBy())
                    .build();

            return documentCreationService.createDocument(documentRequest);
        }
        return OperationResult.success();
    }
}
