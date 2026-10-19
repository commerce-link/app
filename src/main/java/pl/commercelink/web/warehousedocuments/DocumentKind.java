package pl.commercelink.web.warehousedocuments;

import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static pl.commercelink.documents.DocumentReason.*;

/** The warehouse document types the list offers (MM and Res are never created as warehouse documents). */
public enum DocumentKind {
    PZ(DocumentType.GoodsReceipt, true, List.of(SupplierDelivery, CustomerReturn, ServiceReturn)),
    WZ(DocumentType.GoodsIssue, false, List.of(CustomerOrder, ServiceOut)),
    PW(DocumentType.InternalReceipt, true, List.of(StockAdjustment)),
    RW(DocumentType.InternalIssue, false, List.of(Destruction, InternalUse, Theft, StockAdjustment));

    private final DocumentType type;
    private final boolean incoming;
    private final List<DocumentReason> reasons;

    DocumentKind(DocumentType type, boolean incoming, List<DocumentReason> reasons) {
        this.type = type;
        this.incoming = incoming;
        this.reasons = reasons;
    }

    public DocumentType type() { return type; }
    public String code() { return name(); }
    public boolean incoming() { return incoming; }
    public List<DocumentReason> reasons() { return reasons; }

    /** "PZ" or the old enum name "GoodsReceipt" (bookmarks of the old list), case-insensitive. */
    public static Optional<DocumentKind> parse(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String v = value.trim();
        return Arrays.stream(values())
                .filter(k -> k.name().equalsIgnoreCase(v) || k.type.name().equalsIgnoreCase(v))
                .findFirst();
    }

    public static Optional<DocumentKind> of(DocumentType type) {
        return Arrays.stream(values()).filter(k -> k.type == type).findFirst();
    }

    /** Every reason any kind can carry, in a stable order (the menu of "Wszystkie"). */
    public static List<DocumentReason> allReasons() {
        return Arrays.stream(values()).flatMap(k -> k.reasons.stream()).distinct().toList();
    }
}
