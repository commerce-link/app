package pl.commercelink.web.rma;

/** One row of the RMA list, already worded (spec §3.4). Null fields are left out of the cell. */
public record RmaRow(String number, String rmaId, String href, String sourceText, String reference, String clientName,
                     String clientCity, String email, String orderNumber, String orderId, String statusLabel,
                     String statusTone, String trackText, String marketplaceText, String createdText, String ageText,
                     String ageTone, String valueText) {
}
