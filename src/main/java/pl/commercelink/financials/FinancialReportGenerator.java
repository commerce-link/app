package pl.commercelink.financials;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.orders.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FinancialReportGenerator {

    @Autowired
    private OrdersRepository ordersRepository;
    @Autowired
    private OrderItemsRepository orderItemsRepository;
    @Autowired
    private DeliveriesRepository deliveriesRepository;

    public FinancialReports generate(String storeId, LocalDate dateFrom, LocalDate dateTo) {
        List<OrderIndexEntry> pastOrders = ordersRepository.findAllPastOrders(dateFrom, dateTo, storeId);
        List<Entry> entries = pastOrders.stream()
                .map(o -> {
                    Order order = ordersRepository.findById(storeId, o.getOrderId());
                    List<OrderItem> orderItems = orderItemsRepository.findByOrderId(order.getOrderId());

                    return new Entry(order, orderItems);
                })
                .toList();
        var deliveries = deliveriesRepository.findAll(storeId, dateFrom.atStartOfDay(), dateTo.atTime(23, 59, 59));

        List<Entry> ownEntries = entries.stream().filter(e -> !e.getOrder().isMarketplaceOrder()).toList();
        List<Entry> marketplaceEntries = entries.stream().filter(e -> e.getOrder().isMarketplaceOrder()).toList();

        FinancialReport ownReport = buildReport(dateFrom, dateTo, ownEntries, deliveries);
        FinancialReport marketplaceReport = buildReport(dateFrom, dateTo, marketplaceEntries, deliveries);

        return new FinancialReports(ownReport, marketplaceReport);
    }

    private FinancialReport buildReport(LocalDate dateFrom, LocalDate dateTo, List<Entry> entries, List<Delivery> deliveries) {
        int totalNumberOfOrders = entries.size();
        double totalPrice = calculateTotalPriceNet(entries);
        double totalCost = calculateTotalCostNet(entries);
        double totalProfit = calculateTotalProfitNet(entries);
        Map<String, Integer> salesVolumeByProvider = calculateSalesVolumeByProvider(deliveries);

        int totalDeliveries = deliveries.size();
        double totalShippingCost = deliveries.stream().mapToDouble(Delivery::getShippingCost).sum();
        double totalPaymentCost = deliveries.stream().mapToDouble(Delivery::getPaymentCost).sum();

        return new FinancialReport(dateFrom, dateTo, totalNumberOfOrders, totalPrice, totalCost, totalProfit, salesVolumeByProvider, totalDeliveries, totalShippingCost, totalPaymentCost);
    }

    private Map<String, Integer> calculateSalesVolumeByProvider(List<Delivery> deliveries) {
        return deliveries.stream()
                .filter(delivery -> delivery.getProvider() != null)
                .collect(Collectors.groupingBy(
                        Delivery::getProvider,
                        Collectors.reducing(0, e -> 1, Integer::sum)
                ));
    }

    private double calculateTotalPriceNet(List<Entry> orders) {
        return orders.stream()
                .map(o -> o.getFinancials().getTotalPriceNet())
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    private double calculateTotalCostNet(List<Entry> orders) {
        return orders.stream()
                .map(o -> o.getFinancials().getTotalCostNet())
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    private double calculateTotalProfitNet(List<Entry> orders) {
        return orders.stream()
                .map(o -> o.getFinancials().getTotalProfitNet())
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    static class Entry {
        private Order order;
        private List<OrderItem> orderItems;
        private OrderFinancials financials;

        public Entry(Order order, List<OrderItem> orderItems) {
            this.order = order;
            this.orderItems = orderItems;
            this.financials = new OrderFinancials(order, orderItems);
        }

        public Order getOrder() {
            return order;
        }

        public List<OrderItem> getOrderItems() {
            return orderItems;
        }

        public OrderFinancials getFinancials() {
            return financials;
        }
    }
}