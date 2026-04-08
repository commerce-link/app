package pl.commercelink.warehouse.builtin;

import org.springframework.stereotype.Service;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.invoicing.api.BillingParty;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.WarehouseConfiguration;
import pl.commercelink.warehouse.api.GoodsOutItem;
import pl.commercelink.warehouse.api.GoodsOutRequest;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.warehouse.api.Warehouse;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class WarehouseGoodsOutService {

    private final Warehouse warehouse;
    private final WarehouseRepository warehouseRepository;
    private final StoresRepository storesRepository;
    private final InvoicingProviderFactory invoicingProviderFactory;
    private final DeliveriesRepository deliveriesRepository;

    public WarehouseGoodsOutService(
            Warehouse warehouse,
            WarehouseRepository warehouseRepository,
            StoresRepository storesRepository,
            InvoicingProviderFactory invoicingProviderFactory,
            DeliveriesRepository deliveriesRepository
    ) {
        this.warehouse = warehouse;
        this.warehouseRepository = warehouseRepository;
        this.storesRepository = storesRepository;
        this.invoicingProviderFactory = invoicingProviderFactory;
        this.deliveriesRepository = deliveriesRepository;
    }

    public OperationResult<Document> issueGoodsOutForExternalService(
            String storeId,
            List<String> warehouseItemIds,
            String createdBy
    ) {
        return issueGoodsOutForExternalService(storeId, warehouseItemIds, null, createdBy);
    }

    public OperationResult<Document> issueGoodsOutForExternalService(
            String storeId,
            List<String> warehouseItemIds,
            ShippingDetails serviceCenterAddress,
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

        String deliveryId = warehouseItems.get(0).getDeliveryId();
        Delivery delivery = deliveriesRepository.findById(storeId, deliveryId);
        if (delivery == null) {
            return OperationResult.failure("Failed to fetch delivery with id: " + deliveryId);
        }

        BillingParty counterparty = invoicingProvider.fetchBillingPartyByShortcut(delivery.getProvider());
        if (counterparty == null || !counterparty.hasCompanyDetails()) {
            return OperationResult.failure("Failed to fetch counterparty with shortcut: " + delivery.getProvider());
        }

        markAllItemsAsSentToExternalService(warehouseItems);

        if (warehouseConfiguration.isDocumentsGenerationEnabled()) {
            return triggerGoodsOutDocumentGeneration(
                    store.getStoreId(),
                    warehouseConfiguration.getWarehouseId(),
                    warehouseItems,
                    issuer,
                    counterparty,
                    serviceCenterAddress,
                    createdBy
            );
        }

        return OperationResult.success();
    }

    private void markAllItemsAsSentToExternalService(List<WarehouseItem> warehouseItems) {
        for (WarehouseItem item : warehouseItems) {
            item.markAsInExternalService();
            warehouseRepository.save(item);
        }
    }

    private OperationResult<Document> triggerGoodsOutDocumentGeneration(
            String storeId,
            String warehouseId,
            List<WarehouseItem> items,
            BillingParty issuer,
            BillingParty counterparty,
            ShippingDetails serviceCenterAddress,
            String createdBy
    ) {

        List<GoodsOutItem> goodsOutItems = items.stream()
                .map(item -> new GoodsOutItem(
                        item.getDeliveryId(),
                        item.getEan(),
                        item.getManufacturerCode(),
                        item.getName(),
                        item.getQty(),
                        item.getCost(),
                        item.getTax()
                ))
                .collect(Collectors.toList());

        GoodsOutRequest request = GoodsOutRequest.builder()
                .storeId(storeId)
                .issuer(issuer)
                .counterparty(counterparty)
                .deliveryAddress(serviceCenterAddress)
                .warehouseId(warehouseId)
                .items(goodsOutItems)
                .reason(DocumentReason.ServiceOut)
                .createdBy(createdBy)
                .build();

        return warehouse.goodsOutHandler(storeId).issue(request, true);
    }
}
