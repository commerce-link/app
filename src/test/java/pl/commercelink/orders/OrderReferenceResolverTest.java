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
    void anExternalNumberIsLookedUpWhenNoShortNumberMatches() {
        // given
        Order marketplace = new Order(STORE_ID);
        when(ordersRepository.findByShortId(STORE_ID, "5749922740")).thenReturn(List.of());
        when(ordersRepository.findByStoreIdAndExternalOrderId(STORE_ID, "5749922740")).thenReturn(marketplace);

        // when
        OrderReferenceResolver.Resolution resolution = resolver.resolve(STORE_ID, "5749922740");

        // then
        assertThat(resolution.order()).isSameAs(marketplace);
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
