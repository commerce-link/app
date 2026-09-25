package pl.commercelink.web.orders;

/** One row of the orders list with every text already resolved, so the template only prints (spec §8.1). */
public record OrderRow(String href, String number, String sourceText, String externalId,
                       String clientName, String clientCity, String email,
                       String dueText, String dueNote, String dueTone,
                       String statusLabel, String statusTone,
                       String totalText, String unpaidText) {
}
