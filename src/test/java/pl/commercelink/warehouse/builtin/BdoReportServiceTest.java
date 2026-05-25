package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.taxonomy.ProductCategory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BdoReportServiceTest {

    private static final String STORE_ID = "store-1";
    private static final LocalDate FROM = LocalDate.of(2026, 5, 1);
    private static final LocalDate TO   = LocalDate.of(2026, 5, 31);

    @Mock private WarehouseDocumentRepository documentRepository;
    @Mock private WarehouseDocumentItemRepository itemRepository;
    @Mock private PimCatalog pimCatalog;
    @Mock private DeliveriesRepository deliveriesRepository;

    private BdoReportService service;

    @BeforeEach
    void setUp() {
        service = new BdoReportService(documentRepository, itemRepository, pimCatalog, deliveriesRepository);
        when(documentRepository.findAllInDateRange(anyString(), any(), any())).thenReturn(List.of());
    }

    @Test
    void singleFullDocumentProducesOneRowWithMultipliedTotals() {
        WarehouseDocument doc = supplierDeliveryDoc("doc-1", "delivery-1");
        WarehouseDocumentItem item = item(doc.getDocumentId(), "5901234567890", "RTX4070-DUAL", "DUAL RTX 4070", 5);

        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(doc));
        when(itemRepository.findByDocumentId("doc-1")).thenReturn(List.of(item));
        when(pimCatalog.findByGtinOrMpn("5901234567890", "RTX4070-DUAL"))
                .thenReturn(Optional.of(pimEntry(ProductCategory.GPU, 2100, 2400)));
        when(deliveriesRepository.findById(STORE_ID, "delivery-1")).thenReturn(delivery("IngramMicro"));

        List<BdoReportRow> rows = service.generate(STORE_ID, FROM, TO);

        assertThat(rows).hasSize(1);
        BdoReportRow row = rows.get(0);
        assertThat(row.category()).isEqualTo("GPU");
        assertThat(row.name()).isEqualTo("DUAL RTX 4070");
        assertThat(row.mfn()).isEqualTo("RTX4070-DUAL");
        assertThat(row.qty()).isEqualTo(5);
        assertThat(row.weightNetG()).isEqualTo(2100);
        assertThat(row.weightGrossG()).isEqualTo(2400);
        assertThat(row.totalWeightNetG()).isEqualTo(5L * 2100);
        assertThat(row.totalWeightGrossG()).isEqualTo(5L * 2400);
        assertThat(row.supplier()).isEqualTo("IngramMicro");
    }

    @Test
    void skipsDocumentsThatAreNotGoodsReceipt() {
        WarehouseDocument issue = supplierDeliveryDoc("doc-issue", "delivery-x");
        issue.setType(DocumentType.GoodsIssue);
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(issue));
        when(itemRepository.findByDocumentId("doc-issue"))
                .thenReturn(List.of(item("doc-issue", "5901111111111", "MFN-ISSUE", "Widget", 2)));
        when(pimCatalog.findByGtinOrMpn("5901111111111", "MFN-ISSUE"))
                .thenReturn(Optional.of(pimEntry(ProductCategory.GPU, 1000, 1200)));
        when(deliveriesRepository.findById(STORE_ID, "delivery-x")).thenReturn(delivery("IngramMicro"));

        assertThat(service.generate(STORE_ID, FROM, TO)).isEmpty();
    }

    @Test
    void skipsGoodsReceiptWithNonSupplierDeliveryReason() {
        WarehouseDocument customerReturn = supplierDeliveryDoc("doc-rma", "delivery-rma");
        customerReturn.setReason(DocumentReason.CustomerReturn);
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(customerReturn));
        when(itemRepository.findByDocumentId("doc-rma"))
                .thenReturn(List.of(item("doc-rma", "5902222222222", "MFN-RMA", "Returned item", 1)));
        when(pimCatalog.findByGtinOrMpn("5902222222222", "MFN-RMA"))
                .thenReturn(Optional.of(pimEntry(ProductCategory.GPU, 500, 600)));
        when(deliveriesRepository.findById(STORE_ID, "delivery-rma")).thenReturn(delivery("IngramMicro"));

        assertThat(service.generate(STORE_ID, FROM, TO)).isEmpty();
    }

    @Test
    void skipsItemsWithBlankMfn() {
        WarehouseDocument doc = supplierDeliveryDoc("doc-blank", "delivery-1");
        WarehouseDocumentItem blank = item("doc-blank", "5901111111111", "", "Something", 3);

        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(doc));
        when(itemRepository.findByDocumentId("doc-blank")).thenReturn(List.of(blank));
        when(deliveriesRepository.findById(STORE_ID, "delivery-1")).thenReturn(delivery("IngramMicro"));

        assertThat(service.generate(STORE_ID, FROM, TO)).isEmpty();
    }

    @Test
    void sumsQuantitiesAndTotalsWhenSameMfnFromSameSupplierAppearsTwice() {
        WarehouseDocument doc1 = supplierDeliveryDoc("doc-1", "delivery-1");
        WarehouseDocument doc2 = supplierDeliveryDoc("doc-2", "delivery-2");
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(doc1, doc2));
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", "5900000000001", "MFN-A", "Name A", 3)));
        when(itemRepository.findByDocumentId("doc-2"))
                .thenReturn(List.of(item("doc-2", "5900000000001", "MFN-A", "Name A", 2)));
        when(pimCatalog.findByGtinOrMpn("5900000000001", "MFN-A"))
                .thenReturn(Optional.of(pimEntry(ProductCategory.GPU, 1000, 1200)));
        when(deliveriesRepository.findById(STORE_ID, "delivery-1")).thenReturn(delivery("IngramMicro"));
        when(deliveriesRepository.findById(STORE_ID, "delivery-2")).thenReturn(delivery("IngramMicro"));

        List<BdoReportRow> rows = service.generate(STORE_ID, FROM, TO);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).qty()).isEqualTo(5);
        assertThat(rows.get(0).totalWeightNetG()).isEqualTo(5L * 1000);
        assertThat(rows.get(0).totalWeightGrossG()).isEqualTo(5L * 1200);
        assertThat(rows.get(0).supplier()).isEqualTo("IngramMicro");
    }

    @Test
    void emitsSeparateRowsWhenSameMfnComesFromDifferentSuppliers() {
        WarehouseDocument doc1 = supplierDeliveryDoc("doc-1", "delivery-1");
        WarehouseDocument doc2 = supplierDeliveryDoc("doc-2", "delivery-2");
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(doc1, doc2));
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", "5900000000001", "MFN-A", "Name A", 3)));
        when(itemRepository.findByDocumentId("doc-2"))
                .thenReturn(List.of(item("doc-2", "5900000000001", "MFN-A", "Name A", 2)));
        when(pimCatalog.findByGtinOrMpn("5900000000001", "MFN-A"))
                .thenReturn(Optional.of(pimEntry(ProductCategory.GPU, 1000, 1200)));
        when(deliveriesRepository.findById(STORE_ID, "delivery-1")).thenReturn(delivery("IngramMicro"));
        when(deliveriesRepository.findById(STORE_ID, "delivery-2")).thenReturn(delivery("AbGroup"));

        List<BdoReportRow> rows = service.generate(STORE_ID, FROM, TO);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(BdoReportRow::supplier).containsExactlyInAnyOrder("IngramMicro", "AbGroup");
        assertThat(rows).allSatisfy(r -> assertThat(r.mfn()).isEqualTo("MFN-A"));
    }

    @Test
    void usesUnknownCategoryAndEmptyTotalsWhenPimEntryMissing() {
        WarehouseDocument doc = supplierDeliveryDoc("doc-pim-miss", "delivery-1");
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(doc));
        when(itemRepository.findByDocumentId("doc-pim-miss"))
                .thenReturn(List.of(item("doc-pim-miss", "5900000000002", "MFN-MISS", "Mystery", 4)));
        when(pimCatalog.findByGtinOrMpn("5900000000002", "MFN-MISS")).thenReturn(Optional.empty());
        when(deliveriesRepository.findById(STORE_ID, "delivery-1")).thenReturn(delivery("IngramMicro"));

        List<BdoReportRow> rows = service.generate(STORE_ID, FROM, TO);

        assertThat(rows).hasSize(1);
        BdoReportRow row = rows.get(0);
        assertThat(row.category()).isEqualTo("Unknown");
        assertThat(row.weightNetG()).isNull();
        assertThat(row.weightGrossG()).isNull();
        assertThat(row.totalWeightNetG()).isNull();
        assertThat(row.totalWeightGrossG()).isNull();
    }

    @Test
    void leavesNetSideEmptyWhenPimEntryHasOnlyGrossWeight() {
        WarehouseDocument doc = supplierDeliveryDoc("doc-half", "delivery-1");
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(doc));
        when(itemRepository.findByDocumentId("doc-half"))
                .thenReturn(List.of(item("doc-half", "5900000000003", "MFN-HALF", "Half", 7)));
        when(pimCatalog.findByGtinOrMpn("5900000000003", "MFN-HALF"))
                .thenReturn(Optional.of(pimEntry(ProductCategory.PSU, null, 1500)));
        when(deliveriesRepository.findById(STORE_ID, "delivery-1")).thenReturn(delivery("IngramMicro"));

        List<BdoReportRow> rows = service.generate(STORE_ID, FROM, TO);

        assertThat(rows).hasSize(1);
        BdoReportRow row = rows.get(0);
        assertThat(row.weightNetG()).isNull();
        assertThat(row.totalWeightNetG()).isNull();
        assertThat(row.weightGrossG()).isEqualTo(1500);
        assertThat(row.totalWeightGrossG()).isEqualTo(7L * 1500);
    }

    @Test
    void supplierIsUnknownWhenDocumentHasNoDeliveryId() {
        WarehouseDocument doc = supplierDeliveryDoc("doc-manual", null);
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(doc));
        when(itemRepository.findByDocumentId("doc-manual"))
                .thenReturn(List.of(item("doc-manual", "5900000000004", "MFN-X", "X", 1)));
        when(pimCatalog.findByGtinOrMpn("5900000000004", "MFN-X"))
                .thenReturn(Optional.of(pimEntry(ProductCategory.GPU, 1000, 1200)));

        List<BdoReportRow> rows = service.generate(STORE_ID, FROM, TO);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).supplier()).isEqualTo("Unknown");
    }

    @Test
    void supplierIsUnknownWhenDeliveryNotFound() {
        WarehouseDocument doc = supplierDeliveryDoc("doc-missing-delivery", "delivery-ghost");
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(doc));
        when(itemRepository.findByDocumentId("doc-missing-delivery"))
                .thenReturn(List.of(item("doc-missing-delivery", "5900000000005", "MFN-Y", "Y", 1)));
        when(pimCatalog.findByGtinOrMpn("5900000000005", "MFN-Y"))
                .thenReturn(Optional.of(pimEntry(ProductCategory.GPU, 500, 600)));
        when(deliveriesRepository.findById(STORE_ID, "delivery-ghost")).thenReturn(null);

        List<BdoReportRow> rows = service.generate(STORE_ID, FROM, TO);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).supplier()).isEqualTo("Unknown");
    }

    @Test
    void sortsResultByCategoryThenSupplierThenMfn() {
        WarehouseDocument d1 = supplierDeliveryDoc("doc-1", "delivery-1");
        WarehouseDocument d2 = supplierDeliveryDoc("doc-2", "delivery-2");
        WarehouseDocument d3 = supplierDeliveryDoc("doc-3", "delivery-3");
        WarehouseDocument d4 = supplierDeliveryDoc("doc-4", "delivery-4");
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(d1, d2, d3, d4));

        when(itemRepository.findByDocumentId("doc-1")).thenReturn(List.of(item("doc-1", "ean-1", "M-Z", "z", 1)));
        when(itemRepository.findByDocumentId("doc-2")).thenReturn(List.of(item("doc-2", "ean-2", "M-A", "a", 1)));
        when(itemRepository.findByDocumentId("doc-3")).thenReturn(List.of(item("doc-3", "ean-3", "M-A", "a", 1)));
        when(itemRepository.findByDocumentId("doc-4")).thenReturn(List.of(item("doc-4", "ean-4", "M-B", "b", 1)));

        when(pimCatalog.findByGtinOrMpn("ean-1", "M-Z")).thenReturn(Optional.of(pimEntry(ProductCategory.PSU, 1, 1)));
        when(pimCatalog.findByGtinOrMpn("ean-2", "M-A")).thenReturn(Optional.of(pimEntry(ProductCategory.GPU, 1, 1)));
        when(pimCatalog.findByGtinOrMpn("ean-3", "M-A")).thenReturn(Optional.of(pimEntry(ProductCategory.GPU, 1, 1)));
        when(pimCatalog.findByGtinOrMpn("ean-4", "M-B")).thenReturn(Optional.of(pimEntry(ProductCategory.GPU, 1, 1)));

        when(deliveriesRepository.findById(STORE_ID, "delivery-1")).thenReturn(delivery("IngramMicro"));
        when(deliveriesRepository.findById(STORE_ID, "delivery-2")).thenReturn(delivery("IngramMicro"));
        when(deliveriesRepository.findById(STORE_ID, "delivery-3")).thenReturn(delivery("AbGroup"));
        when(deliveriesRepository.findById(STORE_ID, "delivery-4")).thenReturn(delivery("AbGroup"));

        List<BdoReportRow> rows = service.generate(STORE_ID, FROM, TO);

        assertThat(rows).extracting(BdoReportRow::category, BdoReportRow::supplier, BdoReportRow::mfn)
                .containsExactly(
                        tuple("GPU", "AbGroup", "M-A"),
                        tuple("GPU", "AbGroup", "M-B"),
                        tuple("GPU", "IngramMicro", "M-A"),
                        tuple("PSU", "IngramMicro", "M-Z")
                );
    }

    // --- helpers ---

    private static WarehouseDocument supplierDeliveryDoc(String documentId, String deliveryId) {
        WarehouseDocument doc = new WarehouseDocument();
        doc.setStoreId(STORE_ID);
        doc.setDocumentId(documentId);
        doc.setType(DocumentType.GoodsReceipt);
        doc.setReason(DocumentReason.SupplierDelivery);
        doc.setDeliveryId(deliveryId);
        doc.setCreatedAt(LocalDateTime.of(2026, 5, 10, 12, 0));
        return doc;
    }

    private static WarehouseDocumentItem item(String documentId, String ean, String mfn, String name, int qty) {
        WarehouseDocumentItem item = new WarehouseDocumentItem();
        item.setDocumentId(documentId);
        item.setItemId("item-" + mfn);
        item.setEan(ean);
        item.setMfn(mfn);
        item.setName(name);
        item.setQty(qty);
        return item;
    }

    private static PimEntry pimEntry(ProductCategory category, Integer netG, Integer grossG) {
        return new PimEntry(
                "pim-" + category.name(),
                List.<PimIdentifier>of(),
                "Brand",
                "Name",
                category,
                null,
                true,
                netG,
                grossG
        );
    }

    private static Delivery delivery(String provider) {
        Delivery d = new Delivery();
        d.setStoreId(STORE_ID);
        d.setProvider(provider);
        return d;
    }
}
