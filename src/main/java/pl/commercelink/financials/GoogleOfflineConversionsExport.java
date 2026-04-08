package pl.commercelink.financials;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;

import java.io.StringWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class GoogleOfflineConversionsExport {

    private static final String CONVERSION_NAME = "Zakupy offline";
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

            csvWriter.append(String.valueOf(order.getTotalPrice()));
            csvWriter.append(",");

            // Conversion Currency (opcjonalnie)
            csvWriter.append("PLN,");
            csvWriter.append("Granted,");
            csvWriter.append("Granted");
            csvWriter.append("\n");
        }

        return csvWriter.toString();
    }
}