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
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimIdentifier;

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
class ProductWeightOriginComplianceReportServiceTest {

    private static final String STORE_ID = "store-1";
    private static final LocalDate FROM = LocalDate.of(2026, 5, 1);
    private static final LocalDate TO   = LocalDate.of(2026, 5, 31);

    @Mock private WarehouseDocumentRepository documentRepository;
    @Mock private WarehouseDocumentItemRepository itemRepository;
    @Mock private PimCatalog pimCatalog;

    private ProductWeightOriginComplianceReportService service;

    @BeforeEach
    void setUp() {
        service = new ProductWeightOriginComplianceReportService(documentRepository, itemRepository, pimCatalog);
        when(documentRepository.findAllInDateRange(anyString(), any(), any())).thenReturn(List.of());
    }

    @Test
    void singleFullDocumentProducesOneRowWithMultipliedTotalsAndBrand() {
        WarehouseDocument doc = goodsReceiptDoc("doc-1", "Germany");
        WarehouseDocumentItem item = item(doc.getDocumentId(), "5901234567890", "RTX4070-DUAL", "DUAL RTX 4070", 5);

        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(doc));
        when(itemRepository.findByDocumentId("doc-1")).thenReturn(List.of(item));
        when(pimCatalog.findByGtinOrMpn("5901234567890", "RTX4070-DUAL"))
                .thenReturn(Optional.of(pimEntry("GPU", "ASUS", 2100, 2400)));

        List<ProductWeightOriginComplianceReportRow> rows = service.generate(STORE_ID, FROM, TO);

