package pl.commercelink.warehouse.builtin;

import org.springframework.stereotype.Service;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.invoicing.api.BillingParty;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.WarehouseConfiguration;
import pl.commercelink.documents.Document;
import pl.commercelink.starter.util.OperationResult;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
class WarehouseInternalReceiptService {

    private final WarehouseRepository warehouseRepository;
    private final StoresRepository storesRepository;
    private final InvoicingProviderFactory invoicingProviderFactory;
    private final BuiltInDocumentCreationService documentCreationService;

    WarehouseInternalReceiptService(
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

    OperationResult<Document> addItem(String storeId, WarehouseItem item, String createdBy) {
        item.setStoreId(storeId);
        warehouseRepository.save(item);

        if (item.getStatus() != FulfilmentStatus.Delivered) {
            return OperationResult.success();
        }

        Store store = storesRepository.findById(storeId);
        WarehouseConfiguration warehouseConfiguration = store.getWarehouseConfiguration();

        if (warehouseConfiguration == null || !warehouseConfiguration.isDocumentsGenerationEnabled()) {
            return OperationResult.success();
        }

        InvoicingProvider invoicingProvider = invoicingProviderFactory.get(store);

        BillingParty issuer = invoicingProvider.fetchCostCenterById(warehouseConfiguration.getCostCenterId());
        if (issuer == null || !issuer.hasCompanyDetails()) {
            return OperationResult.failure("Failed to fetch cost center with id: " + warehouseConfiguration.getCostCenterId());
        }

        return triggerInternalReceiptDocumentGeneration(
                storeId,
                warehouseConfiguration.getWarehouseId(),
                Collections.singletonList(item),
                issuer,
                createdBy
        );
    }

    private OperationResult<Document> triggerInternalReceiptDocumentGeneration(
            String storeId,
            String warehouseId,
            List<WarehouseItem> items,
            BillingParty issuer,
            String createdBy
    ) {
        List<DocumentLineItem> lineItems = items.stream()
                .map(DocumentLineItem::from)
                .collect(Collectors.toList());

        DocumentCreationRequest request = DocumentCreationRequest.builder(DocumentType.InternalReceipt)
                .storeId(storeId)
                .issuer(IssuerDetails.from(issuer))
                .warehouseId(warehouseId)
                .reason(DocumentReason.StockAdjustment)
                .items(lineItems)
                .createdBy(createdBy)
                .build();

        return documentCreationService.createDocument(request);
    }
}
