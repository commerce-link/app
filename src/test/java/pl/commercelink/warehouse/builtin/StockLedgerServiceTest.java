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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockLedgerServiceTest {

    private static final String STORE_ID = "store-1";
    private static final LocalDate FROM = LocalDate.of(2026, 5, 1);
    private static final LocalDate TO = LocalDate.of(2026, 5, 31);
    private static final LocalDateTime IN_PERIOD = LocalDateTime.of(2026, 5, 10, 12, 0);
    private static final LocalDateTime BEFORE = LocalDateTime.of(2026, 4, 15, 12, 0);

    @Mock private WarehouseDocumentRepository documentRepository;
    @Mock private WarehouseDocumentItemRepository itemRepository;

    private StockLedgerService service;

    @BeforeEach
    void setUp() {
        service = new StockLedgerService(documentRepository, itemRepository);
        when(documentRepository.findAllBeforeDate(anyString(), any())).thenReturn(new ArrayList<>());
        when(documentRepository.findAllInDateRange(anyString(), any(), any())).thenReturn(new ArrayList<>());
    }

    @Test
    void receiptIsCategorizedAsPurchase() {
        WarehouseDocument pz = doc("pz", DocumentType.GoodsReceipt, DocumentReason.SupplierDelivery, IN_PERIOD);
        inPeriod(pz);
        items("pz", item("pz", "MFN-A", "Widget", 10, 100.0));

        StockLedgerRow row = single();
        assertThat(row.qtyByCategory().getOrDefault(LedgerCategory.PURCHASE, 0)).isEqualTo(10);
        assertThat(qty(row, "Zakup ilość")).isEqualTo(10);
        assertThat(money(row, "Zakup wartość")).isEqualTo(1000.0);
        assertThat(qty(row, "Przychód RAZEM ilość")).isEqualTo(10);
        assertThat(qty(row, "Rozchód RAZEM ilość")).isEqualTo(0);
        assertThat(qty(row, "BZ ilość")).isEqualTo(10);
        assertThat(money(row, "BZ wartość")).isEqualTo(1000.0);
    }

    @Test
    void issueReasonsLandInTheirOwnColumns() {
        historical(doc("bo", DocumentType.GoodsReceipt, DocumentReason.SupplierDelivery, BEFORE));
        items("bo", item("bo", "MFN-A", "Widget", 10, 50.0));

        WarehouseDocument sale = doc("wz", DocumentType.GoodsIssue, DocumentReason.CustomerOrder, IN_PERIOD);
        WarehouseDocument destruction = doc("rw1", DocumentType.InternalIssue, DocumentReason.Destruction, IN_PERIOD);
        WarehouseDocument theft = doc("rw2", DocumentType.InternalIssue, DocumentReason.Theft, IN_PERIOD);
        inPeriod(sale, destruction, theft);
        items("wz", item("wz", "MFN-A", "Widget", 4, 50.0));
        items("rw1", item("rw1", "MFN-A", "Widget", 2, 50.0));
        items("rw2", item("rw2", "MFN-A", "Widget", 1, 50.0));

        StockLedgerRow row = single();
        assertThat(qty(row, "Sprzedaż ilość")).isEqualTo(4);
        assertThat(qty(row, "Zniszczenie ilość")).isEqualTo(2);
        assertThat(qty(row, "Kradzież ilość")).isEqualTo(1);
        assertThat(money(row, "Zniszczenie wartość")).isEqualTo(100.0);
        assertThat(qty(row, "Rozchód RAZEM ilość")).isEqualTo(7);
        assertThat(qty(row, "BZ ilość")).isEqualTo(3);
        assertThat(money(row, "BZ wartość")).isEqualTo(150.0);
    }

    @Test
    void stockAdjustmentSplitsIntoSurplusAndShortageBySide() {
        WarehouseDocument surplus = doc("pw", DocumentType.InternalReceipt, DocumentReason.StockAdjustment, IN_PERIOD);
        WarehouseDocument shortage = doc("rw", DocumentType.InternalIssue, DocumentReason.StockAdjustment, IN_PERIOD);
        inPeriod(surplus, shortage);
        items("pw", item("pw", "MFN-A", "Widget", 5, 10.0));
        items("rw", item("rw", "MFN-A", "Widget", 2, 10.0));

        StockLedgerRow row = single();
        assertThat(qty(row, "Nadwyżka ilość")).isEqualTo(5);
        assertThat(money(row, "Nadwyżka wartość")).isEqualTo(50.0);
        assertThat(qty(row, "Niedobór ilość")).isEqualTo(2);
        assertThat(money(row, "Niedobór wartość")).isEqualTo(20.0);
        assertThat(qty(row, "Przychód RAZEM ilość")).isEqualTo(5);
        assertThat(qty(row, "Rozchód RAZEM ilość")).isEqualTo(2);
        assertThat(qty(row, "BZ ilość")).isEqualTo(3);
    }

    @Test
    void nullReasonFallsBackByDocumentType() {
        WarehouseDocument pz = doc("pz", DocumentType.GoodsReceipt, null, IN_PERIOD);
        WarehouseDocument pw = doc("pw", DocumentType.InternalReceipt, null, IN_PERIOD);
        WarehouseDocument wz = doc("wz", DocumentType.GoodsIssue, null, IN_PERIOD);
        WarehouseDocument rw = doc("rw", DocumentType.InternalIssue, null, IN_PERIOD);
        inPeriod(pz, pw, wz, rw);
        items("pz", item("pz", "MFN-A", "Widget", 3, 100.0));
        items("pw", item("pw", "MFN-A", "Widget", 1, 100.0));
        items("wz", item("wz", "MFN-A", "Widget", 2, 100.0));
        items("rw", item("rw", "MFN-A", "Widget", 1, 100.0));

        StockLedgerRow row = single();
        assertThat(qty(row, "Zakup ilość")).isEqualTo(3);
        assertThat(qty(row, "Nadwyżka ilość")).isEqualTo(1);
        assertThat(qty(row, "Sprzedaż ilość")).isEqualTo(2);
        assertThat(qty(row, "Niedobór ilość")).isEqualTo(1);
    }

    @Test
    void openingBalanceIsNetOfHistoricalMovements() {
        WarehouseDocument receipt = doc("h-pz", DocumentType.GoodsReceipt, DocumentReason.SupplierDelivery, BEFORE);
        WarehouseDocument issue = doc("h-wz", DocumentType.GoodsIssue, DocumentReason.CustomerOrder, BEFORE);
        historical(receipt, issue);
        items("h-pz", item("h-pz", "MFN-A", "Widget", 10, 100.0));
        items("h-wz", item("h-wz", "MFN-A", "Widget", 4, 100.0));

        StockLedgerRow row = single();
        assertThat(qty(row, "BO ilość")).isEqualTo(6);
        assertThat(money(row, "BO wartość")).isEqualTo(600.0);
        assertThat(qty(row, "Przychód RAZEM ilość")).isEqualTo(0);
        assertThat(qty(row, "Rozchód RAZEM ilość")).isEqualTo(0);
        assertThat(qty(row, "BZ ilość")).isEqualTo(6);
        assertThat(money(row, "BZ wartość")).isEqualTo(600.0);
    }

    @Test
    void closingBalanceEqualsOpeningPlusReceiptsMinusIssues() {
        historical(doc("bo", DocumentType.GoodsReceipt, DocumentReason.SupplierDelivery, BEFORE));
        items("bo", item("bo", "MFN-A", "Widget", 20, 10.0));

        WarehouseDocument purchase = doc("pz", DocumentType.GoodsReceipt, DocumentReason.SupplierDelivery, IN_PERIOD);
        WarehouseDocument customerReturn = doc("pz-ret", DocumentType.GoodsReceipt, DocumentReason.CustomerReturn, IN_PERIOD);
        WarehouseDocument surplus = doc("pw", DocumentType.InternalReceipt, DocumentReason.StockAdjustment, IN_PERIOD);
        WarehouseDocument sale = doc("wz", DocumentType.GoodsIssue, DocumentReason.CustomerOrder, IN_PERIOD);
        WarehouseDocument destruction = doc("rw1", DocumentType.InternalIssue, DocumentReason.Destruction, IN_PERIOD);
        WarehouseDocument theft = doc("rw2", DocumentType.InternalIssue, DocumentReason.Theft, IN_PERIOD);
        inPeriod(purchase, customerReturn, surplus, sale, destruction, theft);
        items("pz", item("pz", "MFN-A", "Widget", 5, 10.0));
        items("pz-ret", item("pz-ret", "MFN-A", "Widget", 3, 10.0));
        items("pw", item("pw", "MFN-A", "Widget", 2, 10.0));
        items("wz", item("wz", "MFN-A", "Widget", 6, 10.0));
        items("rw1", item("rw1", "MFN-A", "Widget", 1, 10.0));
        items("rw2", item("rw2", "MFN-A", "Widget", 1, 10.0));

        StockLedgerRow row = single();

        int receiptTotal = qty(row, "Zakup ilość") + qty(row, "Zwrot klienta ilość")
                + qty(row, "Powrót z serwisu ilość") + qty(row, "Nadwyżka ilość")
                + qty(row, "Przesunięcie przych. ilość");
        int issueTotal = qty(row, "Sprzedaż ilość") + qty(row, "Wysłanie do serwisu ilość")
                + qty(row, "Zniszczenie ilość") + qty(row, "Kradzież ilość")
                + qty(row, "Zużycie wewn. ilość") + qty(row, "Niedobór ilość")
                + qty(row, "Przesunięcie wych. ilość");

        assertThat(qty(row, "Przychód RAZEM ilość")).isEqualTo(receiptTotal).isEqualTo(10);
        assertThat(qty(row, "Rozchód RAZEM ilość")).isEqualTo(issueTotal).isEqualTo(8);
        assertThat(qty(row, "BZ ilość"))
                .isEqualTo(qty(row, "BO ilość") + receiptTotal - issueTotal)
                .isEqualTo(22);
        assertThat(money(row, "BZ wartość"))
                .isEqualTo(money(row, "BO wartość") + money(row, "Przychód RAZEM wartość") - money(row, "Rozchód RAZEM wartość"))
                .isEqualTo(220.0);
    }

    @Test
    void skipsRowWhoseOpeningNetsToZeroWithNoMovements() {
        WarehouseDocument receipt = doc("h-pz", DocumentType.GoodsReceipt, DocumentReason.SupplierDelivery, BEFORE);
        WarehouseDocument issue = doc("h-wz", DocumentType.GoodsIssue, DocumentReason.CustomerOrder, BEFORE);
        historical(receipt, issue);
        items("h-pz", item("h-pz", "MFN-A", "Widget", 5, 100.0));
        items("h-wz", item("h-wz", "MFN-A", "Widget", 5, 100.0));

        assertThat(service.generate(STORE_ID, FROM, TO)).isEmpty();
    }

    @Test
    void skipsNonStockMovementDocuments() {
        WarehouseDocument transfer = doc("mm", DocumentType.StockTransfer, null, IN_PERIOD);
        WarehouseDocument proforma = doc("pf", DocumentType.Proforma, null, IN_PERIOD);
        inPeriod(transfer, proforma);

        assertThat(service.generate(STORE_ID, FROM, TO)).isEmpty();
    }

    @Test
    void skipsItemsWithBlankOrNullMfn() {
        WarehouseDocument pz = doc("pz", DocumentType.GoodsReceipt, DocumentReason.SupplierDelivery, IN_PERIOD);
        inPeriod(pz);
        items("pz",
                item("pz", "", "Blank", 3, 100.0),
                item("pz", null, "Null", 2, 100.0));

        assertThat(service.generate(STORE_ID, FROM, TO)).isEmpty();
    }

    @Test
    void sortsRowsByMfn() {
        WarehouseDocument b = doc("pz-b", DocumentType.GoodsReceipt, DocumentReason.SupplierDelivery, IN_PERIOD);
        WarehouseDocument a = doc("pz-a", DocumentType.GoodsReceipt, DocumentReason.SupplierDelivery, IN_PERIOD);
        inPeriod(b, a);
        items("pz-b", item("pz-b", "MFN-B", "B", 1, 10.0));
        items("pz-a", item("pz-a", "MFN-A", "A", 1, 10.0));

        List<StockLedgerRow> rows = service.generate(STORE_ID, FROM, TO);
        assertThat(rows).extracting(StockLedgerRow::mfn).containsExactly("MFN-A", "MFN-B");
    }

    @Test
    void headerAndDataColumnsAreAligned() {
        WarehouseDocument pz = doc("pz", DocumentType.GoodsReceipt, DocumentReason.SupplierDelivery, IN_PERIOD);
        inPeriod(pz);
        items("pz", item("pz", "MFN-A", "Widget", 1, 10.0));

        StockLedgerRow row = single();
        assertThat(StockLedgerRow.headers()).hasSize(36);
        assertThat(row.asStringArray()).hasSameSizeAs(StockLedgerRow.headers());
        assertThat(row.asStringArray()[row.asStringArray().length - 1]).isEqualTo("PLN");
    }

    // --- helpers ---

    private StockLedgerRow single() {
        List<StockLedgerRow> rows = service.generate(STORE_ID, FROM, TO);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private void inPeriod(WarehouseDocument... docs) {
        when(documentRepository.findAllInDateRange(STORE_ID, FROM.atStartOfDay(), TO.atTime(LocalTime.MAX)))
                .thenReturn(new ArrayList<>(List.of(docs)));
    }

    private void historical(WarehouseDocument... docs) {
        when(documentRepository.findAllBeforeDate(STORE_ID, FROM.atStartOfDay()))
                .thenReturn(new ArrayList<>(List.of(docs)));
    }

    private void items(String documentId, WarehouseDocumentItem... items) {
        when(itemRepository.findByDocumentId(documentId)).thenReturn(List.of(items));
    }

    private static int qty(StockLedgerRow row, String header) {
        return Integer.parseInt(cell(row, header));
    }

    private static double money(StockLedgerRow row, String header) {
        return Double.parseDouble(cell(row, header).replace(',', '.'));
    }

    private static String cell(StockLedgerRow row, String header) {
        List<String> headers = Arrays.asList(StockLedgerRow.headers());
        int index = headers.indexOf(header);
        assertThat(index).as("header '%s' must exist", header).isGreaterThanOrEqualTo(0);
        return row.asStringArray()[index];
    }

    private static WarehouseDocument doc(String documentId, DocumentType type, DocumentReason reason, LocalDateTime createdAt) {
        WarehouseDocument document = new WarehouseDocument();
        document.setStoreId(STORE_ID);
        document.setDocumentId(documentId);
        document.setType(type);
        document.setReason(reason);
        document.setCreatedAt(createdAt);
        return document;
    }

    private static WarehouseDocumentItem item(String documentId, String mfn, String name, int qty, double unitPrice) {
        WarehouseDocumentItem item = new WarehouseDocumentItem();
        item.setDocumentId(documentId);
        item.setItemId("item-" + documentId + "-" + mfn);
        item.setMfn(mfn);
        item.setName(name);
        item.setQty(qty);
        item.setUnitPrice(unitPrice);
        return item;
    }
}
