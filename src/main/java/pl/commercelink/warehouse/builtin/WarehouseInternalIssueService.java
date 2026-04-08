package pl.commercelink.warehouse.builtin;

import org.springframework.stereotype.Service;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.invoicing.api.BillingParty;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.WarehouseConfiguration;
import pl.commercelink.documents.Document;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.warehouse.api.ReservationItem;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
class WarehouseInternalIssueService {

    private final WarehouseRepository warehouseRepository;
    private final StoresRepository storesRepository;
    private final InvoicingProviderFactory invoicingProviderFactory;
    private final BuiltInDocumentCreationService documentCreationService;

    WarehouseInternalIssueService(
            WarehouseRepository warehouseRepository,
            StoresRepository storesRepository,
            InvoicingProviderFactory invoicingProviderFactory,
            BuiltInDocumentCreationService documentCreationService
    ) {
        this.warehouseRepository = warehouseRepository;
        this.storesRepository = storesRepository;
        this.invoicingProviderFactory = invoicingProviderFactory;
        this.documentCreationService = documentCreationService;
    }

    OperationResult<Document> destroyItems(
            String storeId,
            List<ReservationItem> selections,
            DocumentReason reason,
            String note,
            String createdBy
    ) {
        Store store = storesRepository.findById(storeId);
        WarehouseConfiguration warehouseConfiguration = store.getWarehouseConfiguration();

        List<WarehouseItem> physicallyDestroyedItems = new ArrayList<>();

        for (ReservationItem items : selections) {
            WarehouseItem item = warehouseRepository.findById(storeId, items.getItemId());
            WarehouseItem targetItem = splitIfNeeded(item, items.getQty());

            boolean physicallyInStock = targetItem.isPhysicallyInStock();

            targetItem.markAsDestroyed();
            warehouseRepository.save(targetItem);

            if (physicallyInStock) {
                physicallyDestroyedItems.add(targetItem);
            }
        }

        if (!physicallyDestroyedItems.isEmpty() && warehouseConfiguration != null && warehouseConfiguration.isDocumentsGenerationEnabled()) {
            InvoicingProvider invoicingProvider = invoicingProviderFactory.get(store);

            BillingParty issuer = invoicingProvider.fetchCostCenterById(warehouseConfiguration.getCostCenterId());
            if (issuer == null || !issuer.hasCompanyDetails()) {
                return OperationResult.failure("Failed to fetch cost center with id: " + warehouseConfiguration.getCostCenterId());
            }

            return triggerInternalIssueDocumentGeneration(
                    store.getStoreId(),
                    warehouseConfiguration.getWarehouseId(),
                    physicallyDestroyedItems,
                    issuer,
                    reason,
                    note,
                    createdBy
            );
        }

        return OperationResult.success();
    }

    private WarehouseItem splitIfNeeded(WarehouseItem item, int requestedQty) {
        if (requestedQty >= item.getQty()) {
            return item;
        }
        WarehouseItem splitItem = item.splitOff(requestedQty);
        warehouseRepository.save(item);
        return splitItem;
    }

    private OperationResult<Document> triggerInternalIssueDocumentGeneration(
            String storeId,
            String warehouseId,
            List<WarehouseItem> items,
            BillingParty issuer,
            DocumentReason reason,
            String note,
            String createdBy
    ) {
        List<DocumentLineItem> lineItems = items.stream()
                .map(DocumentLineItem::from)
                .collect(Collectors.toList());

        DocumentCreationRequest request = DocumentCreationRequest.builder(DocumentType.InternalIssue)
                .storeId(storeId)
                .issuer(IssuerDetails.from(issuer))
                .warehouseId(warehouseId)
                .reason(reason)
                .note(note)
                .items(lineItems)
                .createdBy(createdBy)
                .build();

        return documentCreationService.createDocument(request);
    }
}
