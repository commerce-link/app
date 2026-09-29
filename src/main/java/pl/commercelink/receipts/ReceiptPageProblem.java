package pl.commercelink.receipts;

/**
 * An attempt's problem on the order page: cause (one sentence, warning tone), action (what to do, naming a button of
 * the same row, or what to check) and, when the provider has technical hints, details under a disclosure titled
 * detailsSummary. action, detailsSummary and details may be null.
 */
public record ReceiptPageProblem(String cause, String action, String detailsSummary, String details) {
}
