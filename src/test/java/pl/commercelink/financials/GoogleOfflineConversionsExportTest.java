package pl.commercelink.financials;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleOfflineConversionsExportTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    @Mock
    private OrdersRepository ordersRepository;

    @InjectMocks
    private GoogleOfflineConversionsExport export;

    @Test
    void aCancelledOrderIsStillAConversionWorthItsValue() {
        // given
        Order cancelled = order("gclid-1", OrderStatus.Cancelled, 1249);
        when(ordersRepository.findAllOrders("store-1", FROM, TO)).thenReturn(List.of(cancelled));

        // when
        String csv = export.run("store-1", FROM, TO);

        // then
        assertThat(csv).contains("gclid-1,Zakupy offline,2026-09-10 12:30:00,1249.0,PLN,Granted,Granted");
    }

    @Test
    void anOrderWithoutAClickIdIsLeftOut() {
        // given
        Order withoutClick = order(null, OrderStatus.Completed, 500);
        when(ordersRepository.findAllOrders("store-1", FROM, TO)).thenReturn(List.of(withoutClick));

        // when
        String csv = export.run("store-1", FROM, TO);

        // then
        assertThat(csv.lines()).hasSize(2);
    }

    private static Order order(String gclid, OrderStatus status, double totalPrice) {
        Order order = new Order("store-1");
        order.setGclid(gclid);
        order.setStatus(status);
        order.setTotalPrice(totalPrice);
        order.setOrderedAt(LocalDateTime.of(2026, 9, 10, 12, 30));
        return order;
    }
}
