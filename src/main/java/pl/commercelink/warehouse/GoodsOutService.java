package pl.commercelink.warehouse;

import org.springframework.stereotype.Service;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.DropshipItemLookup;
import pl.commercelink.invoicing.api.BillingParty;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.WarehouseConfiguration;
import pl.commercelink.warehouse.api.GoodsOutItem;
import pl.commercelink.warehouse.api.GoodsOutRequest;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.warehouse.api.Warehouse;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
class GoodsOutService {

    private final OrdersRepository ordersRepository;
    private final OrderItemsRepository orderItemsRepository;
    private final Warehouse warehouse;
    private final StoresRepository storesRepository;
    private final InvoicingProviderFactory invoicingProviderFactory;
    private final OptimisticLockingExecutor optimisticLockingExecutor;
    private final DropshipItemLookup dropshipItemLookup;

    GoodsOutService(
            OrdersRepository ordersRepository,
            OrderItemsRepository orderItemsRepository,
            Warehouse warehouse,
            StoresRepository storesRepository,
            InvoicingProviderFactory invoicingProviderFactory,
            OptimisticLockingExecutor optimisticLockingExecutor,
            DropshipItemLookup dropshipItemLookup
    ) {
        this.ordersRepository = ordersRepository;
        this.orderItemsRepository = orderItemsRepository;
        this.warehouse = warehouse;
        this.storesRepository = storesRepository;
        this.invoicingProviderFactory = invoicingProviderFactory;
        this.optimisticLockingExecutor = optimisticLockingExecutor;
        this.dropshipItemLookup = dropshipItemLookup;
    }

    OperationResult<Document> issueGoodsOut(Order order, String createdBy) {
        Optional<Document> existingDocument = order.getDocumentByType(DocumentType.GoodsIssue);
        if (existingDocument.isPresent()) {
            return OperationResult.success(existingDocument.get());
        }

        // Dropship goods are shipped by the supplier and never enter our stock, so a goods issue note for them
        // would be a sale movement against stock we never held. Checked before the warehouse configuration so
        // that a dropship-only order does not fail on a store that never set the warehouse up.
        List<OrderItem> warehouseItems = warehouseFulfilledProductItems(order);
        if (warehouseItems.isEmpty()) {
            return OperationResult.success();
        }

        Store store = storesRepository.findById(order.getStoreId());
        WarehouseConfiguration warehouseConfiguration = store.getWarehouseConfiguration();
        if (warehouseConfiguration == null || !warehouseConfiguration.isComplete()) {
            return OperationResult.failure("Warehouse configuration is missing for store: " + order.getStoreId());
        }

        if (!warehouseConfiguration.isDocumentsGenerationEnabled()) {
            return OperationResult.success();
        }

        return triggerGoodsOutDocumentGeneration(order, store, warehouseConfiguration, warehouseItems, createdBy);
    }

    private List<OrderItem> warehouseFulfilledProductItems(Order order) {
        List<OrderItem> productItems = orderItemsRepository.findByOrderId(order.getOrderId())
                .stream()
                .filter(OrderItem::isProduct)
                .toList();
        Set<String> dropshipItemIds =
                dropshipItemLookup.itemIdsInDropshipDeliveries(order.getStoreId(), productItems);
        return productItems.stream()
                .filter(item -> !dropshipItemIds.contains(item.getItemId()))
                .toList();
    }

    private OperationResult<Document> triggerGoodsOutDocumentGeneration(Order order, Store store, WarehouseConfiguration warehouseConfiguration, List<OrderItem> orderItems, String createdBy) {
        InvoicingProvider invoicingProvider = invoicingProviderFactory.get(store);
        BillingParty issuer = invoicingProvider.fetchCostCenterById(warehouseConfiguration.getCostCenterId());
        if (issuer == null || !issuer.hasCompanyDetails()) {
            return OperationResult.failure("Failed to fetch cost center with id: " + warehouseConfiguration.getCostCenterId());
        }

        List<GoodsOutItem> items = orderItems.stream()
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
                .issuer(issuer)
                .counterparty(order.getBillingDetails().toBillingParty())
                .deliveryAddress(order.getShippingDetails())
                .warehouseId(warehouseConfiguration.getWarehouseId())
                .storeId(store.getStoreId())
                .orderId(order.getOrderId())
                .reason(DocumentReason.CustomerOrder)
                .items(items)
                .createdBy(createdBy)
                .build();

        OperationResult<Document> result = warehouse.goodsOutHandler(store.getStoreId()).issue(request, true);
        if (!result.isSuccess()) {
            return result;
        }

        if (result.hasPayload()) {
            Document document = result.getPayload();
            optimisticLockingExecutor.modifyAndSave(
                    () -> ordersRepository.findById(order.getStoreId(), order.getOrderId()),
                    fresh -> {
                        if (fresh.getDocumentByType(DocumentType.GoodsIssue).isEmpty()) {
                            fresh.getDocuments().add(document);
                        }
                    },
                    ordersRepository::save
            );
            return OperationResult.success(document);
        }

        return OperationResult.success();
    }

}
