package pl.commercelink.warehouse;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.invoicing.InvoiceCreationEventPublisher;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.starter.util.OperationResult;

@Component
public class GoodsOutEventListener {

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private InvoiceCreationEventPublisher invoiceCreationEventPublisher;

    @Autowired
    private GoodsOutService goodsOutService;

    @SqsListener(
            value = "order-goods-out-queue.fifo",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(GoodsOutEventRequest payload) {
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
