package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockLedgerPeriodClosingTest {

    private static final String STORE_ID = "store-1";
    private static final StockLedgerPeriod MARCH = new StockLedgerPeriod(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
    private static final Clock OCTOBER_7 = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private StockLedgerService stockLedgerService;
    @Mock
    private StockLedgerClosings closings;
    @Mock
    private WarehouseDocumentRepository documents;
    @Mock
    private DeliveriesRepository deliveries;

    private StockLedgerPeriodClosing closing;

    @BeforeEach
    void setUp() {
        closing = new StockLedgerPeriodClosing(stockLedgerService, closings, documents, deliveries, OCTOBER_7);
    }

    @Test
    void anyPastPeriodCanBeClosedEvenWithNothingClosedBeforeIt() throws Exception {
        // given
        List<StockLedgerRow> rows = List.of(new StockLedgerRow("MFN-A", "Widget", 1, 10.0, Map.of(), Map.of()));
        when(stockLedgerService.generate(STORE_ID, MARCH.from(), MARCH.to())).thenReturn(rows);

        // when
        StockLedgerClosingResult result = closing.close(STORE_ID, MARCH, false);

        // then
        assertThat(result).isEqualTo(new Closed(MARCH, false));
        verify(closings).save(eq(STORE_ID), eq(MARCH), aryEq(StockLedgerRow.toCsv(rows)));
    }

    @Test
    void closingAClosedPeriodAgainRegeneratesIt() throws Exception {
        // given
        when(closings.exists(STORE_ID, MARCH)).thenReturn(true);

        // when
        StockLedgerClosingResult result = closing.close(STORE_ID, MARCH, false);

        // then
        assertThat(result).isEqualTo(new Closed(MARCH, true));
        verify(closings).save(eq(STORE_ID), eq(MARCH), any());
    }

    @Test
    void periodEndingTodayOrLaterCannotBeClosed() throws Exception {
        // given
        StockLedgerPeriod untilToday = new StockLedgerPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7));

        // when
        StockLedgerClosingResult result = closing.close(STORE_ID, untilToday, false);

        // then
        assertThat(result).isEqualTo(new NotAllowed("reports.stockLedger.closing.error.notOver"));
        verifyNoInteractions(closings, stockLedgerService);
    }

    @Test
    void periodEndingBeforeItStartsIsRejected() throws Exception {
        // given
        StockLedgerPeriod reversed = new StockLedgerPeriod(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 3, 1));

        // when
        StockLedgerClosingResult result = closing.close(STORE_ID, reversed, false);

        // then
        assertThat(result).isEqualTo(new NotAllowed("reports.stockLedger.closing.error.range"));
        verifyNoInteractions(closings, stockLedgerService);
    }

    @Test
    void deliveriesOfThePeriodWithoutAnInvoiceOrItsSyncHoldTheClosing() throws Exception {
        // given
        when(documents.findAllInDateRange(STORE_ID, MARCH.from().atStartOfDay(), MARCH.to().atTime(LocalTime.MAX)))
                .thenReturn(documents(receipt("no-invoice"), receipt("unsynced"), receipt("settled")));
        Delivery noInvoice = delivery("no-invoice", "Manual-Hurt", false, false);
        Delivery unsynced = delivery("unsynced", "Manual-Hurt", true, false);
        Delivery settled = delivery("settled", "Manual-Hurt", true, true);
        when(deliveries.findAllByIds(STORE_ID, Set.of("no-invoice", "unsynced", "settled")))
                .thenReturn(List.of(noInvoice, unsynced, settled));

        // when
        StockLedgerClosingResult result = closing.close(STORE_ID, MARCH, false);

        // then
        assertThat(result).isInstanceOf(Blocked.class);
        assertThat(((Blocked) result).deliveries()).containsExactlyInAnyOrder(noInvoice, unsynced);
        verify(closings, never()).save(any(), any(), any());
    }

    @Test
    void periodCanBeClosedDespiteUnsettledDeliveries() throws Exception {
        // when
        StockLedgerClosingResult result = closing.close(STORE_ID, MARCH, true);

        // then
        assertThat(result).isEqualTo(new Closed(MARCH, false));
        verifyNoInteractions(documents, deliveries);
        verify(closings).save(eq(STORE_ID), eq(MARCH), any());
    }

    @Test
    void ownWarehouseReceiptsAndOtherDocumentsDoNotHoldTheClosing() throws Exception {
        // given
        WarehouseDocument returnFromCustomer = receipt(null);
        WarehouseDocument issue = document("sold", DocumentType.GoodsIssue, LocalDateTime.of(2026, 3, 10, 9, 0));
        when(documents.findAllInDateRange(STORE_ID, MARCH.from().atStartOfDay(), MARCH.to().atTime(LocalTime.MAX)))
                .thenReturn(documents(receipt("own"), returnFromCustomer, issue));
        when(deliveries.findAllByIds(STORE_ID, Set.of("own")))
                .thenReturn(List.of(delivery("own", SupplierRegistry.WAREHOUSE, false, false)));

        // when
        StockLedgerClosingResult result = closing.close(STORE_ID, MARCH, false);

        // then
        assertThat(result).isEqualTo(new Closed(MARCH, false));
    }

    private static List<WarehouseDocument> documents(WarehouseDocument... documents) {
        return new ArrayList<>(List.of(documents));
    }

    private static WarehouseDocument receipt(String deliveryId) {
        WarehouseDocument document = document("pz-" + deliveryId, DocumentType.GoodsReceipt, LocalDateTime.of(2026, 3, 15, 12, 0));
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
