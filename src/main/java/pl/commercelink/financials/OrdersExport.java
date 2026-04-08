package pl.commercelink.financials;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.PaymentSource;

import java.io.StringWriter;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class OrdersExport {

    private final OrdersRepository ordersRepository;

    @Autowired
    public OrdersExport(OrdersRepository ordersRepository) {
        this.ordersRepository = ordersRepository;
    }

    public String run(String storeId, LocalDate dateFrom, LocalDate dateTo) {
        List<Order> orders = ordersRepository.findAllOrders(storeId, dateFrom, dateTo)
                .stream()
                .filter(o -> o.getTotalPrice() > 0) // filter out RMAs
                .sorted(Comparator.comparing(Order::getOrderedAt))
                .collect(Collectors.toList());

        StringWriter csvWriter = new StringWriter();
        csvWriter.append("Order ID,Affiliate ID,Ordered At,Order Source Name,Order Source Type,Billing City,Shipping City,Original payment source,Total Amount\n");

        for (Order order : orders) {
            PaymentSource paymentSource = order.getPayments().get(0).getSource();

            csvWriter.append(order.getOrderId()).append(",")
                    .append(order.getAffiliateId()).append(",")
                    .append(order.getOrderedAt().toString()).append(",")
                    .append(order.getSource().getName()).append(",")
                    .append(order.getSource().getType().name()).append(",")
                    .append(order.getBillingDetails().getCity()).append(",")
                    .append(order.getShippingDetails().getCity()).append(",")
                    .append(paymentSource != null ? paymentSource.name() : "").append(",")
                    .append(String.valueOf(order.getTotalPrice()))
                    .append("\n");
        }

        return csvWriter.toString();
    }

}
