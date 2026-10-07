package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult.Blocked;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult.Closed;
import pl.commercelink.warehouse.builtin.StockLedgerClosingResult.NotAllowed;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockLedgerMonthClosingTest {

    private static final String STORE_ID = "store-1";
    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final YearMonth AUGUST = YearMonth.of(2026, 8);
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final Clock OCTOBER_7 = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private StockLedgerService stockLedgerService;
    @Mock
    private StockLedgerClosings closings;
    @Mock
    private WarehouseDocumentRepository documents;
    @Mock
    private DeliveriesRepository deliveries;

    private StockLedgerMonthClosing closing;

    @BeforeEach
    void setUp() {
        closing = new StockLedgerMonthClosing(stockLedgerService, closings, documents, deliveries, OCTOBER_7);
    }

    @Test
    void firstMonthToCloseIsThePreviousOne() {
        // given
        when(closings.closedMonths(STORE_ID)).thenReturn(List.of());

        // when
        StockLedgerClosingStatus status = closing.status(STORE_ID);

        // then
        assertThat(status.nextToClose()).isEqualTo(SEPTEMBER);
        assertThat(status.nextClosable()).isTrue();
    }

    @Test
    void monthAfterTheLastClosedIsNextToClose() {
        // given
        when(closings.closedMonths(STORE_ID)).thenReturn(List.of(JULY, AUGUST));

        // when
        StockLedgerClosingStatus status = closing.status(STORE_ID);

        // then
        assertThat(status.nextToClose()).isEqualTo(SEPTEMBER);
    }

    @Test
    void runningMonthCannotBeClosedUntilItEnds() throws Exception {
        // given
        when(closings.closedMonths(STORE_ID)).thenReturn(List.of(SEPTEMBER));

        // when
        StockLedgerClosingResult result = closing.close(STORE_ID, OCTOBER);

        // then
        assertThat(closing.status(STORE_ID).closableFrom()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(result).isEqualTo(new NotAllowed("reports.stockLedger.closing.error.notOver"));
        verify(closings, never()).save(any(), any(), any());
    }

    @Test
    void onlyTheMonthAfterTheLastClosedCanBeClosed() throws Exception {
        // given
        when(closings.closedMonths(STORE_ID)).thenReturn(List.of(AUGUST));

        // when
        StockLedgerClosingResult result = closing.close(STORE_ID, JULY);

        // then
        assertThat(result).isEqualTo(new NotAllowed("reports.stockLedger.closing.error.notNext"));
        verifyNoInteractions(documents, deliveries, stockLedgerService);
    }

    @Test
    void deliveriesWithoutAnInvoiceOrItsSyncHoldTheClosing() throws Exception {
        // given
        when(closings.closedMonths(STORE_ID)).thenReturn(List.of(AUGUST));
        when(documents.findAllInDateRange(STORE_ID, SEPTEMBER.atDay(1).atStartOfDay(), SEPTEMBER.atEndOfMonth().atTime(LocalTime.MAX)))
                .thenReturn(documents(receipt("no-invoice"), receipt("unsynced"), receipt("settled")));
        Delivery noInvoice = delivery("no-invoice", "Manual-Hurt", false, false);
        Delivery unsynced = delivery("unsynced", "Manual-Hurt", true, false);
        Delivery settled = delivery("settled", "Manual-Hurt", true, true);
        when(deliveries.findAllByIds(STORE_ID, Set.of("no-invoice", "unsynced", "settled")))
                .thenReturn(List.of(noInvoice, unsynced, settled));

        // when
        StockLedgerClosingResult result = closing.close(STORE_ID, SEPTEMBER);

        // then
        assertThat(result).isInstanceOf(Blocked.class);
        assertThat(((Blocked) result).deliveries()).containsExactlyInAnyOrder(noInvoice, unsynced);
        verify(closings, never()).save(any(), any(), any());
    }

    @Test
    void ownWarehouseReceiptsAndOtherDocumentsDoNotHoldTheClosing() throws Exception {
        // given
        when(closings.closedMonths(STORE_ID)).thenReturn(List.of(AUGUST));
        WarehouseDocument returnFromCustomer = receipt(null);
        WarehouseDocument issue = document("sold", DocumentType.GoodsIssue, SEPTEMBER.atDay(10).atTime(9, 0));
        when(documents.findAllInDateRange(STORE_ID, SEPTEMBER.atDay(1).atStartOfDay(), SEPTEMBER.atEndOfMonth().atTime(LocalTime.MAX)))
                .thenReturn(documents(receipt("own"), returnFromCustomer, issue));
        when(deliveries.findAllByIds(STORE_ID, Set.of("own")))
                .thenReturn(List.of(delivery("own", SupplierRegistry.WAREHOUSE, false, false)));

        // when
        StockLedgerClosingResult result = closing.close(STORE_ID, SEPTEMBER);

        // then
        assertThat(result).isEqualTo(new Closed(List.of(SEPTEMBER)));
    }

    @Test
    void firstClosingChecksTheDeliveriesOfTheWholeHistory() throws Exception {
        // given
        when(closings.closedMonths(STORE_ID)).thenReturn(List.of());
        when(documents.findAllBeforeDate(STORE_ID, OCTOBER.atDay(1).atStartOfDay())).thenReturn(documents(receipt("old")));
        when(deliveries.findAllByIds(STORE_ID, Set.of("old"))).thenReturn(List.of(delivery("old", "Manual-Hurt", true, false)));

        // when
        StockLedgerClosingResult result = closing.close(STORE_ID, SEPTEMBER);

        // then
        assertThat(result).isInstanceOf(Blocked.class);
        verify(documents, never()).findAllInDateRange(any(), any(), any());
    }

    @Test
    void closingStoresTheReportOfTheMonth() throws Exception {
        // given
        when(closings.closedMonths(STORE_ID)).thenReturn(List.of(AUGUST));
        List<StockLedgerRow> rows = List.of(new StockLedgerRow("MFN-A", "Widget", 1, 10.0, Map.of(), Map.of()));
        when(stockLedgerService.generate(STORE_ID, SEPTEMBER.atDay(1), SEPTEMBER.atEndOfMonth())).thenReturn(rows);

        // when
        StockLedgerClosingResult result = closing.close(STORE_ID, SEPTEMBER);

        // then
        assertThat(result).isEqualTo(new Closed(List.of(SEPTEMBER)));
        verify(closings).save(eq(STORE_ID), eq(SEPTEMBER), aryEq(StockLedgerRow.toCsv(rows)));
    }

    @Test
    void regeneratingAMonthGeneratesItAndTheClosedMonthsAfterItAgain() throws Exception {
        // given
        when(closings.closedMonths(STORE_ID)).thenReturn(List.of(JULY, AUGUST, SEPTEMBER));
        List<StockLedgerRow> august = List.of(new StockLedgerRow("MFN-A", "Widget", 1, 10.0, Map.of(), Map.of()));
        List<StockLedgerRow> september = List.of(new StockLedgerRow("MFN-A", "Widget", 2, 20.0, Map.of(), Map.of()));
        when(stockLedgerService.generate(STORE_ID, AUGUST.atDay(1), AUGUST.atEndOfMonth())).thenReturn(august);
        when(stockLedgerService.generate(STORE_ID, SEPTEMBER.atDay(1), SEPTEMBER.atEndOfMonth())).thenReturn(september);

        // when
        StockLedgerClosingResult result = closing.regenerate(STORE_ID, AUGUST);

        // then
        assertThat(result).isEqualTo(new Closed(List.of(AUGUST, SEPTEMBER)));
        InOrder inOrder = inOrder(closings);
        inOrder.verify(closings).save(eq(STORE_ID), eq(AUGUST), aryEq(StockLedgerRow.toCsv(august)));
        inOrder.verify(closings).save(eq(STORE_ID), eq(SEPTEMBER), aryEq(StockLedgerRow.toCsv(september)));
        verify(closings, never()).save(eq(STORE_ID), eq(JULY), any());
        verify(documents).findAllInDateRange(STORE_ID, AUGUST.atDay(1).atStartOfDay(), SEPTEMBER.atEndOfMonth().atTime(LocalTime.MAX));
    }

    @Test
    void regeneratingWaitsForTheInvoicesOfTheMonthsItCovers() throws Exception {
        // given
        when(closings.closedMonths(STORE_ID)).thenReturn(List.of(AUGUST, SEPTEMBER));
        when(documents.findAllBeforeDate(STORE_ID, OCTOBER.atDay(1).atStartOfDay())).thenReturn(documents(receipt("unlinked")));
        Delivery unlinked = delivery("unlinked", "Manual-Hurt", false, false);
        when(deliveries.findAllByIds(STORE_ID, Set.of("unlinked"))).thenReturn(List.of(unlinked));

        // when
        StockLedgerClosingResult result = closing.regenerate(STORE_ID, AUGUST);

        // then
        assertThat(result).isEqualTo(new Blocked(List.of(unlinked)));
        verify(closings, never()).save(any(), any(), any());
    }

    @Test
    void onlyAClosedMonthCanBeGeneratedAgain() throws Exception {
        // given
        when(closings.closedMonths(STORE_ID)).thenReturn(List.of(AUGUST));

        // when
        StockLedgerClosingResult result = closing.regenerate(STORE_ID, SEPTEMBER);

        // then
        assertThat(result).isEqualTo(new NotAllowed("reports.stockLedger.closing.error.notClosed"));
        verifyNoInteractions(documents, deliveries, stockLedgerService);
    }

    private static List<WarehouseDocument> documents(WarehouseDocument... documents) {
        return new ArrayList<>(List.of(documents));
    }

    private static WarehouseDocument receipt(String deliveryId) {
        WarehouseDocument document = document("pz-" + deliveryId, DocumentType.GoodsReceipt, SEPTEMBER.atDay(15).atTime(12, 0));
        document.setDeliveryId(deliveryId);
        return document;
    }

    private static WarehouseDocument document(String documentId, DocumentType type, LocalDateTime createdAt) {
        WarehouseDocument document = new WarehouseDocument();
        document.setStoreId(STORE_ID);
        document.setDocumentId(documentId);
        document.setType(type);
        document.setCreatedAt(createdAt);
        return document;
    }

    private static Delivery delivery(String deliveryId, String provider, boolean invoiced, boolean synced) {
        Delivery delivery = new Delivery(STORE_ID, "EXT-" + deliveryId, provider);
        delivery.setDeliveryId(deliveryId);
        delivery.setInvoiced(invoiced);
        delivery.setSynced(synced);
        return delivery;
    }
}
