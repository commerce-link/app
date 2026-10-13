package pl.commercelink.web.rma;

import java.util.List;
import java.util.Map;

/** Everything the new RMA page prints, for each of its states. */
public record RmaNewPage(State state, String query, String backHref, String backLabel, String from,
                         String leadText, Header order, Choice choice, String notFoundDesc, Ineligible ineligible,
                         List<ItemRow> items, String itemsEmptyText, Summary summary,
                         String pageError, Map<String, String> errorSummary, Map<String, String> fieldErrors) {

    public enum State { START, CHOICE, NOT_FOUND, INELIGIBLE, ORDER }

    public record Header(String orderId, String shortId, String href, String statusKey, String statusTone, String ordered,
                         String client, String source) {
    }

    public record Choice(String title, List<ChoiceRow> rows, String limitText) {
    }

    public record ChoiceRow(String shortId, String fullId, String href, String ordered, String statusKey, String statusTone,
                            String value, String unavailableKey) {
    }

    public record Ineligible(String titleKey, String desc, String orderHref, String openLabel) {
    }

    public record ItemRow(int index, String itemId, String name, String sub, boolean service, int maxQty, String qty,
                          boolean selected, String resolution, String reason, String price, double unitPrice,
                          String claimedShortId, String claimedHref) {

        public boolean claimed() {
            return claimedShortId != null;
        }
    }

    public record Summary(String email, int count, int pieces, String value, boolean canCreate) {
    }

    public boolean hasErrors() {
        return pageError != null || !errorSummary.isEmpty();
    }
}
