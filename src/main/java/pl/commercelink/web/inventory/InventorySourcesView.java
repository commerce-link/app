package pl.commercelink.web.inventory;

import java.util.List;

public record InventorySourcesView(List<SourceRow> attention, List<SourceRow> working, int activeSupplierCount,
                                   int connectedSupplierCount, boolean externalWarehouse) {

    static final int COLLAPSED_ISSUE_LIMIT = 2;

    public static final InventorySourcesView EMPTY = new InventorySourcesView(List.of(), List.of(), 0, 0, false);

    public boolean hasIssues() {
        return !attention.isEmpty();
    }

    public List<SourceRow> collapsedIssues() {
        return attention.subList(0, Math.min(COLLAPSED_ISSUE_LIMIT, attention.size()));
    }

    public int moreIssues() {
        return Math.max(0, attention.size() - COLLAPSED_ISSUE_LIMIT);
    }

    public boolean hasSuppliers() {
        return activeSupplierCount > 0;
    }

    public int sourceCount() {
        return activeSupplierCount + 1;
    }
}
