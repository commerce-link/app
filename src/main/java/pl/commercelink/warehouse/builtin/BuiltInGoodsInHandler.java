package pl.commercelink.warehouse.builtin;

import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.orders.*;
import pl.commercelink.warehouse.api.GoodsInHandler;
import pl.commercelink.warehouse.api.GoodsInRequest;
import pl.commercelink.documents.Document;
import pl.commercelink.starter.util.OperationResult;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static pl.commercelink.inventory.deliveries.DeliveryItem.groupAndUnify;

class BuiltInGoodsInHandler implements GoodsInHandler {

    private final String storeId;

    private OrdersRepository ordersRepository;
    private OrderItemsRepository orderItemsRepository;
    private OrderLifecycle orderLifecycle;

    private final WarehouseRepository warehouseRepository;
    private final BuiltInDocumentCreationService documentCreationService;

    BuiltInGoodsInHandler(
            String storeId,
            OrdersRepository ordersRepository,
            OrderItemsRepository orderItemsRepository,
            OrderLifecycle orderLifecycle,
            WarehouseRepository warehouseRepository,
            BuiltInDocumentCreationService documentCreationService
    ) {
        this.storeId = storeId;
        this.ordersRepository = ordersRepository;
        this.orderItemsRepository = orderItemsRepository;
        this.orderLifecycle = orderLifecycle;
        this.warehouseRepository = warehouseRepository;
        this.documentCreationService = documentCreationService;
    }

    @Override
    public OperationResult<Document> receive(GoodsInRequest goodsInRequest, boolean documentsGenerationEnabled) {
        List<Allocation> receivedOrderAllocations = markOrderItemsAsReceived(goodsInRequest.getOrderAllocations());
        List<Allocation> receivedWarehouseAllocations = markWarehouseItemsAsReceived(goodsInRequest.getWarehouseAllocations());

        if (documentsGenerationEnabled) {
            List<Allocation> allReceived = new LinkedList<>();
            allReceived.addAll(receivedOrderAllocations);
            allReceived.addAll(receivedWarehouseAllocations);

            if (allReceived.isEmpty()) {
                return OperationResult.success();
            }

            List<DeliveryItem> deliveryItems = groupAndUnify(allReceived);

            List<DocumentLineItem> items = deliveryItems.stream()
                    .map(item -> new DocumentLineItem(
                            goodsInRequest.getDeliveryId(),
                            item.getEan(),
                            item.getMfn(),
                            item.getName(),
                            item.getOrderedQty(),
                            item.getUnitCost()
                    ))
                    .collect(Collectors.toList());

            DocumentCreationRequest request = DocumentCreationRequest.builder(DocumentType.GoodsReceipt)
                    .storeId(storeId)
                    .issuer(IssuerDetails.from(goodsInRequest.getIssuer()))
                    .counterparty(CounterpartyDetails.from(goodsInRequest.getCounterparty()))
                    .warehouseId(goodsInRequest.getWarehouseId())
                    .deliveryId(goodsInRequest.getDeliveryId())
                    .reason(DocumentReason.SupplierDelivery)
                    .items(items)
                    .createdBy(goodsInRequest.getCreatedBy())
                    .build();

            return documentCreationService.createDocument(request);
        }

        return OperationResult.success();
    }

    private List<Allocation> markOrderItemsAsReceived(List<Allocation> orderAllocations) {
        Map<String, List<Allocation>> allocationsByOrderId = new HashMap<>();

        for (Allocation allocation : orderAllocations) {
            allocationsByOrderId.computeIfAbsent(allocation.getKey().getOrderId(), k -> new LinkedList<>())
                    .add(allocation);
        }

        List<Allocation> received = new LinkedList<>();
        for (Map.Entry<String, List<Allocation>> entry : allocationsByOrderId.entrySet()) {
            received.addAll(markOrderItemsAsReceived(storeId, entry.getKey(), entry.getValue()));
        }
        return received;
    }

    private List<Allocation> markOrderItemsAsReceived(String storeId, String orderId, List<Allocation> allocations) {
        Order order = ordersRepository.findById(storeId, orderId);
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(order.getOrderId());

        List<Allocation> received = new LinkedList<>();
        for (Allocation allocation : allocations) {
            String itemId = allocation.getKey().getItemId();
            for (OrderItem orderItem : orderItems) {
                if (orderItem.getItemId().equals(itemId) && orderItem.isWaitingForCollection()) {
                    orderItem.markAsReceived();
                    orderItemsRepository.save(orderItem);
                    received.add(allocation);
                }
            }
        }

        order.updateEstimatedAssemblyAt(LocalDate.now());
        orderLifecycle.update(order, orderItems);
        return received;
    }

    private List<Allocation> markWarehouseItemsAsReceived(List<Allocation> allocations) {
        List<Allocation> received = new LinkedList<>();
        for (Allocation allocation : allocations) {
            WarehouseItem warehouseItem = warehouseRepository.findById(storeId, allocation.getKey().getItemId());
            if (warehouseItem.isWaitingForCollection()) {
                warehouseItem.markAsReceived();
                warehouseRepository.save(warehouseItem);
                received.add(allocation);
            }
        }
        return received;
    }
}
