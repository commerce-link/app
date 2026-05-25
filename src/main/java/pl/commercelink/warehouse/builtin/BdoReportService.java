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
import java.util.ArrayList;
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

        Map<AggregateKey, Aggregate> aggregates = new HashMap<>();
        for (WarehouseDocument doc : documentRepository.findAllInDateRange(storeId, periodStart, periodEnd)) {
            String supplier = resolveSupplier(storeId, doc);
            for (WarehouseDocumentItem item : itemRepository.findByDocumentId(doc.getDocumentId())) {
                Optional<PimEntry> pim = pimCatalog.findByGtinOrMpn(item.getEan(), item.getMfn());
                ProductCategory category = pim.map(PimEntry::category).orElse(null);
                Integer netG = pim.map(PimEntry::netWeightInGrams).orElse(null);
                Integer grossG = pim.map(PimEntry::grossWeightInGrams).orElse(null);

                AggregateKey key = new AggregateKey(category, item.getMfn(), supplier);
                Aggregate agg = aggregates.computeIfAbsent(key, k -> new Aggregate());
                agg.qty += item.getQty();
                if (netG != null) agg.totalNetG += (long) item.getQty() * netG;
                if (grossG != null) agg.totalGrossG += (long) item.getQty() * grossG;
                if (item.getName() != null && !item.getName().isBlank()) agg.latestName = item.getName();
                agg.unitNetG = netG;
                agg.unitGrossG = grossG;
            }
        }

        List<BdoReportRow> rows = new ArrayList<>();
        for (Map.Entry<AggregateKey, Aggregate> e : aggregates.entrySet()) {
            AggregateKey k = e.getKey();
            Aggregate a = e.getValue();
            rows.add(new BdoReportRow(
                    k.category() != null ? k.category().name() : UNKNOWN,
                    a.latestName,
                    k.mfn(),
                    a.qty,
                    a.unitNetG,
                    a.unitGrossG,
                    a.unitNetG != null ? a.totalNetG : null,
                    a.unitGrossG != null ? a.totalGrossG : null,
                    k.supplier()
            ));
        }
        rows.sort(Comparator
                .comparing(BdoReportRow::category, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(BdoReportRow::supplier, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(BdoReportRow::mfn, Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    private String resolveSupplier(String storeId, WarehouseDocument doc) {
        if (doc.getDeliveryId() == null) return UNKNOWN;
        Delivery delivery = deliveriesRepository.findById(storeId, doc.getDeliveryId());
        return delivery != null && delivery.getProvider() != null ? delivery.getProvider() : UNKNOWN;
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
