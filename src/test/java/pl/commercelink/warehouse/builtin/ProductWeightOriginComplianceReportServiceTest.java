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
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductWeightOriginComplianceReportServiceTest {

    private static final String STORE_ID = "store-1";
    private static final LocalDate FROM = LocalDate.of(2026, 5, 1);
    private static final LocalDate TO   = LocalDate.of(2026, 5, 31);
    private static final String CODE = "MZ-V9P2T0BW";
    private static final String EAN_PLAIN = "8806094215038";
    private static final String EAN_HEATSINK = "8806094215045";

    @Mock private WarehouseDocumentRepository documentRepository;
    @Mock private WarehouseDocumentItemRepository itemRepository;
    @Mock private PimCatalog pimCatalog;
    @Mock private PimView pimView;

    private TaxonomyCache taxonomy;
    private ProductWeightOriginComplianceReportService service;

    @BeforeEach
    void setUp() {
        when(pimCatalog.withStoreData(any())).thenReturn(pimView);
        when(pimCatalog.withGlobalData()).thenReturn(pimView);
        when(documentRepository.findAllInDateRange(anyString(), any(), any())).thenReturn(List.of());
        taxonomy = new TaxonomyCache(mock(TaxonomyRepository.class));
        service = new ProductWeightOriginComplianceReportService(documentRepository, itemRepository, taxonomy, pimCatalog);
    }

    @Test
    void singleFullDocumentProducesOneRowWithMultipliedTotalsAndBrand() {
        WarehouseDocument doc = goodsReceiptDoc("doc-1", "Germany");
        WarehouseDocumentItem item = item(doc.getDocumentId(), "5901234567890", "RTX4070-DUAL", "DUAL RTX 4070", 5);

        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(doc));
        when(itemRepository.findByDocumentId("doc-1")).thenReturn(List.of(item));
        when(pimView.findByGtin("5901234567890"))
                .thenReturn(Optional.of(pimEntry("GPU", "ASUS", 2100, 2400)));

        List<ProductWeightOriginComplianceReportRow> rows = service.generate(STORE_ID, FROM, TO);

        assertThat(rows).hasSize(1);
        ProductWeightOriginComplianceReportRow row = rows.get(0);
        assertThat(row.category()).isEqualTo("GPU");
        assertThat(row.brand()).isEqualTo("ASUS");
        assertThat(row.name()).isEqualTo("DUAL RTX 4070");
        assertThat(row.ean()).isEqualTo("5901234567890");
        assertThat(row.qty()).isEqualTo(5);
        assertThat(row.weightNetG()).isEqualTo(2100);
        assertThat(row.weightGrossG()).isEqualTo(2400);
        assertThat(row.totalWeightNetG()).isEqualTo(5L * 2100);
        assertThat(row.totalWeightGrossG()).isEqualTo(5L * 2400);
        assertThat(row.country()).isEqualTo("Germany");
    }

    @Test
    void twoProductsUnderOneCodeGiveTwoRows() {
        // given
        givenDocuments(goodsReceiptDoc("doc-1", "Germany"));
        when(itemRepository.findByDocumentId("doc-1")).thenReturn(List.of(
                item("doc-1", EAN_PLAIN, CODE, "Samsung 990 Pro 2TB", 2),
                item("doc-1", EAN_HEATSINK, CODE, "Samsung 990 Pro 2TB z radiatorem", 3)));
        taxonomy.add(record(EAN_PLAIN, "Dyski SSD", 9, 60));
        taxonomy.add(record(EAN_HEATSINK, "Dyski SSD", 30, 90));

        // when
        List<ProductWeightOriginComplianceReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).extracting(ProductWeightOriginComplianceReportRow::ean, ProductWeightOriginComplianceReportRow::qty,
                        ProductWeightOriginComplianceReportRow::totalWeightNetG, ProductWeightOriginComplianceReportRow::totalWeightGrossG)
                .containsExactly(tuple(EAN_PLAIN, 2, 18L, 120L), tuple(EAN_HEATSINK, 3, 90L, 270L));
    }

    @Test
    void itemTakesTheRecordOfItsEan() {
        // given
        givenDocuments(goodsReceiptDoc("doc-1", "Germany"));
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", EAN_HEATSINK, CODE, "Samsung 990 Pro 2TB z radiatorem", 1)));
        taxonomy.add(new Taxonomy(EAN_PLAIN, CODE, "Samsung", "Samsung 990 Pro 2TB", "Dyski SSD", 1, 9, 60));
        taxonomy.add(new Taxonomy(EAN_HEATSINK, CODE, "Samsung Electronics", "Samsung 990 Pro 2TB z radiatorem",
                "Dyski SSD z radiatorem", 5, 30, 90));

        // when
        List<ProductWeightOriginComplianceReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.category()).isEqualTo("Dyski SSD z radiatorem");
            assertThat(row.brand()).isEqualTo("Samsung Electronics");
            assertThat(row.weightNetG()).isEqualTo(30);
            assertThat(row.weightGrossG()).isEqualTo(90);
        });
    }

    @Test
    void recordComesBeforeThePimEntry() {
        // given
        givenDocuments(goodsReceiptDoc("doc-1", "Germany"));
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", EAN_PLAIN, CODE, "Samsung 990 Pro 2TB", 1)));
        taxonomy.add(record(EAN_PLAIN, "Dyski SSD", 9, 60));
        when(pimView.findByGtin(EAN_PLAIN)).thenReturn(Optional.of(pimEntry("GPU", "ASUS", 2100, 2400)));

        // when
        List<ProductWeightOriginComplianceReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.category()).isEqualTo("Dyski SSD");
            assertThat(row.brand()).isEqualTo("Samsung");
            assertThat(row.weightNetG()).isEqualTo(9);
            assertThat(row.weightGrossG()).isEqualTo(60);
        });
    }

    @Test
    void itemWhoseRecordWaitsForCategoryIsUnknown() {
        // given
        givenDocuments(goodsReceiptDoc("doc-1", "Germany"));
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", EAN_PLAIN, CODE, "Samsung 990 Pro 2TB", 1)));
        taxonomy.add(record(EAN_PLAIN, null, 9, 60));
        when(pimView.findByGtin(EAN_PLAIN)).thenReturn(Optional.of(pimEntry("GPU", "ASUS", 2100, 2400)));

        // when
        List<ProductWeightOriginComplianceReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.category()).isEqualTo("Unknown");
            assertThat(row.brand()).isEqualTo("Samsung");
            assertThat(row.weightNetG()).isEqualTo(9);
        });
    }

    @Test
    void itemWithoutRecordTakesThePimEntry() {
        // given
        givenDocuments(goodsReceiptDoc("doc-1", "Germany"));
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", EAN_PLAIN, CODE, "Samsung 990 Pro 2TB", 1)));
        when(pimView.findByGtin(EAN_PLAIN)).thenReturn(Optional.of(pimEntry("GPU", "ASUS", 2100, 2400)));

        // when
        List<ProductWeightOriginComplianceReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.category()).isEqualTo("GPU");
            assertThat(row.brand()).isEqualTo("ASUS");
            assertThat(row.weightNetG()).isEqualTo(2100);
        });
    }

    @Test
    void skipsDocumentsThatAreNotGoodsReceipt() {
        WarehouseDocument issue = goodsReceiptDoc("doc-issue", "Germany");
        issue.setType(DocumentType.GoodsIssue);
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(issue));
        when(itemRepository.findByDocumentId("doc-issue"))
                .thenReturn(List.of(item("doc-issue", "5901111111111", "MFN-ISSUE", "Widget", 2)));
        when(pimView.findByGtin("5901111111111"))
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
        when(pimView.findByGtin("5902222222222"))
                .thenReturn(Optional.of(pimEntry("GPU", "ASUS", 500, 600)));

        assertThat(service.generate(STORE_ID, FROM, TO)).isEmpty();
    }

    @Test
    void sumsQuantitiesAndTotalsWhenSameEanFromSameCountryAppearsTwice() {
        WarehouseDocument doc1 = goodsReceiptDoc("doc-1", "Germany");
        WarehouseDocument doc2 = goodsReceiptDoc("doc-2", "Germany");
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(doc1, doc2));
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", "5900000000001", "MFN-A", "Name A", 3)));
        when(itemRepository.findByDocumentId("doc-2"))
                .thenReturn(List.of(item("doc-2", "5900000000001", "MFN-A", "Name A", 2)));
        when(pimView.findByGtin("5900000000001"))
                .thenReturn(Optional.of(pimEntry("GPU", "ASUS", 1000, 1200)));

        List<ProductWeightOriginComplianceReportRow> rows = service.generate(STORE_ID, FROM, TO);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).qty()).isEqualTo(5);
        assertThat(rows.get(0).totalWeightNetG()).isEqualTo(5L * 1000);
        assertThat(rows.get(0).totalWeightGrossG()).isEqualTo(5L * 1200);
        assertThat(rows.get(0).country()).isEqualTo("Germany");
    }

    @Test
    void emitsSeparateRowsWhenSameEanComesFromDifferentCountries() {
        WarehouseDocument doc1 = goodsReceiptDoc("doc-1", "Germany");
        WarehouseDocument doc2 = goodsReceiptDoc("doc-2", "Poland");
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(doc1, doc2));
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", "5900000000001", "MFN-A", "Name A", 3)));
        when(itemRepository.findByDocumentId("doc-2"))
                .thenReturn(List.of(item("doc-2", "5900000000001", "MFN-A", "Name A", 2)));
        when(pimView.findByGtin("5900000000001"))
                .thenReturn(Optional.of(pimEntry("GPU", "ASUS", 1000, 1200)));

        List<ProductWeightOriginComplianceReportRow> rows = service.generate(STORE_ID, FROM, TO);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(ProductWeightOriginComplianceReportRow::country)
                .containsExactlyInAnyOrder("Germany", "Poland");
        assertThat(rows).allSatisfy(r -> assertThat(r.ean()).isEqualTo("5900000000001"));
    }

    @Test
    void usesUnknownCategoryAndEmptyTotalsAndNoBrandWhenPimEntryMissing() {
        WarehouseDocument doc = goodsReceiptDoc("doc-pim-miss", "Germany");
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(doc));
        when(itemRepository.findByDocumentId("doc-pim-miss"))
                .thenReturn(List.of(item("doc-pim-miss", "5900000000002", "MFN-MISS", "Mystery", 4)));
        when(pimView.findByGtin("5900000000002")).thenReturn(Optional.empty());

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
    void itemTakesNoEntryByCodeAlone() {
        // given
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(goodsReceiptDoc("doc-1", "Germany")));
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", "5909999999999", "RTX4070-DUAL", "DUAL RTX 4070", 2)));
        ProductWeightOriginComplianceReportService report = new ProductWeightOriginComplianceReportService(
                documentRepository, itemRepository, taxonomy,
                ScopedPimCatalog.of(ScopedPimCatalog.globalEntry("pim-1", "5900000000001", "RTX4070-DUAL", "GPU")));

        // when
        List<ProductWeightOriginComplianceReportRow> rows = report.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.category()).isEqualTo("Unknown");
            assertThat(row.brand()).isNull();
            assertThat(row.weightNetG()).isNull();
            assertThat(row.weightGrossG()).isNull();
        });
    }

    @Test
    void leavesNetSideEmptyWhenPimEntryHasOnlyGrossWeight() {
        WarehouseDocument doc = goodsReceiptDoc("doc-half", "Germany");
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(doc));
        when(itemRepository.findByDocumentId("doc-half"))
                .thenReturn(List.of(item("doc-half", "5900000000003", "MFN-HALF", "Half", 7)));
        when(pimView.findByGtin("5900000000003"))
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
        when(pimView.findByGtin("5900000000004"))
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
        when(pimView.findByGtin("5900000000005"))
                .thenReturn(Optional.of(pimEntry("GPU", "ASUS", 500, 600)));

        List<ProductWeightOriginComplianceReportRow> rows = service.generate(STORE_ID, FROM, TO);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).country()).isEqualTo("Unknown");
    }

    @Test
    void sortsResultByCountryThenCategoryThenEan() {
        // given
        givenDocuments(goodsReceiptDoc("doc-1", "Germany"), goodsReceiptDoc("doc-2", "Austria"));
        when(itemRepository.findByDocumentId("doc-1")).thenReturn(List.of(
                item("doc-1", "ean-2", "M-A", "DE GPU A", 1),
                item("doc-1", "ean-1", "M-Z", "DE GPU Z", 1),
                item("doc-1", "ean-5", "M-PSU", "DE PSU", 1)));
        when(itemRepository.findByDocumentId("doc-2")).thenReturn(List.of(item("doc-2", "ean-3", "M-GPU", "AT GPU", 1)));
        when(pimView.findByGtin("ean-1")).thenReturn(Optional.of(pimEntry("GPU", "ASUS", 1, 1)));
        when(pimView.findByGtin("ean-2")).thenReturn(Optional.of(pimEntry("GPU", "ASUS", 1, 1)));
        when(pimView.findByGtin("ean-3")).thenReturn(Optional.of(pimEntry("GPU", "ASUS", 1, 1)));
        when(pimView.findByGtin("ean-5")).thenReturn(Optional.of(pimEntry("PSU", "Corsair", 1, 1)));

        // when
        List<ProductWeightOriginComplianceReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).extracting(
                        ProductWeightOriginComplianceReportRow::country,
                        ProductWeightOriginComplianceReportRow::category,
                        ProductWeightOriginComplianceReportRow::ean)
                .containsExactly(
                        tuple("Austria", "GPU", "ean-3"),
                        tuple("Germany", "GPU", "ean-1"),
                        tuple("Germany", "GPU", "ean-2"),
                        tuple("Germany", "PSU", "ean-5"));
    }

    private void givenDocuments(WarehouseDocument... docs) {
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(docs));
    }

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
        item.setItemId("item-" + ean);
        item.setEan(ean);
        item.setMfn(mfn);
        item.setName(name);
        item.setQty(qty);
        return item;
    }

    private static Taxonomy record(String ean, String category, Integer netG, Integer grossG) {
        return new Taxonomy(ean, CODE, "Samsung", "Samsung 990 Pro 2TB", category, 1, netG, grossG);
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
