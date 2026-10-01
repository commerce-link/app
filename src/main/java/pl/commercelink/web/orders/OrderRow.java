package pl.commercelink.web.orders;

import java.util.List;

/** One row of the orders list with every text already resolved, so the template only prints (spec §8.1). */
public record OrderRow(String href, String number, String sourceText, String externalId,
                       String clientName, String clientCity, String email,
                       String dueText, String dueNote, String dueTone,
                       String statusLabel, String statusTone,
                       String totalText, String unpaidText,
                       List<DocMark> marks) {

    /**
     * One of the markers under the status pill (spec §25): the warehouse document (WZ), the closing invoice or receipt
     * and the customer's review — what Order.isSettled waits for before the order closes on its own. state is a CSS
     * class: is-done (it exists) or is-todo (missing and holding a Delivered order open); code is the visible short text
     * (empty for the review, which shows a star), label the full sentence for screen readers.
     */
    public record DocMark(String kind, String code, String state, String label) {

        public boolean todo() {
            return "is-todo".equals(state);
        }
    }

    /** Something still blocks this order from closing: the phone card shows only these markers, and only then. */
    public boolean hasTodo() {
        return marks.stream().anyMatch(DocMark::todo);
    }
}
