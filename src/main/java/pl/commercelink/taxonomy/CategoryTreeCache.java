package pl.commercelink.taxonomy;

public class CategoryTreeCache {

    public int sequenceNumberOf(String categoryKey) {
        return CategoryCatalog.isKnown(categoryKey) ? CategoryCatalog.sequenceOf(categoryKey) : Integer.MAX_VALUE;
    }

    public String groupForCategoryKey(String categoryKey) {
        return CategoryCatalog.groupKeyOf(categoryKey);
    }
}
