package pl.commercelink.web.warehousedocuments;

import org.springframework.context.MessageSource;
import pl.commercelink.warehouse.builtin.MfnHistory;
import pl.commercelink.warehouse.builtin.MfnHistoryRow;
import pl.commercelink.web.deliveries.details.DeliveryLinks;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The history of one item in one delivery (spec §2.6), every text resolved. */
public record ItemHistoryPage(String backHref, String backLabel, String productName, String mfn, String deliveryShortId,
                              String deliveryHref, String movesTitle, List<Move> moves, String summary, String emptyText) {

    public record Move(String documentNo, String documentHref, String typeName, boolean incoming, String date,
                       String change, String changeTone, String after) {}

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");

    public static ItemHistoryPage of(MfnHistory history, String deliveryId, String mfn, String backHref, String backLabel,
                                     MessageSource messages, Locale locale) {
        int in = 0, out = 0, after = 0;
        List<Move> moves = new ArrayList<>();
        for (MfnHistoryRow r : history.rows()) {
            DocumentKind kind = r.documentType() == null ? null : DocumentKind.of(r.documentType()).orElse(null);
            if (r.stockChange() > 0) in += r.stockChange(); else out -= r.stockChange();
            after = r.stockAfter();
            String change = r.stockChange() > 0 ? "+" + r.stockChange() : r.stockChange() < 0 ? "−" + (-r.stockChange()) : "0";
            String tone = r.stockChange() > 0 ? "is-in" : r.stockChange() < 0 ? "is-out" : null;
            moves.add(new Move(r.documentNo(), DocumentRowMapper.detailsHref("/dashboard", r.documentId()),
                    typeName(r, kind, messages, locale), kind != null && kind.incoming(),
                    r.createdAt() == null ? "—" : r.createdAt().format(WHEN), change, tone,
                    messages.getMessage("warehouse.documents.history.after", new Object[]{r.stockAfter()}, locale)));
        }
        return new ItemHistoryPage(backHref, backLabel, history.productName(), mfn, DocumentRowMapper.shortId(deliveryId),
                DeliveryLinks.of(false, null, deliveryId).details(),
                messages.getMessage("warehouse.documents.history.moves", new Object[]{moves.size()}, locale), moves,
                moves.isEmpty() ? null : messages.getMessage("warehouse.documents.history.summary", new Object[]{in, out, after}, locale),
                moves.isEmpty() ? messages.getMessage("warehouse.documents.history.empty", null, locale) : null);
    }

    private static String typeName(MfnHistoryRow r, DocumentKind kind, MessageSource messages, Locale locale) {
        if (kind != null) return messages.getMessage("warehouse.documents.kind." + kind.code(), null, locale);
        if (r.documentType() == null) return messages.getMessage("warehouse.documents.list.none", null, locale);
        return messages.getMessage("DocumentType." + r.documentType().name(), null, locale);
    }
}
