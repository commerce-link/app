package pl.commercelink.financials;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Payment;
import pl.commercelink.starter.csv.CSVWriter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
@RequiredArgsConstructor
public class PaymentsExport {

    private static final String ORDER = "Zamówienie";
    private static final String DELIVERY = "Dostawa";

    private final OrdersRepository ordersRepository;
    private final DeliveriesRepository deliveriesRepository;

    public byte[] run(String storeId, LocalDate dateFrom, LocalDate dateTo) throws IOException {
        return new CSVWriter().writeAllRowsToBytes(generate(storeId, dateFrom, dateTo), PaymentsReportRow.headers());
    }

    public List<PaymentsReportRow> generate(String storeId, LocalDate dateFrom, LocalDate dateTo) {
        List<PaymentsReportRow> rows = new ArrayList<>();

        for (Order order : ordersRepository.findAllOrders(storeId, dateFrom, dateTo)) {
            appendRows(rows, ORDER, orderNumber(order), order.getOrderedAt(), order.getPayments());
        }

        LocalDateTime from = dateFrom.atStartOfDay();
        LocalDateTime to = dateTo.atTime(LocalTime.MAX);
        for (Delivery delivery : deliveriesRepository.findAll(storeId, from, to)) {
            appendRows(rows, DELIVERY, deliveryNumber(delivery), delivery.getOrderedAt(), delivery.getPayments());
        }

        rows.sort(Comparator.comparing(PaymentsReportRow::registeredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PaymentsReportRow::number, Comparator.nullsLast(Comparator.naturalOrder())));

        return rows;
    }

    private void appendRows(List<PaymentsReportRow> rows, String type, String number, LocalDateTime registeredAt, List<Payment> payments) {
        if (payments == null || payments.isEmpty()) {
            rows.add(new PaymentsReportRow(type, number, registeredAt, null, null, null, null, null, null, null));
            return;
        }

        for (Payment payment : payments) {
            rows.add(new PaymentsReportRow(
                    type,
                    number,
                    registeredAt,
                    payment.getDirection() != null ? payment.getDirection().name() : null,
                    payment.getAmount(),
                    payment.getFee(),
                    payment.getReferenceNo(),
                    payment.getBankTransactionNo(),
                    payment.getBankTransactionDate(),
                    payment.getSource() != null ? payment.getSource().name() : null
            ));
        }
    }

    private String orderNumber(Order order) {
        return order.isMarketplaceOrder() && isNotBlank(order.getExternalOrderId()) ? order.getExternalOrderId() : order.getOrderId();
    }

    private String deliveryNumber(Delivery delivery) {
        return isNotBlank(delivery.getExternalDeliveryId()) ? delivery.getExternalDeliveryId() : delivery.getDeliveryId();
    }
}
