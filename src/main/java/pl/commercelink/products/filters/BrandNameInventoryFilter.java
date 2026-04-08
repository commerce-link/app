package pl.commercelink.products.filters;

import pl.commercelink.starter.dynamodb.Metadata;
import pl.commercelink.starter.dynamodb.MetadataField;
import pl.commercelink.inventory.MatchedInventory;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static pl.commercelink.starter.util.ConversionUtil.asDistinctCollectionFromCommaSeparateText;

class BrandNameInventoryFilter extends InventoryFilter {

    private static final List<MetadataField> FIELDS_LIST = new LinkedList<>();

    private static final String BRAND_FIELD_NAME = "Brands";

    static {
        FIELDS_LIST.add(new MetadataField(BRAND_FIELD_NAME, "Comma separated list of brands"));
    }

    public BrandNameInventoryFilter() {
        super(InventoryFilterType.BRAND_NAME, FIELDS_LIST);
    }

    @Override
    public boolean run(MatchedInventory matchedInventory, List<Metadata> metadata) {
        Collection<String> brands =  asDistinctCollectionFromCommaSeparateText(getMetadataByKey(metadata, BRAND_FIELD_NAME).getValue());
        return brands.contains(matchedInventory.getTaxonomy().brand().toLowerCase());
    }

}
