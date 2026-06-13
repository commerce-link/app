package pl.commercelink.warehouse.builtin;

import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;

import java.util.List;

enum LedgerCategory {
    PURCHASE("Zakup"),
    CUSTOMER_RETURN("Zwrot klienta"),
    SERVICE_RETURN("Powrót z serwisu"),
    SURPLUS("Nadwyżka"),
    TRANSFER_IN("Przesunięcie przych."),
    SALE("Sprzedaż"),
    SERVICE_OUT("Wysłanie do serwisu"),
    DESTRUCTION("Zniszczenie"),
    THEFT("Kradzież"),
    INTERNAL_USE("Zużycie wewn."),
    SHORTAGE("Niedobór"),
    TRANSFER_OUT("Przesunięcie wych.");

    private final String label;

    LedgerCategory(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }

    static List<LedgerCategory> receipts() {
        return List.of(PURCHASE, CUSTOMER_RETURN, SERVICE_RETURN, SURPLUS, TRANSFER_IN);
    }

    static List<LedgerCategory> issues() {
        return List.of(SALE, SERVICE_OUT, DESTRUCTION, THEFT, INTERNAL_USE, SHORTAGE, TRANSFER_OUT);
    }

    static LedgerCategory resolve(DocumentType type, DocumentReason reason) {
        if (reason == null) {
            return switch (type) {
                case GoodsReceipt -> PURCHASE;
                case InternalReceipt -> SURPLUS;
                case GoodsIssue -> SALE;
                case InternalIssue -> SHORTAGE;
                default -> type.isReceiptType() ? PURCHASE : SALE;
            };
        }
        return switch (reason) {
            case SupplierDelivery -> PURCHASE;
            case CustomerReturn -> CUSTOMER_RETURN;
            case ServiceReturn -> SERVICE_RETURN;
            case TransferIn -> TRANSFER_IN;
            case CustomerOrder -> SALE;
            case ServiceOut -> SERVICE_OUT;
            case Destruction -> DESTRUCTION;
            case Theft -> THEFT;
            case InternalUse -> INTERNAL_USE;
            case TransferOut -> TRANSFER_OUT;
            case StockAdjustment -> type.isReceiptType() ? SURPLUS : SHORTAGE;
        };
    }
}
