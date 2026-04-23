package pl.commercelink.warehouse.builtin;

import org.springframework.stereotype.Component;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.taxonomy.TaxonomyResolver;
import pl.commercelink.taxonomy.TaxonomyResolver.ResolvedProduct;
import pl.commercelink.starter.util.OperationResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
class BuiltInDocumentCreationService {

    private static final int MAX_RETRIES = 3;

    private final WarehouseDocumentRepository warehouseDocumentRepository;
    private final WarehouseDocumentItemRepository warehouseDocumentItemRepository;
    private final TaxonomyResolver taxonomyResolver;

    BuiltInDocumentCreationService(
            WarehouseDocumentRepository warehouseDocumentRepository,
            WarehouseDocumentItemRepository warehouseDocumentItemRepository,
            TaxonomyResolver taxonomyResolver
    ) {
        this.warehouseDocumentRepository = warehouseDocumentRepository;
        this.warehouseDocumentItemRepository = warehouseDocumentItemRepository;
        this.taxonomyResolver = taxonomyResolver;
    }

    OperationResult<Document> createDocument(DocumentCreationRequest request) {
        String sequenceKey = request.getType().getSequenceKey(request.getWarehouseId());

        return warehouseDocumentRepository.saveWithSequence(
                request.getStoreId(), sequenceKey, MAX_RETRIES,
                documentNo -> buildDocument(documentNo, request)
        )
                .map(doc -> {
                    saveDocumentItems(doc.getDocumentId(), doc.getCreatedAt(), request.getType(), request.getItems());
                    return OperationResult.success(new Document(doc.getDocumentId(), doc.getDocumentNo(), null, request.getType()));
                })
                .orElseGet(() -> OperationResult.failure("Failed to create document after " + MAX_RETRIES + " attempts due to concurrent access"));
    }

    private WarehouseDocument buildDocument(String documentNo, DocumentCreationRequest request) {
        WarehouseDocument document = new WarehouseDocument(request.getStoreId(), documentNo, request.getType());
        document.setCreatedAt(LocalDateTime.now());
        document.setWarehouseId(request.getWarehouseId());
        document.setIssuer(request.getIssuer());
        document.setCreatedBy(request.getCreatedBy());
        document.setReason(request.getReason());

        if (request.getCounterparty() != null) {
            document.setCounterparty(request.getCounterparty());
        }
        if (request.getDeliveryAddress() != null) {
            document.setDeliveryAddress(request.getDeliveryAddress());
        }
        if (request.hasDeliveryId()) {
            document.setDeliveryId(request.getDeliveryId());
        }
        if (request.hasRmaId()) {
            document.setRmaId(request.getRmaId());
        }
        if (request.hasOrderId()) {
            document.setOrderId(request.getOrderId());
        }
        if (request.getNote() != null) {
            document.setNote(request.getNote());
        }

        return document;
    }

    private void saveDocumentItems(String documentId, LocalDateTime createdAt, DocumentType type, List<DocumentLineItem> items) {
        List<WarehouseDocumentItem> documentItems = new ArrayList<>();
        for (DocumentLineItem item : items) {
            ResolvedProduct resolved = taxonomyResolver.resolve(item.getMfn(), item.getName(), null);
            documentItems.add(new WarehouseDocumentItem(
                    documentId,
                    type,
                    createdAt,
                    item.getDeliveryId(),
                    item.getEan(),
                    item.getMfn(),
                    resolved.name(),
                    item.getQty(),
                    item.getUnitPrice()
            ));
        }
        warehouseDocumentItemRepository.saveAll(documentItems);
    }
}
