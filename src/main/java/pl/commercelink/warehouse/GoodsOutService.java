package pl.commercelink.warehouse;

import org.springframework.stereotype.Service;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.invoicing.api.BillingParty;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.WarehouseConfiguration;
import pl.commercelink.warehouse.api.GoodsOutItem;
import pl.commercelink.warehouse.api.GoodsOutRequest;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.warehouse.api.Warehouse;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
class GoodsOutService {

    private final OrdersRepository ordersRepository;
    private final OrderItemsRepository orderItemsRepository;
    private final Warehouse warehouse;
    private final StoresRepository storesRepository;
    private final InvoicingProviderFactory invoicingProviderFactory;

    GoodsOutService(
            OrdersRepository ordersRepository,
            OrderItemsRepository orderItemsRepository,
            Warehouse warehouse,
            StoresRepository storesRepository,
            InvoicingProviderFactory invoicingProviderFactory
    ) {
        this.ordersRepository = ordersRepository;
        this.orderItemsRepository = orderItemsRepository;
        this.warehouse = warehouse;
        this.storesRepository = storesRepository;
        this.invoicingProviderFactory = invoicingProviderFactory;
    }

    OperationResult<Document> issueGoodsOut(Order order, String createdBy) {
        Optional<Document> existingDocument = order.getDocumentByType(DocumentType.GoodsIssue);
        if (existingDocument.isPresent()) {
            return OperationResult.success(existingDocument.get());
        }

        Store store = storesRepository.findById(order.getStoreId());
        WarehouseConfiguration warehouseConfiguration = store.getWarehouseConfiguration();
        if (warehouseConfiguration == null || !warehouseConfiguration.isComplete()) {
            return OperationResult.failure("Warehouse configuration is missing for store: " + order.getStoreId());
        }

        if (!warehouseConfiguration.isDocumentsGenerationEnabled()) {
            return OperationResult.success();
        }

        return triggerGoodsOutDocumentGeneration(order, store, warehouseConfiguration, createdBy);
    }

    private OperationResult<Document> triggerGoodsOutDocumentGeneration(Order order, Store store, WarehouseConfiguration warehouseConfiguration, String createdBy) {
        InvoicingProvider invoicingProvider = invoicingProviderFactory.get(store);
        BillingParty issuer = invoicingProvider.fetchCostCenterById(warehouseConfiguration.getCostCenterId());
        if (issuer == null || !issuer.hasCompanyDetails()) {
            return OperationResult.failure("Failed to fetch cost center with id: " + warehouseConfiguration.getCostCenterId());
        }

        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(order.getOrderId())
                .stream()
                .filter(i -> !i.hasCategory(ProductCategory.Services))
                .toList();

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
            order.getDocuments().add(document);
            ordersRepository.save(order);
            return OperationResult.success(document);
        }

        return OperationResult.success();
    }

}
