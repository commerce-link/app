package pl.commercelink.web.inventory;

import java.util.List;

public record TechnicalInventoryView(int globalInventorySize, int taxonomySize, String taxonomyFileName,
                                     int pimIndexSize, List<GlobalFeedRow> feeds) {
}
