package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.web.dtos.RmaCreateForm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RmaCreateServiceTest {

    private static final String STORE = "store-1";
    private static final String ORDER = "order-1";

    @Mock
    private OrdersRepository orders;
    @Mock
    private OrderItemsRepository orderItems;
    @Mock
    private RMARepository rmas;
    @Mock
    private RMAItemsRepository rmaItems;
    @Mock
    private OpenRmaCoverage coverage;
    @InjectMocks
    private RmaCreateService service;

    private static Order order(OrderStatus status) {
        Order order = new Order(STORE);
        order.setOrderId(ORDER);
        order.setStatus(status);
        return order;
    }

    private static OrderItem orderItem(String id, int qty, double price) {
        OrderItem item = new OrderItem();
        item.setOrderId(ORDER);
        item.setItemId(id);
        item.setName("Produkt " + id);
        item.setQty(qty);
        item.setPrice(price);
        item.setStatus(FulfilmentStatus.Delivered);
        return item;
    }

    private static RmaCreateForm form(String itemId, String qty) {
        RmaCreateForm.Item item = new RmaCreateForm.Item();
        item.setItemId(itemId);
        item.setSelected(true);
        item.setQty(qty);
        item.setResolution(RMAResolutionType.Return);
        item.setReason("Nie działa");
        RmaCreateForm form = new RmaCreateForm();
        form.setOrderId(ORDER);
        form.setEmail("anna@x.pl");
        form.setItems(new ArrayList<>(List.of(item)));
        return form;
    }

    @Test
    void aValidFormSavesANewRmaInTheSessionStoreAndItsItems() {
        // given
        when(orders.findById(STORE, ORDER)).thenReturn(order(OrderStatus.Delivered));
        when(orderItems.findByOrderIdAndStatuses(ORDER, RmaCreateService.REPORTABLE)).thenReturn(List.of(orderItem("i1", 2, 100.0)));
        when(coverage.claims(STORE, List.of("i1"))).thenReturn(Map.of());

        // when
        RmaCreateService.Outcome outcome = service.create(STORE, form("i1", "2"));

        // then
        assertThat(outcome.created()).isTrue();
        ArgumentCaptor<RMA> rma = ArgumentCaptor.forClass(RMA.class);
        verify(rmas).save(rma.capture());
        assertThat(rma.getValue().getStoreId()).isEqualTo(STORE);
        assertThat(rma.getValue().getStatus()).isEqualTo(RMAStatus.New);
        assertThat(rma.getValue().getRmaId()).isEqualTo(outcome.rmaId());
        assertThat(rma.getValue().getEmail()).isEqualTo("anna@x.pl");
        assertThat(rma.getValue().isEmailNotificationsEnabled()).isTrue();
        assertThat(rma.getValue().getShippingInsurance()).isEqualTo(200.0);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RMAItem>> items = ArgumentCaptor.forClass(List.class);
        verify(rmaItems).batchSave(items.capture());
        assertThat(items.getValue()).singleElement().satisfies(i -> {
            assertThat(i.getRmaId()).isEqualTo(outcome.rmaId());
            assertThat(i.getQty()).isEqualTo(2);
            assertThat(i.getDesiredResolution()).isEqualTo(RMAResolutionType.Return);
        });
    }

    @Test
    void anOrderThatIsNoLongerDeliveredIsRefusedWithoutSaving() {
        // given
        when(orders.findById(STORE, ORDER)).thenReturn(order(OrderStatus.Cancelled));

        // when
        RmaCreateService.Outcome outcome = service.create(STORE, form("i1", "1"));

        // then
        assertThat(outcome.pageErrorKey()).isEqualTo("rma.new.error.orderGone");
        verify(rmas, never()).save(any());
    }

    @Test
    void anOrderIdLongerThanAnyKeyIsGoneWithoutARead() {
        // given
        RmaCreateForm form = form("i1", "1");
        form.setOrderId("e".repeat(2000));

        // when
        RmaCreateService.Outcome outcome = service.create(STORE, form);

        // then
        assertThat(outcome.pageErrorKey()).isEqualTo("rma.new.error.orderGone");
        verify(orders, never()).findById(any(), any());
    }

    @Test
    void anItemClaimedSinceThePageWasOpenedIsRefused() {
        // given
        when(orders.findById(STORE, ORDER)).thenReturn(order(OrderStatus.Completed));
        when(orderItems.findByOrderIdAndStatuses(ORDER, RmaCreateService.REPORTABLE)).thenReturn(List.of(orderItem("i1", 1, 50.0)));
        when(coverage.claims(STORE, List.of("i1"))).thenReturn(Map.of("i1", "e3b0c442-1111"));

        // when
        RmaCreateService.Outcome outcome = service.create(STORE, form("i1", "1"));

        // then
        assertThat(outcome.created()).isFalse();
        assertThat(outcome.errors()).extracting(RmaCreateForm.Error::key).containsExactly("rma.new.error.claimed");
        verify(rmas, never()).save(any());
        verify(rmaItems, never()).batchSave(anyList());
    }

    @Test
    void aForeignItemIsRefusedWithoutSaving() {
        // given
        when(orders.findById(STORE, ORDER)).thenReturn(order(OrderStatus.Delivered));
        when(orderItems.findByOrderIdAndStatuses(ORDER, RmaCreateService.REPORTABLE)).thenReturn(List.of(orderItem("i1", 1, 50.0)));
        when(coverage.claims(STORE, List.of("i1"))).thenReturn(Map.of());

        // when
        RmaCreateService.Outcome outcome = service.create(STORE, form("not-in-this-order", "1"));

        // then
        assertThat(outcome.pageErrorKey()).isEqualTo("rma.new.error.itemsChanged");
        verify(rmas, never()).save(any());
    }

    @Test
    void nothingSelectedIsAPageError() {
        // given
        when(orders.findById(STORE, ORDER)).thenReturn(order(OrderStatus.Delivered));
        when(orderItems.findByOrderIdAndStatuses(ORDER, RmaCreateService.REPORTABLE)).thenReturn(List.of(orderItem("i1", 1, 50.0)));
        when(coverage.claims(STORE, List.of("i1"))).thenReturn(Map.of());
        RmaCreateForm form = form("i1", "1");
        form.getItems().get(0).setSelected(false);

        // when
        RmaCreateService.Outcome outcome = service.create(STORE, form);

        // then
        assertThat(outcome.pageErrorKey()).isEqualTo("rma.new.error.noItems");
        verify(rmas, never()).save(any());
    }
}
