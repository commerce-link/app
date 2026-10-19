package pl.commercelink.web.reports;

import pl.commercelink.financials.SupplierDeliveries;
import pl.commercelink.inventory.supplier.SupplierLabelMap;

import java.util.List;

/** Deliveries and their costs per supplier, the busiest first; a null name is the row of deliveries without a supplier. */
public record SupplierCosts(List<Row> rows, int deliveries, String shipping, String payment, String totalCosts) {

    public record Row(String name, int deliveries, double meter, String shipping, String payment) {

        public String deliveriesText() {
            return ReportNumbers.count(deliveries);
        }
    }

    public static SupplierCosts of(List<SupplierDeliveries> suppliers, SupplierLabelMap labels) {
        int busiest = suppliers.stream().mapToInt(SupplierDeliveries::deliveries).max().orElse(0);
        List<Row> rows = suppliers.stream()
                .map(supplier -> new Row(supplier.provider() == null ? null : labels.of(supplier.provider()),
                        supplier.deliveries(), busiest == 0 ? 0 : supplier.deliveries() * 100.0 / busiest,
                        ReportNumbers.amount(supplier.shippingCost()), ReportNumbers.amount(supplier.paymentCost())))
                .toList();
        double shipping = suppliers.stream().mapToDouble(SupplierDeliveries::shippingCost).sum();
        double payment = suppliers.stream().mapToDouble(SupplierDeliveries::paymentCost).sum();
        return new SupplierCosts(rows, suppliers.stream().mapToInt(SupplierDeliveries::deliveries).sum(),
                ReportNumbers.amount(shipping), ReportNumbers.amount(payment), ReportNumbers.amount(shipping + payment));
    }

    public String deliveriesText() {
        return ReportNumbers.count(deliveries);
    }

    public boolean empty() {
        return rows.isEmpty();
    }
}
