package pl.commercelink.warehouse.builtin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;
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
@RequiredArgsConstructor
public class ProductWeightOriginComplianceReportService {

    private static final String UNKNOWN = "Unknown";

    private final WarehouseDocumentRepository documentRepository;
    private final WarehouseDocumentItemRepository itemRepository;
    private final PimCatalog pimCatalog;

    public List<ProductWeightOriginComplianceReportRow> generate(String storeId, LocalDate dateFrom, LocalDate dateTo) {
        LocalDateTime periodStart = dateFrom.atStartOfDay();
        LocalDateTime periodEnd = dateTo.atTime(LocalTime.MAX);

        Map<AggregateKey, Aggregate> aggregates = aggregate(storeId, periodStart, periodEnd);

        return aggregates.entrySet().stream()
                .map(ProductWeightOriginComplianceReportService::toRow)
                .sorted(Comparator.comparing(ProductWeightOriginComplianceReportRow::country)
                        .thenComparing(ProductWeightOriginComplianceReportRow::category)
                        .thenComparing(ProductWeightOriginComplianceReportRow::mfn))
                .toList();
    }

    private Map<AggregateKey, Aggregate> aggregate(String storeId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        Map<AggregateKey, Aggregate> aggregates = new HashMap<>();
        for (WarehouseDocument doc : documentRepository.findAllInDateRange(storeId, periodStart, periodEnd)) {
            if (doc.getType() != DocumentType.GoodsReceipt) continue;
            if (doc.getReason() != DocumentReason.SupplierDelivery) continue;

            String country = resolveCountry(doc);
            for (WarehouseDocumentItem item : itemRepository.findByDocumentId(doc.getDocumentId())) {
                accumulate(item, country, aggregates);
            }
        }
        return aggregates;
    }

    private void accumulate(WarehouseDocumentItem item, String country, Map<AggregateKey, Aggregate> aggregates) {
        if (item.getMfn() == null || item.getMfn().isBlank()) return;
        Optional<PimEntry> pim = pimCatalog.findByGtinOrMpn(item.getEan(), item.getMfn());
        ProductCategory category = pim.map(PimEntry::category).orElse(null);
        String brand = pim.map(PimEntry::brand).orElse(null);
        Integer netG = pim.map(PimEntry::netWeightInGrams).orElse(null);
        Integer grossG = pim.map(PimEntry::grossWeightInGrams).orElse(null);

        Aggregate agg = aggregates.computeIfAbsent(new AggregateKey(category, item.getMfn(), country), k -> new Aggregate());
        agg.qty += item.getQty();
        if (netG != null) agg.totalNetG += (long) item.getQty() * netG;
        if (grossG != null) agg.totalGrossG += (long) item.getQty() * grossG;
        if (item.getName() != null && !item.getName().isBlank()) agg.latestName = item.getName();
        if (brand != null && !brand.isBlank()) agg.latestBrand = brand;
        agg.unitNetG = netG;
        agg.unitGrossG = grossG;
    }

    private String resolveCountry(WarehouseDocument doc) {
        if (doc.getCounterparty() == null) return UNKNOWN;
        String country = doc.getCounterparty().getCountry();
        return (country == null || country.isBlank()) ? UNKNOWN : country;
    }

    private static ProductWeightOriginComplianceReportRow toRow(Map.Entry<AggregateKey, Aggregate> entry) {
        AggregateKey k = entry.getKey();
        Aggregate a = entry.getValue();
        return new ProductWeightOriginComplianceReportRow(
                k.country(),
                k.category() != null ? k.category().name() : UNKNOWN,
                a.latestBrand,
                a.latestName,
                k.mfn(),
                a.qty,
                a.unitNetG,
                a.unitGrossG,
                a.unitNetG != null ? a.totalNetG : null,
                a.unitGrossG != null ? a.totalGrossG : null);
    }

    private record AggregateKey(ProductCategory category, String mfn, String country) {}

    private static class Aggregate {
        String latestName;
        String latestBrand;
        int qty;
        long totalNetG;
        long totalGrossG;
        Integer unitNetG;
        Integer unitGrossG;
    }
}
