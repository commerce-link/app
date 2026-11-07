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
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseReportServiceTest {

    private static final String STORE_ID = "store-1";
    private static final LocalDate FROM = LocalDate.of(2026, 5, 1);
    private static final LocalDate TO = LocalDate.of(2026, 5, 31);
    private static final String CODE = "MZ-V9P2T0BW";
    private static final String EAN_PLAIN = "8806094215038";
    private static final String EAN_HEATSINK = "8806094215045";

    @Mock private WarehouseDocumentRepository documentRepository;
    @Mock private WarehouseDocumentItemRepository itemRepository;
    @Mock private DeliveriesRepository deliveriesRepository;
    @Mock private PimCatalog pimCatalog;
    @Mock private PimView pimView;

    private TaxonomyCache taxonomy;
    private PurchaseReportService service;

    @BeforeEach
    void setUp() {
        when(pimCatalog.withStoreData(any())).thenReturn(pimView);
        when(pimCatalog.withGlobalData()).thenReturn(pimView);
        when(documentRepository.findAllInDateRange(anyString(), any(), any())).thenReturn(List.of());
        taxonomy = new TaxonomyCache(mock(TaxonomyRepository.class));
        service = new PurchaseReportService(documentRepository, itemRepository, deliveriesRepository, taxonomy, pimCatalog);
    }

    @Test
    void singleDocumentProducesOneRowWithSupplierFromDelivery() {
        // given
        WarehouseDocument doc = goodsReceiptDoc("doc-1", "delivery-1", "Acme sp. z o.o.");
        givenDocuments(doc);
        givenDelivery("delivery-1", "AcmeA");
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", "5901234567890", "RTX4070-DUAL", "DUAL RTX 4070", 5)));
        when(pimView.findByGtin("5901234567890"))
                .thenReturn(Optional.of(pimEntry("GPU")));

        // when
        List<PurchaseReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).containsExactly(
                new PurchaseReportRow("GPU", "AcmeA", "ASUS", "DUAL RTX 4070", "5901234567890", 5));
    }

    @Test
    void twoProductsUnderOneCodeGiveTwoRows() {
        // given
        givenDocuments(goodsReceiptDoc("doc-1", "delivery-1", "Acme"));
        givenDelivery("delivery-1", "AcmeA");
        when(itemRepository.findByDocumentId("doc-1")).thenReturn(List.of(
                item("doc-1", EAN_PLAIN, CODE, "Samsung 990 Pro 2TB", 2),
                item("doc-1", EAN_HEATSINK, CODE, "Samsung 990 Pro 2TB z radiatorem", 3)));
        taxonomy.add(record(EAN_PLAIN, "Dyski SSD"));
        taxonomy.add(record(EAN_HEATSINK, "Dyski SSD"));

        // when
        List<PurchaseReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).containsExactly(
                new PurchaseReportRow("Dyski SSD", "AcmeA", "Samsung", "Samsung 990 Pro 2TB", EAN_PLAIN, 2),
                new PurchaseReportRow("Dyski SSD", "AcmeA", "Samsung", "Samsung 990 Pro 2TB z radiatorem", EAN_HEATSINK, 3));
    }

    @Test
    void itemTakesTheRecordOfItsEan() {
        // given
        givenDocuments(goodsReceiptDoc("doc-1", "delivery-1", "Acme"));
        givenDelivery("delivery-1", "AcmeA");
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", EAN_HEATSINK, CODE, "Samsung 990 Pro 2TB z radiatorem", 1)));
        taxonomy.add(new Taxonomy(EAN_PLAIN, CODE, "Samsung", "Samsung 990 Pro 2TB", "Dyski SSD", 1, null, null));
        taxonomy.add(new Taxonomy(EAN_HEATSINK, CODE, "Samsung Electronics", "Samsung 990 Pro 2TB z radiatorem",
                "Dyski SSD z radiatorem", 5, null, null));

        // when
        List<PurchaseReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).extracting(PurchaseReportRow::category, PurchaseReportRow::brand)
                .containsExactly(tuple("Dyski SSD z radiatorem", "Samsung Electronics"));
    }

    @Test
    void recordComesBeforeThePimEntry() {
        // given
        givenDocuments(goodsReceiptDoc("doc-1", "delivery-1", "Acme"));
        givenDelivery("delivery-1", "AcmeA");
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", EAN_PLAIN, CODE, "Samsung 990 Pro 2TB", 1)));
        taxonomy.add(record(EAN_PLAIN, "Dyski SSD"));
        when(pimView.findByGtin(EAN_PLAIN)).thenReturn(Optional.of(pimEntry("GPU")));

        // when
        List<PurchaseReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).extracting(PurchaseReportRow::category, PurchaseReportRow::brand)
                .containsExactly(tuple("Dyski SSD", "Samsung"));
    }

    @Test
    void itemWhoseRecordWaitsForCategoryIsUnknown() {
        // given
        givenDocuments(goodsReceiptDoc("doc-1", "delivery-1", "Acme"));
        givenDelivery("delivery-1", "AcmeA");
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", EAN_PLAIN, CODE, "Samsung 990 Pro 2TB", 1)));
        taxonomy.add(record(EAN_PLAIN, null));
        when(pimView.findByGtin(EAN_PLAIN)).thenReturn(Optional.of(pimEntry("GPU")));

        // when
        List<PurchaseReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).extracting(PurchaseReportRow::category, PurchaseReportRow::brand)
                .containsExactly(tuple("Unknown", "Samsung"));
    }

    @Test
    void itemWithoutRecordTakesThePimEntry() {
        // given
        givenDocuments(goodsReceiptDoc("doc-1", "delivery-1", "Acme"));
        givenDelivery("delivery-1", "AcmeA");
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", EAN_PLAIN, CODE, "Samsung 990 Pro 2TB", 1)));
        when(pimView.findByGtin(EAN_PLAIN)).thenReturn(Optional.of(pimEntry("GPU")));

        // when
        List<PurchaseReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).extracting(PurchaseReportRow::category, PurchaseReportRow::brand)
                .containsExactly(tuple("GPU", "ASUS"));
    }

    @Test
    void skipsDocumentsThatAreNotGoodsReceipt() {
        // given
        WarehouseDocument issue = goodsReceiptDoc("doc-issue", "delivery-1", "Acme");
        issue.setType(DocumentType.GoodsIssue);
        givenDocuments(issue);
        givenDelivery("delivery-1", "AcmeA");
        when(itemRepository.findByDocumentId("doc-issue"))
                .thenReturn(List.of(item("doc-issue", "5901111111111", "MFN-ISSUE", "Widget", 2)));

        // when
        List<PurchaseReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).isEmpty();
    }

    @Test
    void skipsGoodsReceiptWithNonSupplierDeliveryReason() {
        // given
        WarehouseDocument customerReturn = goodsReceiptDoc("doc-rma", null, "Customer");
        customerReturn.setReason(DocumentReason.CustomerReturn);
        givenDocuments(customerReturn);
        when(itemRepository.findByDocumentId("doc-rma"))
                .thenReturn(List.of(item("doc-rma", "5902222222222", "MFN-RMA", "Returned item", 1)));

        // when
        List<PurchaseReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).isEmpty();
    }

    @Test
    void sumsQuantitiesWhenSameEanFromSameSupplierAppearsInTwoDocuments() {
        // given
        givenDocuments(goodsReceiptDoc("doc-1", "delivery-1", "Acme"), goodsReceiptDoc("doc-2", "delivery-2", "Acme"));
        givenDelivery("delivery-1", "AcmeA");
        givenDelivery("delivery-2", "AcmeA");
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", "5900000000001", "MFN-A", "Name A", 3)));
        when(itemRepository.findByDocumentId("doc-2"))
                .thenReturn(List.of(item("doc-2", "5900000000001", "MFN-A", "Name A", 2)));
        when(pimView.findByGtin("5900000000001")).thenReturn(Optional.of(pimEntry("GPU")));

        // when
        List<PurchaseReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).containsExactly(new PurchaseReportRow("GPU", "AcmeA", "ASUS", "Name A", "5900000000001", 5));
    }

    @Test
    void emitsSeparateRowsWhenSameEanComesFromDifferentSuppliers() {
        // given
        givenDocuments(goodsReceiptDoc("doc-1", "delivery-1", "Acme"), goodsReceiptDoc("doc-2", "delivery-2", "AcmeB"));
        givenDelivery("delivery-1", "AcmeA");
        givenDelivery("delivery-2", "AcmeB");
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", "5900000000001", "MFN-A", "Name A", 3)));
        when(itemRepository.findByDocumentId("doc-2"))
                .thenReturn(List.of(item("doc-2", "5900000000001", "MFN-A", "Name A", 2)));
        when(pimView.findByGtin("5900000000001")).thenReturn(Optional.of(pimEntry("GPU")));

        // when
        List<PurchaseReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).extracting(PurchaseReportRow::supplier, PurchaseReportRow::qty)
                .containsExactly(tuple("AcmeA", 3), tuple("AcmeB", 2));
    }

    @Test
    void usesUnknownCategoryAndNoBrandWhenPimEntryMissing() {
        // given
        givenDocuments(goodsReceiptDoc("doc-pim-miss", "delivery-1", "Acme"));
        givenDelivery("delivery-1", "AcmeA");
        when(itemRepository.findByDocumentId("doc-pim-miss"))
                .thenReturn(List.of(item("doc-pim-miss", "5900000000002", "MFN-MISS", "Mystery", 4)));
        when(pimView.findByGtin("5900000000002")).thenReturn(Optional.empty());

        // when
        List<PurchaseReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).containsExactly(new PurchaseReportRow("Unknown", "AcmeA", null, "Mystery", "5900000000002", 4));
    }

    @Test
    void itemTakesNoEntryByCodeAlone() {
        // given
        givenDocuments(goodsReceiptDoc("doc-1", "delivery-1", "Acme"));
        givenDelivery("delivery-1", "AcmeA");
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", "5909999999999", "RTX4070-DUAL", "DUAL RTX 4070", 2)));
        PurchaseReportService report = new PurchaseReportService(documentRepository, itemRepository, deliveriesRepository,
                taxonomy, ScopedPimCatalog.of(ScopedPimCatalog.globalEntry("pim-1", "5900000000001", "RTX4070-DUAL", "GPU")));

        // when
        List<PurchaseReportRow> rows = report.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).containsExactly(
                new PurchaseReportRow("Unknown", "AcmeA", null, "DUAL RTX 4070", "5909999999999", 2));
    }

    @Test
    void fallsBackToCounterpartyCompanyNameWhenDeliveryMissing() {
        // given
        givenDocuments(goodsReceiptDoc("doc-1", "delivery-gone", "Acme sp. z o.o."));
        when(deliveriesRepository.findById(STORE_ID, "delivery-gone")).thenReturn(null);
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", "5900000000003", "MFN-C", "C", 1)));
        when(pimView.findByGtin("5900000000003")).thenReturn(Optional.of(pimEntry("GPU")));

        // when
        List<PurchaseReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).extracting(PurchaseReportRow::supplier).containsExactly("Acme sp. z o.o.");
    }

    @Test
    void fallsBackToCounterpartyCompanyNameWhenDocumentHasNoDeliveryId() {
        // given
        givenDocuments(goodsReceiptDoc("doc-1", null, "Acme sp. z o.o."));
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", "5900000000003", "MFN-C", "C", 1)));
        when(pimView.findByGtin("5900000000003")).thenReturn(Optional.of(pimEntry("GPU")));

        // when
        List<PurchaseReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).extracting(PurchaseReportRow::supplier).containsExactly("Acme sp. z o.o.");
        verify(deliveriesRepository, times(0)).findById(anyString(), anyString());
    }

    @Test
    void supplierIsUnknownWhenNeitherDeliveryNorCounterpartyAvailable() {
        // given
        WarehouseDocument doc = goodsReceiptDoc("doc-1", null, null);
        doc.setCounterparty(null);
        givenDocuments(doc);
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", "5900000000004", "MFN-X", "X", 1)));
        when(pimView.findByGtin("5900000000004")).thenReturn(Optional.of(pimEntry("GPU")));

        // when
        List<PurchaseReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).extracting(PurchaseReportRow::supplier).containsExactly("Unknown");
    }

    @Test
    void loadsEachDeliveryOnlyOnce() {
        // given
        givenDocuments(goodsReceiptDoc("doc-1", "delivery-1", "Acme"), goodsReceiptDoc("doc-2", "delivery-1", "Acme"));
        givenDelivery("delivery-1", "AcmeA");
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", "5900000000001", "MFN-A", "Name A", 1)));
        when(itemRepository.findByDocumentId("doc-2"))
                .thenReturn(List.of(item("doc-2", "5900000000005", "MFN-B", "Name B", 1)));
        when(pimView.findByGtin(anyString())).thenReturn(Optional.of(pimEntry("GPU")));

        // when
        service.generate(STORE_ID, FROM, TO);

        // then
        verify(deliveriesRepository, times(1)).findById(STORE_ID, "delivery-1");
    }

    @Test
    void sortsResultByCategoryThenSupplierThenEan() {
        // given
        givenDocuments(goodsReceiptDoc("doc-1", "delivery-1", "Acme"), goodsReceiptDoc("doc-2", "delivery-2", "AcmeB"));
        givenDelivery("delivery-1", "Zeta");
        givenDelivery("delivery-2", "Alpha");
        when(itemRepository.findByDocumentId("doc-1")).thenReturn(List.of(
                item("doc-1", "ean-2", "M-A", "Z GPU A", 1),
                item("doc-1", "ean-1", "M-Z", "Z GPU Z", 1),
                item("doc-1", "ean-5", "M-PSU", "Z PSU", 1)));
        when(itemRepository.findByDocumentId("doc-2")).thenReturn(List.of(item("doc-2", "ean-3", "M-GPU", "A GPU", 1)));
        when(pimView.findByGtin("ean-1")).thenReturn(Optional.of(pimEntry("GPU")));
        when(pimView.findByGtin("ean-2")).thenReturn(Optional.of(pimEntry("GPU")));
        when(pimView.findByGtin("ean-3")).thenReturn(Optional.of(pimEntry("GPU")));
        when(pimView.findByGtin("ean-5")).thenReturn(Optional.of(pimEntry("PSU")));

        // when
        List<PurchaseReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).extracting(PurchaseReportRow::category, PurchaseReportRow::supplier, PurchaseReportRow::ean)
                .containsExactly(
                        tuple("GPU", "Alpha", "ean-3"),
                        tuple("GPU", "Zeta", "ean-1"),
                        tuple("GPU", "Zeta", "ean-2"),
                        tuple("PSU", "Zeta", "ean-5"));
    }

    private void givenDocuments(WarehouseDocument... docs) {
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(List.of(docs));
    }

    private void givenDelivery(String deliveryId, String provider) {
        Delivery delivery = new Delivery();
        delivery.setStoreId(STORE_ID);
        delivery.setDeliveryId(deliveryId);
        delivery.setProvider(provider);
        when(deliveriesRepository.findById(STORE_ID, deliveryId)).thenReturn(delivery);
    }

    private static WarehouseDocument goodsReceiptDoc(String documentId, String deliveryId, String companyName) {
        WarehouseDocument doc = new WarehouseDocument();
        doc.setStoreId(STORE_ID);
        doc.setDocumentId(documentId);
        doc.setDeliveryId(deliveryId);
        doc.setType(DocumentType.GoodsReceipt);
        doc.setReason(DocumentReason.SupplierDelivery);
        doc.setCreatedAt(LocalDateTime.of(2026, 5, 10, 12, 0));
        CounterpartyDetails counterparty = new CounterpartyDetails();
        counterparty.setCompanyName(companyName);
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

    private static Taxonomy record(String ean, String category) {
        return new Taxonomy(ean, CODE, "Samsung", "Samsung 990 Pro 2TB", category, 1, null, null);
    }

    private static PimEntry pimEntry(String category) {
        return new PimEntry("pim-" + category, List.<PimIdentifier>of(), "ASUS", "Name", category, null, true, null, null);
    }
}
