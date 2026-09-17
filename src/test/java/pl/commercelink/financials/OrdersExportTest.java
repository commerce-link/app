package pl.commercelink.financials;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShippingDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrdersExportTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    @Mock
    private OrdersRepository ordersRepository;

    @InjectMocks
    private OrdersExport ordersExport;

    @Test
    void exportsOrderWithoutAnyRecordedPayment() {
        // given
        when(ordersRepository.findAllOrders(eq("store-1"), any(), any())).thenReturn(List.of(order("order-1")));

        // when
        String csv = ordersExport.run("store-1", FROM, TO);

        // then
        assertThat(csv).contains("order-1");
    }

    @Test
    void exportsTheOriginalPaymentSourceWhenThereIsOne() {
        // given
        Order order = order("order-2");
        order.getPayments().add(Payment.bankTransfer("ref", null, 100));

        when(ordersRepository.findAllOrders(eq("store-1"), any(), any())).thenReturn(List.of(order));

        // when
        String csv = ordersExport.run("store-1", FROM, TO);

        // then
        assertThat(csv).contains("order-2");
    }

    private Order order(String orderId) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setTotalPrice(100);
        order.setOrderedAt(LocalDateTime.of(2026, 9, 14, 10, 0));
        order.setSource(new OrderSource("Test", OrderSourceType.WebStore));
        order.setBillingDetails(billing());
        order.setShippingDetails(address());
        return order;
    }

    private ShippingDetails address() {
        ShippingDetails details = new ShippingDetails();
        details.setCity("Warszawa");
        return details;
    }

    private BillingDetails billing() {
        BillingDetails details = new BillingDetails();
        details.setCity("Warszawa");
        return details;
    }
}
