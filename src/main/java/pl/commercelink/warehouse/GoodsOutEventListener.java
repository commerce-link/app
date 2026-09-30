package pl.commercelink.warehouse;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.invoicing.InvoiceCreationEventPublisher;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.StoreActivity;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoodsOutEventListener {

    private final OrdersRepository ordersRepository;
    private final InvoiceCreationEventPublisher invoiceCreationEventPublisher;
    private final GoodsOutService goodsOutService;
    private final StoreActivity storeActivity;

    @SqsListener(
            value = "order-goods-out-queue.fifo",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(GoodsOutEventRequest payload) {
        if (!storeActivity.isActive(payload.getStoreId())) {
            log.warn("Goods out of order {} skipped: store {} is inactive", payload.getOrderId(), payload.getStoreId());
            return;
        }
        Order order = ordersRepository.findById(payload.getStoreId(), payload.getOrderId());
        if (order == null) {
            return;
        }

        OperationResult<?> result = goodsOutService.issueGoodsOut(order, payload.getCreatedBy());
        if (!result.isSuccess()) {
            throw new RuntimeException("Failed to create goods out document for " + order.getOrderId() + ": " + result.getMessage());
        }

        invoiceCreationEventPublisher.publish(order, true);
    }
}
