package pl.commercelink.warehouse.builtin;

import org.springframework.stereotype.Service;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.taxonomy.ProductCategory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class BdoReportService {

    private static final String UNKNOWN = "Unknown";

    private final WarehouseDocumentRepository documentRepository;
    private final WarehouseDocumentItemRepository itemRepository;
    private final PimCatalog pimCatalog;
    private final DeliveriesRepository deliveriesRepository;

    BdoReportService(WarehouseDocumentRepository documentRepository,
                     WarehouseDocumentItemRepository itemRepository,
                     PimCatalog pimCatalog,
                     DeliveriesRepository deliveriesRepository) {
        this.documentRepository = documentRepository;
        this.itemRepository = itemRepository;
        this.pimCatalog = pimCatalog;
        this.deliveriesRepository = deliveriesRepository;
    }

    public List<BdoReportRow> generate(String storeId, LocalDate dateFrom, LocalDate dateTo) {
        LocalDateTime periodStart = dateFrom.atStartOfDay();
        LocalDateTime periodEnd = dateTo.atTime(LocalTime.MAX);

        Map<AggregateKey, Aggregate> aggregates = aggregate(storeId, periodStart, periodEnd);

        return aggregates.entrySet().stream()
                .map(BdoReportService::toRow)
                .sorted(Comparator.comparing(BdoReportRow::category)
                        .thenComparing(BdoReportRow::supplier)
                        .thenComparing(BdoReportRow::mfn))
                .toList();
    }

    private Map<AggregateKey, Aggregate> aggregate(String storeId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        Map<AggregateKey, Aggregate> aggregates = new HashMap<>();
        for (WarehouseDocument doc : documentRepository.findAllInDateRange(storeId, periodStart, periodEnd)) {
            if (doc.getType() != DocumentType.GoodsReceipt) continue;
            if (doc.getReason() != DocumentReason.SupplierDelivery) continue;

            String supplier = resolveSupplier(storeId, doc);
            for (WarehouseDocumentItem item : itemRepository.findByDocumentId(doc.getDocumentId())) {
                accumulate(item, supplier, aggregates);
            }
        }
        return aggregates;
    }

    private void accumulate(WarehouseDocumentItem item, String supplier, Map<AggregateKey, Aggregate> aggregates) {
        if (item.getMfn() == null || item.getMfn().isBlank()) return;
        Optional<PimEntry> pim = pimCatalog.findByGtinOrMpn(item.getEan(), item.getMfn());
        ProductCategory category = pim.map(PimEntry::category).orElse(null);
        Integer netG = pim.map(PimEntry::netWeightInGrams).orElse(null);
        Integer grossG = pim.map(PimEntry::grossWeightInGrams).orElse(null);

        Aggregate agg = aggregates.computeIfAbsent(new AggregateKey(category, item.getMfn(), supplier), k -> new Aggregate());
        agg.qty += item.getQty();
        if (netG != null) agg.totalNetG += (long) item.getQty() * netG;
        if (grossG != null) agg.totalGrossG += (long) item.getQty() * grossG;
        if (item.getName() != null && !item.getName().isBlank()) agg.latestName = item.getName();
        agg.unitNetG = netG;
        agg.unitGrossG = grossG;
    }

    private String resolveSupplier(String storeId, WarehouseDocument doc) {
        if (doc.getDeliveryId() == null) return UNKNOWN;
        Delivery delivery = deliveriesRepository.findById(storeId, doc.getDeliveryId());
        return delivery != null && delivery.getProvider() != null ? delivery.getProvider() : UNKNOWN;
    }

    private static BdoReportRow toRow(Map.Entry<AggregateKey, Aggregate> entry) {
        AggregateKey k = entry.getKey();
        Aggregate a = entry.getValue();
        return new BdoReportRow(
                k.category() != null ? k.category().name() : UNKNOWN,
                a.latestName,
                k.mfn(),
                a.qty,
                a.unitNetG,
                a.unitGrossG,
                a.unitNetG != null ? a.totalNetG : null,
                a.unitGrossG != null ? a.totalGrossG : null,
                k.supplier());
    }

    private record AggregateKey(ProductCategory category, String mfn, String supplier) {}

    private static class Aggregate {
        String latestName;
        int qty;
        long totalNetG;
        long totalGrossG;
        Integer unitNetG;
        Integer unitGrossG;
    }
}
