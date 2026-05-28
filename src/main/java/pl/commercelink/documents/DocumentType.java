package pl.commercelink.documents;

import pl.commercelink.invoicing.api.InvoiceKind;

import java.time.Year;

public enum DocumentType {
    Proforma("Proforma"),
    Order("Zamówienie"),
    InvoiceVat("Faktura VAT"),
    InvoiceAdvance("Faktura zaliczkowa"),
    InvoiceFinal("Faktura końcowa"),
    InvoicePersonal("Faktura imienna"),
    Receipt("Paragon"),

    GoodsReceipt("PZ"),
    GoodsIssue("WZ"),
    InternalReceipt("PW"),
    InternalIssue("RW"),
    StockTransfer("MM"),
    Reservation("Res");

    private final String sequenceCode;

    DocumentType(String sequenceCode) {
        this.sequenceCode = sequenceCode;
    }

    public boolean isB2BInvoice() {
        return this == InvoiceVat || this == InvoiceAdvance || this == InvoiceFinal;
    }

    public boolean isClosingInvoice() {
        return this == InvoiceVat || this == InvoiceFinal || this == InvoicePersonal || this == Receipt;
    }

    public boolean isReceiptType() {
        return this == GoodsReceipt || this == InternalReceipt;
    }

    public boolean isWarehouseDocument() {
        return this == GoodsReceipt ||
               this == GoodsIssue ||
               this == InternalReceipt ||
               this == InternalIssue ||
               this == StockTransfer ||
               this == Reservation;
    }

    public InvoiceKind toInvoiceKind() {
        return switch (this) {
            case Proforma -> InvoiceKind.Proforma;
            case Order -> InvoiceKind.Estimate;
            case InvoiceVat, InvoicePersonal -> InvoiceKind.Standard;
            case InvoiceAdvance -> InvoiceKind.Advance;
            case InvoiceFinal -> InvoiceKind.Final;
            default -> throw new IllegalArgumentException("Unsupported type: " + this);
        };
    }

    public String getSequenceKey(String warehouseId) {
        return sequenceCode + "/" + warehouseId + "/" + Year.now().getValue();
    }
}
