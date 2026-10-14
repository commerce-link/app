package pl.commercelink.web.payments;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The reconciliation of one uploaded statement. Rows are grouped by how sure the match is; the page carries the
 * operations (operationsJson), the proposals the operator may change (allocationsJson), the orders that still owe
 * money (receivablesJson) and the payout channels (channelsJson) for its dialogs, so nothing is kept on the server.
 */
public record StatementPage(String accountId, String fileName, List<Tile> tiles, List<Group> groups, List<Notice> notices,
                            List<String> problems, String problemsJson, int outgoing, int skipped, String operationsJson,
                            String allocationsJson, String receivablesJson, String channelsJson, int selectedCount,
                            String selectedTotal, int selectedOrders) {

    /** id is the id of the group the tile sums up. */
    public record Tile(String id, String label, int count, String amount, String hint) {
    }

    public record Group(String id, String title, List<Row> rows) {
    }

    /**
     * One incoming operation. selectable rows have a proposal and are booked when ticked; payout rows open the payout
     * dialog, the others the assignment dialog (actionLabel, or none when booked already). channels lists (JSON) the
     * indexes in channelsJson of the payer's rows; candidatesJson the orders offered first in the dialog.
     */
    public record Row(String number, String date, String sender, List<TitlePart> title, String amount, long amountGrosze,
                      String group, Pill rule, List<Pill> warnings, List<OrderRef> orders, String ordersNote,
                      String paymentType, String paymentReference, boolean selectable, boolean selected,
                      boolean payout, String channels, String candidatesJson, String actionLabel) {

        public String titleText() {
            return title.stream().map(TitlePart::text).collect(Collectors.joining());
        }
    }

    /** A piece of the transfer title; hit marks the number the match was found by. */
    public record TitlePart(String text, boolean hit) {
    }

    public record Pill(String label, String tone) {
    }

    public record OrderRef(String orderId, String shortId, String href, String client, String detail) {
    }

    public record Notice(String text, String tone, List<String> details) {
    }
}