        assertThat(rows).hasSize(1);
        ProductWeightOriginComplianceReportRow row = rows.get(0);
        assertThat(row.category()).isEqualTo("GPU");
        assertThat(row.brand()).isEqualTo("ASUS");
        assertThat(row.name()).isEqualTo("DUAL RTX 4070");
        assertThat(row.mfn()).isEqualTo("RTX4070-DUAL");
        assertThat(row.qty()).isEqualTo(5);
        assertThat(row.weightNetG()).isEqualTo(2100);
        assertThat(row.weightGrossG()).isEqualTo(2400);
        assertThat(row.totalWeightNetG()).isEqualTo(5L * 2100);
        assertThat(row.totalWeightGrossG()).isEqualTo(5L * 2400);
        assertThat(row.country()).isEqualTo("Germany");
    }

    @Test
    void skipsDocumentsThatAreNotGoodsReceipt() {
        WarehouseDocument issue = goodsReceiptDoc("doc-issue", "Germany");
        issue.setType(DocumentType.GoodsIssue);
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(issue));
        when(itemRepository.findByDocumentId("doc-issue"))
                .thenReturn(List.of(item("doc-issue", "5901111111111", "MFN-ISSUE", "Widget", 2)));
        when(pimCatalog.findByGtinOrMpn("5901111111111", "MFN-ISSUE"))
                .thenReturn(Optional.of(pimEntry("GPU", "ASUS", 1000, 1200)));

        assertThat(service.generate(STORE_ID, FROM, TO)).isEmpty();
    }

    @Test
    void skipsGoodsReceiptWithNonSupplierDeliveryReason() {
        WarehouseDocument customerReturn = goodsReceiptDoc("doc-rma", "Germany");
        customerReturn.setReason(DocumentReason.CustomerReturn);
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(customerReturn));
        when(itemRepository.findByDocumentId("doc-rma"))
                .thenReturn(List.of(item("doc-rma", "5902222222222", "MFN-RMA", "Returned item", 1)));
        when(pimCatalog.findByGtinOrMpn("5902222222222", "MFN-RMA"))
                .thenReturn(Optional.of(pimEntry("GPU", "ASUS", 500, 600)));

        assertThat(service.generate(STORE_ID, FROM, TO)).isEmpty();
    }

    @Test
    void skipsItemsWithBlankMfn() {
        WarehouseDocument doc = goodsReceiptDoc("doc-blank", "Germany");
        WarehouseDocumentItem blank = item("doc-blank", "5901111111111", "", "Something", 3);

        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(doc));
        when(itemRepository.findByDocumentId("doc-blank")).thenReturn(List.of(blank));

        assertThat(service.generate(STORE_ID, FROM, TO)).isEmpty();
    }

    @Test
    void sumsQuantitiesAndTotalsWhenSameMfnFromSameCountryAppearsTwice() {
        WarehouseDocument doc1 = goodsReceiptDoc("doc-1", "Germany");
        WarehouseDocument doc2 = goodsReceiptDoc("doc-2", "Germany");
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(doc1, doc2));
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", "5900000000001", "MFN-A", "Name A", 3)));
        when(itemRepository.findByDocumentId("doc-2"))
                .thenReturn(List.of(item("doc-2", "5900000000001", "MFN-A", "Name A", 2)));
        when(pimCatalog.findByGtinOrMpn("5900000000001", "MFN-A"))
                .thenReturn(Optional.of(pimEntry("GPU", "ASUS", 1000, 1200)));

        List<ProductWeightOriginComplianceReportRow> rows = service.generate(STORE_ID, FROM, TO);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).qty()).isEqualTo(5);
        assertThat(rows.get(0).totalWeightNetG()).isEqualTo(5L * 1000);
        assertThat(rows.get(0).totalWeightGrossG()).isEqualTo(5L * 1200);
        assertThat(rows.get(0).country()).isEqualTo("Germany");
    }

    @Test
    void emitsSeparateRowsWhenSameMfnComesFromDifferentCountries() {
        WarehouseDocument doc1 = goodsReceiptDoc("doc-1", "Germany");
        WarehouseDocument doc2 = goodsReceiptDoc("doc-2", "Poland");
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(doc1, doc2));
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", "5900000000001", "MFN-A", "Name A", 3)));
        when(itemRepository.findByDocumentId("doc-2"))
                .thenReturn(List.of(item("doc-2", "5900000000001", "MFN-A", "Name A", 2)));
        when(pimCatalog.findByGtinOrMpn("5900000000001", "MFN-A"))
                .thenReturn(Optional.of(pimEntry("GPU", "ASUS", 1000, 1200)));

        List<ProductWeightOriginComplianceReportRow> rows = service.generate(STORE_ID, FROM, TO);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(ProductWeightOriginComplianceReportRow::country)
                .containsExactlyInAnyOrder("Germany", "Poland");
        assertThat(rows).allSatisfy(r -> assertThat(r.mfn()).isEqualTo("MFN-A"));
    }

    @Test
    void usesUnknownCategoryAndEmptyTotalsAndNoBrandWhenPimEntryMissing() {
        WarehouseDocument doc = goodsReceiptDoc("doc-pim-miss", "Germany");
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(doc));
        when(itemRepository.findByDocumentId("doc-pim-miss"))
                .thenReturn(List.of(item("doc-pim-miss", "5900000000002", "MFN-MISS", "Mystery", 4)));
        when(pimCatalog.findByGtinOrMpn("5900000000002", "MFN-MISS")).thenReturn(Optional.empty());

        List<ProductWeightOriginComplianceReportRow> rows = service.generate(STORE_ID, FROM, TO);

        assertThat(rows).hasSize(1);
        ProductWeightOriginComplianceReportRow row = rows.get(0);
        assertThat(row.category()).isEqualTo("Unknown");
        assertThat(row.brand()).isNull();
        assertThat(row.weightNetG()).isNull();
        assertThat(row.weightGrossG()).isNull();
        assertThat(row.totalWeightNetG()).isNull();
        assertThat(row.totalWeightGrossG()).isNull();
    }

    @Test
    void leavesNetSideEmptyWhenPimEntryHasOnlyGrossWeight() {
        WarehouseDocument doc = goodsReceiptDoc("doc-half", "Germany");
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(doc));
        when(itemRepository.findByDocumentId("doc-half"))
                .thenReturn(List.of(item("doc-half", "5900000000003", "MFN-HALF", "Half", 7)));
        when(pimCatalog.findByGtinOrMpn("5900000000003", "MFN-HALF"))
                .thenReturn(Optional.of(pimEntry("PSU", "Corsair", null, 1500)));

        List<ProductWeightOriginComplianceReportRow> rows = service.generate(STORE_ID, FROM, TO);

        assertThat(rows).hasSize(1);
        ProductWeightOriginComplianceReportRow row = rows.get(0);
        assertThat(row.brand()).isEqualTo("Corsair");
        assertThat(row.weightNetG()).isNull();
        assertThat(row.totalWeightNetG()).isNull();
        assertThat(row.weightGrossG()).isEqualTo(1500);
        assertThat(row.totalWeightGrossG()).isEqualTo(7L * 1500);
    }

    @Test
    void countryIsUnknownWhenCounterpartyMissing() {
        WarehouseDocument doc = goodsReceiptDoc("doc-no-counter", null);
        doc.setCounterparty(null);
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(doc));
        when(itemRepository.findByDocumentId("doc-no-counter"))
                .thenReturn(List.of(item("doc-no-counter", "5900000000004", "MFN-X", "X", 1)));
        when(pimCatalog.findByGtinOrMpn("5900000000004", "MFN-X"))
                .thenReturn(Optional.of(pimEntry("GPU", "ASUS", 1000, 1200)));

        List<ProductWeightOriginComplianceReportRow> rows = service.generate(STORE_ID, FROM, TO);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).country()).isEqualTo("Unknown");
    }

    @Test
    void countryIsUnknownWhenCounterpartyCountryBlank() {
        WarehouseDocument doc = goodsReceiptDoc("doc-blank-country", "");
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(doc));
        when(itemRepository.findByDocumentId("doc-blank-country"))
                .thenReturn(List.of(item("doc-blank-country", "5900000000005", "MFN-Y", "Y", 1)));
        when(pimCatalog.findByGtinOrMpn("5900000000005", "MFN-Y"))
                .thenReturn(Optional.of(pimEntry("GPU", "ASUS", 500, 600)));

        List<ProductWeightOriginComplianceReportRow> rows = service.generate(STORE_ID, FROM, TO);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).country()).isEqualTo("Unknown");
    }

    @Test
    void sortsResultByCountryThenCategoryThenMfn() {
        WarehouseDocument d1 = goodsReceiptDoc("doc-1", "Germany");
        WarehouseDocument d2 = goodsReceiptDoc("doc-2", "Germany");
        WarehouseDocument d3 = goodsReceiptDoc("doc-3", "Austria");
        WarehouseDocument d4 = goodsReceiptDoc("doc-4", "Austria");
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(d1, d2, d3, d4));

        when(itemRepository.findByDocumentId("doc-1")).thenReturn(List.of(item("doc-1", "ean-1", "M-DE-GPU", "DE GPU", 1)));
        when(itemRepository.findByDocumentId("doc-2")).thenReturn(List.of(item("doc-2", "ean-2", "M-DE-PSU", "DE PSU", 1)));
        when(itemRepository.findByDocumentId("doc-3")).thenReturn(List.of(item("doc-3", "ean-3", "M-AT-GPU", "AT GPU", 1)));
        when(itemRepository.findByDocumentId("doc-4")).thenReturn(List.of(item("doc-4", "ean-4", "M-AT-PSU", "AT PSU", 1)));

        when(pimCatalog.findByGtinOrMpn("ean-1", "M-DE-GPU")).thenReturn(Optional.of(pimEntry("GPU", "ASUS", 1, 1)));
        when(pimCatalog.findByGtinOrMpn("ean-2", "M-DE-PSU")).thenReturn(Optional.of(pimEntry("PSU", "Corsair", 1, 1)));
        when(pimCatalog.findByGtinOrMpn("ean-3", "M-AT-GPU")).thenReturn(Optional.of(pimEntry("GPU", "ASUS", 1, 1)));
        when(pimCatalog.findByGtinOrMpn("ean-4", "M-AT-PSU")).thenReturn(Optional.of(pimEntry("PSU", "Corsair", 1, 1)));

        List<ProductWeightOriginComplianceReportRow> rows = service.generate(STORE_ID, FROM, TO);

        assertThat(rows).extracting(
                        ProductWeightOriginComplianceReportRow::country,
                        ProductWeightOriginComplianceReportRow::category,
                        ProductWeightOriginComplianceReportRow::mfn)
                .containsExactly(
                        tuple("Austria", "GPU", "M-AT-GPU"),
                        tuple("Austria", "PSU", "M-AT-PSU"),
                        tuple("Germany", "GPU", "M-DE-GPU"),
                        tuple("Germany", "PSU", "M-DE-PSU")
                );
    }

    // --- helpers ---

    private static WarehouseDocument goodsReceiptDoc(String documentId, String country) {
        WarehouseDocument doc = new WarehouseDocument();
        doc.setStoreId(STORE_ID);
        doc.setDocumentId(documentId);
        doc.setType(DocumentType.GoodsReceipt);
        doc.setReason(DocumentReason.SupplierDelivery);
        doc.setCreatedAt(LocalDateTime.of(2026, 5, 10, 12, 0));
        CounterpartyDetails counterparty = new CounterpartyDetails();
        counterparty.setCountry(country);
        doc.setCounterparty(counterparty);
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

    private static PimEntry pimEntry(String category, String brand, Integer netG, Integer grossG) {
        return new PimEntry(
                "pim-" + category,
                List.<PimIdentifier>of(),
                brand,
                "Name",
                category,
                null,
                true,
                netG,
                grossG
        );
    }
}
