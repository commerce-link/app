package pl.commercelink.web.inventory;

import java.util.List;

public record TechnicalInventoryView(int globalInventorySize, long taxonomySize, String taxonomyFileName,
                                     int pimIndexSize, List<GlobalFeedRow> feeds) {
}
