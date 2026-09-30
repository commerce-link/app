package pl.commercelink.receipts;

import java.util.List;

/**
 * An attempt's problem on the order page: cause (one sentence), action (what to do, naming a button of the same row, or
 * what to check) and, when the provider has technical hints, details under a disclosure titled detailsSummary. action,
 * detailsSummary and details may be null. actions is the action as the page shows it, one paragraph per line: a
 * single line for most problems, several for advice that offers alternatives (a POS sale without the customer's
 * e-mail); action is the same text as one sentence run.
 */
public record ReceiptPageProblem(String cause, String action, String detailsSummary, String details,
                                 List<String> actions) {

    public ReceiptPageProblem {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    /** A problem whose action is one line. */
    public ReceiptPageProblem(String cause, String action, String detailsSummary, String details) {
        this(cause, action, detailsSummary, details, action == null ? List.of() : List.of(action));
    }

    /** A problem whose action is several lines, shown one per paragraph. */
    public static ReceiptPageProblem ofLines(String cause, List<String> actions, String detailsSummary, String details) {
        return new ReceiptPageProblem(cause, actions.isEmpty() ? null : String.join(" ", actions), detailsSummary,
                details, actions);
    }
}
