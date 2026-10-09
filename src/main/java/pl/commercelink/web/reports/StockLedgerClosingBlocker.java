package pl.commercelink.web.reports;

import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.supplier.SupplierLabelMap;

import java.io.Serializable;
import java.time.LocalDate;

/** A delivery that keeps its month from closing, carried to the reports page as a flash attribute. */
public record StockLedgerClosingBlocker(String deliveryId, String number, String supplier, String supplierOrder,
                                        LocalDate receivedOn, String missingKey) implements Serializable {

    public static StockLedgerClosingBlocker of(Delivery delivery, SupplierLabelMap labels) {
        return new StockLedgerClosingBlocker(
                delivery.getDeliveryId(),
                delivery.getShortenedDeliveryId(),
                labels.of(delivery.getProvider()),
                delivery.getExternalDeliveryId(),
                delivery.getReceivedAt() == null ? null : delivery.getReceivedAt().toLocalDate(),
                delivery.isMissingInvoice()
                        ? "reports.stockLedger.closing.missing.invoice"
                        : "reports.stockLedger.closing.missing.sync");
    }
}
