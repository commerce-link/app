package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseDocumentMfnHistoryServiceTest {

    @Mock
    WarehouseDocumentItemRepository items;
    @Mock
    WarehouseDocumentRepository documents;
    @InjectMocks
    WarehouseDocumentMfnHistoryService service;

    @Test
    void movesAreChronologicalWithRunningStockAndTheProductName() {
        // given
        when(items.findByDeliveryId("del-1")).thenReturn(List.of(
                item("d2", DocumentType.GoodsIssue, 2, LocalDateTime.of(2026, 10, 3, 11, 0)),
                item("d1", DocumentType.GoodsReceipt, 5, LocalDateTime.of(2026, 10, 2, 8, 0))));
        when(documents.findByDocumentId("s1", "d1")).thenReturn(doc("d1", "PZ/MAG1/2026/000210"));
        when(documents.findByDocumentId("s1", "d2")).thenReturn(doc("d2", "WZ/MAG1/2026/001179"));

        // when
        MfnHistory history = service.history("s1", "del-1", "MZ-V9P2T0BW");

        // then
        assertThat(history.productName()).isEqualTo("Samsung 990 PRO");
        assertThat(history.rows()).extracting(MfnHistoryRow::documentNo).containsExactly("PZ/MAG1/2026/000210", "WZ/MAG1/2026/001179");
        assertThat(history.rows()).extracting(MfnHistoryRow::stockAfter).containsExactly(5, 3);
    }

    @Test
    void itemsWhoseDocumentIsNotInTheStoreAreSkipped() {
        // given - a delivery id of another store typed into the address
        when(items.findByDeliveryId("del-x")).thenReturn(List.of(item("dx", DocumentType.GoodsReceipt, 5, LocalDateTime.now())));
        when(documents.findByDocumentId("s1", "dx")).thenReturn(null);

        // when
        MfnHistory history = service.history("s1", "del-x", "MZ-V9P2T0BW");

        // then
        assertThat(history.rows()).isEmpty();
        assertThat(history.productName()).isNull();
    }

    @Test
    void anItemWithoutADocumentTypeDoesNotThrowAndDoesNotMoveStock() {
        // given
        when(items.findByDeliveryId("del-1")).thenReturn(List.of(item("d1", null, 4, LocalDateTime.of(2026, 10, 2, 8, 0))));
        when(documents.findByDocumentId("s1", "d1")).thenReturn(doc("d1", "X/1"));

        // when
        MfnHistory history = service.history("s1", "del-1", "MZ-V9P2T0BW");

        // then
        assertThat(history.rows()).extracting(MfnHistoryRow::stockChange).containsExactly(0);
    }

    @Test
    void eachRowCarriesItsDocumentsReasonAndNoteWithoutExtraReads() {
        // given
        when(items.findByDeliveryId("del-1")).thenReturn(List.of(
                item("d1", DocumentType.GoodsReceipt, 5, LocalDateTime.of(2026, 10, 2, 8, 0)),
                item("d2", DocumentType.InternalIssue, 2, LocalDateTime.of(2026, 10, 3, 11, 0))));
        WarehouseDocument plain = doc("d1", "PZ/1");
        WarehouseDocument destruction = doc("d2", "RW/1");
        destruction.setReason(DocumentReason.Destruction);
        destruction.setNote("Zalany w magazynie B");
        when(documents.findByDocumentId("s1", "d1")).thenReturn(plain);
        when(documents.findByDocumentId("s1", "d2")).thenReturn(destruction);

        // when
        MfnHistory history = service.history("s1", "del-1", "MZ-V9P2T0BW");

        // then
        assertThat(history.rows()).extracting(MfnHistoryRow::reason).containsExactly(null, DocumentReason.Destruction);
        assertThat(history.rows()).extracting(MfnHistoryRow::note).containsExactly(null, "Zalany w magazynie B");
        verify(documents, times(2)).findByDocumentId(org.mockito.ArgumentMatchers.eq("s1"), org.mockito.ArgumentMatchers.anyString());
    }

    private static WarehouseDocumentItem item(String documentId, DocumentType type, int qty, LocalDateTime at) {
        return new WarehouseDocumentItem(documentId, type, at, "del-1", "5901234567890", "MZ-V9P2T0BW", "Samsung 990 PRO", qty, 10.0);
    }

    private static WarehouseDocument doc(String id, String no) {
        WarehouseDocument d = new WarehouseDocument();
        d.setDocumentId(id);
        d.setDocumentNo(no);
        return d;
    }
}
