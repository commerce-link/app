package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseReportServiceTest {

    private static final String STORE_ID = "store-1";
    private static final LocalDate FROM = LocalDate.of(2026, 5, 1);
    private static final LocalDate TO = LocalDate.of(2026, 5, 31);

    @Mock private WarehouseDocumentRepository documentRepository;
    @Mock private WarehouseDocumentItemRepository itemRepository;
    @Mock private DeliveriesRepository deliveriesRepository;
    @Mock private PimCatalog pimCatalog;

    @InjectMocks private PurchaseReportService service;

    @BeforeEach
    void setUp() {
        when(documentRepository.findAllInDateRange(anyString(), any(), any())).thenReturn(List.of());
    }

    @Test
    void singleDocumentProducesOneRowWithSupplierFromDelivery() {
        // given
        WarehouseDocument doc = goodsReceiptDoc("doc-1", "delivery-1", "Acme sp. z o.o.");
        givenDocuments(doc);
        givenDelivery("delivery-1", "AcmeA");
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", "5901234567890", "RTX4070-DUAL", "DUAL RTX 4070", 5)));
        when(pimCatalog.findByGtinOrMpn("5901234567890", "RTX4070-DUAL"))
                .thenReturn(Optional.of(pimEntry("GPU")));

        // when
        List<PurchaseReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).containsExactly(new PurchaseReportRow("GPU", "AcmeA", "ASUS", "DUAL RTX 4070", "RTX4070-DUAL", 5));
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
    void skipsItemsWithBlankMfn() {
        // given
        WarehouseDocument doc = goodsReceiptDoc("doc-blank", "delivery-1", "Acme");
        givenDocuments(doc);
        givenDelivery("delivery-1", "AcmeA");
        when(itemRepository.findByDocumentId("doc-blank"))
                .thenReturn(List.of(item("doc-blank", "5901111111111", "", "Something", 3)));

        // when
        List<PurchaseReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).isEmpty();
    }

    @Test
    void sumsQuantitiesWhenSameMfnFromSameSupplierAppearsInTwoDocuments() {
        // given
        givenDocuments(goodsReceiptDoc("doc-1", "delivery-1", "Acme"), goodsReceiptDoc("doc-2", "delivery-2", "Acme"));
        givenDelivery("delivery-1", "AcmeA");
        givenDelivery("delivery-2", "AcmeA");
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", "5900000000001", "MFN-A", "Name A", 3)));
        when(itemRepository.findByDocumentId("doc-2"))
                .thenReturn(List.of(item("doc-2", "5900000000001", "MFN-A", "Name A", 2)));
        when(pimCatalog.findByGtinOrMpn("5900000000001", "MFN-A")).thenReturn(Optional.of(pimEntry("GPU")));

        // when
        List<PurchaseReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).containsExactly(new PurchaseReportRow("GPU", "AcmeA", "ASUS", "Name A", "MFN-A", 5));
    }

    @Test
    void emitsSeparateRowsWhenSameMfnComesFromDifferentSuppliers() {
        // given
        givenDocuments(goodsReceiptDoc("doc-1", "delivery-1", "Acme"), goodsReceiptDoc("doc-2", "delivery-2", "AcmeB"));
        givenDelivery("delivery-1", "AcmeA");
        givenDelivery("delivery-2", "AcmeB");
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", "5900000000001", "MFN-A", "Name A", 3)));
        when(itemRepository.findByDocumentId("doc-2"))
                .thenReturn(List.of(item("doc-2", "5900000000001", "MFN-A", "Name A", 2)));
        when(pimCatalog.findByGtinOrMpn("5900000000001", "MFN-A")).thenReturn(Optional.of(pimEntry("GPU")));

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
        when(pimCatalog.findByGtinOrMpn("5900000000002", "MFN-MISS")).thenReturn(Optional.empty());

        // when
        List<PurchaseReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).containsExactly(new PurchaseReportRow("Unknown", "AcmeA", null, "Mystery", "MFN-MISS", 4));
    }

    @Test
    void fallsBackToCounterpartyCompanyNameWhenDeliveryMissing() {
        // given
        givenDocuments(goodsReceiptDoc("doc-1", "delivery-gone", "Acme sp. z o.o."));
        when(deliveriesRepository.findById(STORE_ID, "delivery-gone")).thenReturn(null);
        when(itemRepository.findByDocumentId("doc-1"))
                .thenReturn(List.of(item("doc-1", "5900000000003", "MFN-C", "C", 1)));
        when(pimCatalog.findByGtinOrMpn("5900000000003", "MFN-C")).thenReturn(Optional.of(pimEntry("GPU")));

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
        when(pimCatalog.findByGtinOrMpn("5900000000003", "MFN-C")).thenReturn(Optional.of(pimEntry("GPU")));

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
        when(pimCatalog.findByGtinOrMpn("5900000000004", "MFN-X")).thenReturn(Optional.of(pimEntry("GPU")));

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
        when(pimCatalog.findByGtinOrMpn(anyString(), anyString())).thenReturn(Optional.of(pimEntry("GPU")));

        // when
        service.generate(STORE_ID, FROM, TO);

        // then
        verify(deliveriesRepository, times(1)).findById(STORE_ID, "delivery-1");
    }

    @Test
    void sortsResultByCategoryThenSupplierThenMfn() {
        // given
        givenDocuments(
                goodsReceiptDoc("doc-1", "delivery-1", "Acme"),
                goodsReceiptDoc("doc-2", "delivery-1", "Acme"),
                goodsReceiptDoc("doc-3", "delivery-2", "AcmeB"),
                goodsReceiptDoc("doc-4", "delivery-2", "AcmeB"));
        givenDelivery("delivery-1", "Zeta");
        givenDelivery("delivery-2", "Alpha");
        when(itemRepository.findByDocumentId("doc-1")).thenReturn(List.of(item("doc-1", "ean-1", "M-Z-GPU", "Z GPU", 1)));
        when(itemRepository.findByDocumentId("doc-2")).thenReturn(List.of(item("doc-2", "ean-2", "M-Z-PSU", "Z PSU", 1)));
        when(itemRepository.findByDocumentId("doc-3")).thenReturn(List.of(item("doc-3", "ean-3", "M-A-PSU", "A PSU", 1)));
        when(itemRepository.findByDocumentId("doc-4")).thenReturn(List.of(item("doc-4", "ean-4", "M-A-GPU", "A GPU", 1)));
        when(pimCatalog.findByGtinOrMpn("ean-1", "M-Z-GPU")).thenReturn(Optional.of(pimEntry("GPU")));
        when(pimCatalog.findByGtinOrMpn("ean-2", "M-Z-PSU")).thenReturn(Optional.of(pimEntry("PSU")));
        when(pimCatalog.findByGtinOrMpn("ean-3", "M-A-PSU")).thenReturn(Optional.of(pimEntry("PSU")));
        when(pimCatalog.findByGtinOrMpn("ean-4", "M-A-GPU")).thenReturn(Optional.of(pimEntry("GPU")));

        // when
        List<PurchaseReportRow> rows = service.generate(STORE_ID, FROM, TO);

        // then
        assertThat(rows).extracting(PurchaseReportRow::category, PurchaseReportRow::supplier, PurchaseReportRow::mfn)
                .containsExactly(
                        tuple("GPU", "Alpha", "M-A-GPU"),
                        tuple("GPU", "Zeta", "M-Z-GPU"),
                        tuple("PSU", "Alpha", "M-A-PSU"),
                        tuple("PSU", "Zeta", "M-Z-PSU"));
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
        item.setItemId("item-" + mfn);
        item.setEan(ean);
        item.setMfn(mfn);
        item.setName(name);
        item.setQty(qty);
        return item;
    }

    private static PimEntry pimEntry(String category) {
        return new PimEntry("pim-" + category, List.<PimIdentifier>of(), "ASUS", "Name", category, null, true, null, null);
    }
}
