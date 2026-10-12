package pl.commercelink.financials;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.orders.*;

import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FinancialReportGenerator {

    private final OrdersRepository ordersRepository;
    private final OrderItemsRepository orderItemsRepository;
    private final DeliveriesRepository deliveriesRepository;

    private static final int ITEMS_CHUNK_SIZE = 25;
    private static final int ITEMS_THREADS = 8;
    private static final int CHUNKS_IN_FLIGHT_PER_REPORT = 4;

    private final ExecutorService itemsExecutor = Executors.newFixedThreadPool(ITEMS_THREADS, new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "report-items-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });

    public FinancialReports generate(String storeId, LocalDate dateFrom, LocalDate dateTo) {
        List<String> orderIds = ordersRepository.findAllPastOrders(dateFrom, dateTo, storeId).stream()
                .map(OrderIndexEntry::getOrderId)
                .toList();
        List<Entry> entries = loadEntries(storeId, orderIds);
        var deliveries = deliveriesRepository.findAll(storeId, dateFrom.atStartOfDay(), dateTo.atTime(LocalTime.MAX));

        List<Entry> ownEntries = entries.stream().filter(e -> !e.getOrder().isMarketplaceOrder()).toList();
        List<Entry> marketplaceEntries = entries.stream().filter(e -> e.getOrder().isMarketplaceOrder()).toList();

        FinancialReport ownReport = buildReport(dateFrom, dateTo, ownEntries, deliveries);
        FinancialReport marketplaceReport = buildReport(dateFrom, dateTo, marketplaceEntries, deliveries);

        return new FinancialReports(ownReport, marketplaceReport,
                dailySales(dateFrom, dateTo, entries), supplierDeliveries(deliveries));
    }

    // an index entry whose order was deleted since the index was read has nothing to report, so it is skipped
    private List<Entry> loadEntries(String storeId, List<String> orderIds) {
        List<Order> orders = ordersRepository.findByIds(storeId, orderIds);
        Map<String, List<OrderItem>> itemsByOrder = loadItems(orders.stream().map(Order::getOrderId).toList());
        return orders.stream()
                .map(order -> new Entry(order, itemsByOrder.getOrDefault(order.getOrderId(), List.of())))
                .toList();
    }

    // the items table is queried once per order, so the chunks run in parallel; the pool is bounded to keep a
    // long range from opening an unbounded number of concurrent queries on a table shared with the whole app, and
    // one report keeps only a few chunks queued so that it cannot make another store's report wait behind all of them
    private Map<String, List<OrderItem>> loadItems(List<String> orderIds) {
        Deque<CompletableFuture<Map<String, List<OrderItem>>>> window = new ArrayDeque<>();
        Map<String, List<OrderItem>> items = new HashMap<>();
        int next = 0;
        try {
            while (next < orderIds.size() || !window.isEmpty()) {
                while (next < orderIds.size() && window.size() < CHUNKS_IN_FLIGHT_PER_REPORT) {
                    List<String> chunk = orderIds.subList(next, Math.min(next + ITEMS_CHUNK_SIZE, orderIds.size()));
                    window.add(CompletableFuture.supplyAsync(() -> orderItemsRepository.findByOrderIds(chunk), itemsExecutor));
                    next += ITEMS_CHUNK_SIZE;
                }
                items.putAll(window.poll().join());
            }
        } catch (CompletionException e) {
            window.forEach(pending -> pending.cancel(true));
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            if (e.getCause() instanceof Error cause) {
                throw cause;
            }
            throw e;
        }
        return items;
    }

    @PreDestroy
    void shutdown() {
        itemsExecutor.shutdown();
    }

    // every day of the range gets an entry, so the daily chart shows a day without orders as a gap rather than skipping it
    private List<DailySales> dailySales(LocalDate dateFrom, LocalDate dateTo, List<Entry> entries) {
        Map<LocalDate, DailySales> days = new LinkedHashMap<>();
        for (LocalDate day = dateFrom; !day.isAfter(dateTo); day = day.plusDays(1)) {
            days.put(day, DailySales.empty(day));
        }
        for (Entry entry : entries) {
            if (entry.getOrder().getOrderedAt() == null) {
                continue;
            }
            days.computeIfPresent(entry.getOrder().getOrderedAt().toLocalDate(), (day, sales) -> sales.plus(
                    entry.getOrder().isMarketplaceOrder(),
                    entry.getFinancials().getTotalPriceNet(),
                    entry.getFinancials().getTotalProfitNet()));
        }
        return List.copyOf(days.values());
    }

    private List<SupplierDeliveries> supplierDeliveries(List<Delivery> deliveries) {
        Map<String, List<Delivery>> byProvider = deliveries.stream()
                .collect(Collectors.groupingBy(delivery -> Objects.toString(delivery.getProvider(), ""),
                        LinkedHashMap::new, Collectors.toList()));
        return byProvider.entrySet().stream()
                .map(group -> new SupplierDeliveries(
                        group.getKey().isEmpty() ? null : group.getKey(),
                        group.getValue().size(),
                        group.getValue().stream().mapToDouble(Delivery::getShippingCost).sum(),
                        group.getValue().stream().mapToDouble(Delivery::getPaymentCost).sum()))
                .sorted(Comparator.comparing((SupplierDeliveries supplier) -> supplier.provider() == null)
                        .thenComparing(SupplierDeliveries::deliveries, Comparator.reverseOrder())
                        .thenComparing(SupplierDeliveries::provider, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
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