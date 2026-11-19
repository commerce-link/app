package pl.commercelink.financials;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentDirection;

import java.io.StringWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class GoogleOfflineConversionsExport {

    // The conversion action in Google Ads must carry exactly this name; the reporting settings page shows it.
    public static final String CONVERSION_NAME = "Zakupy offline";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OrdersRepository ordersRepository;

    @Autowired
    public GoogleOfflineConversionsExport(OrdersRepository ordersRepository) {
        this.ordersRepository = ordersRepository;
    }

    public String run(String storeId, LocalDate dateFrom, LocalDate dateTo) {
        List<Order> orders = ordersRepository.findAllOrders(storeId, dateFrom, dateTo)
                .stream()
                .filter(o -> o.getGclid() != null && !o.getGclid().isEmpty())
                .collect(Collectors.toList());

        StringWriter csvWriter = new StringWriter();
        csvWriter.append("Parameters:TimeZone=+0000,,,,,,\n");
        csvWriter.append("Google Click ID,Conversion Name,Conversion Time,Conversion Value,Conversion Currency,Ad User Data,Ad Personalization\n");

        for (Order order : orders) {
            csvWriter.append(order.getGclid()).append(",")
                    .append(CONVERSION_NAME).append(",")
                    .append(order.getOrderedAt().format(DATE_TIME_FORMATTER)).append(",");

            csvWriter.append(String.valueOf(conversionValue(order)));
            csvWriter.append(",");

            // Conversion Currency (opcjonalnie)
            csvWriter.append("PLN,");
            csvWriter.append("Granted,");
            csvWriter.append("Granted");
            csvWriter.append("\n");
        }

        return csvWriter.toString();
    }

    /**
     * The order's value even when cancelled: for an ad campaign a placed order is the conversion. After the goods came
     * back totalPrice no longer says it, since every return takes the returned goods out of it, down to 0 for an order
     * returned whole; what the customer paid in then stands for the value. An order returned without ever being paid
     * leaves nothing to go by.
     */
    private static double conversionValue(Order order) {
        if (!order.hasStatus(OrderStatus.Cancelled)) {
            return order.getTotalPrice();
        }
        double paidIn = order.getPayments().stream()
                .filter(payment -> payment.getDirection() != PaymentDirection.Outgoing)
                .mapToDouble(Payment::getAmount)
                .filter(amount -> amount > 0)
                .sum();
        return Math.max(order.getTotalPrice(), paidIn);
    }
}