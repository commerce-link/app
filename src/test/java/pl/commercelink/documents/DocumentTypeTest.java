package pl.commercelink.documents;

import org.junit.jupiter.api.Test;

import java.time.Year;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentTypeTest {

    private static final String WAREHOUSE = "wh-1";

    @Test
    void sequenceKeyHistoricalCompatibility() {
        String year = String.valueOf(Year.now().getValue());

        assertEquals("Proforma/wh-1/" + year, DocumentType.Proforma.getSequenceKey(WAREHOUSE));
        assertEquals("Zamówienie/wh-1/" + year, DocumentType.Order.getSequenceKey(WAREHOUSE));
        assertEquals("Faktura VAT/wh-1/" + year, DocumentType.InvoiceVat.getSequenceKey(WAREHOUSE));
        assertEquals("Faktura zaliczkowa/wh-1/" + year, DocumentType.InvoiceAdvance.getSequenceKey(WAREHOUSE));
        assertEquals("Faktura końcowa/wh-1/" + year, DocumentType.InvoiceFinal.getSequenceKey(WAREHOUSE));
        assertEquals("Faktura imienna/wh-1/" + year, DocumentType.InvoicePersonal.getSequenceKey(WAREHOUSE));
        assertEquals("Paragon/wh-1/" + year, DocumentType.Receipt.getSequenceKey(WAREHOUSE));
        assertEquals("PZ/wh-1/" + year, DocumentType.GoodsReceipt.getSequenceKey(WAREHOUSE));
        assertEquals("WZ/wh-1/" + year, DocumentType.GoodsIssue.getSequenceKey(WAREHOUSE));
        assertEquals("PW/wh-1/" + year, DocumentType.InternalReceipt.getSequenceKey(WAREHOUSE));
        assertEquals("RW/wh-1/" + year, DocumentType.InternalIssue.getSequenceKey(WAREHOUSE));
        assertEquals("MM/wh-1/" + year, DocumentType.StockTransfer.getSequenceKey(WAREHOUSE));
        assertEquals("Res/wh-1/" + year, DocumentType.Reservation.getSequenceKey(WAREHOUSE));
    }
}
