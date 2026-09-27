package pl.commercelink.orders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderReferenceResolverTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private OrdersRepository ordersRepository;

    @InjectMocks
    private OrderReferenceResolver resolver;

    @Test
    void aFullNumberIsLoadedDirectly() {
        // given
        Order order = new Order(STORE_ID);
        when(ordersRepository.findById(STORE_ID, order.getOrderId())).thenReturn(order);

        // when
        OrderReferenceResolver.Resolution resolution = resolver.resolve(STORE_ID, "  " + order.getOrderId().toUpperCase() + " ");

        // then
        assertThat(resolution.isFound()).isTrue();
        assertThat(resolution.order()).isSameAs(order);
        verify(ordersRepository, never()).findByShortId(anyString(), anyString());
    }

    @Test
    void aShortNumberFindsTheOnlyOrderStartingWithIt() {
        // given
        Order order = new Order(STORE_ID);
        when(ordersRepository.findByShortId(STORE_ID, "3e37")).thenReturn(List.of(order));

        // when
        OrderReferenceResolver.Resolution resolution = resolver.resolve(STORE_ID, "3E37");

        // then
        assertThat(resolution.outcome()).isEqualTo(OrderReferenceResolver.Outcome.FOUND);
    }

    @Test
    void aShortNumberSharedByTwoOrdersIsAmbiguous() {
        // given
        when(ordersRepository.findByShortId(STORE_ID, "3e37")).thenReturn(List.of(new Order(STORE_ID), new Order(STORE_ID)));

        // when
        OrderReferenceResolver.Resolution resolution = resolver.resolve(STORE_ID, "3e37");

        // then
        assertThat(resolution.outcome()).isEqualTo(OrderReferenceResolver.Outcome.AMBIGUOUS);
        assertThat(resolution.candidates()).isEqualTo(2);
    }

    @Test
    void anExternalNumberLoadsTheWholeOrderBehindTheIndexHit() {
        // given: the index projects only the keys and orderId
        Order skeleton = new Order();
        skeleton.setOrderId("3e37aaaa-0000-0000-0000-000000000001");
        Order full = new Order(STORE_ID);
        when(ordersRepository.findByShortId(STORE_ID, "5749922740")).thenReturn(List.of());
        when(ordersRepository.findAllByStoreIdAndExternalOrderId(STORE_ID, "5749922740")).thenReturn(List.of(skeleton));
        when(ordersRepository.findById(STORE_ID, skeleton.getOrderId())).thenReturn(full);

        // when
        OrderReferenceResolver.Resolution resolution = resolver.resolve(STORE_ID, "5749922740");

        // then
        assertThat(resolution.order()).isSameAs(full);
        assertThat(resolution.order().getStatus()).isNotNull();
    }

    @Test
    void anExternalNumberSharedByTwoOrdersIsAmbiguous() {
        // given
        Order first = new Order();
        first.setOrderId("3e37aaaa-0000-0000-0000-000000000001");
        Order second = new Order();
        second.setOrderId("3e37aaaa-0000-0000-0000-000000000002");
        when(ordersRepository.findByShortId(STORE_ID, "5749922740")).thenReturn(List.of());
        when(ordersRepository.findAllByStoreIdAndExternalOrderId(STORE_ID, "5749922740")).thenReturn(List.of(first, second));

        // when
        OrderReferenceResolver.Resolution resolution = resolver.resolve(STORE_ID, "5749922740");

        // then
        assertThat(resolution.outcome()).isEqualTo(OrderReferenceResolver.Outcome.AMBIGUOUS);
        assertThat(resolution.candidates()).isEqualTo(2);
        verify(ordersRepository, never()).findById(anyString(), anyString());
    }

    @Test
    void anExternalNumberWhoseOrderIsGoneFindsNothing() {
        // given
        Order skeleton = new Order();
        skeleton.setOrderId("3e37aaaa-0000-0000-0000-000000000001");
        when(ordersRepository.findByShortId(STORE_ID, "5749922740")).thenReturn(List.of());
        when(ordersRepository.findAllByStoreIdAndExternalOrderId(STORE_ID, "5749922740")).thenReturn(List.of(skeleton));

        // when
        OrderReferenceResolver.Resolution resolution = resolver.resolve(STORE_ID, "5749922740");

        // then
        assertThat(resolution.outcome()).isEqualTo(OrderReferenceResolver.Outcome.NOT_FOUND);
    }

    @Test
    void aReferenceLongerThanAnyNumberFindsNothingWithoutAQuery() {
        // when
        OrderReferenceResolver.Resolution resolution = resolver.resolve(STORE_ID, "a".repeat(1100));

        // then
        assertThat(resolution.outcome()).isEqualTo(OrderReferenceResolver.Outcome.NOT_FOUND);
        verifyNoInteractions(ordersRepository);
    }

    @Test
    void tooShortOrBlankReferencesFindNothingWithoutAPrefixQuery() {
        // when
        OrderReferenceResolver.Resolution blank = resolver.resolve(STORE_ID, "  ");
        OrderReferenceResolver.Resolution shortOne = resolver.resolve(STORE_ID, "3e3");

        // then
        assertThat(blank.outcome()).isEqualTo(OrderReferenceResolver.Outcome.NOT_FOUND);
        assertThat(shortOne.outcome()).isEqualTo(OrderReferenceResolver.Outcome.NOT_FOUND);
        verify(ordersRepository, never()).findByShortId(anyString(), anyString());
    }
}
