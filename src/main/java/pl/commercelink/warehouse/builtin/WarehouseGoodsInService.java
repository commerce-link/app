package pl.commercelink.warehouse.builtin;

import org.springframework.stereotype.Service;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.invoicing.api.BillingParty;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.WarehouseConfiguration;
import pl.commercelink.documents.Document;
import pl.commercelink.starter.util.OperationResult;

import java.util.List;
import java.util.stream.Collectors;

@Service
class WarehouseGoodsInService {

    private final WarehouseRepository warehouseRepository;
    private final StoresRepository storesRepository;
    private final DeliveriesRepository deliveriesRepository;
    private final InvoicingProviderFactory invoicingProviderFactory;
    private final BuiltInDocumentCreationService documentCreationService;

    WarehouseGoodsInService(
            WarehouseRepository warehouseRepository,
            StoresRepository storesRepository,
            DeliveriesRepository deliveriesRepository,
            InvoicingProviderFactory invoicingProviderFactory,
            BuiltInDocumentCreationService documentCreationService
    ) {
        this.warehouseRepository = warehouseRepository;
        this.storesRepository = storesRepository;
        this.deliveriesRepository = deliveriesRepository;
        this.invoicingProviderFactory = invoicingProviderFactory;
        this.documentCreationService = documentCreationService;
    }

    OperationResult<Document> receiveFromExternalService(
            String storeId,
            List<String> warehouseItemIds,
            String createdBy
    ) {
        Store store = storesRepository.findById(storeId);
        WarehouseConfiguration warehouseConfiguration = store.getWarehouseConfiguration();

        List<WarehouseItem> warehouseItems = warehouseItemIds.stream()
                .map(itemId -> warehouseRepository.findById(storeId, itemId))
                .collect(Collectors.toList());

        InvoicingProvider invoicingProvider = invoicingProviderFactory.get(store);

        BillingParty issuer = invoicingProvider.fetchCostCenterById(warehouseConfiguration.getCostCenterId());
        if (issuer == null || !issuer.hasCompanyDetails()) {
            return OperationResult.failure("Failed to fetch cost center with id: " + warehouseConfiguration.getCostCenterId());
        }

        Delivery delivery = deliveriesRepository.findById(storeId, warehouseItems.get(0).getDeliveryId());
        if (delivery == null) {
            return OperationResult.failure("Failed to find delivery with order no: " + warehouseItems.get(0).getDeliveryId());
        }

        BillingParty counterparty = invoicingProvider.fetchBillingPartyByShortcut(delivery.getProvider());
        if (counterparty == null || !counterparty.hasCompanyDetails()) {
            return OperationResult.failure("Failed to fetch counterparty with shortcut: " + delivery.getProvider());
        }

        markAllItemsAsReceived(warehouseItems);

        if (warehouseConfiguration.isDocumentsGenerationEnabled()) {
            return triggerGoodsReceiptDocumentGeneration(
                    storeId,
                    warehouseConfiguration.getWarehouseId(),
                    warehouseItems,
                    issuer,
                    counterparty,
                    createdBy
            );
        }

        return OperationResult.success();
    }

    private void markAllItemsAsReceived(List<WarehouseItem> warehouseItems) {
        for (WarehouseItem item : warehouseItems) {
            item.markAsInRMA();
            warehouseRepository.save(item);
        }
    }

    private OperationResult<Document> triggerGoodsReceiptDocumentGeneration(
            String storeId,
            String warehouseId,
            List<WarehouseItem> items,
            BillingParty issuer,
            BillingParty counterparty,
            String createdBy
    ) {
        List<DocumentLineItem> lineItems = items.stream()
                .map(DocumentLineItem::from)
                .collect(Collectors.toList());

        DocumentCreationRequest request = DocumentCreationRequest.builder(DocumentType.GoodsReceipt)
                .storeId(storeId)
                .issuer(IssuerDetails.from(issuer))
                .counterparty(CounterpartyDetails.from(counterparty))
                .warehouseId(warehouseId)
                .reason(DocumentReason.ServiceReturn)
                .items(lineItems)
                .createdBy(createdBy)
                .build();

        return documentCreationService.createDocument(request);
    }
}
