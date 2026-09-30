package pl.commercelink.warehouse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.invoicing.InvoiceCreationEventPublisher;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.StoreActivity;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoodsOutEventListenerTest {

    @Mock private OrdersRepository ordersRepository;
    @Mock private InvoiceCreationEventPublisher invoiceCreationEventPublisher;
    @Mock private GoodsOutService goodsOutService;
    @Mock private StoreActivity storeActivity;

    @InjectMocks
    private GoodsOutEventListener listener;

    @Test
    void issuesGoodsOutOfActiveStoreAndAsksForTheInvoice() {
        // given
        Order order = new Order();
        when(storeActivity.isActive("store-1")).thenReturn(true);
        when(ordersRepository.findById("store-1", "order-1")).thenReturn(order);
        when(goodsOutService.issueGoodsOut(order, "admin")).thenReturn(OperationResult.success());

        // when
        listener.handleMessage(new GoodsOutEventRequest("store-1", "order-1", "admin"));

        // then
        verify(invoiceCreationEventPublisher).publish(order, true);
    }

    @Test
    void issuesNothingForInactiveStore() {
        // given
        when(storeActivity.isActive("store-1")).thenReturn(false);

        // when
        listener.handleMessage(new GoodsOutEventRequest("store-1", "order-1", "admin"));

        // then
        verifyNoInteractions(ordersRepository, goodsOutService, invoiceCreationEventPublisher);
    }
}
