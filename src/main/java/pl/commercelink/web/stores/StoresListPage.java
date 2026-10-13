package pl.commercelink.web.stores;

import java.util.List;

public record StoresListPage(StoresListQuery query, List<StoreRow> rows, List<SegmentLink> segments,
                             DeleteDialog deleteDialog, EmptyState emptyState) {

    /** Where every action goes back to: the list as it is now, without an open delete dialog. */
    public String back() {
        return query.href();
    }

    public record SegmentLink(String labelKey, long count, String href, boolean active) {
    }

    /** The delete dialog rendered open by the server for ?delete= (the no-JavaScript path); trialOwnerEmail as in StoreRow. */
    public record DeleteDialog(String storeId, String name, boolean trial, String trialOwnerEmail, String actionPath) {
    }

    public record EmptyState(String textKey, String textArg, String actionKey, String actionHref) {
    }
}
