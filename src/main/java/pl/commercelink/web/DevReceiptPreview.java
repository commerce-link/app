package pl.commercelink.web;

import pl.commercelink.receipts.ReceiptAttempt;
import pl.commercelink.receipts.ReceiptRequestSnapshot;
import pl.commercelink.receipts.api.VatRate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What the dev e-receipt preview shows, computed from the attempt's frozen request so the page needs no arithmetic:
 * amounts are already formatted as PLN with a decimal comma, VAT rates and payment forms are message-key suffixes.
 */
public record DevReceiptPreview(String storeName, String orderId, String saleDate, String receiptKey, String providerReceiptId,
                         String state, String receiptNumber, String buyerEmail, List<Line> lines, String total,
                         List<VatSummary> vatSummary, List<Payment> payments) {

    private static final DateTimeFormatter SALE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public record Line(String name, String quantity, String unitPrice, String vatRate, String total) {
    }

    public record VatSummary(String vatRate, String gross) {
    }

    public record Payment(String form, String label, String amount) {
    }

    static DevReceiptPreview of(ReceiptAttempt attempt, ReceiptRequestSnapshot snapshot, String storeName) {
        long totalCents = 0;
        Map<VatRate, Long> grossByRate = new EnumMap<>(VatRate.class);
        List<Line> lines = new ArrayList<>();
        for (ReceiptRequestSnapshot.Line line : snapshot.lines()) {
            long lineCents = BigDecimal.valueOf(line.unitGrosze()).multiply(line.quantity())
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
            totalCents += lineCents;
            grossByRate.merge(line.vatRate(), lineCents, Long::sum);
            lines.add(new Line(line.name(), quantity(line.quantity()), pln(line.unitGrosze()), line.vatRate().name(),
                    pln(lineCents)));
        }
        List<VatSummary> vatSummary = grossByRate.entrySet().stream()
                .map(e -> new VatSummary(e.getKey().name(), pln(e.getValue())))
                .toList();
        List<Payment> payments = snapshot.payments().stream()
                .map(p -> new Payment(p.form().name(), p.label(), pln(p.grosze())))
                .toList();
        return new DevReceiptPreview(storeName, snapshot.orderId(),
                snapshot.saleDate() == null ? null : snapshot.saleDate().format(SALE_DATE),
                attempt.getReceiptKey(), attempt.getProviderReceiptId(),
                attempt.getState() == null ? null : attempt.getState().name(), attempt.getReceiptNumber(),
                snapshot.buyerEmail(), List.copyOf(lines), pln(totalCents), vatSummary, payments);
    }

    /** "1 234,56 zł": Polish grouping with a no-break space, always two decimals. */
    static String pln(long cents) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0.00", symbols).format(BigDecimal.valueOf(cents, 2)) + " zł";
    }

    private static String quantity(BigDecimal quantity) {
        return quantity.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
