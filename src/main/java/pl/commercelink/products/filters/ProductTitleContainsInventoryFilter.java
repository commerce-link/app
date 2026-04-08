package pl.commercelink.products.filters;

import pl.commercelink.starter.dynamodb.Metadata;
import pl.commercelink.starter.dynamodb.MetadataField;
import pl.commercelink.inventory.MatchedInventory;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static pl.commercelink.starter.util.ConversionUtil.asDistinctCollectionFromCommaSeparateText;

public class ProductTitleContainsInventoryFilter extends InventoryFilter {

    private static final List<MetadataField> FIELDS_LIST = new LinkedList<>();

    private static final String KEYWORDS_FIELD_NAME = "Keywords";

    static {
        FIELDS_LIST.add(new MetadataField(KEYWORDS_FIELD_NAME, "Comma separated list of keywords. Considered true if any of the keywords is found in the product title."));
    }

    public ProductTitleContainsInventoryFilter() {
        super(InventoryFilterType.PRODUCT_TITLE_CONTAINS, FIELDS_LIST);
    }

    @Override
    public boolean run(MatchedInventory matchedInventory, List<Metadata> metadata) {
        Collection<String> keywords =  asDistinctCollectionFromCommaSeparateText(getMetadataByKey(metadata, KEYWORDS_FIELD_NAME).getValue());
        return containsKeyword(matchedInventory.getTaxonomy().name(), keywords);
    }

    private boolean containsKeyword(String name, Collection<String> keywords) {
        for (String keyword : keywords) {
            if (name.toLowerCase().contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
