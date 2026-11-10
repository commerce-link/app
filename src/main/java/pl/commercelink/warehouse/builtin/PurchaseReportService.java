package pl.commercelink.warehouse.builtin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.products.ProductData;
import pl.commercelink.products.ProductDataResolver;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PurchaseReportService {

    private static final String UNKNOWN = "Unknown";

    private final WarehouseDocumentRepository documentRepository;
    private final WarehouseDocumentItemRepository itemRepository;
    private final DeliveriesRepository deliveriesRepository;
    private final ProductDataResolver productDataResolver;

    public List<PurchaseReportRow> generate(String storeId, LocalDate dateFrom, LocalDate dateTo) {
        LocalDateTime periodStart = dateFrom.atStartOfDay();
        LocalDateTime periodEnd = dateTo.atTime(LocalTime.MAX);

        Map<AggregateKey, Aggregate> aggregates = aggregate(storeId, periodStart, periodEnd);

        return aggregates.entrySet().stream()
                .map(PurchaseReportService::toRow)
                .sorted(Comparator.comparing(PurchaseReportRow::category)
                        .thenComparing(PurchaseReportRow::supplier)
                        .thenComparing(PurchaseReportRow::ean))
                .toList();
    }

    private Map<AggregateKey, Aggregate> aggregate(String storeId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        Map<AggregateKey, Aggregate> aggregates = new HashMap<>();
        Map<String, String> supplierByDeliveryId = new HashMap<>();
        for (WarehouseDocument doc : documentRepository.findAllInDateRange(storeId, periodStart, periodEnd)) {
            if (doc.getType() != DocumentType.GoodsReceipt) continue;
            if (doc.getReason() != DocumentReason.SupplierDelivery) continue;

            String supplier = resolveSupplier(storeId, doc, supplierByDeliveryId);
            for (WarehouseDocumentItem item : itemRepository.findByDocumentId(doc.getDocumentId())) {
                accumulate(storeId, item, supplier, aggregates);
            }
        }
        return aggregates;
    }

    private void accumulate(String storeId, WarehouseDocumentItem item, String supplier, Map<AggregateKey, Aggregate> aggregates) {
        ProductData product = productDataResolver.ofEan(storeId, item.getEan()).orElse(ProductData.EMPTY);

        Aggregate agg = aggregates.computeIfAbsent(new AggregateKey(supplier, product.category(), item.getEan()), k -> new Aggregate());
        agg.qty += item.getQty();
        if (item.getName() != null && !item.getName().isBlank()) agg.latestName = item.getName();
        if (product.brand() != null && !product.brand().isBlank()) agg.latestBrand = product.brand();
    }

    private String resolveSupplier(String storeId, WarehouseDocument doc, Map<String, String> supplierByDeliveryId) {
        if (doc.getDeliveryId() != null && !doc.getDeliveryId().isBlank()) {
            String supplier = supplierByDeliveryId.computeIfAbsent(doc.getDeliveryId(),
                    deliveryId -> Optional.ofNullable(deliveriesRepository.findById(storeId, deliveryId))
                            .map(Delivery::getProvider)
                            .filter(provider -> !provider.isBlank())
                            .orElse(""));
            if (!supplier.isEmpty()) return supplier;
        }
        return resolveCounterpartyName(doc);
    }

    private static String resolveCounterpartyName(WarehouseDocument doc) {
        if (doc.getCounterparty() == null) return UNKNOWN;
        String companyName = doc.getCounterparty().getCompanyName();
        return (companyName == null || companyName.isBlank()) ? UNKNOWN : companyName;
    }

    private static PurchaseReportRow toRow(Map.Entry<AggregateKey, Aggregate> entry) {
        AggregateKey k = entry.getKey();
        Aggregate a = entry.getValue();
        return new PurchaseReportRow(
                k.category() != null ? k.category() : UNKNOWN,
                k.supplier(),
                a.latestBrand,
                a.latestName,
                k.ean(),
                a.qty);
    }

    private record AggregateKey(String supplier, String category, String ean) {}

    private static class Aggregate {
        String latestName;
        String latestBrand;
        int qty;
    }
}
