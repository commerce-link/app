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

    @Test
    void amountTakesOneColumnWithTwoDecimals() {
        // given
        Order order = order("order-3");
        order.setTotalPrice(1151.1);

        when(ordersRepository.findAllOrders(eq("store-1"), any(), any())).thenReturn(List.of(order));

        // when
        String csv = ordersExport.run("store-1", FROM, TO);

        // then
        assertThat(csv.lines().toList()).containsExactly(
                "Order ID,Affiliate ID,Ordered At,Order Source Name,Order Source Type,Order Type,Billing City,Shipping City,Original payment source,Total Amount,Invoiced At",
                "order-3,,2026-09-14T10:00,Test,WebStore,B2C,Warszawa,Warszawa,,1151.10,");
    }

    @Test
    void amountIsRoundedToTheGrosz() {
        // given
        Order order = order("order-4");
        order.setTotalPrice(389.7000000000003);

        when(ordersRepository.findAllOrders(eq("store-1"), any(), any())).thenReturn(List.of(order));

        // when
        String csv = ordersExport.run("store-1", FROM, TO);

        // then
        assertThat(csv).contains(",389.70,");
    }

    @Test
    void affiliateIdIsWrittenWhenPresent() {
        // given
        Order order = order("order-5");
        order.setAffiliateId("aff-1");

        when(ordersRepository.findAllOrders(eq("store-1"), any(), any())).thenReturn(List.of(order));

        // when
        String csv = ordersExport.run("store-1", FROM, TO);

        // then
        assertThat(csv).contains("order-5,aff-1,");
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
