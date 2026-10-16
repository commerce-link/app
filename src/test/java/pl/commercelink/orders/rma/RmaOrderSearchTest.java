package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RmaOrderSearchTest {

    private static final String STORE = "store-1";

    @Mock
    private OrdersRepository orders;
    @Mock
    private OrderItemsRepository orderItems;
    @InjectMocks
    private RmaOrderSearch search;

    private static Order order(String id, OrderStatus status, int day) {
        Order order = new Order(STORE);
        order.setOrderId(id);
        order.setStatus(status);
        order.setOrderedAt(LocalDateTime.of(2026, 10, day, 12, 0));
        return order;
    }

    private static OrderItem item(String orderId, String itemId, String serialNo) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setItemId(itemId);
        item.setSerialNo(serialNo);
        return item;
    }

    @Test
    void aBlankQueryIsTheStartPage() {
        // when
        RmaOrderSearch.Result result = search.search(STORE, "   ");

        // then
        assertThat(result.kind()).isEqualTo(RmaOrderSearch.Kind.EMPTY);
    }

    @Test
    void anEmailWithSeveralEligibleOrdersIsAChoiceNewestFirst() {
        // given
        Order older = order("aaaaaaaa-1", OrderStatus.Completed, 1);
        Order newer = order("bbbbbbbb-2", OrderStatus.Delivered, 9);
        when(orders.findOrderIdsByEmail(STORE, "anna@x.pl", RmaOrderSearch.CHOICE_LIMIT + 1)).thenReturn(List.of("bbbbbbbb-2", "aaaaaaaa-1"));
        when(orders.findByIds(STORE, List.of("bbbbbbbb-2", "aaaaaaaa-1"))).thenReturn(List.of(newer, older));

        // when
        RmaOrderSearch.Result result = search.search(STORE, " anna@x.pl ");

        // then
        assertThat(result.kind()).isEqualTo(RmaOrderSearch.Kind.CHOICE);
        assertThat(result.byEmail()).isTrue();
        assertThat(result.orders()).containsExactly(newer, older);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void anEmailWithOneEligibleOrderOpensItEvenBesideAnIneligibleOne() {
        // given
        Order shipping = order("cccccccc-3", OrderStatus.Shipping, 11);
        Order delivered = order("dddddddd-4", OrderStatus.Delivered, 2);
        when(orders.findOrderIdsByEmail(STORE, "anna@x.pl", RmaOrderSearch.CHOICE_LIMIT + 1)).thenReturn(List.of("cccccccc-3", "dddddddd-4"));
        when(orders.findByIds(STORE, List.of("cccccccc-3", "dddddddd-4"))).thenReturn(List.of(shipping, delivered));

        // when
        RmaOrderSearch.Result result = search.search(STORE, "anna@x.pl");

        // then
        assertThat(result.kind()).isEqualTo(RmaOrderSearch.Kind.ORDER);
        assertThat(result.order()).isSameAs(delivered);
    }

    @Test
    void anEmailWithoutOrdersIsNotFound() {
        // given
        when(orders.findOrderIdsByEmail(STORE, "nobody@x.pl", RmaOrderSearch.CHOICE_LIMIT + 1)).thenReturn(List.of());

        // when
        RmaOrderSearch.Result result = search.search(STORE, "nobody@x.pl");

        // then
        assertThat(result.kind()).isEqualTo(RmaOrderSearch.Kind.NOT_FOUND);
        assertThat(result.byEmail()).isTrue();
        verify(orderItems, never()).findBySerialNo(anyString());
    }

    @Test
    void aFullOrderIdOpensTheOrderWhateverItsStatus() {
        // given
        Order shipping = order("8c1d0291-5b2e-4f7a-9d61-3e77d244a0c1", OrderStatus.Shipping, 3);
        when(orders.findById(STORE, "8c1d0291-5b2e-4f7a-9d61-3e77d244a0c1")).thenReturn(shipping);

        // when
        RmaOrderSearch.Result result = search.search(STORE, "8c1d0291-5b2e-4f7a-9d61-3e77d244a0c1");

        // then
        assertThat(result.kind()).isEqualTo(RmaOrderSearch.Kind.ORDER);
        assertThat(result.order()).isSameAs(shipping);
        assertThat(result.serialItemId()).isNull();
    }

    @Test
    void theFirstEightCharactersOpenTheOnlyMatchingOrder() {
        // given
        Order delivered = order("8c1d0291-5b2e", OrderStatus.Delivered, 3);
        when(orders.findById(STORE, "8C1D0291")).thenReturn(null);
        when(orders.findByShortId(STORE, "8c1d0291")).thenReturn(List.of(delivered));

        // when
        RmaOrderSearch.Result result = search.search(STORE, "8C1D0291");

        // then
        assertThat(result.kind()).isEqualTo(RmaOrderSearch.Kind.ORDER);
        assertThat(result.order()).isSameAs(delivered);
    }

    @Test
    void aShortTextIsNotTakenForAPrefix() {
        // given
        when(orders.findById(STORE, "8c1d")).thenReturn(null);
        when(orderItems.findBySerialNo("8c1d")).thenReturn(List.of());

        // when
        RmaOrderSearch.Result result = search.search(STORE, "8c1d");

        // then
        assertThat(result.kind()).isEqualTo(RmaOrderSearch.Kind.NOT_FOUND);
        verify(orders, never()).findByShortId(anyString(), anyString());
    }

    @Test
    void aSerialNumberOpensItsOrderWithThatItemTicked() {
        // given
        Order delivered = order("eeeeeeee-5", OrderStatus.Delivered, 4);
        when(orders.findById(STORE, "SN-77")).thenReturn(null);
        when(orderItems.findBySerialNo("SN-77")).thenReturn(List.of(item("eeeeeeee-5", "i9", "SN-1, SN-77")));
        when(orders.findByIds(STORE, List.of("eeeeeeee-5"))).thenReturn(List.of(delivered));

        // when
        RmaOrderSearch.Result result = search.search(STORE, "SN-77");

        // then
        assertThat(result.kind()).isEqualTo(RmaOrderSearch.Kind.ORDER);
        assertThat(result.order()).isSameAs(delivered);
        assertThat(result.serialItemId()).isEqualTo("i9");
    }

    @Test
    void aSerialFromAnotherStoreIsIgnored() {
        // given: order items carry no store id; the order lookup is store-scoped and does not return the other store's order
        when(orders.findById(STORE, "SN-88")).thenReturn(null);
        when(orderItems.findBySerialNo("SN-88")).thenReturn(List.of(item("foreign-order", "i1", "SN-88")));
        when(orders.findByIds(STORE, List.of("foreign-order"))).thenReturn(List.of());

        // when
        RmaOrderSearch.Result result = search.search(STORE, "SN-88");

        // then
        assertThat(result.kind()).isEqualTo(RmaOrderSearch.Kind.NOT_FOUND);
    }

    @Test
    void aSerialSoldTwiceIsAChoice() {
        // given
        Order first = order("ffffffff-6", OrderStatus.Completed, 1);
        Order second = order("abababab-7", OrderStatus.Delivered, 8);
        when(orders.findById(STORE, "SN-99")).thenReturn(null);
        when(orderItems.findBySerialNo("SN-99")).thenReturn(
                List.of(item("ffffffff-6", "i1", "SN-99"), item("abababab-7", "i2", "SN-99")));
        when(orders.findByIds(any(), any())).thenReturn(List.of(first, second));

        // when
        RmaOrderSearch.Result result = search.search(STORE, "SN-99");

        // then
        assertThat(result.kind()).isEqualTo(RmaOrderSearch.Kind.CHOICE);
        assertThat(result.orders()).containsExactly(second, first);
    }

    @Test
    void aFragmentOfALongerSerialNumberDoesNotMatch() {
        // given: the scan filters with contains(), so it also returns longer numbers holding the query
        when(orders.findById(STORE, "SN-7")).thenReturn(null);
        when(orderItems.findBySerialNo("SN-7")).thenReturn(List.of(item("eeeeeeee-5", "i9", "SN-77")));

        // when
        RmaOrderSearch.Result result = search.search(STORE, "SN-7");

        // then
        assertThat(result.kind()).isEqualTo(RmaOrderSearch.Kind.NOT_FOUND);
        verify(orders, never()).findByIds(any(), any());
    }

    @Test
    void aQueryTooLongForAKeyIsNotFoundWithoutAnyRead() {
        // given
        String pasted = "x".repeat(RmaOrderSearch.MAX_QUERY + 1);

        // when
        RmaOrderSearch.Result result = search.search(STORE, "  " + pasted + "  ");

        // then
        assertThat(result.kind()).isEqualTo(RmaOrderSearch.Kind.NOT_FOUND);
        assertThat(result.query()).isEqualTo(pasted);
        verifyNoInteractions(orders, orderItems);
    }

    @Test
    void aTruncatedEmailResultWithOneEligibleOrderIsStillAChoice() {
        // given: only the newest orders were loaded, an older eligible one may exist
        List<String> ids = IntStream.rangeClosed(1, RmaOrderSearch.CHOICE_LIMIT + 1).mapToObj(i -> "id-" + i).toList();
        List<Order> loaded = new ArrayList<>();
        loaded.add(order("id-1", OrderStatus.Delivered, 1));
        IntStream.rangeClosed(2, RmaOrderSearch.CHOICE_LIMIT).forEach(i -> loaded.add(order("id-" + i, OrderStatus.Shipping, 2)));
        when(orders.findOrderIdsByEmail(STORE, "anna@x.pl", RmaOrderSearch.CHOICE_LIMIT + 1)).thenReturn(ids);
        when(orders.findByIds(STORE, ids.subList(0, RmaOrderSearch.CHOICE_LIMIT))).thenReturn(loaded);

        // when
        RmaOrderSearch.Result result = search.search(STORE, "anna@x.pl");

        // then
        assertThat(result.kind()).isEqualTo(RmaOrderSearch.Kind.CHOICE);
        assertThat(result.truncated()).isTrue();
        assertThat(result.orders()).hasSize(RmaOrderSearch.CHOICE_LIMIT);
    }
}
